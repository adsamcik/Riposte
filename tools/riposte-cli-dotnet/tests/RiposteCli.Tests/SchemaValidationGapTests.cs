using System.Text.Json;
using RiposteCli.Models;

namespace RiposteCli.Tests;

/// <summary>
/// Cross-cutting gap analysis tests for the schema validation system.
/// Each test addresses a specific coverage hole identified by tracing
/// production code paths that no existing test exercised.
/// </summary>
public sealed class SchemaValidationGapTests : IDisposable
{
    private readonly string _tempDir;
    private readonly string _outputDir;

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

    private const string TestModel = "gpt-5-mini";
    private const string TestSchema = "1.4";

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
    };

    public SchemaValidationGapTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), $"riposte-gap-test-{Guid.NewGuid()}");
        _outputDir = Path.Combine(_tempDir, "output");
        Directory.CreateDirectory(_tempDir);
        Directory.CreateDirectory(_outputDir);
        Directory.CreateDirectory(Path.Combine(_outputDir, "sidecars"));
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

    private void WriteSidecar(string imageFilename, SidecarMetadata metadata)
    {
        var path = Path.Combine(_outputDir, "sidecars", imageFilename + ".json");
        File.WriteAllText(path, JsonSerializer.Serialize(metadata, JsonOptions));
    }

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

    private static SidecarMetadata CreateFullExisting() => new()
    {
        SchemaVersion = "1.4",
        Emojis = ["😂", "🔥"],
        CreatedAt = "2024-01-01T00:00:00Z",
        Title = "Existing Title",
        Description = "Existing Description",
        Tags = ["existing-tag"],
        SearchPhrases = ["existing phrase"],
        PrimaryLanguage = "en",
        ContentHash = "abc123",
        BasedOn = "Original Source",
        Emotions = new EmotionMetadata
        {
            Primary = "humor",
            Sentiment = "positive",
            Intensity = "high",
            Secondary = ["joy"],
            MemeUsage = ["when something is funny"],
        },
        Localizations = new Dictionary<string, LocalizedContent>
        {
            ["cs"] = new()
            {
                Title = "Český titulek",
                Description = "Český popis",
                Tags = ["tag-cs"],
                SearchPhrases = ["hledej cs"],
            },
        },
    };

    // ═══════════════════════════════════════════════════════════════
    // GAP 1 (FIXED): Merge cultural group with null BasedOn preserves existing
    // ═══════════════════════════════════════════════════════════════
    // SidecarMerger.Merge line 75: `basedOn = partial.BasedOn ?? basedOn;`
    // Now uses null-coalescing like all other groups — null from AI
    // preserves existing value instead of erasing it.

    [Fact]
    public void Merge_CulturalGroup_NullBasedOn_PreservesExistingValue()
    {
        var existing = CreateFullExisting();
        Assert.NotNull(existing.BasedOn); // precondition

        var partial = new AnalysisResult { BasedOn = null };

        var result = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupCultural]);

        // Null-coalescing preserves existing value (consistent with other groups)
        Assert.Equal(existing.BasedOn, result.BasedOn);
    }

    [Fact]
    public void Merge_CoreGroup_NullTitle_PreservesExisting()
    {
        var existing = CreateFullExisting();
        var partial = new AnalysisResult { Title = null, Description = "New desc" };

        var result = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupCore]);

        // Core group uses ?? so null keeps existing
        Assert.Equal("Existing Title", result.Title);
        Assert.Equal("New desc", result.Description);
    }

    // ═══════════════════════════════════════════════════════════════
    // GAP 2: RebuildPlanner ignores schemaVersion — dead parameter
    // ═══════════════════════════════════════════════════════════════
    // PlanForImage accepts currentSchemaVersion but never compares it
    // to entry.SchemaVersion. A schema bump without prompt changes
    // won't trigger a rebuild.

    [Fact]
    public void PlanForImage_SchemaVersionChange_DoesNotTriggerRebuild()
    {
        var imagePath = CreateTestImage("schema.png");
        WriteSidecar("schema.png", CreateFullExisting());
        var contentHash = ImageHashService.GetContentHash(imagePath);
        var hashes = MakePromptHashes();

        var manifest = new BuildManifest
        {
            Images =
            {
                ["schema.png"] = new ImageManifestEntry
                {
                    ContentHash = contentHash,
                    Model = TestModel,
                    GeneratedAt = "2025-01-01T00:00:00Z",
                    SchemaVersion = "1.3", // OLD schema
                    FieldHashes = new Dictionary<string, string>(hashes),
                    HasApiOptimized = true,
                    HasBundleOptimized = true,
                },
            },
        };

        // Plan with NEW schema version — no rebuild triggered
        var plan = RebuildPlanner.PlanForImage(
            imagePath, manifest, hashes, TestModel, "1.5", _outputDir);

        // Documents the current behavior: schema version alone doesn't trigger rebuild.
        // This is a known limitation — schema changes must accompany prompt changes.
        Assert.Equal(RebuildScope.Skip, plan.Scope);
    }

    // ═══════════════════════════════════════════════════════════════
    // GAP 3: CreateMetadata(AnalysisResult) — null emojis → empty list
    // ═══════════════════════════════════════════════════════════════
    // SidecarService.CreateMetadata uses `result.Emojis ?? []`.
    // If a partial response somehow has null emojis, the sidecar
    // gets an empty emojis list which is invalid per the schema.

    [Fact]
    public void CreateMetadata_FromAnalysisResult_NullEmojis_FallsBackToEmptyList()
    {
        var result = new AnalysisResult
        {
            Emojis = null,
            Title = "Test",
        };

        var metadata = SidecarService.CreateMetadata(result, primaryLanguage: "en");

        Assert.NotNull(metadata.Emojis);
        Assert.Empty(metadata.Emojis);
    }

    [Fact]
    public void CreateMetadata_FromAnalysisResult_PropagatesAllFields()
    {
        var result = new AnalysisResult
        {
            Emojis = ["🎯"],
            Title = "Title",
            Description = "Desc",
            Tags = ["tag"],
            SearchPhrases = ["phrase"],
            BasedOn = "Source",
            Emotions = new EmotionMetadata
            {
                Primary = "humor",
                Sentiment = "positive",
            },
            Localizations = new Dictionary<string, LocalizedContent>
            {
                ["cs"] = new() { Title = "CZ" },
            },
        };

        var metadata = SidecarService.CreateMetadata(result, "en", "hash123");

        Assert.Equal(result.Emojis, metadata.Emojis);
        Assert.Equal(result.Title, metadata.Title);
        Assert.Equal(result.Description, metadata.Description);
        Assert.Equal(result.Tags, metadata.Tags);
        Assert.Equal(result.SearchPhrases, metadata.SearchPhrases);
        Assert.Equal(result.BasedOn, metadata.BasedOn);
        Assert.Equal("humor", metadata.Emotions!.Primary);
        Assert.NotNull(metadata.Localizations);
        Assert.Equal("CZ", metadata.Localizations!["cs"].Title);
        Assert.Equal("en", metadata.PrimaryLanguage);
        Assert.Equal("hash123", metadata.ContentHash);
    }

    // ═══════════════════════════════════════════════════════════════
    // GAP 4: RecordPartialBuild — affected group not in promptHashes
    // ═══════════════════════════════════════════════════════════════
    // If affectedGroups has "localization:cs" but currentPromptHashes
    // doesn't, the hash isn't recorded. Next rebuild sees it as "new"
    // again → infinite partial rebuild loop.

    [Fact]
    public void RecordPartialBuild_AffectedGroupNotInPromptHashes_SilentlySkips()
    {
        var manifest = new BuildManifest();
        var initialHashes = new Dictionary<string, string>
        {
            [PromptHasher.GroupCore] = "c1",
        };
        ManifestService.RecordImageBuild(
            manifest, "photo.jpg", "hash", "model", "1.4", initialHashes);

        // Affected group "localization:cs" but promptHashes doesn't contain it
        var promptHashes = new Dictionary<string, string>
        {
            [PromptHasher.GroupCore] = "c1",
            // localization:cs intentionally MISSING
        };

        ManifestService.RecordPartialBuild(
            manifest, "photo.jpg", "hash", "model", "1.4",
            affectedGroups: [PromptHasher.LocalizationGroup("cs")],
            currentPromptHashes: promptHashes);

        var entry = manifest.Images["photo.jpg"];
        // The localization:cs hash is NOT recorded because it's not in promptHashes
        Assert.False(entry.FieldHashes.ContainsKey(PromptHasher.LocalizationGroup("cs")));
    }

    // ═══════════════════════════════════════════════════════════════
    // GAP 5: GetPartialPrompt with empty fieldGroups
    // ═══════════════════════════════════════════════════════════════
    // Produces a prompt with boilerplate but no field specs.

    [Fact]
    public void GetPartialPrompt_EmptyFieldGroups_StillContainsJsonInstruction()
    {
        var result = Prompts.GetPartialPrompt([], ["en"]);

        Assert.Contains("valid JSON", result);
        Assert.Contains("ONLY the following fields", result);
        // No field-specific instructions
        Assert.DoesNotContain("\"emojis\"", result);
        Assert.DoesNotContain("\"tags\"", result);
    }

    // ═══════════════════════════════════════════════════════════════
    // GAP 6: Merge with unknown/typo group name — silent no-op
    // ═══════════════════════════════════════════════════════════════
    // SidecarMerger.Merge switch default for non-localization:* groups
    // is a no-op. A typo like "cor" instead of "core" silently skips.

    [Fact]
    public void Merge_UnknownGroupName_SilentlyIgnored_ExistingPreserved()
    {
        var existing = CreateFullExisting();
        var partial = new AnalysisResult
        {
            Emojis = ["🚀"],
            Title = "New Title",
            Tags = ["new-tag"],
        };

        // Typo: "cor" instead of "core"
        var result = SidecarMerger.Merge(existing, partial, ["cor", "searc"]);

        // Nothing was updated — existing values preserved
        Assert.Equal(existing.Emojis, result.Emojis);
        Assert.Equal(existing.Title, result.Title);
        Assert.Equal(existing.Tags, result.Tags);
    }

    // ═══════════════════════════════════════════════════════════════
    // GAP 7: StripRemovedGroups — core removal preserves emojis
    // ═══════════════════════════════════════════════════════════════
    // When core group is removed, emojis are kept but title/description
    // are nulled. This asymmetry is undocumented by tests.

    [Fact]
    public void Strip_CoreRemoved_EmojisPreserved_TitleDescriptionNulled()
    {
        var existing = new SidecarMetadata
        {
            Emojis = ["😂", "🔥"],
            Title = "Title to Remove",
            Description = "Description to Remove",
            Tags = ["tag"],
        };

        // No core group in current hashes
        var hashes = new Dictionary<string, string>
        {
            [PromptHasher.GroupSearch] = "h2",
            [PromptHasher.GroupCultural] = "h3",
            [PromptHasher.GroupEmotions] = "h4",
        };

        var result = SidecarMerger.StripRemovedGroups(existing, hashes);

        // Emojis are ALWAYS preserved (not owned by a removable group)
        Assert.Equal(["😂", "🔥"], result.Emojis);
        // Title and description are nulled when core group is removed
        Assert.Null(result.Title);
        Assert.Null(result.Description);
        // Tags preserved (search group still present)
        Assert.Equal(["tag"], result.Tags);
    }

    // ═══════════════════════════════════════════════════════════════
    // GAP 8: Merge localization group when partial has null Localizations
    // ═══════════════════════════════════════════════════════════════
    // If AI returns `{ "tags": [...] }` instead of localizations for a
    // localization:cs group, the merge keeps stale localization data.

    [Fact]
    public void Merge_LocalizationGroup_PartialHasNullLocalizations_PreservesExisting()
    {
        var existing = CreateFullExisting();
        Assert.NotNull(existing.Localizations);
        Assert.Equal("Český titulek", existing.Localizations!["cs"].Title);

        // AI returned no localizations despite being asked for localization:cs
        var partial = new AnalysisResult { Localizations = null };

        var result = SidecarMerger.Merge(
            existing, partial, [PromptHasher.LocalizationGroup("cs")]);

        // Existing Czech localization is preserved (not updated, not deleted)
        Assert.NotNull(result.Localizations);
        Assert.Equal("Český titulek", result.Localizations!["cs"].Title);
    }

    [Fact]
    public void Merge_LocalizationGroup_PartialHasWrongLanguage_TargetLangPreserved()
    {
        var existing = CreateFullExisting();

        // AI returned German instead of Czech
        var partial = new AnalysisResult
        {
            Localizations = new Dictionary<string, LocalizedContent>
            {
                ["de"] = new() { Title = "Deutscher Titel" },
            },
        };

        // We only merge localization:cs — German is ignored, Czech preserved
        var result = SidecarMerger.Merge(
            existing, partial, [PromptHasher.LocalizationGroup("cs")]);

        Assert.NotNull(result.Localizations);
        Assert.Equal("Český titulek", result.Localizations!["cs"].Title);
        // German NOT added because it wasn't in affectedGroups
        Assert.False(result.Localizations.ContainsKey("de"));
    }

    // ═══════════════════════════════════════════════════════════════
    // GAP 9: Cross-module — partial + strip + manifest record + re-plan
    // ═══════════════════════════════════════════════════════════════
    // Full pipeline: planner decides partial + removed → merger merges
    // partial → merger strips removed → manifest records → re-plan = skip.

    [Fact]
    public void CrossModule_PartialPlusStrip_ManifestRecordThenReplan_Skips()
    {
        var imagePath = CreateTestImage("cross.png");
        var contentHash = ImageHashService.GetContentHash(imagePath);

        // Build with cs localization + all base groups
        var storedHashes = MakePromptHashes();
        storedHashes[PromptHasher.LocalizationGroup("cs")] = "h_cs";

        var manifest = new BuildManifest
        {
            Images =
            {
                ["cross.png"] = new ImageManifestEntry
                {
                    ContentHash = contentHash,
                    Model = TestModel,
                    GeneratedAt = "2025-01-01T00:00:00Z",
                    SchemaVersion = TestSchema,
                    FieldHashes = new Dictionary<string, string>(storedHashes),
                    HasApiOptimized = true,
                    HasBundleOptimized = true,
                },
            },
        };

        // Write existing sidecar with cs localization
        WriteSidecar("cross.png", CreateFullExisting());

        // New config: core prompt changed + cs localization REMOVED
        var currentHashes = MakePromptHashes(coreHash: "h_core_v2");

        // Step 1: Plan
        var plan = RebuildPlanner.PlanForImage(
            imagePath, manifest, currentHashes, TestModel, TestSchema, _outputDir);

        Assert.Equal(RebuildScope.Partial, plan.Scope);
        Assert.Contains(PromptHasher.GroupCore, plan.AffectedGroups);
        Assert.Contains(PromptHasher.LocalizationGroup("cs"), plan.RemovedGroups);
        Assert.True(plan.NeedsStripping);

        // Step 2: Load existing sidecar, merge partial result
        var existingSidecar = SidecarMerger.LoadSidecar(imagePath, _outputDir)!;
        var partialResult = new AnalysisResult
        {
            Emojis = ["🎯", "✨"],
            Title = "Updated Title",
            Description = "Updated Description",
        };
        var merged = SidecarMerger.Merge(existingSidecar, partialResult, plan.AffectedGroups);

        // Step 3: Strip removed groups
        var stripped = SidecarMerger.StripRemovedGroups(merged, currentHashes);

        // Verify merge + strip results
        Assert.Equal(["🎯", "✨"], stripped.Emojis);
        Assert.Equal("Updated Title", stripped.Title);
        Assert.Equal("Updated Description", stripped.Description);
        Assert.Equal(["existing-tag"], stripped.Tags); // preserved
        Assert.Null(stripped.Localizations); // cs was stripped

        // Step 4: Write sidecar and record in manifest
        WriteSidecar("cross.png", stripped);
        ManifestService.RecordPartialBuild(
            manifest, "cross.png", contentHash, TestModel, TestSchema,
            plan.AffectedGroups, currentHashes);

        // Also remove the stripped groups' hashes from the manifest entry
        var entry = manifest.Images["cross.png"];
        foreach (var removed in plan.RemovedGroups)
            entry.FieldHashes.Remove(removed);

        // Step 5: Re-plan — should skip
        var rePlan = RebuildPlanner.PlanForImage(
            imagePath, manifest, currentHashes, TestModel, TestSchema, _outputDir);

        Assert.Equal(RebuildScope.Skip, rePlan.Scope);
        Assert.Empty(rePlan.AffectedGroups);
        Assert.Empty(rePlan.RemovedGroups);
    }

    // ═══════════════════════════════════════════════════════════════
    // GAP 10: Merge resets AppVersion/CliToolVersion to current
    // ═══════════════════════════════════════════════════════════════
    // SidecarMerger.Merge creates a new SidecarMetadata without
    // explicitly setting AppVersion/CliToolVersion, so they get
    // default values (current CLI version). Old values are lost.

    [Fact]
    public void Merge_ResetsAppVersionAndCliToolVersion_ToCurrent()
    {
        var existing = new SidecarMetadata
        {
            SchemaVersion = "1.4",
            Emojis = ["😂"],
            CreatedAt = "2024-01-01T00:00:00Z",
            AppVersion = "cli-0.9.0", // old version
            CliToolVersion = "0.9.0",  // old version
            Title = "Old",
        };

        var partial = new AnalysisResult { Title = "New" };

        var result = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupCore]);

        // AppVersion and CliToolVersion are reset to current (not preserved from existing)
        Assert.Equal($"cli-{CliVersion.Current}", result.AppVersion);
        Assert.Equal(CliVersion.Current, result.CliToolVersion);
    }

    // ═══════════════════════════════════════════════════════════════
    // GAP 11: PromptHasher.ComputeAll with single-element list
    // ═══════════════════════════════════════════════════════════════
    // When only primary language is provided, no localization hashes
    // should be produced. Verifies Skip(1) on single-element list.

    [Fact]
    public void ComputeAll_SingleLanguage_NoLocalizationHashes()
    {
        var hashes = PromptHasher.ComputeAll(["en"]);

        Assert.Equal(4, hashes.Count);
        Assert.True(hashes.ContainsKey(PromptHasher.GroupCore));
        Assert.True(hashes.ContainsKey(PromptHasher.GroupSearch));
        Assert.True(hashes.ContainsKey(PromptHasher.GroupCultural));
        Assert.True(hashes.ContainsKey(PromptHasher.GroupEmotions));
        // No localization keys
        Assert.DoesNotContain(hashes.Keys, k => k.StartsWith(PromptHasher.LocalizationPrefix));
    }

    [Fact]
    public void ComputeAll_MultipleLanguages_IncludesLocalizationHashes()
    {
        var hashes = PromptHasher.ComputeAll(["en", "cs", "de"]);

        Assert.Equal(6, hashes.Count); // 4 base + 2 localizations
        Assert.True(hashes.ContainsKey(PromptHasher.LocalizationGroup("cs")));
        Assert.True(hashes.ContainsKey(PromptHasher.LocalizationGroup("de")));
        // Primary language (en) should NOT have a localization hash
        Assert.False(hashes.ContainsKey(PromptHasher.LocalizationGroup("en")));
    }

    [Fact]
    public void ComputeAll_EmptyLanguageList_DefaultsToEnglish()
    {
        var hashes = PromptHasher.ComputeAll([]);

        Assert.Equal(4, hashes.Count);
        // Should produce hashes based on English
        Assert.True(hashes.ContainsKey(PromptHasher.GroupCore));
    }

    // ═══════════════════════════════════════════════════════════════
    // GAP 12: PromptHasher determinism — same input = same hash
    // ═══════════════════════════════════════════════════════════════
    // If hashes aren't deterministic, every run triggers a full rebuild.

    [Fact]
    public void ComputeAll_IsDeterministic_SameInputSameOutput()
    {
        var hashes1 = PromptHasher.ComputeAll(["en", "cs"]);
        var hashes2 = PromptHasher.ComputeAll(["en", "cs"]);

        Assert.Equal(hashes1, hashes2);
    }

    [Fact]
    public void ComputeAll_DifferentLanguage_ProducesDifferentCoreHashes()
    {
        var hashesEn = PromptHasher.ComputeAll(["en"]);
        var hashesCs = PromptHasher.ComputeAll(["cs"]);

        // Core and search hashes depend on language, so they should differ
        Assert.NotEqual(hashesEn[PromptHasher.GroupCore], hashesCs[PromptHasher.GroupCore]);
        Assert.NotEqual(hashesEn[PromptHasher.GroupSearch], hashesCs[PromptHasher.GroupSearch]);
        // Cultural hash is language-independent
        Assert.Equal(hashesEn[PromptHasher.GroupCultural], hashesCs[PromptHasher.GroupCultural]);
    }

    // ═══════════════════════════════════════════════════════════════
    // GAP 13: SidecarMerger.LoadSidecar — corrupt JSON error path
    // ═══════════════════════════════════════════════════════════════
    // LoadSidecar wraps JsonException in CopilotAnalysisException.
    // This error propagation path was never tested.

    [Fact]
    public void LoadSidecar_CorruptJson_ThrowsCopilotAnalysisException()
    {
        var imagePath = CreateTestImage("corrupt.png");
        var sidecarPath = Path.Combine(_outputDir, "sidecars", "corrupt.png.json");
        File.WriteAllText(sidecarPath, "{{not valid json!!!}}}");

        var ex = Assert.Throws<CopilotAnalysisException>(
            () => SidecarMerger.LoadSidecar(imagePath, _outputDir));

        Assert.Contains("Failed to parse sidecar JSON", ex.Message);
        Assert.Contains("corrupt.png.json", ex.Message);
    }

    [Fact]
    public void LoadSidecar_NonExistentFile_ReturnsNull()
    {
        var imagePath = CreateTestImage("nofile.png");

        var result = SidecarMerger.LoadSidecar(imagePath, _outputDir);

        Assert.Null(result);
    }

    // ═══════════════════════════════════════════════════════════════
    // GAP 14: Merge with existing that has no localizations
    // ═══════════════════════════════════════════════════════════════
    // SidecarMerger.Merge line 55-57: handles null localizations by
    // creating a new empty dict. But if merge adds a localization
    // and it's the only one, it should appear in the result.

    [Fact]
    public void Merge_ExistingHasNoLocalizations_NewLocalizationAdded()
    {
        var existing = new SidecarMetadata
        {
            Emojis = ["😂"],
            Title = "Title",
            // No localizations
        };

        var partial = new AnalysisResult
        {
            Localizations = new Dictionary<string, LocalizedContent>
            {
                ["de"] = new() { Title = "Deutscher Titel" },
            },
        };

        var result = SidecarMerger.Merge(
            existing, partial, [PromptHasher.LocalizationGroup("de")]);

        Assert.NotNull(result.Localizations);
        Assert.Single(result.Localizations);
        Assert.Equal("Deutscher Titel", result.Localizations["de"].Title);
    }

    // ═══════════════════════════════════════════════════════════════
    // GAP 15: StripRemovedGroups — no groups removed returns same ref
    // ═══════════════════════════════════════════════════════════════
    // When all existing fields are null AND all groups are present,
    // no change = same reference returned (optimization).

    [Fact]
    public void Strip_AllFieldsNull_AllGroupsPresent_ReturnsSameReference()
    {
        var existing = new SidecarMetadata
        {
            Emojis = ["😂"],
            // All optional fields are null
        };

        var hashes = MakePromptHashes();

        var result = SidecarMerger.StripRemovedGroups(existing, hashes);

        Assert.Same(existing, result);
    }

    // ═══════════════════════════════════════════════════════════════
    // GAP 16: Merge emotions group with null emotions — preserves existing
    // ═══════════════════════════════════════════════════════════════
    // If AI returns null for emotions when asked for emotions group,
    // existing emotions are preserved (null-coalescing on line 79).

    [Fact]
    public void Merge_EmotionsGroup_NullEmotions_PreservesExisting()
    {
        var existing = CreateFullExisting();
        var partial = new AnalysisResult { Emotions = null };

        var result = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupEmotions]);

        // Null-coalescing preserves existing
        Assert.NotNull(result.Emotions);
        Assert.Equal("humor", result.Emotions!.Primary);
    }

    // ═══════════════════════════════════════════════════════════════
    // GAP 17: OptimizationConfig.Fingerprint determinism
    // ═══════════════════════════════════════════════════════════════
    // If fingerprint isn't deterministic, every run re-optimizes.

    [Fact]
    public void OptimizationConfig_Fingerprint_IsDeterministic()
    {
        var config1 = new OptimizationConfig
        {
            ApiMaxDimension = 1200,
            BundleMaxDimension = 800,
            Quality = 90,
        };
        var config2 = new OptimizationConfig
        {
            ApiMaxDimension = 1200,
            BundleMaxDimension = 800,
            Quality = 90,
        };

        Assert.Equal(config1.Fingerprint(), config2.Fingerprint());
    }

    [Fact]
    public void OptimizationConfig_Fingerprint_ChangesWithQuality()
    {
        var config1 = new OptimizationConfig { Quality = 85 };
        var config2 = new OptimizationConfig { Quality = 90 };

        Assert.NotEqual(config1.Fingerprint(), config2.Fingerprint());
    }

    // ═══════════════════════════════════════════════════════════════
    // GAP 18: ParsePartialResponse with empty JSON object
    // ═══════════════════════════════════════════════════════════════
    // If AI returns `{}`, partial parsing should succeed (all null).
    // Full parsing would throw (no emojis), but partial should not.

    [Fact]
    public void ParsePartialResponse_EmptyJsonObject_ReturnsAllNulls()
    {
        var result = CopilotService.ParsePartialResponse("{}", [PromptHasher.GroupSearch]);

        Assert.Null(result.Emojis);
        Assert.Null(result.Tags);
        Assert.Null(result.SearchPhrases);
        Assert.Null(result.Title);
    }

    [Fact]
    public void ParseResponseContent_EmptyJsonObject_Throws()
    {
        // Full parse requires emojis
        Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent("{}"));
    }

    // ═══════════════════════════════════════════════════════════════
    // GAP 19: ManifestService.Save atomicity — temp file used
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void ManifestService_Save_NoTempFileLeftBehind()
    {
        var manifest = new BuildManifest
        {
            Images = { ["test.png"] = new ImageManifestEntry
            {
                ContentHash = "hash",
                Model = "model",
                GeneratedAt = "2025-01-01",
                SchemaVersion = "1.4",
            }},
        };

        ManifestService.Save(_outputDir, manifest);

        var files = Directory.GetFiles(_outputDir, ".meme-build-manifest*");
        Assert.Single(files); // Only the real file, no .tmp leftover
        Assert.EndsWith(BuildManifest.FileName, Path.GetFileName(files[0]));
    }

    // ═══════════════════════════════════════════════════════════════
    // GAP 20: Full parse vs partial parse asymmetry with null emojis
    // ═══════════════════════════════════════════════════════════════
    // Full parse throws on null/empty emojis; partial parse allows it.
    // If they're confused, sidecars get invalid data.

    [Fact]
    public void ParseResponseContent_NullEmojisField_Throws()
    {
        var json = """{"emojis": null, "title": "Test"}""";

        Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent(json));
    }

    [Fact]
    public void ParsePartialResponse_NullEmojisField_DoesNotThrow()
    {
        var json = """{"title": "Partial Test"}""";

        var result = CopilotService.ParsePartialResponse(json, [PromptHasher.GroupCore]);

        Assert.Null(result.Emojis);
        Assert.Equal("Partial Test", result.Title);
    }
}
