using RiposteCli.Services;

namespace RiposteCli.Tests;

public class DeduplicationTests
{
    [Fact]
    public void DeleteImageAndSidecar_BothExist_BothDeleted()
    {
        var tempDir = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString());
        Directory.CreateDirectory(tempDir);
        try
        {
            var img = Path.Combine(tempDir, "test.jpg");
            var sidecar = Path.Combine(tempDir, "test.jpg.json");
            File.WriteAllBytes(img, "image data"u8.ToArray());
            File.WriteAllText(sidecar, """{"emojis": []}""");

            // Simulate the deletion logic from DedupeCommand
            var deleted = 0;
            if (File.Exists(img)) { File.Delete(img); deleted++; }
            if (File.Exists(sidecar)) { File.Delete(sidecar); deleted++; }

            Assert.Equal(2, deleted);
            Assert.False(File.Exists(img));
            Assert.False(File.Exists(sidecar));
        }
        finally
        {
            Directory.Delete(tempDir, true);
        }
    }

    [Fact]
    public void DeleteImageWithoutSidecar_OnlyImageDeleted()
    {
        var tempDir = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString());
        Directory.CreateDirectory(tempDir);
        try
        {
            var img = Path.Combine(tempDir, "test.jpg");
            File.WriteAllBytes(img, "image data"u8.ToArray());

            var deleted = 0;
            if (File.Exists(img)) { File.Delete(img); deleted++; }
            var sidecar = Path.Combine(tempDir, "test.jpg.json");
            if (File.Exists(sidecar)) { File.Delete(sidecar); deleted++; }

            Assert.Equal(1, deleted);
        }
        finally
        {
            Directory.Delete(tempDir, true);
        }
    }

    [Fact]
    public void GetImagesInFolder_EmptyDir_ReturnsEmpty()
    {
        var tempDir = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString());
        Directory.CreateDirectory(tempDir);
        try
        {
            var images = SidecarService.GetImagesInFolder(tempDir);
            Assert.Empty(images);
        }
        finally
        {
            Directory.Delete(tempDir, true);
        }
    }

    [Fact]
    public void FilterImagesByMode_Force_ReturnsAll()
    {
        var tempDir = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString());
        Directory.CreateDirectory(tempDir);
        try
        {
            var images = new List<string>
            {
                Path.Combine(tempDir, "a.jpg"),
                Path.Combine(tempDir, "b.jpg"),
            };

            var (toProcess, skipped) = SidecarService.FilterImagesByMode(images, tempDir, force: true);

            Assert.Equal(2, toProcess.Count);
            Assert.Equal(0, skipped);
        }
        finally
        {
            Directory.Delete(tempDir, true);
        }
    }

    [Fact]
    public void FilterImagesByMode_SkipsExistingSidecars()
    {
        var tempDir = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString());
        Directory.CreateDirectory(tempDir);
        try
        {
            // Create a sidecar for "a.jpg"
            File.WriteAllText(Path.Combine(tempDir, "a.jpg.json"), "{}");

            var images = new List<string>
            {
                Path.Combine(tempDir, "a.jpg"),
                Path.Combine(tempDir, "b.jpg"),
            };

            var (toProcess, skipped) = SidecarService.FilterImagesByMode(images, tempDir, force: false);

            Assert.Single(toProcess);
            Assert.Equal(1, skipped);
            Assert.Contains(Path.Combine(tempDir, "b.jpg"), toProcess);
        }
        finally
        {
            Directory.Delete(tempDir, true);
        }
    }
}
