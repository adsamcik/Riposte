using System.CommandLine;
using System.CommandLine.Parsing;
using RiposteCli.Commands;

namespace RiposteCli.Tests;

public class CommandStructureZipTests
{
    private static RootCommand CreateRoot()
    {
        var root = new RootCommand("test") { AnnotateCommand.Create() };
        return root;
    }

    [Fact]
    public void AnnotateCommand_HasZipOption()
    {
        var cmd = AnnotateCommand.Create();
        var zipOpt = cmd.Options.FirstOrDefault(o => o.Name == "--zip");

        Assert.NotNull(zipOpt);
        Assert.Contains("ZIP bundle", zipOpt.Description, StringComparison.OrdinalIgnoreCase);
    }

    [Theory]
    [InlineData("full")]
    [InlineData("patch")]
    public void ZipOption_AcceptsValidModes(string mode)
    {
        var root = CreateRoot();
        var result = root.Parse($"annotate . --zip {mode}");

        // No errors related to --zip parsing
        var zipErrors = result.Errors
            .Where(e => e.Message.Contains("zip", StringComparison.OrdinalIgnoreCase))
            .ToList();
        Assert.Empty(zipErrors);
    }

    [Fact]
    public void ZipOption_IsOptional_NullWhenNotSpecified()
    {
        var root = CreateRoot();
        var zipOpt = (Option<string?>)root.Subcommands.First().Options.First(o => o.Name == "--zip");
        var result = root.Parse("annotate .");

        var value = result.GetValue(zipOpt);
        Assert.Null(value);
    }

    [Fact]
    public void Command_ParsesZipPatchCorrectly()
    {
        var root = CreateRoot();
        var zipOpt = (Option<string?>)root.Subcommands.First().Options.First(o => o.Name == "--zip");
        var result = root.Parse("annotate . --zip patch");

        var value = result.GetValue(zipOpt);
        Assert.Equal("patch", value);
    }

    [Fact]
    public void Command_ParsesWithoutZipCorrectly()
    {
        var root = CreateRoot();
        var zipOpt = (Option<string?>)root.Subcommands.First().Options.First(o => o.Name == "--zip");
        var result = root.Parse("annotate .");

        var value = result.GetValue(zipOpt);
        Assert.Null(value);
    }
}
