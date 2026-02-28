using RiposteCli.Models;

namespace RiposteCli.Tests;

/// <summary>
/// Regression tests for case-insensitive filename lookups on Windows.
/// Bugs: BuildManifest.Images and ImageHashService manifest used ordinal comparison,
/// causing false cache misses when file casing differs between runs.
/// </summary>
public sealed class CaseInsensitivityTests : IDisposable
{
    private readonly string _tempDir;

    public CaseInsensitivityTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString());
        Directory.CreateDirectory(_tempDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempDir))
            Directory.Delete(_tempDir, recursive: true);
    }

    // ── BuildManifest.Images case-insensitivity ──────────────────────

    [Fact]
    public void BuildManifest_Images_DefaultIsCaseInsensitive()
    {
        var manifest = new BuildManifest();
        manifest.Images["Photo.JPG"] = new ImageManifestEntry
        {
            ContentHash = "abc", Model = "m", GeneratedAt = "now", SchemaVersion = "1.4",
        };

        Assert.True(manifest.Images.ContainsKey("photo.jpg"));
        Assert.True(manifest.Images.ContainsKey("PHOTO.JPG"));
        Assert.True(manifest.Images.ContainsKey("Photo.JPG"));
    }

    [Fact]
    public void ManifestService_Load_ImageKeysAreCaseInsensitive()
    {
        // Save a manifest with specific casing
        var manifest = new BuildManifest();
        ManifestService.RecordImageBuild(manifest, "Photo.JPG", "hash1", "model", "1.4",
            new Dictionary<string, string> { ["core"] = "c1" });
        ManifestService.Save(_tempDir, manifest);

        // Load and lookup with different casing
        var loaded = ManifestService.Load(_tempDir);
        Assert.True(loaded.Images.ContainsKey("photo.jpg"), "lowercase lookup failed");
        Assert.True(loaded.Images.ContainsKey("PHOTO.JPG"), "uppercase lookup failed");
        Assert.True(loaded.Images.ContainsKey("Photo.JPG"), "original casing lookup failed");
    }

    [Fact]
    public void ManifestService_Load_DeserializedImages_SupportsCaseMismatch()
    {
        // Simulate a manifest saved with one casing, loaded and accessed with another
        var manifest = new BuildManifest();
        ManifestService.RecordImageBuild(manifest, "MyMeme.PNG", "hash1", "model", "1.4",
            new Dictionary<string, string> { ["core"] = "c1" });
        ManifestService.Save(_tempDir, manifest);

        var loaded = ManifestService.Load(_tempDir);

        // The filesystem might report "mymeme.png" on next scan — lookup must still work
        Assert.True(loaded.Images.TryGetValue("mymeme.png", out var entry));
        Assert.Equal("hash1", entry!.ContentHash);
    }

    [Fact]
    public void ManifestService_Load_DuplicateCasings_TakesFirst()
    {
        // Manually write a manifest with duplicate casings (edge case from manual editing)
        var json = """
        {
            "manifestVersion": "1.0",
            "schemaVersion": "1.4",
            "model": "gpt-5-mini",
            "promptHashes": {},
            "images": {
                "Photo.JPG": { "contentHash": "first", "model": "m", "generatedAt": "t", "schemaVersion": "1.4" },
                "photo.jpg": { "contentHash": "second", "model": "m", "generatedAt": "t", "schemaVersion": "1.4" }
            }
        }
        """;
        File.WriteAllText(Path.Combine(_tempDir, BuildManifest.FileName), json);

        var loaded = ManifestService.Load(_tempDir);

        // Should have exactly one entry (first one wins with TryAdd)
        Assert.Single(loaded.Images);
        Assert.Equal("first", loaded.Images.Values.First().ContentHash);
    }

    [Fact]
    public void RecordImageBuild_CaseInsensitive_OverwritesSameName()
    {
        var manifest = new BuildManifest();
        ManifestService.RecordImageBuild(manifest, "Photo.JPG", "hash1", "model", "1.4",
            new Dictionary<string, string> { ["core"] = "c1" });

        // Same filename different casing should overwrite, not create duplicate
        ManifestService.RecordImageBuild(manifest, "photo.jpg", "hash2", "model", "1.4",
            new Dictionary<string, string> { ["core"] = "c2" });

        Assert.Single(manifest.Images);
        Assert.Equal("hash2", manifest.Images["PHOTO.JPG"].ContentHash);
    }

    [Fact]
    public void RecordPartialBuild_CaseInsensitive_FindsExistingEntry()
    {
        var manifest = new BuildManifest();
        ManifestService.RecordImageBuild(manifest, "Photo.JPG", "hash1", "model", "1.4",
            new Dictionary<string, string> { ["core"] = "c1", ["search"] = "s1" });

        // Partial build with different casing should find and update the existing entry
        ManifestService.RecordPartialBuild(manifest, "photo.jpg", "hash1", "model", "1.4",
            affectedGroups: new[] { "core" },
            currentPromptHashes: new Dictionary<string, string> { ["core"] = "c2" });

        Assert.Single(manifest.Images);
        Assert.Equal("c2", manifest.Images["Photo.JPG"].FieldHashes["core"]);
        Assert.Equal("s1", manifest.Images["Photo.JPG"].FieldHashes["search"]);
    }

    [Fact]
    public void RecordBundleOptimized_CaseInsensitive_FindsEntry()
    {
        var manifest = new BuildManifest();
        ManifestService.RecordImageBuild(manifest, "Photo.JPG", "hash1", "model", "1.4",
            new Dictionary<string, string> { ["core"] = "c1" });

        ManifestService.RecordBundleOptimized(manifest, "photo.jpg");

        Assert.True(manifest.Images["Photo.JPG"].HasBundleOptimized);
    }

    // ── ImageHashService manifest case-insensitivity ─────────────────

    [Fact]
    public void ImageHashService_LoadManifest_KeysAreCaseInsensitive()
    {
        var manifest = new Dictionary<string, HashEntry>(StringComparer.OrdinalIgnoreCase)
        {
            ["Photo.JPG"] = new HashEntry("abc123", "999"),
        };
        ImageHashService.SaveManifest(_tempDir, manifest);

        var loaded = ImageHashService.LoadManifest(_tempDir);

        Assert.True(loaded.ContainsKey("photo.jpg"), "lowercase lookup failed");
        Assert.True(loaded.ContainsKey("PHOTO.JPG"), "uppercase lookup failed");
        Assert.True(loaded.ContainsKey("Photo.JPG"), "original casing lookup failed");
    }

    [Fact]
    public void ImageHashService_LoadManifest_EmptyDir_ReturnsCaseInsensitiveDict()
    {
        var manifest = ImageHashService.LoadManifest(_tempDir);
        manifest["Photo.JPG"] = new HashEntry("abc", null);

        Assert.True(manifest.ContainsKey("photo.jpg"),
            "Empty manifest should return case-insensitive dictionary");
    }

    [Fact]
    public void ImageHashService_Deduplicate_UsesCacheWithCaseMismatch()
    {
        // Pre-populate manifest with uppercase key
        var manifest = new Dictionary<string, HashEntry>(StringComparer.OrdinalIgnoreCase)
        {
            ["CAT.PNG"] = new HashEntry("hash_from_cache", null),
        };

        // Create a real test image file with lowercase name
        var imgDir = Path.Combine(_tempDir, "images");
        Directory.CreateDirectory(imgDir);
        var imgPath = CreateTestPng(imgDir, "cat.png");

        // Deduplicate should find the cache entry despite case mismatch
        var result = ImageHashService.Deduplicate(
            [imgPath], manifest, detectNearDuplicates: false);

        // Should use cached hash, not recompute
        Assert.True(manifest.ContainsKey("cat.png"));
        var entry = manifest["cat.png"];
        Assert.Equal("hash_from_cache", entry.ContentHash);
    }

    // ── RebuildPlanner with case-mismatched manifest ─────────────────

    [Fact]
    public void RebuildPlanner_FindsManifestEntry_WithDifferentCase()
    {
        var outputDir = Path.Combine(_tempDir, "output");
        Directory.CreateDirectory(outputDir);
        OutputPaths.EnsureDirectories(outputDir);

        // Create a test image
        var imgPath = CreateTestPng(_tempDir, "Photo.JPG");

        // Create sidecar (so HasSidecar returns true)
        var metadata = SidecarService.CreateMetadata(new List<string> { "😀" });
        SidecarService.WriteSidecar(imgPath, metadata, outputDir);

        // Build manifest with lowercase key (simulating a prior save that used different casing)
        var manifest = new BuildManifest();
        var contentHash = ImageHashService.GetContentHash(imgPath);
        var promptHashes = new Dictionary<string, string>
        {
            ["core"] = "c1", ["search"] = "s1", ["cultural"] = "cu1", ["emotions"] = "e1",
        };
        ManifestService.RecordImageBuild(manifest, "photo.jpg", contentHash, "gpt-5-mini", "1.4", promptHashes);

        // Plan with uppercase filename from filesystem
        var plan = RebuildPlanner.PlanForImage(
            imgPath, manifest, promptHashes, "gpt-5-mini", "1.4", outputDir);

        // Should find the entry and skip (not trigger a full rebuild due to "not in build manifest")
        Assert.Equal(RebuildScope.Skip, plan.Scope);
    }

    // ── SidecarService.WriteSidecar atomic write ─────────────────────

    [Fact]
    public void WriteSidecar_DoesNotLeaveTempFile()
    {
        var imgPath = CreateTestPng(_tempDir, "test.png");
        var metadata = SidecarService.CreateMetadata(new List<string> { "😀" });

        var sidecarPath = SidecarService.WriteSidecar(imgPath, metadata, _tempDir);

        Assert.True(File.Exists(sidecarPath));
        Assert.False(File.Exists(sidecarPath + ".tmp"), "Temp file should be cleaned up");
    }

    [Fact]
    public void WriteSidecar_OverwritesExisting_Atomically()
    {
        var imgPath = CreateTestPng(_tempDir, "test.png");

        // Write first version
        var metadata1 = SidecarService.CreateMetadata(new List<string> { "😀" }, title: "first");
        var path1 = SidecarService.WriteSidecar(imgPath, metadata1, _tempDir);

        // Write second version (overwrite)
        var metadata2 = SidecarService.CreateMetadata(new List<string> { "😎" }, title: "second");
        var path2 = SidecarService.WriteSidecar(imgPath, metadata2, _tempDir);

        Assert.Equal(path1, path2);
        var content = File.ReadAllText(path2);
        Assert.Contains("second", content);
        Assert.DoesNotContain("first", content);
        Assert.False(File.Exists(path2 + ".tmp"));
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static string CreateTestPng(string dir, string name)
    {
        var path = Path.Combine(dir, name);
        // Minimal valid PNG: 8-byte signature + IHDR + IEND
        var png = new byte[]
        {
            0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG signature
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, // IHDR chunk
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, // 1x1
            0x08, 0x02, 0x00, 0x00, 0x00, 0x90, 0x77, 0x53, 0xDE, // RGB, CRC
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, // IEND
            0xAE, 0x42, 0x60, 0x82,
        };
        File.WriteAllBytes(path, png);
        return path;
    }
}
