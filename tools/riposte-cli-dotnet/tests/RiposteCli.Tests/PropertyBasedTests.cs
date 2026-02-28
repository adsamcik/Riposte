using RiposteCli.Models;
using RiposteCli.Services;
using System.Text.Json;

namespace RiposteCli.Tests;

public sealed class PropertyBasedTests
{
    [Theory]
    [InlineData("")]
    [InlineData("a")]
    [InlineData("hello")]
    [InlineData("HELLO")]
    [InlineData("hello world")]
    [InlineData("  padded  ")]
    [InlineData("line1\nline2")]
    [InlineData("emoji 😀🔥")]
    [InlineData("cs-CZ")]
    [InlineData("en-US")]
    [InlineData("日本語")]
    [InlineData("русский")]
    [InlineData("العربية")]
    [InlineData("中文")]
    [InlineData("special !@#$%^&*()")]
    [InlineData("{json:true}")]
    [InlineData("Lorem ipsum dolor sit amet")]
    [InlineData("1234567890")]
    [InlineData("repeat repeat repeat")]
    [InlineData("end.")]
    public void PromptHasher_Hash_IsPure_ForAnyInput(string input)
    {
        var hash1 = PromptHasher.Hash(input);
        var hash2 = PromptHasher.Hash(input);

        Assert.Equal(hash1, hash2);
        Assert.Equal(64, hash1.Length); // SHA-256 → 64 hex chars
        Assert.Matches("^[0-9a-f]{64}$", hash1); // lowercase hex only
    }

    [Theory]
    [InlineData("en")]
    [InlineData("cs")]
    [InlineData("de")]
    [InlineData("fr")]
    [InlineData("ja")]
    public void PromptHasher_ComputeAll_HasExactlyFourBaseGroups_ForSingleLanguage(string language)
    {
        var hashes = PromptHasher.ComputeAll([language]);

        Assert.Equal(4, hashes.Count);
        Assert.Contains(PromptHasher.GroupCore, hashes.Keys);
        Assert.Contains(PromptHasher.GroupSearch, hashes.Keys);
        Assert.Contains(PromptHasher.GroupCultural, hashes.Keys);
        Assert.Contains(PromptHasher.GroupEmotions, hashes.Keys);
    }

    [Theory]
    [InlineData("en", "cs", 1)]
    [InlineData("en", "cs,de", 2)]
    [InlineData("en", "cs,de,fr", 3)]
    [InlineData("en", "cs,de,fr,ja", 4)]
    [InlineData("en", "cs,de,fr,ja,es", 5)]
    public void PromptHasher_ComputeAll_HasFourPlusNGroups_ForAdditionalLanguages(string primary, string additionalCsv, int additionalCount)
    {
        var languages = new List<string> { primary };
        languages.AddRange(additionalCsv.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries));

        var hashes = PromptHasher.ComputeAll(languages);

