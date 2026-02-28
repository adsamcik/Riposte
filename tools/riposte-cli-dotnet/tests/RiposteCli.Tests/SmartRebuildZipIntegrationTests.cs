using RiposteCli.Models;
using RiposteCli.Services;

namespace RiposteCli.Tests;

/// <summary>
/// Integration tests for the smart rebuild → ZIP bundle pipeline.
/// Exercises ZipBundler.SelectImagesForBundle with rebuild plans and processed results.
/// </summary>
public class SmartRebuildZipIntegrationTests : IDisposable
{
    private readonly string _tempDir;
    private readonly string _outputDir;

    // Minimal valid 1x1 white PNG
    private static readonly byte[] MinimalPng =
    [
        0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x02, 0x00, 0x00, 0x00, 0x90, 0x77, 0x53,
        0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,
        0x54, 0x08, 0xD7, 0x63, 0xF8, 0xCF, 0xC0, 0x00,
        0x00, 0x00, 0x02, 0x00, 0x01, 0xE2, 0x21, 0xBC,
        0x33, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E,
        0x44, 0xAE, 0x42, 0x60, 0x82,
    ];

    public SmartRebuildZipIntegrationTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), $"riposte-zip-int-{Guid.NewGuid()}");
        _outputDir = Path.Combine(_tempDir, "output");
        Directory.CreateDirectory(_tempDir);
        Directory.CreateDirectory(_outputDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempDir))
            Directory.Delete(_tempDir, true);
    }

    // ─── Helpers ────────────────────────────────────────────────

    private string CreateImageFile(string filename)
    {
        var path = Path.Combine(_tempDir, filename);
        File.WriteAllBytes(path, MinimalPng);
        return path;
    }

    private void CreateSidecar(string imagePath)
    {
        var sidecarPath = Path.Combine(_outputDir, Path.GetFileName(imagePath) + ".json");
        File.WriteAllText(sidecarPath, """{"emojis":["😂"],"title":"Test"}""");
    }

    private static ImageManifestEntry MakeBundledEntry() => new()
    {
        ContentHash = "abc123",
        Model = "gpt-5-mini",
        GeneratedAt = "2025-01-01T00:00:00Z",
        SchemaVersion = "1.4",
        HasBundleOptimized = true,
    };

    private static ImageManifestEntry MakeUnbundledEntry() => new()
    {
        ContentHash = "abc123",
        Model = "gpt-5-mini",
        GeneratedAt = "2025-01-01T00:00:00Z",
        SchemaVersion = "1.4",
        HasBundleOptimized = false,
    };

    // ─── Test 1: New image + patch zip → only new image ─────────

    [Fact]
    public void PatchBundle_OneNewImage_OnlyNewImageSelected()
    {
        var existingImg = CreateImageFile("existing.png");
        var newImg = CreateImageFile("new_meme.png");
        CreateSidecar(existingImg);
        CreateSidecar(newImg);

        var allImages = new List<string> { existingImg, newImg };

        var manifest = new BuildManifest();
        manifest.Images["existing.png"] = MakeBundledEntry();
        // new_meme.png has no manifest entry (brand new)

        var plans = new List<ImageRebuildPlan>
        {
            new() { ImagePath = existingImg, Scope = RebuildScope.Skip, Reason = "up to date" },
            new() { ImagePath = newImg, Scope = RebuildScope.Full, NeedsReoptimization = true, Reason = "no sidecar" },
        };

        // Simulate new image was successfully annotated
        var processed = new List<(string Image, string Sidecar)>
        {
            (newImg, Path.Combine(_outputDir, "new_meme.png.json")),
        };

        var selected = ZipBundler.SelectImagesForBundle(
            ZipMode.Patch, allImages, _outputDir, plans, processed, manifest);

        Assert.Single(selected);
        Assert.Equal(newImg, selected[0]);
    }

    // ─── Test 2: Partial update + patch zip → updated image ─────

    [Fact]
    public void PatchBundle_PartialUpdate_UpdatedImageSelected()
    {
        var unchangedImg = CreateImageFile("unchanged.png");
        var updatedImg = CreateImageFile("updated.png");
        CreateSidecar(unchangedImg);
        CreateSidecar(updatedImg);

        var allImages = new List<string> { unchangedImg, updatedImg };

        var manifest = new BuildManifest();
        manifest.Images["unchanged.png"] = MakeBundledEntry();
        manifest.Images["updated.png"] = MakeBundledEntry();

        var plans = new List<ImageRebuildPlan>
        {
            new() { ImagePath = unchangedImg, Scope = RebuildScope.Skip, Reason = "up to date" },
            new()
            {
                ImagePath = updatedImg,
                Scope = RebuildScope.Partial,
                AffectedGroups = [PromptHasher.GroupSearch],
                Reason = "search prompt changed",
            },
        };

        // Simulate partial rebuild succeeded
        var processed = new List<(string Image, string Sidecar)>
        {
            (updatedImg, Path.Combine(_outputDir, "updated.png.json")),
        };

        var selected = ZipBundler.SelectImagesForBundle(
            ZipMode.Patch, allImages, _outputDir, plans, processed, manifest);

        Assert.Single(selected);
        Assert.Equal(updatedImg, selected[0]);
    }

    // ─── Test 3: All up-to-date + patch zip → empty ─────────────

    [Fact]
    public void PatchBundle_AllUpToDate_EmptyPatch()
    {
        var img1 = CreateImageFile("meme1.png");
        var img2 = CreateImageFile("meme2.png");
        CreateSidecar(img1);
        CreateSidecar(img2);

        var allImages = new List<string> { img1, img2 };

        var manifest = new BuildManifest();
        manifest.Images["meme1.png"] = MakeBundledEntry();
        manifest.Images["meme2.png"] = MakeBundledEntry();

        var plans = new List<ImageRebuildPlan>
        {
            new() { ImagePath = img1, Scope = RebuildScope.Skip, Reason = "up to date" },
            new() { ImagePath = img2, Scope = RebuildScope.Skip, Reason = "up to date" },
        };

        // No work done
        var processed = new List<(string Image, string Sidecar)>();

        var selected = ZipBundler.SelectImagesForBundle(
            ZipMode.Patch, allImages, _outputDir, plans, processed, manifest);

        Assert.Empty(selected);
    }

    // ─── Test 4: Errors + patch zip → error images excluded ─────

    [Fact]
    public void PatchBundle_ErrorImages_ExcludedFromBundle()
    {
        var successImg = CreateImageFile("success.png");
        var errorImg = CreateImageFile("error.png");
        CreateSidecar(successImg);
        CreateSidecar(errorImg); // Has old sidecar but rebuild failed

        var allImages = new List<string> { successImg, errorImg };

        var manifest = new BuildManifest();
        // Neither image was previously bundled
        manifest.Images["success.png"] = MakeUnbundledEntry();
        manifest.Images["error.png"] = MakeUnbundledEntry();

        var plans = new List<ImageRebuildPlan>
        {
            new() { ImagePath = successImg, Scope = RebuildScope.Full, NeedsReoptimization = true, Reason = "full rebuild" },
            new() { ImagePath = errorImg, Scope = RebuildScope.Full, NeedsReoptimization = true, Reason = "full rebuild" },
        };

        // Only successImg was processed; errorImg failed annotation
        var processed = new List<(string Image, string Sidecar)>
        {
            (successImg, Path.Combine(_outputDir, "success.png.json")),
        };

        var selected = ZipBundler.SelectImagesForBundle(
            ZipMode.Patch, allImages, _outputDir, plans, processed, manifest);

        Assert.Single(selected);
        Assert.Equal(successImg, selected[0]);
        Assert.DoesNotContain(errorImg, selected);
    }

    // ─── Test 5: Full rebuild + full zip → all images ───────────

    [Fact]
    public void FullBundle_AllImagesWithSidecars_AllIncluded()
    {
        var img1 = CreateImageFile("meme1.png");
        var img2 = CreateImageFile("meme2.png");
        var img3 = CreateImageFile("meme3.png");
        CreateSidecar(img1);
        CreateSidecar(img2);
        CreateSidecar(img3);

        var allImages = new List<string> { img1, img2, img3 };
        var manifest = new BuildManifest();

        var plans = new List<ImageRebuildPlan>
        {
            new() { ImagePath = img1, Scope = RebuildScope.Full, Reason = "force mode" },
            new() { ImagePath = img2, Scope = RebuildScope.Full, Reason = "force mode" },
            new() { ImagePath = img3, Scope = RebuildScope.Full, Reason = "force mode" },
        };

        var processed = allImages
            .Select(img => (img, Path.Combine(_outputDir, Path.GetFileName(img) + ".json")))
            .ToList();

        var selected = ZipBundler.SelectImagesForBundle(
            ZipMode.Full, allImages, _outputDir, plans, processed, manifest);

        Assert.Equal(3, selected.Count);
        Assert.Contains(img1, selected);
        Assert.Contains(img2, selected);
        Assert.Contains(img3, selected);
    }

    // ─── Test 6: Full bundle skips images without sidecars ──────

    [Fact]
    public void FullBundle_MissingSidecar_ExcludedFromSelection()
    {
        var withSidecar = CreateImageFile("annotated.png");
        var withoutSidecar = CreateImageFile("unannotated.png");
        CreateSidecar(withSidecar);
        // No sidecar for unannotated.png

        var allImages = new List<string> { withSidecar, withoutSidecar };
        var manifest = new BuildManifest();
        var plans = new List<ImageRebuildPlan>
        {
            new() { ImagePath = withSidecar, Scope = RebuildScope.Full, Reason = "force" },
            new() { ImagePath = withoutSidecar, Scope = RebuildScope.Full, Reason = "force" },
        };
        var processed = new List<(string Image, string Sidecar)>
        {
            (withSidecar, Path.Combine(_outputDir, "annotated.png.json")),
        };

        var selected = ZipBundler.SelectImagesForBundle(
            ZipMode.Full, allImages, _outputDir, plans, processed, manifest);

        Assert.Single(selected);
        Assert.Equal(withSidecar, selected[0]);
    }

    // ─── Test 7: Error image with stripping still included ──────

    [Fact]
    public void PatchBundle_ErrorImageWithStripping_StillIncludedViaStrip()
    {
        var errorWithStrip = CreateImageFile("error_strip.png");
        CreateSidecar(errorWithStrip);

        var allImages = new List<string> { errorWithStrip };

        var manifest = new BuildManifest();
        manifest.Images["error_strip.png"] = MakeBundledEntry();

        // Image needs both partial rebuild AND stripping; rebuild failed
        var plans = new List<ImageRebuildPlan>
        {
            new()
            {
                ImagePath = errorWithStrip,
                Scope = RebuildScope.Partial,
                AffectedGroups = [PromptHasher.GroupSearch],
                RemovedGroups = [PromptHasher.LocalizationGroup("cs")],
                Reason = "search changed + cs removed",
            },
        };

        // Partial rebuild failed — not in processed
        var processed = new List<(string Image, string Sidecar)>();

        var selected = ZipBundler.SelectImagesForBundle(
            ZipMode.Patch, allImages, _outputDir, plans, processed, manifest);

        // Should still be included because NeedsStripping is true (sidecar was modified by strip)
        Assert.Single(selected);
        Assert.Equal(errorWithStrip, selected[0]);
    }

    // ─── Test 8: Never-bundled skip images included in patch ────

    [Fact]
    public void PatchBundle_NeverBundledSkipImage_IncludedInPatch()
    {
        var neverBundled = CreateImageFile("never_bundled.png");
        CreateSidecar(neverBundled);

        var allImages = new List<string> { neverBundled };

        var manifest = new BuildManifest();
        manifest.Images["never_bundled.png"] = MakeUnbundledEntry();

        // Image is up-to-date (skip) but was never bundled
        var plans = new List<ImageRebuildPlan>
        {
            new() { ImagePath = neverBundled, Scope = RebuildScope.Skip, Reason = "up to date" },
        };

        var processed = new List<(string Image, string Sidecar)>();

        var selected = ZipBundler.SelectImagesForBundle(
            ZipMode.Patch, allImages, _outputDir, plans, processed, manifest);

        // Should be included because it was never bundled before
        Assert.Single(selected);
        Assert.Equal(neverBundled, selected[0]);
    }
}
