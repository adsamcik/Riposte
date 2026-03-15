using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;

namespace RiposteCli.Tests;

public class ImageOptimizerEdgeCaseTests : IDisposable
{
    private readonly string _tempDir;

    public ImageOptimizerEdgeCaseTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString());
        Directory.CreateDirectory(_tempDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempDir))
            Directory.Delete(_tempDir, recursive: true);
    }

    private string CreateTestImage(int width, int height, string fileName)
    {
        var path = Path.Combine(_tempDir, fileName);
        using var img = new Image<Rgba32>(width, height);
        img.Save(path);
        return path;
    }

    // --- Edge case 1: Image exactly at maxDimension is NOT resized for API ---

    [Theory]
    [InlineData(1200, 800)]
    [InlineData(800, 1200)]
    [InlineData(1200, 1200)]
    public void OptimizeForApi_ExactlyAtMaxDimension_ReturnsOriginalPath(int width, int height)
    {
        var input = CreateTestImage(width, height, "exact.png");

        var result = ImageOptimizer.OptimizeForApi(input, Path.Combine(_tempDir, "out"), maxDimension: 1200);

        Assert.Equal(input, result);
    }

    // --- Edge case 2: Image at maxDimension IS re-encoded to WebP for bundle ---

    [Fact]
    public void OptimizeForBundle_ExactlyAtMaxDimension_StillReencodesToWebP()
    {
        var input = CreateTestImage(1200, 800, "exact.png");

        var result = ImageOptimizer.OptimizeForBundle(input, Path.Combine(_tempDir, "out"), maxDimension: 1200);

        Assert.EndsWith(".webp", result, StringComparison.OrdinalIgnoreCase);
        Assert.True(File.Exists(result));
        using var output = Image.Load(result);
        Assert.Equal(1200, output.Width);
        Assert.Equal(800, output.Height);
    }

    // --- Edge case 3: Square image maintains 1:1 aspect ratio ---

    [Fact]
    public void OptimizeForBundle_SquareImage_MaintainsAspectRatio()
    {
        var input = CreateTestImage(2400, 2400, "square.png");

        var result = ImageOptimizer.OptimizeForBundle(input, Path.Combine(_tempDir, "out"), maxDimension: 1200);

        using var output = Image.Load(result);
        Assert.Equal(1200, output.Width);
        Assert.Equal(1200, output.Height);
    }

    // --- Edge case 4: Very wide panoramic image ---

    [Fact]
    public void OptimizeForBundle_VeryWideImage_ResizesCorrectly()
    {
        var input = CreateTestImage(3000, 100, "wide.png");

        var result = ImageOptimizer.OptimizeForBundle(input, Path.Combine(_tempDir, "out"), maxDimension: 1200);

        using var output = Image.Load(result);
        Assert.Equal(1200, output.Width);
        Assert.Equal(40, output.Height);
        Assert.True(output.Width <= 1200);
        Assert.True(output.Height <= 1200);
    }

    // --- Edge case 5: Very tall image ---

    [Fact]
    public void OptimizeForBundle_VeryTallImage_ResizesCorrectly()
    {
        var input = CreateTestImage(100, 3000, "tall.png");

        var result = ImageOptimizer.OptimizeForBundle(input, Path.Combine(_tempDir, "out"), maxDimension: 1200);

        using var output = Image.Load(result);
        Assert.Equal(40, output.Width);
        Assert.Equal(1200, output.Height);
        Assert.True(output.Width <= 1200);
        Assert.True(output.Height <= 1200);
    }

    // --- Edge case 6: Corrupt image throws meaningful exception ---

    [Fact]
    public void OptimizeForBundle_CorruptImage_ThrowsMeaningfulException()
    {
        var corruptPath = Path.Combine(_tempDir, "corrupt.png");
        File.WriteAllBytes(corruptPath, new byte[] { 0x00, 0x01, 0x02, 0x03, 0xFF });

        var ex = Assert.ThrowsAny<Exception>(() =>
            ImageOptimizer.OptimizeForBundle(corruptPath, Path.Combine(_tempDir, "out")));

        // ImageSharp should throw UnknownImageFormatException or InvalidImageContentException
        Assert.NotNull(ex.Message);
        Assert.False(string.IsNullOrWhiteSpace(ex.Message));
    }

    [Fact]
    public void OptimizeForApi_CorruptImage_ThrowsMeaningfulException()
    {
        var corruptPath = Path.Combine(_tempDir, "corrupt.jpg");
        File.WriteAllBytes(corruptPath, new byte[] { 0xFF, 0xD8, 0xFF, 0x00 }); // partial JPEG header

        var ex = Assert.ThrowsAny<Exception>(() =>
            ImageOptimizer.OptimizeForApi(corruptPath, Path.Combine(_tempDir, "out")));

        Assert.NotNull(ex.Message);
        Assert.False(string.IsNullOrWhiteSpace(ex.Message));
    }

    // --- Edge case 7: Output directory is created if it doesn't exist ---

    [Fact]
    public void OptimizeForBundle_OutputDirDoesNotExist_CreatesItAutomatically()
    {
        var input = CreateTestImage(800, 600, "test.png");
        var nestedOutput = Path.Combine(_tempDir, "nested", "deep", "output");
        Assert.False(Directory.Exists(nestedOutput));

        var result = ImageOptimizer.OptimizeForBundle(input, nestedOutput);

        Assert.True(Directory.Exists(nestedOutput));
        Assert.True(File.Exists(result));
    }

    [Fact]
    public void OptimizeForApi_OutputDirDoesNotExist_CreatesItAutomatically()
    {
        var input = CreateTestImage(2000, 1500, "test.png");
        var nestedOutput = Path.Combine(_tempDir, "nested", "deep", "output");
        Assert.False(Directory.Exists(nestedOutput));

        var result = ImageOptimizer.OptimizeForApi(input, nestedOutput, maxDimension: 1000);

        Assert.True(Directory.Exists(nestedOutput));
        Assert.True(File.Exists(result));
    }

    [Fact]
    public void OptimizeForApi_OutputDirDoesNotExist_SmallImage_StillCreatesDir()
    {
        // When image is small enough, OptimizeForApi returns the original path.
        // The directory should still be created (it may be needed later by callers).
        var input = CreateTestImage(400, 300, "small.png");
        var nestedOutput = Path.Combine(_tempDir, "nested", "output");
        Assert.False(Directory.Exists(nestedOutput));

        var result = ImageOptimizer.OptimizeForApi(input, nestedOutput, maxDimension: 1200);

        Assert.Equal(input, result);
        Assert.True(Directory.Exists(nestedOutput));
    }
}
