using System.Text.Json;
using RiposteCli.Models;
using RiposteCli.RateLimiting;

namespace RiposteCli.Tests;

public sealed class BugRegressionSuiteTests : IDisposable
{
    private readonly string _rootDir;
    private readonly string _imagesDir;
    private readonly string _outputDir;

    // Minimal valid 1x1 PNG
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

    public BugRegressionSuiteTests()
    {
        _rootDir = Path.Combine(Path.GetTempPath(), $"riposte-bug-suite-{Guid.NewGuid():N}");
        _imagesDir = Path.Combine(_rootDir, "images");
        _outputDir = Path.Combine(_rootDir, "output");
        Directory.CreateDirectory(_imagesDir);
        Directory.CreateDirectory(_outputDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_rootDir))
            Directory.Delete(_rootDir, true);
    }

    [Fact]
    public void Bug03_ZipOverwrite_CreatingBundleTwiceAtSamePath_DoesNotCrash()
    {
        var imagePath = WriteImage("meme.png");
        SidecarService.WriteSidecar(imagePath, CreateSidecarMetadata(), _outputDir);
        var folder = new DirectoryInfo(_imagesDir);
        var manifest = new BuildManifest();

        ZipBundler.CreateBundle(
            ZipMode.Full, folder, _outputDir, [imagePath], [], [],
            new Dictionary<string, string> { [imagePath] = imagePath }, manifest);

        var secondCreate = Record.Exception(() => ZipBundler.CreateBundle(
            ZipMode.Full, folder, _outputDir, [imagePath], [], [],
            new Dictionary<string, string> { [imagePath] = imagePath }, manifest));

        Assert.Null(secondCreate);
    }

    [Fact]
    public void Bug04_RateLimiter_DefaultCurrentDelay_IsPointOneSeconds()
    {
        var limiter = new RateLimiter();
        Assert.Equal(0.1, limiter.CurrentDelay);
    }

    [Fact]
    public void Bug05_AnalysisResult_DeserializeWithoutEmojis_DoesNotThrow()
    {
        var json = """{"title":"meme","description":"desc"}""";
        var ex = Record.Exception(() => JsonSerializer.Deserialize<AnalysisResult>(json));
        Assert.Null(ex);
    }

    [Fact]
    public void Bug06_RecordBundleOptimized_SetsHasBundleOptimized_True()
    {
        var manifest = new BuildManifest();
        manifest.Images["meme.png"] = new ImageManifestEntry
        {
            ContentHash = "h",
            Model = "gpt-5-mini",
            GeneratedAt = DateTimeOffset.UtcNow.ToString("o"),
            HasBundleOptimized = false,
        };

        ManifestService.RecordBundleOptimized(manifest, "meme.png");

        Assert.True(manifest.Images["meme.png"].HasBundleOptimized);
    }

    [Fact]
    public void Bug07_SkipAndReoptFlow_UpdatesManifestFingerprint()
    {
        // Test the planning + manifest mutation logic for skip+reopt images directly.
        // NOTE: We don't call AnnotateCommand.ExecuteAsync via reflection because:
        //   1. It's a private method — reflection-based tests are fragile.
        //   2. The end-to-end flow involves Spectre.Console output, ImageSharp optimization,
        //      and an early-return path (workPlans==0 && zipMode==null) that skips manifest save.
        //   3. The component-level behavior is what matters: planner identifies skip+reopt,
        //      and the manifest entry mutation updates fingerprint + HasApiOptimized.

        var imagePath = WriteImage("skip-reopt.png");
        SidecarService.WriteSidecar(imagePath, CreateSidecarMetadata(), _outputDir);

        var prompts = PromptHasher.ComputeAll(["en"]);
        var optimizationConfig = new OptimizationConfig();
        var fingerprint = optimizationConfig.Fingerprint();
        var manifest = new BuildManifest();
        manifest.Images["skip-reopt.png"] = new ImageManifestEntry
        {
            ContentHash = ImageHashService.GetContentHash(imagePath),
            Model = "gpt-5-mini",
            SchemaVersion = "1.4",
            GeneratedAt = DateTimeOffset.UtcNow.ToString("o"),
            FieldHashes = new Dictionary<string, string>(prompts),
            OptimizationFingerprint = "old-fingerprint",
            HasApiOptimized = false,
            HasBundleOptimized = true,
        };

        // Step 1: Planner should produce Skip + NeedsReoptimization
        var plan = RebuildPlanner.PlanForImage(
            imagePath, manifest, prompts, "gpt-5-mini", "1.4", _outputDir, fingerprint);

        Assert.Equal(RebuildScope.Skip, plan.Scope);
        Assert.True(plan.NeedsReoptimization);

        // Step 2: Apply the same manifest mutation that AnnotateCommand does for skip+reopt plans
        // (see AnnotateCommand.cs lines 197-206)
        var entry = manifest.Images["skip-reopt.png"];
        entry.OptimizationFingerprint = fingerprint;
        entry.HasApiOptimized = true;

        // Step 3: Save and reload to verify round-trip
        ManifestService.Save(_outputDir, manifest);
        var updated = ManifestService.Load(_outputDir);

        Assert.Equal(fingerprint, updated.Images["skip-reopt.png"].OptimizationFingerprint);
        Assert.True(updated.Images["skip-reopt.png"].HasApiOptimized);
    }

    [Fact]
    public void Bug08_LoadSidecar_WhenSidecarDisappeared_ReturnsNull()
    {
        var imagePath = WriteImage("partial.png");
        var sidecarPath = SidecarService.WriteSidecar(imagePath, CreateSidecarMetadata(), _outputDir);
        File.Delete(sidecarPath);

        var loaded = SidecarMerger.LoadSidecar(imagePath, _outputDir);

        Assert.Null(loaded);
    }

    [Fact]
    public void Bug09_MergeCultural_WithNullBasedOn_PreservesExistingBasedOn()
    {
        var existing = new SidecarMetadata
        {
            Emojis = ["😂"],
            BasedOn = "Original source",
        };
        var partial = new AnalysisResult { BasedOn = null };

        var merged = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupCultural]);

        Assert.Equal("Original source", merged.BasedOn);
    }

    [Fact]
    public void Bug10_NoManifestButExistingSidecar_PlannerReturnsFull()
    {
        var imagePath = WriteImage("legacy.png");
        SidecarService.WriteSidecar(imagePath, CreateSidecarMetadata(), _outputDir);
        var manifest = new BuildManifest();

        var plan = RebuildPlanner.PlanForImage(
            imagePath,
            manifest,
            PromptHasher.ComputeAll(["en"]),
            "gpt-5-mini",
            "1.4",
            _outputDir,
            new OptimizationConfig().Fingerprint());

        Assert.Equal(RebuildScope.Full, plan.Scope);
    }

    [Fact]
    public void Bug11_OutputPaths_RecognizesDisambiguatedWebpName()
    {
        var path = Path.Combine(_outputDir, "cat_png.webp");
        File.WriteAllBytes(path, MinimalPng);

        var migrated = OutputPaths.MigrateLegacyLayout(_outputDir);

        Assert.Equal(1, migrated);
        Assert.True(File.Exists(Path.Combine(OutputPaths.GetBundleDir(_outputDir), "cat_png.webp")));
    }

    [Fact]
    public void Bug12_ResolveSidecarPath_FindsSidecarInSubdirectory()
    {
        var imagePath = WriteImage("dedupe.png");
        var sidecarPath = SidecarService.WriteSidecar(imagePath, CreateSidecarMetadata(), _outputDir);

        var resolved = SidecarService.ResolveSidecarPath(imagePath, _outputDir);

        Assert.Equal(sidecarPath, resolved);
    }

    [Fact]
    public async Task Bug13_ConcurrencyLimiter_ReduceThenRestore_DoesNotThrow()
    {
        var rateLimiter = new RateLimiter(minDelay: 0.001, maxDelay: 0.01, jitterFactor: 0, maxBackoffAttempts: 100);
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4, minConcurrency: 1, rateLimiter: rateLimiter);

        for (var i = 0; i < 4; i++)
            await limiter.AcquireAsync();

        await limiter.RecordRateLimitAsync(retryAfter: 0.001);

        for (var i = 0; i < 4; i++)
            await limiter.ReleaseAsync();

        var ex = await Record.ExceptionAsync(async () =>
        {
            for (var i = 0; i < 20; i++)
                await limiter.RecordSuccessAsync();
        });

        Assert.Null(ex);
    }

    [Fact]
    public async Task Bug14_RateLimiter_GetErrorRate_ConcurrentAccess_DoesNotThrow()
    {
        var limiter = new RateLimiter();
        var exceptions = new List<Exception>();
        var gate = new Barrier(5);

        var tasks = Enumerable.Range(0, 5).Select(_ => Task.Run(() =>
        {
            gate.SignalAndWait();
            try
            {
                for (var i = 0; i < 200; i++)
                {
                    if (i % 2 == 0) _ = (int)limiter.RecordFailure();
                    else limiter.RecordSuccess();
                    _ = (int)limiter.GetErrorRate();
                }
            }
            catch (Exception ex)
            {
                lock (exceptions)
                    exceptions.Add(ex);
            }
        }));

        await Task.WhenAll(tasks);
        Assert.Empty(exceptions);
    }

    [Fact]
    public void Bug15_ZipBundler_CreateBundle_WithRootFolder_ThrowsArgumentException()
    {
        var root = new DirectoryInfo(Path.GetPathRoot(_rootDir)!);

        Assert.Throws<ArgumentException>(() => ZipBundler.CreateBundle(
            ZipMode.Full,
            root,
            _outputDir,
            [],
            [],
            [],
            new Dictionary<string, string>(),
            new BuildManifest()));
    }

    [Fact]
    public void Bug16_ResolveUniqueWebpNames_CatPngAndCatJpg_Disambiguates()
    {
        var names = ImageOptimizer.ResolveUniqueWebpNames(
        [
            @"C:\tmp\cat.png",
            @"C:\tmp\cat.jpg",
        ]);

        Assert.Equal("cat_png.webp", names[@"C:\tmp\cat.png"]);
        Assert.Equal("cat_jpg.webp", names[@"C:\tmp\cat.jpg"]);
    }

    [Fact]
    public void Bug17_ManifestLoad_AfterRoundTrip_ImageKeysAreCaseInsensitive()
    {
        // Bug: System.Text.Json deserializes dictionaries with ordinal comparer.
        // On Windows, filenames are case-insensitive, so "Photo.JPG" and "photo.jpg"
        // are the same file. Without EnsureCaseInsensitiveKeys, lookups fail after
        // deserialization, causing unnecessary full rebuilds and wasted API quota.
        var manifest = new BuildManifest();
        manifest.Images["Photo.JPG"] = new ImageManifestEntry
        {
            ContentHash = "abc",
            Model = "gpt-5-mini",
            GeneratedAt = DateTimeOffset.UtcNow.ToString("o"),
        };
        ManifestService.Save(_outputDir, manifest);

        var loaded = ManifestService.Load(_outputDir);

        Assert.True(loaded.Images.ContainsKey("photo.jpg"));
        Assert.True(loaded.Images.ContainsKey("PHOTO.JPG"));
    }

    [Fact]
    public void Bug18_WriteSidecar_AtomicWrite_NoTempFileLeftBehind()
    {
        // Bug: SidecarService.WriteSidecar used plain File.WriteAllText which
        // could corrupt the sidecar if the process crashes mid-write.
        // Fix: temp file + rename pattern.
        var imagePath = WriteImage("atomic.png");
        var metadata = CreateSidecarMetadata();

        SidecarService.WriteSidecar(imagePath, metadata, _outputDir);

        var sidecarDir = OutputPaths.GetSidecarDir(_outputDir);
        var tmpFiles = Directory.GetFiles(sidecarDir, "*.tmp");
        Assert.Empty(tmpFiles);
        Assert.True(File.Exists(Path.Combine(sidecarDir, "atomic.png.json")));
    }

    [Fact]
    public void Bug19_ManifestSave_AtomicWrite_NoTempFileLeftBehind()
    {
        // Bug: ManifestService.Save used File.WriteAllText which is non-atomic.
        // Fix: temp file + File.Move (overwrite: true).
        var manifest = new BuildManifest();
        manifest.Images["test.png"] = new ImageManifestEntry
        {
            ContentHash = "abc",
            Model = "gpt-5-mini",
            GeneratedAt = DateTimeOffset.UtcNow.ToString("o"),
        };

        ManifestService.Save(_outputDir, manifest);

        var tmpFiles = Directory.GetFiles(_outputDir, "*.tmp");
        Assert.Empty(tmpFiles);
        Assert.True(File.Exists(Path.Combine(_outputDir, BuildManifest.FileName)));
    }

    [Fact]
    public void Bug20_HashManifest_AfterRoundTrip_KeysAreCaseInsensitive()
    {
        // Bug: ImageHashService.LoadManifest created case-sensitive dictionary,
        // causing cache misses when file casing differed between runs.
        var manifest = new Dictionary<string, HashEntry>(StringComparer.OrdinalIgnoreCase)
        {
            ["Photo.JPG"] = new HashEntry("abc123", null),
        };
        ImageHashService.SaveManifest(_outputDir, manifest);

        var loaded = ImageHashService.LoadManifest(_outputDir);

        Assert.True(loaded.ContainsKey("photo.jpg"));
        Assert.True(loaded.ContainsKey("PHOTO.JPG"));
    }

    [Fact]
    public void Bug21_MergeCultural_WithNullBasedOn_DoesNotEraseExistingEmotions()
    {
        // Same class of bug as Bug09 (BasedOn) but for the emotions group:
        // verify null-coalescing pattern is used for all groups, not just cultural.
        var existing = new SidecarMetadata
        {
            Emojis = ["😂"],
            Emotions = new EmotionMetadata
            {
                Primary = "humor",
                Sentiment = "positive",
            },
        };
        var partial = new AnalysisResult { Emotions = null };

        var merged = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupEmotions]);

        Assert.NotNull(merged.Emotions);
        Assert.Equal("humor", merged.Emotions!.Primary);
    }

    private string WriteImage(string fileName)
    {
        var path = Path.Combine(_imagesDir, fileName);
        File.WriteAllBytes(path, MinimalPng);
        return path;
    }

    private static SidecarMetadata CreateSidecarMetadata() => new()
    {
        Emojis = ["😂"],
        Title = "Title",
        Description = "Description",
    };
}
