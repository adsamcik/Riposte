using System.Text.Json.Serialization;

namespace RiposteCli.Models;

/// <summary>
/// Build manifest tracking prompt hashes and per-image build state.
/// Stored as .meme-build-manifest.json alongside sidecars.
/// </summary>
public sealed record BuildManifest
{
    public const string FileName = ".meme-build-manifest.json";
    public const string CurrentManifestVersion = "1.0";

    [JsonPropertyName("manifestVersion")]
    public string ManifestVersion { get; init; } = CurrentManifestVersion;

    [JsonPropertyName("schemaVersion")]
    public string SchemaVersion { get; init; } = "1.4";

    [JsonPropertyName("model")]
    public string Model { get; init; } = "gpt-5-mini";

    /// <summary>
    /// Current prompt hashes per field group.
    /// Keys: "core", "search", "cultural", "emotions", "localization:cs", etc.
    /// </summary>
    [JsonPropertyName("promptHashes")]
    public Dictionary<string, string> PromptHashes { get; init; } = new();

    /// <summary>
    /// Per-image build state keyed by image filename.
    /// </summary>
    [JsonPropertyName("images")]
    public Dictionary<string, ImageManifestEntry> Images { get; init; } = new();
}

/// <summary>
/// Build state for a single image in the manifest.
/// </summary>
public sealed class ImageManifestEntry
{
    [JsonPropertyName("contentHash")]
    public required string ContentHash { get; set; }

    [JsonPropertyName("schemaVersion")]
    public string SchemaVersion { get; set; } = "1.4";

    [JsonPropertyName("model")]
    public required string Model { get; set; }

    [JsonPropertyName("generatedAt")]
    public required string GeneratedAt { get; set; }

    /// <summary>
    /// Prompt hashes that were used to generate each field group for this image.
    /// Keys match the manifest's promptHashes keys.
    /// </summary>
    [JsonPropertyName("fieldHashes")]
    public Dictionary<string, string> FieldHashes { get; set; } = new();
}
