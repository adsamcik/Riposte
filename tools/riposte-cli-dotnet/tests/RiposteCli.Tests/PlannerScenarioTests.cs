using RiposteCli.Models;

namespace RiposteCli.Tests;

public sealed class PlannerScenarioTests : IDisposable
{
    private readonly string _tempDir;
    private readonly string _imagesDir;
    private readonly string _outputDir;

    private const string SchemaVersion = "1.4";
    private const string MiniModel = "gpt-5-mini";
    private const string FullModel = "gpt-5";

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

    private static readonly byte[] MinimalPngAlt =
    [
        0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x02, 0x00, 0x00, 0x00, 0x90, 0x77, 0x53,
        0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,
        0x54, 0x08, 0xD7, 0x63, 0xF8, 0x4F, 0xC0, 0x00,
        0x00, 0x00, 0x02, 0x00, 0x01, 0xE2, 0x21, 0xBC,
        0x33, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E,
        0x44, 0xAE, 0x42, 0x60, 0x82,
    ];

    public PlannerScenarioTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), $"riposte-planner-scenarios-{Guid.NewGuid()}");
        _imagesDir = Path.Combine(_tempDir, "images");
        _outputDir = Path.Combine(_tempDir, "output");
        Directory.CreateDirectory(_imagesDir);
        Directory.CreateDirectory(_outputDir);
        OutputPaths.EnsureDirectories(_outputDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempDir))
            Directory.Delete(_tempDir, recursive: true);
    }

    [Fact]
    public void Lifecycle_FreshThenRepeat_AllSkipOnSecondRun()
    {
        CreateImages(5);
        var run1Images = SidecarService.GetImagesInFolder(_imagesDir);
        var run1Manifest = ManifestService.Load(_outputDir);
        var promptHashes = PromptHasher.ComputeAll(["en"]);

        var run1Plans = RebuildPlanner.Plan(run1Images, run1Manifest, promptHashes, MiniModel, SchemaVersion, _outputDir);
        AssertPlanCounts(run1Plans, skip: 0, full: 5, partial: 0);
        RecordPlannedBuilds(run1Plans, run1Manifest, promptHashes, MiniModel);
        ManifestService.Save(_outputDir, run1Manifest);

        var run2Images = SidecarService.GetImagesInFolder(_imagesDir);
        var run2Manifest = ManifestService.Load(_outputDir);
        var run2Plans = RebuildPlanner.Plan(run2Images, run2Manifest, promptHashes, MiniModel, SchemaVersion, _outputDir);
        AssertPlanCounts(run2Plans, skip: 5, full: 0, partial: 0);
    }

    [Fact]
    public void Lifecycle_AddNewImages_OnlyNewOnesFull()
    {
        CreateImages(5);
        var promptHashes = PromptHasher.ComputeAll(["en"]);
        var run1Manifest = ManifestService.Load(_outputDir);
        var run1Plans = RebuildPlanner.Plan(
            SidecarService.GetImagesInFolder(_imagesDir), run1Manifest, promptHashes, MiniModel, SchemaVersion, _outputDir);
        AssertPlanCounts(run1Plans, skip: 0, full: 5, partial: 0);
        RecordPlannedBuilds(run1Plans, run1Manifest, promptHashes, MiniModel);
        ManifestService.Save(_outputDir, run1Manifest);

        CreateImages(2, startIndex: 6);
        var run2Manifest = ManifestService.Load(_outputDir);
        var run2Plans = RebuildPlanner.Plan(
            SidecarService.GetImagesInFolder(_imagesDir), run2Manifest, promptHashes, MiniModel, SchemaVersion, _outputDir);
        AssertPlanCounts(run2Plans, skip: 5, full: 2, partial: 0);
    }

    [Fact]
    public void Lifecycle_AddLanguage_AllBecomeLocalizationPartial()
    {
        CreateImages(5);
        var run1Hashes = PromptHasher.ComputeAll(["en"]);
        var run1Manifest = ManifestService.Load(_outputDir);
        var run1Plans = RebuildPlanner.Plan(
            SidecarService.GetImagesInFolder(_imagesDir), run1Manifest, run1Hashes, MiniModel, SchemaVersion, _outputDir);
        AssertPlanCounts(run1Plans, skip: 0, full: 5, partial: 0);
        RecordPlannedBuilds(run1Plans, run1Manifest, run1Hashes, MiniModel);
        ManifestService.Save(_outputDir, run1Manifest);

        var run2Hashes = PromptHasher.ComputeAll(["en", "cs"]);
        var run2Manifest = ManifestService.Load(_outputDir);
        var run2Plans = RebuildPlanner.Plan(
            SidecarService.GetImagesInFolder(_imagesDir), run2Manifest, run2Hashes, MiniModel, SchemaVersion, _outputDir);

        AssertPlanCounts(run2Plans, skip: 0, full: 0, partial: 5);
        Assert.All(run2Plans, p =>
        {
            Assert.Contains(PromptHasher.LocalizationGroup("cs"), p.AffectedGroups);
            Assert.Equal(RebuildScope.Partial, p.Scope);
        });
    }

    [Fact]
    public void Lifecycle_ModelChange_AllFullOnSecondRun()
    {
        CreateImages(5);
        var promptHashes = PromptHasher.ComputeAll(["en"]);
        var run1Manifest = ManifestService.Load(_outputDir);
        var run1Plans = RebuildPlanner.Plan(
            SidecarService.GetImagesInFolder(_imagesDir), run1Manifest, promptHashes, MiniModel, SchemaVersion, _outputDir);
        AssertPlanCounts(run1Plans, skip: 0, full: 5, partial: 0);
        RecordPlannedBuilds(run1Plans, run1Manifest, promptHashes, MiniModel);
        ManifestService.Save(_outputDir, run1Manifest);

        var run2Manifest = ManifestService.Load(_outputDir);
        var run2Plans = RebuildPlanner.Plan(
            SidecarService.GetImagesInFolder(_imagesDir), run2Manifest, promptHashes, FullModel, SchemaVersion, _outputDir);
        AssertPlanCounts(run2Plans, skip: 0, full: 5, partial: 0);
    }

    [Fact]
    public void Lifecycle_DeleteImage_StaleManifestEntryIgnored()
    {
        CreateImages(5);
        var promptHashes = PromptHasher.ComputeAll(["en"]);
        var run1Manifest = ManifestService.Load(_outputDir);
        var run1Plans = RebuildPlanner.Plan(
            SidecarService.GetImagesInFolder(_imagesDir), run1Manifest, promptHashes, MiniModel, SchemaVersion, _outputDir);
        AssertPlanCounts(run1Plans, skip: 0, full: 5, partial: 0);
        RecordPlannedBuilds(run1Plans, run1Manifest, promptHashes, MiniModel);
        ManifestService.Save(_outputDir, run1Manifest);

        File.Delete(Path.Combine(_imagesDir, "meme_001.png"));
        var run2Manifest = ManifestService.Load(_outputDir);
        Assert.Equal(5, run2Manifest.Images.Count); // stale entry persists in manifest
        Assert.True(run2Manifest.Images.ContainsKey("meme_001.png"));
        var run2Plans = RebuildPlanner.Plan(
            SidecarService.GetImagesInFolder(_imagesDir), run2Manifest, promptHashes, MiniModel, SchemaVersion, _outputDir);
        AssertPlanCounts(run2Plans, skip: 4, full: 0, partial: 0);
        Assert.DoesNotContain(run2Plans, p => Path.GetFileName(p.ImagePath) == "meme_001.png");
    }

    [Fact]
    public void Lifecycle_ModifyImage_OnlyChangedImageFull()
    {
        CreateImages(5);
        var promptHashes = PromptHasher.ComputeAll(["en"]);
        var run1Manifest = ManifestService.Load(_outputDir);
        var run1Plans = RebuildPlanner.Plan(
            SidecarService.GetImagesInFolder(_imagesDir), run1Manifest, promptHashes, MiniModel, SchemaVersion, _outputDir);
        AssertPlanCounts(run1Plans, skip: 0, full: 5, partial: 0);
        RecordPlannedBuilds(run1Plans, run1Manifest, promptHashes, MiniModel);
        ManifestService.Save(_outputDir, run1Manifest);

        File.WriteAllBytes(Path.Combine(_imagesDir, "meme_003.png"), MinimalPngAlt);
        var run2Manifest = ManifestService.Load(_outputDir);
        var run2Plans = RebuildPlanner.Plan(
            SidecarService.GetImagesInFolder(_imagesDir), run2Manifest, promptHashes, MiniModel, SchemaVersion, _outputDir);
        AssertPlanCounts(run2Plans, skip: 4, full: 1, partial: 0);
    }

    [Fact]
    public void Lifecycle_RemoveLanguage_SkipWithStrippingMarkers()
    {
        CreateImages(5);
        var run1Hashes = PromptHasher.ComputeAll(["en", "cs"]);
        var run1Manifest = ManifestService.Load(_outputDir);
        var run1Plans = RebuildPlanner.Plan(
            SidecarService.GetImagesInFolder(_imagesDir), run1Manifest, run1Hashes, MiniModel, SchemaVersion, _outputDir);
        AssertPlanCounts(run1Plans, skip: 0, full: 5, partial: 0);
        RecordPlannedBuilds(run1Plans, run1Manifest, run1Hashes, MiniModel);
        ManifestService.Save(_outputDir, run1Manifest);

        var run2Hashes = PromptHasher.ComputeAll(["en"]);
        var run2Manifest = ManifestService.Load(_outputDir);
        var run2Plans = RebuildPlanner.Plan(
            SidecarService.GetImagesInFolder(_imagesDir), run2Manifest, run2Hashes, MiniModel, SchemaVersion, _outputDir);

        AssertPlanCounts(run2Plans, skip: 5, full: 0, partial: 0);
        Assert.All(run2Plans, p =>
        {
            Assert.Equal(RebuildScope.Skip, p.Scope);
            Assert.True(p.NeedsStripping);
            var removedGroup = Assert.Single(p.RemovedGroups);
            Assert.Equal(PromptHasher.LocalizationGroup("cs"), removedGroup);
        });
    }

    [Fact]
    public void Lifecycle_OptimizationConfigChange_ReoptimizationOnly()
    {
        CreateImages(5);
        var promptHashes = PromptHasher.ComputeAll(["en"]);
        var run1Opt = new OptimizationConfig { ApiMaxDimension = 1200, BundleMaxDimension = 1200, Quality = 85 };
        var run1Manifest = ManifestService.Load(_outputDir);
        var run1Plans = RebuildPlanner.Plan(
            SidecarService.GetImagesInFolder(_imagesDir), run1Manifest, promptHashes, MiniModel, SchemaVersion, _outputDir, run1Opt);
        AssertPlanCounts(run1Plans, skip: 0, full: 5, partial: 0);
        RecordPlannedBuilds(run1Plans, run1Manifest, promptHashes, MiniModel, run1Opt.Fingerprint());
        ManifestService.Save(_outputDir, run1Manifest);

        var run2Opt = new OptimizationConfig { ApiMaxDimension = 800, BundleMaxDimension = 1200, Quality = 85 };
        var run2Manifest = ManifestService.Load(_outputDir);
        var run2Plans = RebuildPlanner.Plan(
            SidecarService.GetImagesInFolder(_imagesDir), run2Manifest, promptHashes, MiniModel, SchemaVersion, _outputDir, run2Opt);

        AssertPlanCounts(run2Plans, skip: 0, full: 0, partial: 0, reopt: 5);
        Assert.All(run2Plans, p =>
        {
            Assert.Equal(RebuildScope.Skip, p.Scope);
            Assert.True(p.NeedsReoptimization);
        });
    }

    [Fact]
    public void Lifecycle_ModelAndPromptChange_AllFullNotPartial()
    {
        CreateImages(3);
        var run1Hashes = PromptHasher.ComputeAll(["en"]);
        var run1Manifest = ManifestService.Load(_outputDir);
        var run1Plans = RebuildPlanner.Plan(
            SidecarService.GetImagesInFolder(_imagesDir), run1Manifest, run1Hashes, MiniModel, SchemaVersion, _outputDir);
        AssertPlanCounts(run1Plans, skip: 0, full: 3, partial: 0);
        RecordPlannedBuilds(run1Plans, run1Manifest, run1Hashes, MiniModel);
        ManifestService.Save(_outputDir, run1Manifest);

        // Change BOTH the model AND add a language (new prompt hashes)
        var run2Hashes = PromptHasher.ComputeAll(["en", "cs"]);
        var run2Manifest = ManifestService.Load(_outputDir);
        var run2Plans = RebuildPlanner.Plan(
            SidecarService.GetImagesInFolder(_imagesDir), run2Manifest, run2Hashes, FullModel, SchemaVersion, _outputDir);

        // Model change triggers Full before prompt comparison — must not be Partial
        AssertPlanCounts(run2Plans, skip: 0, full: 3, partial: 0);
    }

    [Fact]
    public void Lifecycle_CorruptedManifest_AllFull()
    {
        CreateImages(3);
        var promptHashes = PromptHasher.ComputeAll(["en"]);
        var run1Manifest = ManifestService.Load(_outputDir);
        var run1Plans = RebuildPlanner.Plan(
            SidecarService.GetImagesInFolder(_imagesDir), run1Manifest, promptHashes, MiniModel, SchemaVersion, _outputDir);
        AssertPlanCounts(run1Plans, skip: 0, full: 3, partial: 0);
        RecordPlannedBuilds(run1Plans, run1Manifest, promptHashes, MiniModel);
        ManifestService.Save(_outputDir, run1Manifest);

        // Corrupt the manifest file between runs
        var manifestPath = Path.Combine(_outputDir, BuildManifest.FileName);
        File.WriteAllText(manifestPath, "{{not valid json!@#$%");

        var run2Manifest = ManifestService.Load(_outputDir);
        Assert.Empty(run2Manifest.Images); // corruption → empty manifest
        var run2Plans = RebuildPlanner.Plan(
            SidecarService.GetImagesInFolder(_imagesDir), run2Manifest, promptHashes, MiniModel, SchemaVersion, _outputDir);

        // Sidecars exist but manifest is empty → all Full (legacy sidecar path)
        AssertPlanCounts(run2Plans, skip: 0, full: 3, partial: 0);
    }

    [Fact]
    public void Lifecycle_SchemaVersionBump_AllFullOnSecondRun()
    {
        const string oldSchema = "1.3";
        const string newSchema = "1.4";

        CreateImages(5);
        var promptHashes = PromptHasher.ComputeAll(["en"]);
        var run1Manifest = ManifestService.Load(_outputDir);
        var run1Plans = RebuildPlanner.Plan(
            SidecarService.GetImagesInFolder(_imagesDir), run1Manifest, promptHashes, MiniModel, oldSchema, _outputDir);
        AssertPlanCounts(run1Plans, skip: 0, full: 5, partial: 0);
        RecordPlannedBuilds(run1Plans, run1Manifest, promptHashes, MiniModel, schemaVersion: oldSchema);
        ManifestService.Save(_outputDir, run1Manifest);

        // Re-run with new schema version — all images should be rebuilt
        var run2Manifest = ManifestService.Load(_outputDir);
        var run2Plans = RebuildPlanner.Plan(
            SidecarService.GetImagesInFolder(_imagesDir), run2Manifest, promptHashes, MiniModel, newSchema, _outputDir);
        AssertPlanCounts(run2Plans, skip: 0, full: 5, partial: 0);
        Assert.All(run2Plans, p => Assert.Contains("schema version changed", p.Reason));
    }

    private void CreateImages(int count, int startIndex = 1)
    {
        for (var i = 0; i < count; i++)
        {
            var index = startIndex + i;
            var fileName = $"meme_{index:000}.png";
            var bytes = index % 2 == 0 ? MinimalPngAlt : MinimalPng;
            File.WriteAllBytes(Path.Combine(_imagesDir, fileName), bytes);
        }
    }

    private void RecordPlannedBuilds(
        IReadOnlyList<ImageRebuildPlan> plans,
        BuildManifest manifest,
        Dictionary<string, string> promptHashes,
        string model,
        string? optimizationFingerprint = null,
        string schemaVersion = SchemaVersion)
    {
        foreach (var plan in plans.Where(p => p.Scope is RebuildScope.Full or RebuildScope.Partial))
        {
            var imagePath = plan.ImagePath;
            var fileName = Path.GetFileName(imagePath);
            var contentHash = ImageHashService.GetContentHash(imagePath);

            SidecarService.WriteSidecar(imagePath, CreateSidecar(contentHash), _outputDir);
            ManifestService.RecordImageBuild(
                manifest,
                fileName,
                contentHash,
                model,
                schemaVersion,
                promptHashes,
                optimizationFingerprint);
            ManifestService.RecordBundleOptimized(manifest, fileName);
        }
    }

    private static SidecarMetadata CreateSidecar(string contentHash) =>
        new()
        {
            Emojis = ["😂", "🔥"],
            Title = "Planner test meme",
            Description = "Sidecar used by planner lifecycle tests",
            Tags = ["planner", "test"],
            SearchPhrases = ["planner scenario"],
            PrimaryLanguage = "en",
            ContentHash = contentHash,
        };

    private static void AssertPlanCounts(
        IReadOnlyList<ImageRebuildPlan> plans,
        int skip,
        int full,
        int partial,
        int reopt = 0)
    {
        Assert.Equal(skip + full + partial + reopt, plans.Count);
        var summary = RebuildPlanner.Summarize(plans);
        Assert.Equal(skip, summary.skip);
        Assert.Equal(full, summary.full);
        Assert.Equal(partial, summary.partial);
        Assert.Equal(reopt, summary.reoptimize);
    }
}
