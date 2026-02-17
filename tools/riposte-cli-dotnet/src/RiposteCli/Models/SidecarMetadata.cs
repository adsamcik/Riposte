using System.Text.Json.Serialization;

namespace RiposteCli.Models;

/// <summary>
/// JSON sidecar metadata for a meme image (schema v1.3).
/// </summary>
public sealed class SidecarMetadata
{
    [JsonPropertyName("schemaVersion")]
    public string SchemaVersion { get; init; } = "1.3";

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
