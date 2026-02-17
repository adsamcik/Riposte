using System.Text;
using System.Text.Json;
using RiposteCli.Models;
using RiposteCli.Services;

namespace RiposteCli.Tests;

public class SidecarServiceTests : IDisposable
{
    private readonly string _tempDir;

    public SidecarServiceTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), $"riposte-test-{Guid.NewGuid()}");
        Directory.CreateDirectory(_tempDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempDir))
            Directory.Delete(_tempDir, true);
    }

    #region IsSupportedImage — Magic Byte Detection

    [Fact]
    public void IsSupportedImage_ValidJpeg_ReturnsTrue()
    {
        var path = CreateFileWithBytes("test.jpg", [0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10]);
        Assert.True(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_ValidPng_ReturnsTrue()
    {
        var path = CreateFileWithBytes("test.png", [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]);
        Assert.True(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_ValidGif87a_ReturnsTrue()
    {
        var path = CreateFileWithBytes("test.gif", "GIF87a"u8.ToArray());
        Assert.True(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_ValidGif89a_ReturnsTrue()
    {
        var path = CreateFileWithBytes("test.gif", "GIF89a"u8.ToArray());
        Assert.True(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_ValidBmp_ReturnsTrue()
    {
        var path = CreateFileWithBytes("test.bmp", "BM\x00\x00\x00\x00"u8.ToArray());
        Assert.True(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_ValidTiffLittleEndian_ReturnsTrue()
    {
        var path = CreateFileWithBytes("test.tiff", [0x49, 0x49, 0x2A, 0x00, 0x08, 0x00]);
        Assert.True(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_ValidTiffBigEndian_ReturnsTrue()
    {
        var path = CreateFileWithBytes("test.tif", [0x4D, 0x4D, 0x00, 0x2A, 0x00, 0x08]);
        Assert.True(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_ValidWebpRiff_ReturnsTrue()
    {
        // RIFF....WEBP
        var bytes = "RIFF\x00\x00\x00\x00WEBP"u8.ToArray();
        var path = CreateFileWithBytes("test.webp", bytes);
        Assert.True(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_HeifFtyp_ReturnsTrue()
    {
        // HEIF: 4 bytes size + "ftyp" + brand
        var bytes = new byte[] { 0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70, 0x68, 0x65, 0x69, 0x63 };
        var path = CreateFileWithBytes("test.heic", bytes);
        Assert.True(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_UnsupportedExtension_ReturnsFalse()
    {
        var path = CreateFileWithBytes("test.txt", [0xFF, 0xD8, 0xFF, 0xE0]);
        Assert.False(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_SupportedExtensionButTooSmall_ReturnsFalse()
    {
        // Only 3 bytes — too small for any magic match (need >= 4)
        var path = CreateFileWithBytes("test.jpg", [0xFF, 0xD8, 0xFF]);
        // Extension matches, but bytesRead < 4 so magic check fails
        // However the method returns true for extension-only match on line 65
        // Actually: bytesRead (3) < 4 returns false on line 51
        Assert.False(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_WrongMagicButSupportedExtension_StillReturnsTrue()
    {
        // JXL extension with random bytes — falls through to extension-only match (line 65)
        var path = CreateFileWithBytes("test.jxl", [0x00, 0x01, 0x02, 0x03, 0x04, 0x05]);
        Assert.True(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_EmptyFile_ReturnsFalse()
    {
        var path = CreateFileWithBytes("test.jpg", []);
        Assert.False(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_NonExistentFile_ReturnsFalse()
    {
        Assert.False(SidecarService.IsSupportedImage(Path.Combine(_tempDir, "nonexistent.jpg")));
    }

    [Theory]
    [InlineData(".jpg")]
    [InlineData(".jpeg")]
    [InlineData(".png")]
    [InlineData(".gif")]
    [InlineData(".webp")]
    [InlineData(".bmp")]
    [InlineData(".tiff")]
    [InlineData(".tif")]
    [InlineData(".heic")]
    [InlineData(".heif")]
    [InlineData(".avif")]
    [InlineData(".jxl")]
    public void IsSupportedImage_AllSupportedExtensions_AcceptedWithValidHeader(string ext)
    {
        // Use JPEG magic bytes — they'll match for .jpg/.jpeg; others fall through to extension-only
        var path = CreateFileWithBytes($"test{ext}", [0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10]);
        Assert.True(SidecarService.IsSupportedImage(path));
    }

    [Theory]
    [InlineData(".txt")]
    [InlineData(".pdf")]
    [InlineData(".mp4")]
    [InlineData(".doc")]
    [InlineData(".json")]
    [InlineData(".xml")]
    public void IsSupportedImage_UnsupportedExtensions_Rejected(string ext)
    {
        var path = CreateFileWithBytes($"test{ext}", [0xFF, 0xD8, 0xFF, 0xE0]);
        Assert.False(SidecarService.IsSupportedImage(path));
    }

    #endregion

    #region GetImagesInFolder

    [Fact]
    public void GetImagesInFolder_MixedFiles_ReturnsOnlyImages()
    {
        CreateFileWithBytes("photo.jpg", [0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10]);
        CreateFileWithBytes("icon.png", [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]);
        CreateFileWithBytes("notes.txt", "hello"u8.ToArray());
        CreateFileWithBytes("data.json", "{}"u8.ToArray());

        var images = SidecarService.GetImagesInFolder(_tempDir);
        Assert.Equal(2, images.Count);
        Assert.All(images, img => Assert.True(
            img.EndsWith(".jpg") || img.EndsWith(".png")));
    }

    [Fact]
    public void GetImagesInFolder_ReturnsSortedByName()
    {
        CreateFileWithBytes("charlie.png", [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]);
        CreateFileWithBytes("alpha.png", [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]);
        CreateFileWithBytes("bravo.png", [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]);

        var images = SidecarService.GetImagesInFolder(_tempDir);
        Assert.Equal(3, images.Count);
        Assert.EndsWith("alpha.png", images[0]);
        Assert.EndsWith("bravo.png", images[1]);
        Assert.EndsWith("charlie.png", images[2]);
    }

    [Fact]
    public void GetImagesInFolder_IgnoresSubdirectories()
    {
        CreateFileWithBytes("root.jpg", [0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10]);
        var subDir = Path.Combine(_tempDir, "subdir");
        Directory.CreateDirectory(subDir);
        File.WriteAllBytes(Path.Combine(subDir, "nested.jpg"),
            [0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10]);

        var images = SidecarService.GetImagesInFolder(_tempDir);
        Assert.Single(images);
    }

    [Fact]
    public void GetImagesInFolder_IgnoresSidecarJsonFiles()
    {
        CreateFileWithBytes("meme.jpg", [0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10]);
        File.WriteAllText(Path.Combine(_tempDir, "meme.jpg.json"), "{}");

        var images = SidecarService.GetImagesInFolder(_tempDir);
        Assert.Single(images);
        Assert.EndsWith("meme.jpg", images[0]);
    }

    #endregion

    #region HasSidecar

    [Fact]
    public void HasSidecar_SidecarExists_ReturnsTrue()
    {
        var img = CreateFileWithBytes("test.jpg", [0xFF, 0xD8, 0xFF]);
        File.WriteAllText(Path.Combine(_tempDir, "test.jpg.json"), "{}");

        Assert.True(SidecarService.HasSidecar(img));
    }

    [Fact]
    public void HasSidecar_NoSidecar_ReturnsFalse()
    {
        var img = CreateFileWithBytes("test.jpg", [0xFF, 0xD8, 0xFF]);
        Assert.False(SidecarService.HasSidecar(img));
    }

    [Fact]
    public void HasSidecar_CustomOutputDir_ChecksCorrectLocation()
    {
        var outDir = Path.Combine(_tempDir, "output");
        Directory.CreateDirectory(outDir);

        var img = CreateFileWithBytes("test.jpg", [0xFF, 0xD8, 0xFF]);
        Assert.False(SidecarService.HasSidecar(img, outDir));

        File.WriteAllText(Path.Combine(outDir, "test.jpg.json"), "{}");
        Assert.True(SidecarService.HasSidecar(img, outDir));
    }

    #endregion

    #region FilterImagesByMode — Extended

    [Fact]
    public void FilterImagesByMode_IncrementalMixed_CorrectPartition()
    {
        var outDir = Path.Combine(_tempDir, "out");
        Directory.CreateDirectory(outDir);

        // Create sidecar for a.jpg only
        File.WriteAllText(Path.Combine(outDir, "a.jpg.json"), "{}");
        File.WriteAllText(Path.Combine(outDir, "c.jpg.json"), "{}");

        var images = new List<string>
        {
            Path.Combine(_tempDir, "a.jpg"),
            Path.Combine(_tempDir, "b.jpg"),
            Path.Combine(_tempDir, "c.jpg"),
            Path.Combine(_tempDir, "d.jpg"),
        };

        var (toProcess, skipped) = SidecarService.FilterImagesByMode(images, outDir, force: false);

        Assert.Equal(2, toProcess.Count);
        Assert.Equal(2, skipped);
        Assert.Contains(images[1], toProcess); // b.jpg
        Assert.Contains(images[3], toProcess); // d.jpg
    }

    [Fact]
    public void FilterImagesByMode_AllHaveSidecars_ReturnsEmpty()
    {
        File.WriteAllText(Path.Combine(_tempDir, "a.jpg.json"), "{}");
        File.WriteAllText(Path.Combine(_tempDir, "b.jpg.json"), "{}");

        var images = new List<string>
        {
            Path.Combine(_tempDir, "a.jpg"),
            Path.Combine(_tempDir, "b.jpg"),
        };

        var (toProcess, skipped) = SidecarService.FilterImagesByMode(images, _tempDir, force: false);

        Assert.Empty(toProcess);
        Assert.Equal(2, skipped);
    }

    [Fact]
    public void FilterImagesByMode_ForceIgnoresSidecars()
    {
        File.WriteAllText(Path.Combine(_tempDir, "a.jpg.json"), "{}");

        var images = new List<string> { Path.Combine(_tempDir, "a.jpg") };

        var (toProcess, skipped) = SidecarService.FilterImagesByMode(images, _tempDir, force: true);

        Assert.Single(toProcess);
        Assert.Equal(0, skipped);
    }

    #endregion

    #region WriteSidecar

    [Fact]
    public void WriteSidecar_CreatesJsonFile_WithCorrectName()
    {
        var imgPath = CreateFileWithBytes("meme.jpg", [0xFF, 0xD8, 0xFF]);
        var metadata = SidecarService.CreateMetadata(emojis: ["😂"]);

        var result = SidecarService.WriteSidecar(imgPath, metadata);

        Assert.Equal(Path.Combine(_tempDir, "meme.jpg.json"), result);
        Assert.True(File.Exists(result));
    }

    [Fact]
    public void WriteSidecar_WritesValidJson()
    {
        var imgPath = CreateFileWithBytes("meme.jpg", [0xFF, 0xD8, 0xFF]);
        var metadata = SidecarService.CreateMetadata(
            emojis: ["😂", "🔥"],
            title: "Test Meme",
            description: "A funny test meme");

        var sidecarPath = SidecarService.WriteSidecar(imgPath, metadata);
        var json = File.ReadAllText(sidecarPath);
        var doc = JsonDocument.Parse(json);
        var root = doc.RootElement;

        Assert.Equal("1.3", root.GetProperty("schemaVersion").GetString());
        Assert.Equal(2, root.GetProperty("emojis").GetArrayLength());
        Assert.Equal("😂", root.GetProperty("emojis")[0].GetString());
        Assert.Equal("Test Meme", root.GetProperty("title").GetString());
    }

    [Fact]
    public void WriteSidecar_NullFieldsOmittedFromJson()
    {
        var imgPath = CreateFileWithBytes("meme.jpg", [0xFF, 0xD8, 0xFF]);
        var metadata = SidecarService.CreateMetadata(emojis: ["😂"]);

        var sidecarPath = SidecarService.WriteSidecar(imgPath, metadata);
        var json = File.ReadAllText(sidecarPath);

        // Null optional fields should NOT appear in JSON
        Assert.DoesNotContain("\"title\"", json);
        Assert.DoesNotContain("\"description\"", json);
        Assert.DoesNotContain("\"tags\"", json);
        Assert.DoesNotContain("\"searchPhrases\"", json);
        Assert.DoesNotContain("\"localizations\"", json);
        Assert.DoesNotContain("\"basedOn\"", json);
        Assert.DoesNotContain("\"contentHash\"", json);

        // Required fields should appear
        Assert.Contains("\"schemaVersion\"", json);
        Assert.Contains("\"emojis\"", json);
        Assert.Contains("\"createdAt\"", json);
        Assert.Contains("\"appVersion\"", json);
        Assert.Contains("\"cliVersion\"", json);
    }

    [Fact]
    public void WriteSidecar_CustomOutputDir_WritesToOutputDir()
    {
        var outDir = Path.Combine(_tempDir, "output");
        Directory.CreateDirectory(outDir);
        var imgPath = CreateFileWithBytes("meme.jpg", [0xFF, 0xD8, 0xFF]);
        var metadata = SidecarService.CreateMetadata(emojis: ["😂"]);

        var result = SidecarService.WriteSidecar(imgPath, metadata, outDir);

        Assert.StartsWith(outDir, result);
        Assert.True(File.Exists(result));
        Assert.False(File.Exists(Path.Combine(_tempDir, "meme.jpg.json")));
    }

    [Fact]
    public void WriteSidecar_OverwritesExisting()
    {
        var imgPath = CreateFileWithBytes("meme.jpg", [0xFF, 0xD8, 0xFF]);
        var sidecarPath = Path.Combine(_tempDir, "meme.jpg.json");
        File.WriteAllText(sidecarPath, """{"old": true}""");

        var metadata = SidecarService.CreateMetadata(emojis: ["🔥"]);
        SidecarService.WriteSidecar(imgPath, metadata);

        var json = File.ReadAllText(sidecarPath);
        Assert.DoesNotContain("\"old\"", json);
        Assert.Contains("schemaVersion", json);
        Assert.Contains("emojis", json);
    }

    #endregion

    #region CreateMetadata from AnalysisResult

    [Fact]
    public void CreateMetadata_FromAnalysisResult_MapsAllFields()
    {
        var result = new AnalysisResult
        {
            Emojis = ["😂", "🐱"],
            Title = "Cat Meme",
            Description = "A funny cat",
            Tags = ["cat", "funny"],
            SearchPhrases = ["funny cat meme"],
            BasedOn = "Grumpy Cat",
            Localizations = new Dictionary<string, LocalizedContent>
            {
                ["cs"] = new()
                {
                    Title = "Kočičí Meme",
                    Description = "Vtipná kočka",
                }
            },
        };

        var metadata = SidecarService.CreateMetadata(result, "en", "hash123");

        Assert.Equal(result.Emojis, metadata.Emojis);
        Assert.Equal("Cat Meme", metadata.Title);
        Assert.Equal("A funny cat", metadata.Description);
        Assert.Equal(["cat", "funny"], metadata.Tags);
        Assert.Equal(["funny cat meme"], metadata.SearchPhrases);
        Assert.Equal("Grumpy Cat", metadata.BasedOn);
        Assert.Equal("en", metadata.PrimaryLanguage);
        Assert.Equal("hash123", metadata.ContentHash);
        Assert.NotNull(metadata.Localizations);
        Assert.Equal("Kočičí Meme", metadata.Localizations["cs"].Title);
    }

    [Fact]
    public void CreateMetadata_FromAnalysisResult_NullOptionalFields()
    {
        var result = new AnalysisResult
        {
            Emojis = ["😂"],
        };

        var metadata = SidecarService.CreateMetadata(result);

        Assert.Equal(["😂"], metadata.Emojis);
        Assert.Null(metadata.Title);
        Assert.Null(metadata.Description);
        Assert.Null(metadata.Tags);
        Assert.Null(metadata.PrimaryLanguage);
        Assert.Null(metadata.ContentHash);
    }

    #endregion

    private string CreateFileWithBytes(string name, byte[] bytes)
    {
        var path = Path.Combine(_tempDir, name);
        File.WriteAllBytes(path, bytes);
        return path;
    }
}
