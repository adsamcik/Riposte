using RiposteCli.Commands;
using System.CommandLine;

namespace RiposteCli.Tests;

public class AuthCommandTests
{
    [Fact]
    public void Create_HasAuthName()
    {
        var command = AuthCommand.Create();

        Assert.Equal("auth", command.Name);
    }

    [Fact]
    public void Create_HasStatusSubcommand()
    {
        var command = AuthCommand.Create();

        Assert.Contains(command.Subcommands, sub => sub.Name == "status");
    }

    [Fact]
    public void Create_HasCheckSubcommandOrAlias()
    {
        var command = AuthCommand.Create();

        var hasCheckByName = command.Subcommands.Any(sub => sub.Name == "check");
        var hasCheckByAlias = command.Subcommands.Any(sub => sub.Aliases.Contains("check"));

        Assert.True(hasCheckByName || hasCheckByAlias);
    }

    [Fact]
    public void Parse_AuthStatus_HasNoErrors()
    {
        var root = new RootCommand("test") { AuthCommand.Create() };

        var result = root.Parse("auth status");

        Assert.Empty(result.Errors);
    }

    [Fact]
    public void Parse_AuthCheck_HasNoErrors()
    {
        var root = new RootCommand("test") { AuthCommand.Create() };

        var result = root.Parse("auth check");

        Assert.Empty(result.Errors);
    }

    [Fact]
    public void Create_HasNoUnexpectedSubcommands()
    {
        var command = AuthCommand.Create();
        var names = command.Subcommands.Select(s => s.Name).ToList();

        Assert.Equal(2, names.Count);
        Assert.Contains("status", names);
        Assert.Contains("check", names);
    }

    [Fact]
    public void Parse_AuthAlone_IsGraceful()
    {
        var root = new RootCommand("test") { AuthCommand.Create() };

        var result = root.Parse("auth");

        Assert.NotNull(result);
    }

    [Fact]
    public void Parse_AuthInvalid_HasError()
    {
        var root = new RootCommand("test") { AuthCommand.Create() };

        var result = root.Parse("auth invalid");

        Assert.NotEmpty(result.Errors);
    }
}
