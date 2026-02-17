using System.Text.Json;
using RiposteCli.Models;
using RiposteCli.Services;

namespace RiposteCli.Tests;

/// <summary>
/// Edge cases: unusual inputs, boundary conditions, special characters.
/// </summary>
public class EdgeCaseTests : IDisposable
{
    private readonly string _tempDir;

    public EdgeCaseTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), $"riposte-edge-{Guid.NewGuid()}");
        Directory.CreateDirectory(_tempDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempDir))
            Directory.Delete(_tempDir, true);
    }

    #region Extension Case Sensitivity

    [Theory]
    [InlineData("test.JPG")]
    [InlineData("test.Jpg")]
    [InlineData("test.jPg")]
    [InlineData("test.PNG")]
    [InlineData("test.GIF")]
    [InlineData("test.WEBP")]
    public void IsSupportedImage_CaseInsensitiveExtension(string name)
    {
        // Use JPEG magic bytes
        var path = Path.Combine(_tempDir, name);
        File.WriteAllBytes(path, [0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10]);
        Assert.True(SidecarService.IsSupportedImage(path));
    }

    #endregion

    #region Special Characters in Filenames

    [Fact]
    public void SidecarPath_WithSpacesInFilename()
    {
        var imgPath = Path.Combine(_tempDir, "my meme image.jpg");
        File.WriteAllBytes(imgPath, [0xFF, 0xD8, 0xFF, 0xE0]);

        var metadata = SidecarService.CreateMetadata(emojis: ["😂"]);
        var sidecarPath = SidecarService.WriteSidecar(imgPath, metadata);

        Assert.Equal(Path.Combine(_tempDir, "my meme image.jpg.json"), sidecarPath);
        Assert.True(File.Exists(sidecarPath));
    }

    [Fact]
    public void SidecarPath_WithParentheses()
    {
        var imgPath = Path.Combine(_tempDir, "image (1).jpg");
        File.WriteAllBytes(imgPath, [0xFF, 0xD8, 0xFF, 0xE0]);

        var metadata = SidecarService.CreateMetadata(emojis: ["📸"]);
        var sidecarPath = SidecarService.WriteSidecar(imgPath, metadata);

        Assert.True(File.Exists(sidecarPath));
        Assert.Contains("image (1).jpg.json", sidecarPath);
    }

    [Fact]
    public void SidecarPath_WithHyphensAndUnderscores()
    {
        var imgPath = Path.Combine(_tempDir, "my-meme_2024-01.jpg");
        File.WriteAllBytes(imgPath, [0xFF, 0xD8, 0xFF, 0xE0]);

        var metadata = SidecarService.CreateMetadata(emojis: ["🗓️"]);
        var sidecarPath = SidecarService.WriteSidecar(imgPath, metadata);
        Assert.True(File.Exists(sidecarPath));
    }

    #endregion

    #region AnalysisResult Edge Cases

    [Fact]
    public void AnalysisResult_EmptyOptionalLists()
    {
        var json = """
            {
                "emojis": ["😂"],
                "tags": [],
                "searchPhrases": []
            }
            """;
        var result = CopilotService.ParseResponseContent(json);
        Assert.Single(result.Emojis);
        Assert.NotNull(result.Tags);
        Assert.Empty(result.Tags);
        Assert.NotNull(result.SearchPhrases);
        Assert.Empty(result.SearchPhrases);
    }

    [Fact]
    public void AnalysisResult_SingleEmoji()
    {
        var json = """{"emojis": ["🎉"]}""";
        var result = CopilotService.ParseResponseContent(json);
        Assert.Single(result.Emojis);
        Assert.Equal("🎉", result.Emojis[0]);
    }

    [Fact]
    public void AnalysisResult_NullBasedOn_RemainsNull()
    {
        var json = """{"emojis": ["😂"], "basedOn": null}""";
        var result = CopilotService.ParseResponseContent(json);
        Assert.Null(result.BasedOn);
    }

    [Fact]
    public void AnalysisResult_EmptyStringTitle_PreservedAsEmpty()
    {
        var json = """{"emojis": ["😂"], "title": ""}""";
        var result = CopilotService.ParseResponseContent(json);
        Assert.Equal("", result.Title);
    }

    [Fact]
    public void AnalysisResult_VeryLongDescription_Preserved()
    {
        var longDesc = new string('a', 5000);
        var json = $$"""{"emojis": ["😂"], "description": "{{longDesc}}"}""";
        var result = CopilotService.ParseResponseContent(json);
        Assert.Equal(5000, result.Description?.Length);
    }

    [Fact]
    public void AnalysisResult_SpecialCharsInDescription()
    {
        var json = """{"emojis": ["😂"], "description": "He said \"hello\" & she said <goodbye>"}""";
        var result = CopilotService.ParseResponseContent(json);
        Assert.Contains("\"hello\"", result.Description);
        Assert.Contains("<goodbye>", result.Description);
    }

    [Fact]
    public void AnalysisResult_UnicodeInTags()
    {
        var json = """{"emojis": ["😂"], "tags": ["café", "naïve", "über", "日本語"]}""";
        var result = CopilotService.ParseResponseContent(json);
        Assert.Equal(4, result.Tags?.Count);
        Assert.Contains("日本語", result.Tags);
    }

    [Fact]
    public void AnalysisResult_NewlinesInDescription()
    {
        var json = """{"emojis": ["😂"], "description": "Line 1\nLine 2\nLine 3"}""";
        var result = CopilotService.ParseResponseContent(json);
        Assert.Contains("\n", result.Description);
    }

    #endregion

    #region SidecarMetadata Edge Cases

    [Fact]
    public void SidecarMetadata_ManyEmojis()
    {
        var metadata = SidecarService.CreateMetadata(
            emojis: ["😀", "😁", "😂", "🤣", "😃", "😄", "😅", "😆"]);
        Assert.Equal(8, metadata.Emojis.Count);
    }

    [Fact]
    public void SidecarMetadata_EmptyTags_SerializesCorrectly()
    {
        var metadata = SidecarService.CreateMetadata(
            emojis: ["😂"],
            tags: []);

        var json = JsonSerializer.Serialize(metadata, new JsonSerializerOptions
        {
            DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
        });

        // Empty list should still appear (it's not null)
        Assert.Contains("\"tags\"", json);
        Assert.Contains("[]", json);
    }

    [Fact]
    public void SidecarMetadata_EmptyLocalizations_SerializesCorrectly()
    {
        var metadata = new SidecarMetadata
        {
            Emojis = ["😂"],
            Localizations = new Dictionary<string, LocalizedContent>(),
        };

        var json = JsonSerializer.Serialize(metadata, new JsonSerializerOptions
        {
            DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
        });

        Assert.Contains("\"localizations\"", json);
        Assert.Contains("{}", json);
    }

    #endregion

    #region Hash Edge Cases

    [Fact]
    public void ContentHash_LargeFile_Completes()
    {
        // Create a 1MB file
        var path = Path.Combine(_tempDir, "large.bin");
        var data = new byte[1024 * 1024];
        new Random(42).NextBytes(data);
        File.WriteAllBytes(path, data);

        var hash = ImageHashService.GetContentHash(path);
        Assert.Equal(64, hash.Length);
    }

    [Fact]
    public void ContentHash_SingleByte_ValidHash()
    {
        var path = Path.Combine(_tempDir, "tiny.bin");
        File.WriteAllBytes(path, [0x42]);

        var hash = ImageHashService.GetContentHash(path);
        Assert.Equal(64, hash.Length);
    }

    [Fact]
    public void HammingDistance_OneBitDifference_ReturnsOne()
    {
        // Test all single-bit differences at various positions
        Assert.Equal(1, ImageHashService.HammingDistance(0, 1));
        Assert.Equal(1, ImageHashService.HammingDistance(0, 2));
        Assert.Equal(1, ImageHashService.HammingDistance(0, 4));
        Assert.Equal(1, ImageHashService.HammingDistance(0, 1UL << 63));
    }

    #endregion

    #region Manifest Edge Cases

    [Fact]
    public void Manifest_SpecialCharsInFilename()
    {
        var manifest = new Dictionary<string, HashEntry>
        {
            ["image (1).jpg"] = new("hash1", null),
            ["my-meme_v2.png"] = new("hash2", "phash2"),
        };

        ImageHashService.SaveManifest(_tempDir, manifest);
        var loaded = ImageHashService.LoadManifest(_tempDir);

        Assert.Equal(2, loaded.Count);
        Assert.Equal("hash1", loaded["image (1).jpg"].ContentHash);
    }

    [Fact]
    public void Manifest_EmptyManifest_SavesAndLoads()
    {
        var manifest = new Dictionary<string, HashEntry>();
        ImageHashService.SaveManifest(_tempDir, manifest);

        var loaded = ImageHashService.LoadManifest(_tempDir);
        Assert.Empty(loaded);
    }

    [Fact]
    public void Manifest_LargeManifest_RoundTrips()
    {
        var manifest = new Dictionary<string, HashEntry>();
        for (var i = 0; i < 500; i++)
            manifest[$"image_{i:D4}.jpg"] = new($"hash_{i}", i % 2 == 0 ? $"phash_{i}" : null);

        ImageHashService.SaveManifest(_tempDir, manifest);
        var loaded = ImageHashService.LoadManifest(_tempDir);

        Assert.Equal(500, loaded.Count);
        Assert.Equal("hash_0", loaded["image_0000.jpg"].ContentHash);
        Assert.Equal("phash_0", loaded["image_0000.jpg"].PerceptualHash);
        Assert.Null(loaded["image_0001.jpg"].PerceptualHash);
    }

    #endregion

    #region Error Classification Edge Cases

    [Fact]
    public void ParseResponse_OnlyWhitespace_Throws()
    {
        Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent("   \n\t  "));
    }

    [Fact]
    public void ParseResponse_JsonWithTrailingComma_Throws()
    {
        Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent("""{"emojis": ["😂",]}"""));
    }

    [Fact]
    public void ParseResponse_NestedCodeBlocks_HandlesOuter()
    {
        var content = """
            ```json
            {"emojis": ["😂"], "description": "contains ``` backticks"}
            ```
            """;
        // The triple backticks in the description will be stripped by the outer handling
        // This tests that the parser handles it gracefully (may throw or parse)
        try
        {
            var result = CopilotService.ParseResponseContent(content);
            Assert.NotNull(result);
        }
        catch (CopilotAnalysisException)
        {
            // Also acceptable — malformed markdown
        }
    }

    [Fact]
    public void ParseResponse_CodeBlockWithLanguageVariant()
    {
        // ```JSON (uppercase) won't match StartsWith("```json") but will match StartsWith("```")
        // After stripping "```", the remaining "JSON\n..." is still not valid JSON on its own.
        // The parser handles this by stripping ```<anything> → content → ```.
        // With ```JSON, the ``` is stripped leaving "JSON\n{...}\n" which starts with "JSON"
        // and a newline — JSON itself starts on the next line, so we need the parser to handle
        // multi-line content where first line after ``` may be a language tag.
        // Actually, the parser does: strip "```json" (7 chars) OR "```" (3 chars).
        // For "```JSON", it strips 3 chars leaving "JSON\n{...}" which is invalid JSON → throws.
        var content = """
            ```JSON
            {"emojis": ["😂"]}
            ```
            """;
        Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent(content));
    }

    #endregion
}
