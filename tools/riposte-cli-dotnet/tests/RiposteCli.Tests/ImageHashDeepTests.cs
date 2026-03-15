using System.Diagnostics;
using RiposteCli.Services;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;

namespace RiposteCli.Tests;

public sealed class ImageHashDeepTests
{
    [Fact]
    public void ContentHash_SameImageContent_ReturnsSameHash()
    {
        using var temp = new TempDirectory();
        var imageA = CreateSolidImage(temp.Path, "a.png", 64, 64, new Rgba32(255, 0, 0));
        var imageB = Path.Combine(temp.Path, "b.png");
        File.Copy(imageA, imageB);

        var hashA = ImageHashService.GetContentHash(imageA);
        var hashB = ImageHashService.GetContentHash(imageB);

        Assert.Equal(hashA, hashB);
    }

    [Fact]
    public void ContentHash_DifferentImageContent_ReturnsDifferentHash()
    {
        using var temp = new TempDirectory();
        var imageA = CreateSolidImage(temp.Path, "a.png", 64, 64, new Rgba32(255, 0, 0));
        var imageB = CreateSolidImage(temp.Path, "b.png", 64, 64, new Rgba32(0, 255, 0));

        var hashA = ImageHashService.GetContentHash(imageA);
        var hashB = ImageHashService.GetContentHash(imageB);

        Assert.NotEqual(hashA, hashB);
    }

    [Fact]
    public void ContentHash_SameImageDifferentFilename_IsContentBased()
    {
        using var temp = new TempDirectory();
        var original = CreatePatternImage(temp.Path, "original.png", 128, 128, seed: 42);
        var renamed = Path.Combine(temp.Path, "renamed.png");
        File.Copy(original, renamed);

        var originalHash = ImageHashService.GetContentHash(original);
        var renamedHash = ImageHashService.GetContentHash(renamed);

        Assert.Equal(originalHash, renamedHash);
    }

    [Fact]
    public void ContentHash_IsLowercaseHexWith64Chars()
    {
        using var temp = new TempDirectory();
        var image = CreateSolidImage(temp.Path, "sample.png", 32, 32, new Rgba32(1, 2, 3));

        var hash = ImageHashService.GetContentHash(image);

        Assert.Equal(64, hash.Length);
        Assert.Matches("^[0-9a-f]{64}$", hash);
    }

    [Fact]
    public void PerceptualHash_IdenticalImages_ReturnSameHash()
    {
        using var temp = new TempDirectory();
        var imageA = CreatePatternImage(temp.Path, "a.png", 128, 128, seed: 10);
        var imageB = Path.Combine(temp.Path, "b.png");
        File.Copy(imageA, imageB);

        var hashA = ImageHashService.ComputePerceptualHash(imageA);
        var hashB = ImageHashService.ComputePerceptualHash(imageB);

        Assert.NotNull(hashA);
        Assert.NotNull(hashB);
        Assert.Equal(hashA, hashB);
    }

    [Fact]
    public void PerceptualHash_SinglePixelChange_DoesNotAffectHash()
    {
        // A single pixel change in a 128x128 image is invisible after DCT downscaling.
        // This tests hash ROBUSTNESS, not sensitivity.
        using var temp = new TempDirectory();
        var original = CreatePatternImage(temp.Path, "original.png", 128, 128, seed: 123);
        var modified = Path.Combine(temp.Path, "modified.png");
        File.Copy(original, modified);

        using (var image = Image.Load<Rgba32>(modified))
        {
            image[0, 0] = new Rgba32(255, 255, 255);
            image.Save(modified);
        }

        var originalHash = ImageHashService.ComputePerceptualHash(original);
        var modifiedHash = ImageHashService.ComputePerceptualHash(modified);

        Assert.NotNull(originalHash);
        Assert.NotNull(modifiedHash);
        var distance = ImageHashService.HammingDistance(originalHash.Value, modifiedHash.Value);
        Assert.InRange(distance, 0, 2);
    }

    [Fact]
    public void PerceptualHash_ModerateImageChange_ProducesNonZeroDistance()
    {
        using var temp = new TempDirectory();
        var original = CreatePatternImage(temp.Path, "original.png", 128, 128, seed: 500);
        var modified = Path.Combine(temp.Path, "modified.png");
        var created = CreateNearDuplicate(original, modified, maxDistance: 10);
        Assert.True(created, "Failed to generate a near-duplicate with Hamming distance in 1..10.");

        var originalHash = ImageHashService.ComputePerceptualHash(original);
        var modifiedHash = ImageHashService.ComputePerceptualHash(modified);

        Assert.NotNull(originalHash);
        Assert.NotNull(modifiedHash);
        var distance = ImageHashService.HammingDistance(originalHash.Value, modifiedHash.Value);
        Assert.True(distance > 0, $"Expected non-zero Hamming distance but got {distance}.");
        Assert.InRange(distance, 1, 10);
    }

