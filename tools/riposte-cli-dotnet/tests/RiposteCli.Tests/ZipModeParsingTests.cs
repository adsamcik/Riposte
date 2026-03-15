using System.CommandLine;
using RiposteCli.Commands;

namespace RiposteCli.Tests;

public class ZipModeParsingTests
{
    #region ParseZipMode Unit Tests

    [Fact]
    public void ParseZipMode_Null_ReturnsNull()
    {
        var result = AnnotateCommand.ParseZipMode(null);
        Assert.Null(result);
    }

    [Fact]
    public void ParseZipMode_EmptyString_ReturnsFull()
    {
        var result = AnnotateCommand.ParseZipMode("");
        Assert.Equal(ZipMode.Full, result);
    }

    [Fact]
    public void ParseZipMode_Full_ReturnsFull()
    {
        var result = AnnotateCommand.ParseZipMode("full");
        Assert.Equal(ZipMode.Full, result);
    }

    [Fact]
    public void ParseZipMode_FullUpperCase_ReturnsFull()
    {
        var result = AnnotateCommand.ParseZipMode("Full");
        Assert.Equal(ZipMode.Full, result);
    }

    [Fact]
    public void ParseZipMode_FULL_ReturnsFull()
    {
        var result = AnnotateCommand.ParseZipMode("FULL");
        Assert.Equal(ZipMode.Full, result);
    }

    [Fact]
    public void ParseZipMode_Patch_ReturnsPatch()
    {
        var result = AnnotateCommand.ParseZipMode("patch");
        Assert.Equal(ZipMode.Patch, result);
    }

    [Fact]
    public void ParseZipMode_Delta_ReturnsPatch()
    {
        var result = AnnotateCommand.ParseZipMode("delta");
        Assert.Equal(ZipMode.Patch, result);
    }

    [Fact]
    public void ParseZipMode_True_ReturnsFull()
    {
        // Bare --zip flag compatibility: System.CommandLine may pass "true" for boolean-style flags
        var result = AnnotateCommand.ParseZipMode("true");
        Assert.Equal(ZipMode.Full, result);
    }

    [Fact]
    public void ParseZipMode_Invalid_ThrowsArgumentException()
    {
        var ex = Assert.Throws<ArgumentException>(() => AnnotateCommand.ParseZipMode("invalid"));
        Assert.Contains("invalid", ex.Message);
        Assert.Contains("full", ex.Message);
        Assert.Contains("patch", ex.Message);
    }

    [Fact]
    public void ParseZipMode_Whitespace_ThrowsArgumentException()
    {
        Assert.Throws<ArgumentException>(() => AnnotateCommand.ParseZipMode(" "));
    }

    #endregion

    #region Command Parsing Integration Tests

    private static RootCommand CreateRoot()
    {
        return new RootCommand("test") { AnnotateCommand.Create() };
    }

    [Fact]
    public void Command_ParsesZipFull_NoErrors()
    {
        var root = CreateRoot();
        var result = root.Parse("annotate . --zip full");
        Assert.Empty(result.Errors);
    }

    [Fact]
    public void Command_ParsesZipPatch_NoErrors()
    {
        var root = CreateRoot();
        var result = root.Parse("annotate . --zip patch");
        Assert.Empty(result.Errors);
    }

    [Fact]
    public void Command_ParsesBareZip_NoErrors()
    {
        // With ZeroOrOne arity, --zip without a value should parse without errors
        var root = CreateRoot();
        var result = root.Parse("annotate . --zip");
        Assert.Empty(result.Errors);
    }

    [Fact]
    public void Command_ParsesWithoutZip_NoErrors()
    {
        var root = CreateRoot();
        var result = root.Parse("annotate .");
        Assert.Empty(result.Errors);
    }

    [Fact]
    public void Command_ZipOptionHasZeroOrOneArity()
    {
        var cmd = AnnotateCommand.Create();
        var zipOpt = cmd.Options.First(o => o.Name == "--zip");
        Assert.Equal(0, zipOpt.Arity.MinimumNumberOfValues);
        Assert.Equal(1, zipOpt.Arity.MaximumNumberOfValues);
    }

    #endregion
}