        Assert.Equal(4 + additionalCount, hashes.Count);
    }

    [Theory]
    [InlineData(1200, "original", 1200, "webp", 85)]
    [InlineData(800, "original", 1200, "webp", 85)]
    [InlineData(1200, "jpeg", 1200, "webp", 85)]
    [InlineData(1200, "original", 1024, "webp", 85)]
    [InlineData(1200, "original", 1200, "png", 85)]
    [InlineData(1200, "original", 1200, "webp", 70)]
    [InlineData(600, "jpeg", 900, "webp", 50)]
    [InlineData(1600, "original", 800, "webp", 100)]
    [InlineData(1200, "png", 1200, "png", 1)]
    [InlineData(2048, "original", 2048, "webp", 95)]
    public void OptimizationConfig_Fingerprint_IsPure_ForAnyConfig(int apiMax, string apiFormat, int bundleMax, string bundleFormat, int quality)
    {
        var config = new OptimizationConfig
        {
            ApiMaxDimension = apiMax,
            ApiFormat = apiFormat,
            BundleMaxDimension = bundleMax,
            BundleFormat = bundleFormat,
            Quality = quality,
        };

        var fp1 = config.Fingerprint();
        var fp2 = config.Fingerprint();

        Assert.Equal(fp1, fp2);
    }

    [Fact]
    public void OptimizationConfig_AnySingleFieldMutation_ChangesFingerprint()
    {
        var baseline = new OptimizationConfig
        {
            ApiMaxDimension = 1200,
            ApiFormat = "original",
            BundleMaxDimension = 1200,
            BundleFormat = "webp",
            Quality = 85,
        };
        var baselineFingerprint = baseline.Fingerprint();

        var mutations = new[]
        {
            baseline with { ApiMaxDimension = 999 },
            baseline with { ApiFormat = "jpeg" },
            baseline with { BundleMaxDimension = 999 },
            baseline with { BundleFormat = "png" },
            baseline with { Quality = 84 },
        };

        foreach (var mutation in mutations)
            Assert.NotEqual(baselineFingerprint, mutation.Fingerprint());
    }

    [Theory]
    [InlineData(PromptHasher.GroupCore)]
    [InlineData(PromptHasher.GroupSearch)]
    [InlineData(PromptHasher.GroupCultural)]
    [InlineData(PromptHasher.GroupEmotions)]
    [InlineData(PromptHasher.GroupCore + "," + PromptHasher.GroupSearch)]
    [InlineData(PromptHasher.GroupCore + "," + PromptHasher.GroupSearch + "," + PromptHasher.GroupCultural + "," + PromptHasher.GroupEmotions)]
    [InlineData("localization:cs")]
    [InlineData(PromptHasher.GroupCore + ",localization:cs")]
    public void SidecarMerger_Merge_PreservesContentHash_RegardlessOfGroups(string groupsCsv)
    {
        var merged = SidecarMerger.Merge(CreateExistingMetadata(), CreatePartialResult(), ParseGroups(groupsCsv));
        Assert.Equal("content-hash-1", merged.ContentHash);
    }

    [Theory]
    [InlineData(PromptHasher.GroupCore)]
    [InlineData(PromptHasher.GroupSearch)]
    [InlineData(PromptHasher.GroupCultural)]
    [InlineData(PromptHasher.GroupEmotions)]
    [InlineData(PromptHasher.GroupCore + "," + PromptHasher.GroupSearch)]
    [InlineData(PromptHasher.GroupCore + "," + PromptHasher.GroupSearch + "," + PromptHasher.GroupCultural + "," + PromptHasher.GroupEmotions)]
    [InlineData("localization:cs")]
    [InlineData(PromptHasher.GroupCore + ",localization:cs")]
    public void SidecarMerger_Merge_PreservesCreatedAt_RegardlessOfGroups(string groupsCsv)
    {
        var merged = SidecarMerger.Merge(CreateExistingMetadata(), CreatePartialResult(), ParseGroups(groupsCsv));
        Assert.Equal("2025-01-01T00:00:00.0000000+00:00", merged.CreatedAt);
    }

    [Theory]
    [InlineData(PromptHasher.GroupCore)]
    [InlineData(PromptHasher.GroupSearch)]
    [InlineData(PromptHasher.GroupCultural)]
    [InlineData(PromptHasher.GroupEmotions)]
    [InlineData(PromptHasher.GroupCore + "," + PromptHasher.GroupSearch)]
    [InlineData(PromptHasher.GroupCore + "," + PromptHasher.GroupSearch + "," + PromptHasher.GroupCultural + "," + PromptHasher.GroupEmotions)]
    [InlineData("localization:cs")]
    [InlineData(PromptHasher.GroupCore + ",localization:cs")]
    public void SidecarMerger_Merge_PreservesPrimaryLanguage_RegardlessOfGroups(string groupsCsv)
    {
        var merged = SidecarMerger.Merge(CreateExistingMetadata(), CreatePartialResult(), ParseGroups(groupsCsv));
        Assert.Equal("en", merged.PrimaryLanguage);
    }

    [Theory]
    [InlineData(PromptHasher.GroupCore)]
    [InlineData(PromptHasher.GroupSearch)]
    [InlineData(PromptHasher.GroupCultural)]
    [InlineData(PromptHasher.GroupEmotions)]
    [InlineData(PromptHasher.GroupCore + "," + PromptHasher.GroupSearch)]
    [InlineData(PromptHasher.GroupCore + "," + PromptHasher.GroupSearch + "," + PromptHasher.GroupCultural + "," + PromptHasher.GroupEmotions)]
    [InlineData("localization:cs")]
    [InlineData(PromptHasher.GroupCore + ",localization:cs")]
    public void SidecarMerger_Merge_PreservesSchemaVersion_RegardlessOfGroups(string groupsCsv)
    {
        var merged = SidecarMerger.Merge(CreateExistingMetadata(), CreatePartialResult(), ParseGroups(groupsCsv));
        Assert.Equal("1.4", merged.SchemaVersion);
    }

    [Theory]
    [InlineData(1)]
    [InlineData(2)]
    [InlineData(3)]
    [InlineData(4)]
    [InlineData(5)]
    public void RebuildPlanner_PlanForImage_WithMatchingManifest_AlwaysSkips(int hashVariant)
    {
        using var temp = new TempArtifact();
        temp.CreateImageAndSidecar();

        var currentPromptHashes = BuildHashSet(hashVariant);
        var plan = RebuildPlanner.PlanForImage(
            imagePath: temp.ImagePath,
            manifest: temp.CreateManifestEntry(currentPromptHashes, model: "gpt-5-mini"),
            currentPromptHashes: currentPromptHashes,
            currentModel: "gpt-5-mini",
            currentSchemaVersion: "1.4",
            outputDir: temp.OutputDir);

        Assert.Equal(RebuildScope.Skip, plan.Scope);
    }

    [Fact]
    public void RebuildPlanner_PlanForImage_WithNoManifestEntry_AlwaysFull()
    {
        using var temp = new TempArtifact();
        temp.CreateImageAndSidecar();

        var plan = RebuildPlanner.PlanForImage(
            imagePath: temp.ImagePath,
            manifest: new BuildManifest(),
            currentPromptHashes: BuildHashSet(1),
            currentModel: "gpt-5-mini",
            currentSchemaVersion: "1.4",
            outputDir: temp.OutputDir);

        Assert.Equal(RebuildScope.Full, plan.Scope);
    }

    [Theory]
    [InlineData(true)]
    [InlineData(false)]
    public void RebuildPlanner_PlanForImage_WithDifferentModel_AlwaysFull_RegardlessOfHashState(bool hashesMatch)
    {
        using var temp = new TempArtifact();
        temp.CreateImageAndSidecar();

        var currentPromptHashes = BuildHashSet(1);
        var manifestHashes = hashesMatch ? BuildHashSet(1) : BuildHashSet(2);

        var plan = RebuildPlanner.PlanForImage(
            imagePath: temp.ImagePath,
            manifest: temp.CreateManifestEntry(manifestHashes, model: "other-model"),
            currentPromptHashes: currentPromptHashes,
            currentModel: "gpt-5-mini",
            currentSchemaVersion: "1.4",
            outputDir: temp.OutputDir);

        Assert.Equal(RebuildScope.Full, plan.Scope);
    }

    [Fact]
    public void SidecarMetadata_JsonRoundtrip_SerializeDeserializeSerialize_IsIdentical()
    {
        var options = JsonOptions();
        var original = CreateExistingMetadata();

        var json1 = JsonSerializer.Serialize(original, options);
        var restored = JsonSerializer.Deserialize<SidecarMetadata>(json1, options);
        Assert.NotNull(restored);
        var json2 = JsonSerializer.Serialize(restored, options);

        Assert.Equal(json1, json2);
    }

    [Fact]
    public void BuildManifest_JsonRoundtrip_SerializeDeserializeSerialize_IsIdentical()
    {
        var options = JsonOptions();
        var manifest = new BuildManifest
        {
            ManifestVersion = BuildManifest.CurrentManifestVersion,
            SchemaVersion = "1.4",
            Model = "gpt-5-mini",
            PromptHashes = new Dictionary<string, string>
            {
                [PromptHasher.GroupCore] = PromptHasher.Hash("a"),
                [PromptHasher.GroupSearch] = PromptHasher.Hash("b"),
            },
            Images = new Dictionary<string, ImageManifestEntry>(StringComparer.OrdinalIgnoreCase)
            {
                ["image.png"] = new ImageManifestEntry
                {
                    ContentHash = "abc",
                    Model = "gpt-5-mini",
                    SchemaVersion = "1.4",
                    GeneratedAt = "2025-01-01T00:00:00.0000000+00:00",
                    FieldHashes = new Dictionary<string, string>
                    {
                        [PromptHasher.GroupCore] = PromptHasher.Hash("a"),
                    },
                    OptimizationFingerprint = "api:1200:original|bundle:1200:webp|q:85",
                    HasApiOptimized = true,
                    HasBundleOptimized = true,
                },
            },
        };

        var json1 = JsonSerializer.Serialize(manifest, options);
        var restored = JsonSerializer.Deserialize<BuildManifest>(json1, options);
        Assert.NotNull(restored);
        var json2 = JsonSerializer.Serialize(restored, options);

        Assert.Equal(json1, json2);
    }

    [Fact]
    public void StripRemovedGroups_ReturnsSameReference_WhenNothingToStrip()
    {
        var existing = CreateExistingMetadata();
        var currentPromptHashes = new Dictionary<string, string>
        {
            [PromptHasher.GroupCore] = PromptHasher.Hash("core"),
            [PromptHasher.GroupSearch] = PromptHasher.Hash("search"),
            [PromptHasher.GroupCultural] = PromptHasher.Hash("cultural"),
            [PromptHasher.GroupEmotions] = PromptHasher.Hash("emotions"),
            ["localization:cs"] = PromptHasher.Hash("loc-cs"),
        };

        var stripped = SidecarMerger.StripRemovedGroups(existing, currentPromptHashes);

        Assert.Same(existing, stripped);
    }

    [Fact]
    public void StripRemovedGroups_RemovesLocalization_WhenNotInCurrentHashes()
    {
        var existing = CreateExistingMetadata(); // has "cs" localization
        var currentPromptHashes = new Dictionary<string, string>
        {
            [PromptHasher.GroupCore] = PromptHasher.Hash("core"),
            [PromptHasher.GroupSearch] = PromptHasher.Hash("search"),
            [PromptHasher.GroupCultural] = PromptHasher.Hash("cultural"),
            [PromptHasher.GroupEmotions] = PromptHasher.Hash("emotions"),
            // No "localization:cs" → cs should be stripped
        };

        var stripped = SidecarMerger.StripRemovedGroups(existing, currentPromptHashes);

        Assert.NotSame(existing, stripped);
        Assert.Null(stripped.Localizations);
    }

    [Theory]
    [InlineData(PromptHasher.GroupSearch)]
    [InlineData(PromptHasher.GroupCultural)]
    [InlineData(PromptHasher.GroupEmotions)]
    [InlineData("localization:cs")]
    public void SidecarMerger_Merge_NonCoreGroup_DoesNotChangeCoreFields(string group)
    {
        var existing = CreateExistingMetadata();
        var partial = CreatePartialResult(); // has different values for everything
        var merged = SidecarMerger.Merge(existing, partial, [group]);

        // Core fields must remain unchanged when only a non-core group is merged.
        Assert.Equal(existing.Emojis, merged.Emojis);
        Assert.Equal(existing.Title, merged.Title);
        Assert.Equal(existing.Description, merged.Description);
    }

    [Theory]
    [InlineData(PromptHasher.GroupCore)]
    [InlineData(PromptHasher.GroupCultural)]
    [InlineData(PromptHasher.GroupEmotions)]
    [InlineData("localization:cs")]
    public void SidecarMerger_Merge_NonSearchGroup_DoesNotChangeSearchFields(string group)
    {
        var existing = CreateExistingMetadata();
        var partial = CreatePartialResult();
        var merged = SidecarMerger.Merge(existing, partial, [group]);

        Assert.Equal(existing.Tags, merged.Tags);
        Assert.Equal(existing.SearchPhrases, merged.SearchPhrases);
    }

    [Theory]
    [InlineData(PromptHasher.GroupCore)]
    [InlineData(PromptHasher.GroupSearch)]
    [InlineData(PromptHasher.GroupEmotions)]
    [InlineData("localization:cs")]
    public void SidecarMerger_Merge_NonCulturalGroup_DoesNotChangeCulturalFields(string group)
    {
        var existing = CreateExistingMetadata();
        var partial = CreatePartialResult();
        var merged = SidecarMerger.Merge(existing, partial, [group]);

        Assert.Equal(existing.BasedOn, merged.BasedOn);
    }

    [Theory]
    [InlineData(PromptHasher.GroupCore)]
    [InlineData(PromptHasher.GroupSearch)]
    [InlineData(PromptHasher.GroupCultural)]
    [InlineData("localization:cs")]
    public void SidecarMerger_Merge_NonEmotionsGroup_DoesNotChangeEmotionsFields(string group)
    {
        var existing = CreateExistingMetadata();
        var partial = CreatePartialResult();
        var merged = SidecarMerger.Merge(existing, partial, [group]);

        Assert.Equal(existing.Emotions!.Primary, merged.Emotions!.Primary);
        Assert.Equal(existing.Emotions!.Sentiment, merged.Emotions!.Sentiment);
    }

    private static JsonSerializerOptions JsonOptions() => new()
    {
        WriteIndented = true,
        DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
    };

    private static List<string> ParseGroups(string groupsCsv) =>
        groupsCsv.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries).ToList();

    private static Dictionary<string, string> BuildHashSet(int variant)
    {
        return variant switch
        {
            1 => new Dictionary<string, string>
            {
                [PromptHasher.GroupCore] = PromptHasher.Hash("v1-core"),
                [PromptHasher.GroupSearch] = PromptHasher.Hash("v1-search"),
                [PromptHasher.GroupCultural] = PromptHasher.Hash("v1-cultural"),
                [PromptHasher.GroupEmotions] = PromptHasher.Hash("v1-emotions"),
            },
            2 => new Dictionary<string, string>
            {
                [PromptHasher.GroupCore] = PromptHasher.Hash("v2-core"),
                [PromptHasher.GroupSearch] = PromptHasher.Hash("v2-search"),
                [PromptHasher.GroupCultural] = PromptHasher.Hash("v2-cultural"),
                [PromptHasher.GroupEmotions] = PromptHasher.Hash("v2-emotions"),
                ["localization:cs"] = PromptHasher.Hash("v2-loc-cs"),
            },
            3 => new Dictionary<string, string>
            {
                [PromptHasher.GroupCore] = PromptHasher.Hash("v3-core"),
                [PromptHasher.GroupSearch] = PromptHasher.Hash("v3-search"),
                [PromptHasher.GroupCultural] = PromptHasher.Hash("v3-cultural"),
                [PromptHasher.GroupEmotions] = PromptHasher.Hash("v3-emotions"),
                ["localization:de"] = PromptHasher.Hash("v3-loc-de"),
            },
            4 => new Dictionary<string, string>
            {
                [PromptHasher.GroupCore] = PromptHasher.Hash("v4-core"),
                [PromptHasher.GroupSearch] = PromptHasher.Hash("v4-search"),
                [PromptHasher.GroupCultural] = PromptHasher.Hash("v4-cultural"),
                [PromptHasher.GroupEmotions] = PromptHasher.Hash("v4-emotions"),
                ["localization:cs"] = PromptHasher.Hash("v4-loc-cs"),
                ["localization:de"] = PromptHasher.Hash("v4-loc-de"),
            },
            _ => new Dictionary<string, string>
            {
                [PromptHasher.GroupCore] = PromptHasher.Hash("v5-core"),
                [PromptHasher.GroupSearch] = PromptHasher.Hash("v5-search"),
                [PromptHasher.GroupCultural] = PromptHasher.Hash("v5-cultural"),
                [PromptHasher.GroupEmotions] = PromptHasher.Hash("v5-emotions"),
                ["localization:cs"] = PromptHasher.Hash("v5-loc-cs"),
                ["localization:de"] = PromptHasher.Hash("v5-loc-de"),
                ["localization:fr"] = PromptHasher.Hash("v5-loc-fr"),
            },
        };
    }

    private static SidecarMetadata CreateExistingMetadata() =>
        new()
        {
            SchemaVersion = "1.4",
            Emojis = ["🙂"],
            CreatedAt = "2025-01-01T00:00:00.0000000+00:00",
            Title = "Old title",
            Description = "Old description",
            Tags = ["old"],
            SearchPhrases = ["old phrase"],
            PrimaryLanguage = "en",
            Localizations = new Dictionary<string, LocalizedContent>
            {
                ["cs"] = new LocalizedContent
                {
                    Title = "Stary titulek",
                    Description = "Stary popis",
                    Tags = ["stary"],
                    SearchPhrases = ["stara fraze"],
                },
            },
            ContentHash = "content-hash-1",
            BasedOn = "Old source",
            Emotions = new EmotionMetadata
            {
                Primary = "humor",
                Sentiment = "positive",
                Intensity = "medium",
                Secondary = ["joy"],
                MemeUsage = ["old usage"],
            },
        };

    private static AnalysisResult CreatePartialResult() =>
        new()
        {
            Emojis = ["🔥"],
            Title = "New title",
            Description = "New description",
            Tags = ["new"],
            SearchPhrases = ["new phrase"],
            BasedOn = "New source",
            Emotions = new EmotionMetadata
            {
                Primary = "anger",
                Sentiment = "negative",
                Intensity = "high",
                Secondary = ["frustration"],
                MemeUsage = ["new usage"],
            },
            Localizations = new Dictionary<string, LocalizedContent>
            {
                ["cs"] = new LocalizedContent
                {
                    Title = "Novy titulek",
                    Description = "Novy popis",
                    Tags = ["novy"],
                    SearchPhrases = ["nova fraze"],
                },
            },
        };

    private sealed class TempArtifact : IDisposable
    {
        public string OutputDir { get; } = Path.Combine(Path.GetTempPath(), "riposte-cli-prop-" + Guid.NewGuid().ToString("N"));
        public string ImagePath => Path.Combine(OutputDir, "image.png");

        public void CreateImageAndSidecar()
        {
            Directory.CreateDirectory(OutputDir);
            File.WriteAllBytes(ImagePath, "not-an-image-but-hashable"u8.ToArray());
            var sidecarDir = Path.Combine(OutputDir, OutputPaths.SidecarDir);
            Directory.CreateDirectory(sidecarDir);
            File.WriteAllText(Path.Combine(sidecarDir, Path.GetFileName(ImagePath) + ".json"), "{}");
        }

        public BuildManifest CreateManifestEntry(Dictionary<string, string> fieldHashes, string model)
        {
            var contentHash = ImageHashService.GetContentHash(ImagePath);
            return new BuildManifest
            {
                Model = model,
                SchemaVersion = "1.4",
                PromptHashes = new Dictionary<string, string>(fieldHashes),
                Images = new Dictionary<string, ImageManifestEntry>(StringComparer.OrdinalIgnoreCase)
                {
                    [Path.GetFileName(ImagePath)] = new ImageManifestEntry
                    {
                        ContentHash = contentHash,
                        Model = model,
                        SchemaVersion = "1.4",
                        GeneratedAt = "2025-01-01T00:00:00.0000000+00:00",
                        FieldHashes = new Dictionary<string, string>(fieldHashes),
                    },
                },
            };
        }

        public void Dispose()
        {
            if (Directory.Exists(OutputDir))
                Directory.Delete(OutputDir, recursive: true);
        }
    }
}
