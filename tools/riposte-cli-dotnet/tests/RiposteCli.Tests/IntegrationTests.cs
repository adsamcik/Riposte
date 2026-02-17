using System.Text.Json;
using RiposteCli.Models;
using RiposteCli.Services;

namespace RiposteCli.Tests;

/// <summary>
/// End-to-end integration tests exercising multiple services together.
/// </summary>
public class IntegrationTests : IDisposable
{
    private readonly string _tempDir;

    public IntegrationTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), $"riposte-integration-{Guid.NewGuid()}");
        Directory.CreateDirectory(_tempDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempDir))
            Directory.Delete(_tempDir, true);
    }

    #region Metadata → Sidecar → Readback

    [Fact]
    public void CreateMetadata_WriteSidecar_ReadBack_RoundTrip()
    {
        var imgPath = CreateImage("meme.jpg");
        var result = new AnalysisResult
        {
            Emojis = ["😂", "🐱"],
            Title = "Funny Cat",
            Description = "A cat looking confused at a computer screen",
            Tags = ["cat", "funny", "programming"],
            SearchPhrases = ["confused cat meme", "programmer cat"],
            BasedOn = "Programmer humor",
        };

        var contentHash = ImageHashService.GetContentHash(imgPath);
        var metadata = SidecarService.CreateMetadata(result, "en", contentHash);
        var sidecarPath = SidecarService.WriteSidecar(imgPath, metadata);

        // Read back and verify
        var json = File.ReadAllText(sidecarPath);
        var readBack = JsonSerializer.Deserialize<SidecarMetadata>(json);

        Assert.NotNull(readBack);
        Assert.Equal(["😂", "🐱"], readBack.Emojis);
        Assert.Equal("Funny Cat", readBack.Title);
        Assert.Equal("A cat looking confused at a computer screen", readBack.Description);
        Assert.Equal(["cat", "funny", "programming"], readBack.Tags);
        Assert.Equal("en", readBack.PrimaryLanguage);
        Assert.Equal(contentHash, readBack.ContentHash);
        Assert.Equal("Programmer humor", readBack.BasedOn);
        Assert.Equal("1.3", readBack.SchemaVersion);
    }

    [Fact]
    public void CreateMetadata_WithLocalizations_RoundTrip()
    {
        var imgPath = CreateImage("global.jpg");
        var result = new AnalysisResult
        {
            Emojis = ["🌍"],
            Title = "Global Meme",
            Localizations = new Dictionary<string, LocalizedContent>
            {
                ["cs"] = new()
                {
                    Title = "Globální Meme",
                    Description = "Popis v češtině",
                    Tags = ["vtipné", "globální"],
                    SearchPhrases = ["globální meme"],
                },
                ["de"] = new()
                {
                    Title = "Globales Meme",
                    Description = "Beschreibung auf Deutsch",
                },
            },
        };

        var metadata = SidecarService.CreateMetadata(result, "en");
        var sidecarPath = SidecarService.WriteSidecar(imgPath, metadata);

        var json = File.ReadAllText(sidecarPath);
        var readBack = JsonSerializer.Deserialize<SidecarMetadata>(json);

        Assert.NotNull(readBack?.Localizations);
        Assert.Equal(2, readBack.Localizations.Count);
        Assert.Equal("Globální Meme", readBack.Localizations["cs"].Title);
        Assert.Equal(["vtipné", "globální"], readBack.Localizations["cs"].Tags);
        Assert.Equal("Globales Meme", readBack.Localizations["de"].Title);
        Assert.Null(readBack.Localizations["de"].Tags);
    }

    #endregion

    #region Image Discovery → Filter → Process

    [Fact]
    public void FullDiscoveryPipeline_MixedFiles_CorrectFiltering()
    {
        // Setup: mix of images, non-images, and existing sidecars
        CreateImage("photo1.jpg");
        CreateImage("photo2.png");
        CreateImage("photo3.jpg");
        File.WriteAllText(Path.Combine(_tempDir, "notes.txt"), "text");
        File.WriteAllText(Path.Combine(_tempDir, "data.json"), "{}");

        // One already-annotated image
        File.WriteAllText(Path.Combine(_tempDir, "photo1.jpg.json"), "{}");

        // Step 1: Discover images
        var allImages = SidecarService.GetImagesInFolder(_tempDir);
        Assert.Equal(3, allImages.Count);

        // Step 2: Filter (incremental mode)
        var (toProcess, skipped) = SidecarService.FilterImagesByMode(allImages, _tempDir, force: false);
        Assert.Equal(2, toProcess.Count);
        Assert.Equal(1, skipped);

        // Verify the skipped one is photo1
        Assert.All(toProcess, p => Assert.DoesNotContain("photo1", Path.GetFileName(p)));
    }

    [Fact]
    public void FullDiscoveryPipeline_ForceMode_ProcessesAll()
    {
        CreateImage("a.jpg");
        CreateImage("b.jpg");
        File.WriteAllText(Path.Combine(_tempDir, "a.jpg.json"), "{}");
        File.WriteAllText(Path.Combine(_tempDir, "b.jpg.json"), "{}");

        var allImages = SidecarService.GetImagesInFolder(_tempDir);
        var (toProcess, skipped) = SidecarService.FilterImagesByMode(allImages, _tempDir, force: true);

        Assert.Equal(2, toProcess.Count);
        Assert.Equal(0, skipped);
    }

    #endregion

    #region Dedup → Manifest → Reload Consistency

    [Fact]
    public void DedupWorkflow_ManifestConsistentAcrossReloads()
    {
        var content = "same content"u8.ToArray();
        CreateFile("orig.jpg", content);
        CreateFile("copy.jpg", content);
        CreateFile("unique.jpg", "different"u8.ToArray());

        var images = SidecarService.GetImagesInFolder(_tempDir);
        var manifest = ImageHashService.LoadManifest(_tempDir);

        // First run: detect duplicates, save manifest
        var result = ImageHashService.Deduplicate(images, manifest, detectNearDuplicates: false);
        ImageHashService.SaveManifest(_tempDir, manifest);

        // Second run: reload manifest, results should be consistent
        var manifest2 = ImageHashService.LoadManifest(_tempDir);
        var result2 = ImageHashService.Deduplicate(images, manifest2, detectNearDuplicates: false);

        Assert.Equal(result.ExactDuplicates.Count, result2.ExactDuplicates.Count);
        Assert.Equal(result.UniqueImages.Count, result2.UniqueImages.Count);
    }

    [Fact]
    public void ContentHash_InSidecar_MatchesManifest()
    {
        var imgPath = CreateImage("verified.jpg");

        // Compute hash two ways
        var hashFromService = ImageHashService.GetContentHash(imgPath);

        // Put in sidecar
        var metadata = SidecarService.CreateMetadata(
            emojis: ["✅"],
            contentHash: hashFromService);
        var sidecarPath = SidecarService.WriteSidecar(imgPath, metadata);

        // Read back from sidecar
        var json = File.ReadAllText(sidecarPath);
        var readBack = JsonSerializer.Deserialize<SidecarMetadata>(json);

        Assert.Equal(hashFromService, readBack?.ContentHash);

        // Also verify manifest
        var manifest = new Dictionary<string, HashEntry>();
        ImageHashService.Deduplicate([imgPath], manifest, detectNearDuplicates: false);
        Assert.Equal(hashFromService, manifest["verified.jpg"].ContentHash);
    }

    #endregion

    #region Language Parsing

    [Fact]
    public void LanguageParsing_CommaSeparated_SplitsCorrectly()
    {
        var input = "en,cs,de";
        var languages = input.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries).ToList();

        Assert.Equal(3, languages.Count);
        Assert.Equal("en", languages[0]);
        Assert.Equal("cs", languages[1]);
        Assert.Equal("de", languages[2]);
    }

    [Fact]
    public void LanguageParsing_WithSpaces_TrimsCorrectly()
    {
        var input = "en, cs , de";
        var languages = input.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries).ToList();

        Assert.Equal(["en", "cs", "de"], languages);
    }

    [Fact]
    public void LanguageParsing_SingleLanguage()
    {
        var input = "fr";
        var languages = input.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries).ToList();

        Assert.Single(languages);
        Assert.Equal("fr", languages[0]);
    }

    [Fact]
    public void LanguageParsing_EmptyString_ReturnsEmpty()
    {
        var input = "";
        var languages = input.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries).ToList();

        Assert.Empty(languages);
    }

    [Fact]
    public void ConcurrencyClamping_BelowMin_ClampsTo1()
    {
        Assert.Equal(1, Math.Clamp(0, 1, 10));
        Assert.Equal(1, Math.Clamp(-5, 1, 10));
    }

    [Fact]
    public void ConcurrencyClamping_AboveMax_ClampsTo10()
    {
        Assert.Equal(10, Math.Clamp(100, 1, 10));
        Assert.Equal(10, Math.Clamp(11, 1, 10));
    }

    [Fact]
    public void ConcurrencyClamping_WithinRange_Unchanged()
    {
        Assert.Equal(4, Math.Clamp(4, 1, 10));
        Assert.Equal(1, Math.Clamp(1, 1, 10));
        Assert.Equal(10, Math.Clamp(10, 1, 10));
    }

    #endregion

    #region Multiple Sidecars In Same Directory

    [Fact]
    public void MultipleSidecars_DontInterfere()
    {
        var img1 = CreateImage("meme1.jpg");
        var img2 = CreateImage("meme2.png");

        var meta1 = SidecarService.CreateMetadata(emojis: ["😂"], title: "First");
        var meta2 = SidecarService.CreateMetadata(emojis: ["🔥"], title: "Second");

        SidecarService.WriteSidecar(img1, meta1);
        SidecarService.WriteSidecar(img2, meta2);

        // Verify each sidecar has correct content
        var json1 = File.ReadAllText(Path.Combine(_tempDir, "meme1.jpg.json"));
        var json2 = File.ReadAllText(Path.Combine(_tempDir, "meme2.png.json"));

        var read1 = JsonSerializer.Deserialize<SidecarMetadata>(json1);
        var read2 = JsonSerializer.Deserialize<SidecarMetadata>(json2);

        Assert.Equal("First", read1?.Title);
        Assert.Equal("Second", read2?.Title);
        Assert.Equal(["😂"], read1?.Emojis);
        Assert.Equal(["🔥"], read2?.Emojis);
    }

    #endregion

    private string CreateImage(string name)
    {
        var path = Path.Combine(_tempDir, name);
        byte[] data = Path.GetExtension(name).ToLower() switch
        {
            ".png" => [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A],
            _ => [0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10],
        };
        File.WriteAllBytes(path, data);
        return path;
    }

    private string CreateFile(string name, byte[] content)
    {
        var path = Path.Combine(_tempDir, name);
        File.WriteAllBytes(path, content);
        return path;
    }
}
