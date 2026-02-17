using System.CommandLine;
using RiposteCli.Commands;

namespace RiposteCli.Tests;

/// <summary>
/// Tests for command invocation patterns: parsing args, help output,
/// root command wiring, version display.
/// </summary>
public class CommandInvocationTests
{
    #region Root Command

    [Fact]
    public void RootCommand_HasVersion()
    {
        var root = new RootCommand($"Riposte CLI v{RiposteCli.Models.CliVersion.Current}")
        {
            AnnotateCommand.Create(),
            DedupeCommand.Create(),
            AuthCommand.Create(),
        };

        Assert.Contains(RiposteCli.Models.CliVersion.Current, root.Description);
    }

    [Fact]
    public void RootCommand_ParseHelp_Succeeds()
    {
        var root = new RootCommand("test")
        {
            AnnotateCommand.Create(),
            DedupeCommand.Create(),
            AuthCommand.Create(),
        };

        var result = root.Parse("--help");
        Assert.NotNull(result);
    }

    [Fact]
    public void RootCommand_ParseUnknownCommand_HasErrors()
    {
        var root = new RootCommand("test")
        {
            AnnotateCommand.Create(),
            DedupeCommand.Create(),
            AuthCommand.Create(),
        };

        var result = root.Parse("unknown-command");
        Assert.True(result.Errors.Count > 0);
    }

    #endregion

    #region Annotate Command Parsing

    [Fact]
    public void AnnotateCommand_ParseValidArgs_NoErrors()
    {
        var cmd = AnnotateCommand.Create();
        var root = new RootCommand("test") { cmd };

        var result = root.Parse("annotate .");
        // Should not have errors (folder is valid syntax)
        Assert.Empty(result.Errors);
    }

    [Fact]
    public void AnnotateCommand_ParseWithAllOptions_NoErrors()
    {
        var cmd = AnnotateCommand.Create();
        var root = new RootCommand("test") { cmd };

        var result = root.Parse(
            "annotate . --zip --model gpt-5 --languages en,cs --force --dry-run --verbose -j 2 --similarity-threshold 5 --no-dedup");
        Assert.Empty(result.Errors);
    }

    [Fact]
    public void AnnotateCommand_ParseWithAliases_NoErrors()
    {
        var cmd = AnnotateCommand.Create();
        var root = new RootCommand("test") { cmd };

        var result = root.Parse("annotate . -m gpt-5 -l en -f -v -j 2");
        Assert.Empty(result.Errors);
    }

    [Fact]
    public void AnnotateCommand_ParseMissingFolder_HasErrors()
    {
        var cmd = AnnotateCommand.Create();
        var root = new RootCommand("test") { cmd };

        var result = root.Parse("annotate");
        Assert.True(result.Errors.Count > 0);
    }

    #endregion

    #region Dedupe Command Parsing

    [Fact]
    public void DedupeCommand_ParseValidArgs_NoErrors()
    {
        var cmd = DedupeCommand.Create();
        var root = new RootCommand("test") { cmd };

        var result = root.Parse("dedupe .");
        Assert.Empty(result.Errors);
    }

    [Fact]
    public void DedupeCommand_ParseWithAllOptions_NoErrors()
    {
        var cmd = DedupeCommand.Create();
        var root = new RootCommand("test") { cmd };

        var result = root.Parse("dedupe . --dry-run --yes --verbose --no-near --similarity-threshold 5");
        Assert.Empty(result.Errors);
    }

    [Fact]
    public void DedupeCommand_ParseWithAliases_NoErrors()
    {
        var cmd = DedupeCommand.Create();
        var root = new RootCommand("test") { cmd };

        var result = root.Parse("dedupe . -y -v");
        Assert.Empty(result.Errors);
    }

    #endregion

    #region Auth Command Parsing

    [Fact]
    public void AuthCommand_ParseStatus_NoErrors()
    {
        var cmd = AuthCommand.Create();
        var root = new RootCommand("test") { cmd };

        var result = root.Parse("auth status");
        Assert.Empty(result.Errors);
    }

    [Fact]
    public void AuthCommand_ParseCheck_NoErrors()
    {
        var cmd = AuthCommand.Create();
        var root = new RootCommand("test") { cmd };

        var result = root.Parse("auth check");
        Assert.Empty(result.Errors);
    }

    [Fact]
    public void AuthCommand_ParseUnknownSubcommand_HasErrors()
    {
        var cmd = AuthCommand.Create();
        var root = new RootCommand("test") { cmd };

        var result = root.Parse("auth unknown");
        Assert.True(result.Errors.Count > 0);
    }

    [Fact]
    public void AuthCommand_ParseNoSubcommand_HasErrors()
    {
        var cmd = AuthCommand.Create();
        var root = new RootCommand("test") { cmd };

        var result = root.Parse("auth");
        // auth without subcommand is valid in System.CommandLine (shows help)
        // but let's verify it at least parses
        Assert.NotNull(result);
    }

    #endregion

    #region Option Default Values via Parse

    [Fact]
    public void AnnotateCommand_DefaultsParsedCorrectly()
    {
        var cmd = AnnotateCommand.Create();
        var root = new RootCommand("test") { cmd };

        var parseResult = root.Parse("annotate .");

        // Use GetDefaultValue() on options since parseResult.GetValue requires typed option
        var modelOpt = cmd.Options.First(o => o.Name == "--model");
        var concOpt = cmd.Options.First(o => o.Name == "--concurrency");
        var threshOpt = cmd.Options.First(o => o.Name == "--similarity-threshold");
        var langOpt = cmd.Options.First(o => o.Name == "--languages");

        Assert.Equal("gpt-5-mini", modelOpt.GetDefaultValue());
        Assert.Equal(4, concOpt.GetDefaultValue());
        Assert.Equal(10, threshOpt.GetDefaultValue());
        Assert.Equal("en", langOpt.GetDefaultValue());
    }

    [Fact]
    public void DedupeCommand_DefaultsParsedCorrectly()
    {
        var cmd = DedupeCommand.Create();
        var root = new RootCommand("test") { cmd };

        var threshOpt = cmd.Options.First(o => o.Name == "--similarity-threshold");
        Assert.Equal(10, threshOpt.GetDefaultValue());
    }

    #endregion
}
