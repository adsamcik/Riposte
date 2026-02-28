using RiposteCli.Models;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;
using System.IO.Compression;

namespace RiposteCli.Tests;

public class ZipBundlerExtendedTests : IDisposable
{
    private readonly string _tempDir;
    private readonly string _imageDir;
    private readonly string _outputDir;

    public ZipBundlerExtendedTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), $"riposte-zipbundler-extended-{Guid.NewGuid()}");
        _imageDir = Path.Combine(_tempDir, "memes");
        _outputDir = Path.Combine(_tempDir, "output");
        Directory.CreateDirectory(_imageDir);
        Directory.CreateDirectory(_outputDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempDir))
            Directory.Delete(_tempDir, true);
    }

    [Fact]
    public void CreateBundle_NullBundleOptimizedMap_OptimizesOnDemand()
    {
        var image = CreateImage("null-map.png");
        CreateSidecar(image);

        var result = ZipBundler.CreateBundle(
            ZipMode.Full,
            new DirectoryInfo(_imageDir),
            _outputDir,
            allImages: [image],
            plans: [],
            processed: [],
            bundleOptimizedMap: null,
            manifest: new BuildManifest());

        Assert.Equal(1, result.ImageCount);
        Assert.True(File.Exists(result.ZipPath));

        using var zip = ZipFile.OpenRead(result.ZipPath);
        var names = zip.Entries.Select(e => e.FullName).OrderBy(n => n).ToList();
        Assert.Equal(2, names.Count);
        Assert.Contains("null-map.webp", names);
        Assert.Contains("null-map.webp.json", names);
    }

    [Fact]
    public void CreateBundle_PartialBundleOptimizedMap_OptimizesMissingImages()
    {
        var first = CreateImage("first.png");
        var second = CreateImage("second.png");
        CreateSidecar(first);
        CreateSidecar(second);

        var map = new Dictionary<string, string>
        {
            [first] = ImageOptimizer.OptimizeForBundle(first, _outputDir),
        };

        var result = ZipBundler.CreateBundle(
            ZipMode.Full,
            new DirectoryInfo(_imageDir),
            _outputDir,
            allImages: [first, second],
            plans: [],
            processed: [],
            bundleOptimizedMap: map,
            manifest: new BuildManifest());

        Assert.Equal(2, result.ImageCount);
        Assert.True(map.ContainsKey(second));
        Assert.EndsWith(".webp", map[second], StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void CreateBundle_PatchMode_IncludesStrippedImages()
    {
        var image = CreateImage("strip-me.png");
        CreateSidecar(image);

        var manifest = ManifestWith("strip-me.png", hasBundleOptimized: true);
        var plans = new[]
        {
            new ImageRebuildPlan
            {
                ImagePath = image,
                Scope = RebuildScope.Skip,
                RemovedGroups = ["cultural"],
            },
        };
        var map = new Dictionary<string, string> { [image] = image };

        var result = ZipBundler.CreateBundle(
            ZipMode.Patch,
            new DirectoryInfo(_imageDir),
            _outputDir,
            allImages: [image],
            plans: plans,
            processed: [],
            bundleOptimizedMap: map,
            manifest: manifest);

        Assert.Equal(1, result.ImageCount);
        Assert.Single(result.BundledImagePaths);
        Assert.Equal(image, result.BundledImagePaths[0]);
    }

    [Fact]
    public void CreateBundle_PatchMode_IncludesImagesNeverBundled()
    {
        var image = CreateImage("never-bundled.png");
        CreateSidecar(image);

        var manifest = ManifestWith("never-bundled.png", hasBundleOptimized: false);
        var plans = new[]
        {
            new ImageRebuildPlan
            {
                ImagePath = image,
                Scope = RebuildScope.Skip,
            },
        };
        var map = new Dictionary<string, string> { [image] = image };

        var result = ZipBundler.CreateBundle(
            ZipMode.Patch,
            new DirectoryInfo(_imageDir),
            _outputDir,
            allImages: [image],
            plans: plans,
            processed: [],
            bundleOptimizedMap: map,
            manifest: manifest);

        Assert.Equal(1, result.ImageCount);
        Assert.Contains(image, result.BundledImagePaths);
    }

    [Fact]
    public void RecordBundledImages_EmptyList_NoOp()
    {
        var manifest = ManifestWith("unchanged.png", hasBundleOptimized: false);

        ZipBundler.RecordBundledImages(manifest, []);

        Assert.False(manifest.Images["unchanged.png"].HasBundleOptimized);
    }

    [Fact]
    public void CreateBundle_PatchMode_ImageWithoutSidecar_IsSilentlySkipped()
    {
        var image = CreateImage("no-sidecar.png");
        var map = new Dictionary<string, string> { [image] = image };
        var plans = new[]
        {
            new ImageRebuildPlan
            {
                ImagePath = image,
                Scope = RebuildScope.Skip,
            },
        };

        var result = ZipBundler.CreateBundle(
            ZipMode.Patch,
            new DirectoryInfo(_imageDir),
            _outputDir,
            allImages: [image],
            plans: plans,
            processed: [],
            bundleOptimizedMap: map,
            manifest: new BuildManifest());

        Assert.Equal(0, result.ImageCount);
        Assert.Empty(result.BundledImagePaths);
        Assert.True(File.Exists(result.ZipPath));

        using var zip = ZipFile.OpenRead(result.ZipPath);
        Assert.Empty(zip.Entries);
    }

    [Fact]
    public void CreateBundle_FullMode_RootDirectoryWithoutParent_ThrowsArgumentException()
    {
        var root = new DirectoryInfo(Path.GetPathRoot(_tempDir)!);
        Assert.Null(root.Parent);

        var ex = Assert.Throws<ArgumentException>(() =>
            ZipBundler.CreateBundle(
                ZipMode.Full,
                root,
                _outputDir,
                allImages: [],
                plans: [],
                processed: [],
                bundleOptimizedMap: null,
                manifest: new BuildManifest()));

        Assert.Contains("no parent directory", ex.Message);
        Assert.Equal("folder", ex.ParamName);
    }

    [Fact]
    public void CreateBundle_ZipContainsExpectedEntriesAndCount()
    {
        var first = CreateImage("entries-a.png");
        var second = CreateImage("entries-b.png");
        CreateSidecar(first);
        CreateSidecar(second);

        var map = new Dictionary<string, string>
        {
            [first] = first,
            [second] = second,
        };

        var result = ZipBundler.CreateBundle(
            ZipMode.Full,
            new DirectoryInfo(_imageDir),
            _outputDir,
            allImages: [first, second],
            plans: [],
            processed: [],
            bundleOptimizedMap: map,
            manifest: new BuildManifest());

        using var zip = ZipFile.OpenRead(result.ZipPath);
        var entries = zip.Entries.Select(e => e.FullName).OrderBy(n => n).ToList();

        Assert.Equal(2, result.ImageCount);
        Assert.Equal(4, entries.Count);
        Assert.Equal(
            new[]
            {
                "entries-a.png",
                "entries-a.png.json",
                "entries-b.png",
                "entries-b.png.json",
            },
            entries);
    }

    [Fact]
    public void CreateBundle_DuplicateEntryNames_SecondImageSkipped()
    {
        var first = CreateImage("dup-a.png");
        var second = CreateImage("dup-b.png");
        CreateSidecar(first);
        CreateSidecar(second);

        // Both source images map to the same optimized file → same ZIP entry name
        var map = new Dictionary<string, string>
        {
            [first] = first,
            [second] = first,
        };

        var result = ZipBundler.CreateBundle(
            ZipMode.Full,
            new DirectoryInfo(_imageDir),
            _outputDir,
            allImages: [first, second],
            plans: [],
            processed: [],
            bundleOptimizedMap: map,
            manifest: new BuildManifest());

        Assert.Equal(1, result.ImageCount);
        Assert.Single(result.BundledImagePaths);

        using var zip = ZipFile.OpenRead(result.ZipPath);
        var imageEntries = zip.Entries.Where(e => !e.FullName.EndsWith(".json")).ToList();
        Assert.Single(imageEntries);
    }

    [Fact]
    public void CreateBundle_EndToEnd_ProducesAndroidCompatibleZip()
    {
        var img1 = CreateImage("compat-a.png");
        var img2 = CreateImage("compat-b.png");
        CreateSidecar(img1);
        CreateSidecar(img2);

        var result = ZipBundler.CreateBundle(
            ZipMode.Full,
            new DirectoryInfo(_imageDir),
            _outputDir,
            allImages: [img1, img2],
            plans: [],
            processed: [],
            bundleOptimizedMap: null,
            manifest: new BuildManifest());

        Assert.Equal(2, result.ImageCount);
        Assert.True(File.Exists(result.ZipPath));

        using var zip = ZipFile.OpenRead(result.ZipPath);
        Assert.Equal(4, zip.Entries.Count);

        var imageEntries = zip.Entries.Where(e => !e.FullName.EndsWith(".json")).ToList();
        var jsonEntries = zip.Entries.Where(e => e.FullName.EndsWith(".json")).ToList();

        // Flat entries: no path separators
        foreach (var entry in zip.Entries)
        {
            Assert.DoesNotContain("/", entry.FullName);
            Assert.DoesNotContain("\\", entry.FullName);
        }

        // All images optimized to .webp
        foreach (var entry in imageEntries)
            Assert.EndsWith(".webp", entry.Name, StringComparison.OrdinalIgnoreCase);

        // Paired: each image has a matching .json sidecar
        foreach (var imgEntry in imageEntries)
            Assert.Contains(zip.Entries, e => e.FullName == imgEntry.FullName + ".json");

        // JSON sidecars are valid and contain required fields
        foreach (var jsonEntry in jsonEntries)
        {
            using var stream = jsonEntry.Open();
            using var reader = new StreamReader(stream);
            var content = reader.ReadToEnd();
            var doc = System.Text.Json.JsonDocument.Parse(content);
            Assert.True(doc.RootElement.TryGetProperty("emojis", out var emojis));
            Assert.True(emojis.GetArrayLength() > 0);
        }
    }

    [Fact]
    public void CreateBundle_PatchMode_OnlyStrippedImages_ZipContainsCorrectEntries()
    {
        var img1 = CreateImage("strip-a.png");
        var img2 = CreateImage("strip-b.png");
        CreateSidecar(img1);
        CreateSidecar(img2);

        var manifest = ManifestWith("strip-a.png", hasBundleOptimized: true);
        manifest.Images["strip-b.png"] = new ImageManifestEntry
        {
            ContentHash = "hash2",
            Model = "gpt-5-mini",
            GeneratedAt = DateTimeOffset.UtcNow.ToString("o"),
            HasBundleOptimized = true,
        };

        var plans = new[]
        {
            new ImageRebuildPlan
            {
                ImagePath = img1,
                Scope = RebuildScope.Skip,
                RemovedGroups = ["cultural"],
            },
            new ImageRebuildPlan
            {
                ImagePath = img2,
                Scope = RebuildScope.Skip,
                RemovedGroups = ["localize_de"],
            },
        };
        var map = new Dictionary<string, string>
        {
            [img1] = img1,
            [img2] = img2,
        };

        var result = ZipBundler.CreateBundle(
            ZipMode.Patch,
            new DirectoryInfo(_imageDir),
            _outputDir,
            allImages: [img1, img2],
            plans: plans,
            processed: [],
            bundleOptimizedMap: map,
            manifest: manifest);

        Assert.Equal(2, result.ImageCount);
        Assert.True(File.Exists(result.ZipPath));

        using var zip = ZipFile.OpenRead(result.ZipPath);
        Assert.Equal(4, zip.Entries.Count);

        var entryNames = zip.Entries.Select(e => e.FullName).OrderBy(n => n).ToList();
        Assert.Contains("strip-a.png", entryNames);
        Assert.Contains("strip-a.png.json", entryNames);
        Assert.Contains("strip-b.png", entryNames);
        Assert.Contains("strip-b.png.json", entryNames);
    }

    [Fact]
    public void GetSystemPrompt_WithThreeAdditionalLanguages_FormatsAdditionalLanguagesList()
    {
        var prompt = Prompts.GetSystemPrompt(["en", "cs", "de", "fr"]);

        Assert.Contains("ADDITIONAL LANGUAGES: Czech (cs), German (de), French (fr)", prompt);
    }

    [Fact]
    public void GetPartialPrompt_LocalizationLanguageNotInLanguagesList_StillBuildsValidSpec()
    {
        var prompt = Prompts.GetPartialPrompt([PromptHasher.LocalizationGroup("xx")], ["en"]);

        Assert.Contains("\"localizations\"", prompt);
        Assert.Contains("\"xx\"", prompt);
        Assert.Contains("all in xx", prompt);
        Assert.Contains("valid JSON", prompt);
    }

    private string CreateImage(string name)
    {
        var path = Path.Combine(_imageDir, name);
        using var image = new Image<Rgba32>(4, 4);
        image.Save(path);
        return path;
    }

    private void CreateSidecar(string imagePath)
    {
        var metadata = new SidecarMetadata
        {
            Emojis = ["😂"],
            Title = "test",
            Description = "test description",
            Tags = ["test"],
            SearchPhrases = ["test search"],
        };
        SidecarService.WriteSidecar(imagePath, metadata, _outputDir);
    }

    private static BuildManifest ManifestWith(string fileName, bool hasBundleOptimized)
    {
        var manifest = new BuildManifest();
        manifest.Images[fileName] = new ImageManifestEntry
        {
            ContentHash = "hash",
            Model = "gpt-5-mini",
            GeneratedAt = DateTimeOffset.UtcNow.ToString("o"),
            HasBundleOptimized = hasBundleOptimized,
        };
        return manifest;
    }
}
