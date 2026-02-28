using System.IO.Compression;
using System.Text.Json;
using RiposteCli.Models;
using RiposteCli.Services;

namespace RiposteCli.Tests;

/// <summary>
/// Verifies that CLI-generated ZIP bundles are compatible with the Riposte Android app's
/// import system (DefaultZipImporter). Checks entry flatness, supported extensions, valid
/// JSON sidecars, image+sidecar pairing, and WebP bundling.
/// </summary>
public class ZipAppCompatibilityTests : IDisposable
{
    private readonly string _tempDir;

    /// <summary>
    /// Supported image extensions in the Android app (DefaultZipImporter.SUPPORTED_IMAGE_EXTENSIONS).
    /// </summary>
    private static readonly HashSet<string> AppSupportedExtensions = new(StringComparer.OrdinalIgnoreCase)
    {
        ".jpg", ".jpeg", ".png", ".webp", ".gif",
        ".bmp", ".tiff", ".tif", ".heic", ".heif",
        ".avif", ".jxl",
    };

    public ZipAppCompatibilityTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), $"riposte-compat-test-{Guid.NewGuid()}");
        Directory.CreateDirectory(_tempDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempDir))
            Directory.Delete(_tempDir, true);
    }

    [Fact]
    public void ZipEntries_AreFlat_NoPathSeparators()
    {
        var (zipPath, _) = CreateBundleWithImages("flat-test", ("img1.jpg", "😂"), ("img2.png", "🔥"));

        using var zip = ZipFile.OpenRead(zipPath);
        foreach (var entry in zip.Entries)
        {
            Assert.DoesNotContain("/", entry.FullName);
            Assert.DoesNotContain("\\", entry.FullName);
            Assert.Equal(entry.Name, entry.FullName);
        }
    }

    [Fact]
    public void AllImageEntries_HaveSupportedExtensions()
    {
        var (zipPath, _) = CreateBundleWithImages("ext-test",
            ("photo.jpg", "😂"), ("meme.png", "🔥"), ("reaction.gif", "🤣"));

        using var zip = ZipFile.OpenRead(zipPath);
        var imageEntries = zip.Entries.Where(e => !e.Name.EndsWith(".json")).ToList();

        Assert.NotEmpty(imageEntries);
        foreach (var entry in imageEntries)
        {
            var ext = Path.GetExtension(entry.Name).ToLowerInvariant();
            Assert.Contains(ext, AppSupportedExtensions);
        }
    }

    [Fact]
    public void AllSidecarEntries_AreValidJson()
    {
        var (zipPath, _) = CreateBundleWithImages("json-test",
            ("a.jpg", "😂"), ("b.png", "🔥"));

        using var zip = ZipFile.OpenRead(zipPath);
        var jsonEntries = zip.Entries.Where(e => e.Name.EndsWith(".json")).ToList();

        Assert.NotEmpty(jsonEntries);
        foreach (var entry in jsonEntries)
        {
            using var stream = entry.Open();
            using var reader = new StreamReader(stream);
            var content = reader.ReadToEnd();

            // Must parse without throwing
            var doc = JsonDocument.Parse(content);

            // Must have required fields for app's MemeMetadata
            Assert.True(doc.RootElement.TryGetProperty("schemaVersion", out _),
                $"Entry '{entry.Name}' missing 'schemaVersion'");
            Assert.True(doc.RootElement.TryGetProperty("emojis", out var emojis),
                $"Entry '{entry.Name}' missing 'emojis'");
            Assert.Equal(JsonValueKind.Array, emojis.ValueKind);
            Assert.True(emojis.GetArrayLength() > 0,
                $"Entry '{entry.Name}' has empty 'emojis' array");
        }
    }

    [Fact]
    public void ImageAndSidecar_PairsMatchByName()
    {
        var (zipPath, _) = CreateBundleWithImages("pair-test",
            ("cat.jpg", "🐱"), ("dog.png", "🐶"));

        using var zip = ZipFile.OpenRead(zipPath);
        var imageEntries = zip.Entries
            .Where(e => !e.Name.EndsWith(".json"))
            .Select(e => e.Name)
            .ToHashSet();
        var jsonEntries = zip.Entries
            .Where(e => e.Name.EndsWith(".json"))
            .Select(e => e.Name)
            .ToHashSet();

        // Every image must have a matching sidecar
        foreach (var image in imageEntries)
        {
            var expectedSidecar = image + ".json";
            Assert.Contains(expectedSidecar, jsonEntries);
        }

        // Every sidecar must have a matching image
        foreach (var sidecar in jsonEntries)
        {
            var expectedImage = sidecar.Replace(".json", "");
            Assert.Contains(expectedImage, imageEntries);
        }

        // Equal number of images and sidecars
        Assert.Equal(imageEntries.Count, jsonEntries.Count);
    }

    [Fact]
    public void WebpImage_IsBundledCorrectly()
    {
        var imageDir = Path.Combine(_tempDir, "webp-test");
        Directory.CreateDirectory(imageDir);

        // Create a minimal WebP file (RIFF header)
        var webpData = CreateMinimalWebp();
        var imgPath = Path.Combine(imageDir, "funny.webp");
        File.WriteAllBytes(imgPath, webpData);

        var metadata = SidecarService.CreateMetadata(emojis: ["😂"], title: "WebP Meme");
        SidecarService.WriteSidecar(imgPath, metadata, imageDir);

        var zipPath = Path.Combine(_tempDir, "webp-bundle.meme.zip");
        CreateZipFromDir(imageDir, zipPath);

        using var zip = ZipFile.OpenRead(zipPath);
        var imageEntry = zip.GetEntry("funny.webp");
        Assert.NotNull(imageEntry);

        // Verify image data preserved
        using var stream = imageEntry!.Open();
        using var ms = new MemoryStream();
        stream.CopyTo(ms);
        Assert.Equal(webpData, ms.ToArray());

        // Verify sidecar exists and is valid
        var sidecarEntry = zip.GetEntry("funny.webp.json");
        Assert.NotNull(sidecarEntry);

        using var jsonStream = sidecarEntry!.Open();
        using var jsonReader = new StreamReader(jsonStream);
        var json = jsonReader.ReadToEnd();
        var doc = JsonDocument.Parse(json);
        Assert.Equal("WebP Meme", doc.RootElement.GetProperty("title").GetString());
        Assert.Equal(".webp", Path.GetExtension(imageEntry.Name).ToLowerInvariant());
        Assert.Contains(".webp", AppSupportedExtensions);
    }

    [Fact]
    public void PatchBundleExtension_IsRecognizedByApp()
    {
        // The Android app checks: fileName.endsWith(".meme.zip")
        // .patch.meme.zip must end with .meme.zip
        const string patchExtension = ".patch.meme.zip";
        const string appCheck = ".meme.zip";

        Assert.EndsWith(appCheck, patchExtension);
    }

    [Fact]
    public void SidecarSchema_MatchesAppExpectedFields()
    {
        var metadata = SidecarService.CreateMetadata(
            emojis: ["😂", "🔥"],
            title: "Test Meme",
            description: "A test description",
            tags: ["funny", "test"],
            searchPhrases: ["when something is funny"],
            primaryLanguage: "en",
            basedOn: "Drake Hotline Bling");

        var json = JsonSerializer.Serialize(metadata, new JsonSerializerOptions
        {
            WriteIndented = true,
            DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
        });

        var doc = JsonDocument.Parse(json);
        var root = doc.RootElement;

        // Fields the Android app's MemeMetadata expects (all present in CLI output)
        Assert.True(root.TryGetProperty("schemaVersion", out _));
        Assert.True(root.TryGetProperty("emojis", out _));
        Assert.True(root.TryGetProperty("title", out _));
        Assert.True(root.TryGetProperty("description", out _));
        Assert.True(root.TryGetProperty("tags", out _));
        Assert.True(root.TryGetProperty("searchPhrases", out _));
        Assert.True(root.TryGetProperty("primaryLanguage", out _));
        Assert.True(root.TryGetProperty("basedOn", out _));

        // Extra CLI fields that the app ignores via ignoreUnknownKeys=true
        Assert.True(root.TryGetProperty("appVersion", out _));
        Assert.True(root.TryGetProperty("cliVersion", out _));
    }

    [Fact]
    public void SidecarEmojis_AreNonEmpty_AppRequiresAtLeastOne()
    {
        // Android MemeMetadata.init { require(emojis.isNotEmpty()) }
        var metadata = SidecarService.CreateMetadata(emojis: ["😂"]);

        var json = JsonSerializer.Serialize(metadata, new JsonSerializerOptions
        {
            DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
        });

        var doc = JsonDocument.Parse(json);
        var emojis = doc.RootElement.GetProperty("emojis");
        Assert.True(emojis.GetArrayLength() > 0);
    }

    [Fact]
    public void BundleEntryCount_WithinAppLimits()
    {
        // App limit: MAX_ENTRY_COUNT = 10_000
        // A bundle with N images produces 2*N entries (image + sidecar)
        // So max images = 5000
        var (zipPath, imageCount) = CreateBundleWithImages("limit-test",
            ("a.jpg", "😂"), ("b.jpg", "🔥"), ("c.jpg", "🤣"));

        using var zip = ZipFile.OpenRead(zipPath);
        Assert.True(zip.Entries.Count <= 10_000,
            $"Bundle has {zip.Entries.Count} entries, exceeding app limit of 10,000");
        Assert.Equal(imageCount * 2, zip.Entries.Count);
    }

    [Fact]
    public void EntryNames_NoHiddenFiles_NoDirectories()
    {
        // App skips: entry.isDirectory || entryName.startsWith(".") || entryName.contains("/")
        var (zipPath, _) = CreateBundleWithImages("hidden-test", ("visible.jpg", "😂"));

        using var zip = ZipFile.OpenRead(zipPath);
        foreach (var entry in zip.Entries)
        {
            Assert.False(entry.FullName.StartsWith("."),
                $"Entry '{entry.FullName}' starts with '.' — app will skip it");
            Assert.False(entry.FullName.Contains("/"),
                $"Entry '{entry.FullName}' contains '/' — app will skip it");
            Assert.False(entry.FullName.Contains("\\"),
                $"Entry '{entry.FullName}' contains '\\' — invalid path separator");
        }
    }

    // --- Helpers ---

    private (string ZipPath, int ImageCount) CreateBundleWithImages(
        string testName, params (string FileName, string Emoji)[] images)
    {
        var imageDir = Path.Combine(_tempDir, testName);
        Directory.CreateDirectory(imageDir);

        foreach (var (fileName, emoji) in images)
        {
            var imgPath = CreateImage(imageDir, fileName);
            var metadata = SidecarService.CreateMetadata(emojis: [emoji], title: Path.GetFileNameWithoutExtension(fileName));
            SidecarService.WriteSidecar(imgPath, metadata, imageDir);
        }

        var zipPath = Path.Combine(_tempDir, $"{testName}.meme.zip");
        CreateZipFromDir(imageDir, zipPath);

        return (zipPath, images.Length);
    }

    private static void CreateZipFromDir(string imageDir, string zipPath)
    {
        var allImages = SidecarService.GetImagesInFolder(imageDir);
        using var zip = ZipFile.Open(zipPath, ZipArchiveMode.Create);
        foreach (var imagePath in allImages)
        {
            var sidecarPath = Path.Combine(imageDir, Path.GetFileName(imagePath) + ".json");
            if (File.Exists(sidecarPath))
            {
                zip.CreateEntryFromFile(imagePath, Path.GetFileName(imagePath));
                zip.CreateEntryFromFile(sidecarPath, Path.GetFileName(sidecarPath));
            }
        }
    }

    private static string CreateImage(string dir, string name)
    {
        var path = Path.Combine(dir, name);
        byte[] data = Path.GetExtension(name).ToLowerInvariant() switch
        {
            ".png" => [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A],
            ".gif" => [(byte)'G', (byte)'I', (byte)'F', (byte)'8', (byte)'9', (byte)'a'],
            ".webp" => CreateMinimalWebp(),
            _ => [0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10],
        };
        File.WriteAllBytes(path, data);
        return path;
    }

    private static byte[] CreateMinimalWebp()
    {
        // RIFF....WEBP - minimal WebP header
        return [
            0x52, 0x49, 0x46, 0x46, // "RIFF"
            0x24, 0x00, 0x00, 0x00, // file size (36 bytes)
            0x57, 0x45, 0x42, 0x50, // "WEBP"
            0x56, 0x50, 0x38, 0x20, // "VP8 "
            0x18, 0x00, 0x00, 0x00, // chunk size
            0x30, 0x01, 0x00, 0x9D, // VP8 bitstream
            0x01, 0x2A, 0x01, 0x00, // width=1
            0x01, 0x00, 0x01, 0x40, // height=1
            0x25, 0xA4, 0x00, 0x03,
            0x70, 0x00, 0xFE, 0xFB,
            0x94, 0x00, 0x00,
        ];
    }
}
