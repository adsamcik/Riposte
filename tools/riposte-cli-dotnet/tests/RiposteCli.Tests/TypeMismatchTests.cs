using RiposteCli.Models;
using System.Text.Json;

namespace RiposteCli.Tests;

public class TypeMismatchTests
{
    // --- ParseResponseContent: type mismatches that cause JsonException wrapped in CopilotAnalysisException ---

    public static IEnumerable<object[]> AnalysisResultMismatchCases()
    {
        // emojis wrong type
        yield return ["""{"emojis": "😂"}"""];
        yield return ["""{"emojis": 42}"""];
        yield return ["""{"emojis": {"a": 1}}"""];
        yield return ["""{"emojis": [1, 2, 3]}"""];

        // title wrong type
        yield return ["""{"emojis": ["😂"], "title": 123}"""];
        yield return ["""{"emojis": ["😂"], "title": ["a","b"]}"""];

        // description wrong type
        yield return ["""{"emojis": ["😂"], "description": 456}"""];
        yield return ["""{"emojis": ["😂"], "description": false}"""];

        // tags wrong type
        yield return ["""{"emojis": ["😂"], "tags": "tag1,tag2"}"""];
        yield return ["""{"emojis": ["😂"], "tags": {"a": "b"}}"""];

        // searchPhrases wrong type
        yield return ["""{"emojis": ["😂"], "searchPhrases": "phrase"}"""];

        // basedOn wrong type
        yield return ["""{"emojis": ["😂"], "basedOn": ["meme1", "meme2"]}"""];
        yield return ["""{"emojis": ["😂"], "basedOn": 99}"""];

        // emotions wrong type
        yield return ["""{"emojis": ["😂"], "emotions": "happy"}"""];
        yield return ["""{"emojis": ["😂"], "emotions": ["happy"]}"""];

        // emotions with wrong field types
        yield return ["""{"emojis": ["😂"], "emotions": {"primary": 1, "sentiment": "positive"}}"""];
        yield return ["""{"emojis": ["😂"], "emotions": {"primary": "joy", "secondary": "sad", "sentiment": "positive"}}"""];
        yield return ["""{"emojis": ["😂"], "emotions": {"primary": "joy", "sentiment": 42}}"""];

        // localizations wrong type
        yield return ["""{"emojis": ["😂"], "localizations": [{"lang": "cs"}]}"""];
        yield return ["""{"emojis": ["😂"], "localizations": {"cs": "czech"}}"""];
        yield return ["""{"emojis": ["😂"], "localizations": {"cs": {"title": 123}}}"""];
    }

    [Theory]
    [MemberData(nameof(AnalysisResultMismatchCases))]
    public void ParseResponseContent_WhenTypeMismatch_ThrowsCopilotAnalysisException(string json)
    {
        var ex = Assert.Throws<CopilotAnalysisException>(() => CopilotService.ParseResponseContent(json));
        Assert.Contains("Failed to parse API response as JSON", ex.Message);
    }

    // --- ParseResponseContent: semantically invalid (valid JSON, wrong content) ---

    [Fact]
    public void ParseResponseContent_NullEmojis_ThrowsMissingEmojis()
    {
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent("""{"emojis": null, "title": "test"}"""));

        // This hits the "emojis is not { Count: > 0 }" branch, not the JSON parse branch
        Assert.Contains("emojis", ex.Message, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("Failed to parse", ex.Message);
    }

    [Fact]
    public void ParseResponseContent_EmptyEmojis_ThrowsMissingEmojis()
    {
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent("""{"emojis": [], "title": "test"}"""));

        Assert.Contains("emojis", ex.Message, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("Failed to parse", ex.Message);
    }

    [Fact]
    public void ParseResponseContent_MissingEmojisField_ThrowsMissingEmojis()
    {
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent("""{"title": "test", "description": "desc"}"""));

