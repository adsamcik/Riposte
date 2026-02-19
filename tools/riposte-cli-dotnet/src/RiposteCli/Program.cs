using System.CommandLine;
using RiposteCli.Commands;
using RiposteCli.Models;

var rootCommand = new RootCommand($"Riposte CLI v{CliVersion.Current} - AI-powered meme annotation tool")
{
    AnnotateCommand.Create(),
    DedupeCommand.Create(),
    AuthCommand.Create(),
};

return await rootCommand.Parse(args).InvokeAsync(new InvocationConfiguration(), CancellationToken.None);

