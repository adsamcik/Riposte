using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;

namespace RiposteCli.Tests;

public sealed class OutputPathsTests : IDisposable
{
    private readonly TestWorkspace _workspace = new();

    public void Dispose() => _workspace.Dispose();

    [Fact]
    public void EnsureDirectories_CreatesAllSubdirs()
    {
        OutputPaths.EnsureDirectories(_workspace.Root);

        Assert.True(Directory.Exists(Path.Combine(_workspace.Root, OutputPaths.SidecarDir)));
        Assert.True(Directory.Exists(Path.Combine(_workspace.Root, OutputPaths.OptimizedDir)));
        Assert.True(Directory.Exists(Path.Combine(_workspace.Root, OutputPaths.BundleDir)));
    }

    [Fact]
    public void EnsureDirectories_IsIdempotent()
    {
        OutputPaths.EnsureDirectories(_workspace.Root);
        var ex = Record.Exception(() => OutputPaths.EnsureDirectories(_workspace.Root));

        Assert.Null(ex);
        Assert.True(Directory.Exists(Path.Combine(_workspace.Root, OutputPaths.SidecarDir)));
        Assert.True(Directory.Exists(Path.Combine(_workspace.Root, OutputPaths.OptimizedDir)));
        Assert.True(Directory.Exists(Path.Combine(_workspace.Root, OutputPaths.BundleDir)));
    }

    [Fact]
    public void GetDirectoryHelpers_ReturnCorrectPaths()
    {
        Assert.Equal(Path.Combine(_workspace.Root, "sidecars"), OutputPaths.GetSidecarDir(_workspace.Root));
        Assert.Equal(Path.Combine(_workspace.Root, "optimized"), OutputPaths.GetOptimizedDir(_workspace.Root));
        Assert.Equal(Path.Combine(_workspace.Root, "bundle"), OutputPaths.GetBundleDir(_workspace.Root));
    }

    [Fact]
    public void MigrateLegacyLayout_MovesRootImageSidecarToSidecars()
    {
        var source = _workspace.WriteTextFile("photo.jpg.json", """{"schemaVersion":"1.4","emojis":["😀"]}""");

        var migrated = OutputPaths.MigrateLegacyLayout(_workspace.Root);
        var destination = Path.Combine(OutputPaths.GetSidecarDir(_workspace.Root), "photo.jpg.json");

        Assert.Equal(1, migrated);
        Assert.False(File.Exists(source));
        Assert.True(File.Exists(destination));
    }

    [Fact]
    public void MigrateLegacyLayout_MovesMultipleSidecarsInOneCall()
    {
        _workspace.WriteTextFile("a.jpg.json", """{"schemaVersion":"1.4"}""");
        _workspace.WriteTextFile("b.png.json", """{"schemaVersion":"1.4"}""");

        var migrated = OutputPaths.MigrateLegacyLayout(_workspace.Root);

        Assert.Equal(2, migrated);
        Assert.True(File.Exists(Path.Combine(OutputPaths.GetSidecarDir(_workspace.Root), "a.jpg.json")));
        Assert.True(File.Exists(Path.Combine(OutputPaths.GetSidecarDir(_workspace.Root), "b.png.json")));
    }

    [Fact]
    public void MigrateLegacyLayout_DoesNotMoveManifestFiles()
    {
        _workspace.WriteTextFile(".meme-build-manifest.json", """{"images":{}}""");
        _workspace.WriteTextFile(".meme-hashes.json", """{"hashes":{}}""");

        var migrated = OutputPaths.MigrateLegacyLayout(_workspace.Root);

        Assert.Equal(0, migrated);
        Assert.True(File.Exists(Path.Combine(_workspace.Root, ".meme-build-manifest.json")));
        Assert.True(File.Exists(Path.Combine(_workspace.Root, ".meme-hashes.json")));
    }

    [Fact]
    public void MigrateLegacyLayout_DoesNotMoveNonSidecarJsonFiles()
    {
        _workspace.WriteTextFile("config.json", """{"enabled":true}""");
        _workspace.WriteTextFile("data.json", """{"values":[1,2,3]}""");

        var migrated = OutputPaths.MigrateLegacyLayout(_workspace.Root);

        Assert.Equal(0, migrated);
        Assert.True(File.Exists(Path.Combine(_workspace.Root, "config.json")));
        Assert.True(File.Exists(Path.Combine(_workspace.Root, "data.json")));
    }

