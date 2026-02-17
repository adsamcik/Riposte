using System.CommandLine;
using RiposteCli.Commands;

namespace RiposteCli.Tests;

public class CommandStructureTests
{
    #region Annotate Command

    [Fact]
    public void AnnotateCommand_HasCorrectName()
    {
        var cmd = AnnotateCommand.Create();
        Assert.Equal("annotate", cmd.Name);
    }

    [Fact]
    public void AnnotateCommand_HasDescription()
    {
        var cmd = AnnotateCommand.Create();
        Assert.NotEmpty(cmd.Description!);
        Assert.Contains("Annotate", cmd.Description, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void AnnotateCommand_HasFolderArgument()
    {
        var cmd = AnnotateCommand.Create();
        var arg = cmd.Arguments.FirstOrDefault();
        Assert.NotNull(arg);
        Assert.Equal("folder", arg.Name);
    }

    [Fact]
    public void AnnotateCommand_HasAllExpectedOptions()
    {
        var cmd = AnnotateCommand.Create();
        var optionNames = cmd.Options.Select(o => o.Name).ToList();

        Assert.Contains("--zip", optionNames);
        Assert.Contains("--output", optionNames);
        Assert.Contains("--model", optionNames);
        Assert.Contains("--languages", optionNames);
        Assert.Contains("--force", optionNames);
        Assert.Contains("--continue", optionNames);
        Assert.Contains("--add-new", optionNames);
        Assert.Contains("--no-dedup", optionNames);
        Assert.Contains("--similarity-threshold", optionNames);
        Assert.Contains("--dry-run", optionNames);
        Assert.Contains("--verbose", optionNames);
        Assert.Contains("--concurrency", optionNames);
    }

    [Fact]
    public void AnnotateCommand_ModelDefaultsToGpt5Mini()
    {
        var cmd = AnnotateCommand.Create();
        var modelOpt = cmd.Options.First(o => o.Name == "--model");
        var defaultValue = modelOpt.GetDefaultValue();
        Assert.Equal("gpt-5-mini", defaultValue);
    }

    [Fact]
    public void AnnotateCommand_LanguagesDefaultsToEn()
    {
        var cmd = AnnotateCommand.Create();
        var langOpt = cmd.Options.First(o => o.Name == "--languages");
        var defaultValue = langOpt.GetDefaultValue();
        Assert.Equal("en", defaultValue);
    }

    [Fact]
    public void AnnotateCommand_ConcurrencyDefaultsTo4()
    {
        var cmd = AnnotateCommand.Create();
        var concOpt = cmd.Options.First(o => o.Name == "--concurrency");
        var defaultValue = concOpt.GetDefaultValue();
        Assert.Equal(4, defaultValue);
    }

    [Fact]
    public void AnnotateCommand_ThresholdDefaultsTo10()
    {
        var cmd = AnnotateCommand.Create();
        var threshOpt = cmd.Options.First(o => o.Name == "--similarity-threshold");
        var defaultValue = threshOpt.GetDefaultValue();
        Assert.Equal(10, defaultValue);
    }

    [Fact]
    public void AnnotateCommand_OutputHasAlias()
    {
        var cmd = AnnotateCommand.Create();
        var outputOpt = cmd.Options.First(o => o.Name == "--output");
        Assert.Contains("-o", outputOpt.Aliases);
    }

    [Fact]
    public void AnnotateCommand_VerboseHasAlias()
    {
        var cmd = AnnotateCommand.Create();
        var verboseOpt = cmd.Options.First(o => o.Name == "--verbose");
        Assert.Contains("-v", verboseOpt.Aliases);
    }

    [Fact]
    public void AnnotateCommand_ConcurrencyHasAlias()
    {
        var cmd = AnnotateCommand.Create();
        var concOpt = cmd.Options.First(o => o.Name == "--concurrency");
        Assert.Contains("-j", concOpt.Aliases);
    }

    [Fact]
    public void AnnotateCommand_ModelHasAlias()
    {
        var cmd = AnnotateCommand.Create();
        var modelOpt = cmd.Options.First(o => o.Name == "--model");
        Assert.Contains("-m", modelOpt.Aliases);
    }

    [Fact]
    public void AnnotateCommand_LanguagesHasAlias()
    {
        var cmd = AnnotateCommand.Create();
        var langOpt = cmd.Options.First(o => o.Name == "--languages");
        Assert.Contains("-l", langOpt.Aliases);
    }

    [Fact]
    public void AnnotateCommand_ForceHasAlias()
    {
        var cmd = AnnotateCommand.Create();
        var forceOpt = cmd.Options.First(o => o.Name == "--force");
        Assert.Contains("-f", forceOpt.Aliases);
    }

    #endregion

    #region Dedupe Command

    [Fact]
    public void DedupeCommand_HasCorrectName()
    {
        var cmd = DedupeCommand.Create();
        Assert.Equal("dedupe", cmd.Name);
    }

    [Fact]
    public void DedupeCommand_HasDescription()
    {
        var cmd = DedupeCommand.Create();
        Assert.NotEmpty(cmd.Description!);
    }

    [Fact]
    public void DedupeCommand_HasFolderArgument()
    {
        var cmd = DedupeCommand.Create();
        var arg = cmd.Arguments.FirstOrDefault();
        Assert.NotNull(arg);
        Assert.Equal("folder", arg.Name);
    }

    [Fact]
    public void DedupeCommand_HasAllExpectedOptions()
    {
        var cmd = DedupeCommand.Create();
        var optionNames = cmd.Options.Select(o => o.Name).ToList();

        Assert.Contains("--output", optionNames);
        Assert.Contains("--similarity-threshold", optionNames);
        Assert.Contains("--no-near", optionNames);
        Assert.Contains("--dry-run", optionNames);
        Assert.Contains("--yes", optionNames);
        Assert.Contains("--verbose", optionNames);
    }

    [Fact]
    public void DedupeCommand_YesHasAlias()
    {
        var cmd = DedupeCommand.Create();
        var yesOpt = cmd.Options.First(o => o.Name == "--yes");
        Assert.Contains("-y", yesOpt.Aliases);
    }

    #endregion

    #region Auth Command

    [Fact]
    public void AuthCommand_HasCorrectName()
    {
        var cmd = AuthCommand.Create();
        Assert.Equal("auth", cmd.Name);
    }

    [Fact]
    public void AuthCommand_HasStatusSubcommand()
    {
        var cmd = AuthCommand.Create();
        var status = cmd.Subcommands.FirstOrDefault(c => c.Name == "status");
        Assert.NotNull(status);
    }

    [Fact]
    public void AuthCommand_HasCheckSubcommand()
    {
        var cmd = AuthCommand.Create();
        var check = cmd.Subcommands.FirstOrDefault(c => c.Name == "check");
        Assert.NotNull(check);
    }

    [Fact]
    public void AuthCommand_HasExactlyTwoSubcommands()
    {
        var cmd = AuthCommand.Create();
        Assert.Equal(2, cmd.Subcommands.Count);
    }

    #endregion

    #region Root Command Structure

    [Fact]
    public void RootCommand_HasThreeSubcommands()
    {
        var root = new RootCommand("test")
        {
            AnnotateCommand.Create(),
            DedupeCommand.Create(),
            AuthCommand.Create(),
        };

        Assert.Equal(3, root.Subcommands.Count);
    }

    [Fact]
    public void RootCommand_SubcommandNamesCorrect()
    {
        var root = new RootCommand("test")
        {
            AnnotateCommand.Create(),
            DedupeCommand.Create(),
            AuthCommand.Create(),
        };

        var names = root.Subcommands.Select(c => c.Name).ToList();
        Assert.Contains("annotate", names);
        Assert.Contains("dedupe", names);
        Assert.Contains("auth", names);
    }

    #endregion
}
