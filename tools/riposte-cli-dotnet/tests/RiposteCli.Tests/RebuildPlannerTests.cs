using RiposteCli.Models;

namespace RiposteCli.Tests;

public class RebuildPlannerTests : IDisposable
{
    private readonly string _tempDir;
    private readonly string _outputDir;

    // Minimal valid 1x1 white PNG (67 bytes)
    private static readonly byte[] MinimalPng =
    [
        0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG signature
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, // IHDR chunk
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, // 1x1
        0x08, 0x02, 0x00, 0x00, 0x00, 0x90, 0x77, 0x53, // 8-bit RGB
        0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, // IDAT chunk
        0x54, 0x08, 0xD7, 0x63, 0xF8, 0xCF, 0xC0, 0x00, // compressed data
        0x00, 0x00, 0x02, 0x00, 0x01, 0xE2, 0x21, 0xBC, // ...
        0x33, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, // IEND chunk
        0x44, 0xAE, 0x42, 0x60, 0x82,
    ];

    private const string TestModel = "gpt-5-mini";
    private const string TestSchema = "1.4";

    public RebuildPlannerTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), $"riposte-rebuild-test-{Guid.NewGuid()}");
        _outputDir = Path.Combine(_tempDir, "output");
        Directory.CreateDirectory(_tempDir);
        Directory.CreateDirectory(_outputDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempDir))
            Directory.Delete(_tempDir, true);
    }

    private string CreateTestImage(string filename = "test.png")
    {
        var path = Path.Combine(_tempDir, filename);
        File.WriteAllBytes(path, MinimalPng);
        return path;
    }

    private void CreateSidecar(string imageFilename, string jsonContent = "{}")
    {
        var sidecarPath = Path.Combine(_outputDir, imageFilename + ".json");
        File.WriteAllText(sidecarPath, jsonContent);
    }

    private string GetContentHash(string imagePath) =>
        ImageHashService.GetContentHash(imagePath);

    private static Dictionary<string, string> MakePromptHashes(
        string coreHash = "h_core",
        string searchHash = "h_search",
        string culturalHash = "h_cultural",
        string emotionsHash = "h_emotions") =>
        new()
        {
            [PromptHasher.GroupCore] = coreHash,
            [PromptHasher.GroupSearch] = searchHash,
            [PromptHasher.GroupCultural] = culturalHash,
            [PromptHasher.GroupEmotions] = emotionsHash,
        };

    private ImageManifestEntry MakeManifestEntry(
        string contentHash,
        string model = TestModel,
        Dictionary<string, string>? fieldHashes = null,
        string? optFingerprint = null,
        bool hasApiOptimized = true,
        bool hasBundleOptimized = true) =>
        new()
        {
            ContentHash = contentHash,
            Model = model,
            GeneratedAt = "2025-01-01T00:00:00Z",
            SchemaVersion = TestSchema,
            FieldHashes = fieldHashes ?? MakePromptHashes(),
            OptimizationFingerprint = optFingerprint,
            HasApiOptimized = hasApiOptimized,
            HasBundleOptimized = hasBundleOptimized,
        };

    [Fact]
    public void NoSidecar_ReturnsFull()
    {
        var imagePath = CreateTestImage();
        // No sidecar created
        var manifest = new BuildManifest();

        var plan = RebuildPlanner.PlanForImage(
            imagePath, manifest, MakePromptHashes(), TestModel, TestSchema, _outputDir);

        Assert.Equal(RebuildScope.Full, plan.Scope);
        Assert.True(plan.NeedsReoptimization);
        Assert.Contains("no existing sidecar", plan.Reason);
    }

    [Fact]
    public void NoManifestEntry_ReturnsFull()
    {
        var imagePath = CreateTestImage("legacy.png");
        CreateSidecar("legacy.png");
        // Manifest exists but has no entry for this image
        var manifest = new BuildManifest();

        var plan = RebuildPlanner.PlanForImage(
            imagePath, manifest, MakePromptHashes(), TestModel, TestSchema, _outputDir);

        Assert.Equal(RebuildScope.Full, plan.Scope);
        Assert.True(plan.NeedsReoptimization);
        Assert.Contains("not in build manifest", plan.Reason);
    }

    [Fact]
    public void ContentHashChanged_ReturnsFull()
    {
        var imagePath = CreateTestImage("modified.png");
        CreateSidecar("modified.png");

        var manifest = new BuildManifest
        {
            Images = { ["modified.png"] = MakeManifestEntry(contentHash: "stale_hash_does_not_match") },
        };

        var plan = RebuildPlanner.PlanForImage(
            imagePath, manifest, MakePromptHashes(), TestModel, TestSchema, _outputDir);

        Assert.Equal(RebuildScope.Full, plan.Scope);
        Assert.True(plan.NeedsReoptimization);
        Assert.Contains("image content changed", plan.Reason);
    }

    [Fact]
    public void ModelChanged_ReturnsFull()
    {
        var imagePath = CreateTestImage("model.png");
        CreateSidecar("model.png");
        var contentHash = GetContentHash(imagePath);

        var manifest = new BuildManifest
        {
            Images = { ["model.png"] = MakeManifestEntry(contentHash, model: "old-model") },
        };

        var plan = RebuildPlanner.PlanForImage(
            imagePath, manifest, MakePromptHashes(), TestModel, TestSchema, _outputDir);

        Assert.Equal(RebuildScope.Full, plan.Scope);
        Assert.Contains("model changed", plan.Reason);
        Assert.Contains("old-model", plan.Reason);
    }

    [Fact]
    public void AllFieldHashesMatch_ReturnsSkip()
    {
        var imagePath = CreateTestImage("uptodate.png");
        CreateSidecar("uptodate.png");
        var contentHash = GetContentHash(imagePath);
        var hashes = MakePromptHashes();

        var manifest = new BuildManifest
        {
            Images = { ["uptodate.png"] = MakeManifestEntry(contentHash, fieldHashes: new Dictionary<string, string>(hashes)) },
        };

        var plan = RebuildPlanner.PlanForImage(
            imagePath, manifest, hashes, TestModel, TestSchema, _outputDir);

        Assert.Equal(RebuildScope.Skip, plan.Scope);
        Assert.Empty(plan.AffectedGroups);
        Assert.Empty(plan.RemovedGroups);
        Assert.False(plan.NeedsReoptimization);
        Assert.Contains("all field groups up to date", plan.Reason);
    }

    [Fact]
    public void SingleFieldGroupPromptChanged_ReturnsPartial()
    {
        var imagePath = CreateTestImage("partial.png");
        CreateSidecar("partial.png");
        var contentHash = GetContentHash(imagePath);

        var storedHashes = MakePromptHashes();
        var currentHashes = MakePromptHashes(searchHash: "h_search_v2");

        var manifest = new BuildManifest
        {
            Images = { ["partial.png"] = MakeManifestEntry(contentHash, fieldHashes: storedHashes) },
        };

        var plan = RebuildPlanner.PlanForImage(
            imagePath, manifest, currentHashes, TestModel, TestSchema, _outputDir);

        Assert.Equal(RebuildScope.Partial, plan.Scope);
        Assert.Single(plan.AffectedGroups);
        Assert.Contains(PromptHasher.GroupSearch, plan.AffectedGroups);
        Assert.Contains("search", plan.Reason);
        Assert.Contains("prompt changed", plan.Reason);
    }

    [Fact]
    public void NewFieldGroupAdded_ReturnsPartial()
    {
        var imagePath = CreateTestImage("newgroup.png");
        CreateSidecar("newgroup.png");
        var contentHash = GetContentHash(imagePath);

        // Stored hashes only have core + search (missing cultural + emotions)
        var storedHashes = new Dictionary<string, string>
        {
            [PromptHasher.GroupCore] = "h_core",
            [PromptHasher.GroupSearch] = "h_search",
        };
        var currentHashes = new Dictionary<string, string>
        {
            [PromptHasher.GroupCore] = "h_core",
            [PromptHasher.GroupSearch] = "h_search",
            [PromptHasher.GroupCultural] = "h_cultural",
        };

        var manifest = new BuildManifest
        {
            Images = { ["newgroup.png"] = MakeManifestEntry(contentHash, fieldHashes: storedHashes) },
        };

        var plan = RebuildPlanner.PlanForImage(
            imagePath, manifest, currentHashes, TestModel, TestSchema, _outputDir);

        Assert.Equal(RebuildScope.Partial, plan.Scope);
        Assert.Contains(PromptHasher.GroupCultural, plan.AffectedGroups);
        Assert.Contains("cultural", plan.Reason);
        Assert.Contains("new field group", plan.Reason);
    }

    [Fact]
    public void AllBaseGroupsStale_ReturnsFull()
    {
        var imagePath = CreateTestImage("allstale.png");
        CreateSidecar("allstale.png");
        var contentHash = GetContentHash(imagePath);

        var storedHashes = MakePromptHashes("old1", "old2", "old3", "old4");
        var currentHashes = MakePromptHashes("new1", "new2", "new3", "new4");

        var manifest = new BuildManifest
        {
            Images = { ["allstale.png"] = MakeManifestEntry(contentHash, fieldHashes: storedHashes) },
        };

        var plan = RebuildPlanner.PlanForImage(
            imagePath, manifest, currentHashes, TestModel, TestSchema, _outputDir);

        Assert.Equal(RebuildScope.Full, plan.Scope);
        Assert.Contains("all base groups stale", plan.Reason);
    }

    [Fact]
    public void LanguageAdded_ReturnsPartial()
    {
        var imagePath = CreateTestImage("lang.png");
        CreateSidecar("lang.png");
        var contentHash = GetContentHash(imagePath);

        var storedHashes = MakePromptHashes();
        var currentHashes = MakePromptHashes();
        currentHashes[PromptHasher.LocalizationGroup("cs")] = "h_cs";

        var manifest = new BuildManifest
        {
            Images = { ["lang.png"] = MakeManifestEntry(contentHash, fieldHashes: new Dictionary<string, string>(storedHashes)) },
        };

        var plan = RebuildPlanner.PlanForImage(
            imagePath, manifest, currentHashes, TestModel, TestSchema, _outputDir);

        Assert.Equal(RebuildScope.Partial, plan.Scope);
        Assert.Contains("localization:cs", plan.AffectedGroups);
        Assert.Contains("new field group", plan.Reason);
    }

    [Fact]
    public void RemovedGroups_AreDetected()
    {
        var imagePath = CreateTestImage("removed.png");
        CreateSidecar("removed.png");
        var contentHash = GetContentHash(imagePath);

        // Manifest has a localization:de entry that no longer exists in current prompts
        var storedHashes = MakePromptHashes();
        storedHashes[PromptHasher.LocalizationGroup("de")] = "h_de";

        var currentHashes = MakePromptHashes();

        var manifest = new BuildManifest
        {
            Images = { ["removed.png"] = MakeManifestEntry(contentHash, fieldHashes: storedHashes) },
        };

        var plan = RebuildPlanner.PlanForImage(
            imagePath, manifest, currentHashes, TestModel, TestSchema, _outputDir);

        Assert.Equal(RebuildScope.Skip, plan.Scope);
        Assert.Contains("localization:de", plan.RemovedGroups);
        Assert.True(plan.NeedsStripping);
        Assert.Contains("stripping", plan.Reason);
    }

    [Fact]
    public void OptimizationFingerprintChanged_NeedsReoptimization()
    {
        var imagePath = CreateTestImage("reopt.png");
        CreateSidecar("reopt.png");
        var contentHash = GetContentHash(imagePath);
        var hashes = MakePromptHashes();

        var oldConfig = new OptimizationConfig { Quality = 80 };
        var newConfig = new OptimizationConfig { Quality = 95 };

        var manifest = new BuildManifest
        {
            Images =
            {
                ["reopt.png"] = MakeManifestEntry(
                    contentHash,
                    fieldHashes: new Dictionary<string, string>(hashes),
                    optFingerprint: oldConfig.Fingerprint(),
                    hasApiOptimized: true,
                    hasBundleOptimized: true),
            },
        };

        var plan = RebuildPlanner.PlanForImage(
            imagePath, manifest, hashes, TestModel, TestSchema, _outputDir, newConfig.Fingerprint());

        Assert.Equal(RebuildScope.Skip, plan.Scope);
        Assert.True(plan.NeedsReoptimization);
        Assert.Contains("optimization config changed", plan.Reason);
    }

    [Fact]
    public void Summarize_ReturnsCorrectCounts()
    {
        var plans = new List<ImageRebuildPlan>
        {
            new() { ImagePath = "a.png", Scope = RebuildScope.Skip, NeedsReoptimization = false, Reason = "up to date" },
            new() { ImagePath = "b.png", Scope = RebuildScope.Skip, NeedsReoptimization = false, Reason = "up to date" },
            new() { ImagePath = "c.png", Scope = RebuildScope.Full, NeedsReoptimization = true, Reason = "no sidecar" },
            new() { ImagePath = "d.png", Scope = RebuildScope.Partial, AffectedGroups = ["search"], Reason = "prompt changed" },
            new() { ImagePath = "e.png", Scope = RebuildScope.Partial, AffectedGroups = ["core"], Reason = "prompt changed" },
            new() { ImagePath = "f.png", Scope = RebuildScope.Skip, NeedsReoptimization = true, Reason = "reopt needed" },
        };

        var (skip, full, partial, reoptimize) = RebuildPlanner.Summarize(plans);

        Assert.Equal(2, skip);
        Assert.Equal(1, full);
        Assert.Equal(2, partial);
        Assert.Equal(1, reoptimize);
    }

    [Fact]
    public void Plan_ProcessesMultipleImages()
    {
        var img1 = CreateTestImage("one.png");
        var img2 = CreateTestImage("two.png");
        // Only create sidecar for img2
        CreateSidecar("two.png");

        var contentHash2 = GetContentHash(img2);
        var hashes = MakePromptHashes();

        var manifest = new BuildManifest
        {
            Images = { ["two.png"] = MakeManifestEntry(contentHash2, fieldHashes: new Dictionary<string, string>(hashes)) },
        };

        var plans = RebuildPlanner.Plan(
            [img1, img2], manifest, hashes, TestModel, TestSchema, _outputDir);

        Assert.Equal(2, plans.Count);
        Assert.Equal(RebuildScope.Full, plans[0].Scope);   // no sidecar
        Assert.Equal(RebuildScope.Skip, plans[1].Scope);   // up to date
    }

    [Fact]
    public void MissingOptimizedFiles_NeedsReoptimization()
    {
        var imagePath = CreateTestImage("noopt.png");
        CreateSidecar("noopt.png");
        var contentHash = GetContentHash(imagePath);
        var hashes = MakePromptHashes();
        var optConfig = new OptimizationConfig();

        var manifest = new BuildManifest
        {
            Images =
            {
                ["noopt.png"] = MakeManifestEntry(
                    contentHash,
                    fieldHashes: new Dictionary<string, string>(hashes),
                    optFingerprint: optConfig.Fingerprint(),
                    hasApiOptimized: false,
                    hasBundleOptimized: true),
            },
        };

        var plan = RebuildPlanner.PlanForImage(
            imagePath, manifest, hashes, TestModel, TestSchema, _outputDir, optConfig.Fingerprint());

        Assert.Equal(RebuildScope.Skip, plan.Scope);
        Assert.True(plan.NeedsReoptimization);
    }

    [Fact]
    public void NullOptimizationFingerprint_InEntry_NeedsReoptimization()
    {
        var imagePath = CreateTestImage("nullopt.png");
        CreateSidecar("nullopt.png");
        var contentHash = GetContentHash(imagePath);
        var hashes = MakePromptHashes();
        var optConfig = new OptimizationConfig();

        var manifest = new BuildManifest
        {
            Images =
            {
                ["nullopt.png"] = MakeManifestEntry(
                    contentHash,
                    fieldHashes: new Dictionary<string, string>(hashes),
                    optFingerprint: null,
                    hasApiOptimized: true,
                    hasBundleOptimized: true),
            },
        };

        var plan = RebuildPlanner.PlanForImage(
            imagePath, manifest, hashes, TestModel, TestSchema, _outputDir, optConfig.Fingerprint());

        Assert.Equal(RebuildScope.Skip, plan.Scope);
        Assert.True(plan.NeedsReoptimization);
    }

    [Fact]
    public void NoOptimizationConfig_SkipsReoptCheck()
    {
        var imagePath = CreateTestImage("noconfig.png");
        CreateSidecar("noconfig.png");
        var contentHash = GetContentHash(imagePath);
        var hashes = MakePromptHashes();

        var manifest = new BuildManifest
        {
            Images = { ["noconfig.png"] = MakeManifestEntry(contentHash, fieldHashes: new Dictionary<string, string>(hashes)) },
        };

        // No optimization fingerprint passed → NeedsReoptimization should be false
        var plan = RebuildPlanner.PlanForImage(
            imagePath, manifest, hashes, TestModel, TestSchema, _outputDir, currentOptFingerprint: null);

        Assert.Equal(RebuildScope.Skip, plan.Scope);
        Assert.False(plan.NeedsReoptimization);
    }

    [Fact]
    public void AllBaseGroupsStale_WithLocalization_StillReturnsFull()
    {
        var imagePath = CreateTestImage("stalepluscz.png");
        CreateSidecar("stalepluscz.png");
        var contentHash = GetContentHash(imagePath);

        var storedHashes = MakePromptHashes("old1", "old2", "old3", "old4");
        storedHashes[PromptHasher.LocalizationGroup("cs")] = "h_cs";

        var currentHashes = MakePromptHashes("new1", "new2", "new3", "new4");
        currentHashes[PromptHasher.LocalizationGroup("cs")] = "h_cs"; // localization unchanged

        var manifest = new BuildManifest
        {
            Images = { ["stalepluscz.png"] = MakeManifestEntry(contentHash, fieldHashes: storedHashes) },
        };

        var plan = RebuildPlanner.PlanForImage(
            imagePath, manifest, currentHashes, TestModel, TestSchema, _outputDir);

        // All 4 base groups stale → optimization upgrades to Full
        Assert.Equal(RebuildScope.Full, plan.Scope);
        Assert.Contains("all base groups stale", plan.Reason);
    }

    [Fact]
    public void RemovedGroups_WithPartialRebuild_BothPopulated()
    {
        var imagePath = CreateTestImage("mixed.png");
        CreateSidecar("mixed.png");
        var contentHash = GetContentHash(imagePath);

        var storedHashes = MakePromptHashes();
        storedHashes[PromptHasher.LocalizationGroup("fr")] = "h_fr";

        // Change one base group AND remove localization:fr
        var currentHashes = MakePromptHashes(coreHash: "h_core_v2");

        var manifest = new BuildManifest
        {
            Images = { ["mixed.png"] = MakeManifestEntry(contentHash, fieldHashes: storedHashes) },
        };

        var plan = RebuildPlanner.PlanForImage(
            imagePath, manifest, currentHashes, TestModel, TestSchema, _outputDir);

        Assert.Equal(RebuildScope.Partial, plan.Scope);
        Assert.Contains(PromptHasher.GroupCore, plan.AffectedGroups);
        Assert.Contains("localization:fr", plan.RemovedGroups);
        Assert.True(plan.NeedsStripping);
    }
}
