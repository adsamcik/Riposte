using System.Text.Json;
using RiposteCli.Models;

namespace RiposteCli.Services;

/// <summary>
/// Load and save the build manifest file.
/// </summary>
public static class ManifestService
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
    };

    /// <summary>
    /// Load a build manifest from the output directory, or create a new empty one.
    /// </summary>
    public static BuildManifest Load(string outputDir)
    {
        var path = Path.Combine(outputDir, BuildManifest.FileName);
        if (!File.Exists(path))
            return new BuildManifest();

        try
        {
            var json = File.ReadAllText(path);
            var manifest = JsonSerializer.Deserialize<BuildManifest>(json, JsonOptions) ?? new BuildManifest();
            return EnsureCaseInsensitiveKeys(manifest);
        }
        catch
        {
            return new BuildManifest();
        }
    }

    /// <summary>
    /// Ensure the Images dictionary uses case-insensitive key comparison.
    /// System.Text.Json always creates dictionaries with ordinal comparer during deserialization,
    /// but Windows filesystems are case-insensitive — "Photo.JPG" and "photo.jpg" are the same file.
    /// </summary>
    private static BuildManifest EnsureCaseInsensitiveKeys(BuildManifest manifest)
    {
        if (manifest.Images.Comparer == StringComparer.OrdinalIgnoreCase)
            return manifest;

        var ciImages = new Dictionary<string, ImageManifestEntry>(StringComparer.OrdinalIgnoreCase);
        foreach (var (key, value) in manifest.Images)
            ciImages.TryAdd(key, value);

        return manifest with { Images = ciImages };
    }

    /// <summary>
    /// Save a build manifest to the output directory.
    /// Uses atomic write (temp file + rename) to prevent corruption on crash.
    /// </summary>
    public static void Save(string outputDir, BuildManifest manifest)
    {
        var path = Path.Combine(outputDir, BuildManifest.FileName);
        var tempPath = path + $".{Guid.NewGuid():N}.tmp";
        var json = JsonSerializer.Serialize(manifest, JsonOptions);
        try
        {
            File.WriteAllText(tempPath, json);
            File.Move(tempPath, path, overwrite: true);
        }
        catch
        {
            try { File.Delete(tempPath); } catch { /* best-effort cleanup */ }
            throw;
        }
    }

    /// <summary>
    /// Update or create a manifest entry for an image after successful generation.
    /// <para>Thread-safety: This method mutates <paramref name="manifest"/>.Images directly.
    /// Callers MUST hold an external lock when invoking concurrently.</para>
    /// </summary>
    public static void RecordImageBuild(
        BuildManifest manifest,
        string imageFileName,
        string contentHash,
        string model,
        string schemaVersion,
        Dictionary<string, string> fieldHashes,
        string? optimizationFingerprint = null)
    {
        manifest.Images[imageFileName] = new ImageManifestEntry
        {
            ContentHash = contentHash,
            Model = model,
            SchemaVersion = schemaVersion,
            GeneratedAt = DateTimeOffset.UtcNow.ToString("o"),
            FieldHashes = new Dictionary<string, string>(fieldHashes),
            OptimizationFingerprint = optimizationFingerprint,
            HasApiOptimized = optimizationFingerprint is not null,
            HasBundleOptimized = false, // Bundle optimization is a separate step
        };
    }

    /// <summary>
    /// Update specific field hashes for an image after a partial rebuild.
    /// Preserves field hashes for groups that weren't regenerated.
    /// <para>Thread-safety: This method mutates <paramref name="manifest"/>.Images directly.
    /// Callers MUST hold an external lock when invoking concurrently.</para>
    /// </summary>
    public static void RecordPartialBuild(
        BuildManifest manifest,
        string imageFileName,
        string contentHash,
        string model,
        string schemaVersion,
        IReadOnlyList<string> affectedGroups,
        Dictionary<string, string> currentPromptHashes,
        string? optimizationFingerprint = null)
    {
        if (!manifest.Images.TryGetValue(imageFileName, out var entry))
        {
            entry = new ImageManifestEntry
            {
                ContentHash = contentHash,
                Model = model,
                SchemaVersion = schemaVersion,
                GeneratedAt = DateTimeOffset.UtcNow.ToString("o"),
                FieldHashes = new Dictionary<string, string>(),
            };
            manifest.Images[imageFileName] = entry;
        }

        entry.Model = model;
        entry.SchemaVersion = schemaVersion;
        entry.GeneratedAt = DateTimeOffset.UtcNow.ToString("o");
        if (optimizationFingerprint is not null)
        {
            entry.OptimizationFingerprint = optimizationFingerprint;
            entry.HasApiOptimized = true;
        }

        foreach (var group in affectedGroups)
        {
            if (currentPromptHashes.TryGetValue(group, out var hash))
            {
                entry.FieldHashes[group] = hash;
            }
        }
    }

    /// <summary>
    /// Record that bundle optimization was completed for an image.
    /// </summary>
    public static void RecordBundleOptimized(BuildManifest manifest, string imageFileName)
    {
        if (manifest.Images.TryGetValue(imageFileName, out var entry))
            entry.HasBundleOptimized = true;
    }
}
