using System.CommandLine;
using RiposteCli.Commands;
using RiposteCli.Models;

var rootCommand = new RootCommand($"Riposte CLI v{CliVersion.Current} - AI-powered meme annotation tool")
{
    AnnotateCommand.Create(),
    DedupeCommand.Create(),
    AuthCommand.Create(),
};

try
{
    return await rootCommand.Parse(args).InvokeAsync(new InvocationConfiguration(), CancellationToken.None);
}
catch (Exception ex)
{
    Console.Error.WriteLine();
    Console.Error.WriteLine($"Fatal error: {ex.GetType().Name}: {ex.Message}");
    Console.Error.WriteLine(ex.StackTrace);
    if (ex.InnerException is not null)
        Console.Error.WriteLine($"Inner: {ex.InnerException.GetType().Name}: {ex.InnerException.Message}");
    return 1;
}

