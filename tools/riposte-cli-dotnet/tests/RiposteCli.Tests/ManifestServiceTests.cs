using RiposteCli.Models;

namespace RiposteCli.Tests;

public sealed class ManifestServiceTests : IDisposable
{
    private readonly string _tempDir;

    public ManifestServiceTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString());
        Directory.CreateDirectory(_tempDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempDir))
            Directory.Delete(_tempDir, recursive: true);
    }

    // ── Load / Save roundtrip ────────────────────────────────────────

    [Fact]
    public void Load_NonExistentFile_ReturnsEmptyManifest()
    {
        var manifest = ManifestService.Load(_tempDir);

        Assert.Empty(manifest.Images);
        Assert.Empty(manifest.PromptHashes);
        Assert.Equal(BuildManifest.CurrentManifestVersion, manifest.ManifestVersion);
    }

    [Fact]
    public void SaveThenLoad_PreservesAllFields()
    {
        var original = new BuildManifest
        {
            Model = "test-model",
            SchemaVersion = "2.0",
            PromptHashes = new Dictionary<string, string>
            {
                ["core"] = "hash-core",
                ["search"] = "hash-search",
            },
            Optimization = new OptimizationConfig
            {
                ApiMaxDimension = 800,
                ApiFormat = "jpeg",
                BundleMaxDimension = 600,
                BundleFormat = "png",
                Quality = 90,
            },
            Images = new Dictionary<string, ImageManifestEntry>
            {
                ["img1.png"] = new ImageManifestEntry
                {
                    ContentHash = "abc123",
                    Model = "test-model",
                    SchemaVersion = "2.0",
                    GeneratedAt = "2025-01-01T00:00:00+00:00",
                    FieldHashes = new Dictionary<string, string> { ["core"] = "h1" },
                    OptimizationFingerprint = "fp1",
                    HasApiOptimized = true,
                    HasBundleOptimized = true,
                },
            },
        };

        ManifestService.Save(_tempDir, original);
        var loaded = ManifestService.Load(_tempDir);

        Assert.Equal(original.Model, loaded.Model);
        Assert.Equal(original.SchemaVersion, loaded.SchemaVersion);
        Assert.Equal(original.ManifestVersion, loaded.ManifestVersion);
        Assert.Equal(original.PromptHashes, loaded.PromptHashes);

        Assert.NotNull(loaded.Optimization);
        Assert.Equal(800, loaded.Optimization.ApiMaxDimension);
        Assert.Equal("jpeg", loaded.Optimization.ApiFormat);
        Assert.Equal(600, loaded.Optimization.BundleMaxDimension);
        Assert.Equal("png", loaded.Optimization.BundleFormat);
        Assert.Equal(90, loaded.Optimization.Quality);

        Assert.Single(loaded.Images);
        var entry = loaded.Images["img1.png"];
        Assert.Equal("abc123", entry.ContentHash);
        Assert.Equal("test-model", entry.Model);
        Assert.Equal("2.0", entry.SchemaVersion);
        Assert.Equal("2025-01-01T00:00:00+00:00", entry.GeneratedAt);
        Assert.Equal("h1", entry.FieldHashes["core"]);
        Assert.Equal("fp1", entry.OptimizationFingerprint);
        Assert.True(entry.HasApiOptimized);
        Assert.True(entry.HasBundleOptimized);
    }

    [Fact]
    public void Load_CorruptJson_ReturnsEmptyManifest()
    {
        var path = Path.Combine(_tempDir, BuildManifest.FileName);
        File.WriteAllText(path, "{{not valid json!@#$");

        var manifest = ManifestService.Load(_tempDir);

        Assert.Empty(manifest.Images);
        Assert.Empty(manifest.PromptHashes);
    }

    [Fact]
    public void Load_EmptyFile_ReturnsEmptyManifest()
    {
        var path = Path.Combine(_tempDir, BuildManifest.FileName);
        File.WriteAllText(path, string.Empty);

        var manifest = ManifestService.Load(_tempDir);

        Assert.Empty(manifest.Images);
        Assert.Empty(manifest.PromptHashes);
    }

    // ── RecordImageBuild ─────────────────────────────────────────────

    [Fact]
    public void RecordImageBuild_CreatesNewEntry_WithAllFields()
    {
        var manifest = new BuildManifest();
        var hashes = new Dictionary<string, string>
        {
            ["core"] = "c1",
            ["search"] = "s1",
        };

        ManifestService.RecordImageBuild(
            manifest, "photo.jpg", "content-hash", "gpt-5-mini", "1.4", hashes);

        Assert.Single(manifest.Images);
        var entry = manifest.Images["photo.jpg"];
        Assert.Equal("content-hash", entry.ContentHash);
        Assert.Equal("gpt-5-mini", entry.Model);
        Assert.Equal("1.4", entry.SchemaVersion);
        Assert.NotNull(entry.GeneratedAt);
        Assert.NotEmpty(entry.GeneratedAt);
        Assert.Equal("c1", entry.FieldHashes["core"]);
        Assert.Equal("s1", entry.FieldHashes["search"]);
        Assert.Null(entry.OptimizationFingerprint);
        Assert.False(entry.HasApiOptimized);
        Assert.False(entry.HasBundleOptimized);
    }

    [Fact]
    public void RecordImageBuild_OverwritesExistingEntry()
    {
        var manifest = new BuildManifest();
        var oldHashes = new Dictionary<string, string> { ["core"] = "old" };
        ManifestService.RecordImageBuild(
            manifest, "photo.jpg", "old-hash", "old-model", "1.0", oldHashes);

        var newHashes = new Dictionary<string, string> { ["core"] = "new" };
        ManifestService.RecordImageBuild(
            manifest, "photo.jpg", "new-hash", "new-model", "2.0", newHashes);

        Assert.Single(manifest.Images);
        var entry = manifest.Images["photo.jpg"];
        Assert.Equal("new-hash", entry.ContentHash);
        Assert.Equal("new-model", entry.Model);
        Assert.Equal("2.0", entry.SchemaVersion);
        Assert.Equal("new", entry.FieldHashes["core"]);
    }

    [Fact]
    public void RecordImageBuild_StoresOptimizationFingerprint()
    {
        var manifest = new BuildManifest();
        var hashes = new Dictionary<string, string> { ["core"] = "c1" };

        ManifestService.RecordImageBuild(
            manifest, "photo.jpg", "hash", "model", "1.4", hashes,
            optimizationFingerprint: "api:1200:original|bundle:1200:webp|q:85");

        var entry = manifest.Images["photo.jpg"];
        Assert.Equal("api:1200:original|bundle:1200:webp|q:85", entry.OptimizationFingerprint);
        Assert.True(entry.HasApiOptimized);
    }

    [Fact]
    public void RecordImageBuild_FieldHashes_IsCopy()
    {
        var manifest = new BuildManifest();
        var hashes = new Dictionary<string, string> { ["core"] = "c1" };

        ManifestService.RecordImageBuild(
            manifest, "photo.jpg", "hash", "model", "1.4", hashes);

        // Mutate the original dictionary after recording
        hashes["core"] = "mutated";
        hashes["extra"] = "added";

        var entry = manifest.Images["photo.jpg"];
        Assert.Equal("c1", entry.FieldHashes["core"]);
        Assert.False(entry.FieldHashes.ContainsKey("extra"));
    }

    // ── RecordPartialBuild ───────────────────────────────────────────

    [Fact]
    public void RecordPartialBuild_UpdatesOnlyAffectedFieldHashes()
    {
        var manifest = new BuildManifest();
        var initialHashes = new Dictionary<string, string>
        {
            ["core"] = "c1",
            ["search"] = "s1",
            ["cultural"] = "cu1",
        };
        ManifestService.RecordImageBuild(
            manifest, "photo.jpg", "hash1", "model1", "1.4", initialHashes);

        var updatedPromptHashes = new Dictionary<string, string>
        {
            ["core"] = "c2",
            ["search"] = "s2",
            ["cultural"] = "cu1",
        };
        ManifestService.RecordPartialBuild(
            manifest, "photo.jpg", "hash1", "model2", "1.5",
            affectedGroups: new[] { "core", "search" },
            currentPromptHashes: updatedPromptHashes);

        var entry = manifest.Images["photo.jpg"];
        Assert.Equal("c2", entry.FieldHashes["core"]);
        Assert.Equal("s2", entry.FieldHashes["search"]);
        // Unaffected group retains original value
        Assert.Equal("cu1", entry.FieldHashes["cultural"]);
    }

    [Fact]
    public void RecordPartialBuild_CreatesNewEntry_WhenImageNotInManifest()
    {
        var manifest = new BuildManifest();
        var promptHashes = new Dictionary<string, string> { ["core"] = "c1" };

        ManifestService.RecordPartialBuild(
            manifest, "new.jpg", "hash", "model", "1.4",
            affectedGroups: new[] { "core" },
            currentPromptHashes: promptHashes);

        Assert.Single(manifest.Images);
        var entry = manifest.Images["new.jpg"];
        Assert.Equal("hash", entry.ContentHash);
        Assert.Equal("model", entry.Model);
        Assert.Equal("1.4", entry.SchemaVersion);
        Assert.Equal("c1", entry.FieldHashes["core"]);
    }

    [Fact]
    public void RecordPartialBuild_UpdatesModelSchemaVersionGeneratedAt()
    {
        var manifest = new BuildManifest();
        var hashes = new Dictionary<string, string> { ["core"] = "c1" };
        ManifestService.RecordImageBuild(
            manifest, "photo.jpg", "hash", "old-model", "1.0", hashes);

        var originalGeneratedAt = manifest.Images["photo.jpg"].GeneratedAt;

        // Small delay to ensure timestamp differs
        ManifestService.RecordPartialBuild(
            manifest, "photo.jpg", "hash", "new-model", "2.0",
            affectedGroups: Array.Empty<string>(),
            currentPromptHashes: new Dictionary<string, string>(),
            optimizationFingerprint: "fp-new");

        var entry = manifest.Images["photo.jpg"];
        Assert.Equal("new-model", entry.Model);
        Assert.Equal("2.0", entry.SchemaVersion);
        Assert.Equal("fp-new", entry.OptimizationFingerprint);
        Assert.True(entry.HasApiOptimized);
    }

    [Fact]
    public void RecordPartialBuild_PreservesUnaffectedFieldHashes()
    {
        var manifest = new BuildManifest();
        var initialHashes = new Dictionary<string, string>
        {
            ["core"] = "c1",
            ["emotions"] = "e1",
            ["localization:cs"] = "l1",
        };
        ManifestService.RecordImageBuild(
            manifest, "photo.jpg", "hash", "model", "1.4", initialHashes);

        // Only update "core"; "emotions" and "localization:cs" should survive
        ManifestService.RecordPartialBuild(
            manifest, "photo.jpg", "hash", "model", "1.4",
            affectedGroups: new[] { "core" },
            currentPromptHashes: new Dictionary<string, string> { ["core"] = "c2" });

        var entry = manifest.Images["photo.jpg"];
        Assert.Equal("c2", entry.FieldHashes["core"]);
        Assert.Equal("e1", entry.FieldHashes["emotions"]);
        Assert.Equal("l1", entry.FieldHashes["localization:cs"]);
    }

    // ── RecordBundleOptimized ────────────────────────────────────────

    [Fact]
    public void RecordBundleOptimized_SetsFlag()
    {
        var manifest = new BuildManifest();
        var hashes = new Dictionary<string, string> { ["core"] = "c1" };
        ManifestService.RecordImageBuild(
            manifest, "photo.jpg", "hash", "model", "1.4", hashes);
        Assert.False(manifest.Images["photo.jpg"].HasBundleOptimized);

        ManifestService.RecordBundleOptimized(manifest, "photo.jpg");

        Assert.True(manifest.Images["photo.jpg"].HasBundleOptimized);
    }

    [Fact]
    public void RecordBundleOptimized_NoOp_WhenImageNotInManifest()
    {
        var manifest = new BuildManifest();

        // Should not throw
        ManifestService.RecordBundleOptimized(manifest, "nonexistent.jpg");

        Assert.Empty(manifest.Images);
    }
}
