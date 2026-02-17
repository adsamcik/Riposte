using System.Text.Json.Serialization;

namespace RiposteCli.Models;

/// <summary>
/// Parsed AI analysis response for a meme image.
/// </summary>
public sealed class AnalysisResult
{
    [JsonPropertyName("emojis")]
    public required List<string> Emojis { get; init; }

    [JsonPropertyName("title")]
    public string? Title { get; init; }

    [JsonPropertyName("description")]
    public string? Description { get; init; }

    [JsonPropertyName("tags")]
    public List<string>? Tags { get; init; }

    [JsonPropertyName("searchPhrases")]
    public List<string>? SearchPhrases { get; init; }

    [JsonPropertyName("basedOn")]
    public string? BasedOn { get; init; }

    [JsonPropertyName("localizations")]
    public Dictionary<string, LocalizedContent>? Localizations { get; init; }
}
