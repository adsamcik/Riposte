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
    /// Current image optimization/pipeline configuration.
    /// </summary>
    [JsonPropertyName("optimization")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public OptimizationConfig? Optimization { get; init; }

    /// <summary>
    /// Per-image build state keyed by image filename.
    /// </summary>
    [JsonPropertyName("images")]
    public Dictionary<string, ImageManifestEntry> Images { get; init; } = new();
}

/// <summary>
/// Tracks image optimization pipeline settings so changes trigger re-processing.
/// </summary>
public sealed record OptimizationConfig
{
    /// <summary>Max dimension for API-bound images (e.g., 1200).</summary>
    [JsonPropertyName("apiMaxDimension")]
    public int ApiMaxDimension { get; init; } = 1200;

    /// <summary>Format used for API images: "original" (preserves PNG/JPEG).</summary>
    [JsonPropertyName("apiFormat")]
    public string ApiFormat { get; init; } = "original";

    /// <summary>Max dimension for bundle images.</summary>
    [JsonPropertyName("bundleMaxDimension")]
    public int BundleMaxDimension { get; init; } = 1200;

    /// <summary>Format for bundle images: "webp".</summary>
    [JsonPropertyName("bundleFormat")]
    public string BundleFormat { get; init; } = "webp";

    /// <summary>Quality setting for lossy encoding (1-100).</summary>
    [JsonPropertyName("quality")]
    public int Quality { get; init; } = 85;

    /// <summary>
    /// Deterministic fingerprint of this config for quick comparison.
    /// </summary>
    public string Fingerprint() =>
        $"api:{ApiMaxDimension}:{ApiFormat}|bundle:{BundleMaxDimension}:{BundleFormat}|q:{Quality}";
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

    /// <summary>
    /// Fingerprint of the optimization config used when this image was processed.
    /// If null, the image was built before optimization tracking existed.
    /// </summary>
    [JsonPropertyName("optimizationFingerprint")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? OptimizationFingerprint { get; set; }

    /// <summary>
    /// Whether optimized API image exists for this image.
    /// </summary>
    [JsonPropertyName("hasApiOptimized")]
    public bool HasApiOptimized { get; set; }

    /// <summary>
    /// Whether optimized bundle (WebP) image exists for this image.
    /// </summary>
    [JsonPropertyName("hasBundleOptimized")]
    public bool HasBundleOptimized { get; set; }
}
