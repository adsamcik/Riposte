using System.CommandLine;
using RiposteCli.Commands;
using RiposteCli.Services;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;

namespace RiposteCli.Tests;

public sealed class DedupeCommandTests
{
    [Fact]
    public void DedupeCommand_HasCorrectNameAndDescription()
    {
        var command = DedupeCommand.Create();

        Assert.Equal("dedupe", command.Name);
        Assert.Equal("Find and remove duplicate images in a folder", command.Description);
    }

    [Fact]
    public void DedupeCommand_HasFolderArgument()
    {
        var command = DedupeCommand.Create();
        var folderArg = command.Arguments.SingleOrDefault();

        Assert.NotNull(folderArg);
        Assert.Equal("folder", folderArg.Name);
    }

    [Fact]
    public void DedupeCommand_HasAllExpectedOptions()
    {
        var command = DedupeCommand.Create();
        var optionNames = command.Options.Select(o => o.Name).ToList();

        Assert.Contains("--output", optionNames);
        Assert.Contains("--similarity-threshold", optionNames);
        Assert.Contains("--no-near", optionNames);
        Assert.Contains("--dry-run", optionNames);
        Assert.Contains("--yes", optionNames);
        Assert.Contains("--verbose", optionNames);
    }

    [Fact]
    public void DedupeCommand_ParseDryRunAndYes_NoErrors()
    {
        var root = new RootCommand("test") { DedupeCommand.Create() };

        var parseResult = root.Parse("dedupe . --dry-run --yes");

        Assert.Empty(parseResult.Errors);
    }

    [Fact]
    public void DedupeCommand_ParseThresholdNoNearVerbose_NoErrors()
    {
        var root = new RootCommand("test") { DedupeCommand.Create() };

        var parseResult = root.Parse("dedupe . --similarity-threshold 5 --no-near --verbose");

        Assert.Empty(parseResult.Errors);
    }

    [Fact]
    public void DedupeCommand_DefaultSimilarityThreshold_IsTen()
    {
        var command = DedupeCommand.Create();
        var option = command.Options.First(o => o.Name == "--similarity-threshold");

        Assert.Equal(10, option.GetDefaultValue());
    }

    [Fact]
    public void Deduplicate_IdenticalImages_AreExactDuplicates()
    {
        using var temp = new TempDirectory();
        var imageA = CreateSolidImage(temp.Path, "original.png", 64, 64, new Rgba32(10, 20, 30));
        var imageB = Path.Combine(temp.Path, "copy.png");
        File.Copy(imageA, imageB);

        var manifest = new Dictionary<string, HashEntry>();
        var result = ImageHashService.Deduplicate([imageA, imageB], manifest, detectNearDuplicates: false);

        Assert.Single(result.ExactDuplicates);
        Assert.Equal(imageB, result.ExactDuplicates[0].Duplicate);
        Assert.Equal(imageA, result.ExactDuplicates[0].Original);
    }

    [Fact]
    public void Deduplicate_DifferentImages_AreNotDuplicates()
    {
        using var temp = new TempDirectory();
        var imageA = CreateSolidImage(temp.Path, "red.png", 64, 64, new Rgba32(255, 0, 0));
        var imageB = CreateSolidImage(temp.Path, "green.png", 64, 64, new Rgba32(0, 255, 0));

        var manifest = new Dictionary<string, HashEntry>();
        var result = ImageHashService.Deduplicate([imageA, imageB], manifest, detectNearDuplicates: false, similarityThreshold: 10);

        Assert.Empty(result.ExactDuplicates);
        Assert.Empty(result.NearDuplicates);
        Assert.Equal(2, result.UniqueImages.Count);
    }

    [Fact]
    public void Deduplicate_SimilarImages_DetectedAsNearDuplicatesAtComputedThreshold()
    {
        using var temp = new TempDirectory();
        var imageA = CreatePatternImage(temp.Path, "base.png", 80, 80, invertCorner: false);
        var imageB = CreatePatternImage(temp.Path, "near.png", 80, 80, invertCorner: true);

        var phashA = ImageHashService.ComputePerceptualHash(imageA);
        var phashB = ImageHashService.ComputePerceptualHash(imageB);
        Assert.True(phashA.HasValue);
        Assert.True(phashB.HasValue);
        var distance = ImageHashService.HammingDistance(phashA.Value, phashB.Value);

        var manifest = new Dictionary<string, HashEntry>();
        var result = ImageHashService.Deduplicate([imageA, imageB], manifest, detectNearDuplicates: true, similarityThreshold: distance);

        Assert.Empty(result.ExactDuplicates);
        Assert.Single(result.NearDuplicates);
        Assert.Equal(distance, result.NearDuplicates[0].Distance);
    }