    [Fact]
    public void MigrateLegacyLayout_DoesNotRemigrateSidecarAlreadyInSidecarsDirectory()
    {
        var inSidecars = Path.Combine(OutputPaths.GetSidecarDir(_workspace.Root), "photo.jpg.json");
        Directory.CreateDirectory(OutputPaths.GetSidecarDir(_workspace.Root));
        File.WriteAllText(inSidecars, """{"schemaVersion":"1.4"}""");

        var migrated = OutputPaths.MigrateLegacyLayout(_workspace.Root);

        Assert.Equal(0, migrated);
        Assert.True(File.Exists(inSidecars));
    }

    [Fact]
    public void MigrateLegacyLayout_SkipsRootSidecarWhenDestinationAlreadyExists()
    {
        _workspace.WriteTextFile("photo.jpg.json", """{"schemaVersion":"1.4","source":"root"}""");
        var destination = Path.Combine(OutputPaths.GetSidecarDir(_workspace.Root), "photo.jpg.json");
        Directory.CreateDirectory(OutputPaths.GetSidecarDir(_workspace.Root));
        File.WriteAllText(destination, """{"schemaVersion":"1.4","source":"subdir"}""");

        var migrated = OutputPaths.MigrateLegacyLayout(_workspace.Root);

        Assert.Equal(0, migrated);
        Assert.True(File.Exists(Path.Combine(_workspace.Root, "photo.jpg.json")));
        Assert.Equal("""{"schemaVersion":"1.4","source":"subdir"}""", File.ReadAllText(destination));
    }

    [Fact]
    public void MigrateLegacyLayout_MovesRootApiPngToOptimized()
    {
        var source = _workspace.WriteImageFile("photo_api.png");

        var migrated = OutputPaths.MigrateLegacyLayout(_workspace.Root);
        var destination = Path.Combine(OutputPaths.GetOptimizedDir(_workspace.Root), "photo_api.png");

        Assert.Equal(1, migrated);
        Assert.False(File.Exists(source));
        Assert.True(File.Exists(destination));
    }

    [Fact]
    public void MigrateLegacyLayout_MovesRootApiJpgToOptimized()
    {
        var source = _workspace.WriteImageFile("photo_api.jpg");

        var migrated = OutputPaths.MigrateLegacyLayout(_workspace.Root);
        var destination = Path.Combine(OutputPaths.GetOptimizedDir(_workspace.Root), "photo_api.jpg");

        Assert.Equal(1, migrated);
        Assert.False(File.Exists(source));
        Assert.True(File.Exists(destination));
    }

    [Fact]
    public void MigrateLegacyLayout_DoesNotMoveNonApiPng()
    {
        _workspace.WriteImageFile("photo.png");

        var migrated = OutputPaths.MigrateLegacyLayout(_workspace.Root);

        Assert.Equal(0, migrated);
        Assert.True(File.Exists(Path.Combine(_workspace.Root, "photo.png")));
        Assert.False(File.Exists(Path.Combine(OutputPaths.GetOptimizedDir(_workspace.Root), "photo.png")));
    }

    [Fact]
    public void MigrateLegacyLayout_DoesNotRemigrateApiImageAlreadyInOptimized()
    {
        var existing = Path.Combine(OutputPaths.GetOptimizedDir(_workspace.Root), "photo_api.png");
        Directory.CreateDirectory(OutputPaths.GetOptimizedDir(_workspace.Root));
        _workspace.WriteImageFile("optimized\\photo_api.png");

        var migrated = OutputPaths.MigrateLegacyLayout(_workspace.Root);

        Assert.Equal(0, migrated);
        Assert.True(File.Exists(existing));
    }

    [Fact]
    public void MigrateLegacyLayout_MovesDerivedWebpToBundle()
    {
        _workspace.WriteImageFile("photo.png");
        var source = _workspace.WriteImageFile("photo.webp");

        var migrated = OutputPaths.MigrateLegacyLayout(_workspace.Root);
        var destination = Path.Combine(OutputPaths.GetBundleDir(_workspace.Root), "photo.webp");

        Assert.Equal(1, migrated);
        Assert.False(File.Exists(source));
        Assert.True(File.Exists(destination));
    }