        Assert.Contains("emojis", ex.Message, StringComparison.OrdinalIgnoreCase);
    }

    // --- ParsePartialResponse: type mismatches ---

    public static IEnumerable<object[]> PartialResponseMismatchCases()
    {
        yield return ["""{"title": 123}"""];
        yield return ["""{"tags": "not-array"}"""];
        yield return ["""{"emotions": "happy"}"""];
        yield return ["""{"localizations": [1, 2]}"""];
    }

    [Theory]
    [MemberData(nameof(PartialResponseMismatchCases))]
    public void ParsePartialResponse_WhenTypeMismatch_ThrowsCopilotAnalysisException(string json)
    {
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParsePartialResponse(json, ["core"]));

        Assert.Contains("Failed to parse partial API response as JSON", ex.Message);
    }

    [Fact]
    public void ParsePartialResponse_ValidWithNullEmojis_Succeeds()
    {
        // Unlike ParseResponseContent, partial response allows null/missing emojis
        var result = CopilotService.ParsePartialResponse(
            """{"title": "test", "description": "desc"}""", ["core"]);

        Assert.Null(result.Emojis);
        Assert.Equal("test", result.Title);
    }

    // --- SidecarMetadata direct deserialization ---

    [Fact]
    public void SidecarMetadata_EmojisAsString_ThrowsJsonException()
    {
        const string json = """{"emojis": "test", "schemaVersion": "1.4"}""";
        Assert.Throws<JsonException>(() => JsonSerializer.Deserialize<SidecarMetadata>(json));
    }

    [Fact]
    public void SidecarMetadata_SchemaVersionAsNumber_ThrowsJsonException()
    {
        const string json = """{"emojis": ["😂"], "schemaVersion": 14}""";
        Assert.Throws<JsonException>(() => JsonSerializer.Deserialize<SidecarMetadata>(json));
    }

    [Fact]
    public void SidecarMetadata_TagsAsObject_ThrowsJsonException()
    {
        const string json = """{"emojis": ["😂"], "tags": {"a": "b"}}""";
        Assert.Throws<JsonException>(() => JsonSerializer.Deserialize<SidecarMetadata>(json));
    }

    [Fact]
    public void SidecarMetadata_EmotionsAsString_ThrowsJsonException()
    {
        const string json = """{"emojis": ["😂"], "emotions": "happy"}""";
        Assert.Throws<JsonException>(() => JsonSerializer.Deserialize<SidecarMetadata>(json));
    }

    [Fact]
    public void SidecarMetadata_LocalizationsAsArray_ThrowsJsonException()
    {
        const string json = """{"emojis": ["😂"], "localizations": [{"title": "a"}]}""";
        Assert.Throws<JsonException>(() => JsonSerializer.Deserialize<SidecarMetadata>(json));
    }

    [Fact]
    public void SidecarMetadata_DescriptionAsNumber_ThrowsJsonException()
    {
        const string json = """{"emojis": ["😂"], "description": 42}""";
        Assert.Throws<JsonException>(() => JsonSerializer.Deserialize<SidecarMetadata>(json));
    }

    // --- BuildManifest direct deserialization ---

    [Fact]
    public void BuildManifest_PromptHashesAsString_ThrowsJsonException()
    {
        const string json = """{"promptHashes": "hash"}""";
        Assert.Throws<JsonException>(() => JsonSerializer.Deserialize<BuildManifest>(json));
    }

    [Fact]
    public void BuildManifest_ImagesAsArray_ThrowsJsonException()
    {
        const string json = """{"images": []}""";
        Assert.Throws<JsonException>(() => JsonSerializer.Deserialize<BuildManifest>(json));
    }

    [Fact]
    public void BuildManifest_OptimizationAsString_ThrowsJsonException()
    {
        const string json = """{"optimization": "default"}""";
        Assert.Throws<JsonException>(() => JsonSerializer.Deserialize<BuildManifest>(json));
    }

    [Fact]
    public void BuildManifest_PromptHashValuesAsNumbers_ThrowsJsonException()
    {
        const string json = """{"promptHashes": {"core": 123}}""";
        Assert.Throws<JsonException>(() => JsonSerializer.Deserialize<BuildManifest>(json));
    }

    [Fact]
    public void BuildManifest_ManifestVersionAsNumber_ThrowsJsonException()
    {
        const string json = """{"manifestVersion": 1}""";
        Assert.Throws<JsonException>(() => JsonSerializer.Deserialize<BuildManifest>(json));
    }

    // --- EmotionMetadata direct deserialization ---

    [Fact]
    public void EmotionMetadata_SecondaryAsString_ThrowsJsonException()
    {
        const string json = """{"primary": "joy", "sentiment": "positive", "secondary": "sadness"}""";
        Assert.Throws<JsonException>(() => JsonSerializer.Deserialize<EmotionMetadata>(json));
    }

    [Fact]
    public void EmotionMetadata_MemeUsageAsString_ThrowsJsonException()
    {
        const string json = """{"primary": "joy", "sentiment": "positive", "memeUsage": "when happy"}""";
        Assert.Throws<JsonException>(() => JsonSerializer.Deserialize<EmotionMetadata>(json));
    }

    [Fact]
    public void EmotionMetadata_IntensityAsNumber_ThrowsJsonException()
    {
        const string json = """{"primary": "joy", "sentiment": "positive", "intensity": 5}""";
        Assert.Throws<JsonException>(() => JsonSerializer.Deserialize<EmotionMetadata>(json));
    }
}
