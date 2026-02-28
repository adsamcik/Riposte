using System.IO.Compression;
using RiposteCli.Models;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;

namespace RiposteCli.Tests;

public class ZipBundlerTests : IDisposable
{
    private readonly string _tempDir;
    private readonly string _imageDir;
    private readonly string _outputDir;

    public ZipBundlerTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), $"riposte-zipbundler-{Guid.NewGuid()}");
        // imageDir must have a parent so ZipBundler can place the zip at folder.Parent
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

    // ── helpers ───────────────────────────────────────────────────────

    private string CreateTestImage(string name)
    {
        var path = Path.Combine(_imageDir, name);
        using var img = new Image<Rgba32>(1, 1);
        img.Save(path);
        return path;
    }

    private void CreateSidecar(string imagePath)
    {
        var sidecarPath = Path.Combine(_outputDir, Path.GetFileName(imagePath) + ".json");
        File.WriteAllText(sidecarPath, """{"emojis":["😂"],"schemaVersion":"1.4"}""");
    }

    private static ImageRebuildPlan MakePlan(
        string imagePath,
        RebuildScope scope = RebuildScope.Skip,
        IReadOnlyList<string>? removedGroups = null,
        string reason = "test")
    {
        return new ImageRebuildPlan
        {
            ImagePath = imagePath,
            Scope = scope,
            RemovedGroups = removedGroups ?? [],
            Reason = reason,
        };
    }

    private static BuildManifest EmptyManifest() => new();

    private static BuildManifest ManifestWith(string fileName, bool hasBundleOptimized)
    {
        var m = new BuildManifest();
        m.Images[fileName] = new ImageManifestEntry
        {
            ContentHash = "abc",
            Model = "gpt-5-mini",
            GeneratedAt = DateTimeOffset.UtcNow.ToString("o"),
            HasBundleOptimized = hasBundleOptimized,
        };
        return m;
    }

    // ── 1. Full mode: selects images with sidecars, ignores those without ──

    [Fact]
    public void SelectImages_FullMode_IncludesImagesWithSidecars()
    {
        var img1 = CreateTestImage("a.png");
        var img2 = CreateTestImage("b.png");
        CreateSidecar(img1);
        // img2 has no sidecar

        var result = ZipBundler.SelectImagesForBundle(
            ZipMode.Full,
            [img1, img2],
            _outputDir,
            plans: [],
            processed: [],
            EmptyManifest());

        Assert.Single(result);
        Assert.Equal(img1, result[0]);
    }

    // ── 2. Patch mode: selects only processed images ──

    [Fact]
    public void SelectImages_PatchMode_IncludesProcessedImages()
    {
        var img1 = CreateTestImage("a.png");
        var img2 = CreateTestImage("b.png");
        CreateSidecar(img1);
        CreateSidecar(img2);

        var manifest = ManifestWith("a.png", hasBundleOptimized: true);
        manifest.Images["b.png"] = new ImageManifestEntry
        {
            ContentHash = "x",
            Model = "gpt-5-mini",
            GeneratedAt = DateTimeOffset.UtcNow.ToString("o"),
            HasBundleOptimized = true,
        };

        var result = ZipBundler.SelectImagesForBundle(
            ZipMode.Patch,
            [img1, img2],
            _outputDir,
            plans: [MakePlan(img1), MakePlan(img2)],
            processed: [(img1, img1 + ".json")],
            manifest);

        Assert.Single(result);
        Assert.Equal(img1, result[0]);
    }

    // ── 3. Patch mode: selects images that need stripping ──

    [Fact]
    public void SelectImages_PatchMode_IncludesImagesNeedingStripping()
    {
        var img1 = CreateTestImage("strip.png");

        var manifest = ManifestWith("strip.png", hasBundleOptimized: true);

        var plan = MakePlan(img1, removedGroups: ["cultural"]);

        var result = ZipBundler.SelectImagesForBundle(
            ZipMode.Patch,
            [img1],
            _outputDir,
            plans: [plan],
            processed: [],
            manifest);

        Assert.Single(result);
        Assert.Equal(img1, result[0]);
    }

    // ── 4. Patch mode: selects images never previously bundled ──

    [Fact]
    public void SelectImages_PatchMode_IncludesNeverBundledImages()
    {
        var img1 = CreateTestImage("new.png");

        // Image is in manifest but HasBundleOptimized = false
        var manifest = ManifestWith("new.png", hasBundleOptimized: false);

        var result = ZipBundler.SelectImagesForBundle(
            ZipMode.Patch,
            [img1],
            _outputDir,
            plans: [MakePlan(img1)],
            processed: [],
            manifest);

        Assert.Single(result);
        Assert.Equal(img1, result[0]);
    }

    [Fact]
    public void SelectImages_PatchMode_IncludesImageNotInManifest()
    {
        var img1 = CreateTestImage("brand-new.png");

        var result = ZipBundler.SelectImagesForBundle(
            ZipMode.Patch,
            [img1],
            _outputDir,
            plans: [MakePlan(img1)],
            processed: [],
            EmptyManifest());

        Assert.Single(result);
        Assert.Equal(img1, result[0]);
    }

    // ── 5. Patch mode: no changes returns empty list ──

    [Fact]
    public void SelectImages_PatchMode_NoChanges_ReturnsEmpty()
    {
        var img1 = CreateTestImage("unchanged.png");

        // Already bundled, no processing, no stripping
        var manifest = ManifestWith("unchanged.png", hasBundleOptimized: true);

        var result = ZipBundler.SelectImagesForBundle(
            ZipMode.Patch,
            [img1],
            _outputDir,
            plans: [MakePlan(img1)],
            processed: [],
            manifest);

        Assert.Empty(result);
    }

    // ── 6. Empty image list returns empty result ──

    [Fact]
    public void CreateBundle_EmptyImageList_ReturnsZeroImageCount()
    {
        var folder = new DirectoryInfo(_imageDir);

        var result = ZipBundler.CreateBundle(
            ZipMode.Full,
            folder,
            _outputDir,
            allImages: [],
            plans: [],
            processed: [],
            bundleOptimizedMap: null,
            EmptyManifest());

        Assert.Equal(0, result.ImageCount);
        Assert.Empty(result.BundledImagePaths);
    }

    // ── 7. Zip file naming ──

    [Fact]
    public void CreateBundle_FullMode_ZipNamedMemeZip()
    {
        var folder = new DirectoryInfo(_imageDir);

        var result = ZipBundler.CreateBundle(
            ZipMode.Full,
            folder,
            _outputDir,
            allImages: [],
            plans: [],
            processed: [],
            bundleOptimizedMap: null,
            EmptyManifest());

        Assert.EndsWith(".meme.zip", result.ZipPath);
        Assert.DoesNotContain(".patch.", result.ZipPath);
    }

    [Fact]
    public void CreateBundle_PatchMode_ZipNamedPatchMemeZip()
    {
        var folder = new DirectoryInfo(_imageDir);

        var result = ZipBundler.CreateBundle(
            ZipMode.Patch,
            folder,
            _outputDir,
            allImages: [],
            plans: [],
            processed: [],
            bundleOptimizedMap: null,
            EmptyManifest());

        Assert.EndsWith(".patch.meme.zip", result.ZipPath);
    }

    // ── 8. Existing zip is overwritten without error ──

    [Fact]
    public void CreateBundle_ExistingZip_OverwrittenWithoutError()
    {
        var img = CreateTestImage("over.png");
        CreateSidecar(img);
        var folder = new DirectoryInfo(_imageDir);

        // Pre-populate bundleOptimizedMap so ImageOptimizer is never called
        var optimizedMap = new Dictionary<string, string> { [img] = img };

        // First call creates the zip
        var result1 = ZipBundler.CreateBundle(
            ZipMode.Full, folder, _outputDir,
            allImages: [img],
            plans: [],
            processed: [],
            bundleOptimizedMap: optimizedMap,
            EmptyManifest());

        Assert.True(File.Exists(result1.ZipPath));

        // Second call overwrites
        var result2 = ZipBundler.CreateBundle(
            ZipMode.Full, folder, _outputDir,
            allImages: [img],
            plans: [],
            processed: [],
            bundleOptimizedMap: optimizedMap,
            EmptyManifest());

        Assert.True(File.Exists(result2.ZipPath));
        Assert.Equal(1, result2.ImageCount);
    }

    // ── 9. RecordBundledImages updates manifest ──

    [Fact]
    public void RecordBundledImages_SetsHasBundleOptimized()
    {
        var manifest = new BuildManifest();
        manifest.Images["a.png"] = new ImageManifestEntry
        {
            ContentHash = "h1",
            Model = "gpt-5-mini",
            GeneratedAt = DateTimeOffset.UtcNow.ToString("o"),
            HasBundleOptimized = false,
        };
        manifest.Images["b.png"] = new ImageManifestEntry
        {
            ContentHash = "h2",
            Model = "gpt-5-mini",
            GeneratedAt = DateTimeOffset.UtcNow.ToString("o"),
            HasBundleOptimized = false,
        };

        ZipBundler.RecordBundledImages(manifest, ["/imgs/a.png", "/imgs/b.png"]);

        Assert.True(manifest.Images["a.png"].HasBundleOptimized);
        Assert.True(manifest.Images["b.png"].HasBundleOptimized);
    }

    [Fact]
    public void RecordBundledImages_IgnoresImagesNotInManifest()
    {
        var manifest = EmptyManifest();

        // Should not throw even though "missing.png" is not in manifest
        var ex = Record.Exception(() =>
            ZipBundler.RecordBundledImages(manifest, ["/imgs/missing.png"]));

        Assert.Null(ex);
    }

    // ── 10. SelectImagesForBundle throws on invalid mode ──

    [Fact]
    public void SelectImages_InvalidMode_Throws()
    {
        Assert.Throws<ArgumentOutOfRangeException>(() =>
            ZipBundler.SelectImagesForBundle(
                (ZipMode)999,
                allImages: [],
                outputDir: _outputDir,
                plans: [],
                processed: [],
                EmptyManifest()));
    }
}
