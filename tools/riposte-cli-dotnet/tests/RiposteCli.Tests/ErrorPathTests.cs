using System.Reflection;
using RiposteCli.Models;
using SixLabors.ImageSharp;

namespace RiposteCli.Tests;

public sealed class ErrorPathTests : IDisposable
{
    private readonly string _tempDir;

    public ErrorPathTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), $"riposte-error-paths-{Guid.NewGuid()}");
        Directory.CreateDirectory(_tempDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempDir))
            Directory.Delete(_tempDir, recursive: true);
    }

    [Fact]
    public void ParseResponseContent_MissingEmojis_ThrowsCopilotAnalysisException()
    {
        var ex = Assert.Throws<CopilotAnalysisException>(() =>
            CopilotService.ParseResponseContent("""{"title":"x"}"""));

        Assert.Contains("missing 'emojis'", ex.Message, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void ParseResponseContent_EmptyEmojis_ThrowsCopilotAnalysisException()
    {
        Assert.Throws<CopilotAnalysisException>(() =>
            CopilotService.ParseResponseContent("""{"emojis":[]}"""));
    }

    [Fact]
    public void ParseResponseContent_NullLiteral_ThrowsCopilotAnalysisException()
    {
        Assert.Throws<CopilotAnalysisException>(() =>
            CopilotService.ParseResponseContent("null"));
    }

    [Fact]
    public void ParsePartialResponse_NullLiteral_ThrowsCopilotAnalysisExceptionWithWasNull()
    {
        var ex = Assert.Throws<CopilotAnalysisException>(() =>
            CopilotService.ParsePartialResponse("null", [PromptHasher.GroupCore]));

        Assert.Contains("was null", ex.Message, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void ClassifyAndThrow_RateLimitMessage_ThrowsRateLimitException()
    {
        var ex = InvokeClassifyAndThrow<RateLimitException>("rate limit exceeded by upstream");
        Assert.Contains("Rate limit", ex.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void ClassifyAndThrow_502Message_ThrowsServerErrorExceptionWithStatusCode()
    {
        var ex = InvokeClassifyAndThrow<ServerErrorException>("HTTP 502 Bad Gateway");
        Assert.Equal(502, ex.StatusCode);
    }

    [Fact]
    public void LoadSidecar_FileNotFound_ReturnsNull()
    {
        var imagePath = Path.Combine(_tempDir, "missing.jpg");
        var loaded = SidecarMerger.LoadSidecar(imagePath, _tempDir);
        Assert.Null(loaded);
    }

    [Fact]
    public void LoadSidecar_CorruptJson_ThrowsCopilotAnalysisException()
    {
        var imagePath = Path.Combine(_tempDir, "meme.jpg");
        var sidecarDir = OutputPaths.GetSidecarDir(_tempDir);
        Directory.CreateDirectory(sidecarDir);
        File.WriteAllText(Path.Combine(sidecarDir, "meme.jpg.json"), """{"emojis":["😂"]""");

        Assert.Throws<CopilotAnalysisException>(() => SidecarMerger.LoadSidecar(imagePath, _tempDir));
    }

    [Fact]
    public void LoadSidecar_MissingRequiredEmojis_ThrowsCopilotAnalysisException()
    {
        // SidecarMetadata.Emojis is 'required' — .NET 8 STJ enforces this during
        // deserialization, so a JSON object without "emojis" throws JsonException,
        // which LoadSidecar wraps as CopilotAnalysisException.
        var imagePath = Path.Combine(_tempDir, "schema.jpg");
        var sidecarDir = OutputPaths.GetSidecarDir(_tempDir);
        Directory.CreateDirectory(sidecarDir);
        File.WriteAllText(Path.Combine(sidecarDir, "schema.jpg.json"), """{"unknown":"value"}""");

        Assert.Throws<CopilotAnalysisException>(() => SidecarMerger.LoadSidecar(imagePath, _tempDir));
    }

    [Fact]
    public void ManifestLoad_FileNotFound_ReturnsEmptyManifest()
    {
        var manifest = ManifestService.Load(_tempDir);
        Assert.Empty(manifest.Images);
    }

    [Fact]
    public void ManifestLoad_CorruptJson_ReturnsEmptyManifest()
    {
        File.WriteAllText(Path.Combine(_tempDir, BuildManifest.FileName), "{broken");
        var manifest = ManifestService.Load(_tempDir);
        Assert.Empty(manifest.Images);
    }

    [Fact]
    public void ManifestLoad_EmptyFile_ReturnsEmptyManifest()
    {
        File.WriteAllText(Path.Combine(_tempDir, BuildManifest.FileName), string.Empty);
        var manifest = ManifestService.Load(_tempDir);
        Assert.Empty(manifest.Images);
    }

    [Fact]
    public void ManifestSave_AtomicWrite_RemovesTempFileAfterSuccess()
    {
        ManifestService.Save(_tempDir, new BuildManifest());
        var path = Path.Combine(_tempDir, BuildManifest.FileName);
        Assert.True(File.Exists(path));
        Assert.False(File.Exists(path + ".tmp"));
    }

    [Fact]
    public void IsSupportedImage_FileDoesNotExist_ReturnsFalse()
    {
        var path = Path.Combine(_tempDir, "missing.png");
        Assert.False(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void IsSupportedImage_FileUnder4Bytes_ReturnsFalse()
    {
        var path = Path.Combine(_tempDir, "tiny.png");
        File.WriteAllBytes(path, [0x89, 0x50, 0x4E]);
        Assert.False(SidecarService.IsSupportedImage(path));
    }

    [Fact]
    public void HasSidecar_NeitherLocationHasSidecar_ReturnsFalse()
    {
        var imagePath = Path.Combine(_tempDir, "nosidecar.jpg");
        Assert.False(SidecarService.HasSidecar(imagePath, _tempDir));
    }

    [Fact]
    public void ResolveSidecarPath_NeitherLocationHasSidecar_ReturnsNull()
    {
        var imagePath = Path.Combine(_tempDir, "nosidecar2.jpg");
        Assert.Null(SidecarService.ResolveSidecarPath(imagePath, _tempDir));
    }

    [Fact]
    public void OptimizeForApi_CorruptImage_ThrowsUnknownImageFormatException()
    {
        var corruptPath = Path.Combine(_tempDir, "corrupt-api.jpg");
        File.WriteAllBytes(corruptPath, [0x00, 0x01, 0x02, 0x03, 0x04]);

        Assert.Throws<UnknownImageFormatException>(() =>
            ImageOptimizer.OptimizeForApi(corruptPath, _tempDir));
    }

    [Fact]
    public void OptimizeForBundle_CorruptImage_ThrowsUnknownImageFormatException()
    {
        var corruptPath = Path.Combine(_tempDir, "corrupt-bundle.jpg");
        File.WriteAllBytes(corruptPath, [0x00, 0x01, 0x02, 0x03, 0x04]);

        Assert.Throws<UnknownImageFormatException>(() =>
            ImageOptimizer.OptimizeForBundle(corruptPath, _tempDir));
    }

    [Fact]
    public void CreateBundle_FolderParentNull_ThrowsArgumentException()
    {
        var root = new DirectoryInfo(Path.GetPathRoot(_tempDir)!);
        Assert.Null(root.Parent);

        Assert.Throws<ArgumentException>(() =>
            ZipBundler.CreateBundle(
                ZipMode.Full,
                root,
                _tempDir,
                allImages: [],
                plans: [],
                processed: [],
                bundleOptimizedMap: null,
                manifest: new BuildManifest()));
    }

    [Fact]
    public void SelectImagesForBundle_InvalidZipMode_ThrowsArgumentOutOfRangeException()
    {
        Assert.Throws<ArgumentOutOfRangeException>(() =>
            ZipBundler.SelectImagesForBundle(
                (ZipMode)999,
                allImages: [],
                outputDir: _tempDir,
                plans: [],
                processed: [],
                manifest: new BuildManifest()));
    }

    [Fact]
    public void ClassifyAndThrow_GenericMessage_ThrowsCopilotAnalysisException()
    {
        // Messages that don't match rate-limit or server-error patterns fall through
        // to the default CopilotAnalysisException on line 310 of CopilotService.cs.
        var ex = InvokeClassifyAndThrow<CopilotAnalysisException>("Something unexpected happened");
        Assert.Contains("Copilot error:", ex.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void ClassifyAndThrow_429Numeric_ThrowsRateLimitException()
    {
        // The regex \b429\b path (line 288), distinct from the "rate limit" text path.
        var ex = InvokeClassifyAndThrow<RateLimitException>("Error code 429 from upstream");
        Assert.Contains("Rate limit", ex.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void ClassifyAndThrow_TextServerError_ThrowsServerErrorExceptionWith503()
    {
        // Text-based server error path (lines 303-308): no numeric status code in message,
        // but matches Contains("gateway timeout"). Falls through the regex check, hits the
        // text pattern branch, and throws with default StatusCode 503.
        var ex = InvokeClassifyAndThrow<ServerErrorException>("The server returned gateway timeout");
        Assert.Equal(503, ex.StatusCode);
    }

    [Fact]
    public void ParseResponseContent_InvalidJson_ThrowsCopilotAnalysisException()
    {
        // Exercises the catch(JsonException) path, not the null/missing-emojis path.
        var ex = Assert.Throws<CopilotAnalysisException>(() =>
            CopilotService.ParseResponseContent("not json at all"));

        Assert.Contains("Failed to parse", ex.Message, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void ParsePartialResponse_InvalidJson_ThrowsCopilotAnalysisException()
    {
        // Same JsonException path but in the partial response parser.
        var ex = Assert.Throws<CopilotAnalysisException>(() =>
            CopilotService.ParsePartialResponse("not json", [PromptHasher.GroupCore]));

        Assert.Contains("Failed to parse partial", ex.Message, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void IsSupportedImage_UnsupportedExtension_ReturnsFalse()
    {
        // Extension check (line 43-44) rejects before magic-byte inspection.
        var path = Path.Combine(_tempDir, "document.txt");
        File.WriteAllText(path, "not an image");
        Assert.False(SidecarService.IsSupportedImage(path));
    }

    private static TException InvokeClassifyAndThrow<TException>(string message) where TException : Exception
    {
        var method = typeof(CopilotService).GetMethod("ClassifyAndThrow", BindingFlags.NonPublic | BindingFlags.Static);
        Assert.NotNull(method);

        var invocationException = Assert.Throws<TargetInvocationException>(
            () => method!.Invoke(null, [new Exception(message), null]));

        Assert.NotNull(invocationException.InnerException);
        return Assert.IsType<TException>(invocationException.InnerException);
    }
}
