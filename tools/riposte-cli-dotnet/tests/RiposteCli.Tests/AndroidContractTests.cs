using System.IO.Compression;
using System.Text.Json;
using System.Text.Json.Serialization;
using RiposteCli.Models;
using RiposteCli.Services;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;

namespace RiposteCli.Tests;

public class AndroidContractTests : IDisposable
{
    private const long MaxAppEntryBytes = 50L * 1024 * 1024;
    private readonly string _tempRoot;

    // Must match DefaultZipImporter.SUPPORTED_IMAGE_EXTENSIONS on Android side
    private static readonly HashSet<string> SupportedExtensions = new(StringComparer.OrdinalIgnoreCase)
    {
        ".webp", ".jpg", ".jpeg", ".png", ".gif",
        ".bmp", ".tiff", ".tif", ".heic", ".heif",
        ".avif", ".jxl",
    };

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
    };

    public AndroidContractTests()
    {
        _tempRoot = Path.Combine(Path.GetTempPath(), $"riposte-android-contract-{Guid.NewGuid()}");
        Directory.CreateDirectory(_tempRoot);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempRoot))
            Directory.Delete(_tempRoot, true);
    }

    [Fact]
    public void SidecarSchema_IsCompatibleWithAndroidImportContract()
    {
        var metadata = new SidecarMetadata
        {
            Emojis = ["😂", "🔥"],
            ContentHash = new string('a', 64),
            Localizations = new Dictionary<string, LocalizedContent>
            {
                ["en"] = new() { Title = "English title" },
                ["cs-CZ"] = new() { Title = "Český název" },
            },
            Emotions = new EmotionMetadata
            {
                Primary = "humor",
                Sentiment = "positive",
            },
        };

        using var doc = JsonDocument.Parse(JsonSerializer.Serialize(metadata, JsonOptions));
        var root = doc.RootElement;

        Assert.True(root.TryGetProperty("schemaVersion", out var schemaVersion));
        Assert.Equal(JsonValueKind.String, schemaVersion.ValueKind);

        Assert.True(root.TryGetProperty("emojis", out var emojis));
        Assert.Equal(JsonValueKind.Array, emojis.ValueKind);
        Assert.All(emojis.EnumerateArray(), emoji => Assert.Equal(JsonValueKind.String, emoji.ValueKind));

        Assert.True(root.TryGetProperty("createdAt", out var createdAt));
        Assert.Equal(JsonValueKind.String, createdAt.ValueKind);
        Assert.True(DateTimeOffset.TryParse(createdAt.GetString(), out _));

        Assert.True(root.TryGetProperty("emotions", out var emotions));
        Assert.Equal(JsonValueKind.String, emotions.GetProperty("primary").ValueKind);
        Assert.Equal(JsonValueKind.String, emotions.GetProperty("sentiment").ValueKind);

        Assert.True(root.TryGetProperty("localizations", out var localizations));
        foreach (var localization in localizations.EnumerateObject())
        {
            Assert.Matches("^[a-z]{2}(-[A-Za-z]{2})?$", localization.Name);
        }

        Assert.True(root.TryGetProperty("contentHash", out var contentHash));
        Assert.Matches("^[a-f0-9]{64}$", contentHash.GetString()!);
    }

    [Fact]
    public void SidecarOptionalFields_AreOmittedWhenNull()
    {
        var metadata = new SidecarMetadata { Emojis = ["😂"] };
        using var doc = JsonDocument.Parse(JsonSerializer.Serialize(metadata, JsonOptions));
        var root = doc.RootElement;

        Assert.False(root.TryGetProperty("title", out _));
        Assert.False(root.TryGetProperty("description", out _));
        Assert.False(root.TryGetProperty("tags", out _));
        Assert.False(root.TryGetProperty("searchPhrases", out _));
        Assert.False(root.TryGetProperty("primaryLanguage", out _));
        Assert.False(root.TryGetProperty("localizations", out _));
        Assert.False(root.TryGetProperty("contentHash", out _));
        Assert.False(root.TryGetProperty("basedOn", out _));
        Assert.False(root.TryGetProperty("emotions", out _));
    }

    [Fact]
    public void FullBundle_IsCompatibleWithAndroidZipImportContract()
    {
        var result = BuildBundle(ZipMode.Full);
        Assert.EndsWith(".meme.zip", result.ZipPath, StringComparison.OrdinalIgnoreCase);

        using var zip = ZipFile.OpenRead(result.ZipPath);
        Assert.NotEmpty(zip.Entries);
        Assert.All(zip.Entries, entry =>
        {
            Assert.DoesNotContain("/", entry.FullName);
            Assert.DoesNotContain("\\", entry.FullName);
            Assert.False(string.IsNullOrEmpty(entry.Name));
            Assert.True(entry.Length <= MaxAppEntryBytes, $"{entry.FullName} exceeds 50MB");
        });

        var imageEntries = zip.Entries.Where(e => !e.Name.EndsWith(".json", StringComparison.OrdinalIgnoreCase)).ToList();
        var sidecarEntries = zip.Entries.Where(e => e.Name.EndsWith(".json", StringComparison.OrdinalIgnoreCase)).ToList();

        Assert.NotEmpty(imageEntries);
        Assert.Equal(imageEntries.Count, sidecarEntries.Count);

        foreach (var imageEntry in imageEntries)
        {
            Assert.Contains(Path.GetExtension(imageEntry.Name), SupportedExtensions);
            Assert.Contains($"{imageEntry.Name}.json", sidecarEntries.Select(e => e.Name));
        }

        foreach (var sidecarEntry in sidecarEntries)
        {
            Assert.Contains(sidecarEntry.Name[..^5], imageEntries.Select(e => e.Name));
            using var stream = sidecarEntry.Open();
            using var doc = JsonDocument.Parse(stream);
            Assert.Equal(JsonValueKind.Object, doc.RootElement.ValueKind);
        }
    }

    [Fact]
    public void PatchBundle_NameMatchesAndroidRecognitionContract()
    {
        var result = BuildBundle(ZipMode.Patch);
        Assert.EndsWith(".patch.meme.zip", result.ZipPath, StringComparison.OrdinalIgnoreCase);
        Assert.Contains(".meme.zip", Path.GetFileName(result.ZipPath), StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void SchemaVersion_MatchesAndroidCurrentVersion()
    {
        // Must stay in sync with MemeMetadata.CURRENT_SCHEMA_VERSION ("1.4") on Android.
        var metadata = new SidecarMetadata { Emojis = ["😂"] };
        using var doc = JsonDocument.Parse(JsonSerializer.Serialize(metadata, JsonOptions));
        Assert.Equal("1.4", doc.RootElement.GetProperty("schemaVersion").GetString());
    }

    [Fact]
    public void BundleSidecars_HaveNonEmptyEmojis_RequiredByAndroid()
    {
        // Android MemeMetadata init block: require(emojis.isNotEmpty())
        // Empty emojis will throw during deserialization, skipping the meme.
        var result = BuildBundle(ZipMode.Full);
        using var zip = ZipFile.OpenRead(result.ZipPath);

        var sidecarEntries = zip.Entries
            .Where(e => e.Name.EndsWith(".json", StringComparison.OrdinalIgnoreCase))
            .ToList();

        Assert.NotEmpty(sidecarEntries);
        foreach (var entry in sidecarEntries)
        {
            using var stream = entry.Open();
            using var doc = JsonDocument.Parse(stream);
            var emojis = doc.RootElement.GetProperty("emojis");
            Assert.Equal(JsonValueKind.Array, emojis.ValueKind);
            Assert.NotEqual(0, emojis.GetArrayLength());
        }
    }

    [Fact]
    public void EmotionsObject_CompatibleWithAndroidEmotionData()
    {
        // Verifies all field names match Android's EmotionData @SerialName annotations:
        // primary, secondary, sentiment, intensity, memeUsage
        var metadata = new SidecarMetadata
        {
            Emojis = ["😂"],
            Emotions = new EmotionMetadata
            {
                Primary = "humor",
                Sentiment = "positive",
                Intensity = "high",
                Secondary = ["joy", "amusement"],
                MemeUsage = ["when something is hilarious"],
            },
        };

        using var doc = JsonDocument.Parse(JsonSerializer.Serialize(metadata, JsonOptions));
        var emotions = doc.RootElement.GetProperty("emotions");

        Assert.Equal("humor", emotions.GetProperty("primary").GetString());
        Assert.Equal("positive", emotions.GetProperty("sentiment").GetString());
        Assert.Equal("high", emotions.GetProperty("intensity").GetString());
        Assert.Equal(JsonValueKind.Array, emotions.GetProperty("secondary").ValueKind);
        Assert.Equal(JsonValueKind.Array, emotions.GetProperty("memeUsage").ValueKind);
    }

    [Fact]
    public void FullBundle_NoEntriesStartWithDot()
    {
        // Android DefaultZipImporter skips entries starting with "."
        var result = BuildBundle(ZipMode.Full);
        using var zip = ZipFile.OpenRead(result.ZipPath);
        Assert.All(zip.Entries, entry =>
            Assert.False(entry.Name.StartsWith('.'),
                $"Entry '{entry.Name}' starts with dot — Android skips these"));
    }

    [Fact]
    public void Sidecar_ExtraCliFields_PresentButSafeForAndroid()
    {
        // Android uses ignoreUnknownKeys = true, so CLI-specific fields are safely
        // ignored. This test documents which extra fields the CLI writes.
        var metadata = new SidecarMetadata { Emojis = ["😂"] };
        using var doc = JsonDocument.Parse(JsonSerializer.Serialize(metadata, JsonOptions));
        var root = doc.RootElement;

        // CLI-specific provenance fields not in Android's MemeMetadata
        Assert.True(root.TryGetProperty("appVersion", out _), "appVersion must be present for CLI provenance");
        Assert.True(root.TryGetProperty("cliVersion", out _), "cliVersion must be present for CLI provenance");

        // These fields ARE in Android's MemeMetadata and must serialize correctly
        Assert.True(root.TryGetProperty("schemaVersion", out _));
        Assert.True(root.TryGetProperty("emojis", out _));
        Assert.True(root.TryGetProperty("createdAt", out _));
    }

    private ZipBundleResult BuildBundle(ZipMode mode)
    {
        var imageDir = Path.Combine(_tempRoot, $"{mode.ToString().ToLowerInvariant()}-images");
        var outputDir = Path.Combine(_tempRoot, $"{mode.ToString().ToLowerInvariant()}-output");
        Directory.CreateDirectory(imageDir);
        Directory.CreateDirectory(outputDir);

        var imageA = CreateImage(imageDir, "one.jpg");
        var imageB = CreateImage(imageDir, "two.png");
        var images = new List<string> { imageA, imageB };

        SidecarService.WriteSidecar(imageA, new SidecarMetadata
        {
            Emojis = ["😂"],
            Emotions = new EmotionMetadata
            {
                Primary = "humor",
                Sentiment = "positive",
            },
        }, outputDir);

        SidecarService.WriteSidecar(imageB, new SidecarMetadata
        {
            Emojis = ["🔥"],
            ContentHash = new string('b', 64),
            Localizations = new Dictionary<string, LocalizedContent>
            {
                ["en"] = new() { Title = "Title" },
            },
        }, outputDir);

        var plans = images.Select(i => new ImageRebuildPlan
        {
            ImagePath = i,
            Scope = RebuildScope.Skip,
        }).ToList();

        var optimizedMap = images.ToDictionary(i => i, i => i, StringComparer.OrdinalIgnoreCase);
        var processed = mode == ZipMode.Patch
            ? images.Select(i => (i, $"{Path.GetFileName(i)}.json")).ToList()
            : [];

        return ZipBundler.CreateBundle(
            mode,
            new DirectoryInfo(imageDir),
            outputDir,
            allImages: images,
            plans: plans,
            processed: processed,
            bundleOptimizedMap: optimizedMap,
            manifest: new BuildManifest());
    }

    private static string CreateImage(string directory, string fileName)
    {
        var path = Path.Combine(directory, fileName);
        using var image = new Image<Rgba32>(2, 2);
        image.Save(path);
        return path;
    }
}
