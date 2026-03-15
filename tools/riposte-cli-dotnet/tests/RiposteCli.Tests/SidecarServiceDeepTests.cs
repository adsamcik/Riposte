using System.Text;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;

namespace RiposteCli.Tests;

public sealed class SidecarServiceDeepTests : IDisposable
{
    private readonly TempDir _temp = new();

    public void Dispose() => _temp.Dispose();

    [Fact]
    public void IsSupportedImage_ValidJpegMagicBytes_ReturnsTrue()
    {
        var path = WriteBytes("valid.jpg", 0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10);
        Assert.True(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_ValidPngMagicBytes_ReturnsTrue()
    {
        var path = WriteBytes("valid.png", 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
        Assert.True(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_ValidGifMagicBytes_ReturnsTrue()
    {
        var path = WriteBytes("valid.gif", Encoding.ASCII.GetBytes("GIF89a"));
        Assert.True(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_ValidBmpMagicBytes_ReturnsTrue()
    {
        var path = WriteBytes("valid.bmp", Encoding.ASCII.GetBytes("BM1234"));
        Assert.True(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_ValidWebpRiffMagicBytes_ReturnsTrue()
    {
        var path = WriteBytes("valid.webp", Encoding.ASCII.GetBytes("RIFFxxxxWEBP"));
        Assert.True(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_PngExtensionWithJpegMagicBytes_ReturnsTrue()
    {
        var path = WriteBytes("mismatch.png", 0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10);
        Assert.True(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_TxtExtensionWithPngContent_ReturnsFalse()
    {
        var path = WriteBytes("wrong.txt", 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
        Assert.False(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_ValidTiffLittleEndianMagicBytes_ReturnsTrue()
    {
        var path = WriteBytes("valid.tiff", 0x49, 0x49, 0x2A, 0x00, 0x08, 0x00);
        Assert.True(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_ValidTiffBigEndianMagicBytes_ReturnsTrue()
    {
        var path = WriteBytes("valid.tif", 0x4D, 0x4D, 0x00, 0x2A, 0x00, 0x08);
        Assert.True(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_ValidHeifFtypMagicBytes_ReturnsTrue()
    {
        // HEIF/AVIF files have a ftyp box: 4-byte size, then "ftyp" at offset 4
        var bytes = new byte[12];
        bytes[0] = 0x00; bytes[1] = 0x00; bytes[2] = 0x00; bytes[3] = 0x1C;
        System.Text.Encoding.ASCII.GetBytes("ftyp").CopyTo(bytes, 4);
        System.Text.Encoding.ASCII.GetBytes("heic").CopyTo(bytes, 8);
        var path = WriteBytes("valid.heic", bytes);
        Assert.True(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_JxlExtensionWithUnknownMagic_ReturnsTrueViaFallback()
    {
        // JXL has no entry in the magic byte table; the extension-only fallback allows it
        var path = WriteBytes("image.jxl", 0xFF, 0x0A, 0x00, 0x00, 0x00);
        Assert.True(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_NonexistentFile_ReturnsFalse()
    {
        var path = Path.Combine(_temp.Path, "nonexistent.png");
        Assert.False(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_UnsupportedSvgExtension_ReturnsFalse()
    {
        var path = WriteBytes("vector.svg", System.Text.Encoding.ASCII.GetBytes("<svg></svg>pad"));
        Assert.False(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_PngExtensionWithGarbageContent_ReturnsTrue()
    {
        var path = WriteBytes("garbage.png", 0x10, 0x20, 0x30, 0x40, 0x50);
        Assert.True(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_EmptyFile_ReturnsFalse()
    {
        var path = WriteBytes("empty.png", []);
        Assert.False(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_FileShorterThanFourBytes_ReturnsFalse()
    {
        var path = WriteBytes("tiny.png", 0x89, 0x50, 0x4E);
        Assert.False(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void GetImagesInFolder_MixedImagesAndNonImages_ReturnsOnlyImages()
    {
        CreateRealImage("photo.jpg");
        CreateRealImage("icon.png");
        File.WriteAllText(Path.Combine(_temp.Path, "notes.txt"), "hello");
        File.WriteAllText(Path.Combine(_temp.Path, "payload.json"), "{}");

        var images = SidecarService.GetImagesInFolder(_temp.Path);

        Assert.Equal(2, images.Count);
        Assert.All(images, p => Assert.Contains(Path.GetExtension(p), new[] { ".jpg", ".png" }));
    }

    [Fact]
    public void GetImagesInFolder_DoesNotRecurseIntoSubdirectories()
    {
        CreateRealImage("root.jpg");
        var nested = Directory.CreateDirectory(Path.Combine(_temp.Path, "nested"));
        using (var image = new Image<Rgba32>(2, 2))
        {
            image.SaveAsPng(Path.Combine(nested.FullName, "inner.png"));
        }

        var images = SidecarService.GetImagesInFolder(_temp.Path);

        Assert.Single(images);
        Assert.EndsWith("root.jpg", images[0]);
    }

    [Fact]
    public void GetImagesInFolder_EmptyFolder_ReturnsEmptyList()
    {
        var empty = Directory.CreateDirectory(Path.Combine(_temp.Path, "empty"));
        var images = SidecarService.GetImagesInFolder(empty.FullName);
        Assert.Empty(images);
    }

    [Fact]
    public void GetImagesInFolder_ReturnsImagesSortedByName()
    {
        CreateRealImage("charlie.jpg");
        CreateRealImage("alpha.jpg");
        CreateRealImage("bravo.png");

        var images = SidecarService.GetImagesInFolder(_temp.Path)
            .Select(Path.GetFileName)
            .ToList();

        Assert.Equal(["alpha.jpg", "bravo.png", "charlie.jpg"], images);
    }

    [Fact]
    public void GetImagesInFolder_DotPrefixedImage_IsIncluded()
    {
        CreateRealImage(".hidden.jpg");

        var images = SidecarService.GetImagesInFolder(_temp.Path)
            .Select(Path.GetFileName)
            .ToList();

        Assert.Contains(".hidden.jpg", images);
    }

    [Fact]
    public void GetImagesInFolder_HiddenAttributeImage_IsIncluded()
    {
        var hiddenPath = CreateRealImage("attribute-hidden.jpg");
        File.SetAttributes(hiddenPath, File.GetAttributes(hiddenPath) | FileAttributes.Hidden);

        var images = SidecarService.GetImagesInFolder(_temp.Path)
            .Select(Path.GetFileName)
            .ToList();

        Assert.Contains("attribute-hidden.jpg", images);
    }

    [Fact]
    public void HasSidecar_SidecarInSubdirectory_ReturnsTrue()
    {
        var image = CreateRealImage("meme.jpg");
        var outputDir = Directory.CreateDirectory(Path.Combine(_temp.Path, "out")).FullName;
        var sidecarDir = OutputPaths.GetSidecarDir(outputDir);
        Directory.CreateDirectory(sidecarDir);
        File.WriteAllText(Path.Combine(sidecarDir, "meme.jpg.json"), "{}");

        Assert.True(SidecarService.HasSidecar(image, outputDir));
    }

    [Fact]
    public void HasSidecar_LegacyRootSidecar_ReturnsTrue()
    {
        var image = CreateRealImage("legacy.jpg");
        var outputDir = Directory.CreateDirectory(Path.Combine(_temp.Path, "out")).FullName;
        File.WriteAllText(Path.Combine(outputDir, "legacy.jpg.json"), "{}");

        Assert.True(SidecarService.HasSidecar(image, outputDir));
    }

    [Fact]
    public void HasSidecar_NoSidecarAnywhere_ReturnsFalse()
    {
        var image = CreateRealImage("nosidecar.jpg");
        var outputDir = Directory.CreateDirectory(Path.Combine(_temp.Path, "out")).FullName;

        Assert.False(SidecarService.HasSidecar(image, outputDir));
    }

    [Fact]
    public void HasSidecar_SidecarInWrongSubdirectory_ReturnsFalse()
    {
        var image = CreateRealImage("wrongplace.jpg");
        var outputDir = Directory.CreateDirectory(Path.Combine(_temp.Path, "out")).FullName;
        var wrongDir = OutputPaths.GetBundleDir(outputDir);
        Directory.CreateDirectory(wrongDir);
        File.WriteAllText(Path.Combine(wrongDir, "wrongplace.jpg.json"), "{}");

        Assert.False(SidecarService.HasSidecar(image, outputDir));
    }

    [Fact]
    public void ResolveSidecarPath_PrefersSubdirectoryOverRoot()
    {
        var image = CreateRealImage("prefer.jpg");
        var outputDir = Directory.CreateDirectory(Path.Combine(_temp.Path, "out")).FullName;
        var sidecarName = "prefer.jpg.json";
        var sidecarSubDir = OutputPaths.GetSidecarDir(outputDir);
        Directory.CreateDirectory(sidecarSubDir);
        var subPath = Path.Combine(sidecarSubDir, sidecarName);
        var rootPath = Path.Combine(outputDir, sidecarName);
        File.WriteAllText(rootPath, "{\"legacy\":true}");
        File.WriteAllText(subPath, "{\"new\":true}");

        var resolved = SidecarService.ResolveSidecarPath(image, outputDir);

        Assert.Equal(subPath, resolved);
    }

    [Fact]
    public void ResolveSidecarPath_FallsBackToRootWhenSubdirectoryMissing()
    {
        var image = CreateRealImage("fallback.jpg");
        var outputDir = Directory.CreateDirectory(Path.Combine(_temp.Path, "out")).FullName;
        var rootPath = Path.Combine(outputDir, "fallback.jpg.json");
        File.WriteAllText(rootPath, "{}");

        var resolved = SidecarService.ResolveSidecarPath(image, outputDir);

        Assert.Equal(rootPath, resolved);
    }

    [Fact]
    public void ResolveSidecarPath_NoSidecarFound_ReturnsNull()
    {
        var image = CreateRealImage("missing.jpg");
        var outputDir = Directory.CreateDirectory(Path.Combine(_temp.Path, "out")).FullName;

        var resolved = SidecarService.ResolveSidecarPath(image, outputDir);

        Assert.Null(resolved);
    }

    [Fact]
    public void FilterImagesByMode_ForceMode_ReturnsAllImagesAndZeroSkipped()
    {
        var outputDir = Directory.CreateDirectory(Path.Combine(_temp.Path, "out")).FullName;
        var images = new List<string>
        {
            CreateRealImage("a.jpg"),
            CreateRealImage("b.jpg"),
        };

        Directory.CreateDirectory(OutputPaths.GetSidecarDir(outputDir));
        File.WriteAllText(Path.Combine(OutputPaths.GetSidecarDir(outputDir), "a.jpg.json"), "{}");

        var (toProcess, skipped) = SidecarService.FilterImagesByMode(images, outputDir, force: true);

        Assert.Equal(images, toProcess);
        Assert.Equal(0, skipped);
    }

    [Fact]
    public void FilterImagesByMode_NormalMode_SkipsOnlyImagesWithSidecars()
    {
        var outputDir = Directory.CreateDirectory(Path.Combine(_temp.Path, "out")).FullName;
        var first = CreateRealImage("first.jpg");
        var second = CreateRealImage("second.jpg");
        var third = CreateRealImage("third.jpg");
        var images = new List<string> { first, second, third };

        var sidecarDir = OutputPaths.GetSidecarDir(outputDir);
        Directory.CreateDirectory(sidecarDir);
        File.WriteAllText(Path.Combine(sidecarDir, "first.jpg.json"), "{}");
        File.WriteAllText(Path.Combine(outputDir, "third.jpg.json"), "{}");

        var (toProcess, skipped) = SidecarService.FilterImagesByMode(images, outputDir, force: false);

        Assert.Single(toProcess);
        Assert.Equal(second, toProcess[0]);
        Assert.Equal(2, skipped);
    }

    [Fact]
    public void FilterImagesByMode_AllHaveSidecars_ReturnsEmptyAndSkippedCount()
    {
        var outputDir = Directory.CreateDirectory(Path.Combine(_temp.Path, "out")).FullName;
        var images = new List<string>
        {
            CreateRealImage("all-a.jpg"),
            CreateRealImage("all-b.jpg"),
            CreateRealImage("all-c.jpg"),
        };

        var sidecarDir = OutputPaths.GetSidecarDir(outputDir);
        Directory.CreateDirectory(sidecarDir);
        foreach (var image in images)
        {
            File.WriteAllText(Path.Combine(sidecarDir, Path.GetFileName(image) + ".json"), "{}");
        }

        var (toProcess, skipped) = SidecarService.FilterImagesByMode(images, outputDir, force: false);

        Assert.Empty(toProcess);
        Assert.Equal(images.Count, skipped);
    }

    private string CreateRealImage(string fileName)
    {
        var path = Path.Combine(_temp.Path, fileName);
        using var image = new Image<Rgba32>(2, 2);
        image[0, 0] = new Rgba32(255, 0, 0);
        image[1, 0] = new Rgba32(0, 255, 0);
        image[0, 1] = new Rgba32(0, 0, 255);
        image[1, 1] = new Rgba32(255, 255, 0);

        switch (Path.GetExtension(fileName).ToLowerInvariant())
        {
            case ".png":
                image.SaveAsPng(path);
                break;
            default:
                image.SaveAsJpeg(path);
                break;
        }

        return path;
    }

    private string WriteBytes(string fileName, params byte[] bytes)
    {
        var path = Path.Combine(_temp.Path, fileName);
        File.WriteAllBytes(path, bytes);
        return path;
    }

    private sealed class TempDir : IDisposable
    {
        public string Path { get; } = System.IO.Path.Combine(System.IO.Path.GetTempPath(), $"riposte-sidecar-deep-{Guid.NewGuid()}");

        public TempDir()
        {
            Directory.CreateDirectory(Path);
        }

        public void Dispose()
        {
            if (Directory.Exists(Path))
            {
                Directory.Delete(Path, recursive: true);
            }
        }
    }
}
