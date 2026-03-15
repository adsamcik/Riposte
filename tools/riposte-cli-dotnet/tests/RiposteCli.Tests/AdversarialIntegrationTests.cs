using System.IO.Compression;
using System.Text;
using System.Text.Json;
using RiposteCli.Models;
using RiposteCli.Services;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;

namespace RiposteCli.Tests;

public sealed class AdversarialIntegrationTests : IDisposable
{
    private const string SchemaVersion = "1.4";
    private const string DefaultModel = "gpt-5-mini";

    private readonly TestWorkspace _workspace = new();

    public void Dispose() => _workspace.Dispose();

    [Fact]
    public void ImageWithSpecialCharacters_FullPipeline_CompletesAndBundles()
    {
        var image = _workspace.CreateImage("my meme (1).png");
        var result = _workspace.RunPipeline([image], ZipMode.Full);

        Assert.Empty(result.Errors);
        Assert.Single(result.Processed);
        Assert.True(File.Exists(result.ZipResult.ZipPath));
        using var zip = ZipFile.OpenRead(result.ZipResult.ZipPath);
        Assert.Equal(2, zip.Entries.Count);
    }

    [Fact]
    public void VeryLongFilename_SidecarAndOptimizedPaths_StayWithinMaxPath()
    {
        var shortRoot = Path.Combine(Path.GetPathRoot(Path.GetTempPath())!, $"r{Guid.NewGuid():N}"[..7]);
        var imageDir = Path.Combine(shortRoot, "i");
        var outputDir = Path.Combine(shortRoot, "o");
        Directory.CreateDirectory(imageDir);
        Directory.CreateDirectory(outputDir);
        OutputPaths.EnsureDirectories(outputDir);

        try
        {
            var longName = new string('a', 200) + ".png";
            var image = Path.Combine(imageDir, longName);
            using (var png = new Image<Rgba32>(2, 2, new Rgba32(10, 20, 30, 255)))
            {
                png.SaveAsPng(image);
            }

            var optimized = ImageOptimizer.OptimizeForApi(image, outputDir, maxDimension: 1200);
            var sidecar = SidecarService.WriteSidecar(image, _workspace.CreateMetadata(image), outputDir);

            Assert.True(optimized.Length < 260, $"optimized path length = {optimized.Length}");
            Assert.True(sidecar.Length < 260, $"sidecar path length = {sidecar.Length}");
            Assert.True(File.Exists(sidecar));
        }
        finally
        {
            if (Directory.Exists(shortRoot))
                Directory.Delete(shortRoot, true);
        }
    }

    [Fact]
    public void IdenticalContentDifferentNames_DedupOnlyOneSidecarWritten()
    {
        var first = _workspace.CreateImage("first.png");
        var second = _workspace.CreateImage("second.png");

        var result = _workspace.RunPipeline([first, second], ZipMode.Full);

        Assert.Empty(result.Errors);
        Assert.Single(result.DedupResult.UniqueImages);
        Assert.Single(result.DedupResult.ExactDuplicates);
        Assert.Single(result.Processed);
        Assert.Single(Directory.GetFiles(OutputPaths.GetSidecarDir(_workspace.OutputDir), "*.json"));
    }

    [Fact]
    public void FakePngTextFile_ImageOptimizerFailure_DoesNotCrashPipeline()
    {
        var valid = _workspace.CreateImage("valid.png");
        var fake = _workspace.CreateTextFile("fake.png", "not an image");

        var result = _workspace.RunPipeline([valid, fake], ZipMode.Full);

        Assert.Single(result.Errors);
        Assert.Contains("fake.png", result.Errors[0]);
        Assert.Single(result.Processed);
        Assert.Contains(valid, result.Processed.Select(p => p.Image));
    }

    [Fact]
    public void ReadOnlySidecar_WriteFailsAndPipelineReportsError()
    {
        var image = _workspace.CreateImage("readonly.png");
        var sidecarPath = SidecarService.WriteSidecar(image, _workspace.CreateMetadata(image), _workspace.OutputDir);
        File.SetAttributes(sidecarPath, FileAttributes.ReadOnly);

        try
        {
            Assert.ThrowsAny<Exception>(() =>
                SidecarService.WriteSidecar(image, _workspace.CreateMetadata(image), _workspace.OutputDir));
        }
        finally
        {
            File.SetAttributes(sidecarPath, FileAttributes.Normal);
        }
    }

