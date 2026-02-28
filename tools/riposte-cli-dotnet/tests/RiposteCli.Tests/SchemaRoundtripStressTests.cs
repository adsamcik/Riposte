using System.Text.Json;
using RiposteCli.Models;

namespace RiposteCli.Tests;

public sealed class SchemaRoundtripStressTests : IDisposable
{
    private readonly string _tempDir;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
    };

    public SchemaRoundtripStressTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(_tempDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempDir))
            Directory.Delete(_tempDir, recursive: true);
    }

    [Fact]
    public void SidecarMetadata_MaximalSerializeDeserialize_RoundtripsWithDeepEquality()
    {
        var original = CreateMaximalSidecarMetadata();

        var json = JsonSerializer.Serialize(original, JsonOptions);
        var roundtrip = JsonSerializer.Deserialize<SidecarMetadata>(json, JsonOptions);

        Assert.NotNull(roundtrip);
        AssertSidecarEqual(original, roundtrip);
    }

    [Fact]
    public void SidecarMetadata_WriteSidecarThenLoadSidecar_RoundtripsWithDeepEquality()
    {
        var metadata = CreateMaximalSidecarMetadata();
        var outputDir = Path.Combine(_tempDir, "out");
        Directory.CreateDirectory(outputDir);

        var imagePath = Path.Combine(outputDir, "roundtrip.png");
        File.WriteAllBytes(imagePath, [0x89, 0x50, 0x4E, 0x47]);

        var sidecarPath = SidecarService.WriteSidecar(imagePath, metadata, outputDir);
        var loaded = SidecarMerger.LoadSidecar(imagePath, outputDir);

        Assert.Contains($"{Path.DirectorySeparatorChar}sidecars{Path.DirectorySeparatorChar}", sidecarPath);
        Assert.NotNull(loaded);
        AssertSidecarEqual(metadata, loaded);
    }

    [Fact]
    public void SidecarMetadata_UnicodeFields_RoundtripPreservesEncoding()
    {
        var baseline = CreateMaximalSidecarMetadata();
        var metadata = new SidecarMetadata
        {
            SchemaVersion = baseline.SchemaVersion,
            Emojis = baseline.Emojis,
            CreatedAt = baseline.CreatedAt,
            AppVersion = baseline.AppVersion,
            CliToolVersion = baseline.CliToolVersion,
            Title = "猫ミームの王様",
            Description = "هذا وصف عربي مع 日本語 و emoji 😂🔥",
            Tags = ["😂", "🔥", "مرحبا", "日本語"],
            SearchPhrases = ["猫 ミーム", "بحث عربي", "emoji 🔥"],
            BasedOn = "ドラゴンボール",
            PrimaryLanguage = "ja",
            ContentHash = "ユニコード-ハッシュ-١٢٣",
            Emotions = baseline.Emotions,
            Localizations = new Dictionary<string, LocalizedContent>
            {
                ["ja"] = new()
                {
                    Title = "タイトル",
                    Description = "説明",
                    Tags = ["タグ", "絵文字😂"],
                    SearchPhrases = ["検索 フレーズ", "ミーム 共有"],
                },
                ["ar"] = new()
                {
                    Title = "عنوان",
                    Description = "وصف",
                    Tags = ["وسم", "😂"],
                    SearchPhrases = ["عبارة بحث", "ميم مضحك"],
                },
            },
        };

        var json = JsonSerializer.Serialize(metadata, JsonOptions);
        var roundtrip = JsonSerializer.Deserialize<SidecarMetadata>(json, JsonOptions);

        Assert.NotNull(roundtrip);
        AssertSidecarEqual(metadata, roundtrip);
    }

    [Fact]
    public void SidecarMetadata_EmptyStringsVsNull_RoundtripKeepsThemDistinct()
    {
        var emptyMetadata = new SidecarMetadata
        {
            Emojis = ["😶"],
            Title = "",
            Description = "",
        };
        var nullMetadata = new SidecarMetadata
        {
            Emojis = ["😶"],
            Title = null,
            Description = null,
        };

        var emptyJson = JsonSerializer.Serialize(emptyMetadata, JsonOptions);
        var nullJson = JsonSerializer.Serialize(nullMetadata, JsonOptions);
        var emptyRoundtrip = JsonSerializer.Deserialize<SidecarMetadata>(emptyJson, JsonOptions);
        var nullRoundtrip = JsonSerializer.Deserialize<SidecarMetadata>(nullJson, JsonOptions);

        Assert.NotNull(emptyRoundtrip);
        Assert.NotNull(nullRoundtrip);
        Assert.Equal("", emptyRoundtrip.Title);
        Assert.Equal("", emptyRoundtrip.Description);
        Assert.Null(nullRoundtrip.Title);
        Assert.Null(nullRoundtrip.Description);
        Assert.Contains("\"title\":\"\"", emptyJson.Replace(" ", string.Empty).Replace(Environment.NewLine, string.Empty));
        Assert.DoesNotContain("\"title\"", nullJson);
    }

    [Fact]
    public void BuildManifest_MaximalWith100Images_SaveLoad_RoundtripsWithDeepEquality()
    {
        var outputDir = Path.Combine(_tempDir, "manifest-max");
        Directory.CreateDirectory(outputDir);
        var manifest = CreateMaximalManifest(100);

        ManifestService.Save(outputDir, manifest);
        var loaded = ManifestService.Load(outputDir);

        AssertManifestEqual(manifest, loaded);
    }

    [Fact]
    public void BuildManifest_WithOptimizationConfig_SaveLoad_PreservesFingerprint()
    {
        var outputDir = Path.Combine(_tempDir, "manifest-opt");
        Directory.CreateDirectory(outputDir);
        var manifest = CreateMaximalManifest(3);

        ManifestService.Save(outputDir, manifest);
        var loaded = ManifestService.Load(outputDir);

        Assert.NotNull(manifest.Optimization);
        Assert.NotNull(loaded.Optimization);
        Assert.Equal(manifest.Optimization.Fingerprint(), loaded.Optimization.Fingerprint());
    }

    [Fact]
    public void BuildManifest_WithBothBundleTimestamps_SaveLoad_PreservesBoth()
    {
        var outputDir = Path.Combine(_tempDir, "manifest-timestamps");
        Directory.CreateDirectory(outputDir);
        var manifest = CreateMaximalManifest(2) with
        {
            LastFullBundleAt = "2026-01-01T00:00:00Z",
            LastPatchBundleAt = "2026-01-01T01:00:00Z",
        };

        ManifestService.Save(outputDir, manifest);
        var loaded = ManifestService.Load(outputDir);

        Assert.Equal("2026-01-01T00:00:00Z", loaded.LastFullBundleAt);
        Assert.Equal("2026-01-01T01:00:00Z", loaded.LastPatchBundleAt);
    }

    [Fact]
    public void Merge_FullSidecarThenPartialSearchOnly_PreservesNonSearchFields()
    {
        var full = CreateMaximalSidecarMetadata();
        var partial = new AnalysisResult
        {
            Tags = ["new-search-tag-1", "new-search-tag-2"],
            SearchPhrases = ["new phrase 1", "new phrase 2", "new phrase 3"],
        };

        var merged = SidecarMerger.Merge(full, partial, [PromptHasher.GroupSearch]);

        Assert.Equal(partial.Tags, merged.Tags);
        Assert.Equal(partial.SearchPhrases, merged.SearchPhrases);
        Assert.Equal(full.SchemaVersion, merged.SchemaVersion);
        Assert.Equal(full.Emojis, merged.Emojis);
        Assert.Equal(full.CreatedAt, merged.CreatedAt);
        Assert.Equal(full.Title, merged.Title);
        Assert.Equal(full.Description, merged.Description);
        Assert.Equal(full.PrimaryLanguage, merged.PrimaryLanguage);
        Assert.Equal(full.ContentHash, merged.ContentHash);
        Assert.Equal(full.BasedOn, merged.BasedOn);
        AssertEmotionEqual(full.Emotions, merged.Emotions);
        AssertLocalizationsEqual(full.Localizations, merged.Localizations);
    }

    [Fact]
    public void Merge_FullSidecarThenPartialEmotionsOnly_OnlyEmotionsChange()
    {
        var full = CreateMaximalSidecarMetadata();
        var partial = new AnalysisResult
        {
            Emotions = new EmotionMetadata
            {
                Primary = "sadness",
                Sentiment = "negative",
                Intensity = "low",
                Secondary = ["nostalgia", "disappointment"],
                MemeUsage = ["when plans fail", "when Monday hits"],
            },
        };

        var merged = SidecarMerger.Merge(full, partial, [PromptHasher.GroupEmotions]);

        AssertEmotionEqual(partial.Emotions, merged.Emotions);
        Assert.Equal(full.SchemaVersion, merged.SchemaVersion);
        Assert.Equal(full.Emojis, merged.Emojis);
        Assert.Equal(full.CreatedAt, merged.CreatedAt);
        Assert.Equal(full.Title, merged.Title);
        Assert.Equal(full.Description, merged.Description);
        Assert.Equal(full.Tags, merged.Tags);
        Assert.Equal(full.SearchPhrases, merged.SearchPhrases);
        Assert.Equal(full.PrimaryLanguage, merged.PrimaryLanguage);
        Assert.Equal(full.ContentHash, merged.ContentHash);
        Assert.Equal(full.BasedOn, merged.BasedOn);
        AssertLocalizationsEqual(full.Localizations, merged.Localizations);
    }

    [Fact]
    public void Merge_AllBaseGroupsPlusTwoLocalizations_MergesAllExpectedFields()
    {
        var baseline = CreateMaximalSidecarMetadata();
        var existing = new SidecarMetadata
        {
            SchemaVersion = baseline.SchemaVersion,
            Emojis = baseline.Emojis,
            CreatedAt = baseline.CreatedAt,
            AppVersion = baseline.AppVersion,
            CliToolVersion = baseline.CliToolVersion,
            Title = "Old title",
            Description = "Old description",
            Tags = ["old-tag"],
            SearchPhrases = ["old phrase"],
            PrimaryLanguage = baseline.PrimaryLanguage,
            Localizations = new Dictionary<string, LocalizedContent>
            {
                ["fr"] = new() { Title = "Ancien titre" },
            },
            ContentHash = baseline.ContentHash,
            BasedOn = "Old source",
            Emotions = baseline.Emotions,
        };

        var partial = new AnalysisResult
        {
            Emojis = ["🎯", "🚀"],
            Title = "Merged title",
            Description = "Merged description",
            Tags = ["merge-tag-1", "merge-tag-2"],
            SearchPhrases = ["merge phrase 1", "merge phrase 2", "merge phrase 3"],
            BasedOn = "Merged source",
            Emotions = new EmotionMetadata
            {
                Primary = "awe",
                Sentiment = "positive",
                Intensity = "high",
                Secondary = ["excitement"],
                MemeUsage = ["when launch succeeds", "when demo works"],
            },
            Localizations = new Dictionary<string, LocalizedContent>
            {
                ["cs"] = new()
                {
                    Title = "Sloučený titulek",
                    Description = "Sloučený popis",
                    Tags = ["slouceny"],
                    SearchPhrases = ["sloucena fraze"],
                },
                ["de"] = new()
                {
                    Title = "Zusammengeführt",
                    Description = "Beschreibung",
                    Tags = ["zusammen"],
                    SearchPhrases = ["suchbegriff"],
                },
            },
        };

        var groups = new[]
        {
            PromptHasher.GroupCore,
            PromptHasher.GroupSearch,
            PromptHasher.GroupCultural,
            PromptHasher.GroupEmotions,
            PromptHasher.LocalizationGroup("cs"),
            PromptHasher.LocalizationGroup("de"),
        };

        var merged = SidecarMerger.Merge(existing, partial, groups);

        Assert.Equal(existing.SchemaVersion, merged.SchemaVersion);
        Assert.Equal(existing.CreatedAt, merged.CreatedAt);
        Assert.Equal(existing.PrimaryLanguage, merged.PrimaryLanguage);
        Assert.Equal(existing.ContentHash, merged.ContentHash);
        Assert.Equal(partial.Emojis, merged.Emojis);
        Assert.Equal(partial.Title, merged.Title);
        Assert.Equal(partial.Description, merged.Description);
        Assert.Equal(partial.Tags, merged.Tags);
        Assert.Equal(partial.SearchPhrases, merged.SearchPhrases);
        Assert.Equal(partial.BasedOn, merged.BasedOn);
        AssertEmotionEqual(partial.Emotions, merged.Emotions);
        Assert.NotNull(merged.Localizations);
        Assert.True(merged.Localizations.ContainsKey("cs"));
        Assert.True(merged.Localizations.ContainsKey("de"));
        Assert.True(merged.Localizations.ContainsKey("fr"));
        Assert.Equal("Sloučený titulek", merged.Localizations["cs"].Title);
        Assert.Equal("Sloučený popis", merged.Localizations["cs"].Description);
        Assert.Equal(new[] { "slouceny" }, merged.Localizations["cs"].Tags);
        Assert.Equal(new[] { "sloucena fraze" }, merged.Localizations["cs"].SearchPhrases);
        Assert.Equal("Zusammengeführt", merged.Localizations["de"].Title);
        Assert.Equal("Beschreibung", merged.Localizations["de"].Description);
        Assert.Equal(new[] { "zusammen" }, merged.Localizations["de"].Tags);
        Assert.Equal(new[] { "suchbegriff" }, merged.Localizations["de"].SearchPhrases);
        Assert.Equal("Ancien titre", merged.Localizations["fr"].Title);
    }

    [Fact]
    public void Sidecar_WriteLoadWrite_IsByteIdentical()
    {
        var outputDir = Path.Combine(_tempDir, "idempotent-sidecar");
        Directory.CreateDirectory(outputDir);
        var metadata = CreateMaximalSidecarMetadata();
        var imageA = Path.Combine(outputDir, "a.jpg");
        var imageB = Path.Combine(outputDir, "b.jpg");

        var firstPath = SidecarService.WriteSidecar(imageA, metadata, outputDir);
        var loaded = SidecarMerger.LoadSidecar(imageA, outputDir);
        Assert.NotNull(loaded);
        var secondPath = SidecarService.WriteSidecar(imageB, loaded, outputDir);

        Assert.Equal(File.ReadAllBytes(firstPath), File.ReadAllBytes(secondPath));
    }

    [Fact]
    public void Manifest_SaveLoadSave_IsByteIdentical_WhenTimestampsFixed()
    {
        var outputDir = Path.Combine(_tempDir, "idempotent-manifest");
        Directory.CreateDirectory(outputDir);
        var manifest = CreateMaximalManifest(5) with
        {
            LastFullBundleAt = "2026-01-10T00:00:00Z",
            LastPatchBundleAt = "2026-01-10T00:05:00Z",
        };

        ManifestService.Save(outputDir, manifest);
        var firstPath = Path.Combine(outputDir, BuildManifest.FileName);
        var loaded = ManifestService.Load(outputDir);
        var secondDir = Path.Combine(_tempDir, "idempotent-manifest-2");
        Directory.CreateDirectory(secondDir);
        ManifestService.Save(secondDir, loaded);
        var secondPath = Path.Combine(secondDir, BuildManifest.FileName);

        Assert.Equal(File.ReadAllBytes(firstPath), File.ReadAllBytes(secondPath));
    }

    [Fact]
    public void Sidecar_TitleContainingJsonLikeContent_RoundtripsWithoutInjection()
    {
        var metadata = new SidecarMetadata
        {
            Emojis = ["🧪"],
            Title = "he said {\"hello\"}",
            Description = "safe",
        };

        var json = JsonSerializer.Serialize(metadata, JsonOptions);
        var roundtrip = JsonSerializer.Deserialize<SidecarMetadata>(json, JsonOptions);

        Assert.NotNull(roundtrip);
        Assert.Equal("he said {\"hello\"}", roundtrip.Title);
    }

    [Fact]
    public void Sidecar_DescriptionContainingMarkdownJsonBlock_RoundtripsCorrectly()
    {
        var metadata = new SidecarMetadata
        {
            Emojis = ["🧪"],
            Title = "markdown",
            Description = """
                Example payload:
                ```json
                {"foo":"bar","nested":{"a":1}}
                ```
                End.
                """,
        };

        var json = JsonSerializer.Serialize(metadata, JsonOptions);
        var roundtrip = JsonSerializer.Deserialize<SidecarMetadata>(json, JsonOptions);

        Assert.NotNull(roundtrip);
        Assert.Equal(metadata.Description, roundtrip.Description);
    }

    [Fact]
    public void Sidecar_TagsWithBackslashesAndQuotes_RoundtripsCorrectly()
    {
        var metadata = new SidecarMetadata
        {
            Emojis = ["🧪"],
            Tags = ["path\\to\\meme", "quote\"inside", "combo\\\"tag"],
        };

        var json = JsonSerializer.Serialize(metadata, JsonOptions);
        var roundtrip = JsonSerializer.Deserialize<SidecarMetadata>(json, JsonOptions);

        Assert.NotNull(roundtrip);
        Assert.Equal(metadata.Tags, roundtrip.Tags);
    }

    [Fact]
    public void SidecarMetadata_FivePlusLocalizations_RoundtripsAllLanguages()
    {
        var metadata = new SidecarMetadata
        {
            Emojis = ["🌍"],
            Title = "Polyglot meme",
            PrimaryLanguage = "en",
            Localizations = new Dictionary<string, LocalizedContent>
            {
                ["cs"] = new()
                {
                    Title = "Český",
                    Description = "Český popis",
                    Tags = ["vtip"],
                    SearchPhrases = ["český meme"],
                },
                ["de"] = new()
                {
                    Title = "Deutsch",
                    Description = "Deutsche Beschreibung",
                    Tags = ["lustig"],
                    SearchPhrases = ["deutsches meme"],
                },
                ["fr"] = new()
                {
                    Title = "Français",
                    Description = "Description française",
                    Tags = ["drôle"],
                    SearchPhrases = ["mème français"],
                },
                ["ja"] = new()
                {
                    Title = "日本語タイトル",
                    Description = "日本語の説明",
                    Tags = ["ミーム", "面白い"],
                    SearchPhrases = ["日本語ミーム", "面白い画像"],
                },
                ["ar"] = new()
                {
                    Title = "عنوان عربي",
                    Description = "وصف عربي",
                    Tags = ["ميم", "مضحك"],
                    SearchPhrases = ["ميم عربي"],
                },
                ["ko"] = new()
                {
                    Title = "한국어 제목",
                    Description = "한국어 설명",
                    Tags = ["밈"],
                    SearchPhrases = ["한국어 밈"],
                },
            },
        };

        var json = JsonSerializer.Serialize(metadata, JsonOptions);
        var roundtrip = JsonSerializer.Deserialize<SidecarMetadata>(json, JsonOptions);

        Assert.NotNull(roundtrip);
        AssertSidecarEqual(metadata, roundtrip);
    }

    [Fact]
    public void SidecarMetadata_FivePlusLocalizations_WriteSidecarThenLoad_RoundtripsAllLanguages()
    {
        var metadata = new SidecarMetadata
        {
            Emojis = ["🌍"],
            Title = "Polyglot file roundtrip",
            PrimaryLanguage = "en",
            Localizations = new Dictionary<string, LocalizedContent>
            {
                ["cs"] = new() { Title = "Český", Description = "Popis", Tags = ["tag-cs"], SearchPhrases = ["fráze"] },
                ["de"] = new() { Title = "Deutsch", Description = "Beschreibung", Tags = ["tag-de"], SearchPhrases = ["suche"] },
                ["fr"] = new() { Title = "Français", Description = "Description", Tags = ["tag-fr"], SearchPhrases = ["phrase"] },
                ["ja"] = new() { Title = "日本語", Description = "説明", Tags = ["tag-ja"], SearchPhrases = ["検索"] },
                ["ar"] = new() { Title = "عربي", Description = "وصف", Tags = ["tag-ar"], SearchPhrases = ["بحث"] },
            },
        };

        var outputDir = Path.Combine(_tempDir, "polyglot");
        Directory.CreateDirectory(outputDir);
        var imagePath = Path.Combine(outputDir, "polyglot.png");
        File.WriteAllBytes(imagePath, [0x89, 0x50, 0x4E, 0x47]);

        SidecarService.WriteSidecar(imagePath, metadata, outputDir);
        var loaded = SidecarMerger.LoadSidecar(imagePath, outputDir);

        Assert.NotNull(loaded);
        AssertSidecarEqual(metadata, loaded);
    }

    [Fact]
    public void BuildManifest_OptimizationConfigEdgeValues_SaveLoad_RoundtripsCorrectly()
    {
        var outputDir = Path.Combine(_tempDir, "manifest-edge");
        Directory.CreateDirectory(outputDir);
        var manifest = new BuildManifest
        {
            ManifestVersion = BuildManifest.CurrentManifestVersion,
            SchemaVersion = "1.4",
            Model = "gpt-5-mini",
            Optimization = new OptimizationConfig
            {
                ApiMaxDimension = 0,
                ApiFormat = "",
                BundleMaxDimension = int.MaxValue,
                BundleFormat = "webp",
                Quality = 1,
            },
            Images = new Dictionary<string, ImageManifestEntry>
            {
                ["edge.png"] = new()
                {
                    ContentHash = "edge-hash",
                    Model = "gpt-5-mini",
                    GeneratedAt = "2026-01-01T00:00:00Z",
                    HasApiOptimized = false,
                    HasBundleOptimized = false,
                },
            },
        };

        ManifestService.Save(outputDir, manifest);
        var loaded = ManifestService.Load(outputDir);

        Assert.NotNull(loaded.Optimization);
        Assert.Equal(0, loaded.Optimization.ApiMaxDimension);
        Assert.Equal("", loaded.Optimization.ApiFormat);
        Assert.Equal(int.MaxValue, loaded.Optimization.BundleMaxDimension);
        Assert.Equal(1, loaded.Optimization.Quality);
        Assert.Equal(manifest.Optimization.Fingerprint(), loaded.Optimization.Fingerprint());
    }

    [Fact]
    public void Merge_CreatedAtIsNeverOverwritten_AcrossAllGroupCombinations()
    {
        var existing = CreateMaximalSidecarMetadata();
        var originalCreatedAt = existing.CreatedAt;

        var partial = new AnalysisResult
        {
            Emojis = ["🆕"],
            Title = "New title",
            Description = "New desc",
            Tags = ["new-tag"],
            SearchPhrases = ["new phrase"],
            BasedOn = "New source",
            Emotions = new EmotionMetadata
            {
                Primary = "awe",
                Sentiment = "positive",
                Intensity = "high",
            },
            Localizations = new Dictionary<string, LocalizedContent>
            {
                ["cs"] = new() { Title = "Nový" },
            },
        };

        var allGroups = new[]
        {
            PromptHasher.GroupCore,
            PromptHasher.GroupSearch,
            PromptHasher.GroupCultural,
            PromptHasher.GroupEmotions,
            PromptHasher.LocalizationGroup("cs"),
        };

        var merged = SidecarMerger.Merge(existing, partial, allGroups);

        Assert.Equal(originalCreatedAt, merged.CreatedAt);
        Assert.Equal(existing.SchemaVersion, merged.SchemaVersion);
        Assert.Equal(existing.PrimaryLanguage, merged.PrimaryLanguage);
        Assert.Equal(existing.ContentHash, merged.ContentHash);
    }

    private static SidecarMetadata CreateMaximalSidecarMetadata() => new()
    {
        SchemaVersion = "1.4",
        Emojis = ["😂", "🔥", "🤖", "🎯"],
        CreatedAt = "2026-01-01T00:00:00Z",
        AppVersion = "cli-1.0.0",
        CliToolVersion = "1.0.0",
        Title = "Maximal sidecar title",
        Description = "Maximal sidecar description for stress roundtrip checks.",
        Tags =
        [
            "tag01", "tag02", "tag03", "tag04", "tag05",
            "tag06", "tag07", "tag08", "tag09", "tag10",
            "tag11", "tag12", "tag13", "tag14", "tag15",
        ],
        SearchPhrases = ["phrase one", "phrase two", "phrase three"],
        PrimaryLanguage = "en",
        ContentHash = "sha256-maximal-content-hash",
        BasedOn = "Classic template",
        Emotions = new EmotionMetadata
        {
            Primary = "humor",
            Sentiment = "positive",
            Intensity = "high",
            Secondary = ["joy", "amusement", "relatability", "surprise"],
            MemeUsage = ["when code compiles", "after fixing production", "when tests pass"],
        },
        Localizations = new Dictionary<string, LocalizedContent>
        {
            ["cs"] = new()
            {
                Title = "Český titulek",
                Description = "Český popis",
                Tags = ["vtip", "programování"],
                SearchPhrases = ["český meme", "vtipný obrázek"],
            },
            ["de"] = new()
            {
                Title = "Deutscher Titel",
                Description = "Deutsche Beschreibung",
                Tags = ["lustig", "programmierung"],
                SearchPhrases = ["deutsches meme", "witziges bild"],
            },
            ["fr"] = new()
            {
                Title = "Titre français",
                Description = "Description française",
                Tags = ["drôle", "code"],
                SearchPhrases = ["mème français", "image drôle"],
            },
        },
    };

    private static BuildManifest CreateMaximalManifest(int imageCount) => new()
    {
        ManifestVersion = BuildManifest.CurrentManifestVersion,
        SchemaVersion = "1.4",
        Model = "gpt-5-mini",
        PromptHashes = new Dictionary<string, string>
        {
            [PromptHasher.GroupCore] = "hash-core",
            [PromptHasher.GroupSearch] = "hash-search",
            [PromptHasher.GroupCultural] = "hash-cultural",
            [PromptHasher.GroupEmotions] = "hash-emotions",
            [PromptHasher.LocalizationGroup("cs")] = "hash-cs",
            [PromptHasher.LocalizationGroup("de")] = "hash-de",
        },
        Optimization = new OptimizationConfig
        {
            ApiMaxDimension = 1200,
            ApiFormat = "original",
            BundleMaxDimension = 1200,
            BundleFormat = "webp",
            Quality = 85,
        },
        Images = Enumerable.Range(1, imageCount).ToDictionary(
            i => $"image-{i:000}.png",
            i => new ImageManifestEntry
            {
                ContentHash = $"content-hash-{i:000}",
                SchemaVersion = "1.4",
                Model = "gpt-5-mini",
                GeneratedAt = $"2026-01-{((i - 1) % 28) + 1:00}T12:00:00Z",
                FieldHashes = new Dictionary<string, string>
                {
                    [PromptHasher.GroupCore] = $"core-{i:000}",
                    [PromptHasher.GroupSearch] = $"search-{i:000}",
                    [PromptHasher.GroupCultural] = $"cultural-{i:000}",
                    [PromptHasher.GroupEmotions] = $"emotions-{i:000}",
                    [PromptHasher.LocalizationGroup("cs")] = $"cs-{i:000}",
                    [PromptHasher.LocalizationGroup("de")] = $"de-{i:000}",
                },
                OptimizationFingerprint = "api:1200:original|bundle:1200:webp|q:85",
                HasApiOptimized = true,
                HasBundleOptimized = i % 2 == 0,
            }),
    };

    private static void AssertSidecarEqual(SidecarMetadata expected, SidecarMetadata actual)
    {
        Assert.Equal(expected.SchemaVersion, actual.SchemaVersion);
        Assert.Equal(expected.Emojis, actual.Emojis);
        Assert.Equal(expected.CreatedAt, actual.CreatedAt);
        Assert.Equal(expected.AppVersion, actual.AppVersion);
        Assert.Equal(expected.CliToolVersion, actual.CliToolVersion);
        Assert.Equal(expected.Title, actual.Title);
        Assert.Equal(expected.Description, actual.Description);
        Assert.Equal(expected.Tags, actual.Tags);
        Assert.Equal(expected.SearchPhrases, actual.SearchPhrases);
        Assert.Equal(expected.PrimaryLanguage, actual.PrimaryLanguage);
        Assert.Equal(expected.ContentHash, actual.ContentHash);
        Assert.Equal(expected.BasedOn, actual.BasedOn);
        AssertEmotionEqual(expected.Emotions, actual.Emotions);
        AssertLocalizationsEqual(expected.Localizations, actual.Localizations);
    }

    private static void AssertEmotionEqual(EmotionMetadata? expected, EmotionMetadata? actual)
    {
        if (expected is null)
        {
            Assert.Null(actual);
            return;
        }

        Assert.NotNull(actual);
        Assert.Equal(expected.Primary, actual.Primary);
        Assert.Equal(expected.Secondary, actual.Secondary);
        Assert.Equal(expected.Sentiment, actual.Sentiment);
        Assert.Equal(expected.Intensity, actual.Intensity);
        Assert.Equal(expected.MemeUsage, actual.MemeUsage);
    }

    private static void AssertLocalizationsEqual(
        Dictionary<string, LocalizedContent>? expected,
        Dictionary<string, LocalizedContent>? actual)
    {
        if (expected is null)
        {
            Assert.Null(actual);
            return;
        }

        Assert.NotNull(actual);
        Assert.Equal(expected.Count, actual.Count);
        foreach (var (key, expectedValue) in expected)
        {
            Assert.True(actual.ContainsKey(key));
            var actualValue = actual[key];
            Assert.Equal(expectedValue.Title, actualValue.Title);
            Assert.Equal(expectedValue.Description, actualValue.Description);
            Assert.Equal(expectedValue.Tags, actualValue.Tags);
            Assert.Equal(expectedValue.SearchPhrases, actualValue.SearchPhrases);
        }
    }

    private static void AssertManifestEqual(BuildManifest expected, BuildManifest actual)
    {
        Assert.Equal(expected.ManifestVersion, actual.ManifestVersion);
        Assert.Equal(expected.SchemaVersion, actual.SchemaVersion);
        Assert.Equal(expected.Model, actual.Model);
        Assert.Equal(expected.PromptHashes, actual.PromptHashes);
        Assert.Equal(expected.LastFullBundleAt, actual.LastFullBundleAt);
        Assert.Equal(expected.LastPatchBundleAt, actual.LastPatchBundleAt);

        if (expected.Optimization is null)
        {
            Assert.Null(actual.Optimization);
        }
        else
        {
            Assert.NotNull(actual.Optimization);
            Assert.Equal(expected.Optimization.ApiMaxDimension, actual.Optimization.ApiMaxDimension);
            Assert.Equal(expected.Optimization.ApiFormat, actual.Optimization.ApiFormat);
            Assert.Equal(expected.Optimization.BundleMaxDimension, actual.Optimization.BundleMaxDimension);
            Assert.Equal(expected.Optimization.BundleFormat, actual.Optimization.BundleFormat);
            Assert.Equal(expected.Optimization.Quality, actual.Optimization.Quality);
            Assert.Equal(expected.Optimization.Fingerprint(), actual.Optimization.Fingerprint());
        }

        Assert.Equal(expected.Images.Count, actual.Images.Count);
        foreach (var (key, expectedImage) in expected.Images)
        {
            Assert.True(actual.Images.ContainsKey(key));
            var actualImage = actual.Images[key];
            Assert.Equal(expectedImage.ContentHash, actualImage.ContentHash);
            Assert.Equal(expectedImage.SchemaVersion, actualImage.SchemaVersion);
            Assert.Equal(expectedImage.Model, actualImage.Model);
            Assert.Equal(expectedImage.GeneratedAt, actualImage.GeneratedAt);
            Assert.Equal(expectedImage.FieldHashes, actualImage.FieldHashes);
            Assert.Equal(expectedImage.OptimizationFingerprint, actualImage.OptimizationFingerprint);
            Assert.Equal(expectedImage.HasApiOptimized, actualImage.HasApiOptimized);
            Assert.Equal(expectedImage.HasBundleOptimized, actualImage.HasBundleOptimized);
        }
    }
}