    [Fact]
    public void Deduplicate_NoNearMode_DoesNotReportNearDuplicates()
    {
        using var temp = new TempDirectory();
        var imageA = CreatePatternImage(temp.Path, "base.png", 80, 80, invertCorner: false);
        var imageB = CreatePatternImage(temp.Path, "near.png", 80, 80, invertCorner: true);

        var manifest = new Dictionary<string, HashEntry>();
        var result = ImageHashService.Deduplicate([imageA, imageB], manifest, detectNearDuplicates: false, similarityThreshold: 256);

        Assert.Empty(result.ExactDuplicates);
        Assert.Empty(result.NearDuplicates);
        Assert.Equal(2, result.UniqueImages.Count);
    }

    [Fact]
    public void Deduplicate_SaveManifest_PersistsComputedHashes()
    {
        using var temp = new TempDirectory();
        var imageA = CreateSolidImage(temp.Path, "a.png", 48, 48, new Rgba32(40, 50, 60));
        var imageB = CreateSolidImage(temp.Path, "b.png", 48, 48, new Rgba32(80, 90, 100));

        var manifest = new Dictionary<string, HashEntry>();
        ImageHashService.Deduplicate([imageA, imageB], manifest, detectNearDuplicates: true, similarityThreshold: 10);
        ImageHashService.SaveManifest(temp.Path, manifest);
        var loaded = ImageHashService.LoadManifest(temp.Path);

        Assert.Equal(2, loaded.Count);
        Assert.All(loaded.Values, entry => Assert.False(string.IsNullOrWhiteSpace(entry.ContentHash)));
    }

    [Fact]
    public void Deduplicate_ManifestRoundtrip_SecondRunUsesCachedHashes()
    {
        using var temp = new TempDirectory();
        var imageA = CreateSolidImage(temp.Path, "first.png", 32, 32, new Rgba32(12, 34, 56));
        var imageB = Path.Combine(temp.Path, "first_copy.png");
        File.Copy(imageA, imageB);
        var images = new List<string> { imageA, imageB };

        var firstManifest = new Dictionary<string, HashEntry>();
        var firstRun = ImageHashService.Deduplicate(images, firstManifest, detectNearDuplicates: false);
        ImageHashService.SaveManifest(temp.Path, firstManifest);

        File.Delete(imageA);
        File.Delete(imageB);

        var secondManifest = ImageHashService.LoadManifest(temp.Path);
        var secondRun = ImageHashService.Deduplicate(images, secondManifest, detectNearDuplicates: false);

        Assert.Single(firstRun.ExactDuplicates);
        Assert.Single(secondRun.ExactDuplicates);
        Assert.Equal(firstRun.ExactDuplicates[0].Duplicate, secondRun.ExactDuplicates[0].Duplicate);
    }

    [Fact]
    public void DeleteImageAndSidecar_DeletesBothImageAndSidecar()
    {
        using var temp = new TempDirectory();
        var imageA = CreateSolidImage(temp.Path, "base.png", 64, 64, new Rgba32(7, 8, 9));
        var imageB = Path.Combine(temp.Path, "duplicate.png");
        File.Copy(imageA, imageB);
        var sidecar = Path.Combine(temp.Path, "duplicate.png.json");
        File.WriteAllText(sidecar, """{"emojis":["🔥"]}""");

        var deleted = DedupeCommand.DeleteImageAndSidecar(imageB, temp.Path, verbose: false);

        Assert.Equal(2, deleted);
        Assert.True(File.Exists(imageA));
        Assert.False(File.Exists(imageB));
        Assert.False(File.Exists(sidecar));
    }

    [Fact]
    public void DeleteImageAndSidecar_SidecarInSubdir_StillDeleted()
    {
        using var temp = new TempDirectory();
        var image = CreateSolidImage(temp.Path, "photo.png", 64, 64, new Rgba32(1, 2, 3));
        var sidecarDir = Path.Combine(temp.Path, OutputPaths.SidecarDir);
        Directory.CreateDirectory(sidecarDir);
        var sidecar = Path.Combine(sidecarDir, "photo.png.json");
        File.WriteAllText(sidecar, """{"emojis":["😂"]}""");

        var deleted = DedupeCommand.DeleteImageAndSidecar(image, temp.Path, verbose: false);

        Assert.Equal(2, deleted);
        Assert.False(File.Exists(image));
        Assert.False(File.Exists(sidecar));
    }

    [Fact]
    public void DeleteImageAndSidecar_NoSidecar_OnlyDeletesImage()
    {
        using var temp = new TempDirectory();
        var image = CreateSolidImage(temp.Path, "orphan.png", 64, 64, new Rgba32(1, 2, 3));

        var deleted = DedupeCommand.DeleteImageAndSidecar(image, temp.Path, verbose: false);

        Assert.Equal(1, deleted);
        Assert.False(File.Exists(image));
    }

    [Fact]
    public void DeleteImageAndSidecar_NonexistentImage_ReturnsZero()
    {
        using var temp = new TempDirectory();
        var fakePath = Path.Combine(temp.Path, "nonexistent.png");

        var deleted = DedupeCommand.DeleteImageAndSidecar(fakePath, temp.Path, verbose: false);

        Assert.Equal(0, deleted);
    }