    [Fact]
    public void MigrateLegacyLayout_DoesNotMoveSourceWebpWithoutOtherVariant()
    {
        _workspace.WriteImageFile("meme.webp");

        var migrated = OutputPaths.MigrateLegacyLayout(_workspace.Root);

        Assert.Equal(0, migrated);
        Assert.True(File.Exists(Path.Combine(_workspace.Root, "meme.webp")));
        Assert.False(File.Exists(Path.Combine(OutputPaths.GetBundleDir(_workspace.Root), "meme.webp")));
    }

    [Fact]
    public void MigrateLegacyLayout_MovesDisambiguatedWebpToBundle()
    {
        // Standalone "cat_png.webp" — no companion file.
        // The "_png" suffix signals it was derived from a .png source.
        var source = _workspace.WriteImageFile("cat_png.webp");

        var migrated = OutputPaths.MigrateLegacyLayout(_workspace.Root);
        var destination = Path.Combine(OutputPaths.GetBundleDir(_workspace.Root), "cat_png.webp");

        Assert.Equal(1, migrated);
        Assert.False(File.Exists(source));
        Assert.True(File.Exists(destination));
    }

    [Fact]
    public void MigrateLegacyLayout_MovesDerivedWebpWhenCompanionFileExists()
    {
        // "cat_png.webp" with companion "cat_png.png" — derived via companion match, not disambiguation.
        _workspace.WriteImageFile("cat_png.png");
        var source = _workspace.WriteImageFile("cat_png.webp");

        var migrated = OutputPaths.MigrateLegacyLayout(_workspace.Root);
        var destination = Path.Combine(OutputPaths.GetBundleDir(_workspace.Root), "cat_png.webp");

        Assert.Equal(1, migrated);
        Assert.False(File.Exists(source));
        Assert.True(File.Exists(destination));
    }

    [Fact]
    public void MigrateLegacyLayout_DoesNotMoveWebpWithNonImageDisambiguationSuffix()
    {
        // "photo_final.webp" — has underscore but "final" is not an image extension
        _workspace.WriteImageFile("photo_final.webp");

        var migrated = OutputPaths.MigrateLegacyLayout(_workspace.Root);

        Assert.Equal(0, migrated);
        Assert.True(File.Exists(Path.Combine(_workspace.Root, "photo_final.webp")));
    }

    [Fact]
    public void MigrateLegacyLayout_MovesDerivedWebpWithJpgCompanion()
    {
        _workspace.WriteImageFile("meme.jpg");
        var source = _workspace.WriteImageFile("meme.webp");

        var migrated = OutputPaths.MigrateLegacyLayout(_workspace.Root);
        var destination = Path.Combine(OutputPaths.GetBundleDir(_workspace.Root), "meme.webp");

        Assert.Equal(1, migrated);
        Assert.False(File.Exists(source));
        Assert.True(File.Exists(destination));
    }

    [Fact]
    public void MigrateLegacyLayout_MovesBothSidecarAndApiOptimizedForSameSource()
    {
        _workspace.WriteImageFile("photo.jpg");
        _workspace.WriteTextFile("photo.jpg.json", """{"schemaVersion":"1.4"}""");
        _workspace.WriteImageFile("photo_api.png");

        var migrated = OutputPaths.MigrateLegacyLayout(_workspace.Root);

        Assert.Equal(2, migrated);
        Assert.True(File.Exists(Path.Combine(OutputPaths.GetSidecarDir(_workspace.Root), "photo.jpg.json")));
        Assert.True(File.Exists(Path.Combine(OutputPaths.GetOptimizedDir(_workspace.Root), "photo_api.png")));
        Assert.True(File.Exists(Path.Combine(_workspace.Root, "photo.jpg"))); // source untouched
    }

    [Fact]
    public void MigrateLegacyLayout_ApiWebpNotMovedByApiLoop()
    {
        // _api.webp has API suffix but .webp extension — API loop only handles PNG/JPEG.
        // Without a companion file, webp loop treats it as a source image.
        _workspace.WriteImageFile("photo_api.webp");

        var migrated = OutputPaths.MigrateLegacyLayout(_workspace.Root);

        Assert.Equal(0, migrated);
        Assert.True(File.Exists(Path.Combine(_workspace.Root, "photo_api.webp")));
    }

