using System.CommandLine;
using System.Diagnostics;
using GitHub.Copilot.SDK;
using Spectre.Console;

namespace RiposteCli.Commands;

public static class AuthCommand
{
    public static Command Create()
    {
        var command = new Command("auth", "Manage Copilot CLI setup and status");

        var statusCmd = new Command("status", "Check Copilot CLI installation status");
        statusCmd.SetAction((parseResult) =>
        {
            AnsiConsole.MarkupLine("Checking Copilot CLI installation...");
            var (available, result) = CheckCopilotCli();
            if (available)
            {
                AnsiConsole.MarkupLine($"[green]✓ Copilot CLI installed: {result}[/]");
                AnsiConsole.MarkupLine("\nYou're ready to use meme-cli!");
                AnsiConsole.MarkupLine("Run: meme-cli annotate <folder>");
            }
            else
            {
                AnsiConsole.MarkupLine($"[red]❌ {result}[/]");
                AnsiConsole.MarkupLine("\nTo install Copilot CLI:");
                AnsiConsole.MarkupLine("  https://github.com/github/copilot-cli");
                AnsiConsole.MarkupLine("\nMake sure 'copilot' is in your PATH.");
            }
        });

        var checkCmd = new Command("check", "Verify Copilot CLI can connect");
        checkCmd.SetAction(async (parseResult, cancellationToken) =>
        {
            AnsiConsole.MarkupLine("Checking Copilot CLI connectivity...");
            var (available, version) = CheckCopilotCli();

            if (!available)
            {
                AnsiConsole.MarkupLine($"[red]❌ Copilot CLI not available: {version}[/]");
                AnsiConsole.MarkupLine("\nInstall from: https://github.com/github/copilot-cli");
                return;
            }

            AnsiConsole.MarkupLine($"[green]✓ Copilot CLI: {version}[/]");
            AnsiConsole.MarkupLine("Testing connection...");

            try
            {
                await using var client = new CopilotClient(new CopilotClientOptions());
                await client.StartAsync();
                AnsiConsole.MarkupLine("[green]✓ Connection successful[/]");
            }
            catch (Exception ex)
            {
                AnsiConsole.MarkupLine($"[red]❌ Connection failed: {ex.Message}[/]");
                AnsiConsole.MarkupLine("\nMake sure you're logged in to Copilot CLI:");
                AnsiConsole.MarkupLine("  copilot auth login");
            }
        });

        command.Subcommands.Add(statusCmd);
        command.Subcommands.Add(checkCmd);

        return command;
    }

    private static (bool Available, string Result) CheckCopilotCli()
    {
        try
        {
            var copilotPath = FindInPath("copilot");
            if (copilotPath is null)
                return (false, "Copilot CLI not found in PATH");

            var psi = new ProcessStartInfo("copilot", "--version")
            {
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
                CreateNoWindow = true,
            };

            using var process = Process.Start(psi);
            if (process is null)
                return (false, "Failed to start Copilot CLI");

            if (!process.WaitForExit(10_000))
                return (false, "CLI timed out");

            if (process.ExitCode == 0)
            {
                var version = (process.StandardOutput.ReadToEnd().Trim()
                    + process.StandardError.ReadToEnd().Trim()).Trim();
                return (true, version);
            }

            return (false, $"CLI returned error: {process.StandardError.ReadToEnd()}");
        }
        catch (Exception ex)
        {
            return (false, $"Error running CLI: {ex.Message}");
        }
    }

    private static string? FindInPath(string command)
    {
        var pathVar = Environment.GetEnvironmentVariable("PATH") ?? "";
        var ext = OperatingSystem.IsWindows() ? ".exe" : "";
        foreach (var dir in pathVar.Split(Path.PathSeparator))
        {
            var fullPath = Path.Combine(dir, command + ext);
            if (File.Exists(fullPath))
                return fullPath;
        }
        return null;
    }
}
