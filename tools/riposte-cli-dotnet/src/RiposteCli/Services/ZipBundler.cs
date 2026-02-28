using System.IO.Compression;
using RiposteCli.Models;
using Spectre.Console;

namespace RiposteCli.Services;

/// <summary>
/// ZIP bundle creation modes.
/// </summary>
public enum ZipMode
{
    /// <summary>Bundle ALL images with sidecars (complete collection).</summary>
    Full,

    /// <summary>Bundle only images that were new or changed in this run (delta/patch bundle).</summary>
    Patch,
}

/// <summary>
/// Result of a ZIP bundle operation.
/// </summary>
public sealed class ZipBundleResult
{
    public required string ZipPath { get; init; }
    public required int ImageCount { get; init; }
    public required ZipMode Mode { get; init; }
    public required List<string> BundledImagePaths { get; init; }
}

/// <summary>
/// Creates .meme.zip bundles compatible with the Riposte Android app.
/// Supports full bundles (all images) and patch bundles (only changed/new).
/// </summary>
public static class ZipBundler
{
    /// <summary>
    /// Create a ZIP bundle based on the specified mode.
    /// </summary>
    public static ZipBundleResult CreateBundle(
        ZipMode mode,
        DirectoryInfo folder,
        string outputDir,
        IReadOnlyList<string> allImages,
        IReadOnlyList<ImageRebuildPlan> plans,
        IReadOnlyList<(string Image, string Sidecar)> processed,
        Dictionary<string, string>? bundleOptimizedMap,
        BuildManifest manifest,
        bool verbose = false)
    {
        var zipSuffix = mode == ZipMode.Patch ? ".patch.meme.zip" : ".meme.zip";
        var zipPath = Path.Combine(folder.Parent!.FullName, $"{folder.Name}{zipSuffix}");
        if (File.Exists(zipPath))
            File.Delete(zipPath);

        var imagesToBundle = SelectImagesForBundle(mode, allImages, outputDir, plans, processed, manifest);

        if (imagesToBundle.Count == 0)
        {
            return new ZipBundleResult
            {
                ZipPath = zipPath,
                ImageCount = 0,
                Mode = mode,
                BundledImagePaths = [],
            };
        }

        // Optimize images for bundle if not already done
        if (bundleOptimizedMap is null)
        {
            bundleOptimizedMap = ImageOptimizer.OptimizeBatchForBundle(imagesToBundle, outputDir);
        }
        else
        {
            // Optimize any images not already in the map
            var missing = imagesToBundle.Where(img => !bundleOptimizedMap.ContainsKey(img)).ToList();
            if (missing.Count > 0)
            {
                var extra = ImageOptimizer.OptimizeBatchForBundle(missing, outputDir);
                foreach (var kv in extra)
                    bundleOptimizedMap[kv.Key] = kv.Value;
            }
        }

        var bundled = new List<string>();

        using (var zip = ZipFile.Open(zipPath, ZipArchiveMode.Create))
        {
            var usedEntryNames = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

            foreach (var imagePath in imagesToBundle)
            {
                var sidecarPath = Path.Combine(outputDir, Path.GetFileName(imagePath) + ".json");
                if (!File.Exists(sidecarPath))
                    continue;

                var bundlePath = bundleOptimizedMap.TryGetValue(imagePath, out var optPath)
                    ? optPath : imagePath;
                var bundleImageName = Path.GetFileName(bundlePath);
                var bundleSidecarName = bundleImageName + ".json";

                if (!usedEntryNames.Add(bundleImageName))
                {
                    if (verbose)
                        AnsiConsole.MarkupLine($"  [yellow]Skipped duplicate ZIP entry: {bundleImageName}[/]");
                    continue;
                }

                zip.CreateEntryFromFile(bundlePath, bundleImageName);

                if (bundleSidecarName != Path.GetFileName(sidecarPath))
                {
                    var entry = zip.CreateEntry(bundleSidecarName);
                    using var entryStream = entry.Open();
                    using var sidecarStream = File.OpenRead(sidecarPath);
                    sidecarStream.CopyTo(entryStream);
                }
                else
                {
                    zip.CreateEntryFromFile(sidecarPath, bundleSidecarName);
                }

                bundled.Add(imagePath);

                if (verbose)
                    AnsiConsole.MarkupLine($"  [dim]Bundled: {Path.GetFileName(imagePath)}[/]");
            }
        }

        return new ZipBundleResult
        {
            ZipPath = zipPath,
            ImageCount = bundled.Count,
            Mode = mode,
            BundledImagePaths = bundled,
        };
    }

    /// <summary>
    /// Select which images to include in the bundle based on mode.
    /// </summary>
    internal static List<string> SelectImagesForBundle(
        ZipMode mode,
        IReadOnlyList<string> allImages,
        string outputDir,
        IReadOnlyList<ImageRebuildPlan> plans,
        IReadOnlyList<(string Image, string Sidecar)> processed,
        BuildManifest manifest)
    {
        return mode switch
        {
            ZipMode.Full => SelectForFullBundle(allImages, outputDir),
            ZipMode.Patch => SelectForPatchBundle(allImages, plans, processed, manifest),
            _ => throw new ArgumentOutOfRangeException(nameof(mode)),
        };
    }

    /// <summary>
    /// Full bundle: all images that have sidecars.
    /// </summary>
    private static List<string> SelectForFullBundle(IReadOnlyList<string> allImages, string outputDir)
    {
        return allImages.Where(img => SidecarService.HasSidecar(img, outputDir)).ToList();
    }

    /// <summary>
    /// Patch bundle: only images that were changed, new, or had partial updates in this run.
    /// Includes: newly processed images + images with partial rebuilds + images that got stripped.
    /// Excludes: unchanged skip images with no modifications.
    /// </summary>
    private static List<string> SelectForPatchBundle(
        IReadOnlyList<string> allImages,
        IReadOnlyList<ImageRebuildPlan> plans,
        IReadOnlyList<(string Image, string Sidecar)> processed,
        BuildManifest manifest)
    {
        var changedImages = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var processedImages = new HashSet<string>(
            processed.Select(p => p.Image), StringComparer.OrdinalIgnoreCase);

        // Images that were successfully processed (full or partial rebuild)
        foreach (var (image, _) in processed)
            changedImages.Add(image);

        // Images that had field groups stripped (sidecar was modified)
        foreach (var plan in plans)
        {
            if (plan.NeedsStripping)
                changedImages.Add(plan.ImagePath);
        }

        // Images not yet in any previous bundle (new to the collection)
        // Exclude images that were planned for rebuild but failed annotation
        foreach (var plan in plans)
        {
            if (plan.Scope != RebuildScope.Skip && !processedImages.Contains(plan.ImagePath))
                continue;

            var fileName = Path.GetFileName(plan.ImagePath);
            if (!manifest.Images.TryGetValue(fileName, out var entry) || !entry.HasBundleOptimized)
                changedImages.Add(plan.ImagePath);
        }

        // Filter to images in the changedImages set that have sidecars
        return allImages
            .Where(img => changedImages.Contains(img))
            .ToList();
    }

    /// <summary>
    /// Record which images were bundled in the manifest.
    /// </summary>
    public static void RecordBundledImages(
        BuildManifest manifest,
        IReadOnlyList<string> bundledImagePaths)
    {
        foreach (var imagePath in bundledImagePaths)
        {
            ManifestService.RecordBundleOptimized(manifest, Path.GetFileName(imagePath));
        }
    }
}
