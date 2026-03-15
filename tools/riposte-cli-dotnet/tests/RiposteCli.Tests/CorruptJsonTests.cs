using System.Text;
using System.Text.Json;
using RiposteCli.Models;

namespace RiposteCli.Tests;

public sealed class CorruptJsonTests : IDisposable
{
    private readonly string _tempDir;

    public CorruptJsonTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString());
        Directory.CreateDirectory(_tempDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempDir))
            Directory.Delete(_tempDir, recursive: true);
    }

    // --- ParseResponseContent: corrupt/malformed JSON ---

    [Fact]
    public void ParseResponseContent_TruncatedJson_ThrowsCopilotAnalysisException()
    {
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent("{\"emojis\": [\"😂\""));

        Assert.Contains("Failed to parse API response as JSON", ex.Message);
    }

    [Fact]
    public void ParseResponseContent_EmptyString_ThrowsCopilotAnalysisException()
    {
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent(string.Empty));

        Assert.Contains("Failed to parse API response as JSON", ex.Message);
    }

    [Fact]
    public void ParseResponseContent_WhitespaceOnly_ThrowsCopilotAnalysisException()
    {
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent("   \n\t  "));

        Assert.Contains("Failed to parse API response as JSON", ex.Message);
    }

    [Fact]
    public void ParseResponseContent_HtmlInsteadOfJson_ThrowsCopilotAnalysisException()
    {
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent("<html>Error</html>"));

        Assert.Contains("Failed to parse API response as JSON", ex.Message);
    }

    [Fact]
    public void ParseResponseContent_ArrayInsteadOfObject_ThrowsCopilotAnalysisException()
    {
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent("""[{"emojis": ["😂"]}]"""));

        Assert.Contains("Failed to parse API response as JSON", ex.Message);
    }

    [Fact]
    public void ParseResponseContent_NestedCodeBlocks_ThrowsCopilotAnalysisException()
    {
        var content = "```json\n```json\n{\"emojis\":[\"😂\"]}```\n```";
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent(content));

        Assert.Contains("Failed to parse API response as JSON", ex.Message);
    }

    [Fact]
    public void ParseResponseContent_TrailingComma_ThrowsCopilotAnalysisException()
    {
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent("""{"emojis": ["😂",]}"""));

        Assert.Contains("Failed to parse API response as JSON", ex.Message);
    }

    [Fact]
    public void ParseResponseContent_BinaryGarbage_ThrowsCopilotAnalysisException()
    {
        var content = Encoding.Latin1.GetString(new byte[] { 0x00, 0x01, 0x02, 0xFF, 0xFE, 0xFD });
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent(content));

        Assert.Contains("Failed to parse API response as JSON", ex.Message);
    }

    [Fact]
    public void ParseResponseContent_ExtremelyLargeJson_SucceedsWithoutCrash()
    {
        var huge = new string('a', 1_100_000);
        var content = $$"""{"emojis":["😂"],"description":"{{huge}}"}""";

        var result = CopilotService.ParseResponseContent(content);

        Assert.Single(result.Emojis!);
        Assert.Equal(1_100_000, result.Description!.Length);
    }

    [Fact]
    public void ParseResponseContent_JsonWithBom_ThrowsCopilotAnalysisException()
    {
        var content = "\uFEFF{\"emojis\":[\"😂\"]}";
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent(content));

        Assert.Contains("Failed to parse API response as JSON", ex.Message);
    }

    [Fact]
    public void ParseResponseContent_NullLiteral_ThrowsCopilotAnalysisException()
    {
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent("null"));

        Assert.Contains("emojis", ex.Message, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void ParseResponseContent_EmptyEmojis_ThrowsCopilotAnalysisException()
    {
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent("""{"emojis": []}"""));

        Assert.Contains("emojis", ex.Message, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void ParseResponseContent_SingleQuotedJson_ThrowsCopilotAnalysisException()
    {
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent("{'emojis': ['😂']}"));

        Assert.Contains("Failed to parse API response as JSON", ex.Message);
    }

    [Fact]
    public void ParseResponseContent_JsonWithComments_ThrowsCopilotAnalysisException()
    {
        var content = """
            {
                // this is a comment
                "emojis": ["😂"]
            }
            """;
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent(content));

        Assert.Contains("Failed to parse API response as JSON", ex.Message);
    }

    [Fact]
    public void ParseResponseContent_ValidCodeBlock_StripsWrappingAndSucceeds()
    {
        var content = "```json\n{\"emojis\":[\"😂\"],\"title\":\"test\"}\n```";

        var result = CopilotService.ParseResponseContent(content);

        Assert.Single(result.Emojis!);
        Assert.Equal("😂", result.Emojis![0]);
        Assert.Equal("test", result.Title);
    }

    [Fact]
    public void ParseResponseContent_EmptyObject_ThrowsMissingEmojis()
    {
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent("{}"));

        Assert.Contains("emojis", ex.Message, StringComparison.OrdinalIgnoreCase);
    }

    // --- ParsePartialResponse: corrupt/malformed JSON ---

    [Fact]
    public void ParsePartialResponse_EmptyObject_Succeeds()
    {
        var result = CopilotService.ParsePartialResponse("{}", ["core"]);

        Assert.Null(result.Emojis);
        Assert.Null(result.Title);
        Assert.Null(result.Description);
        Assert.Null(result.Tags);
        Assert.Null(result.SearchPhrases);
        Assert.Null(result.BasedOn);
        Assert.Null(result.Localizations);
        Assert.Null(result.Emotions);
    }

    [Fact]
    public void ParsePartialResponse_NullLiteral_ThrowsCopilotAnalysisException()
    {
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParsePartialResponse("null", ["core"]));

        Assert.Contains("null", ex.Message, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void ParsePartialResponse_NumberLiteral_ThrowsCopilotAnalysisException()
    {
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParsePartialResponse("42", ["core"]));

        Assert.Contains("Failed to parse partial API response as JSON", ex.Message);
    }

    [Fact]
    public void ParsePartialResponse_TruncatedJson_ThrowsCopilotAnalysisException()
    {
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParsePartialResponse("{\"title\": \"te", ["core"]));

        Assert.Contains("Failed to parse partial API response as JSON", ex.Message);
    }

    [Fact]
    public void ParsePartialResponse_BinaryGarbage_ThrowsCopilotAnalysisException()
    {
        var content = Encoding.Latin1.GetString(new byte[] { 0x00, 0x01, 0x02, 0xFF, 0xFE, 0xFD });
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParsePartialResponse(content, ["core"]));

        Assert.Contains("Failed to parse partial API response as JSON", ex.Message);
    }

    [Fact]
    public void ParsePartialResponse_ValidCodeBlock_StripsWrappingAndSucceeds()
    {
        var content = "```json\n{\"title\":\"hello\"}\n```";

        var result = CopilotService.ParsePartialResponse(content, ["core"]);

        Assert.Equal("hello", result.Title);
    }

    [Fact]
    public void ParsePartialResponse_ArrayInsteadOfObject_ThrowsCopilotAnalysisException()
    {
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParsePartialResponse("""[{"title":"a"}]""", ["core"]));

        Assert.Contains("Failed to parse partial API response as JSON", ex.Message);
    }

    // --- ManifestService.Load: corrupt files ---

    [Fact]
    public void ManifestLoad_TruncatedJson_ReturnsEmptyManifest()
    {
        var path = Path.Combine(_tempDir, BuildManifest.FileName);
        File.WriteAllText(path, """{"promptHashes":{"core":"abc"}""");

        var manifest = ManifestService.Load(_tempDir);

        Assert.Empty(manifest.Images);
        Assert.Empty(manifest.PromptHashes);
    }

    [Fact]
    public void ManifestLoad_EmptyFile_ReturnsEmptyManifest()
    {
        var path = Path.Combine(_tempDir, BuildManifest.FileName);
        File.WriteAllText(path, string.Empty);

        var manifest = ManifestService.Load(_tempDir);

        Assert.Empty(manifest.Images);
        Assert.Empty(manifest.PromptHashes);
    }

    [Fact]
    public void ManifestLoad_WhitespaceOnly_ReturnsEmptyManifest()
    {
        var path = Path.Combine(_tempDir, BuildManifest.FileName);
        File.WriteAllText(path, "   \n\t  ");

        var manifest = ManifestService.Load(_tempDir);

        Assert.Empty(manifest.Images);
        Assert.Empty(manifest.PromptHashes);
    }

    [Fact]
    public void ManifestLoad_ArrayInsteadOfObject_ReturnsEmptyManifest()
    {
        var path = Path.Combine(_tempDir, BuildManifest.FileName);
        File.WriteAllText(path, """[{"promptHashes":{"core":"abc"}}]""");

        var manifest = ManifestService.Load(_tempDir);

        Assert.Empty(manifest.Images);
        Assert.Empty(manifest.PromptHashes);
    }

    [Fact]
    public void ManifestLoad_PromptHashesWrongType_ReturnsEmptyManifest()
    {
        var path = Path.Combine(_tempDir, BuildManifest.FileName);
        File.WriteAllText(path, """{"promptHashes":"not-an-object"}""");

        var manifest = ManifestService.Load(_tempDir);

        Assert.Empty(manifest.Images);
        Assert.Empty(manifest.PromptHashes);
    }

    [Fact]
    public void ManifestLoad_BinaryGarbage_ReturnsEmptyManifest()
    {
        var path = Path.Combine(_tempDir, BuildManifest.FileName);
        File.WriteAllBytes(path, new byte[] { 0x00, 0x01, 0x02, 0xFF, 0xFE, 0xFD });

        var manifest = ManifestService.Load(_tempDir);

        Assert.Empty(manifest.Images);
        Assert.Empty(manifest.PromptHashes);
    }

    [Fact]
    public void ManifestLoad_NoFile_ReturnsEmptyManifest()
    {
        var manifest = ManifestService.Load(_tempDir);

        Assert.Empty(manifest.Images);
        Assert.Empty(manifest.PromptHashes);
    }

    // --- SidecarMerger.LoadSidecar: corrupt files ---

    [Fact]
    public void LoadSidecar_TruncatedJson_ThrowsCopilotAnalysisException()
    {
        var imagePath = Path.Combine(_tempDir, "meme.jpg");
        var sidecarDir = OutputPaths.GetSidecarDir(_tempDir);
        Directory.CreateDirectory(sidecarDir);
        var sidecarPath = Path.Combine(sidecarDir, "meme.jpg.json");
        File.WriteAllText(sidecarPath, """{"emojis":["😂"]""");

        var ex = Assert.Throws<CopilotAnalysisException>(
            () => SidecarMerger.LoadSidecar(imagePath, _tempDir));

        Assert.Contains("Failed to parse sidecar JSON", ex.Message);
    }

    [Fact]
    public void LoadSidecar_EmojisWrongType_ThrowsCopilotAnalysisException()
    {
        var imagePath = Path.Combine(_tempDir, "meme.jpg");
        var sidecarDir = OutputPaths.GetSidecarDir(_tempDir);
        Directory.CreateDirectory(sidecarDir);
        var sidecarPath = Path.Combine(sidecarDir, "meme.jpg.json");
        File.WriteAllText(sidecarPath, """{"emojis":"😂"}""");

        var ex = Assert.Throws<CopilotAnalysisException>(
            () => SidecarMerger.LoadSidecar(imagePath, _tempDir));

        Assert.Contains("Failed to parse sidecar JSON", ex.Message);
    }

    [Fact]
    public void LoadSidecar_NoSidecarFile_ReturnsNull()
    {
        var imagePath = Path.Combine(_tempDir, "nonexistent.jpg");

        var result = SidecarMerger.LoadSidecar(imagePath, _tempDir);

        Assert.Null(result);
    }

    [Fact]
    public void LoadSidecar_BinaryGarbage_ThrowsCopilotAnalysisException()
    {
        var imagePath = Path.Combine(_tempDir, "meme.jpg");
        var sidecarDir = OutputPaths.GetSidecarDir(_tempDir);
        Directory.CreateDirectory(sidecarDir);
        var sidecarPath = Path.Combine(sidecarDir, "meme.jpg.json");
        File.WriteAllBytes(sidecarPath, new byte[] { 0x00, 0x01, 0x02, 0xFF, 0xFE, 0xFD });

        var ex = Assert.Throws<CopilotAnalysisException>(
            () => SidecarMerger.LoadSidecar(imagePath, _tempDir));

        Assert.Contains("Failed to parse sidecar JSON", ex.Message);
    }

    [Fact]
    public void LoadSidecar_EmptyFile_ThrowsCopilotAnalysisException()
    {
        var imagePath = Path.Combine(_tempDir, "meme.jpg");
        var sidecarDir = OutputPaths.GetSidecarDir(_tempDir);
        Directory.CreateDirectory(sidecarDir);
        var sidecarPath = Path.Combine(sidecarDir, "meme.jpg.json");
        File.WriteAllText(sidecarPath, string.Empty);

        var ex = Assert.Throws<CopilotAnalysisException>(
            () => SidecarMerger.LoadSidecar(imagePath, _tempDir));

        Assert.Contains("Failed to parse sidecar JSON", ex.Message);
    }
}
