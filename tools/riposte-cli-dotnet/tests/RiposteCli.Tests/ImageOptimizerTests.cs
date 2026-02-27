using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;

namespace RiposteCli.Tests;

public class ImageOptimizerTests : IDisposable
{
    private readonly string _tempDir;
    private readonly string _outputDir;

    public ImageOptimizerTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString());
        _outputDir = Path.Combine(_tempDir, "output");
        Directory.CreateDirectory(_tempDir);
        Directory.CreateDirectory(_outputDir);
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

    // --- OptimizeForApi ---

    [Fact]
    public void OptimizeForApi_LargerThanMax_DownscalesPreservingAspectRatio()
    {
        var input = CreateTestImage(2400, 1200, "wide.png");

        var result = ImageOptimizer.OptimizeForApi(input, _outputDir, maxDimension: 1200);

        using var output = Image.Load(result);
        Assert.Equal(1200, output.Width);
        Assert.Equal(600, output.Height);
    }

    [Fact]
    public void OptimizeForApi_SmallerThanMax_ReturnsOriginalPath()
    {
        var input = CreateTestImage(800, 600, "small.png");

        var result = ImageOptimizer.OptimizeForApi(input, _outputDir, maxDimension: 1200);

        Assert.Equal(input, result);
    }

    [Fact]
    public void OptimizeForApi_PngInput_ProducesPngOutput()
    {
        var input = CreateTestImage(2000, 2000, "transparent.png");

        var result = ImageOptimizer.OptimizeForApi(input, _outputDir, maxDimension: 1000);

        Assert.EndsWith(".png", result, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void OptimizeForApi_JpegInput_ProducesJpegOutput()
    {
        var input = CreateTestImage(2000, 2000, "photo.jpg");

        var result = ImageOptimizer.OptimizeForApi(input, _outputDir, maxDimension: 1000);

        Assert.EndsWith(".jpg", result, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void OptimizeForApi_OutputFileExists()
    {
        var input = CreateTestImage(2000, 1500, "exists.png");

        var result = ImageOptimizer.OptimizeForApi(input, _outputDir, maxDimension: 1000);

        Assert.True(File.Exists(result));
        Assert.StartsWith(_outputDir, result);
    }

    [Fact]
    public void OptimizeForApi_DownscaledImageFitsWithinMaxDimension()
    {
        var input = CreateTestImage(3000, 2000, "large.jpg");
        const int maxDim = 800;

        var result = ImageOptimizer.OptimizeForApi(input, _outputDir, maxDimension: maxDim);

        using var output = Image.Load(result);
        Assert.True(output.Width <= maxDim);
        Assert.True(output.Height <= maxDim);
    }

    // --- OptimizeForBundle ---

    [Theory]
    [InlineData("input.png")]
    [InlineData("input.jpg")]
    public void OptimizeForBundle_AlwaysConvertsToWebP(string fileName)
    {
        var input = CreateTestImage(800, 600, fileName);

        var result = ImageOptimizer.OptimizeForBundle(input, _outputDir, maxDimension: 1200);

        Assert.EndsWith(".webp", result, StringComparison.OrdinalIgnoreCase);
        Assert.True(File.Exists(result));
    }

    [Fact]
    public void OptimizeForBundle_LargerThanMax_Downscales()
    {
        var input = CreateTestImage(2400, 1600, "big.png");

        var result = ImageOptimizer.OptimizeForBundle(input, _outputDir, maxDimension: 1200);

        using var output = Image.Load(result);
        Assert.True(output.Width <= 1200);
        Assert.True(output.Height <= 1200);
    }

    [Fact]
    public void OptimizeForBundle_SmallerThanMax_ReencodesToWebP()
    {
        var input = CreateTestImage(400, 300, "tiny.png");

        var result = ImageOptimizer.OptimizeForBundle(input, _outputDir, maxDimension: 1200);

        Assert.EndsWith(".webp", result, StringComparison.OrdinalIgnoreCase);
        Assert.NotEqual(input, result);
        Assert.True(File.Exists(result));
    }

    [Fact]
    public void OptimizeForBundle_OutputFilenameHasWebpExtension()
    {
        var input = CreateTestImage(500, 500, "source.jpg");

        var result = ImageOptimizer.OptimizeForBundle(input, _outputDir, maxDimension: 1200);

        Assert.Equal(".webp", Path.GetExtension(result), ignoreCase: true);
        Assert.Equal("source.webp", Path.GetFileName(result));
    }

    // --- Batch operations ---

    [Fact]
    public void OptimizeBatchForApi_ProcessesAllImages_ReturnsCorrectMapping()
    {
        var inputs = new List<string>
        {
            CreateTestImage(2000, 1000, "batch1.png"),
            CreateTestImage(2000, 1000, "batch2.jpg"),
            CreateTestImage(500, 500, "batch3.png"),
        };

        var result = ImageOptimizer.OptimizeBatchForApi(inputs, _outputDir, maxDimension: 1000);

        Assert.Equal(3, result.Count);
        foreach (var input in inputs)
            Assert.True(result.ContainsKey(input));

        // Large images should produce new files
        Assert.NotEqual(inputs[0], result[inputs[0]]);
        Assert.NotEqual(inputs[1], result[inputs[1]]);
        // Small image returns original
        Assert.Equal(inputs[2], result[inputs[2]]);
    }

    [Fact]
    public void OptimizeBatchForBundle_ProcessesAllImages_ReturnsCorrectMapping()
    {
        var inputs = new List<string>
        {
            CreateTestImage(800, 600, "b1.png"),
            CreateTestImage(800, 600, "b2.jpg"),
        };

        var result = ImageOptimizer.OptimizeBatchForBundle(inputs, _outputDir, maxDimension: 1200);

        Assert.Equal(2, result.Count);
        foreach (var kvp in result)
        {
            Assert.True(result.ContainsKey(kvp.Key));
            Assert.EndsWith(".webp", kvp.Value, StringComparison.OrdinalIgnoreCase);
            Assert.True(File.Exists(kvp.Value));
        }
    }

    [Fact]
    public void OptimizeBatch_OnCompleteCallback_InvokedForEachImage()
    {
        var inputs = new List<string>
        {
            CreateTestImage(2000, 1000, "cb1.png"),
            CreateTestImage(2000, 1000, "cb2.png"),
            CreateTestImage(2000, 1000, "cb3.png"),
        };
        var completed = new System.Collections.Concurrent.ConcurrentBag<string>();

        ImageOptimizer.OptimizeBatchForApi(
            inputs,
            _outputDir,
            maxDimension: 1000,
            onComplete: (original, _) => completed.Add(original));

        Assert.Equal(inputs.Count, completed.Count);
        foreach (var input in inputs)
            Assert.Contains(input, completed);
    }

    [Fact]
    public void OptimizeBatchForApi_EmptyInput_ReturnsEmptyDictionary()
    {
        var result = ImageOptimizer.OptimizeBatchForApi(
            Array.Empty<string>(), _outputDir, maxDimension: 1200);

        Assert.Empty(result);
    }

    [Fact]
    public void OptimizeBatchForBundle_EmptyInput_ReturnsEmptyDictionary()
    {
        var result = ImageOptimizer.OptimizeBatchForBundle(
            Array.Empty<string>(), _outputDir, maxDimension: 1200);

        Assert.Empty(result);
    }
}
