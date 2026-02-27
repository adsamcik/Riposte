using SixLabors.ImageSharp;
using SixLabors.ImageSharp.Formats;
using SixLabors.ImageSharp.Formats.Jpeg;
using SixLabors.ImageSharp.Formats.Png;
using SixLabors.ImageSharp.Formats.Webp;
using SixLabors.ImageSharp.Processing;

namespace RiposteCli.Services;

/// <summary>
/// Optimizes images by downscaling and re-encoding.
/// </summary>
public static class ImageOptimizer
{
    private const int DefaultMaxDimension = 1200;
    private const int DefaultQuality = 85;

    private static readonly HashSet<string> TransparentFormats = new(StringComparer.OrdinalIgnoreCase)
        { ".png", ".webp", ".gif" };

    /// <summary>
    /// Optimize for ZIP bundling: downscale and convert to WebP.
    /// </summary>
    public static string OptimizeForBundle(
        string imagePath,
        string outputDir,
        int maxDimension = DefaultMaxDimension,
        int quality = DefaultQuality)
    {
        var outputFileName = Path.GetFileNameWithoutExtension(imagePath) + ".webp";
        var outputPath = Path.Combine(outputDir, outputFileName);

        using var image = Image.Load(imagePath);
        ResizeIfNeeded(image, maxDimension);

        var encoder = new WebpEncoder
        {
            Quality = quality,
            FileFormat = WebpFileFormatType.Lossy,
        };
        image.Save(outputPath, encoder);

        return outputPath;
    }

    /// <summary>
    /// Optimize for API calls: downscale but preserve original format (PNG or JPEG).
    /// Returns the path to the downscaled file, or the original path if no resize was needed.
    /// </summary>
    public static string OptimizeForApi(
        string imagePath,
        string outputDir,
        int maxDimension = DefaultMaxDimension,
        int quality = DefaultQuality)
    {
        using var image = Image.Load(imagePath);

        if (image.Width <= maxDimension && image.Height <= maxDimension)
            return imagePath;

        // Preserve format: use PNG for formats that support transparency, JPEG otherwise
        var ext = Path.GetExtension(imagePath);
        var usePng = TransparentFormats.Contains(ext);
        var outExt = usePng ? ".png" : ".jpg";

        var outputFileName = Path.GetFileNameWithoutExtension(imagePath) + "_api" + outExt;
        var outputPath = Path.Combine(outputDir, outputFileName);

        ResizeIfNeeded(image, maxDimension);

        IImageEncoder encoder = usePng
            ? new PngEncoder { CompressionLevel = PngCompressionLevel.BestSpeed }
            : new JpegEncoder { Quality = quality };

        image.Save(outputPath, encoder);
        return outputPath;
    }

    /// <summary>
    /// Batch-optimize images for bundling (WebP), returning original path → optimized path.
    /// </summary>
    public static Dictionary<string, string> OptimizeBatchForBundle(
        IReadOnlyList<string> imagePaths,
        string outputDir,
        int maxDimension = DefaultMaxDimension,
        int quality = DefaultQuality,
        int concurrency = 4,
        Action<string, string>? onComplete = null)
    {
        return RunBatch(imagePaths, outputDir, OptimizeForBundle, maxDimension, quality, concurrency, onComplete);
    }

    /// <summary>
    /// Batch-optimize images for API calls (PNG/JPEG), returning original path → optimized path.
    /// </summary>
    public static Dictionary<string, string> OptimizeBatchForApi(
        IReadOnlyList<string> imagePaths,
        string outputDir,
        int maxDimension = DefaultMaxDimension,
        int quality = DefaultQuality,
        int concurrency = 4,
        Action<string, string>? onComplete = null)
    {
        return RunBatch(imagePaths, outputDir, OptimizeForApi, maxDimension, quality, concurrency, onComplete);
    }

    private static Dictionary<string, string> RunBatch(
        IReadOnlyList<string> imagePaths,
        string outputDir,
        Func<string, string, int, int, string> optimizeFunc,
        int maxDimension,
        int quality,
        int concurrency,
        Action<string, string>? onComplete)
    {
        var result = new Dictionary<string, string>(imagePaths.Count);
        var lockObj = new object();

        Parallel.ForEach(imagePaths, new ParallelOptions { MaxDegreeOfParallelism = concurrency }, imagePath =>
        {
            var optimized = optimizeFunc(imagePath, outputDir, maxDimension, quality);
            lock (lockObj)
            {
                result[imagePath] = optimized;
            }
            onComplete?.Invoke(imagePath, optimized);
        });

        return result;
    }

    private static void ResizeIfNeeded(Image image, int maxDimension)
    {
        if (image.Width > maxDimension || image.Height > maxDimension)
        {
            image.Mutate(x => x.Resize(new ResizeOptions
            {
                Mode = ResizeMode.Max,
                Size = new Size(maxDimension, maxDimension),
                Sampler = KnownResamplers.Lanczos3,
            }));
        }
    }
}