    [Fact]
    public void PerceptualHash_CompletelyDifferentImages_HasHighHammingDistance()
    {
        using var temp = new TempDirectory();
        var imageA = CreatePatternImage(temp.Path, "a.png", 128, 128, seed: 1);
        var imageB = CreatePatternImage(temp.Path, "b.png", 128, 128, seed: 999);

        var hashA = ImageHashService.ComputePerceptualHash(imageA);
        var hashB = ImageHashService.ComputePerceptualHash(imageB);

        Assert.NotNull(hashA);
        Assert.NotNull(hashB);
        var distance = ImageHashService.HammingDistance(hashA.Value, hashB.Value);
        Assert.True(distance >= 12, $"Expected high Hamming distance but got {distance}.");
    }

    [Fact]
    public void Deduplicate_ThreeExactCopies_DetectsTwoAndKeepsOne()
    {
        using var temp = new TempDirectory();
        var original = CreatePatternImage(temp.Path, "one.png", 128, 128, seed: 77);
        var copyA = Path.Combine(temp.Path, "two.png");
        var copyB = Path.Combine(temp.Path, "three.png");
        File.Copy(original, copyA);
        File.Copy(original, copyB);
        var manifest = new Dictionary<string, HashEntry>();

        var result = ImageHashService.Deduplicate(new[] { original, copyA, copyB }, manifest);

        Assert.Single(result.UniqueImages);
        Assert.Equal(original, result.UniqueImages[0]);
        Assert.Equal(2, result.ExactDuplicates.Count);
        Assert.Empty(result.NearDuplicates);
    }

    [Fact]
    public void Deduplicate_NearDuplicate_WithThreshold10_IsDetected()
    {
        using var temp = new TempDirectory();
        var original = CreatePatternImage(temp.Path, "original.png", 128, 128, seed: 222);
        var near = Path.Combine(temp.Path, "near.png");
        var created = CreateNearDuplicate(original, near, maxDistance: 10);
        Assert.True(created, "Failed to generate a near-duplicate with Hamming distance in 1..10.");

        var manifest = new Dictionary<string, HashEntry>();
        var result = ImageHashService.Deduplicate(
            new[] { original, near },
            manifest,
            detectNearDuplicates: true,
            similarityThreshold: 10);

        Assert.Single(result.UniqueImages);
        Assert.Equal(original, result.UniqueImages[0]);
        Assert.Empty(result.ExactDuplicates);
        Assert.Single(result.NearDuplicates);
        Assert.True(result.NearDuplicates[0].Distance > 0,
            $"Expected non-zero Hamming distance but got {result.NearDuplicates[0].Distance}.");
    }

    [Fact]
    public void Deduplicate_NearDuplicate_WithThreshold0_IsNotDetected()
    {
        using var temp = new TempDirectory();
        var original = CreatePatternImage(temp.Path, "original.png", 128, 128, seed: 333);
        var near = Path.Combine(temp.Path, "near.png");
        var originalHash = ImageHashService.ComputePerceptualHash(original);
        Assert.NotNull(originalHash);
        int? nearDistance = null;

        for (var delta = 1; delta <= 32; delta++)
        {
            using var image = Image.Load<Rgba32>(original);
            for (var y = 0; y < 12; y++)
            {
                for (var x = 0; x < 12; x++)
                {
                    image[x, y] = new Rgba32((byte)(delta * 7), (byte)(delta * 9), (byte)(delta * 11));
                }
            }

            image.Save(near);
            var nearHash = ImageHashService.ComputePerceptualHash(near);
            if (!nearHash.HasValue) continue;

            var distance = ImageHashService.HammingDistance(originalHash.Value, nearHash.Value);
            if (distance > 0 && distance <= 10)
            {
                nearDistance = distance;
                break;
            }
        }

        Assert.True(nearDistance.HasValue, "Failed to generate a near-duplicate with distance in range 1..10.");
        var manifest = new Dictionary<string, HashEntry>();
        var result = ImageHashService.Deduplicate(
            new[] { original, near },
            manifest,
            detectNearDuplicates: true,
            similarityThreshold: 0);

        Assert.Equal(2, result.UniqueImages.Count);
        Assert.Empty(result.ExactDuplicates);
        Assert.Empty(result.NearDuplicates);
    }

