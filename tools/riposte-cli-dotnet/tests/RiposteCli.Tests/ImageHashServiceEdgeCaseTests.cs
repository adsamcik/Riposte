using RiposteCli.Models;
using RiposteCli.Services;

namespace RiposteCli.Tests;

/// <summary>
/// Edge case tests for ImageHashService: manifest error handling, perceptual hash,
/// dedup with cached manifest, hamming distance boundaries.
/// </summary>
public class ImageHashServiceEdgeCaseTests : IDisposable
{
    private readonly string _tempDir;

    public ImageHashServiceEdgeCaseTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), $"riposte-hash-edge-{Guid.NewGuid()}");
        Directory.CreateDirectory(_tempDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempDir))
            Directory.Delete(_tempDir, true);
    }

    #region LoadManifest Error Handling

    [Fact]
    public void LoadManifest_NoFile_ReturnsEmpty()
    {
        var manifest = ImageHashService.LoadManifest(_tempDir);
        Assert.Empty(manifest);
    }

    [Fact]
    public void LoadManifest_MalformedJson_ReturnsEmpty()
    {
        File.WriteAllText(Path.Combine(_tempDir, ".meme-hashes.json"), "not valid json");
        var manifest = ImageHashService.LoadManifest(_tempDir);
        Assert.Empty(manifest);
    }

    [Fact]
    public void LoadManifest_EmptyFile_ReturnsEmpty()
    {
        File.WriteAllText(Path.Combine(_tempDir, ".meme-hashes.json"), "");
        var manifest = ImageHashService.LoadManifest(_tempDir);
        Assert.Empty(manifest);
    }

    [Fact]
    public void LoadManifest_EmptyObject_ReturnsEmpty()
    {
        File.WriteAllText(Path.Combine(_tempDir, ".meme-hashes.json"), "{}");
        var manifest = ImageHashService.LoadManifest(_tempDir);
        Assert.Empty(manifest);
    }

    [Fact]
    public void LoadManifest_MissingPhash_LoadsWithNull()
    {
        var json = """{"img.jpg": {"content_hash": "abc123"}}""";
        File.WriteAllText(Path.Combine(_tempDir, ".meme-hashes.json"), json);

        var manifest = ImageHashService.LoadManifest(_tempDir);
        Assert.Single(manifest);
        Assert.Equal("abc123", manifest["img.jpg"].ContentHash);
        Assert.Null(manifest["img.jpg"].PerceptualHash);
    }

    [Fact]
    public void LoadManifest_WithPhash_LoadsBoth()
    {
        var json = """{"img.jpg": {"content_hash": "abc", "phash": "12345"}}""";
        File.WriteAllText(Path.Combine(_tempDir, ".meme-hashes.json"), json);

        var manifest = ImageHashService.LoadManifest(_tempDir);
        Assert.Equal("abc", manifest["img.jpg"].ContentHash);
        Assert.Equal("12345", manifest["img.jpg"].PerceptualHash);
    }

    [Fact]
    public void LoadManifest_NullContentHash_LoadsEmpty()
    {
        var json = """{"img.jpg": {"content_hash": null}}""";
        File.WriteAllText(Path.Combine(_tempDir, ".meme-hashes.json"), json);

        var manifest = ImageHashService.LoadManifest(_tempDir);
        Assert.Equal("", manifest["img.jpg"].ContentHash);
    }

    #endregion

    #region SaveManifest

    [Fact]
    public void SaveManifest_CreatesFile()
    {
        var manifest = new Dictionary<string, HashEntry>
        {
            ["test.jpg"] = new("hash1", "phash1"),
        };

        ImageHashService.SaveManifest(_tempDir, manifest);

        var path = Path.Combine(_tempDir, ".meme-hashes.json");
        Assert.True(File.Exists(path));

        var json = File.ReadAllText(path);
        Assert.Contains("hash1", json);
        Assert.Contains("phash1", json);
    }

    [Fact]
    public void SaveManifest_NullPhash_OmittedInJson()
    {
        var manifest = new Dictionary<string, HashEntry>
        {
            ["test.jpg"] = new("hash1", null),
        };

        ImageHashService.SaveManifest(_tempDir, manifest);

        var json = File.ReadAllText(Path.Combine(_tempDir, ".meme-hashes.json"));
        Assert.Contains("content_hash", json);
        // phash should be null/omitted via WhenWritingNull
        Assert.DoesNotContain("\"phash\": \"", json);
    }

    [Fact]
    public void SaveManifest_OverwritesExisting()
    {
        var manifest1 = new Dictionary<string, HashEntry>
        {
            ["old.jpg"] = new("old_hash", null),
        };
        ImageHashService.SaveManifest(_tempDir, manifest1);

        var manifest2 = new Dictionary<string, HashEntry>
        {
            ["new.jpg"] = new("new_hash", null),
        };
        ImageHashService.SaveManifest(_tempDir, manifest2);

        var loaded = ImageHashService.LoadManifest(_tempDir);
        Assert.Single(loaded);
        Assert.True(loaded.ContainsKey("new.jpg"));
        Assert.False(loaded.ContainsKey("old.jpg"));
    }

    #endregion

    #region Hamming Distance Boundaries

    [Fact]
    public void HammingDistance_Identical_ReturnsZero()
    {
        Assert.Equal(0, ImageHashService.HammingDistance(0, 0));
        Assert.Equal(0, ImageHashService.HammingDistance(ulong.MaxValue, ulong.MaxValue));
        Assert.Equal(0, ImageHashService.HammingDistance(12345, 12345));
    }

    [Fact]
    public void HammingDistance_AllDifferent_Returns64()
    {
        Assert.Equal(64, ImageHashService.HammingDistance(0, ulong.MaxValue));
    }

    [Fact]
    public void HammingDistance_Symmetric()
    {
        Assert.Equal(
            ImageHashService.HammingDistance(0xFF00, 0x00FF),
            ImageHashService.HammingDistance(0x00FF, 0xFF00));
    }

    [Theory]
    [InlineData(0b0001UL, 0b0011UL, 1)] // One bit different
    [InlineData(0b0000UL, 0b1111UL, 4)] // Four bits different
    [InlineData(0b10101010UL, 0b01010101UL, 8)] // Eight bits different
    public void HammingDistance_KnownValues(ulong a, ulong b, int expected)
    {
        Assert.Equal(expected, ImageHashService.HammingDistance(a, b));
    }

    #endregion

    #region Dedup with Cached Manifest

    [Fact]
    public void Deduplicate_UsesManifestCache_SkipsHashing()
    {
        var img1 = CreateFile("a.jpg", "content_a"u8.ToArray());
        var img2 = CreateFile("b.jpg", "content_b"u8.ToArray());

        // Pre-populate manifest with known hashes
        var manifest = new Dictionary<string, HashEntry>
        {
            ["a.jpg"] = new("cached_hash_a", null),
            ["b.jpg"] = new("cached_hash_b", null),
        };

        var result = ImageHashService.Deduplicate([img1, img2], manifest, detectNearDuplicates: false);

        // Both unique (different cached hashes)
        Assert.Empty(result.ExactDuplicates);
        Assert.Equal(2, result.UniqueImages.Count);

        // Manifest should still have the cached values
        Assert.Equal("cached_hash_a", manifest["a.jpg"].ContentHash);
    }

    [Fact]
    public void Deduplicate_CachedExactDuplicates_Detected()
    {
        var img1 = CreateFile("a.jpg", "content_a"u8.ToArray());
        var img2 = CreateFile("b.jpg", "content_b"u8.ToArray());

        // Same content hash = exact duplicate
        var manifest = new Dictionary<string, HashEntry>
        {
            ["a.jpg"] = new("same_hash", null),
            ["b.jpg"] = new("same_hash", null),
        };

        var result = ImageHashService.Deduplicate([img1, img2], manifest, detectNearDuplicates: false);
        Assert.Single(result.ExactDuplicates);
    }

    [Fact]
    public void Deduplicate_NearDuplicatesDisabled_SkipsPhash()
    {
        var img1 = CreateFile("a.jpg", "content_a"u8.ToArray());
        var img2 = CreateFile("b.jpg", "content_b"u8.ToArray());

        var manifest = new Dictionary<string, HashEntry>();
        var result = ImageHashService.Deduplicate([img1, img2], manifest,
            detectNearDuplicates: false);

        Assert.Empty(result.NearDuplicates);
        // Manifest entries should NOT have perceptual hash
        Assert.Null(manifest["a.jpg"].PerceptualHash);
    }

    [Fact]
    public void Deduplicate_NewImages_AddedToManifest()
    {
        var img1 = CreateFile("new1.jpg", "c1"u8.ToArray());
        var img2 = CreateFile("new2.jpg", "c2"u8.ToArray());

        var manifest = new Dictionary<string, HashEntry>();
        ImageHashService.Deduplicate([img1, img2], manifest, detectNearDuplicates: false);

        Assert.Equal(2, manifest.Count);
        Assert.NotEmpty(manifest["new1.jpg"].ContentHash);
        Assert.NotEmpty(manifest["new2.jpg"].ContentHash);
    }

    [Fact]
    public void Deduplicate_TripleDuplicate_TwoMarkedAsDuplicates()
    {
        var content = "identical"u8.ToArray();
        var img1 = CreateFile("a.jpg", content);
        var img2 = CreateFile("b.jpg", content);
        var img3 = CreateFile("c.jpg", content);

        var manifest = new Dictionary<string, HashEntry>();
        var result = ImageHashService.Deduplicate([img1, img2, img3], manifest,
            detectNearDuplicates: false);

        Assert.Single(result.UniqueImages);
        Assert.Equal(2, result.ExactDuplicates.Count);
    }

    [Fact]
    public void Deduplicate_EmptyImageList_ReturnsEmpty()
    {
        var manifest = new Dictionary<string, HashEntry>();
        var result = ImageHashService.Deduplicate([], manifest, detectNearDuplicates: false);

        Assert.Empty(result.UniqueImages);
        Assert.Empty(result.ExactDuplicates);
        Assert.Empty(result.NearDuplicates);
    }

    #endregion

    #region ContentHash

    [Fact]
    public void GetContentHash_SameContent_SameHash()
    {
        var content = "test content"u8.ToArray();
        var path1 = CreateFile("a.bin", content);
        var path2 = CreateFile("b.bin", content);

        Assert.Equal(
            ImageHashService.GetContentHash(path1),
            ImageHashService.GetContentHash(path2));
    }

    [Fact]
    public void GetContentHash_DifferentContent_DifferentHash()
    {
        var path1 = CreateFile("a.bin", "content1"u8.ToArray());
        var path2 = CreateFile("b.bin", "content2"u8.ToArray());

        Assert.NotEqual(
            ImageHashService.GetContentHash(path1),
            ImageHashService.GetContentHash(path2));
    }

    [Fact]
    public void GetContentHash_Returns64CharHexString()
    {
        var path = CreateFile("test.bin", "data"u8.ToArray());
        var hash = ImageHashService.GetContentHash(path);
        Assert.Equal(64, hash.Length);
        Assert.True(hash.All(c => "0123456789abcdef".Contains(c)),
            "Hash should be lowercase hex");
    }

    #endregion

    #region ComputePerceptualHash

    [Fact]
    public void ComputePerceptualHash_InvalidImage_ReturnsNull()
    {
        var path = CreateFile("notimage.jpg", "this is not an image"u8.ToArray());
        var hash = ImageHashService.ComputePerceptualHash(path);
        Assert.Null(hash);
    }

    [Fact]
    public void ComputePerceptualHash_NonexistentFile_ReturnsNull()
    {
        var hash = ImageHashService.ComputePerceptualHash(Path.Combine(_tempDir, "missing.jpg"));
        Assert.Null(hash);
    }

    #endregion

    private string CreateFile(string name, byte[] content)
    {
        var path = Path.Combine(_tempDir, name);
        File.WriteAllBytes(path, content);
        return path;
    }
}
