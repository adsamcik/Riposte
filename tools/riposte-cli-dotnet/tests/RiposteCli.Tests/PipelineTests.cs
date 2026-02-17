using System.IO.Compression;
using System.Text.Json;
using RiposteCli.Models;
using RiposteCli.Services;

namespace RiposteCli.Tests;

/// <summary>
/// End-to-end pipeline tests: full annotation pipeline (minus Copilot API),
/// full dedup pipeline, ZIP bundling with output dir, multi-format discovery.
/// </summary>
public class PipelineTests : IDisposable
{
    private readonly string _tempDir;

    public PipelineTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), $"riposte-pipeline-{Guid.NewGuid()}");
        Directory.CreateDirectory(_tempDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempDir))
            Directory.Delete(_tempDir, true);
    }

    #region Full Annotation Pipeline (sans API)

    [Fact]
    public void FullAnnotationPipeline_DiscoverFilterHashAnnotateBundle()
    {
        var imageDir = Path.Combine(_tempDir, "memes");
        Directory.CreateDirectory(imageDir);

        // Step 1: Create various images
        CreateImage(imageDir, "cat.jpg");
        CreateImage(imageDir, "dog.png");
        CreateImage(imageDir, "bird.gif");
        File.WriteAllText(Path.Combine(imageDir, "notes.txt"), "not an image");

        // Step 2: Discover
        var images = SidecarService.GetImagesInFolder(imageDir);
        Assert.Equal(3, images.Count);

        // Step 3: Filter (incremental - none have sidecars)
        var (toProcess, skipped) = SidecarService.FilterImagesByMode(images, imageDir);
        Assert.Equal(3, toProcess.Count);
        Assert.Equal(0, skipped);

        // Step 4: Dedup check
        var manifest = ImageHashService.LoadManifest(imageDir);
        var dedupResult = ImageHashService.Deduplicate(toProcess, manifest,
            detectNearDuplicates: false);
        Assert.Empty(dedupResult.ExactDuplicates);
        ImageHashService.SaveManifest(imageDir, manifest);

        // Step 5: Simulate annotation (create metadata as if Copilot returned results)
        foreach (var img in dedupResult.UniqueImages)
        {
            var analysisResult = new AnalysisResult
            {
                Emojis = ["😂", "🐱"],
                Title = $"Test {Path.GetFileNameWithoutExtension(img)}",
                Description = $"A test meme of {Path.GetFileNameWithoutExtension(img)}",
                Tags = ["test", "meme"],
                SearchPhrases = [$"test {Path.GetFileNameWithoutExtension(img)} meme"],
            };

            var contentHash = ImageHashService.GetContentHash(img);
            var metadata = SidecarService.CreateMetadata(analysisResult, "en", contentHash);
            SidecarService.WriteSidecar(img, metadata, imageDir);
        }

        // Step 6: Verify all sidecars created
        Assert.True(SidecarService.HasSidecar(images[0], imageDir));
        Assert.True(SidecarService.HasSidecar(images[1], imageDir));
        Assert.True(SidecarService.HasSidecar(images[2], imageDir));

        // Step 7: Re-filter (incremental) — all should be skipped now
        var (toProcess2, skipped2) = SidecarService.FilterImagesByMode(images, imageDir);
        Assert.Empty(toProcess2);
        Assert.Equal(3, skipped2);

        // Step 8: Bundle into ZIP
        var zipPath = Path.Combine(_tempDir, "memes.meme.zip");
        using (var zip = ZipFile.Open(zipPath, ZipArchiveMode.Create))
        {
            foreach (var img in images)
            {
                var sidecarPath = Path.Combine(imageDir, Path.GetFileName(img) + ".json");
                if (File.Exists(sidecarPath))
                {
                    zip.CreateEntryFromFile(img, Path.GetFileName(img));
                    zip.CreateEntryFromFile(sidecarPath, Path.GetFileName(sidecarPath));
                }
            }
        }

        // Step 9: Verify ZIP
        using var zipRead = ZipFile.OpenRead(zipPath);
        Assert.Equal(6, zipRead.Entries.Count); // 3 images + 3 sidecars
    }

    #endregion

    #region Full Dedup Pipeline

    [Fact]
    public void FullDedupPipeline_DetectDeleteCleanReverify()
    {
        // Setup: 4 images, 2 are identical
        var content = "duplicate data"u8.ToArray();
        CreateFile("original.jpg", content);
        CreateFile("copy.jpg", content);
        CreateFile("unique1.jpg", "unique1"u8.ToArray());
        CreateFile("unique2.jpg", "unique2"u8.ToArray());

        // Also create sidecars for all
        foreach (var img in new[] { "original.jpg", "copy.jpg", "unique1.jpg", "unique2.jpg" })
        {
            var meta = SidecarService.CreateMetadata(emojis: ["😂"]);
            SidecarService.WriteSidecar(Path.Combine(_tempDir, img), meta);
        }

        // Step 1: Discover
        var images = SidecarService.GetImagesInFolder(_tempDir);
        Assert.Equal(4, images.Count);

        // Step 2: Load manifest and dedup
        var manifest = ImageHashService.LoadManifest(_tempDir);
        var result = ImageHashService.Deduplicate(images, manifest, detectNearDuplicates: false);
        ImageHashService.SaveManifest(_tempDir, manifest);

        Assert.Single(result.ExactDuplicates);
        Assert.Equal(3, result.UniqueImages.Count);

        // Step 3: Delete duplicates (simulate DedupeCommand.DeleteImageAndSidecar)
        foreach (var (dupe, _) in result.ExactDuplicates)
        {
            File.Delete(dupe);
            var sidecarPath = Path.Combine(_tempDir, Path.GetFileName(dupe) + ".json");
            if (File.Exists(sidecarPath))
                File.Delete(sidecarPath);
            manifest.Remove(Path.GetFileName(dupe));
        }
        ImageHashService.SaveManifest(_tempDir, manifest);

        // Step 4: Re-verify
        var remainingImages = SidecarService.GetImagesInFolder(_tempDir);
        Assert.Equal(3, remainingImages.Count);

        var manifest2 = ImageHashService.LoadManifest(_tempDir);
        Assert.Equal(3, manifest2.Count);

        var result2 = ImageHashService.Deduplicate(remainingImages, manifest2,
            detectNearDuplicates: false);
        Assert.Empty(result2.ExactDuplicates);
        Assert.Equal(3, result2.UniqueImages.Count);
    }

    #endregion

    #region Output Dir Separation

    [Fact]
    public void SeparateOutputDir_SidecarsInOutputDir_ImagesUntouched()
    {
        var imageDir = Path.Combine(_tempDir, "images");
        var outputDir = Path.Combine(_tempDir, "output");
        Directory.CreateDirectory(imageDir);
        Directory.CreateDirectory(outputDir);

        CreateImage(imageDir, "photo1.jpg");
        CreateImage(imageDir, "photo2.png");

        // Annotate with separate output dir
        var images = SidecarService.GetImagesInFolder(imageDir);
        foreach (var img in images)
        {
            var meta = SidecarService.CreateMetadata(emojis: ["📸"], title: Path.GetFileNameWithoutExtension(img));
            SidecarService.WriteSidecar(img, meta, outputDir);
        }

        // Verify: sidecars in output dir, not image dir
        Assert.True(File.Exists(Path.Combine(outputDir, "photo1.jpg.json")));
        Assert.True(File.Exists(Path.Combine(outputDir, "photo2.png.json")));
        Assert.False(File.Exists(Path.Combine(imageDir, "photo1.jpg.json")));
        Assert.False(File.Exists(Path.Combine(imageDir, "photo2.png.json")));

        // Filter should check output dir for existing sidecars
        var (toProcess, skipped) = SidecarService.FilterImagesByMode(images, outputDir);
        Assert.Empty(toProcess);
        Assert.Equal(2, skipped);
    }

    #endregion

    #region Force Mode Pipeline

    [Fact]
    public void ForceMode_ReprocessesExistingAnnotations()
    {
        CreateImage(_tempDir, "meme.jpg");
        var imgPath = Path.Combine(_tempDir, "meme.jpg");

        // First annotation
        var meta1 = SidecarService.CreateMetadata(emojis: ["😂"], title: "Original");
        SidecarService.WriteSidecar(imgPath, meta1);

        // Verify exists
        Assert.True(SidecarService.HasSidecar(imgPath));

        // Force mode should still include it
        var images = SidecarService.GetImagesInFolder(_tempDir);
        var (toProcess, skipped) = SidecarService.FilterImagesByMode(images, _tempDir, force: true);
        Assert.Single(toProcess);
        Assert.Equal(0, skipped);

        // Overwrite with new annotation
        var meta2 = SidecarService.CreateMetadata(emojis: ["🔥"], title: "Updated");
        SidecarService.WriteSidecar(imgPath, meta2);

        var json = File.ReadAllText(Path.Combine(_tempDir, "meme.jpg.json"));
        var readBack = JsonSerializer.Deserialize<SidecarMetadata>(json);
        Assert.Equal("Updated", readBack?.Title);
    }

    #endregion

    #region Manifest Persistence Across Runs

    [Fact]
    public void ManifestPersistence_SurvivesMultipleRuns()
    {
        // Run 1: Process some images
        CreateFile("img1.jpg", "content1"u8.ToArray());
        CreateFile("img2.jpg", "content2"u8.ToArray());

        var images1 = new List<string>
        {
            Path.Combine(_tempDir, "img1.jpg"),
            Path.Combine(_tempDir, "img2.jpg"),
        };
        var manifest1 = ImageHashService.LoadManifest(_tempDir);
        ImageHashService.Deduplicate(images1, manifest1, detectNearDuplicates: false);
        ImageHashService.SaveManifest(_tempDir, manifest1);

        // Run 2: Add more images, reload manifest
        CreateFile("img3.jpg", "content3"u8.ToArray());
        var images2 = new List<string>
        {
            Path.Combine(_tempDir, "img1.jpg"),
            Path.Combine(_tempDir, "img2.jpg"),
            Path.Combine(_tempDir, "img3.jpg"),
        };
        var manifest2 = ImageHashService.LoadManifest(_tempDir);
        Assert.Equal(2, manifest2.Count); // Should have persisted entries from run 1

        ImageHashService.Deduplicate(images2, manifest2, detectNearDuplicates: false);
        ImageHashService.SaveManifest(_tempDir, manifest2);
        Assert.Equal(3, manifest2.Count);

        // Run 3: Verify consistency
        var manifest3 = ImageHashService.LoadManifest(_tempDir);
        Assert.Equal(3, manifest3.Count);
    }

    #endregion

    #region Mixed Format Discovery

    [Fact]
    public void MixedFormats_AllDiscoveredAndProcessed()
    {
        File.WriteAllBytes(Path.Combine(_tempDir, "a.jpg"), [0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10]);
        File.WriteAllBytes(Path.Combine(_tempDir, "b.jpeg"), [0xFF, 0xD8, 0xFF, 0xE1, 0x00, 0x10]);
        File.WriteAllBytes(Path.Combine(_tempDir, "c.png"), [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]);
        File.WriteAllBytes(Path.Combine(_tempDir, "d.gif"), [0x47, 0x49, 0x46, 0x38, 0x39, 0x61]);
        File.WriteAllBytes(Path.Combine(_tempDir, "e.webp"), [0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00]);
        File.WriteAllBytes(Path.Combine(_tempDir, "f.bmp"), [0x42, 0x4D, 0x00, 0x00, 0x00, 0x00]);

        var images = SidecarService.GetImagesInFolder(_tempDir);
        Assert.Equal(6, images.Count);

        // All should be hashable
        foreach (var img in images)
        {
            var hash = ImageHashService.GetContentHash(img);
            Assert.Equal(64, hash.Length);
        }

        // All should be annotatable (metadata creation doesn't depend on format)
        foreach (var img in images)
        {
            var meta = SidecarService.CreateMetadata(emojis: ["📸"]);
            var sidecar = SidecarService.WriteSidecar(img, meta);
            Assert.True(File.Exists(sidecar));
        }
    }

    #endregion

    #region Sidecar Schema Consistency

    [Fact]
    public void SidecarSchema_AllRequiredFieldsPresent()
    {
        CreateImage(_tempDir, "test.jpg");
        var imgPath = Path.Combine(_tempDir, "test.jpg");

        var result = new AnalysisResult
        {
            Emojis = ["😂"],
            Title = "Test",
            Description = "Description",
            Tags = ["tag1"],
            SearchPhrases = ["search phrase"],
            BasedOn = "Source",
        };

        var hash = ImageHashService.GetContentHash(imgPath);
        var metadata = SidecarService.CreateMetadata(result, "en", hash);
        var sidecarPath = SidecarService.WriteSidecar(imgPath, metadata);

        var json = File.ReadAllText(sidecarPath);
        var doc = JsonDocument.Parse(json);
        var root = doc.RootElement;

        // Required fields
        Assert.True(root.TryGetProperty("schemaVersion", out var sv));
        Assert.Equal("1.3", sv.GetString());

        Assert.True(root.TryGetProperty("emojis", out var emojis));
        Assert.Equal(JsonValueKind.Array, emojis.ValueKind);

        Assert.True(root.TryGetProperty("createdAt", out var ca));
        Assert.True(DateTimeOffset.TryParse(ca.GetString(), out _));

        Assert.True(root.TryGetProperty("appVersion", out var av));
        Assert.StartsWith("cli-", av.GetString());

        Assert.True(root.TryGetProperty("cliVersion", out _));

        // Optional but present fields
        Assert.True(root.TryGetProperty("title", out _));
        Assert.True(root.TryGetProperty("description", out _));
        Assert.True(root.TryGetProperty("tags", out _));
        Assert.True(root.TryGetProperty("searchPhrases", out _));
        Assert.True(root.TryGetProperty("primaryLanguage", out _));
        Assert.True(root.TryGetProperty("contentHash", out _));
        Assert.True(root.TryGetProperty("basedOn", out _));
    }

    [Fact]
    public void SidecarSchema_MinimalMetadata_OnlyRequiredFields()
    {
        CreateImage(_tempDir, "minimal.jpg");
        var imgPath = Path.Combine(_tempDir, "minimal.jpg");

        var metadata = SidecarService.CreateMetadata(emojis: ["🎉"]);
        var sidecarPath = SidecarService.WriteSidecar(imgPath, metadata);

        var json = File.ReadAllText(sidecarPath);
        var doc = JsonDocument.Parse(json);
        var root = doc.RootElement;

        // Required fields present
        Assert.True(root.TryGetProperty("schemaVersion", out _));
        Assert.True(root.TryGetProperty("emojis", out _));
        Assert.True(root.TryGetProperty("createdAt", out _));

        // Optional fields omitted (WhenWritingNull)
        Assert.False(root.TryGetProperty("title", out _));
        Assert.False(root.TryGetProperty("description", out _));
        Assert.False(root.TryGetProperty("tags", out _));
        Assert.False(root.TryGetProperty("basedOn", out _));
    }

    #endregion

    private string CreateImage(string dir, string name)
    {
        var path = Path.Combine(dir, name);
        byte[] data = Path.GetExtension(name).ToLower() switch
        {
            ".png" => [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A],
            ".gif" => [0x47, 0x49, 0x46, 0x38, 0x39, 0x61],
            ".webp" => [0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00],
            ".bmp" => [0x42, 0x4D, 0x00, 0x00, 0x00, 0x00],
            _ => [0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10],
        };
        File.WriteAllBytes(path, data);
        return path;
    }

    private string CreateFile(string name, byte[] content)
    {
        var path = Path.Combine(_tempDir, name);
        File.WriteAllBytes(path, content);
        return path;
    }
}