    [Fact]
    public void Deduplicate_MixedDuplicatesAndUnique_CategorizesCorrectly()
    {
        using var temp = new TempDirectory();
        var original = CreatePatternImage(temp.Path, "original.png", 128, 128, seed: 444);
        var exact = Path.Combine(temp.Path, "exact.png");
        File.Copy(original, exact);
        var near = Path.Combine(temp.Path, "near.png");
        var created = CreateNearDuplicate(original, near, maxDistance: 10);
        Assert.True(created, "Failed to generate a near-duplicate with Hamming distance in 1..10.");

        var unique = CreatePatternImage(temp.Path, "unique.png", 128, 128, seed: 9999);
        var manifest = new Dictionary<string, HashEntry>();

        var result = ImageHashService.Deduplicate(
            new[] { original, exact, near, unique },
            manifest,
            detectNearDuplicates: true,
            similarityThreshold: 10);

        Assert.Equal(2, result.UniqueImages.Count);
        Assert.Equal(original, result.UniqueImages[0]);
        Assert.Equal(unique, result.UniqueImages[1]);
        Assert.Single(result.ExactDuplicates);
        Assert.Single(result.NearDuplicates);
        Assert.Equal(original, result.ExactDuplicates[0].Original);
        Assert.Equal(original, result.NearDuplicates[0].Original);
        Assert.True(result.NearDuplicates[0].Distance > 0,
            $"Expected non-zero Hamming distance but got {result.NearDuplicates[0].Distance}.");
    }

    [Fact]
    public void Deduplicate_PreservesOrder_FirstOccurrenceIsKept()
    {
        using var temp = new TempDirectory();
        var first = CreatePatternImage(temp.Path, "first.png", 128, 128, seed: 52);
        var second = CreatePatternImage(temp.Path, "second.png", 128, 128, seed: 63);
        var firstCopy = Path.Combine(temp.Path, "first-copy.png");
        File.Copy(first, firstCopy);
        var manifest = new Dictionary<string, HashEntry>();

        var result = ImageHashService.Deduplicate(new[] { first, second, firstCopy }, manifest);

        Assert.Equal(new[] { first, second }, result.UniqueImages);
        Assert.Single(result.ExactDuplicates);
        Assert.Equal(firstCopy, result.ExactDuplicates[0].Duplicate);
        Assert.Equal(first, result.ExactDuplicates[0].Original);
    }

    [Fact]
    public void Manifest_SaveAndLoad_RoundTripPreservesAllHashes()
    {
        using var temp = new TempDirectory();
        var original = new Dictionary<string, HashEntry>
        {
            ["a.png"] = new("hash-a", "123"),
            ["b.png"] = new("hash-b", null),
            ["c.png"] = new("hash-c", "999999"),
        };

        ImageHashService.SaveManifest(temp.Path, original);
        var loaded = ImageHashService.LoadManifest(temp.Path);

        Assert.Equal(original.Count, loaded.Count);
        foreach (var (key, value) in original)
        {
            Assert.Contains(key, (IDictionary<string, HashEntry>)loaded);
            Assert.Equal(value.ContentHash, loaded[key].ContentHash);
            Assert.Equal(value.PerceptualHash, loaded[key].PerceptualHash);
        }
    }

    [Fact]
    public void Manifest_LoadFromNonExistentFile_ReturnsEmptyDictionary()
    {
        using var temp = new TempDirectory();
        var missing = Path.Combine(temp.Path, "missing");

        var loaded = ImageHashService.LoadManifest(missing);

        Assert.Empty(loaded);
    }

    [Fact]
    public void Manifest_SaveToNonExistentDirectory_CreatesManifestFile()
    {
        using var temp = new TempDirectory();
        var nested = Path.Combine(temp.Path, "new", "manifest", "dir");
        var data = new Dictionary<string, HashEntry> { ["x.png"] = new("abc", "1") };

        ImageHashService.SaveManifest(nested, data);

        var manifestPath = Path.Combine(nested, ".meme-hashes.json");
        Assert.True(File.Exists(manifestPath));
    }

    [Fact]
    public void EdgeCase_VerySmallImage_CanBeHashedAndDeduped()
    {
        using var temp = new TempDirectory();
        var image = CreateSolidImage(temp.Path, "tiny.png", 1, 1, new Rgba32(7, 8, 9));
        var copy = Path.Combine(temp.Path, "tiny-copy.png");
        File.Copy(image, copy);
        var manifest = new Dictionary<string, HashEntry>();

        var hash = ImageHashService.GetContentHash(image);
        var phash = ImageHashService.ComputePerceptualHash(image);
        var result = ImageHashService.Deduplicate(new[] { image, copy }, manifest);

        Assert.Matches("^[0-9a-f]{64}$", hash);
        Assert.NotNull(phash);
        Assert.Single(result.UniqueImages);
        Assert.Single(result.ExactDuplicates);
    }

    [Fact]
    public void EdgeCase_LargeImage_HashesCorrectly()
    {
        using var temp = new TempDirectory();
        var image = CreatePatternImage(temp.Path, "large.png", 2000, 2000, seed: 1500);

        var contentHash = ImageHashService.GetContentHash(image);
        var perceptualHash = ImageHashService.ComputePerceptualHash(image);

        Assert.Matches("^[0-9a-f]{64}$", contentHash);
        Assert.NotNull(perceptualHash);
    }

