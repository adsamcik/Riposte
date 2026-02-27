using System.Text.Json.Serialization;

namespace RiposteCli.Models;

/// <summary>
/// JSON sidecar metadata for a meme image (schema v1.4).
/// </summary>
public sealed class SidecarMetadata
{
    [JsonPropertyName("schemaVersion")]
    public string SchemaVersion { get; init; } = "1.4";

    [JsonPropertyName("emojis")]
    public required List<string> Emojis { get; init; }

    [JsonPropertyName("createdAt")]
    public string CreatedAt { get; init; } = DateTimeOffset.UtcNow.ToString("o");

    [JsonPropertyName("appVersion")]
    public string AppVersion { get; init; } = $"cli-{Models.CliVersion.Current}";

    [JsonPropertyName("cliVersion")]
    public string CliToolVersion { get; init; } = Models.CliVersion.Current;

    [JsonPropertyName("title")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? Title { get; init; }

    [JsonPropertyName("description")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? Description { get; init; }

    [JsonPropertyName("tags")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public List<string>? Tags { get; init; }

    [JsonPropertyName("searchPhrases")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public List<string>? SearchPhrases { get; init; }

    [JsonPropertyName("primaryLanguage")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? PrimaryLanguage { get; init; }

    [JsonPropertyName("localizations")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public Dictionary<string, LocalizedContent>? Localizations { get; init; }

    [JsonPropertyName("contentHash")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? ContentHash { get; init; }

    [JsonPropertyName("basedOn")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? BasedOn { get; init; }

    [JsonPropertyName("emotions")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public EmotionMetadata? Emotions { get; init; }
}

/// <summary>
/// Structured emotion metadata for mood-based semantic search.
/// </summary>
public sealed class EmotionMetadata
{
    /// <summary>Primary emotion category (e.g., "humor", "sadness", "wholesome").</summary>
    [JsonPropertyName("primary")]
    public required string Primary { get; init; }

    /// <summary>Additional emotion labels that apply.</summary>
    [JsonPropertyName("secondary")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public List<string>? Secondary { get; init; }

    /// <summary>Overall sentiment: "positive", "negative", "neutral", or "mixed".</summary>
    [JsonPropertyName("sentiment")]
    public required string Sentiment { get; init; }

    /// <summary>Emotion intensity: "low", "medium", or "high".</summary>
    [JsonPropertyName("intensity")]
    public string Intensity { get; init; } = "medium";

    /// <summary>
    /// Natural language descriptions of when/how someone would use this meme.
    /// E.g., "when something unexpectedly funny happens", "me on Monday morning".
    /// </summary>
    [JsonPropertyName("memeUsage")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public List<string>? MemeUsage { get; init; }
}

public sealed class LocalizedContent
{
    [JsonPropertyName("title")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? Title { get; init; }

    [JsonPropertyName("description")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? Description { get; init; }

    [JsonPropertyName("tags")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public List<string>? Tags { get; init; }

    [JsonPropertyName("searchPhrases")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public List<string>? SearchPhrases { get; init; }
}

public static class CliVersion
{
    public const string Current = "1.0.0";
}