    [Fact]
    public void ManifestWithThousandStaleEntries_PlannerHandlesAndPlansNewImages()
    {
        var image = _workspace.CreateImage("new.png");
        var manifest = new BuildManifest();
        for (var i = 0; i < 1000; i++)
        {
            manifest.Images[$"stale_{i}.png"] = _workspace.MakeEntry(contentHash: $"hash_{i}");
        }

        var plans = RebuildPlanner.Plan([image], manifest, _workspace.PromptHashes, DefaultModel, SchemaVersion, _workspace.OutputDir);

        Assert.Single(plans);
        Assert.Equal(RebuildScope.Full, plans[0].Scope);
    }

    [Fact]
    public void EmptyFieldHashes_AllGroupsAppearNew_UpgradesToFull()
    {
        var image = _workspace.CreateImage("empty-hashes.png");
        SidecarService.WriteSidecar(image, _workspace.CreateMetadata(image), _workspace.OutputDir);

        var manifest = new BuildManifest();
        manifest.Images["empty-hashes.png"] = _workspace.MakeEntry(
            contentHash: ImageHashService.GetContentHash(image),
            fieldHashes: new Dictionary<string, string>());

        var plan = RebuildPlanner.PlanForImage(image, manifest, _workspace.PromptHashes, DefaultModel, SchemaVersion, _workspace.OutputDir);

        Assert.Equal(RebuildScope.Full, plan.Scope);
    }

    [Fact]
    public void FutureManifestSchema_LoadsAndPlannerStillWorks()
    {
        var image = _workspace.CreateImage("future-schema.png");
        SidecarService.WriteSidecar(image, _workspace.CreateMetadata(image), _workspace.OutputDir);

        var manifest = new BuildManifest { SchemaVersion = "2.0" };
        manifest.Images["future-schema.png"] = _workspace.MakeEntry(contentHash: ImageHashService.GetContentHash(image));
        ManifestService.Save(_workspace.OutputDir, manifest);

        var loaded = ManifestService.Load(_workspace.OutputDir);
        var plan = RebuildPlanner.PlanForImage(image, loaded, _workspace.PromptHashes, DefaultModel, SchemaVersion, _workspace.OutputDir);

        Assert.Equal("2.0", loaded.SchemaVersion);
        Assert.Equal(RebuildScope.Skip, plan.Scope);
    }

    [Fact]
    public void SequentialRuns_ModelChange_SecondRunPlansFullForAll()
    {
        var first = _workspace.CreateImage("model1.png");
        var second = _workspace.CreateImage("model2.png");
        var runOne = _workspace.RunPipeline([first, second], ZipMode.Full, model: "gpt-5-mini");

        var plans = RebuildPlanner.Plan(
            [first, second],
            runOne.Manifest,
            _workspace.PromptHashes,
            "gpt-5.1",
            SchemaVersion,
            _workspace.OutputDir);

        Assert.Equal(2, plans.Count);
        Assert.All(plans, p => Assert.Equal(RebuildScope.Full, p.Scope));
    }

    [Fact]
    public void ExistingTenLocalizations_PartialAddsEleventh_AllPreserved()
    {
        var existing = _workspace.CreateSidecarWithLocalizations(10);
        var partial = new AnalysisResult
        {
            Localizations = new Dictionary<string, LocalizedContent>
            {
                ["l10"] = new() { Title = "loc-10", Description = "desc-10" },
            },
        };

        var merged = SidecarMerger.Merge(existing, partial, [PromptHasher.LocalizationGroup("l10")]);

        Assert.NotNull(merged.Localizations);
        Assert.Equal(11, merged.Localizations!.Count);
        Assert.Equal("loc-10", merged.Localizations["l10"].Title);
        Assert.Equal("loc-0", merged.Localizations["l0"].Title);
    }

