using RiposteCli.Models;
using System.Text.Json;

namespace RiposteCli.Tests;

/// <summary>
/// End-to-end integration tests for the smart rebuild pipeline.
/// Exercises the full flow: images + sidecars + manifest → plan → merge/strip → verify.
/// </summary>
public class SmartRebuildIntegrationTests : IDisposable
{
    private readonly string _tempDir;
    private readonly string _outputDir;

    // Minimal valid 1x1 white PNG (67 bytes)
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

    // Second distinct 1x1 PNG (red pixel) for content-change tests
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

    private const string TestModel = "gpt-5-mini";
    private const string TestSchema = "1.4";

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
    };

    public SmartRebuildIntegrationTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), $"riposte-integration-{Guid.NewGuid()}");
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

    private string CreateTestImage(string filename = "test.png", byte[]? data = null)
    {
        var path = Path.Combine(_tempDir, filename);
        File.WriteAllBytes(path, data ?? MinimalPng);
        return path;
    }

    private static Dictionary<string, string> MakePromptHashes(
        string coreHash = "h_core",
        string searchHash = "h_search",
        string culturalHash = "h_cultural",
        string emotionsHash = "h_emotions",
        Dictionary<string, string>? localizations = null)
    {
        var hashes = new Dictionary<string, string>
        {
            [PromptHasher.GroupCore] = coreHash,
            [PromptHasher.GroupSearch] = searchHash,
            [PromptHasher.GroupCultural] = culturalHash,
            [PromptHasher.GroupEmotions] = emotionsHash,
        };
        if (localizations is not null)
        {
            foreach (var (lang, hash) in localizations)
                hashes[PromptHasher.LocalizationGroup(lang)] = hash;
        }
        return hashes;
    }

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

    private static SidecarMetadata CreateFullSidecar(string? contentHash = null) => new()
    {
        SchemaVersion = TestSchema,
        Emojis = ["😂", "🔥"],
        CreatedAt = "2024-01-01T00:00:00Z",
        Title = "Test Meme",
        Description = "A funny test meme",
        Tags = ["funny", "test", "meme"],
        SearchPhrases = ["funny meme", "test image"],
        PrimaryLanguage = "en",
        ContentHash = contentHash,
        BasedOn = "Drake Hotline Bling",
        Emotions = new EmotionMetadata
        {
            Primary = "humor",
            Sentiment = "positive",
            Intensity = "high",
            Secondary = ["joy", "amusement"],
            MemeUsage = ["when something is funny", "reaction to good news"],
        },
        Localizations = new Dictionary<string, LocalizedContent>
        {
            ["cs"] = new()
            {
                Title = "Testovací mém",
                Description = "Vtipný testovací mém",
                Tags = ["vtipné", "test"],
                SearchPhrases = ["vtipný mém"],
            },
        },
    };

    private void WriteSidecar(string imageFilename, SidecarMetadata metadata)
    {
        var path = Path.Combine(_outputDir, imageFilename + ".json");
        var json = JsonSerializer.Serialize(metadata, JsonOptions);
        File.WriteAllText(path, json);
    }

    private SidecarMetadata? ReadSidecar(string imageFilename)
    {
        var path = Path.Combine(_outputDir, imageFilename + ".json");
        if (!File.Exists(path)) return null;
        var json = File.ReadAllText(path);
        return JsonSerializer.Deserialize<SidecarMetadata>(json, JsonOptions);
    }

    /// <summary>
    /// Simulate the full AnnotateCommand flow for a single image after planning.
    /// Records the build in the manifest and writes the sidecar.
    /// </summary>
    private void SimulateFullBuild(
        string imagePath,
        BuildManifest manifest,
        Dictionary<string, string> promptHashes,
        SidecarMetadata sidecar,
        string? optFingerprint = null)
    {
        var fileName = Path.GetFileName(imagePath);
        var contentHash = ImageHashService.GetContentHash(imagePath);
        var sidecarWithHash = new SidecarMetadata
        {
            SchemaVersion = sidecar.SchemaVersion,
            Emojis = sidecar.Emojis,
            CreatedAt = sidecar.CreatedAt,
            Title = sidecar.Title,
            Description = sidecar.Description,
            Tags = sidecar.Tags,
            SearchPhrases = sidecar.SearchPhrases,
            PrimaryLanguage = sidecar.PrimaryLanguage,
            Localizations = sidecar.Localizations,
            ContentHash = contentHash,
            BasedOn = sidecar.BasedOn,
            Emotions = sidecar.Emotions,
        };

        WriteSidecar(fileName, sidecarWithHash);
        ManifestService.RecordImageBuild(
            manifest, fileName, contentHash, TestModel, TestSchema, promptHashes, optFingerprint);
    }

    // ─── Test 1: Fresh build ────────────────────────────────────

    [Fact]
    public void FreshBuild_NoManifestNoSidecars_AllImagesPlanAsFull()
    {
        var img1 = CreateTestImage("meme1.png");
        var img2 = CreateTestImage("meme2.png", MinimalPngAlt);
        var img3 = CreateTestImage("meme3.png");
        var images = new List<string> { img1, img2, img3 };
        var manifest = new BuildManifest();
        var promptHashes = MakePromptHashes();

        var plans = RebuildPlanner.Plan(images, manifest, promptHashes, TestModel, TestSchema, _outputDir);

        Assert.Equal(3, plans.Count);
        Assert.All(plans, p =>
        {
            Assert.Equal(RebuildScope.Full, p.Scope);
            Assert.True(p.NeedsReoptimization);
            Assert.Empty(p.AffectedGroups);
            Assert.Empty(p.RemovedGroups);
        });

        var (skip, full, partial, reopt) = RebuildPlanner.Summarize(plans);
        Assert.Equal(0, skip);
        Assert.Equal(3, full);
        Assert.Equal(0, partial);
        Assert.Equal(0, reopt);
    }

    // ─── Test 2: Everything up to date ──────────────────────────

    [Fact]
    public void EverythingUpToDate_MatchingManifest_AllSkip()
    {
        var img1 = CreateTestImage("meme1.png");
        var img2 = CreateTestImage("meme2.png", MinimalPngAlt);
        var images = new List<string> { img1, img2 };
        var promptHashes = MakePromptHashes();
        var manifest = new BuildManifest();

        // Simulate a previous full build for each image
        foreach (var img in images)
        {
            var sidecar = CreateFullSidecar();
            SimulateFullBuild(img, manifest, promptHashes, sidecar);
        }

        // Now plan again — everything should be skipped
        var plans = RebuildPlanner.Plan(images, manifest, promptHashes, TestModel, TestSchema, _outputDir);

        Assert.Equal(2, plans.Count);
        Assert.All(plans, p =>
        {
            Assert.Equal(RebuildScope.Skip, p.Scope);
            Assert.False(p.NeedsReoptimization);
            Assert.Empty(p.AffectedGroups);
            Assert.Empty(p.RemovedGroups);
        });

        var (skip, full, partial, reopt) = RebuildPlanner.Summarize(plans);
        Assert.Equal(2, skip);
        Assert.Equal(0, full);
        Assert.Equal(0, partial);
        Assert.Equal(0, reopt);
    }

    // ─── Test 3: Prompt change in one group ─────────────────────

    [Fact]
    public void PromptChangeInSearchGroup_AffectedImagesGetPartial()
    {
        var img = CreateTestImage("meme1.png");
        var images = new List<string> { img };
        var originalHashes = MakePromptHashes();
        var manifest = new BuildManifest();

        // Build with original hashes
        SimulateFullBuild(img, manifest, originalHashes, CreateFullSidecar());

        // Change the search prompt hash
        var updatedHashes = MakePromptHashes(searchHash: "h_search_v2");

        var plans = RebuildPlanner.Plan(images, manifest, updatedHashes, TestModel, TestSchema, _outputDir);

        Assert.Single(plans);
        var plan = plans[0];
        Assert.Equal(RebuildScope.Partial, plan.Scope);
        Assert.Contains(PromptHasher.GroupSearch, plan.AffectedGroups);
        Assert.DoesNotContain(PromptHasher.GroupCore, plan.AffectedGroups);
        Assert.DoesNotContain(PromptHasher.GroupEmotions, plan.AffectedGroups);

        // Now simulate the partial rebuild by merging search-only results
        var existingSidecar = ReadSidecar("meme1.png")!;
        var partialResult = new AnalysisResult
        {
            Tags = ["updated-tag-1", "updated-tag-2"],
            SearchPhrases = ["new search phrase"],
        };
        var merged = SidecarMerger.Merge(existingSidecar, partialResult, plan.AffectedGroups);
        WriteSidecar("meme1.png", merged);
        ManifestService.RecordPartialBuild(
            manifest, "meme1.png",
            ImageHashService.GetContentHash(img), TestModel, TestSchema,
            plan.AffectedGroups, updatedHashes);

        // Verify the merge preserved other fields and updated search
        var final = ReadSidecar("meme1.png")!;
        Assert.Equal("Test Meme", final.Title);
        Assert.Equal("A funny test meme", final.Description);
        Assert.Equal(["😂", "🔥"], final.Emojis);
        Assert.Equal("Drake Hotline Bling", final.BasedOn);
        Assert.NotNull(final.Emotions);
        Assert.Equal("humor", final.Emotions!.Primary);
        Assert.Equal(["updated-tag-1", "updated-tag-2"], final.Tags);
        Assert.Equal(["new search phrase"], final.SearchPhrases);

        // Re-plan — should now skip
        var rePlans = RebuildPlanner.Plan(images, manifest, updatedHashes, TestModel, TestSchema, _outputDir);
        Assert.All(rePlans, p => Assert.Equal(RebuildScope.Skip, p.Scope));
    }

    // ─── Test 4: Model change ───────────────────────────────────

    [Fact]
    public void ModelChange_AllImagesPlanAsFull()
    {
        var img1 = CreateTestImage("meme1.png");
        var img2 = CreateTestImage("meme2.png", MinimalPngAlt);
        var images = new List<string> { img1, img2 };
        var promptHashes = MakePromptHashes();
        var manifest = new BuildManifest();

        foreach (var img in images)
            SimulateFullBuild(img, manifest, promptHashes, CreateFullSidecar());

        // Plan with a different model
        var plans = RebuildPlanner.Plan(
            images, manifest, promptHashes, "gpt-5-turbo", TestSchema, _outputDir);

        Assert.Equal(2, plans.Count);
        Assert.All(plans, p =>
        {
            Assert.Equal(RebuildScope.Full, p.Scope);
            Assert.Contains("model changed", p.Reason);
        });
    }

    // ─── Test 5: New language added ─────────────────────────────

    [Fact]
    public void NewLanguageAdded_PartialWithLocalizationGroup()
    {
        var img = CreateTestImage("meme1.png");
        var images = new List<string> { img };
        var originalHashes = MakePromptHashes();
        var manifest = new BuildManifest();

        SimulateFullBuild(img, manifest, originalHashes, CreateFullSidecar());

        // Add Czech localization to prompt hashes
        var updatedHashes = MakePromptHashes(
            localizations: new Dictionary<string, string> { ["cs"] = "h_loc_cs" });

        var plans = RebuildPlanner.Plan(images, manifest, updatedHashes, TestModel, TestSchema, _outputDir);

        Assert.Single(plans);
        var plan = plans[0];
        Assert.Equal(RebuildScope.Partial, plan.Scope);
        Assert.Contains(PromptHasher.LocalizationGroup("cs"), plan.AffectedGroups);
        // Base groups should NOT be affected
        Assert.DoesNotContain(PromptHasher.GroupCore, plan.AffectedGroups);
        Assert.DoesNotContain(PromptHasher.GroupSearch, plan.AffectedGroups);
    }

    // ─── Test 6: Language removed + strip ───────────────────────

    [Fact]
    public void LanguageRemoved_SkipWithRemovedGroups_StripRemovesFromSidecar()
    {
        var img = CreateTestImage("meme1.png");
        var images = new List<string> { img };

        // Build with Czech localization
        var hashesWithCs = MakePromptHashes(
            localizations: new Dictionary<string, string> { ["cs"] = "h_loc_cs" });
        var manifest = new BuildManifest();
        var sidecarWithCs = CreateFullSidecar();
        SimulateFullBuild(img, manifest, hashesWithCs, sidecarWithCs);

        // Verify Czech localization is in the sidecar
        var beforeStrip = ReadSidecar("meme1.png")!;
        Assert.NotNull(beforeStrip.Localizations);
        Assert.True(beforeStrip.Localizations!.ContainsKey("cs"));

        // Now plan without Czech — base hashes unchanged
        var hashesWithoutCs = MakePromptHashes();

        var plans = RebuildPlanner.Plan(images, manifest, hashesWithoutCs, TestModel, TestSchema, _outputDir);

        Assert.Single(plans);
        var plan = plans[0];
        Assert.Equal(RebuildScope.Skip, plan.Scope);
        Assert.Contains(PromptHasher.LocalizationGroup("cs"), plan.RemovedGroups);
        Assert.True(plan.NeedsStripping);

        // Execute the strip
        var stripped = SidecarMerger.StripRemovedGroups(beforeStrip, hashesWithoutCs);
        WriteSidecar("meme1.png", stripped);

        // Verify Czech was removed
        var afterStrip = ReadSidecar("meme1.png")!;
        Assert.True(afterStrip.Localizations is null || !afterStrip.Localizations.ContainsKey("cs"));
        // Core fields preserved
        Assert.Equal("Test Meme", afterStrip.Title);
        Assert.Equal("A funny test meme", afterStrip.Description);
        Assert.NotNull(afterStrip.Emotions);
    }

    // ─── Test 7: Partial merge preserves data ───────────────────

    [Fact]
    public void PartialMerge_OnlySearchUpdated_CoreEmotionsCulturalPreserved()
    {
        var existingSidecar = CreateFullSidecar("original_hash");

        var partialResult = new AnalysisResult
        {
            Tags = ["brand-new-tag"],
            SearchPhrases = ["brand new phrase"],
        };

        var merged = SidecarMerger.Merge(
            existingSidecar, partialResult, [PromptHasher.GroupSearch]);

        // Search fields updated
        Assert.Equal(["brand-new-tag"], merged.Tags);
        Assert.Equal(["brand new phrase"], merged.SearchPhrases);

        // Core fields preserved
        Assert.Equal(existingSidecar.Emojis, merged.Emojis);
        Assert.Equal(existingSidecar.Title, merged.Title);
        Assert.Equal(existingSidecar.Description, merged.Description);

        // Cultural preserved
        Assert.Equal(existingSidecar.BasedOn, merged.BasedOn);

        // Emotions preserved
        Assert.NotNull(merged.Emotions);
        Assert.Equal("humor", merged.Emotions!.Primary);
        Assert.Equal("positive", merged.Emotions.Sentiment);
        Assert.Equal("high", merged.Emotions.Intensity);
        Assert.Equal(["joy", "amusement"], merged.Emotions.Secondary);

        // Localizations preserved
        Assert.NotNull(merged.Localizations);
        Assert.True(merged.Localizations!.ContainsKey("cs"));
        Assert.Equal("Testovací mém", merged.Localizations["cs"].Title);

        // Metadata preserved
        Assert.Equal(existingSidecar.ContentHash, merged.ContentHash);
        Assert.Equal(existingSidecar.PrimaryLanguage, merged.PrimaryLanguage);
        Assert.Equal(existingSidecar.CreatedAt, merged.CreatedAt);
    }

    // ─── Test 8: Optimization config change ─────────────────────

    [Fact]
    public void OptimizationConfigChange_NeedsReoptimizationForAllImages()
    {
        var img1 = CreateTestImage("meme1.png");
        var img2 = CreateTestImage("meme2.png", MinimalPngAlt);
        var images = new List<string> { img1, img2 };
        var promptHashes = MakePromptHashes();
        var manifest = new BuildManifest();

        var originalOpt = new OptimizationConfig
        {
            ApiMaxDimension = 1200,
            BundleMaxDimension = 1200,
            BundleFormat = "webp",
            Quality = 85,
        };

        // Build with original optimization config
        foreach (var img in images)
            SimulateFullBuild(img, manifest, promptHashes, CreateFullSidecar(), originalOpt.Fingerprint());

        // Change optimization config
        var newOpt = new OptimizationConfig
        {
            ApiMaxDimension = 800,
            BundleMaxDimension = 800,
            BundleFormat = "webp",
            Quality = 90,
        };

        var plans = RebuildPlanner.Plan(
            images, manifest, promptHashes, TestModel, TestSchema, _outputDir, newOpt);

        Assert.Equal(2, plans.Count);
        Assert.All(plans, p =>
        {
            // Sidecar is fine, but optimization changed
            Assert.Equal(RebuildScope.Skip, p.Scope);
            Assert.True(p.NeedsReoptimization);
        });

        var (skip, full, partial, reopt) = RebuildPlanner.Summarize(plans);
        Assert.Equal(0, skip);   // skip count excludes reoptimize-needed
        Assert.Equal(0, full);
        Assert.Equal(0, partial);
        Assert.Equal(2, reopt);
    }

    // ─── Test 9: Mixed scenarios ────────────────────────────────

    [Fact]
    public void MixedScenarios_CorrectCountsFromSummarize()
    {
        // Image 1: no sidecar → Full
        var img1 = CreateTestImage("new_meme.png");

        // Image 2: up to date → Skip
        var img2 = CreateTestImage("uptodate.png", MinimalPngAlt);

        // Image 3: search prompt changed → Partial
        var img3 = CreateTestImage("stale_search.png");

        var promptHashes = MakePromptHashes();
        var manifest = new BuildManifest();

        // Build img2 and img3 with original hashes
        SimulateFullBuild(img2, manifest, promptHashes, CreateFullSidecar());
        SimulateFullBuild(img3, manifest, promptHashes, CreateFullSidecar());

        // Now change search hash for the re-plan
        var updatedHashes = MakePromptHashes(searchHash: "h_search_changed");

        var images = new List<string> { img1, img2, img3 };
        var plans = RebuildPlanner.Plan(images, manifest, updatedHashes, TestModel, TestSchema, _outputDir);

        Assert.Equal(3, plans.Count);

        // img1 (new_meme.png): Full (no sidecar)
        var plan1 = plans.First(p => p.ImagePath == img1);
        Assert.Equal(RebuildScope.Full, plan1.Scope);

        // img2 (uptodate.png): Partial (search hash changed, has existing sidecar+manifest)
        var plan2 = plans.First(p => p.ImagePath == img2);
        Assert.Equal(RebuildScope.Partial, plan2.Scope);
        Assert.Contains(PromptHasher.GroupSearch, plan2.AffectedGroups);

        // img3 (stale_search.png): Partial (same search hash change)
        var plan3 = plans.First(p => p.ImagePath == img3);
        Assert.Equal(RebuildScope.Partial, plan3.Scope);

        var (skip, full, partial, reopt) = RebuildPlanner.Summarize(plans);
        Assert.Equal(0, skip);
        Assert.Equal(1, full);
        Assert.Equal(2, partial);
        Assert.Equal(0, reopt);
    }

    // ─── Test 10: Manifest persistence ──────────────────────────

    [Fact]
    public void ManifestPersistence_SaveLoadPlanAgain_AllSkip()
    {
        var img1 = CreateTestImage("meme1.png");
        var img2 = CreateTestImage("meme2.png", MinimalPngAlt);
        var images = new List<string> { img1, img2 };
        var promptHashes = MakePromptHashes();
        var manifest = new BuildManifest();

        // Full build of all images
        foreach (var img in images)
            SimulateFullBuild(img, manifest, promptHashes, CreateFullSidecar());

        // Save manifest to disk
        ManifestService.Save(_outputDir, manifest);

        // Load manifest from disk (simulates a new CLI run)
        var loadedManifest = ManifestService.Load(_outputDir);

        // Plan again with loaded manifest — everything should skip
        var plans = RebuildPlanner.Plan(images, loadedManifest, promptHashes, TestModel, TestSchema, _outputDir);

        Assert.Equal(2, plans.Count);
        Assert.All(plans, p =>
        {
            Assert.Equal(RebuildScope.Skip, p.Scope);
            Assert.False(p.NeedsReoptimization);
        });

        // Verify manifest file exists and is valid JSON
        var manifestPath = Path.Combine(_outputDir, BuildManifest.FileName);
        Assert.True(File.Exists(manifestPath));
        var manifestJson = File.ReadAllText(manifestPath);
        var parsed = JsonSerializer.Deserialize<BuildManifest>(manifestJson, JsonOptions);
        Assert.NotNull(parsed);
        Assert.Equal(2, parsed!.Images.Count);
        Assert.True(parsed.Images.ContainsKey("meme1.png"));
        Assert.True(parsed.Images.ContainsKey("meme2.png"));
    }
}
