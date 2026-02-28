using RiposteCli.Models;
using System.Text.Json;

namespace RiposteCli.Tests;

public sealed class BackwardCompatTests : IDisposable
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

    public BackwardCompatTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), $"riposte-backward-compat-{Guid.NewGuid()}");
        _outputDir = Path.Combine(_tempDir, "output");
        Directory.CreateDirectory(_tempDir);
        Directory.CreateDirectory(_outputDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempDir))
            Directory.Delete(_tempDir, recursive: true);
    }

    [Fact]
    public void AnalysisResult_UnknownFields_AreIgnored()
    {
        var json = """{"emojis":["😂"],"futureField":"value"}""";

        var result = JsonSerializer.Deserialize<AnalysisResult>(json);

        Assert.NotNull(result);
        Assert.Equal(["😂"], result.Emojis);
    }

    [Fact]
    public void SidecarMetadata_UnknownFields_AreIgnored()
    {
        var json = """
            {
              "schemaVersion": "1.4",
              "emojis": ["😂"],
              "title": "Known",
              "newFeature": { "enabled": true }
            }
            """;

        var result = JsonSerializer.Deserialize<SidecarMetadata>(json);

        Assert.NotNull(result);
        Assert.Equal("1.4", result.SchemaVersion);
        Assert.Equal(["😂"], result.Emojis);
        Assert.Equal("Known", result.Title);
    }

    [Fact]
    public void BuildManifest_UnknownFields_AreIgnored()
    {
        var json = """
            {
              "manifestVersion": "1.0",
              "schemaVersion": "1.4",
              "model": "gpt-5-mini",
              "promptHashes": {},
              "images": {},
              "analytics": { "enabled": true }
            }
            """;

        var result = JsonSerializer.Deserialize<BuildManifest>(json);

        Assert.NotNull(result);
        Assert.Equal("1.0", result.ManifestVersion);
        Assert.Equal("1.4", result.SchemaVersion);
    }

    [Fact]
    public void EmotionMetadata_UnknownFields_AreIgnored()
    {
        var json = """{"primary":"joy","sentiment":"positive","vibe":"chill"}""";

        var result = JsonSerializer.Deserialize<EmotionMetadata>(json);

        Assert.NotNull(result);
        Assert.Equal("joy", result.Primary);
        Assert.Equal("positive", result.Sentiment);
    }

    [Fact]
    public void LocalizedContent_UnknownFields_AreIgnored()
    {
        var json = """{"title":"x","quality":5}""";

        var result = JsonSerializer.Deserialize<LocalizedContent>(json);

        Assert.NotNull(result);
        Assert.Equal("x", result.Title);
        Assert.Null(result.Description);
    }

    [Fact]
    public void SidecarMetadata_V1_3WithoutEmotionsAndBasedOn_DeserializesWithNulls()
    {
        var json = """
            {
              "schemaVersion": "1.3",
              "emojis": ["😂"],
              "title": "Legacy",
              "description": "Legacy description",
              "tags": ["old"],
              "searchPhrases": ["legacy phrase"]
            }
            """;

        var result = JsonSerializer.Deserialize<SidecarMetadata>(json);

        Assert.NotNull(result);
        Assert.Equal("1.3", result.SchemaVersion);
        Assert.Equal(["😂"], result.Emojis);
        Assert.Null(result.Emotions);
        Assert.Null(result.BasedOn);
    }

    [Fact]
    public void SidecarMetadata_V1_0Minimal_Deserializes()
    {
        var json = """
            {
              "schemaVersion": "1.0",
              "emojis": ["🔥"],
              "title": "Old title",
              "description": "Old description"
            }
            """;

        var result = JsonSerializer.Deserialize<SidecarMetadata>(json);

        Assert.NotNull(result);
        Assert.Equal("1.0", result.SchemaVersion);
        Assert.Equal(["🔥"], result.Emojis);
        Assert.Equal("Old title", result.Title);
    }

    [Fact]
    public void SidecarMetadata_WithoutSchemaVersion_UsesDefault()
    {
        var json = """{"emojis":["😂"],"title":"No schema"}""";

        var result = JsonSerializer.Deserialize<SidecarMetadata>(json);

        Assert.NotNull(result);
        Assert.Equal("1.4", result.SchemaVersion);
    }

    [Fact]
    public void BuildManifest_WithoutOptimization_DeserializesWithNullOptimization()
    {
        var json = """
            {
              "manifestVersion": "1.0",
              "schemaVersion": "1.4",
              "model": "gpt-5-mini",
              "promptHashes": {},
              "images": {}
            }
            """;

        var result = JsonSerializer.Deserialize<BuildManifest>(json);

        Assert.NotNull(result);
        Assert.Null(result.Optimization);
    }

    [Fact]
    public void BuildManifest_WithoutBundleTimestamps_DeserializesWithNulls()
    {
        var json = """
            {
              "manifestVersion": "1.0",
              "schemaVersion": "1.4",
              "model": "gpt-5-mini",
              "promptHashes": {},
              "images": {}
            }
            """;

        var result = JsonSerializer.Deserialize<BuildManifest>(json);

        Assert.NotNull(result);
        Assert.Null(result.LastFullBundleAt);
        Assert.Null(result.LastPatchBundleAt);
    }

    [Fact]
    public void ImageManifestEntry_WithoutOptimizationFingerprint_DeserializesWithNull()
    {
        var json = """
            {
              "contentHash": "hash",
              "schemaVersion": "1.4",
              "model": "gpt-5-mini",
              "generatedAt": "2025-01-01T00:00:00Z",
              "fieldHashes": {}
            }
            """;

        var result = JsonSerializer.Deserialize<ImageManifestEntry>(json);

        Assert.NotNull(result);
        Assert.Null(result.OptimizationFingerprint);
    }

    [Fact]
    public void ImageManifestEntry_WithoutHasBundleOptimized_DefaultsToFalse()
    {
        var json = """
            {
              "contentHash": "hash",
              "schemaVersion": "1.4",
              "model": "gpt-5-mini",
              "generatedAt": "2025-01-01T00:00:00Z",
              "fieldHashes": {},
              "hasApiOptimized": true
            }
            """;

        var result = JsonSerializer.Deserialize<ImageManifestEntry>(json);

        Assert.NotNull(result);
        Assert.False(result.HasBundleOptimized);
    }

    [Fact]
    public void Sidecar_V1_3_MergedWithEmotions_CanBeUpgradedToV1_4()
    {
        var v13Json = """
            {
              "schemaVersion": "1.3",
              "emojis": ["😂"],
              "title": "Legacy title",
              "description": "Legacy description"
            }
            """;
        var legacy = JsonSerializer.Deserialize<SidecarMetadata>(v13Json)!;
        var partial = new AnalysisResult
        {
            Emotions = new EmotionMetadata
            {
                Primary = "joy",
                Sentiment = "positive",
            },
        };

        var merged = SidecarMerger.Merge(legacy, partial, [PromptHasher.GroupEmotions]);
        var upgraded = new SidecarMetadata
        {
            SchemaVersion = "1.4",
            Emojis = merged.Emojis,
            CreatedAt = merged.CreatedAt,
            CliToolVersion = merged.CliToolVersion,
            AppVersion = merged.AppVersion,
            Title = merged.Title,
            Description = merged.Description,
            Tags = merged.Tags,
            SearchPhrases = merged.SearchPhrases,
            PrimaryLanguage = merged.PrimaryLanguage,
            Localizations = merged.Localizations,
            ContentHash = merged.ContentHash,
            BasedOn = merged.BasedOn,
            Emotions = merged.Emotions,
        };

        Assert.Equal("1.4", upgraded.SchemaVersion);
        Assert.Equal(["😂"], upgraded.Emojis);
        Assert.NotNull(upgraded.Emotions);
        Assert.Equal("joy", upgraded.Emotions.Primary);
        Assert.Equal("positive", upgraded.Emotions.Sentiment);
    }

    [Fact]
    public void EmptyManifest_WithLegacySidecar_PlannerReturnsFull()
    {
        var imagePath = CreateTestImage("legacy.png");
        SidecarService.WriteSidecar(imagePath, new SidecarMetadata { Emojis = ["😂"] }, _outputDir);

        var manifest = new BuildManifest();
        var promptHashes = new Dictionary<string, string>
        {
            [PromptHasher.GroupCore] = "h1",
            [PromptHasher.GroupSearch] = "h2",
            [PromptHasher.GroupCultural] = "h3",
            [PromptHasher.GroupEmotions] = "h4",
        };

        var plan = RebuildPlanner.PlanForImage(
            imagePath,
            manifest,
            promptHashes,
            currentModel: "gpt-5-mini",
            currentSchemaVersion: "1.4",
            outputDir: _outputDir);

        Assert.Equal(RebuildScope.Full, plan.Scope);
        Assert.Contains("legacy", plan.Reason, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void PreOptimizationManifest_LoadsWithNullOptimization()
    {
        var manifestJson = """
            {
              "manifestVersion": "1.0",
              "schemaVersion": "1.4",
              "model": "gpt-5-mini",
              "promptHashes": { "core": "h1" },
              "images": {
                "old.png": {
                  "contentHash": "abc",
                  "schemaVersion": "1.4",
                  "model": "gpt-5-mini",
                  "generatedAt": "2025-01-01T00:00:00Z",
                  "fieldHashes": { "core": "h1" }
                }
              }
            }
            """;
        File.WriteAllText(Path.Combine(_outputDir, BuildManifest.FileName), manifestJson);

        var loaded = ManifestService.Load(_outputDir);

        Assert.NotNull(loaded);
        Assert.Null(loaded.Optimization);
    }

    [Fact]
    public void SidecarMetadata_WithPascalCasePropertyNames_FailsDeserialization()
    {
        var pascalCaseJson = """
            {
              "SchemaVersion": "1.4",
              "Emojis": ["😂"],
              "Title": "Wrong casing"
            }
            """;

        Assert.Throws<JsonException>(() => JsonSerializer.Deserialize<SidecarMetadata>(pascalCaseJson));
    }

    [Fact]
    public void BuildManifest_WithPascalCasePropertyNames_FailsToBindExpectedValues()
    {
        var pascalCaseJson = """
            {
              "ManifestVersion": "9.9",
              "SchemaVersion": "9.9",
              "Model": "legacy-model",
              "PromptHashes": {},
              "Images": {
                "legacy.png": {
                  "ContentHash": "abc",
                  "SchemaVersion": "9.9",
                  "Model": "legacy-model",
                  "GeneratedAt": "2025-01-01T00:00:00Z",
                  "FieldHashes": {}
                }
              }
            }
            """;

        var result = JsonSerializer.Deserialize<BuildManifest>(pascalCaseJson);

        Assert.NotNull(result);
        Assert.Equal(BuildManifest.CurrentManifestVersion, result.ManifestVersion);
        Assert.Equal("1.4", result.SchemaVersion);
        Assert.Equal("gpt-5-mini", result.Model);
        Assert.Empty(result.Images);
    }

    [Fact]
    public void FullUpgradePath_LegacySidecar_FullRebuild_ThenSkipOnSecondRun()
    {
        var imagePath = CreateTestImage("upgrade.png");
        SidecarService.WriteSidecar(imagePath, new SidecarMetadata { Emojis = ["🔥"] }, _outputDir);

        var manifest = new BuildManifest();
        var promptHashes = new Dictionary<string, string>
        {
            [PromptHasher.GroupCore] = "h1",
            [PromptHasher.GroupSearch] = "h2",
            [PromptHasher.GroupCultural] = "h3",
            [PromptHasher.GroupEmotions] = "h4",
        };

        // First run: legacy sidecar, no manifest entry → Full rebuild
        var firstPlan = RebuildPlanner.PlanForImage(
            imagePath, manifest, promptHashes,
            currentModel: "gpt-5-mini", currentSchemaVersion: "1.4", outputDir: _outputDir);

        Assert.Equal(RebuildScope.Full, firstPlan.Scope);

        // Simulate successful rebuild: record in manifest
        ManifestService.RecordImageBuild(
            manifest,
            Path.GetFileName(imagePath),
            ImageHashService.GetContentHash(imagePath),
            model: "gpt-5-mini",
            schemaVersion: "1.4",
            fieldHashes: promptHashes);

        // Second run: same config → Skip
        var secondPlan = RebuildPlanner.PlanForImage(
            imagePath, manifest, promptHashes,
            currentModel: "gpt-5-mini", currentSchemaVersion: "1.4", outputDir: _outputDir);

        Assert.Equal(RebuildScope.Skip, secondPlan.Scope);
    }

    [Fact]
    public void MigrateLegacyLayout_MovesSidecarsToSubdir()
    {
        var imagePath = CreateTestImage("photo.jpg");

        // Write sidecar directly in the legacy flat location (outputDir root)
        var legacyPath = Path.Combine(_outputDir, "photo.jpg.json");
        File.WriteAllText(legacyPath, """{"schemaVersion":"1.3","emojis":["😂"],"title":"Legacy"}""");

        Assert.True(File.Exists(legacyPath));

        var migrated = OutputPaths.MigrateLegacyLayout(_outputDir);

        Assert.Equal(1, migrated);
        Assert.False(File.Exists(legacyPath));
        Assert.True(File.Exists(Path.Combine(_outputDir, OutputPaths.SidecarDir, "photo.jpg.json")));
    }

    [Fact]
    public void MigrateLegacyLayout_SkipsManifestFiles()
    {
        // Write manifest in outputDir — should NOT be migrated
        File.WriteAllText(
            Path.Combine(_outputDir, BuildManifest.FileName),
            """{"manifestVersion":"1.0","schemaVersion":"1.4","model":"gpt-5-mini","promptHashes":{},"images":{}}""");

        var migrated = OutputPaths.MigrateLegacyLayout(_outputDir);

        Assert.Equal(0, migrated);
        Assert.True(File.Exists(Path.Combine(_outputDir, BuildManifest.FileName)));
    }

    [Fact]
    public void MigrateLegacyLayout_MigratedSidecar_StillFoundByHasSidecar()
    {
        var imagePath = CreateTestImage("migrated.png");

        // Place sidecar in legacy flat location
        var legacyPath = Path.Combine(_outputDir, "migrated.png.json");
        File.WriteAllText(legacyPath, """{"schemaVersion":"1.3","emojis":["🔥"],"title":"Legacy"}""");

        Assert.True(SidecarService.HasSidecar(imagePath, _outputDir));

        OutputPaths.MigrateLegacyLayout(_outputDir);

        // After migration, sidecar should still be found
        Assert.True(SidecarService.HasSidecar(imagePath, _outputDir));
    }

    [Fact]
    public void ManifestLoad_OlderVersionMissingBundleTimestamps_RoundTripsWithNulls()
    {
        var manifestJson = """
            {
              "manifestVersion": "1.0",
              "schemaVersion": "1.3",
              "model": "gpt-5-mini",
              "promptHashes": { "core": "abc" },
              "images": {
                "old.png": {
                  "contentHash": "def",
                  "schemaVersion": "1.3",
                  "model": "gpt-5-mini",
                  "generatedAt": "2025-01-01T00:00:00Z",
                  "fieldHashes": { "core": "abc" }
                }
              }
            }
            """;
        File.WriteAllText(Path.Combine(_outputDir, BuildManifest.FileName), manifestJson);

        var loaded = ManifestService.Load(_outputDir);

        Assert.NotNull(loaded);
        Assert.Null(loaded.Optimization);
        Assert.Null(loaded.LastFullBundleAt);
        Assert.Null(loaded.LastPatchBundleAt);
        Assert.Single(loaded.Images);
        Assert.Null(loaded.Images["old.png"].OptimizationFingerprint);
        Assert.False(loaded.Images["old.png"].HasApiOptimized);
        Assert.False(loaded.Images["old.png"].HasBundleOptimized);
    }

    [Fact]
    public void StaleManifestEntry_ImageDeletedFromDisk_PlannerSkipsWithoutCrash()
    {
        // Create image, record it in manifest, then delete image from disk
        var imagePath = CreateTestImage("stale.png");
        var manifest = new BuildManifest();
        var promptHashes = new Dictionary<string, string>
        {
            [PromptHasher.GroupCore] = "h1",
            [PromptHasher.GroupSearch] = "h2",
            [PromptHasher.GroupCultural] = "h3",
            [PromptHasher.GroupEmotions] = "h4",
        };

        SidecarService.WriteSidecar(imagePath, new SidecarMetadata { Emojis = ["😂"] }, _outputDir);
        ManifestService.RecordImageBuild(
            manifest, Path.GetFileName(imagePath),
            ImageHashService.GetContentHash(imagePath),
            model: "gpt-5-mini", schemaVersion: "1.4", fieldHashes: promptHashes);

        // Delete the image file
        File.Delete(imagePath);

        // Simulate discovery: GetImagesInFolder won't return the deleted file
        var discoveredImages = SidecarService.GetImagesInFolder(_tempDir);
        Assert.DoesNotContain(imagePath, discoveredImages);

        // Manifest still has the stale entry — verify it doesn't break anything
        Assert.True(manifest.Images.ContainsKey("stale.png"));

        // Plan only processes discovered images — stale entry is harmlessly ignored
        var plans = RebuildPlanner.Plan(
            discoveredImages, manifest, promptHashes,
            currentModel: "gpt-5-mini", currentSchemaVersion: "1.4", outputDir: _outputDir);

        Assert.DoesNotContain(plans, p => Path.GetFileName(p.ImagePath) == "stale.png");
    }

    [Fact]
    public void DeletedSidecar_ManifestHasEntry_PlannerReturnsFull()
    {
        var imagePath = CreateTestImage("orphan.png");
        var manifest = new BuildManifest();
        var promptHashes = new Dictionary<string, string>
        {
            [PromptHasher.GroupCore] = "h1",
            [PromptHasher.GroupSearch] = "h2",
            [PromptHasher.GroupCultural] = "h3",
            [PromptHasher.GroupEmotions] = "h4",
        };

        // Write sidecar + record in manifest
        SidecarService.WriteSidecar(imagePath, new SidecarMetadata { Emojis = ["😂"] }, _outputDir);
        ManifestService.RecordImageBuild(
            manifest, Path.GetFileName(imagePath),
            ImageHashService.GetContentHash(imagePath),
            model: "gpt-5-mini", schemaVersion: "1.4", fieldHashes: promptHashes);

        // Delete only the sidecar file (simulate manual deletion)
        var sidecarPath = SidecarService.ResolveSidecarPath(imagePath, _outputDir);
        Assert.NotNull(sidecarPath);
        File.Delete(sidecarPath);

        // Planner should detect missing sidecar and request full rebuild
        var plan = RebuildPlanner.PlanForImage(
            imagePath, manifest, promptHashes,
            currentModel: "gpt-5-mini", currentSchemaVersion: "1.4", outputDir: _outputDir);

        Assert.Equal(RebuildScope.Full, plan.Scope);
        Assert.Contains("no existing sidecar", plan.Reason);
    }

    [Fact]
    public void LegacySidecar_InFlatDir_FoundBeforeMigration()
    {
        var imagePath = CreateTestImage("flat.png");

        // Write sidecar in legacy flat location (not in sidecars/ subdir)
        var legacyPath = Path.Combine(_outputDir, "flat.png.json");
        File.WriteAllText(legacyPath, """{"schemaVersion":"1.3","emojis":["🔥"],"title":"Flat"}""");

        // HasSidecar should find it even at the legacy location
        Assert.True(SidecarService.HasSidecar(imagePath, _outputDir));
    }

    private string CreateTestImage(string filename)
    {
        var path = Path.Combine(_tempDir, filename);
        File.WriteAllBytes(path, MinimalPng);
        return path;
    }
}
