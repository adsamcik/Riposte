namespace RiposteCli.Services;

/// <summary>
/// Centralizes output directory layout. All generated files go into subdirectories
/// to keep the source image folder clean.
/// </summary>
/// <remarks>
/// Layout:
///   {outputDir}/
///     .meme-build-manifest.json   (build manifest — stays at root for discoverability)
///     .meme-hashes.json           (hash manifest — stays at root for discoverability)
///     sidecars/                   (JSON metadata files)
///       photo.jpg.json
///     optimized/                  (API-downscaled images — PNG/JPEG)
///       photo_api.png
///     bundle/                     (WebP images for ZIP bundling)
///       photo.webp
/// </remarks>
public static class OutputPaths
{
    public const string SidecarDir = "sidecars";
    public const string OptimizedDir = "optimized";
    public const string BundleDir = "bundle";

    /// <summary>
    /// Ensure all output subdirectories exist.
    /// </summary>
    public static void EnsureDirectories(string outputDir)
    {
        Directory.CreateDirectory(Path.Combine(outputDir, SidecarDir));
        Directory.CreateDirectory(Path.Combine(outputDir, OptimizedDir));
        Directory.CreateDirectory(Path.Combine(outputDir, BundleDir));
    }

    public static string GetSidecarDir(string outputDir) =>
        Path.Combine(outputDir, SidecarDir);

    public static string GetOptimizedDir(string outputDir) =>
        Path.Combine(outputDir, OptimizedDir);

    public static string GetBundleDir(string outputDir) =>
        Path.Combine(outputDir, BundleDir);

    /// <summary>
    /// Detect and migrate legacy flat-directory layout to the new subdirectory layout.
    /// Moves files silently and returns the count of migrated files.
    /// </summary>
    public static int MigrateLegacyLayout(string outputDir)
    {
        EnsureDirectories(outputDir);
        var migrated = 0;

        // Migrate sidecars: *.json (but not manifests)
        foreach (var file in Directory.GetFiles(outputDir, "*.json"))
        {
            var name = Path.GetFileName(file);
            if (name.StartsWith(".")) continue; // skip .meme-build-manifest.json, .meme-hashes.json
            if (!name.EndsWith(".json")) continue;

            // Only move files that look like image sidecars (e.g., photo.jpg.json)
            // They have a double extension: the image extension + .json
            var baseName = Path.GetFileNameWithoutExtension(name); // e.g., "photo.jpg"
            if (!HasImageExtension(baseName)) continue;

            var dest = Path.Combine(outputDir, SidecarDir, name);
            if (!File.Exists(dest))
            {
                File.Move(file, dest);
                migrated++;
            }
        }

        // Migrate API-optimized images: *_api.png, *_api.jpg
        foreach (var file in Directory.GetFiles(outputDir, "*_api.*"))
        {
            var name = Path.GetFileName(file);
            var ext = Path.GetExtension(name).ToLowerInvariant();
            if (ext is not (".png" or ".jpg" or ".jpeg")) continue;

            var dest = Path.Combine(outputDir, OptimizedDir, name);
            if (!File.Exists(dest))
            {
                File.Move(file, dest);
                migrated++;
            }
        }

        // Migrate bundle-optimized WebP images
        // Only move .webp files that aren't original source images
        foreach (var file in Directory.GetFiles(outputDir, "*.webp"))
        {
            var name = Path.GetFileName(file);

            // Check if there's a corresponding non-webp source image
            // If the .webp IS the source image, don't migrate it
            var stem = Path.GetFileNameWithoutExtension(name);
            var isSourceImage = !Directory.GetFiles(outputDir)
                .Any(f =>
                {
                    var fn = Path.GetFileName(f);
                    return fn != name
                        && Path.GetFileNameWithoutExtension(fn) == stem
                        && HasImageExtension(fn)
                        && !fn.EndsWith(".webp", StringComparison.OrdinalIgnoreCase);
                });

            // Also check for collision-disambiguated names (e.g., cat_png.webp)
            var isDisambiguated = stem.Contains('_') && HasImageExtension(
                stem[(stem.LastIndexOf('_') + 1)..] + ".x"); // crude check: suffix is an extension name

            if (isSourceImage && !isDisambiguated) continue;

            var dest = Path.Combine(outputDir, BundleDir, name);
            if (!File.Exists(dest))
            {
                File.Move(file, dest);
                migrated++;
            }
        }

        return migrated;
    }

    private static readonly HashSet<string> ImageExtensions = new(StringComparer.OrdinalIgnoreCase)
    {
        ".jpg", ".jpeg", ".png", ".webp", ".gif",
        ".bmp", ".tiff", ".tif", ".heic", ".heif",
        ".avif", ".jxl",
    };

    private static bool HasImageExtension(string fileName) =>
        ImageExtensions.Contains(Path.GetExtension(fileName));
}