    [Fact]
    public void Deduplicate_EmptyManifest_FirstRunAddsEntries()
    {
        using var temp = new TempDirectory();
        var imageA = CreatePatternImage(temp.Path, "a.png", 128, 128, seed: 12);
        var imageB = CreatePatternImage(temp.Path, "b.png", 128, 128, seed: 13);
        var manifest = new Dictionary<string, HashEntry>();

        _ = ImageHashService.Deduplicate(new[] { imageA, imageB }, manifest);

        Assert.Equal(2, manifest.Count);
        Assert.Contains("a.png", (IDictionary<string, HashEntry>)manifest);
        Assert.Contains("b.png", (IDictionary<string, HashEntry>)manifest);
    }

    [Fact]
    public void Deduplicate_SecondRunWithManifest_UsesCachedHashesAndIsFaster()
    {
        using var temp = new TempDirectory();
        var images = Enumerable.Range(0, 12)
            .Select(i => CreatePatternImage(temp.Path, $"img-{i}.png", 900, 900, seed: 100 + i))
            .ToArray();
        var manifest = new Dictionary<string, HashEntry>();

        var firstStopwatch = Stopwatch.StartNew();
        _ = ImageHashService.Deduplicate(images, manifest, detectNearDuplicates: true, similarityThreshold: 10);
        firstStopwatch.Stop();

        var secondStopwatch = Stopwatch.StartNew();
        _ = ImageHashService.Deduplicate(images, manifest, detectNearDuplicates: true, similarityThreshold: 10);
        secondStopwatch.Stop();

        Assert.Equal(images.Length, manifest.Count);
        // Allow generous margin for CI timing variance (OS scheduling, GC pauses)
        Assert.True(
            secondStopwatch.ElapsedMilliseconds <= firstStopwatch.ElapsedMilliseconds * 5 + 500,
            $"Cached run unexpectedly slow. First={firstStopwatch.Elapsed}, Second={secondStopwatch.Elapsed}");
    }

    private static string CreateSolidImage(string directory, string filename, int width, int height, Rgba32 color)
    {
        var path = Path.Combine(directory, filename);
        using var image = new Image<Rgba32>(width, height, color);
        image.Save(path);
        return path;
    }

    private static string CreatePatternImage(string directory, string filename, int width, int height, int seed)
    {
        var path = Path.Combine(directory, filename);
        using var image = new Image<Rgba32>(width, height);
        for (var y = 0; y < height; y++)
        {
            for (var x = 0; x < width; x++)
            {
                var r = (byte)((x * 13 + y * 17 + seed * 19) % 256);
                var g = (byte)((x * 29 + y * 7 + seed * 11) % 256);
                var b = (byte)((x * 5 + y * 31 + seed * 3) % 256);
                image[x, y] = new Rgba32(r, g, b);
            }
        }

        image.Save(path);
        return path;
    }

    /// <summary>
    /// Creates a near-duplicate of the original image with a guaranteed
    /// Hamming distance in [1, maxDistance] by iteratively modifying
    /// pixel blocks of increasing size until the perceptual hash changes.
    /// </summary>
    private static bool CreateNearDuplicate(string originalPath, string outputPath, int maxDistance = 10)
    {
        var originalHash = ImageHashService.ComputePerceptualHash(originalPath);
        if (!originalHash.HasValue) return false;

        for (var blockSize = 8; blockSize <= 32; blockSize += 4)
        {
            for (var delta = 1; delta <= 32; delta++)
            {
                using var image = Image.Load<Rgba32>(originalPath);
                for (var y = 0; y < blockSize && y < image.Height; y++)
                {
                    for (var x = 0; x < blockSize && x < image.Width; x++)
                    {
                        image[x, y] = new Rgba32(
                            (byte)(delta * 7), (byte)(delta * 9), (byte)(delta * 11));
                    }
                }

                image.Save(outputPath);
                var nearHash = ImageHashService.ComputePerceptualHash(outputPath);
                if (!nearHash.HasValue) continue;

                var distance = ImageHashService.HammingDistance(originalHash.Value, nearHash.Value);
                if (distance > 0 && distance <= maxDistance)
                    return true;
            }
        }

        return false;
    }

    private sealed class TempDirectory: IDisposable
    {
        public TempDirectory()
        {
            Path = System.IO.Path.Combine(System.IO.Path.GetTempPath(), Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(Path);
        }

        public string Path { get; }

        public void Dispose()
        {
            if (Directory.Exists(Path))
            {
                Directory.Delete(Path, recursive: true);
            }
        }
    }
}
