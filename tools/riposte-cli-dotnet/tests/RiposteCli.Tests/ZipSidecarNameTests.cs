using System.IO.Compression;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;
using RiposteCli.Services;

namespace RiposteCli.Tests;

/// <summary>
/// Tests for sidecar-to-image name matching in ZIP bundles.
/// Covers collision detection, extension disambiguation, dots in filenames,
/// and the full optimize → bundle pipeline.
/// </summary>
public class ZipSidecarNameTests : IDisposable
{
    private readonly string _tempDir;
    private readonly string _imageDir;
    private readonly string _outputDir;

    public ZipSidecarNameTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), $"riposte-sidecar-test-{Guid.NewGuid()}");
        _imageDir = Path.Combine(_tempDir, "images");
        _outputDir = Path.Combine(_tempDir, "output");
        Directory.CreateDirectory(_imageDir);
        Directory.CreateDirectory(_outputDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempDir))
            Directory.Delete(_tempDir, true);
    }

    private string CreateTestImage(string dir, string name, int width = 100, int height = 100)
    {
        var path = Path.Combine(dir, name);
        using var img = new Image<Rgba32>(width, height);
        img.Save(path);
        return path;
    }

    // ─── ResolveUniqueWebpNames unit tests ───────────────────────────────

    [Fact]
    public void ResolveUniqueWebpNames_SingleImage_UsesPlainStem()
    {
        var paths = new[] { @"C:\imgs\cat.png" };

        var result = ImageOptimizer.ResolveUniqueWebpNames(paths);

        Assert.Single(result);
        Assert.Equal("cat.webp", result[paths[0]]);
    }

    [Fact]
    public void ResolveUniqueWebpNames_DifferentStems_NoCollision()
    {
        var paths = new[] { @"C:\imgs\cat.png", @"C:\imgs\dog.jpg" };

        var result = ImageOptimizer.ResolveUniqueWebpNames(paths);

        Assert.Equal("cat.webp", result[paths[0]]);
        Assert.Equal("dog.webp", result[paths[1]]);
    }

    [Fact]
    public void ResolveUniqueWebpNames_SameStemDifferentExtensions_Disambiguates()
    {
        var paths = new[] { @"C:\imgs\cat.png", @"C:\imgs\cat.jpg" };

        var result = ImageOptimizer.ResolveUniqueWebpNames(paths);

        Assert.Equal("cat_png.webp", result[paths[0]]);
        Assert.Equal("cat_jpg.webp", result[paths[1]]);
    }

    [Fact]
    public void ResolveUniqueWebpNames_ThreeWayCollision_AllDisambiguated()
    {
        var paths = new[]
        {
            @"C:\imgs\meme.png",
            @"C:\imgs\meme.jpg",
            @"C:\imgs\meme.bmp",
        };

        var result = ImageOptimizer.ResolveUniqueWebpNames(paths);

        Assert.Equal("meme_png.webp", result[paths[0]]);
        Assert.Equal("meme_jpg.webp", result[paths[1]]);
        Assert.Equal("meme_bmp.webp", result[paths[2]]);
        // All names are unique
        Assert.Equal(3, result.Values.Distinct(StringComparer.OrdinalIgnoreCase).Count());
    }

    [Fact]
    public void ResolveUniqueWebpNames_AlreadyWebp_UsesPlainStem()
    {
        // Only one image with this stem → no collision even though it's .webp
        var paths = new[] { @"C:\imgs\cat.webp" };

        var result = ImageOptimizer.ResolveUniqueWebpNames(paths);

        Assert.Equal("cat.webp", result[paths[0]]);
    }

    [Fact]
    public void ResolveUniqueWebpNames_WebpCollidesWithPng_Disambiguates()
    {
        var paths = new[] { @"C:\imgs\cat.webp", @"C:\imgs\cat.png" };

        var result = ImageOptimizer.ResolveUniqueWebpNames(paths);

        Assert.Equal("cat_webp.webp", result[paths[0]]);
        Assert.Equal("cat_png.webp", result[paths[1]]);
    }

    [Fact]
    public void ResolveUniqueWebpNames_DotsInFilename_HandlesCorrectly()
    {
        var paths = new[] { @"C:\imgs\my.image.v2.png" };

        var result = ImageOptimizer.ResolveUniqueWebpNames(paths);

        // Path.GetFileNameWithoutExtension("my.image.v2.png") = "my.image.v2"
        Assert.Equal("my.image.v2.webp", result[paths[0]]);
    }

    [Fact]
    public void ResolveUniqueWebpNames_DotsInFilename_CollisionDisambiguates()
    {
        var paths = new[]
        {
            @"C:\imgs\my.image.v2.png",
            @"C:\imgs\my.image.v2.jpg",
        };

        var result = ImageOptimizer.ResolveUniqueWebpNames(paths);

        Assert.Equal("my.image.v2_png.webp", result[paths[0]]);
        Assert.Equal("my.image.v2_jpg.webp", result[paths[1]]);
    }

    [Fact]
    public void ResolveUniqueWebpNames_CaseInsensitiveCollision_Disambiguates()
    {
        // On case-insensitive FS, Cat.PNG and cat.png would collide
        var paths = new[] { @"C:\imgs\Cat.PNG", @"C:\imgs\cat.jpg" };

        var result = ImageOptimizer.ResolveUniqueWebpNames(paths);

        // Both stems are "cat" (case-insensitive) → collision
        Assert.Equal("Cat_PNG.webp", result[paths[0]]);
        Assert.Equal("cat_jpg.webp", result[paths[1]]);
    }

    [Theory]
    [InlineData(".jpg")]
    [InlineData(".jpeg")]
    [InlineData(".png")]
    [InlineData(".webp")]
    [InlineData(".gif")]
    [InlineData(".bmp")]
    [InlineData(".tiff")]
    [InlineData(".heic")]
    public void ResolveUniqueWebpNames_AllFormats_ProducesWebpName(string ext)
    {
        var paths = new[] { $@"C:\imgs\test{ext}" };

        var result = ImageOptimizer.ResolveUniqueWebpNames(paths);

        Assert.Equal("test.webp", result[paths[0]]);
    }

    [Fact]
    public void ResolveUniqueWebpNames_EmptyInput_ReturnsEmpty()
    {
        var result = ImageOptimizer.ResolveUniqueWebpNames(Array.Empty<string>());

        Assert.Empty(result);
    }

    [Fact]
    public void ResolveUniqueWebpNames_MixedCollisionAndNon_CorrectResults()
    {
        var paths = new[]
        {
            @"C:\imgs\cat.png",
            @"C:\imgs\cat.jpg",
            @"C:\imgs\dog.png",
            @"C:\imgs\bird.gif",
        };

        var result = ImageOptimizer.ResolveUniqueWebpNames(paths);

        // cat collides → disambiguated
        Assert.Equal("cat_png.webp", result[paths[0]]);
        Assert.Equal("cat_jpg.webp", result[paths[1]]);
        // dog and bird don't collide → plain names
        Assert.Equal("dog.webp", result[paths[2]]);
        Assert.Equal("bird.webp", result[paths[3]]);
    }

    // ─── OptimizeBatchForBundle integration tests (real images) ──────────

    [Fact]
    public void OptimizeBatchForBundle_SameStemCollision_ProducesDistinctFiles()
    {
        var catPng = CreateTestImage(_imageDir, "cat.png");
        var catJpg = CreateTestImage(_imageDir, "cat.jpg");

        var result = ImageOptimizer.OptimizeBatchForBundle(
            new[] { catPng, catJpg }, _outputDir);

        // Both outputs exist and are different files
        Assert.True(File.Exists(result[catPng]));
        Assert.True(File.Exists(result[catJpg]));
        Assert.NotEqual(result[catPng], result[catJpg]);

        // Both are WebP
        Assert.EndsWith("_png.webp", Path.GetFileName(result[catPng]));
        Assert.EndsWith("_jpg.webp", Path.GetFileName(result[catJpg]));
    }

    [Fact]
    public void OptimizeBatchForBundle_NoCollision_UsesPlainNames()
    {
        var cat = CreateTestImage(_imageDir, "cat.png");
        var dog = CreateTestImage(_imageDir, "dog.jpg");

        var result = ImageOptimizer.OptimizeBatchForBundle(
            new[] { cat, dog }, _outputDir);

        Assert.Equal("cat.webp", Path.GetFileName(result[cat]));
        Assert.Equal("dog.webp", Path.GetFileName(result[dog]));
    }

    [Fact]
    public void OptimizeBatchForBundle_AlreadyWebp_Reencodes()
    {
        var catWebp = CreateTestImage(_imageDir, "cat.webp");

        var result = ImageOptimizer.OptimizeBatchForBundle(
            new[] { catWebp }, _outputDir);

        Assert.True(File.Exists(result[catWebp]));
        Assert.Equal("cat.webp", Path.GetFileName(result[catWebp]));
        // Output is in the output dir, not the source dir
        Assert.StartsWith(_outputDir, result[catWebp]);
    }

    [Fact]
    public void OptimizeBatchForBundle_DotsInFilename_PreservesStemCorrectly()
    {
        var img = CreateTestImage(_imageDir, "my.image.v2.png");

        var result = ImageOptimizer.OptimizeBatchForBundle(
            new[] { img }, _outputDir);

        Assert.Equal("my.image.v2.webp", Path.GetFileName(result[img]));
    }

    // ─── Full ZIP bundle pipeline tests ─────────────────────────────────

    [Fact]
    public void ZipBundle_SidecarRenamedToMatchOptimizedImage()
    {
        // cat.png → sidecar is cat.png.json → optimized to cat.webp → ZIP has cat.webp + cat.webp.json
        var imgPath = CreateTestImage(_imageDir, "cat.png");
        var metadata = SidecarService.CreateMetadata(emojis: ["😺"], title: "Cat");
        SidecarService.WriteSidecar(imgPath, metadata, _outputDir);

        var optimizedMap = ImageOptimizer.OptimizeBatchForBundle(new[] { imgPath }, _outputDir);

        var zipPath = Path.Combine(_tempDir, "test.meme.zip");
        using (var zip = ZipFile.Open(zipPath, ZipArchiveMode.Create))
        {
            AddToZip(zip, imgPath, _outputDir, optimizedMap);
        }

        using var readZip = ZipFile.OpenRead(zipPath);
        var entries = readZip.Entries.Select(e => e.Name).ToList();
        Assert.Contains("cat.webp", entries);
        Assert.Contains("cat.webp.json", entries);
        Assert.DoesNotContain("cat.png.json", entries);
    }

    [Fact]
    public void ZipBundle_AlreadyWebpSource_SidecarNameUnchanged()
    {
        // cat.webp → sidecar is cat.webp.json → optimized to cat.webp → ZIP has cat.webp + cat.webp.json
        var imgPath = CreateTestImage(_imageDir, "cat.webp");
        var metadata = SidecarService.CreateMetadata(emojis: ["😺"]);
        SidecarService.WriteSidecar(imgPath, metadata, _outputDir);

        var optimizedMap = ImageOptimizer.OptimizeBatchForBundle(new[] { imgPath }, _outputDir);

        var zipPath = Path.Combine(_tempDir, "test.meme.zip");
        using (var zip = ZipFile.Open(zipPath, ZipArchiveMode.Create))
        {
            AddToZip(zip, imgPath, _outputDir, optimizedMap);
        }

        using var readZip = ZipFile.OpenRead(zipPath);
        var entries = readZip.Entries.Select(e => e.Name).ToList();
        Assert.Contains("cat.webp", entries);
        Assert.Contains("cat.webp.json", entries);
        Assert.Equal(2, entries.Count);
    }

    [Fact]
    public void ZipBundle_SameStemCollision_BothImagesInZipWithDistinctNames()
    {
        // cat.png + cat.jpg → both have sidecars → optimized to cat_png.webp + cat_jpg.webp
        var catPng = CreateTestImage(_imageDir, "cat.png");
        var catJpg = CreateTestImage(_imageDir, "cat.jpg");

        var metadata = SidecarService.CreateMetadata(emojis: ["😺"]);
        SidecarService.WriteSidecar(catPng, metadata, _outputDir);
        SidecarService.WriteSidecar(catJpg, metadata, _outputDir);

        var optimizedMap = ImageOptimizer.OptimizeBatchForBundle(
            new[] { catPng, catJpg }, _outputDir);

        var zipPath = Path.Combine(_tempDir, "test.meme.zip");
        using (var zip = ZipFile.Open(zipPath, ZipArchiveMode.Create))
        {
            AddToZip(zip, catPng, _outputDir, optimizedMap);
            AddToZip(zip, catJpg, _outputDir, optimizedMap);
        }

        using var readZip = ZipFile.OpenRead(zipPath);
        var entries = readZip.Entries.Select(e => e.Name).OrderBy(e => e).ToList();
        Assert.Equal(4, entries.Count); // 2 images + 2 sidecars
        Assert.Contains("cat_png.webp", entries);
        Assert.Contains("cat_png.webp.json", entries);
        Assert.Contains("cat_jpg.webp", entries);
        Assert.Contains("cat_jpg.webp.json", entries);
    }

    [Fact]
    public void ZipBundle_DotsInFilename_SidecarMatchesOptimized()
    {
        var imgPath = CreateTestImage(_imageDir, "my.meme.v2.png");
        var metadata = SidecarService.CreateMetadata(emojis: ["🔥"]);
        SidecarService.WriteSidecar(imgPath, metadata, _outputDir);

        var optimizedMap = ImageOptimizer.OptimizeBatchForBundle(new[] { imgPath }, _outputDir);

        var zipPath = Path.Combine(_tempDir, "test.meme.zip");
        using (var zip = ZipFile.Open(zipPath, ZipArchiveMode.Create))
        {
            AddToZip(zip, imgPath, _outputDir, optimizedMap);
        }

        using var readZip = ZipFile.OpenRead(zipPath);
        var entries = readZip.Entries.Select(e => e.Name).ToList();
        Assert.Contains("my.meme.v2.webp", entries);
        Assert.Contains("my.meme.v2.webp.json", entries);
    }

    [Fact]
    public void ZipBundle_SidecarContentPreservedAfterRename()
    {
        var imgPath = CreateTestImage(_imageDir, "test.png");
        var metadata = SidecarService.CreateMetadata(
            emojis: ["😂", "🐱"],
            title: "Funny Cat",
            description: "A very funny cat meme");
        SidecarService.WriteSidecar(imgPath, metadata, _outputDir);

        var optimizedMap = ImageOptimizer.OptimizeBatchForBundle(new[] { imgPath }, _outputDir);

        var zipPath = Path.Combine(_tempDir, "test.meme.zip");
        using (var zip = ZipFile.Open(zipPath, ZipArchiveMode.Create))
        {
            AddToZip(zip, imgPath, _outputDir, optimizedMap);
        }

        using var readZip = ZipFile.OpenRead(zipPath);
        var sidecarEntry = readZip.GetEntry("test.webp.json");
        Assert.NotNull(sidecarEntry);

        using var stream = sidecarEntry.Open();
        using var reader = new StreamReader(stream);
        var json = reader.ReadToEnd();
        Assert.Contains("Funny Cat", json);
        Assert.Contains("very funny cat meme", json);
    }

    // ─── ZipBundler entry dedup defense-in-depth ────────────────────────

    [Fact]
    public void ZipBundler_DuplicateEntryNames_DoesNotThrow()
    {
        // Simulate the old bug: two images mapping to same optimized file
        var img1 = CreateTestImage(_imageDir, "a.png");
        var img2 = CreateTestImage(_imageDir, "b.png");

        var metadata = SidecarService.CreateMetadata(emojis: ["😺"]);
        SidecarService.WriteSidecar(img1, metadata, _outputDir);
        SidecarService.WriteSidecar(img2, metadata, _outputDir);

        // Manually create an optimized map where both point to same output
        var sameOutput = ImageOptimizer.OptimizeForBundle(img1, _outputDir);
        var badMap = new Dictionary<string, string>
        {
            [img1] = sameOutput,
            [img2] = sameOutput, // deliberate collision
        };

        var zipPath = Path.Combine(_tempDir, "dedup.meme.zip");
        using (var zip = ZipFile.Open(zipPath, ZipArchiveMode.Create))
        {
            var usedEntryNames = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

            foreach (var imagePath in new[] { img1, img2 })
            {
                var sidecarPath = SidecarService.ResolveSidecarPath(imagePath, _outputDir);
                if (sidecarPath == null)
                    continue;

                var bundlePath = badMap.TryGetValue(imagePath, out var optPath)
                    ? optPath : imagePath;
                var bundleImageName = Path.GetFileName(bundlePath);

                if (!usedEntryNames.Add(bundleImageName))
                    continue; // skip duplicate — this is the defense-in-depth guard

                zip.CreateEntryFromFile(bundlePath, bundleImageName);
                var bundleSidecarName = bundleImageName + ".json";
                zip.CreateEntryFromFile(sidecarPath, bundleSidecarName);
            }
        }

        // Should not throw, and should only contain one copy
        using var readZip = ZipFile.OpenRead(zipPath);
        var entries = readZip.Entries.Select(e => e.Name).ToList();
        Assert.Equal(2, entries.Count); // 1 image + 1 sidecar (second was skipped)
    }

    // ─── Helper: replicate ZipBundler's per-image ZIP logic ─────────────

    /// <summary>
    /// Replicates the per-image ZIP entry creation from ZipBundler.CreateBundle.
    /// </summary>
    private static void AddToZip(
        ZipArchive zip,
        string imagePath,
        string outputDir,
        Dictionary<string, string> optimizedMap)
    {
        var sidecarPath = SidecarService.ResolveSidecarPath(imagePath, outputDir);
        if (sidecarPath == null)
            return;

        var bundlePath = optimizedMap.TryGetValue(imagePath, out var optPath)
            ? optPath : imagePath;
        var bundleImageName = Path.GetFileName(bundlePath);
        var bundleSidecarName = bundleImageName + ".json";

        zip.CreateEntryFromFile(bundlePath, bundleImageName);

        if (bundleSidecarName != Path.GetFileName(sidecarPath))
        {
            // Sidecar name changed (e.g., cat.png.json → cat.webp.json)
            var entry = zip.CreateEntry(bundleSidecarName);
            using var entryStream = entry.Open();
            using var sidecarStream = File.OpenRead(sidecarPath);
            sidecarStream.CopyTo(entryStream);
        }
        else
        {
            zip.CreateEntryFromFile(sidecarPath, bundleSidecarName);
        }
    }
}
