using System.Numerics;
using System.Security.Cryptography;
using System.Text.Json;
using CoenM.ImageHash;
using CoenM.ImageHash.HashAlgorithms;
using RiposteCli.Models;
using SixLabors.ImageSharp;

namespace RiposteCli.Services;

/// <summary>
/// Image hashing and deduplication service.
/// Provides SHA-256 content hashing and DCT-based perceptual hashing.
/// </summary>
public static class ImageHashService
{
    private const string HashManifestFilename = ".meme-hashes.json";
    private static readonly PerceptualHash PHashAlgorithm = new();

    /// <summary>
    /// Compute SHA-256 content hash of an image file.
    /// </summary>
    public static string GetContentHash(string imagePath)
    {
        using var stream = File.OpenRead(imagePath);
        var hashBytes = SHA256.HashData(stream);
        return Convert.ToHexString(hashBytes).ToLowerInvariant();
    }

    /// <summary>
    /// Compute DCT-based perceptual hash of an image.
    /// </summary>
    public static ulong? ComputePerceptualHash(string imagePath)
    {
        try
        {
            using var stream = File.OpenRead(imagePath);
            return PHashAlgorithm.Hash(stream);
        }
        catch
        {
            return null;
        }
    }

    /// <summary>
    /// Compute Hamming distance between two perceptual hashes.
    /// </summary>
    public static int HammingDistance(ulong hash1, ulong hash2)
    {
        var xor = hash1 ^ hash2;
        return BitOperations.PopCount(xor);
    }

    /// <summary>
    /// Load hash manifest from a directory.
    /// </summary>
    public static Dictionary<string, HashEntry> LoadManifest(string directory)
    {
        var manifestPath = Path.Combine(directory, HashManifestFilename);
        if (!File.Exists(manifestPath))
            return new Dictionary<string, HashEntry>();

        try
        {
            var json = File.ReadAllText(manifestPath);
            var raw = JsonSerializer.Deserialize<Dictionary<string, JsonElement>>(json);
            if (raw is null) return new Dictionary<string, HashEntry>();

            var manifest = new Dictionary<string, HashEntry>();
            foreach (var (key, value) in raw)
            {
                var contentHash = value.TryGetProperty("content_hash", out var ch) ? ch.GetString() ?? "" : "";
                var phashStr = value.TryGetProperty("phash", out var ph) ? ph.GetString() : null;
                manifest[key] = new HashEntry(contentHash, phashStr);
            }
            return manifest;
        }
        catch
        {
            return new Dictionary<string, HashEntry>();
        }
    }

    /// <summary>
    /// Save hash manifest to a directory.
    /// </summary>
    public static void SaveManifest(string directory, Dictionary<string, HashEntry> manifest)
    {
        var manifestPath = Path.Combine(directory, HashManifestFilename);
        var raw = new Dictionary<string, object>();
        foreach (var (key, entry) in manifest)
        {
            raw[key] = new { content_hash = entry.ContentHash, phash = entry.PerceptualHash };
        }

        var json = JsonSerializer.Serialize(raw, new JsonSerializerOptions
        {
            WriteIndented = true,
            DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
        });
        File.WriteAllText(manifestPath, json);
    }

    /// <summary>
    /// Deduplicate a list of images using content and perceptual hashing.
    /// </summary>
    public static DeduplicationResult Deduplicate(
        IReadOnlyList<string> images,
        Dictionary<string, HashEntry> manifest,
        bool detectNearDuplicates = true,
        int similarityThreshold = 10,
        bool verbose = false)
    {
        var unique = new List<string>();
        var exactDuplicates = new List<(string Duplicate, string Original)>();
        var nearDuplicates = new List<(string Duplicate, string Original, int Distance)>();
        var seenContent = new Dictionary<string, string>();
        var seenPhash = new Dictionary<ulong, string>();

        foreach (var imagePath in images)
        {
            var filename = Path.GetFileName(imagePath);
            string contentHash;
            ulong? phash = null;

            if (manifest.TryGetValue(filename, out var cached))
            {
                contentHash = cached.ContentHash;
                if (cached.PerceptualHash is not null && ulong.TryParse(cached.PerceptualHash, out var cachedPhash))
                    phash = cachedPhash;
            }
            else
            {
                contentHash = GetContentHash(imagePath);
                if (detectNearDuplicates)
                    phash = ComputePerceptualHash(imagePath);

                manifest[filename] = new HashEntry(contentHash, phash?.ToString());
            }

            // Check for exact duplicates
            if (seenContent.TryGetValue(contentHash, out var originalPath))
            {
                exactDuplicates.Add((imagePath, originalPath));
                continue;
            }

            // Check for near-duplicates via perceptual hash
            if (detectNearDuplicates && phash.HasValue)
            {
                var isNearDupe = false;
                foreach (var (existingPhash, existingPath) in seenPhash)
                {
                    var distance = HammingDistance(phash.Value, existingPhash);
                    if (distance <= similarityThreshold)
                    {
                        nearDuplicates.Add((imagePath, existingPath, distance));
                        isNearDupe = true;
                        break;
                    }
                }

                if (isNearDupe) continue;
                seenPhash[phash.Value] = imagePath;
            }

            seenContent[contentHash] = imagePath;
            unique.Add(imagePath);
        }

        return new DeduplicationResult
        {
            UniqueImages = unique,
            ExactDuplicates = exactDuplicates,
            NearDuplicates = nearDuplicates,
        };
    }
}

public record HashEntry(string ContentHash, string? PerceptualHash);
