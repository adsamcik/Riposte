using RiposteCli.Models;
using System.Text.Json;

namespace RiposteCli.Tests;

public class TypeMismatchTests
{
    public static IEnumerable<object[]> AnalysisResultMismatchCases()
    {
        yield return ["""{"emojis": "😂"}"""];
        yield return ["""{"emojis": 42}"""];
        yield return ["""{"emojis": {"a": 1}}"""];
        yield return ["""{"emojis": ["😂"], "title": 123}"""];
        yield return ["""{"emojis": ["😂"], "title": ["a","b"]}"""];
        yield return ["""{"emojis": ["😂"], "tags": "tag1,tag2"}"""];
        yield return ["""{"emojis": ["😂"], "emotions": "happy"}"""];
        yield return ["""{"emojis": ["😂"], "emotions": ["happy"]}"""];
        yield return ["""{"emojis": ["😂"], "emotions": {"primary": 1, "sentiment": "positive"}}"""];
        yield return ["""{"emojis": ["😂"], "emotions": {"primary": "joy", "secondary": "sad", "sentiment": "positive"}}"""];
        yield return ["""{"emojis": ["😂"], "localizations": [{"lang": "cs"}]}"""];
        yield return ["""{"emojis": ["😂"], "localizations": {"cs": "czech"}}"""];
    }

    [Theory]
    [MemberData(nameof(AnalysisResultMismatchCases))]
    public void ParseResponseContent_WhenTypeMismatch_ThrowsCopilotAnalysisException(string json)
    {
        var ex = Assert.Throws<CopilotAnalysisException>(() => CopilotService.ParseResponseContent(json));
        Assert.Contains("Failed to parse API response as JSON", ex.Message);
    }

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
}