    [Fact]
    public void Deduplicate_ThreeIdenticalImages_TwoAreDuplicates()
    {
        using var temp = new TempDirectory();
        var imageA = CreateSolidImage(temp.Path, "a.png", 64, 64, new Rgba32(10, 20, 30));
        var imageB = Path.Combine(temp.Path, "b.png");
        var imageC = Path.Combine(temp.Path, "c.png");
        File.Copy(imageA, imageB);
        File.Copy(imageA, imageC);

        var manifest = new Dictionary<string, HashEntry>();
        var result = ImageHashService.Deduplicate([imageA, imageB, imageC], manifest, detectNearDuplicates: false);

        Assert.Equal(2, result.ExactDuplicates.Count);
        Assert.Single(result.UniqueImages);
        Assert.All(result.ExactDuplicates, d => Assert.Equal(imageA, d.Original));
    }

    [Fact]
    public void ManifestCleanup_MatchesDedupeCommandPattern()
    {
        // DedupeCommand removes manifest entries by filename after deletion (lines 117-120)
        using var temp = new TempDirectory();
        var imageA = CreateSolidImage(temp.Path, "keep.png", 64, 64, new Rgba32(1, 2, 3));
        var imageB = Path.Combine(temp.Path, "dup.png");
        File.Copy(imageA, imageB);

        var manifest = new Dictionary<string, HashEntry>();
        var result = ImageHashService.Deduplicate([imageA, imageB], manifest, detectNearDuplicates: false);
        Assert.Single(result.ExactDuplicates);
        Assert.Equal(2, manifest.Count);

        // Apply the same cleanup pattern as DedupeCommand.Execute
        foreach (var (dupe, _) in result.ExactDuplicates)
        {
            DedupeCommand.DeleteImageAndSidecar(dupe, temp.Path, verbose: false);
            manifest.Remove(Path.GetFileName(dupe));
        }

        ImageHashService.SaveManifest(temp.Path, manifest);
        var reloaded = ImageHashService.LoadManifest(temp.Path);

        Assert.Single(reloaded);
        Assert.True(reloaded.ContainsKey("keep.png"));
        Assert.False(reloaded.ContainsKey("dup.png"));
    }

    [Fact]
    public void GetImagesInFolder_ProcessesOnlySupportedImageFormats()
    {
        using var temp = new TempDirectory();
        var png = CreateSolidImage(temp.Path, "a.png", 16, 16, new Rgba32(255, 0, 0));
        var jpg = CreateSolidImage(temp.Path, "b.jpg", 16, 16, new Rgba32(0, 0, 255));
        var txt = Path.Combine(temp.Path, "notes.txt");
        var csv = Path.Combine(temp.Path, "data.csv");
        File.WriteAllText(txt, "not an image");
        File.WriteAllText(csv, "also not an image");

        var images = SidecarService.GetImagesInFolder(temp.Path);

        Assert.Contains(png, images);
        Assert.Contains(jpg, images);
        Assert.DoesNotContain(txt, images);
        Assert.DoesNotContain(csv, images);
    }

    private static string CreateSolidImage(string directory, string name, int width, int height, Rgba32 color)
    {
        var path = Path.Combine(directory, name);
        using var image = new Image<Rgba32>(width, height);
        for (var y = 0; y < image.Height; y++)
        {
            for (var x = 0; x < image.Width; x++)
            {
                image[x, y] = color;
            }
        }

        image.Save(path);
        return path;
    }

    private static string CreatePatternImage(string directory, string name, int width, int height, bool invertCorner)
    {
        var path = Path.Combine(directory, name);
        using var image = new Image<Rgba32>(width, height);

        for (var y = 0; y < image.Height; y++)
        {
            for (var x = 0; x < image.Width; x++)
            {
                var baseValue = (byte)((x + y) % 255);
                image[x, y] = new Rgba32(baseValue, (byte)(255 - baseValue), (byte)(baseValue / 2));
            }
        }

        if (invertCorner)
        {
            for (var y = 0; y < 12; y++)
            {
                for (var x = 0; x < 12; x++)
                {
                    var original = image[x, y];
                    image[x, y] = new Rgba32((byte)(255 - original.R), (byte)(255 - original.G), (byte)(255 - original.B));
                }
            }
        }

        image.Save(path);
        return path;
    }

    private sealed class TempDirectory : IDisposable
    {
        public TempDirectory()
        {
            Path = System.IO.Path.Combine(System.IO.Path.GetTempPath(), $"riposte-dedupe-tests-{Guid.NewGuid()}");
            Directory.CreateDirectory(Path);
        }

        public string Path { get; }

        public void Dispose()
        {
            if (Directory.Exists(Path))
                Directory.Delete(Path, recursive: true);
        }
    }
}
