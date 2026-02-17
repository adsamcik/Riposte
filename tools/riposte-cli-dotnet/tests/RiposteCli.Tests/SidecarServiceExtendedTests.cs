using System.Text.Json;
using RiposteCli.Models;
using RiposteCli.Services;

namespace RiposteCli.Tests;

/// <summary>
/// Extended SidecarService tests: GetImagesInFolder, FilterImagesByMode, HasSidecar,
/// WriteSidecar with output dir, IsSupportedImage edge cases.
/// </summary>
public class SidecarServiceExtendedTests : IDisposable
{
    private readonly string _tempDir;

    public SidecarServiceExtendedTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), $"riposte-sidecar-ext-{Guid.NewGuid()}");
        Directory.CreateDirectory(_tempDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempDir))
            Directory.Delete(_tempDir, true);
    }

    #region GetImagesInFolder

    [Fact]
    public void GetImagesInFolder_EmptyDirectory_ReturnsEmpty()
    {
        var result = SidecarService.GetImagesInFolder(_tempDir);
        Assert.Empty(result);
    }

    [Fact]
    public void GetImagesInFolder_MixedFiles_OnlyImages()
    {
        CreateImage("photo.jpg");
        CreateImage("pic.png");
        File.WriteAllText(Path.Combine(_tempDir, "notes.txt"), "text");
        File.WriteAllText(Path.Combine(_tempDir, "data.json"), "{}");
        File.WriteAllText(Path.Combine(_tempDir, "readme.md"), "# readme");

        var result = SidecarService.GetImagesInFolder(_tempDir);
        Assert.Equal(2, result.Count);
    }

    [Fact]
    public void GetImagesInFolder_SortedByName()
    {
        CreateImage("charlie.jpg");
        CreateImage("alpha.jpg");
        CreateImage("bravo.png");

        var result = SidecarService.GetImagesInFolder(_tempDir);
        Assert.Equal(3, result.Count);
        Assert.Contains("alpha", Path.GetFileName(result[0]));
        Assert.Contains("bravo", Path.GetFileName(result[1]));
        Assert.Contains("charlie", Path.GetFileName(result[2]));
    }

    [Fact]
    public void GetImagesInFolder_IgnoresSubdirectories()
    {
        CreateImage("root.jpg");
        var subDir = Path.Combine(_tempDir, "sub");
        Directory.CreateDirectory(subDir);
        File.WriteAllBytes(Path.Combine(subDir, "sub.jpg"), [0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10]);

        var result = SidecarService.GetImagesInFolder(_tempDir);
        Assert.Single(result);
        Assert.Contains("root.jpg", result[0]);
    }

    [Fact]
    public void GetImagesInFolder_AllSupportedFormats()
    {
        // JPEG
        File.WriteAllBytes(Path.Combine(_tempDir, "a.jpg"), [0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10]);
        File.WriteAllBytes(Path.Combine(_tempDir, "b.jpeg"), [0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10]);
        // PNG
        File.WriteAllBytes(Path.Combine(_tempDir, "c.png"), [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]);
        // GIF
        File.WriteAllBytes(Path.Combine(_tempDir, "d.gif"), [0x47, 0x49, 0x46, 0x38, 0x39, 0x61]);
        // BMP
        File.WriteAllBytes(Path.Combine(_tempDir, "e.bmp"), [0x42, 0x4D, 0x00, 0x00, 0x00, 0x00]);
        // WebP (RIFF container)
        File.WriteAllBytes(Path.Combine(_tempDir, "f.webp"), [0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00]);

        var result = SidecarService.GetImagesInFolder(_tempDir);
        Assert.Equal(6, result.Count);
    }

    #endregion

    #region FilterImagesByMode

    [Fact]
    public void FilterImagesByMode_NoSidecars_AllToProcess()
    {
        var images = new List<string>
        {
            CreateImage("a.jpg"),
            CreateImage("b.jpg"),
            CreateImage("c.jpg"),
        };

        var (toProcess, skipped) = SidecarService.FilterImagesByMode(images, _tempDir);
        Assert.Equal(3, toProcess.Count);
        Assert.Equal(0, skipped);
    }

    [Fact]
    public void FilterImagesByMode_AllHaveSidecars_AllSkipped()
    {
        var images = new List<string>
        {
            CreateImage("a.jpg"),
            CreateImage("b.jpg"),
        };

        File.WriteAllText(Path.Combine(_tempDir, "a.jpg.json"), "{}");
        File.WriteAllText(Path.Combine(_tempDir, "b.jpg.json"), "{}");

        var (toProcess, skipped) = SidecarService.FilterImagesByMode(images, _tempDir);
        Assert.Empty(toProcess);
        Assert.Equal(2, skipped);
    }

    [Fact]
    public void FilterImagesByMode_ForceMode_IgnoresSidecars()
    {
        var images = new List<string>
        {
            CreateImage("a.jpg"),
            CreateImage("b.jpg"),
        };

        File.WriteAllText(Path.Combine(_tempDir, "a.jpg.json"), "{}");
        File.WriteAllText(Path.Combine(_tempDir, "b.jpg.json"), "{}");

        var (toProcess, skipped) = SidecarService.FilterImagesByMode(images, _tempDir, force: true);
        Assert.Equal(2, toProcess.Count);
        Assert.Equal(0, skipped);
    }

    [Fact]
    public void FilterImagesByMode_CustomOutputDir()
    {
        var outputDir = Path.Combine(_tempDir, "output");
        Directory.CreateDirectory(outputDir);

        var images = new List<string>
        {
            CreateImage("a.jpg"),
            CreateImage("b.jpg"),
        };

        // Sidecar in output dir, not image dir
        File.WriteAllText(Path.Combine(outputDir, "a.jpg.json"), "{}");

        var (toProcess, skipped) = SidecarService.FilterImagesByMode(images, outputDir);
        Assert.Single(toProcess);
        Assert.Equal(1, skipped);
        Assert.Contains("b.jpg", Path.GetFileName(toProcess[0]));
    }

    #endregion

    #region HasSidecar

    [Fact]
    public void HasSidecar_Exists_ReturnsTrue()
    {
        var imgPath = CreateImage("test.jpg");
        File.WriteAllText(Path.Combine(_tempDir, "test.jpg.json"), "{}");
        Assert.True(SidecarService.HasSidecar(imgPath));
    }

    [Fact]
    public void HasSidecar_NotExists_ReturnsFalse()
    {
        var imgPath = CreateImage("test.jpg");
        Assert.False(SidecarService.HasSidecar(imgPath));
    }

    [Fact]
    public void HasSidecar_CustomOutputDir()
    {
        var outputDir = Path.Combine(_tempDir, "output");
        Directory.CreateDirectory(outputDir);

        var imgPath = CreateImage("test.jpg");
        File.WriteAllText(Path.Combine(outputDir, "test.jpg.json"), "{}");

        Assert.True(SidecarService.HasSidecar(imgPath, outputDir));
        Assert.False(SidecarService.HasSidecar(imgPath)); // Not in image dir
    }

    #endregion

    #region WriteSidecar

    [Fact]
    public void WriteSidecar_DefaultOutputDir_SameAsImage()
    {
        var imgPath = CreateImage("test.jpg");
        var metadata = SidecarService.CreateMetadata(emojis: ["😂"]);
        var sidecarPath = SidecarService.WriteSidecar(imgPath, metadata);

        Assert.Equal(Path.Combine(_tempDir, "test.jpg.json"), sidecarPath);
        Assert.True(File.Exists(sidecarPath));
    }

    [Fact]
    public void WriteSidecar_CustomOutputDir()
    {
        var outputDir = Path.Combine(_tempDir, "output");
        Directory.CreateDirectory(outputDir);

        var imgPath = CreateImage("test.jpg");
        var metadata = SidecarService.CreateMetadata(emojis: ["😂"]);
        var sidecarPath = SidecarService.WriteSidecar(imgPath, metadata, outputDir);

        Assert.Equal(Path.Combine(outputDir, "test.jpg.json"), sidecarPath);
        Assert.True(File.Exists(sidecarPath));
    }

    [Fact]
    public void WriteSidecar_OverwritesExisting()
    {
        var imgPath = CreateImage("test.jpg");
        var sidecarPath = Path.Combine(_tempDir, "test.jpg.json");
        File.WriteAllText(sidecarPath, "old content");

        var metadata = SidecarService.CreateMetadata(emojis: ["🔥"], title: "New");
        SidecarService.WriteSidecar(imgPath, metadata);

        var json = File.ReadAllText(sidecarPath);
        Assert.Contains("New", json);
        Assert.DoesNotContain("old content", json);
    }

    [Fact]
    public void WriteSidecar_JsonIsIndented()
    {
        var imgPath = CreateImage("test.jpg");
        var metadata = SidecarService.CreateMetadata(emojis: ["😂"], title: "Test");
        SidecarService.WriteSidecar(imgPath, metadata);

        var json = File.ReadAllText(Path.Combine(_tempDir, "test.jpg.json"));
        Assert.Contains("\n", json);
        Assert.Contains("  ", json); // Indentation
    }

    [Fact]
    public void WriteSidecar_NullFieldsOmitted()
    {
        var imgPath = CreateImage("test.jpg");
        var metadata = SidecarService.CreateMetadata(emojis: ["😂"]);
        SidecarService.WriteSidecar(imgPath, metadata);

        var json = File.ReadAllText(Path.Combine(_tempDir, "test.jpg.json"));
        Assert.DoesNotContain("\"title\"", json);
        Assert.DoesNotContain("\"description\"", json);
        Assert.DoesNotContain("\"basedOn\"", json);
    }

    #endregion

    #region CreateMetadata from AnalysisResult

    [Fact]
    public void CreateMetadata_FromAnalysisResult_CopiesAllFields()
    {
        var result = new AnalysisResult
        {
            Emojis = ["😂", "🐱"],
            Title = "Cat",
            Description = "A cat meme",
            Tags = ["cat", "funny"],
            SearchPhrases = ["funny cat"],
            BasedOn = "Grumpy Cat",
            Localizations = new Dictionary<string, LocalizedContent>
            {
                ["cs"] = new() { Title = "Kočka" },
            },
        };

        var metadata = SidecarService.CreateMetadata(result, "en", "abc123");

        Assert.Equal(["😂", "🐱"], metadata.Emojis);
        Assert.Equal("Cat", metadata.Title);
        Assert.Equal("A cat meme", metadata.Description);
        Assert.Equal(["cat", "funny"], metadata.Tags);
        Assert.Equal(["funny cat"], metadata.SearchPhrases);
        Assert.Equal("Grumpy Cat", metadata.BasedOn);
        Assert.Equal("en", metadata.PrimaryLanguage);
        Assert.Equal("abc123", metadata.ContentHash);
        Assert.NotNull(metadata.Localizations);
        Assert.Equal("Kočka", metadata.Localizations["cs"].Title);
    }

    [Fact]
    public void CreateMetadata_FromAnalysisResult_NullOptionalFields()
    {
        var result = new AnalysisResult { Emojis = ["🎉"] };
        var metadata = SidecarService.CreateMetadata(result);

        Assert.Equal(["🎉"], metadata.Emojis);
        Assert.Null(metadata.Title);
        Assert.Null(metadata.Description);
        Assert.Null(metadata.Tags);
        Assert.Null(metadata.PrimaryLanguage);
        Assert.Null(metadata.ContentHash);
    }

    #endregion

    #region IsSupportedImage Edge Cases

    [Fact]
    public void IsSupportedImage_NonexistentFile_ReturnsFalse()
    {
        Assert.False(SidecarService.IsSupportedImage(Path.Combine(_tempDir, "nope.jpg")));
    }

    [Fact]
    public void IsSupportedImage_WrongExtension_ReturnsFalse()
    {
        var path = Path.Combine(_tempDir, "image.txt");
        File.WriteAllBytes(path, [0xFF, 0xD8, 0xFF, 0xE0]); // JPEG magic
        Assert.False(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_TooSmallFile_ReturnsFalse()
    {
        var path = Path.Combine(_tempDir, "tiny.jpg");
        File.WriteAllBytes(path, [0xFF, 0xD8]); // Only 2 bytes — below 4 byte minimum
        Assert.False(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_JxlExtension_NoMagicMatch_StillTrue()
    {
        // JXL doesn't have a magic byte check — extension-only fallback
        var path = Path.Combine(_tempDir, "photo.jxl");
        File.WriteAllBytes(path, [0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00]);
        Assert.True(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_HeifFtypBox()
    {
        var path = Path.Combine(_tempDir, "photo.heic");
        // HEIF: ftyp box at offset 4
        var bytes = new byte[12];
        "ftyp"u8.CopyTo(bytes.AsSpan(4));
        bytes[8] = (byte)'h'; bytes[9] = (byte)'e';
        bytes[10] = (byte)'i'; bytes[11] = (byte)'c';
        File.WriteAllBytes(path, bytes);
        Assert.True(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_TiffLittleEndian()
    {
        var path = Path.Combine(_tempDir, "photo.tiff");
        File.WriteAllBytes(path, [0x49, 0x49, 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00]);
        Assert.True(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_TiffBigEndian()
    {
        var path = Path.Combine(_tempDir, "photo.tif");
        File.WriteAllBytes(path, [0x4D, 0x4D, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08]);
        Assert.True(SidecarService.IsSupportedImage(path));
    }

    #endregion

    private string CreateImage(string name)
    {
        var path = Path.Combine(_tempDir, name);
        byte[] data = Path.GetExtension(name).ToLower() switch
        {
            ".png" => [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A],
            ".gif" => [0x47, 0x49, 0x46, 0x38, 0x39, 0x61],
            _ => [0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10],
        };
        File.WriteAllBytes(path, data);
        return path;
    }
}