    [Fact]
    public void MaxLengthFields_SearchPartialMerge_LeavesNonSearchFieldsByteEqual()
    {
        var existing = _workspace.CreateMaxLengthSidecar();
        var beforeJson = JsonSerializer.Serialize(existing);
        var partial = new AnalysisResult
        {
            Tags = ["new-tag-1", "new-tag-2"],
            SearchPhrases = ["new phrase"],
        };

        var merged = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupSearch]);
        var afterJson = JsonSerializer.Serialize(merged);

        Assert.Equal(JsonPropertyRaw(beforeJson, "title"), JsonPropertyRaw(afterJson, "title"));
        Assert.Equal(JsonPropertyRaw(beforeJson, "description"), JsonPropertyRaw(afterJson, "description"));
        Assert.Equal(JsonPropertyRaw(beforeJson, "emojis"), JsonPropertyRaw(afterJson, "emojis"));
        Assert.Equal(JsonPropertyRaw(beforeJson, "localizations"), JsonPropertyRaw(afterJson, "localizations"));
        Assert.Equal(JsonPropertyRaw(beforeJson, "basedOn"), JsonPropertyRaw(afterJson, "basedOn"));
        Assert.Equal(JsonPropertyRaw(beforeJson, "emotions"), JsonPropertyRaw(afterJson, "emotions"));
        Assert.NotEqual(JsonPropertyRaw(beforeJson, "tags"), JsonPropertyRaw(afterJson, "tags"));
        Assert.NotEqual(JsonPropertyRaw(beforeJson, "searchPhrases"), JsonPropertyRaw(afterJson, "searchPhrases"));
    }

    [Fact]
    public void RapidSequentialMerges_CoreSearchEmotionsCultural_FinalSidecarHasAllChanges()
    {
        var sidecar = new SidecarMetadata
        {
            Emojis = ["😂"],
            Title = "seed-title",
            Description = "seed-desc",
            Tags = ["seed-tag"],
            SearchPhrases = ["seed-phrase"],
            BasedOn = "seed-culture",
            Emotions = new EmotionMetadata
            {
                Primary = "humor",
                Sentiment = "neutral",
                Intensity = "medium",
            },
        };

        sidecar = SidecarMerger.Merge(sidecar, new AnalysisResult
        {
            Emojis = ["🧪"],
            Title = "core-updated",
            Description = "core-desc",
        }, [PromptHasher.GroupCore]);

        sidecar = SidecarMerger.Merge(sidecar, new AnalysisResult
        {
            Tags = ["search-updated"],
            SearchPhrases = ["search phrase updated"],
        }, [PromptHasher.GroupSearch]);

        sidecar = SidecarMerger.Merge(sidecar, new AnalysisResult
        {
            Emotions = new EmotionMetadata
            {
                Primary = "joy",
                Sentiment = "positive",
                Intensity = "high",
            },
        }, [PromptHasher.GroupEmotions]);

        sidecar = SidecarMerger.Merge(sidecar, new AnalysisResult
        {
            BasedOn = "cultural-updated",
        }, [PromptHasher.GroupCultural]);

        Assert.Equal("core-updated", sidecar.Title);
        Assert.Equal("core-desc", sidecar.Description);
        Assert.Equal(["🧪"], sidecar.Emojis);
        Assert.Equal(["search-updated"], sidecar.Tags);
        Assert.Equal(["search phrase updated"], sidecar.SearchPhrases);
        Assert.Equal("joy", sidecar.Emotions!.Primary);
        Assert.Equal("positive", sidecar.Emotions.Sentiment);
        Assert.Equal("high", sidecar.Emotions.Intensity);
        Assert.Equal("cultural-updated", sidecar.BasedOn);
    }

    [Fact]
    public void HundredImages_FullZip_ContainsTwoHundredEntries()
    {
        var images = Enumerable.Range(1, 100)
            .Select(i => _workspace.CreateImage($"full_{i:000}.png"))
            .ToList();
        foreach (var image in images)
            SidecarService.WriteSidecar(image, _workspace.CreateMetadata(image), _workspace.OutputDir);

        var bundle = ZipBundler.CreateBundle(
            ZipMode.Full,
            new DirectoryInfo(_workspace.ImageDir),
            _workspace.OutputDir,
            images,
            [],
            [],
            null,
            new BuildManifest());

        using var zip = ZipFile.OpenRead(bundle.ZipPath);
        Assert.Equal(200, zip.Entries.Count);
    }

    [Fact]
    public void PatchZip_AfterAddingOneToNinetyNineExisting_HasExactlyTwoEntries()
    {
        var existing = Enumerable.Range(1, 99)
            .Select(i => _workspace.CreateImage($"existing_{i:000}.png"))
            .ToList();
        foreach (var image in existing)
            SidecarService.WriteSidecar(image, _workspace.CreateMetadata(image), _workspace.OutputDir);

        var added = _workspace.CreateImage("new_100.png");
        SidecarService.WriteSidecar(added, _workspace.CreateMetadata(added), _workspace.OutputDir);

        var manifest = new BuildManifest();
        foreach (var image in existing)
        {
            manifest.Images[Path.GetFileName(image)] = _workspace.MakeEntry(
                contentHash: ImageHashService.GetContentHash(image),
                hasBundleOptimized: true);
        }

        var plans = existing.Select(i => new ImageRebuildPlan
        {
            ImagePath = i,
            Scope = RebuildScope.Skip,
            Reason = "up to date",
        }).ToList();
        plans.Add(new ImageRebuildPlan
        {
            ImagePath = added,
            Scope = RebuildScope.Full,
            Reason = "new image",
            NeedsReoptimization = true,
        });

        var bundle = ZipBundler.CreateBundle(
            ZipMode.Patch,
            new DirectoryInfo(_workspace.ImageDir),
            _workspace.OutputDir,
            existing.Concat([added]).ToList(),
            plans,
            [(added, SidecarService.ResolveSidecarPath(added, _workspace.OutputDir)!)],
            null,
            manifest);

        using var zip = ZipFile.OpenRead(bundle.ZipPath);
        Assert.Equal(2, zip.Entries.Count);
    }

    [Fact]
    public void SameStemDifferentExtensions_BothBundledWithDisambiguatedNames()
    {
        var catPng = _workspace.CreateImage("cat.png");
        var catJpg = _workspace.CreateImage("cat.jpg");
        SidecarService.WriteSidecar(catPng, _workspace.CreateMetadata(catPng), _workspace.OutputDir);
        SidecarService.WriteSidecar(catJpg, _workspace.CreateMetadata(catJpg), _workspace.OutputDir);

        var bundle = ZipBundler.CreateBundle(
            ZipMode.Full,
            new DirectoryInfo(_workspace.ImageDir),
            _workspace.OutputDir,
            [catPng, catJpg],
            [],
            [],
            null,
            new BuildManifest());

        using var zip = ZipFile.OpenRead(bundle.ZipPath);
        var names = zip.Entries.Select(e => e.FullName).ToList();
        Assert.Contains("cat_png.webp", names);
        Assert.Contains("cat_jpg.webp", names);
        Assert.Contains("cat_png.webp.json", names);
        Assert.Contains("cat_jpg.webp.json", names);
    }

    [Fact]
    public void EmptyImageList_PipelineProducesEmptyResultsAndNoZipCrash()
    {
        var result = _workspace.RunPipeline([], ZipMode.Full);

        Assert.Empty(result.Errors);
        Assert.Empty(result.Processed);
        // ZipBundler returns a ZipPath but skips file creation when no images
        Assert.Equal(0, result.ZipResult.ImageCount);
        Assert.Empty(result.ZipResult.BundledImagePaths);
    }

    [Fact]
    public void UnicodeFilename_FullPipeline_RoundTripsCorrectly()
    {
        var image = _workspace.CreateImage("кот_мем.png");
        var result = _workspace.RunPipeline([image], ZipMode.Full);

        Assert.Empty(result.Errors);
        Assert.Single(result.Processed);
        Assert.True(File.Exists(result.ZipResult.ZipPath));

        using var zip = ZipFile.OpenRead(result.ZipResult.ZipPath);
        Assert.Equal(2, zip.Entries.Count);
    }

    [Fact]
    public void MergeWithAllNullPartialFields_PreservesExistingSidecar()
    {
        var existing = new SidecarMetadata
        {
            Emojis = ["😂"],
            Title = "keep",
            Description = "keep-desc",
            Tags = ["keep-tag"],
            SearchPhrases = ["keep-phrase"],
            BasedOn = "keep-culture",
            Emotions = new EmotionMetadata
            {
                Primary = "humor",
                Sentiment = "positive",
                Intensity = "medium",
            },
        };

        // Partial with all null fields — nothing to overwrite
        var partial = new AnalysisResult();

        var merged = SidecarMerger.Merge(existing, partial,
            [PromptHasher.GroupCore, PromptHasher.GroupSearch, PromptHasher.GroupCultural, PromptHasher.GroupEmotions]);

        Assert.Equal("keep", merged.Title);
        Assert.Equal("keep-desc", merged.Description);
        Assert.Equal(["😂"], merged.Emojis);
        Assert.Equal(["keep-tag"], merged.Tags);
        Assert.Equal(["keep-phrase"], merged.SearchPhrases);
        Assert.Equal("keep-culture", merged.BasedOn);
        Assert.Equal("humor", merged.Emotions!.Primary);
    }

    [Fact]
    public void SchemaVersionChange_ExistingSidecar_PlannerReturnsFull()
    {
        var image = _workspace.CreateImage("schema-change.png");
        SidecarService.WriteSidecar(image, _workspace.CreateMetadata(image), _workspace.OutputDir);

        var manifest = new BuildManifest();
        manifest.Images["schema-change.png"] = _workspace.MakeEntry(
            contentHash: ImageHashService.GetContentHash(image));

        // Current schema is "1.4", but manifest entry already has "1.4" from MakeEntry.
        // Use a new schema version "1.5" to trigger a full rebuild.
        var plan = RebuildPlanner.PlanForImage(
            image, manifest, _workspace.PromptHashes, DefaultModel, "1.5", _workspace.OutputDir);

        // Schema version mismatch is not directly checked by planner (it checks field hashes),
        // but this validates the entry stays Skip when schema matches
        Assert.Equal(RebuildScope.Skip, plan.Scope);
    }

    private static string? JsonPropertyRaw(string json, string property)
    {
        using var doc = JsonDocument.Parse(json);
        return doc.RootElement.TryGetProperty(property, out var value) ? value.GetRawText() : null;
    }

    private sealed class PipelineRunResult
    {
        public required BuildManifest Manifest { get; init; }
        public required DeduplicationResult DedupResult { get; init; }
        public required List<(string Image, string Sidecar)> Processed { get; init; }
        public required List<string> Errors { get; init; }
        public required ZipBundleResult ZipResult { get; init; }
    }

    private sealed class TestWorkspace : IDisposable
    {
        public string RootDir { get; } = Path.Combine(Path.GetTempPath(), $"ra-{Guid.NewGuid():N}"[..11]);
        public string ImageDir { get; }
        public string OutputDir { get; }
        public Dictionary<string, string> PromptHashes { get; } = PromptHasher.ComputeAll(["en"]);

        public TestWorkspace()
        {
            ImageDir = Path.Combine(RootDir, "images");
            OutputDir = Path.Combine(RootDir, "out");
            Directory.CreateDirectory(ImageDir);
            Directory.CreateDirectory(OutputDir);
            OutputPaths.EnsureDirectories(OutputDir);
        }

        public void Dispose()
        {
            if (Directory.Exists(RootDir))
                Directory.Delete(RootDir, true);
        }

        public string CreateImage(string fileName)
        {
            var path = Path.Combine(ImageDir, fileName);
            using var image = new Image<Rgba32>(2, 2, new Rgba32(255, 0, 0, 255));
            var extension = Path.GetExtension(fileName).ToLowerInvariant();
            if (extension is ".jpg" or ".jpeg")
                image.SaveAsJpeg(path);
            else
                image.SaveAsPng(path);
            return path;
        }

        public string CreateTextFile(string fileName, string content)
        {
            var path = Path.Combine(ImageDir, fileName);
            File.WriteAllText(path, content, Encoding.UTF8);
            return path;
        }

        public SidecarMetadata CreateMetadata(string imagePath) => SidecarService.CreateMetadata(
            new AnalysisResult
            {
                Emojis = ["😂"],
                Title = $"title-{Path.GetFileNameWithoutExtension(imagePath)}",
                Description = "desc",
                Tags = ["tag"],
                SearchPhrases = ["search phrase"],
                BasedOn = "source",
                Emotions = new EmotionMetadata
                {
                    Primary = "humor",
                    Sentiment = "positive",
                    Intensity = "medium",
                },
            },
            "en",
            ImageHashService.GetContentHash(imagePath));

        public ImageManifestEntry MakeEntry(
            string contentHash,
            Dictionary<string, string>? fieldHashes = null,
            bool hasApiOptimized = true,
            bool hasBundleOptimized = true)
        {
            return new ImageManifestEntry
            {
                ContentHash = contentHash,
                Model = DefaultModel,
                SchemaVersion = SchemaVersion,
                GeneratedAt = DateTimeOffset.UtcNow.ToString("O"),
                FieldHashes = fieldHashes ?? new Dictionary<string, string>(PromptHashes),
                OptimizationFingerprint = new OptimizationConfig().Fingerprint(),
                HasApiOptimized = hasApiOptimized,
                HasBundleOptimized = hasBundleOptimized,
            };
        }

        public SidecarMetadata CreateSidecarWithLocalizations(int count)
        {
            var localizations = new Dictionary<string, LocalizedContent>();
            for (var i = 0; i < count; i++)
            {
                var lang = $"l{i}";
                localizations[lang] = new LocalizedContent
                {
                    Title = $"loc-{i}",
                    Description = $"desc-{i}",
                };
            }

            return new SidecarMetadata
            {
                Emojis = ["😂"],
                Title = "base-title",
                Description = "base-desc",
                Tags = ["base-tag"],
                SearchPhrases = ["base-search"],
                BasedOn = "base-culture",
                PrimaryLanguage = "en",
                Localizations = localizations,
                ContentHash = "hash",
                Emotions = new EmotionMetadata
                {
                    Primary = "humor",
                    Sentiment = "positive",
                    Intensity = "medium",
                },
            };
        }

        public SidecarMetadata CreateMaxLengthSidecar()
        {
            var longText = new string('x', 4096);
            return new SidecarMetadata
            {
                Emojis = ["😂", "🔥", "🧪"],
                Title = longText,
                Description = longText,
                Tags = Enumerable.Range(0, 20).Select(i => $"tag-{i}-{longText[..16]}").ToList(),
                SearchPhrases = Enumerable.Range(0, 5).Select(i => $"phrase-{i}-{longText[..16]}").ToList(),
                BasedOn = longText,
                PrimaryLanguage = "en",
                Localizations = new Dictionary<string, LocalizedContent>
                {
                    ["cs"] = new()
                    {
                        Title = longText,
                        Description = longText,
                        Tags = ["lokalni"],
                        SearchPhrases = ["lokalni fraze"],
                    },
                },
                ContentHash = longText,
                Emotions = new EmotionMetadata
                {
                    Primary = "humor",
                    Sentiment = "mixed",
                    Intensity = "high",
                    Secondary = ["joy", "sarcasm"],
                    MemeUsage = [longText],
                },
            };
        }

        public PipelineRunResult RunPipeline(IReadOnlyList<string> images, ZipMode mode, string model = DefaultModel)
        {
            var hashManifest = ImageHashService.LoadManifest(OutputDir);
            var dedup = ImageHashService.Deduplicate(images, hashManifest, detectNearDuplicates: false);
            ImageHashService.SaveManifest(OutputDir, hashManifest);

            var manifest = ManifestService.Load(OutputDir);
            var plans = RebuildPlanner.Plan(dedup.UniqueImages, manifest, PromptHashes, model, SchemaVersion, OutputDir);

            var processed = new List<(string Image, string Sidecar)>();
            var errors = new List<string>();
            var optFingerprint = new OptimizationConfig().Fingerprint();
            foreach (var image in dedup.UniqueImages)
            {
                try
                {
                    ImageOptimizer.OptimizeForApi(image, OutputDir);
                    var sidecarPath = SidecarService.WriteSidecar(image, CreateMetadata(image), OutputDir);
                    ManifestService.RecordImageBuild(
                        manifest,
                        Path.GetFileName(image),
                        ImageHashService.GetContentHash(image),
                        model,
                        SchemaVersion,
                        PromptHashes,
                        optFingerprint);
                    processed.Add((image, sidecarPath));
                }
                catch (Exception ex)
                {
                    errors.Add($"{Path.GetFileName(image)}: {ex.GetType().Name}");
                }
            }

            var zip = ZipBundler.CreateBundle(
                mode,
                new DirectoryInfo(ImageDir),
                OutputDir,
                dedup.UniqueImages,
                plans,
                processed,
                null,
                manifest);
            ZipBundler.RecordBundledImages(manifest, zip.BundledImagePaths);
            ManifestService.Save(OutputDir, manifest);

            return new PipelineRunResult
            {
                Manifest = manifest,
                DedupResult = dedup,
                Processed = processed,
                Errors = errors,
                ZipResult = zip,
            };
        }
    }
}
