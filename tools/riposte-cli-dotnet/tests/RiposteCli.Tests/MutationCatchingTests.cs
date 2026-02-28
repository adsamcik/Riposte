using RiposteCli.Models;
using RiposteCli.Services;
using System.Text.Json;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;

namespace RiposteCli.Tests;

public sealed class MutationCatchingTests : IDisposable
{
    private readonly string _tempDir;
    private readonly string _outputDir;

    public MutationCatchingTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), $"riposte-mutation-{Guid.NewGuid()}");
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
    public void RebuildPlanner_NoSidecar_MustPlanFull()
    {
        var imagePath = CreateSolidImage("no-sidecar.png", 64, 64, new Rgba32(255, 0, 0));
        var currentHash = ImageHashService.GetContentHash(imagePath);
        var promptHashes = DefaultPromptHashes();

        // Provide a manifest entry that matches everything so that ONLY the sidecar check triggers Full.
        // Without this, an empty manifest itself causes Full via the "not in manifest" branch,
        // making the test tautological for the sidecar mutation.
        var manifest = new BuildManifest
        {
            Images =
            {
                ["no-sidecar.png"] = CreateManifestEntry(
                    currentHash,
                    "gpt-5-mini",
                    new Dictionary<string, string>(promptHashes))
            },
        };

        var plan = RebuildPlanner.PlanForImage(
            imagePath,
            manifest,
            promptHashes,
            "gpt-5-mini",
            "1.4",
            _outputDir);

        // Catches mutation removing `if (!SidecarService.HasSidecar(...))` in RebuildPlanner.PlanForImage.
        // With sidecar check: returns Full (no sidecar exists).
        // Without sidecar check: would return Skip (manifest entry matches on all fields).
        Assert.Equal(RebuildScope.Full, plan.Scope);
    }

    [Fact]
    public void RebuildPlanner_ModelChange_MustPlanFull()
    {
        var imagePath = CreateSolidImage("model-change.png", 64, 64, new Rgba32(10, 20, 30));
        WriteRealSidecar(imagePath);
        var currentHash = ImageHashService.GetContentHash(imagePath);
        var promptHashes = DefaultPromptHashes();

        var manifest = new BuildManifest
        {
            Images =
            {
                ["model-change.png"] = CreateManifestEntry(
                    currentHash,
                    "old-model",
                    new Dictionary<string, string>(promptHashes))
            },
        };

        var plan = RebuildPlanner.PlanForImage(
            imagePath,
            manifest,
            promptHashes,
            "gpt-5-mini",
            "1.4",
            _outputDir);

        // Catches mutation removing `if (entry.Model != currentModel)` full-rebuild branch.
        Assert.Equal(RebuildScope.Full, plan.Scope);
    }

    [Fact]
    public void RebuildPlanner_ContentHashChange_MustPlanFull()
    {
        var imagePath = CreateSolidImage("content-change.png", 64, 64, new Rgba32(0, 255, 0));
        WriteRealSidecar(imagePath);
        var promptHashes = DefaultPromptHashes();

        var manifest = new BuildManifest
        {
            Images =
            {
                ["content-change.png"] = CreateManifestEntry(
                    "definitely-not-the-current-hash",
                    "gpt-5-mini",
                    new Dictionary<string, string>(promptHashes))
            },
        };

        var plan = RebuildPlanner.PlanForImage(
            imagePath,
            manifest,
            promptHashes,
            "gpt-5-mini",
            "1.4",
            _outputDir);

        // Catches mutation removing `if (entry.ContentHash != currentContentHash)` full-rebuild branch.
        Assert.Equal(RebuildScope.Full, plan.Scope);
    }

    [Fact]
    public void RebuildPlanner_FullPlan_FromModelChange_MustKeepNeedsReoptimization()
    {
        var imagePath = CreateSolidImage("full-needs-reopt.png", 64, 64, new Rgba32(0, 0, 255));
        WriteRealSidecar(imagePath);
        var currentHash = ImageHashService.GetContentHash(imagePath);
        var promptHashes = DefaultPromptHashes();
        var optFingerprint = new OptimizationConfig().Fingerprint();

        var manifest = new BuildManifest
        {
            Images =
            {
                ["full-needs-reopt.png"] = CreateManifestEntry(
                    currentHash,
                    "old-model",
                    new Dictionary<string, string>(promptHashes),
                    optFingerprint,
                    hasApiOptimized: false,
                    hasBundleOptimized: false)
            },
        };

        var plan = RebuildPlanner.PlanForImage(
            imagePath,
            manifest,
            promptHashes,
            "gpt-5-mini",
            "1.4",
            _outputDir,
            optFingerprint);

        // Catches mutation where Full branches stop propagating `NeedsReoptimization`.
        Assert.True(plan.NeedsReoptimization);
        Assert.Equal(RebuildScope.Full, plan.Scope);
    }

    [Fact]
    public void SidecarMerger_CoreMerge_NullEmojisMustNotOverwriteExisting()
    {
        var existing = ExistingSidecar();
        var partial = new AnalysisResult
        {
            Emojis = null,
            Title = "new title",
            Description = "new description",
        };

        var merged = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupCore]);

        // Catches mutation removing `partial.Emojis ?? emojis` in core merge.
        Assert.Equal(existing.Emojis, merged.Emojis);
    }

    [Fact]
    public void SidecarMerger_CulturalMerge_NullBasedOnMustNotOverwriteExisting()
    {
        var existing = ExistingSidecar();
        var partial = new AnalysisResult { BasedOn = null };

        var merged = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupCultural]);

        // Catches mutation removing `partial.BasedOn ?? basedOn` in cultural merge.
        Assert.Equal(existing.BasedOn, merged.BasedOn);
    }

    [Fact]
    public void SidecarMerger_EmotionsMerge_NullEmotionsMustNotOverwriteExisting()
    {
        var existing = ExistingSidecar();
        var partial = new AnalysisResult { Emotions = null };

        var merged = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupEmotions]);

        // Catches mutation removing `partial.Emotions ?? emotions` in emotions merge.
        Assert.NotNull(merged.Emotions);
        Assert.Equal(existing.Emotions!.Primary, merged.Emotions!.Primary);
    }

    [Fact]
    public void SidecarMerger_SearchMerge_NullTagsMustNotOverwriteExisting()
    {
        var existing = ExistingSidecar();
        var partial = new AnalysisResult
        {
            Tags = null,
            SearchPhrases = ["replacement phrase"],
        };

        var merged = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupSearch]);

        // Catches mutation removing `partial.Tags ?? tags` in search merge.
        Assert.Equal(existing.Tags, merged.Tags);
    }

    [Fact]
    public void SidecarMerger_CoreMerge_NullTitleAndDescriptionMustNotOverwriteExisting()
    {
        var existing = ExistingSidecar();
        var partial = new AnalysisResult
        {
            Emojis = ["🎉"],
            Title = null,
            Description = null,
        };

        var merged = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupCore]);

        // Catches mutation removing `?? title` or `?? description` in core merge.
        Assert.Equal(existing.Title, merged.Title);
        Assert.Equal(existing.Description, merged.Description);
    }

    [Fact]
    public void SidecarMerger_SearchMerge_NullSearchPhrasesMustNotOverwriteExisting()
    {
        var existing = ExistingSidecar();
        var partial = new AnalysisResult
        {
            Tags = ["replacement-tag"],
            SearchPhrases = null,
        };

        var merged = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupSearch]);

        // Catches mutation removing `?? searchPhrases` in search merge.
        Assert.Equal(existing.SearchPhrases, merged.SearchPhrases);
    }

    [Fact]
    public void SidecarMerger_CoreMerge_NonNullPartialMustOverwriteExisting()
    {
        var existing = ExistingSidecar();
        var partial = new AnalysisResult
        {
            Emojis = ["🎉", "🎊"],
            Title = "replacement title",
            Description = "replacement description",
        };

        var merged = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupCore]);

        // Catches mutation changing `partial.X ?? existing.X` to always `existing.X`.
        Assert.Equal(partial.Emojis, merged.Emojis);
        Assert.Equal(partial.Title, merged.Title);
        Assert.Equal(partial.Description, merged.Description);
    }

    [Fact]
    public void PromptHasher_HashMustBeLowercaseForStableCaseSensitiveComparison()
    {
        var hash = PromptHasher.Hash("mutation-case-sensitive-check");

        // Catches mutation changing `ToLowerInvariant()` to uppercase output.
        Assert.Equal(hash.ToLowerInvariant(), hash);
    }

    [Fact]
    public void PromptHasher_CoreHashMustChangeWithPrimaryLanguage()
    {
        var en = PromptHasher.ComputeAll(["en"]);
        var cs = PromptHasher.ComputeAll(["cs"]);

        // Catches mutation removing language from core spec template/hash input.
        Assert.NotEqual(en[PromptHasher.GroupCore], cs[PromptHasher.GroupCore]);
    }

    [Fact]
    public void ManifestService_SaveMustConsumeStaleTempFileFromInterruptedPreviousWrite()
    {
        var manifestPath = Path.Combine(_outputDir, BuildManifest.FileName);
        var tempPath = manifestPath + ".tmp";
        File.WriteAllText(tempPath, "stale temp from crash");

        ManifestService.Save(_outputDir, new BuildManifest
        {
            Model = "gpt-5-mini",
            PromptHashes = DefaultPromptHashes(),
        });

        // Catches mutation replacing atomic temp+move flow with direct non-atomic write.
        Assert.True(File.Exists(manifestPath));
        Assert.False(File.Exists(tempPath));
    }

    [Fact]
    public void ManifestService_RecordPartialBuild_MustOnlyUpdateAffectedGroupHashes()
    {
        var manifest = new BuildManifest();
        ManifestService.RecordImageBuild(
            manifest,
            "partial.png",
            "hash-1",
            "gpt-5-mini",
            "1.4",
            new Dictionary<string, string>
            {
                [PromptHasher.GroupCore] = "core-old",
                [PromptHasher.GroupSearch] = "search-old",
                [PromptHasher.GroupCultural] = "cultural-old",
            });

        ManifestService.RecordPartialBuild(
            manifest,
            "partial.png",
            "hash-1",
            "gpt-5-mini",
            "1.4",
            affectedGroups: [PromptHasher.GroupCore],
            currentPromptHashes: new Dictionary<string, string>
            {
                [PromptHasher.GroupCore] = "core-new",
                [PromptHasher.GroupSearch] = "search-new-should-not-apply",
                [PromptHasher.GroupCultural] = "cultural-new-should-not-apply",
            });

        var entry = manifest.Images["partial.png"];

        // Catches mutation that replaces all hashes in partial build instead of affected-only updates.
        Assert.Equal("core-new", entry.FieldHashes[PromptHasher.GroupCore]);
        Assert.Equal("search-old", entry.FieldHashes[PromptHasher.GroupSearch]);
        Assert.Equal("cultural-old", entry.FieldHashes[PromptHasher.GroupCultural]);
    }

    [Fact]
    public void ImageOptimizer_OptimizeForApi_SmallImageMustReturnOriginalAndCreateNoOptimizedFile()
    {
        var imagePath = CreateSolidImage("small-shortcut.png", 128, 128, new Rgba32(5, 6, 7));

        var result = ImageOptimizer.OptimizeForApi(imagePath, _outputDir, maxDimension: 1200);
        var optimizedDir = OutputPaths.GetOptimizedDir(_outputDir);

        // Catches mutation removing small-image early return in OptimizeForApi.
        Assert.Equal(imagePath, result);
        Assert.False(Directory.Exists(optimizedDir) && Directory.EnumerateFiles(optimizedDir).Any());
    }

    [Fact]
    public void ImageOptimizer_OptimizeForApi_DownscaleMustUseQualityResamplerNotNearestNeighbor()
    {
        var checkerboard = CreateCheckerboardImage("checkerboard.png", 240, 240);

        var optimizedPath = ImageOptimizer.OptimizeForApi(checkerboard, _outputDir, maxDimension: 24);

        using var output = Image.Load<Rgba32>(optimizedPath);
        var hasIntermediateTone = HasIntermediateTone(output);

        // Catches mutation replacing Lanczos3 with NearestNeighbor in ResizeIfNeeded.
        Assert.True(hasIntermediateTone);
    }

    private static Dictionary<string, string> DefaultPromptHashes() => new()
    {
        [PromptHasher.GroupCore] = "core-h",
        [PromptHasher.GroupSearch] = "search-h",
        [PromptHasher.GroupCultural] = "cultural-h",
        [PromptHasher.GroupEmotions] = "emotions-h",
    };

    private static ImageManifestEntry CreateManifestEntry(
        string contentHash,
        string model,
        Dictionary<string, string> fieldHashes,
        string? optimizationFingerprint = null,
        bool hasApiOptimized = true,
        bool hasBundleOptimized = true) =>
        new()
        {
            ContentHash = contentHash,
            Model = model,
            SchemaVersion = "1.4",
            GeneratedAt = "2025-01-01T00:00:00Z",
            FieldHashes = fieldHashes,
            OptimizationFingerprint = optimizationFingerprint,
            HasApiOptimized = hasApiOptimized,
            HasBundleOptimized = hasBundleOptimized,
        };

    private string CreateSolidImage(string fileName, int width, int height, Rgba32 color)
    {
        var path = Path.Combine(_tempDir, fileName);
        using var image = new Image<Rgba32>(width, height);
        for (var y = 0; y < height; y++)
        {
            for (var x = 0; x < width; x++)
                image[x, y] = color;
        }
        image.Save(path);
        return path;
    }

    private string CreateCheckerboardImage(string fileName, int width, int height)
    {
        var path = Path.Combine(_tempDir, fileName);
        using var image = new Image<Rgba32>(width, height);
        for (var y = 0; y < height; y++)
        {
            for (var x = 0; x < width; x++)
            {
                var isWhite = ((x + y) & 1) == 0;
                image[x, y] = isWhite ? new Rgba32(255, 255, 255) : new Rgba32(0, 0, 0);
            }
        }
        image.Save(path);
        return path;
    }

    private string WriteRealSidecar(string imagePath)
    {
        var existing = ExistingSidecar();
        var metadata = new SidecarMetadata
        {
            SchemaVersion = existing.SchemaVersion,
            Emojis = existing.Emojis,
            CreatedAt = existing.CreatedAt,
            Title = existing.Title,
            Description = existing.Description,
            Tags = existing.Tags,
            SearchPhrases = existing.SearchPhrases,
            PrimaryLanguage = existing.PrimaryLanguage,
            Localizations = existing.Localizations,
            ContentHash = ImageHashService.GetContentHash(imagePath),
            BasedOn = existing.BasedOn,
            Emotions = existing.Emotions,
        };
        var sidecarPath = SidecarService.WriteSidecar(imagePath, metadata, _outputDir);
        var parsed = JsonSerializer.Deserialize<SidecarMetadata>(File.ReadAllText(sidecarPath));
        Assert.NotNull(parsed);
        return sidecarPath;
    }

    private static SidecarMetadata ExistingSidecar() => new()
    {
        SchemaVersion = "1.4",
        Emojis = ["🙂", "🔥"],
        CreatedAt = "2025-01-01T00:00:00Z",
        Title = "existing title",
        Description = "existing description",
        Tags = ["existing-tag"],
        SearchPhrases = ["existing phrase"],
        PrimaryLanguage = "en",
        ContentHash = "content-hash",
        BasedOn = "existing based-on",
        Emotions = new EmotionMetadata
        {
            Primary = "humor",
            Sentiment = "positive",
            Intensity = "medium",
            Secondary = ["joy"],
            MemeUsage = ["when it's funny"],
        },
    };

    private static bool HasIntermediateTone(Image<Rgba32> image)
    {
        for (var y = 0; y < image.Height; y++)
        {
            for (var x = 0; x < image.Width; x++)
            {
                var px = image[x, y];
                if (px.R > 0 && px.R < 255)
                    return true;
            }
        }

        return false;
    }
}
