using System.Text.Json;
using RiposteCli.Models;
using RiposteCli.Services;

namespace RiposteCli.Tests;

public class ImageHashServiceExtendedTests : IDisposable
{
    private readonly string _tempDir;

    public ImageHashServiceExtendedTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), $"riposte-hash-test-{Guid.NewGuid()}");
        Directory.CreateDirectory(_tempDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempDir))
            Directory.Delete(_tempDir, true);
    }

    #region GetContentHash

    [Fact]
    public void GetContentHash_Deterministic_SameFileReturnsIdenticalHash()
    {
        var path = CreateFile("test.bin", "deterministic content"u8.ToArray());

        var hash1 = ImageHashService.GetContentHash(path);
        var hash2 = ImageHashService.GetContentHash(path);

        Assert.Equal(hash1, hash2);
    }

    [Fact]
    public void GetContentHash_DifferentContent_DifferentHashes()
    {
        var path1 = CreateFile("a.bin", "content A"u8.ToArray());
        var path2 = CreateFile("b.bin", "content B"u8.ToArray());

        Assert.NotEqual(
            ImageHashService.GetContentHash(path1),
            ImageHashService.GetContentHash(path2));
    }

    [Fact]
    public void GetContentHash_KnownValue_MatchesExpectedSha256()
    {
        // SHA-256("hello world") = b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9
        var path = CreateFile("hello.bin", "hello world"u8.ToArray());
        var hash = ImageHashService.GetContentHash(path);
        Assert.Equal("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", hash);
    }

    [Fact]
    public void GetContentHash_EmptyFile_ReturnsValidHash()
    {
        var path = CreateFile("empty.bin", []);
        var hash = ImageHashService.GetContentHash(path);
        Assert.Equal(64, hash.Length);
        // SHA-256 of empty = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        Assert.Equal("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash);
    }

    [Fact]
    public void GetContentHash_LowerCaseHex()
    {
        var path = CreateFile("test.bin", [0xFF, 0xAB, 0xCD]);
        var hash = ImageHashService.GetContentHash(path);
        Assert.Equal(hash, hash.ToLowerInvariant());
    }

    #endregion

    #region ComputePerceptualHash

    [Fact]
    public void ComputePerceptualHash_ValidPng_ReturnsHash()
    {
        // Minimal 1x1 white PNG
        var pngData = Convert.FromBase64String(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==");
        var path = CreateFile("pixel.png", pngData);

        var hash = ImageHashService.ComputePerceptualHash(path);
        Assert.NotNull(hash);
    }

    [Fact]
    public void ComputePerceptualHash_InvalidFile_ReturnsNull()
    {
        var path = CreateFile("garbage.png", "not an image"u8.ToArray());
        var hash = ImageHashService.ComputePerceptualHash(path);
        Assert.Null(hash);
    }

    [Fact]
    public void ComputePerceptualHash_IdenticalImages_SameHash()
    {
        var pngData = Convert.FromBase64String(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==");
        var path1 = CreateFile("img1.png", pngData);
        var path2 = CreateFile("img2.png", pngData);

        var hash1 = ImageHashService.ComputePerceptualHash(path1);
        var hash2 = ImageHashService.ComputePerceptualHash(path2);

        Assert.NotNull(hash1);
        Assert.NotNull(hash2);
        Assert.Equal(hash1, hash2);
    }

    #endregion

    #region HammingDistance — Extended

    [Theory]
    [InlineData(0UL, 0UL, 0)]
    [InlineData(0UL, 1UL, 1)]
    [InlineData(0UL, 0xFUL, 4)]
    [InlineData(0UL, 0xFFUL, 8)]
    [InlineData(0UL, ulong.MaxValue, 64)]
    [InlineData(ulong.MaxValue, ulong.MaxValue, 0)]
    [InlineData(0xAAAAAAAAAAAAAAAAUL, 0x5555555555555555UL, 64)]
    public void HammingDistance_KnownValues(ulong a, ulong b, int expected)
    {
        Assert.Equal(expected, ImageHashService.HammingDistance(a, b));
    }

    [Fact]
    public void HammingDistance_IsSymmetric()
    {
        Assert.Equal(
            ImageHashService.HammingDistance(0x1234, 0x5678),
            ImageHashService.HammingDistance(0x5678, 0x1234));
    }

    #endregion

    #region Manifest Persistence

    [Fact]
    public void LoadManifest_CorruptedJson_ReturnsEmpty()
    {
        File.WriteAllText(Path.Combine(_tempDir, ".meme-hashes.json"), "not valid json {{{");
        var manifest = ImageHashService.LoadManifest(_tempDir);
        Assert.Empty(manifest);
    }

    [Fact]
    public void LoadManifest_EmptyJsonObject_ReturnsEmpty()
    {
        File.WriteAllText(Path.Combine(_tempDir, ".meme-hashes.json"), "{}");
        var manifest = ImageHashService.LoadManifest(_tempDir);
        Assert.Empty(manifest);
    }

    [Fact]
    public void SaveManifest_ThenLoad_RoundTrips()
    {
        var data = new Dictionary<string, HashEntry>
        {
            ["a.jpg"] = new("hash_a", "phash_a"),
            ["b.png"] = new("hash_b", null),
        };

        ImageHashService.SaveManifest(_tempDir, data);
        var loaded = ImageHashService.LoadManifest(_tempDir);

        Assert.Equal(2, loaded.Count);
        Assert.Equal("hash_a", loaded["a.jpg"].ContentHash);
        Assert.Equal("phash_a", loaded["a.jpg"].PerceptualHash);
        Assert.Equal("hash_b", loaded["b.png"].ContentHash);
        Assert.Null(loaded["b.png"].PerceptualHash);
    }

    [Fact]
    public void SaveManifest_OverwritesExisting()
    {
        var data1 = new Dictionary<string, HashEntry>
        {
            ["old.jpg"] = new("old_hash", null),
        };
        ImageHashService.SaveManifest(_tempDir, data1);

        var data2 = new Dictionary<string, HashEntry>
        {
            ["new.jpg"] = new("new_hash", "new_phash"),
        };
        ImageHashService.SaveManifest(_tempDir, data2);

        var loaded = ImageHashService.LoadManifest(_tempDir);
        Assert.Single(loaded);
        Assert.True(loaded.ContainsKey("new.jpg"));
        Assert.False(loaded.ContainsKey("old.jpg"));
    }

    [Fact]
    public void SaveManifest_WritesValidJson()
    {
        var data = new Dictionary<string, HashEntry>
        {
            ["test.jpg"] = new("abc", "123"),
        };
        ImageHashService.SaveManifest(_tempDir, data);

        var json = File.ReadAllText(Path.Combine(_tempDir, ".meme-hashes.json"));
        var doc = JsonDocument.Parse(json); // Should not throw
        Assert.True(doc.RootElement.TryGetProperty("test.jpg", out var entry));
        Assert.Equal("abc", entry.GetProperty("content_hash").GetString());
        Assert.Equal("123", entry.GetProperty("phash").GetString());
    }

    [Fact]
    public void SaveManifest_NullPhash_OmittedFromJson()
    {
        var data = new Dictionary<string, HashEntry>
        {
            ["test.jpg"] = new("abc", null),
        };
        ImageHashService.SaveManifest(_tempDir, data);

        var json = File.ReadAllText(Path.Combine(_tempDir, ".meme-hashes.json"));
        var doc = JsonDocument.Parse(json);
        var entry = doc.RootElement.GetProperty("test.jpg");
        Assert.Equal("abc", entry.GetProperty("content_hash").GetString());
        // phash should be null/omitted
        if (entry.TryGetProperty("phash", out var phash))
            Assert.Equal(JsonValueKind.Null, phash.ValueKind);
    }

    #endregion

    #region Deduplicate

    [Fact]
    public void Deduplicate_NoImages_ReturnsEmpty()
    {
        var manifest = new Dictionary<string, HashEntry>();
        var result = ImageHashService.Deduplicate([], manifest);

        Assert.Empty(result.UniqueImages);
        Assert.Empty(result.ExactDuplicates);
        Assert.Empty(result.NearDuplicates);
    }

    [Fact]
    public void Deduplicate_AllUnique_NoExactOrNear()
    {
        var a = CreateFile("a.jpg", "content_a"u8.ToArray());
        var b = CreateFile("b.jpg", "content_b"u8.ToArray());
        var manifest = new Dictionary<string, HashEntry>();

        var result = ImageHashService.Deduplicate([a, b], manifest,
            detectNearDuplicates: false);

        Assert.Equal(2, result.UniqueImages.Count);
        Assert.Empty(result.ExactDuplicates);
        Assert.Empty(result.NearDuplicates);
    }

    [Fact]
    public void Deduplicate_ExactDuplicates_Detected()
    {
        var content = "same content"u8.ToArray();
        var a = CreateFile("original.jpg", content);
        var b = CreateFile("copy.jpg", content);
        var manifest = new Dictionary<string, HashEntry>();

        var result = ImageHashService.Deduplicate([a, b], manifest,
            detectNearDuplicates: false);

        Assert.Single(result.UniqueImages);
        Assert.Single(result.ExactDuplicates);
        Assert.Equal(b, result.ExactDuplicates[0].Duplicate);
        Assert.Equal(a, result.ExactDuplicates[0].Original);
    }

    [Fact]
    public void Deduplicate_ThreeExactDuplicates_TwoDetected()
    {
        var content = "duplicate content"u8.ToArray();
        var a = CreateFile("first.jpg", content);
        var b = CreateFile("second.jpg", content);
        var c = CreateFile("third.jpg", content);
        var manifest = new Dictionary<string, HashEntry>();

        var result = ImageHashService.Deduplicate([a, b, c], manifest,
            detectNearDuplicates: false);

        Assert.Single(result.UniqueImages);
        Assert.Equal(2, result.ExactDuplicates.Count);
    }

    [Fact]
    public void Deduplicate_UsesCachedManifest_WhenAvailable()
    {
        var a = CreateFile("a.jpg", "content_a"u8.ToArray());
        var b = CreateFile("b.jpg", "content_b_different"u8.ToArray());

        // Pre-populate manifest with same hash for both (fake exact duplicate)
        var manifest = new Dictionary<string, HashEntry>
        {
            ["a.jpg"] = new("same_hash", null),
            ["b.jpg"] = new("same_hash", null),
        };

        var result = ImageHashService.Deduplicate([a, b], manifest,
            detectNearDuplicates: false);

        // Should detect as duplicate via cached manifest, despite different actual content
        Assert.Single(result.ExactDuplicates);
    }

    [Fact]
    public void Deduplicate_NearDuplicates_WithinThreshold()
    {
        // Use cached manifest with known phash values that differ by 2 bits
        var a = CreateFile("a.jpg", "content_a"u8.ToArray());
        var b = CreateFile("b.jpg", "content_b"u8.ToArray());

        var manifest = new Dictionary<string, HashEntry>
        {
            ["a.jpg"] = new("hash_a", "100"), // phash = 100
            ["b.jpg"] = new("hash_b", "103"), // phash = 103, hamming distance = 2
        };

        var result = ImageHashService.Deduplicate([a, b], manifest,
            detectNearDuplicates: true, similarityThreshold: 5);

        Assert.Empty(result.ExactDuplicates);
        Assert.Single(result.NearDuplicates);
        Assert.Equal(b, result.NearDuplicates[0].Duplicate);
    }

    [Fact]
    public void Deduplicate_NearDuplicates_AboveThreshold_NotDetected()
    {
        var a = CreateFile("a.jpg", "content_a"u8.ToArray());
        var b = CreateFile("b.jpg", "content_b"u8.ToArray());

        // phash values: 0 and 0xFF differ in 8 bits
        var manifest = new Dictionary<string, HashEntry>
        {
            ["a.jpg"] = new("hash_a", "0"),
            ["b.jpg"] = new("hash_b", "255"),
        };

        var result = ImageHashService.Deduplicate([a, b], manifest,
            detectNearDuplicates: true, similarityThreshold: 5);

        Assert.Empty(result.NearDuplicates);
        Assert.Equal(2, result.UniqueImages.Count);
    }

    [Fact]
    public void Deduplicate_DisableNearDetection_SkipsPerceptualHash()
    {
        var a = CreateFile("a.jpg", "content_a"u8.ToArray());
        var b = CreateFile("b.jpg", "content_b"u8.ToArray());

        // Same phash — would be near-duplicate if detection enabled
        var manifest = new Dictionary<string, HashEntry>
        {
            ["a.jpg"] = new("hash_a", "100"),
            ["b.jpg"] = new("hash_b", "100"),
        };

        var result = ImageHashService.Deduplicate([a, b], manifest,
            detectNearDuplicates: false);

        Assert.Empty(result.NearDuplicates);
        Assert.Equal(2, result.UniqueImages.Count);
    }

    [Fact]
    public void Deduplicate_ExactDuplicateTakesPriorityOverNear()
    {
        var content = "same"u8.ToArray();
        var a = CreateFile("a.jpg", content);
        var b = CreateFile("b.jpg", content);

        var manifest = new Dictionary<string, HashEntry>();

        var result = ImageHashService.Deduplicate([a, b], manifest,
            detectNearDuplicates: true);

        // Should be classified as exact, not near
        Assert.Single(result.ExactDuplicates);
        Assert.Empty(result.NearDuplicates);
    }

    [Fact]
    public void Deduplicate_UpdatesManifest_ForNewImages()
    {
        var a = CreateFile("new_image.jpg", "some content"u8.ToArray());
        var manifest = new Dictionary<string, HashEntry>();

        ImageHashService.Deduplicate([a], manifest, detectNearDuplicates: false);

        Assert.True(manifest.ContainsKey("new_image.jpg"));
        Assert.NotEmpty(manifest["new_image.jpg"].ContentHash);
    }

    [Fact]
    public void Deduplicate_OrderMatters_FirstImageKeptAsOriginal()
    {
        var content = "duplicate"u8.ToArray();
        var first = CreateFile("001_first.jpg", content);
        var second = CreateFile("002_second.jpg", content);
        var manifest = new Dictionary<string, HashEntry>();

        var result = ImageHashService.Deduplicate([first, second], manifest,
            detectNearDuplicates: false);

        Assert.Single(result.UniqueImages);
        Assert.Equal(first, result.UniqueImages[0]);
        Assert.Equal(second, result.ExactDuplicates[0].Duplicate);
        Assert.Equal(first, result.ExactDuplicates[0].Original);
    }

    #endregion

    private string CreateFile(string name, byte[] content)
    {
        var path = Path.Combine(_tempDir, name);
        File.WriteAllBytes(path, content);
        return path;
    }
}
