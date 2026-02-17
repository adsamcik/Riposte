using System.Numerics;
using System.Text.Json;
using RiposteCli.Models;

namespace RiposteCli.Services;

/// <summary>
/// Service for creating and managing JSON sidecar files.
/// </summary>
public static class SidecarService
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
    };

    private static readonly HashSet<string> SupportedExtensions = new(StringComparer.OrdinalIgnoreCase)
    {
        ".jpg", ".jpeg", ".png", ".webp", ".gif",
        ".bmp", ".tiff", ".tif", ".heic", ".heif",
        ".avif", ".jxl",
    };

    // Magic bytes for common image formats
    private static readonly (byte[] Magic, int Offset)[] ImageSignatures =
    [
        (new byte[] { 0xFF, 0xD8, 0xFF }, 0),                // JPEG
        (new byte[] { 0x89, 0x50, 0x4E, 0x47 }, 0),          // PNG
        ("GIF8"u8.ToArray(), 0),                               // GIF
        ("BM"u8.ToArray(), 0),                                 // BMP
        ("RIFF"u8.ToArray(), 0),                               // WebP (RIFF container)
        (new byte[] { 0x49, 0x49, 0x2A, 0x00 }, 0),          // TIFF LE
        (new byte[] { 0x4D, 0x4D, 0x00, 0x2A }, 0),          // TIFF BE
    ];

    /// <summary>
    /// Check if a file is a supported image format by extension and magic bytes.
    /// </summary>
    public static bool IsSupportedImage(string path)
    {
        var ext = Path.GetExtension(path);
        if (!SupportedExtensions.Contains(ext))
            return false;

        try
        {
            using var stream = File.OpenRead(path);
            var header = new byte[12];
            var bytesRead = stream.Read(header, 0, header.Length);
            if (bytesRead < 4) return false;

            foreach (var (magic, offset) in ImageSignatures)
            {
                if (offset + magic.Length <= bytesRead &&
                    header.AsSpan(offset, magic.Length).SequenceEqual(magic))
                    return true;
            }

            // HEIF/AVIF check: ftyp box at offset 4
            if (bytesRead >= 12 && header.AsSpan(4, 4).SequenceEqual("ftyp"u8))
                return true;

            // If extension matches but magic doesn't, still allow (for formats like JXL)
            return true;
        }
        catch
        {
            return false;
        }
    }

    /// <summary>
    /// Get all supported images in a folder, sorted by name.
    /// </summary>
    public static List<string> GetImagesInFolder(string folder)
    {
        return Directory.GetFiles(folder)
            .Where(f => IsSupportedImage(f))
            .OrderBy(f => f, StringComparer.Ordinal)
            .ToList();
    }

    /// <summary>
    /// Check if an image already has a JSON sidecar file.
    /// </summary>
    public static bool HasSidecar(string imagePath, string? outputDir = null)
    {
        outputDir ??= Path.GetDirectoryName(imagePath)!;
        var sidecarPath = Path.Combine(outputDir, Path.GetFileName(imagePath) + ".json");
        return File.Exists(sidecarPath);
    }

    /// <summary>
    /// Filter images based on processing mode.
    /// </summary>
    public static (List<string> ToProcess, int Skipped) FilterImagesByMode(
        IReadOnlyList<string> images,
        string outputDir,
        bool force = false)
    {
        if (force)
            return (images.ToList(), 0);

        var toProcess = new List<string>();
        var skipped = 0;
        foreach (var img in images)
        {
            if (HasSidecar(img, outputDir))
                skipped++;
            else
                toProcess.Add(img);
        }

        return (toProcess, skipped);
    }

    /// <summary>
    /// Create a metadata sidecar from analysis results.
    /// </summary>
    public static SidecarMetadata CreateMetadata(
        AnalysisResult result,
        string? primaryLanguage = null,
        string? contentHash = null)
    {
        return new SidecarMetadata
        {
            Emojis = result.Emojis,
            Title = result.Title,
            Description = result.Description,
            Tags = result.Tags,
            SearchPhrases = result.SearchPhrases,
            PrimaryLanguage = primaryLanguage,
            Localizations = result.Localizations,
            ContentHash = contentHash,
            BasedOn = result.BasedOn,
        };
    }

    /// <summary>
    /// Create a metadata sidecar from individual fields.
    /// </summary>
    public static SidecarMetadata CreateMetadata(
        List<string> emojis,
        string? title = null,
        string? description = null,
        List<string>? tags = null,
        List<string>? searchPhrases = null,
        string? primaryLanguage = null,
        Dictionary<string, LocalizedContent>? localizations = null,
        string? contentHash = null,
        string? basedOn = null)
    {
        return new SidecarMetadata
        {
            Emojis = emojis,
            Title = title,
            Description = description,
            Tags = tags,
            SearchPhrases = searchPhrases,
            PrimaryLanguage = primaryLanguage,
            Localizations = localizations,
            ContentHash = contentHash,
            BasedOn = basedOn,
        };
    }

    /// <summary>
    /// Write a JSON sidecar file for an image.
    /// </summary>
    public static string WriteSidecar(string imagePath, SidecarMetadata metadata, string? outputDir = null)
    {
        outputDir ??= Path.GetDirectoryName(imagePath)!;
        var sidecarPath = Path.Combine(outputDir, Path.GetFileName(imagePath) + ".json");
        var json = JsonSerializer.Serialize(metadata, JsonOptions);
        File.WriteAllText(sidecarPath, json);
        return sidecarPath;
    }
}