    [Fact]
    public void MigrateLegacyLayout_DoesNotRemigrateWebpAlreadyInBundle()
    {
        _workspace.WriteImageFile("photo.png");
        _workspace.WriteImageFile("bundle\\photo.webp");

        var migrated = OutputPaths.MigrateLegacyLayout(_workspace.Root);

        Assert.Equal(0, migrated);
        Assert.True(File.Exists(Path.Combine(OutputPaths.GetBundleDir(_workspace.Root), "photo.webp")));
    }

    [Fact]
    public void MigrateLegacyLayout_EmptyDirectoryReturnsZeroAndCreatesSubdirs()
    {
        var migrated = OutputPaths.MigrateLegacyLayout(_workspace.Root);

        Assert.Equal(0, migrated);
        Assert.True(Directory.Exists(OutputPaths.GetSidecarDir(_workspace.Root)));
        Assert.True(Directory.Exists(OutputPaths.GetOptimizedDir(_workspace.Root)));
        Assert.True(Directory.Exists(OutputPaths.GetBundleDir(_workspace.Root)));
    }

    [Fact]
    public void MigrateLegacyLayout_DirectoryWithOnlyImagesReturnsZero()
    {
        _workspace.WriteImageFile("photo1.png");
        _workspace.WriteImageFile("photo2.jpg");
        _workspace.WriteImageFile("photo3.gif");

        var migrated = OutputPaths.MigrateLegacyLayout(_workspace.Root);

        Assert.Equal(0, migrated);
    }

    [Fact]
    public void MigrateLegacyLayout_ReturnValueMatchesMovedFileCount()
    {
        _workspace.WriteTextFile("a.jpg.json", """{"schemaVersion":"1.4"}""");
        _workspace.WriteImageFile("thumb_api.png");
        _workspace.WriteImageFile("base.png");
        _workspace.WriteImageFile("base.webp");
        _workspace.WriteTextFile("config.json", """{"keep":true}""");

        var migrated = OutputPaths.MigrateLegacyLayout(_workspace.Root);

        var sidecarsCount = Directory.GetFiles(OutputPaths.GetSidecarDir(_workspace.Root)).Length;
        var optimizedCount = Directory.GetFiles(OutputPaths.GetOptimizedDir(_workspace.Root)).Length;
        var bundleCount = Directory.GetFiles(OutputPaths.GetBundleDir(_workspace.Root)).Length;
        var actualMoved = sidecarsCount + optimizedCount + bundleCount;

        Assert.Equal(actualMoved, migrated);
        Assert.Equal(3, migrated);
    }

    private sealed class TestWorkspace : IDisposable
    {
        public string Root { get; }

        public TestWorkspace()
        {
            Root = Path.Combine(Path.GetTempPath(), $"riposte-outputpaths-{Guid.NewGuid():N}");
            Directory.CreateDirectory(Root);
        }

        public string WriteTextFile(string relativePath, string content)
        {
            var fullPath = Path.Combine(Root, relativePath);
            var parent = Path.GetDirectoryName(fullPath);
            if (!string.IsNullOrEmpty(parent))
            {
                Directory.CreateDirectory(parent);
            }

            File.WriteAllText(fullPath, content);
            return fullPath;
        }

        public string WriteImageFile(string relativePath)
        {
            var fullPath = Path.Combine(Root, relativePath);
            var parent = Path.GetDirectoryName(fullPath);
            if (!string.IsNullOrEmpty(parent))
            {
                Directory.CreateDirectory(parent);
            }

            var extension = Path.GetExtension(fullPath);
            var pngPath = extension.Equals(".png", StringComparison.OrdinalIgnoreCase)
                ? fullPath
                : fullPath + ".tmp.png";

            using (var image = new Image<Rgba32>(2, 2))
            {
                image[0, 0] = new Rgba32(255, 0, 0);
                image[1, 0] = new Rgba32(0, 255, 0);
                image[0, 1] = new Rgba32(0, 0, 255);
                image[1, 1] = new Rgba32(255, 255, 0);
                image.SaveAsPng(pngPath);
            }

            if (!pngPath.Equals(fullPath, StringComparison.OrdinalIgnoreCase))
            {
                if (File.Exists(fullPath))
                {
                    File.Delete(fullPath);
                }

                File.Move(pngPath, fullPath);
            }

            return fullPath;
        }

        public void Dispose()
        {
            if (Directory.Exists(Root))
            {
                Directory.Delete(Root, recursive: true);
            }
        }
    }
}
