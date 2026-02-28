using System.IO.Compression;
using RiposteCli.Models;
using RiposteCli.Services;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;

namespace RiposteCli.Tests;

/// <summary>
/// Tests targeting specific uncovered lines and branch gaps identified by coverage analysis.
/// </summary>
public class CoverageGapTests : IDisposable
{
    private readonly string _tempDir;

    public CoverageGapTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), $"riposte-coverage-gaps-{Guid.NewGuid()}");
        Directory.CreateDirectory(_tempDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempDir))
            Directory.Delete(_tempDir, true);
    }

    // ─── Prompts line 141: unknown field group falls through to _ => null ───

    [Fact]
    public void GetPartialPrompt_UnknownFieldGroup_IsIgnored()
    {
        // "unknown_group" hits the _ => null branch in the switch (line 141)
        var result = Prompts.GetPartialPrompt(["unknown_group"], ["en"]);

        // Should still contain the boilerplate but NOT any field spec
        Assert.Contains("valid JSON", result);
        Assert.DoesNotContain("\"emojis\"", result);
        Assert.DoesNotContain("\"tags\"", result);
        Assert.DoesNotContain("\"basedOn\"", result);
        Assert.DoesNotContain("\"emotions\"", result);
    }

    [Fact]
    public void GetPartialPrompt_MixOfKnownAndUnknownGroups_OnlyKnownIncluded()
    {
        var result = Prompts.GetPartialPrompt(
            [PromptHasher.GroupCore, "bogus_group", PromptHasher.GroupCultural],
            ["en"]);

        Assert.Contains("\"emojis\"", result);
        Assert.Contains("\"basedOn\"", result);
        // Unknown group is silently skipped
        Assert.DoesNotContain("bogus", result);
    }

    // ─── CopilotService line 222: ParsePartialResponse null result ───

    [Fact]
    public void ParsePartialResponse_NullJsonLiteral_ThrowsWithNullMessage()
    {
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParsePartialResponse("null", ["core"]));

        Assert.Contains("null", ex.Message, StringComparison.OrdinalIgnoreCase);
    }

    // ─── ZipBundler lines 102-106: duplicate ZIP entry skipping ───

    [Fact]
    public void CreateBundle_DuplicateOptimizedNames_SkipsDuplicateEntry()
    {
        // Setup: two source images that map to the same optimized filename
        var imageDir = Path.Combine(_tempDir, "images");
        var outputDir = Path.Combine(_tempDir, "output");
        Directory.CreateDirectory(imageDir);
        Directory.CreateDirectory(outputDir);

        var img1 = CreateImage(imageDir, "photo.png");
        var img2 = CreateImage(imageDir, "photo_copy.png");
        WriteSidecar(img1, outputDir);
        WriteSidecar(img2, outputDir);

        // Both map to the SAME optimized file name to trigger the duplicate entry path
        var sharedOptimized = CreateImage(outputDir, "shared.webp");
        var optimizedMap = new Dictionary<string, string>
        {
            [img1] = sharedOptimized,
            [img2] = sharedOptimized, // Same destination → duplicate entry name
        };

        var result = ZipBundler.CreateBundle(
            ZipMode.Full,
            new DirectoryInfo(imageDir),
            outputDir,
            allImages: [img1, img2],
            plans: [],
            processed: [],
            bundleOptimizedMap: optimizedMap,
            manifest: new BuildManifest());

        // Only the first image should be bundled; the duplicate entry is skipped
        Assert.Equal(1, result.ImageCount);
        Assert.Single(result.BundledImagePaths);

        using var zip = ZipFile.OpenRead(result.ZipPath);
        var entryNames = zip.Entries.Select(e => e.FullName).ToList();
        // Exactly one image entry + one sidecar entry
        Assert.Equal(2, entryNames.Count);
    }

    // ─── ImageHashService line 68: manifest deserialization returns null ───

    [Fact]
    public void LoadManifest_NullJsonContent_ReturnsEmptyDictionary()
    {
        var manifestPath = Path.Combine(_tempDir, ".meme-hashes.json");
        File.WriteAllText(manifestPath, "null");

        var result = ImageHashService.LoadManifest(_tempDir);

        Assert.NotNull(result);
        Assert.Empty(result);
    }

    [Fact]
    public void LoadManifest_CorruptJson_ReturnsEmptyDictionary()
    {
        var manifestPath = Path.Combine(_tempDir, ".meme-hashes.json");
        File.WriteAllText(manifestPath, "{{{invalid json");

        var result = ImageHashService.LoadManifest(_tempDir);

        Assert.NotNull(result);
        Assert.Empty(result);
    }

    // ─── ImageOptimizer line 122: onComplete callback in OptimizeBatchForBundle ───

    [Fact]
    public void OptimizeBatchForBundle_OnCompleteCallback_InvokedForEachImage()
    {
        var img1 = CreateImage(_tempDir, "cb1.png", 16, 16);
        var img2 = CreateImage(_tempDir, "cb2.png", 16, 16);

        var callbacks = new List<(string Original, string Optimized)>();
        ImageOptimizer.OptimizeBatchForBundle(
            [img1, img2],
            _tempDir,
            concurrency: 1,
            onComplete: (orig, opt) => callbacks.Add((orig, opt)));

        Assert.Equal(2, callbacks.Count);
        Assert.Contains(callbacks, c => c.Original == img1);
        Assert.Contains(callbacks, c => c.Original == img2);
        Assert.All(callbacks, c => Assert.EndsWith(".webp", c.Optimized));
    }

    // ─── ImageOptimizer: onComplete callback in OptimizeBatchForApi ───

    [Fact]
    public void OptimizeBatchForApi_OnCompleteCallback_InvokedForEachImage()
    {
        // Use images larger than default max to trigger actual optimization
        var img1 = CreateImage(_tempDir, "api1.png", 2000, 2000);
        var img2 = CreateImage(_tempDir, "api2.png", 2000, 2000);

        var callbacks = new List<(string Original, string Optimized)>();
        ImageOptimizer.OptimizeBatchForApi(
            [img1, img2],
            _tempDir,
            concurrency: 1,
            onComplete: (orig, opt) => callbacks.Add((orig, opt)));

        Assert.Equal(2, callbacks.Count);
        Assert.Contains(callbacks, c => c.Original == img1);
        Assert.Contains(callbacks, c => c.Original == img2);
    }

    // ─── SidecarMerger line 70: search group with null Tags preserves existing ───

    [Fact]
    public void Merge_SearchGroup_NullTags_PreservesExistingTags()
    {
        var existing = new SidecarMetadata
        {
            Emojis = ["😂"],
            Tags = ["old-tag"],
            SearchPhrases = ["old phrase"],
        };

        // Partial result has new searchPhrases but null Tags
        var partial = new AnalysisResult
        {
            Tags = null,
            SearchPhrases = ["new phrase"],
        };

        var result = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupSearch]);

        // Null Tags preserves existing, non-null SearchPhrases is updated
        Assert.Equal(["old-tag"], result.Tags);
        Assert.Equal(["new phrase"], result.SearchPhrases);
    }

    [Fact]
    public void Merge_SearchGroup_NullSearchPhrases_PreservesExistingSearchPhrases()
    {
        var existing = new SidecarMetadata
        {
            Emojis = ["😂"],
            Tags = ["old-tag"],
            SearchPhrases = ["old phrase"],
        };

        var partial = new AnalysisResult
        {
            Tags = ["new-tag"],
            SearchPhrases = null,
        };

        var result = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupSearch]);

        Assert.Equal(["new-tag"], result.Tags);
        Assert.Equal(["old phrase"], result.SearchPhrases);
    }

    // ─── SidecarMerger lines 155-156: strip with description non-null ───

    [Fact]
    public void Strip_CoreRemoved_TitleAndDescriptionBecomesNull()
    {
        var existing = new SidecarMetadata
        {
            Emojis = ["😂"],
            Title = "My Title",
            Description = "My Description",
        };

        // Core group missing from current hashes
        var hashes = new Dictionary<string, string>
        {
            [PromptHasher.GroupSearch] = "h2",
            [PromptHasher.GroupCultural] = "h3",
            [PromptHasher.GroupEmotions] = "h4",
        };

        var result = SidecarMerger.StripRemovedGroups(existing, hashes);

        Assert.Null(result.Title);
        Assert.Null(result.Description);
        // Emojis are always preserved (never stripped)
        Assert.Equal(["😂"], result.Emojis);
    }

    [Fact]
    public void Strip_SearchRemoved_WithNonNullDescription_PreservesDescription()
    {
        var existing = new SidecarMetadata
        {
            Emojis = ["😂"],
            Title = "Title",
            Description = "Desc",
            Tags = ["tag1"],
            SearchPhrases = ["search"],
        };

        // Search removed, Core present
        var hashes = new Dictionary<string, string>
        {
            [PromptHasher.GroupCore] = "h1",
            [PromptHasher.GroupCultural] = "h3",
            [PromptHasher.GroupEmotions] = "h4",
        };

        var result = SidecarMerger.StripRemovedGroups(existing, hashes);

        Assert.Equal("Title", result.Title);
        Assert.Equal("Desc", result.Description);
        Assert.Null(result.Tags);
        Assert.Null(result.SearchPhrases);
    }

    // ─── RebuildPlanner line 123: NeedsReoptimization branches ───

    [Fact]
    public void PlanForImage_OptFingerprint_HasBundleOptimizedFalse_NeedsReopt()
    {
        var imageDir = Path.Combine(_tempDir, "imgs");
        var outputDir = Path.Combine(_tempDir, "output");
        Directory.CreateDirectory(imageDir);
        Directory.CreateDirectory(outputDir);

        var img = CreateImage(imageDir, "reopt.png");
        // Create sidecar so HasSidecar returns true
        SidecarService.WriteSidecar(img, new SidecarMetadata { Emojis = ["😂"] }, outputDir);

        var contentHash = ImageHashService.GetContentHash(img);
        var promptHashes = PromptHasher.ComputeAll(["en"]);

        var manifest = new BuildManifest();
        manifest.Images["reopt.png"] = new ImageManifestEntry
        {
            ContentHash = contentHash,
            Model = "gpt-5-mini",
            SchemaVersion = "1.3",
            GeneratedAt = DateTimeOffset.UtcNow.ToString("o"),
            FieldHashes = new Dictionary<string, string>(promptHashes),
            OptimizationFingerprint = "matching_fp",
            HasApiOptimized = true,
            HasBundleOptimized = false, // ← This triggers NeedsReoptimization
        };

        var plan = RebuildPlanner.PlanForImage(
            img, manifest, promptHashes, "gpt-5-mini", "1.3", outputDir,
            currentOptFingerprint: "matching_fp");

        Assert.Equal(RebuildScope.Skip, plan.Scope);
        Assert.True(plan.NeedsReoptimization);
    }

    [Fact]
    public void PlanForImage_OptFingerprint_HasApiOptimizedFalse_NeedsReopt()
    {
        var imageDir = Path.Combine(_tempDir, "imgs2");
        var outputDir = Path.Combine(_tempDir, "output2");
        Directory.CreateDirectory(imageDir);
        Directory.CreateDirectory(outputDir);

        var img = CreateImage(imageDir, "reopt2.png");
        SidecarService.WriteSidecar(img, new SidecarMetadata { Emojis = ["😂"] }, outputDir);

        var contentHash = ImageHashService.GetContentHash(img);
        var promptHashes = PromptHasher.ComputeAll(["en"]);

        var manifest = new BuildManifest();
        manifest.Images["reopt2.png"] = new ImageManifestEntry
        {
            ContentHash = contentHash,
            Model = "gpt-5-mini",
            SchemaVersion = "1.3",
            GeneratedAt = DateTimeOffset.UtcNow.ToString("o"),
            FieldHashes = new Dictionary<string, string>(promptHashes),
            OptimizationFingerprint = "matching_fp",
            HasApiOptimized = false, // ← This triggers NeedsReoptimization
            HasBundleOptimized = true,
        };

        var plan = RebuildPlanner.PlanForImage(
            img, manifest, promptHashes, "gpt-5-mini", "1.3", outputDir,
            currentOptFingerprint: "matching_fp");

        Assert.Equal(RebuildScope.Skip, plan.Scope);
        Assert.True(plan.NeedsReoptimization);
    }

    // ─── ManifestService line 29: deserialize returns null ───

    [Fact]
    public void ManifestService_Load_NullJsonContent_ReturnsEmptyManifest()
    {
        var manifestPath = Path.Combine(_tempDir, BuildManifest.FileName);
        File.WriteAllText(manifestPath, "null");

        var result = ManifestService.Load(_tempDir);

        Assert.NotNull(result);
        Assert.Empty(result.Images);
    }

    // ─── SidecarService line 55: image magic bytes edge case ───

    [Fact]
    public void IsSupportedImage_SmallFile_LessThan4Bytes_ReturnsFalse()
    {
        var path = Path.Combine(_tempDir, "tiny.png");
        File.WriteAllBytes(path, [0x89, 0x50]); // Only 2 bytes — not enough

        Assert.False(SidecarService.IsSupportedImage(path));
    }

    // ─── ImageHashService line 73: manifest entry missing phash property ───

    [Fact]
    public void LoadManifest_EntryWithoutPhashProperty_ParsesWithNullPhash()
    {
        var manifestPath = Path.Combine(_tempDir, ".meme-hashes.json");
        File.WriteAllText(manifestPath, """{"img.png": {"content_hash": "abc123"}}""");

        var result = ImageHashService.LoadManifest(_tempDir);

        Assert.Single(result);
        Assert.Equal("abc123", result["img.png"].ContentHash);
        Assert.Null(result["img.png"].PerceptualHash);
    }

    [Fact]
    public void LoadManifest_EntryWithoutContentHashProperty_DefaultsToEmpty()
    {
        var manifestPath = Path.Combine(_tempDir, ".meme-hashes.json");
        File.WriteAllText(manifestPath, """{"img.png": {"phash": "12345"}}""");

        var result = ImageHashService.LoadManifest(_tempDir);

        Assert.Single(result);
        Assert.Equal("", result["img.png"].ContentHash);
        Assert.Equal("12345", result["img.png"].PerceptualHash);
    }

    // ─── ZipBundler: verbose path in bundle creation (line 126) ───

    [Fact]
    public void CreateBundle_VerboseMode_DoesNotThrow()
    {
        var imageDir = Path.Combine(_tempDir, "verb_imgs");
        var outputDir = Path.Combine(_tempDir, "verb_output");
        Directory.CreateDirectory(imageDir);
        Directory.CreateDirectory(outputDir);

        var img = CreateImage(imageDir, "verbose.png");
        WriteSidecar(img, outputDir);
        var map = new Dictionary<string, string> { [img] = img };

        var result = ZipBundler.CreateBundle(
            ZipMode.Full,
            new DirectoryInfo(imageDir),
            outputDir,
            allImages: [img],
            plans: [],
            processed: [],
            bundleOptimizedMap: map,
            manifest: new BuildManifest(),
            verbose: true);

        Assert.Equal(1, result.ImageCount);
    }

    // ─── SidecarMerger: merge localization when partial.Localizations is null ───

    [Fact]
    public void Merge_LocalizationGroup_NullPartialLocalizations_PreservesExisting()
    {
        var existing = new SidecarMetadata
        {
            Emojis = ["😂"],
            Localizations = new Dictionary<string, LocalizedContent>
            {
                ["cs"] = new() { Title = "Starý" },
            },
        };

        // Partial has no localizations at all
        var partial = new AnalysisResult { Localizations = null };

        var result = SidecarMerger.Merge(
            existing, partial, [PromptHasher.LocalizationGroup("cs")]);

        // Existing localization is preserved since partial doesn't have the key
        Assert.NotNull(result.Localizations);
        Assert.True(result.Localizations.ContainsKey("cs"));
        Assert.Equal("Starý", result.Localizations["cs"].Title);
    }

    // ─── DedupeCommand: Execute static helper tests ───
    // The Execute method uses AnsiConsole and file I/O — we can test the underlying
    // services it calls to cover the code paths.

    [Fact]
    public void Deduplicate_VerboseMode_DoesNotThrow()
    {
        var img1 = CreateImage(_tempDir, "v1.png", 32, 32);
        var img2 = Path.Combine(_tempDir, "v2.png");
        File.Copy(img1, img2);

        var manifest = new Dictionary<string, HashEntry>();
        var result = ImageHashService.Deduplicate(
            [img1, img2], manifest,
            detectNearDuplicates: true,
            similarityThreshold: 10,
            verbose: true);

        Assert.Single(result.ExactDuplicates);
    }

    [Fact]
    public void Deduplicate_CachedManifestWithPhash_UsesCache()
    {
        var img = CreateImage(_tempDir, "cached.png", 32, 32);

        // Pre-populate manifest with phash so the code uses the cached path
        var manifest = new Dictionary<string, HashEntry>
        {
            ["cached.png"] = new(ImageHashService.GetContentHash(img), "999"),
        };

        var result = ImageHashService.Deduplicate(
            [img], manifest,
            detectNearDuplicates: true,
            similarityThreshold: 10);

        Assert.Single(result.UniqueImages);
        // Manifest entry unchanged (used cache)
        Assert.Equal("999", manifest["cached.png"].PerceptualHash);
    }

    [Fact]
    public void CreateBundle_DuplicateOptimizedNames_VerboseMode_SkipsDuplicateEntry()
    {
        var imageDir = Path.Combine(_tempDir, "dup_verb_images");
        var outputDir = Path.Combine(_tempDir, "dup_verb_output");
        Directory.CreateDirectory(imageDir);
        Directory.CreateDirectory(outputDir);

        var img1 = CreateImage(imageDir, "dup1.png");
        var img2 = CreateImage(imageDir, "dup2.png");
        WriteSidecar(img1, outputDir);
        WriteSidecar(img2, outputDir);

        var sharedOpt = CreateImage(outputDir, "same.webp");
        var optimizedMap = new Dictionary<string, string>
        {
            [img1] = sharedOpt,
            [img2] = sharedOpt, // duplicate entry name
        };

        var result = ZipBundler.CreateBundle(
            ZipMode.Full,
            new DirectoryInfo(imageDir),
            outputDir,
            allImages: [img1, img2],
            plans: [],
            processed: [],
            bundleOptimizedMap: optimizedMap,
            manifest: new BuildManifest(),
            verbose: true); // ← verbose=true to hit line 110

        Assert.Equal(1, result.ImageCount);
    }

    // ─── SidecarMerger: strip branch where Title is null but Description is not ───

    [Fact]
    public void Strip_CoreRemoved_NullTitle_NonNullDescription_StillStrips()
    {
        var existing = new SidecarMetadata
        {
            Emojis = ["😂"],
            Title = null,
            Description = "Has description but no title",
        };

        var hashes = new Dictionary<string, string>
        {
            [PromptHasher.GroupSearch] = "h2",
            [PromptHasher.GroupCultural] = "h3",
            [PromptHasher.GroupEmotions] = "h4",
        };

        var result = SidecarMerger.StripRemovedGroups(existing, hashes);

        Assert.NotSame(existing, result); // Should be a new object (changed)
        Assert.Null(result.Title);
        Assert.Null(result.Description);
    }

    [Fact]
    public void Strip_SearchRemoved_NullTags_NonNullSearchPhrases_StillStrips()
    {
        var existing = new SidecarMetadata
        {
            Emojis = ["😂"],
            Tags = null,
            SearchPhrases = ["has phrases but no tags"],
        };

        var hashes = new Dictionary<string, string>
        {
            [PromptHasher.GroupCore] = "h1",
            [PromptHasher.GroupCultural] = "h3",
            [PromptHasher.GroupEmotions] = "h4",
        };

        var result = SidecarMerger.StripRemovedGroups(existing, hashes);

        Assert.NotSame(existing, result);
        Assert.Null(result.Tags);
        Assert.Null(result.SearchPhrases);
    }

    // ─── CopilotService line 222: generic code block stripping in ParsePartialResponse ───

    [Fact]
    public void ParsePartialResponse_GenericCodeBlock_IsUnwrapped()
    {
        // Uses ``` without "json" suffix — hits line 222
        var content = "```\n{\"tags\": [\"cat\", \"funny\"]}\n```";

        var result = CopilotService.ParsePartialResponse(content, ["search"]);

        Assert.Equal(["cat", "funny"], result.Tags);
    }

    // ─── Helpers ────────────────────────────────────────────────

    private static string CreateImage(string dir, string name, int w = 4, int h = 4)
    {
        var path = Path.Combine(dir, name);
        using var img = new Image<Rgba32>(w, h);
        img.Save(path);
        return path;
    }

    private static void WriteSidecar(string imagePath, string outputDir)
    {
        SidecarService.WriteSidecar(
            imagePath,
            new SidecarMetadata
            {
                Emojis = ["😂"],
                Title = "test",
                Description = "test desc",
                Tags = ["test"],
                SearchPhrases = ["test search"],
            },
            outputDir);
    }
}
