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
        var bundleDir = OutputPaths.GetBundleDir(outputDir);
        var outputFileName = Path.GetFileNameWithoutExtension(imagePath) + ".webp";
        return OptimizeForBundleCore(imagePath, bundleDir, outputFileName, maxDimension, quality);
    }

    /// <summary>
    /// Core bundle optimization with explicit output filename.
    /// Used by batch optimization to provide collision-safe names.
    /// </summary>
    internal static string OptimizeForBundleCore(
        string imagePath,
        string outputDir,
        string outputFileName,
        int maxDimension,
        int quality)
    {
        Directory.CreateDirectory(outputDir);

        var outputPath = Path.Combine(outputDir, outputFileName);

        using var image = Image.Load(imagePath);
        ResizeIfNeeded(image, maxDimension);

        var encoder = new WebpEncoder
        {
            Quality = quality,
            FileFormat = WebpFileFormatType.Lossy,
        };
        SaveWithSharing(image, outputPath, encoder);

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
        var optimizedDir = OutputPaths.GetOptimizedDir(outputDir);
        Directory.CreateDirectory(optimizedDir);

        using var image = Image.Load(imagePath);

        if (image.Width <= maxDimension && image.Height <= maxDimension)
            return imagePath;

        // Preserve format: use PNG for formats that support transparency, JPEG otherwise
        var ext = Path.GetExtension(imagePath);
        var usePng = TransparentFormats.Contains(ext);
        var outExt = usePng ? ".png" : ".jpg";

        var outputFileName = Path.GetFileNameWithoutExtension(imagePath) + "_api" + outExt;
        var outputPath = Path.Combine(optimizedDir, outputFileName);

        ResizeIfNeeded(image, maxDimension);

        IImageEncoder encoder = usePng
            ? new PngEncoder { CompressionLevel = PngCompressionLevel.BestSpeed }
            : new JpegEncoder { Quality = quality };

        SaveWithSharing(image, outputPath, encoder);
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
        var bundleDir = OutputPaths.GetBundleDir(outputDir);
        var uniqueNames = ResolveUniqueWebpNames(imagePaths);
        var result = new Dictionary<string, string>(imagePaths.Count);
        var lockObj = new object();

        Parallel.ForEach(imagePaths, new ParallelOptions { MaxDegreeOfParallelism = concurrency }, imagePath =>
        {
            try
            {
                var optimized = OptimizeForBundleCore(imagePath, bundleDir, uniqueNames[imagePath], maxDimension, quality);
                lock (lockObj)
                {
                    result[imagePath] = optimized;
                }
                onComplete?.Invoke(imagePath, optimized);
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine($"Warning: failed to optimize {Path.GetFileName(imagePath)} for bundle: {ex.Message}");
            }
        });

        return result;
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
            try
            {
                var optimized = optimizeFunc(imagePath, outputDir, maxDimension, quality);
                lock (lockObj)
                {
                    result[imagePath] = optimized;
                }
                onComplete?.Invoke(imagePath, optimized);
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine($"Warning: failed to optimize {Path.GetFileName(imagePath)}: {ex.Message}");
            }
        });

        return result;
    }

    /// <summary>
    /// Pre-compute unique WebP output filenames, disambiguating images that share
    /// the same stem but have different source extensions (e.g. cat.png + cat.jpg).
    /// </summary>
    internal static Dictionary<string, string> ResolveUniqueWebpNames(IReadOnlyList<string> imagePaths)
    {
        var groups = imagePaths
            .GroupBy(p => Path.GetFileNameWithoutExtension(p), StringComparer.OrdinalIgnoreCase);

        var result = new Dictionary<string, string>(imagePaths.Count);

        foreach (var group in groups)
        {
            var paths = group.ToList();
            if (paths.Count == 1)
            {
                result[paths[0]] = group.Key + ".webp";
            }
            else
            {
                // Multiple source images would collide → include original extension in stem
                foreach (var path in paths)
                {
                    var stem = Path.GetFileNameWithoutExtension(path);
                    var ext = Path.GetExtension(path).TrimStart('.');
                    result[path] = $"{stem}_{ext}.webp";
                }
            }
        }

        return result;
    }

    /// <summary>
    /// Save image using a FileStream with FileShare.ReadWrite so cloud sync
    /// services (e.g. Proton Drive) holding the file open for reading don't block writes.
    /// Retries with exponential backoff if the file is still locked.
    /// </summary>
    private static void SaveWithSharing(Image image, string outputPath, IImageEncoder encoder)
    {
        const int maxRetries = 5;
        for (var attempt = 0; attempt <= maxRetries; attempt++)
        {
            try
            {
                using var stream = new FileStream(
                    outputPath, FileMode.Create, FileAccess.Write, FileShare.ReadWrite);
                image.Save(stream, encoder);
                return;
            }
            catch (IOException) when (attempt < maxRetries)
            {
                Thread.Sleep(200 * (1 << attempt)); // 200ms, 400ms, 800ms, 1.6s, 3.2s
            }
        }
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
