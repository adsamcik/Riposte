using System.IO.Compression;
using System.Text.Json;
using RiposteCli.Models;
using RiposteCli.Services;

namespace RiposteCli.Tests;

public class ZipBundlingTests : IDisposable
{
    private readonly string _tempDir;

    public ZipBundlingTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), $"riposte-zip-test-{Guid.NewGuid()}");
        Directory.CreateDirectory(_tempDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempDir))
            Directory.Delete(_tempDir, true);
    }

    [Fact]
    public void ZipBundle_ContainsImageAndSidecar()
    {
        var imageDir = Path.Combine(_tempDir, "memes");
        Directory.CreateDirectory(imageDir);

        var imgPath = CreateImage(imageDir, "meme1.jpg");
        var metadata = SidecarService.CreateMetadata(emojis: ["😂"], title: "Test");
        SidecarService.WriteSidecar(imgPath, metadata, imageDir);

        var zipPath = Path.Combine(_tempDir, "memes.meme.zip");
        CreateZipBundle(imageDir, zipPath);

        using var zip = ZipFile.OpenRead(zipPath);
        var entryNames = zip.Entries.Select(e => e.Name).ToList();
        Assert.Contains("meme1.jpg", entryNames);
        Assert.Contains("meme1.jpg.json", entryNames);
    }

    [Fact]
    public void ZipBundle_MultipleImages_AllIncluded()
    {
        var imageDir = Path.Combine(_tempDir, "memes");
        Directory.CreateDirectory(imageDir);

        for (var i = 1; i <= 3; i++)
        {
            var imgPath = CreateImage(imageDir, $"img{i}.png");
            var metadata = SidecarService.CreateMetadata(emojis: ["🔥"]);
            SidecarService.WriteSidecar(imgPath, metadata, imageDir);
        }

        var zipPath = Path.Combine(_tempDir, "bundle.zip");
        CreateZipBundle(imageDir, zipPath);

        using var zip = ZipFile.OpenRead(zipPath);
        Assert.Equal(6, zip.Entries.Count); // 3 images + 3 sidecars
    }

    [Fact]
    public void ZipBundle_SkipsImagesWithoutSidecar()
    {
        var imageDir = Path.Combine(_tempDir, "memes");
        Directory.CreateDirectory(imageDir);

        // Image with sidecar
        var img1 = CreateImage(imageDir, "annotated.jpg");
        var metadata = SidecarService.CreateMetadata(emojis: ["😂"]);
        SidecarService.WriteSidecar(img1, metadata, imageDir);

        // Image without sidecar
        CreateImage(imageDir, "unannotated.jpg");

        var zipPath = Path.Combine(_tempDir, "bundle.zip");
        CreateZipBundle(imageDir, zipPath);

        using var zip = ZipFile.OpenRead(zipPath);
        var entryNames = zip.Entries.Select(e => e.Name).ToList();
        Assert.Contains("annotated.jpg", entryNames);
        Assert.Contains("annotated.jpg.json", entryNames);
        Assert.DoesNotContain("unannotated.jpg", entryNames);
    }

    [Fact]
    public void ZipBundle_SidecarContainsValidJson()
    {
        var imageDir = Path.Combine(_tempDir, "memes");
        Directory.CreateDirectory(imageDir);

        var imgPath = CreateImage(imageDir, "test.jpg");
        var metadata = SidecarService.CreateMetadata(
            emojis: ["😂", "🐱"],
            title: "Cat Meme",
            description: "A funny cat");
        SidecarService.WriteSidecar(imgPath, metadata, imageDir);

        var zipPath = Path.Combine(_tempDir, "bundle.zip");
        CreateZipBundle(imageDir, zipPath);

        using var zip = ZipFile.OpenRead(zipPath);
        var sidecarEntry = zip.GetEntry("test.jpg.json");
        Assert.NotNull(sidecarEntry);

        using var stream = sidecarEntry.Open();
        using var reader = new StreamReader(stream);
        var json = reader.ReadToEnd();
        var doc = JsonDocument.Parse(json);
        Assert.Equal("Cat Meme", doc.RootElement.GetProperty("title").GetString());
    }

    [Fact]
    public void ZipBundle_EmptyDir_CreatesEmptyZip()
    {
        var imageDir = Path.Combine(_tempDir, "empty");
        Directory.CreateDirectory(imageDir);

        var zipPath = Path.Combine(_tempDir, "empty.zip");
        CreateZipBundle(imageDir, zipPath);

        using var zip = ZipFile.OpenRead(zipPath);
        Assert.Empty(zip.Entries);
    }

    [Fact]
    public void ZipBundle_ImageDataPreserved()
    {
        var imageDir = Path.Combine(_tempDir, "memes");
        Directory.CreateDirectory(imageDir);

        var imageData = new byte[] { 0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46 };
        var imgPath = Path.Combine(imageDir, "data.jpg");
        File.WriteAllBytes(imgPath, imageData);

        var metadata = SidecarService.CreateMetadata(emojis: ["📸"]);
        SidecarService.WriteSidecar(imgPath, metadata, imageDir);

        var zipPath = Path.Combine(_tempDir, "bundle.zip");
        CreateZipBundle(imageDir, zipPath);

        using var zip = ZipFile.OpenRead(zipPath);
        var imageEntry = zip.GetEntry("data.jpg");
        Assert.NotNull(imageEntry);

        using var stream = imageEntry.Open();
        using var ms = new MemoryStream();
        stream.CopyTo(ms);
        Assert.Equal(imageData, ms.ToArray());
    }

    [Fact]
    public void ZipBundle_WithCustomOutputDir_BundlesFromOutputDir()
    {
        var imageDir = Path.Combine(_tempDir, "images");
        var outputDir = Path.Combine(_tempDir, "output");
        Directory.CreateDirectory(imageDir);
        Directory.CreateDirectory(outputDir);

        var imgPath = CreateImage(imageDir, "photo.jpg");
        var metadata = SidecarService.CreateMetadata(emojis: ["🌅"]);
        SidecarService.WriteSidecar(imgPath, metadata, outputDir);

        var zipPath = Path.Combine(_tempDir, "bundle.zip");
        CreateZipBundleWithOutputDir(imageDir, outputDir, zipPath);

        using var zip = ZipFile.OpenRead(zipPath);
        var entryNames = zip.Entries.Select(e => e.Name).ToList();
        Assert.Contains("photo.jpg", entryNames);
        Assert.Contains("photo.jpg.json", entryNames);
    }

    /// <summary>
    /// Replicates the ZIP bundle logic from AnnotateCommand lines 282-308.
    /// </summary>
    private static void CreateZipBundle(string imageDir, string zipPath)
    {
        CreateZipBundleWithOutputDir(imageDir, imageDir, zipPath);
    }

    private static void CreateZipBundleWithOutputDir(string imageDir, string outputDir, string zipPath)
    {
        var allImages = SidecarService.GetImagesInFolder(imageDir);
        using var zip = ZipFile.Open(zipPath, ZipArchiveMode.Create);
        foreach (var imagePath in allImages)
        {
            var sidecarPath = Path.Combine(outputDir, Path.GetFileName(imagePath) + ".json");
            if (File.Exists(sidecarPath))
            {
                zip.CreateEntryFromFile(imagePath, Path.GetFileName(imagePath));
                zip.CreateEntryFromFile(sidecarPath, Path.GetFileName(sidecarPath));
            }
        }
    }

    private static string CreateImage(string dir, string name)
    {
        var path = Path.Combine(dir, name);
        // Create minimal valid image based on extension
        byte[] data = Path.GetExtension(name).ToLower() switch
        {
            ".png" => [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A],
            _ => [0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10],
        };
        File.WriteAllBytes(path, data);
        return path;
    }
}
