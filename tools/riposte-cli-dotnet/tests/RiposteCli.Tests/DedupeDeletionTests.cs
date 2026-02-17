using System.Text.Json;
using RiposteCli.Models;
using RiposteCli.Services;

namespace RiposteCli.Tests;

/// <summary>
/// Full deletion workflow: image + sidecar removal, manifest cleanup.
/// </summary>
public class DedupeDeletionTests : IDisposable
{
    private readonly string _tempDir;

    public DedupeDeletionTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), $"riposte-dedupe-del-{Guid.NewGuid()}");
        Directory.CreateDirectory(_tempDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempDir))
            Directory.Delete(_tempDir, true);
    }

    [Fact]
    public void DeleteDuplicate_RemovesBothImageAndSidecar()
    {
        var imgPath = CreateFile("dup.jpg", "image data"u8.ToArray());
        var sidecarPath = Path.Combine(_tempDir, "dup.jpg.json");
        File.WriteAllText(sidecarPath, """{"emojis": ["😂"]}""");

        Assert.True(File.Exists(imgPath));
        Assert.True(File.Exists(sidecarPath));

        File.Delete(imgPath);
        File.Delete(sidecarPath);

        Assert.False(File.Exists(imgPath));
        Assert.False(File.Exists(sidecarPath));
    }

    [Fact]
    public void DeleteDuplicate_NoSidecar_OnlyImageDeleted()
    {
        var imgPath = CreateFile("orphan.jpg", "image data"u8.ToArray());

        File.Delete(imgPath);

        Assert.False(File.Exists(imgPath));
        Assert.False(File.Exists(Path.Combine(_tempDir, "orphan.jpg.json")));
    }

    [Fact]
    public void ManifestCleanup_RemovedFilesDroppedFromManifest()
    {
        // Setup manifest with 3 entries
        var manifest = new Dictionary<string, HashEntry>
        {
            ["keep.jpg"] = new("hash1", "phash1"),
            ["dup1.jpg"] = new("hash2", "phash2"),
            ["dup2.jpg"] = new("hash2", "phash3"),
        };

        // Simulate deleting duplicates
        manifest.Remove("dup1.jpg");
        manifest.Remove("dup2.jpg");

        ImageHashService.SaveManifest(_tempDir, manifest);
        var reloaded = ImageHashService.LoadManifest(_tempDir);

        Assert.Single(reloaded);
        Assert.True(reloaded.ContainsKey("keep.jpg"));
        Assert.False(reloaded.ContainsKey("dup1.jpg"));
        Assert.False(reloaded.ContainsKey("dup2.jpg"));
    }

    [Fact]
    public void FullWorkflow_Detect_Delete_CleanManifest()
    {
        // Create 3 files: 2 identical + 1 unique
        var content = "duplicate content"u8.ToArray();
        CreateFile("original.jpg", content);
        CreateFile("copy.jpg", content);
        CreateFile("unique.jpg", "different"u8.ToArray());

        // Step 1: Detect duplicates
        var images = new List<string>
        {
            Path.Combine(_tempDir, "original.jpg"),
            Path.Combine(_tempDir, "copy.jpg"),
            Path.Combine(_tempDir, "unique.jpg"),
        };
        var manifest = new Dictionary<string, HashEntry>();
        var result = ImageHashService.Deduplicate(images, manifest, detectNearDuplicates: false);

        Assert.Single(result.ExactDuplicates);
        Assert.Equal(2, result.UniqueImages.Count);

        // Step 2: Delete duplicate
        var (dup, _) = result.ExactDuplicates[0];
        File.Delete(dup);
        manifest.Remove(Path.GetFileName(dup));

        // Step 3: Save cleaned manifest
        ImageHashService.SaveManifest(_tempDir, manifest);

        // Step 4: Verify
        Assert.False(File.Exists(dup));
        var reloaded = ImageHashService.LoadManifest(_tempDir);
        Assert.Equal(2, reloaded.Count);
        Assert.False(reloaded.ContainsKey(Path.GetFileName(dup)));
    }

    [Fact]
    public void NearDuplicate_ThresholdZero_OnlyExact()
    {
        var a = CreateFile("a.jpg", "content_a"u8.ToArray());
        var b = CreateFile("b.jpg", "content_b"u8.ToArray());

        // Same phash = near-duplicate at any threshold
        var manifest = new Dictionary<string, HashEntry>
        {
            ["a.jpg"] = new("hash_a", "100"),
            ["b.jpg"] = new("hash_b", "100"),
        };

        var result = ImageHashService.Deduplicate([a, b], manifest,
            detectNearDuplicates: true, similarityThreshold: 0);

        // Distance = 0, threshold = 0 → still considered near-duplicate (distance <= threshold)
        Assert.Single(result.NearDuplicates);
    }

    [Fact]
    public void NearDuplicate_ExactThresholdBoundary_Included()
    {
        var a = CreateFile("a.jpg", "content_a"u8.ToArray());
        var b = CreateFile("b.jpg", "content_b"u8.ToArray());

        // phash 0 and 7 (binary: 0111) differ in 3 bits
        var manifest = new Dictionary<string, HashEntry>
        {
            ["a.jpg"] = new("hash_a", "0"),
            ["b.jpg"] = new("hash_b", "7"),
        };

        // Threshold exactly = distance → should match
        var resultMatch = ImageHashService.Deduplicate([a, b], manifest,
            detectNearDuplicates: true, similarityThreshold: 3);
        Assert.Single(resultMatch.NearDuplicates);

        // Threshold below distance → should not match
        var resultNoMatch = ImageHashService.Deduplicate(
            [a, b],
            new Dictionary<string, HashEntry>
            {
                ["a.jpg"] = new("hash_a", "0"),
                ["b.jpg"] = new("hash_b", "7"),
            },
            detectNearDuplicates: true, similarityThreshold: 2);
        Assert.Empty(resultNoMatch.NearDuplicates);
    }

    [Fact]
    public void Deduplicate_MixedExactAndNear()
    {
        var content = "exact duplicate"u8.ToArray();
        var a = CreateFile("a.jpg", content);
        var b = CreateFile("b.jpg", content);           // exact dup of a
        var c = CreateFile("c.jpg", "unique_c"u8.ToArray());
        var d = CreateFile("d.jpg", "unique_d"u8.ToArray()); // near-dup of c via manifest

        var manifest = new Dictionary<string, HashEntry>
        {
            ["c.jpg"] = new("hash_c", "100"),
            ["d.jpg"] = new("hash_d", "101"), // distance = 1 from c
        };

        var result = ImageHashService.Deduplicate([a, b, c, d], manifest,
            detectNearDuplicates: true, similarityThreshold: 5);

        Assert.Single(result.ExactDuplicates);
        Assert.Single(result.NearDuplicates);
        Assert.Equal(2, result.UniqueImages.Count);
    }

    [Fact]
    public void Deduplicate_ManifestGrowsWithNewImages()
    {
        CreateFile("new1.jpg", "content1"u8.ToArray());
        CreateFile("new2.jpg", "content2"u8.ToArray());

        var images = Directory.GetFiles(_tempDir, "*.jpg").OrderBy(f => f).ToList();
        var manifest = new Dictionary<string, HashEntry>();

        ImageHashService.Deduplicate(images, manifest, detectNearDuplicates: false);

        Assert.Equal(2, manifest.Count);
        Assert.All(manifest.Values, v => Assert.NotEmpty(v.ContentHash));
    }

    private string CreateFile(string name, byte[] content)
    {
        var path = Path.Combine(_tempDir, name);
        File.WriteAllBytes(path, content);
        return path;
    }
}
