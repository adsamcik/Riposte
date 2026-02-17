using RiposteCli.Services;

namespace RiposteCli.Tests;

public class ImageHashServiceTests
{
    [Fact]
    public void GetContentHash_ReturnsSha256()
    {
        var tempDir = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString());
        Directory.CreateDirectory(tempDir);
        try
        {
            var testFile = Path.Combine(tempDir, "test.bin");
            File.WriteAllBytes(testFile, "hello world"u8.ToArray());

            var result = ImageHashService.GetContentHash(testFile);

            Assert.IsType<string>(result);
            Assert.Equal(64, result.Length); // SHA-256 hex length
        }
        finally
        {
            Directory.Delete(tempDir, true);
        }
    }

    [Fact]
    public void LoadManifest_EmptyDirectory_ReturnsEmpty()
    {
        var tempDir = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString());
        Directory.CreateDirectory(tempDir);
        try
        {
            var manifest = ImageHashService.LoadManifest(tempDir);
            Assert.Empty(manifest);
        }
        finally
        {
            Directory.Delete(tempDir, true);
        }
    }

    [Fact]
    public void SaveAndLoadManifest_RoundTrip()
    {
        var tempDir = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString());
        Directory.CreateDirectory(tempDir);
        try
        {
            var data = new Dictionary<string, HashEntry>
            {
                ["image.jpg"] = new("abc123", "def456"),
            };

            ImageHashService.SaveManifest(tempDir, data);
            var loaded = ImageHashService.LoadManifest(tempDir);

            Assert.Single(loaded);
            Assert.Equal("abc123", loaded["image.jpg"].ContentHash);
            Assert.Equal("def456", loaded["image.jpg"].PerceptualHash);
        }
        finally
        {
            Directory.Delete(tempDir, true);
        }
    }

    [Fact]
    public void HammingDistance_IdenticalHashes_ReturnsZero()
    {
        Assert.Equal(0, ImageHashService.HammingDistance(0xABCD, 0xABCD));
    }

    [Fact]
    public void HammingDistance_DifferentHashes_ReturnsCorrectCount()
    {
        // 0x0 and 0x1 differ in 1 bit
        Assert.Equal(1, ImageHashService.HammingDistance(0, 1));
        // 0x0 and 0xF differ in 4 bits
        Assert.Equal(4, ImageHashService.HammingDistance(0, 0xF));
    }
}
