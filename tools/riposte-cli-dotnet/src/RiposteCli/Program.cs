using System.CommandLine;
using RiposteCli.Commands;
using RiposteCli.Models;

// Catch unobserved Task exceptions (fire-and-forget, GC'd tasks)
TaskScheduler.UnobservedTaskException += (_, e) =>
{
    Console.Error.WriteLine();
    Console.Error.WriteLine($"[{DateTime.Now:HH:mm:ss}] Unobserved task exception: {e.Exception.GetType().Name}: {e.Exception.Message}");
    foreach (var inner in e.Exception.Flatten().InnerExceptions)
        Console.Error.WriteLine($"  • {inner.GetType().Name}: {inner.Message}");
    e.SetObserved();
};

// Catch truly unhandled exceptions (thread crashes, finalizer errors)
AppDomain.CurrentDomain.UnhandledException += (_, e) =>
{
    var ex = e.ExceptionObject as Exception;
    Console.Error.WriteLine();
    Console.Error.WriteLine($"[{DateTime.Now:HH:mm:ss}] UNHANDLED EXCEPTION (terminating={e.IsTerminating}):");
    if (ex is not null)
    {
        Console.Error.WriteLine($"  {ex.GetType().Name}: {ex.Message}");
        Console.Error.WriteLine(ex.StackTrace);
    }
    else
    {
        Console.Error.WriteLine($"  Non-exception object: {e.ExceptionObject}");
    }
};

// Catch unhandled exceptions on the main async context
AppDomain.CurrentDomain.ProcessExit += (_, _) =>
{
    var exitCode = Environment.ExitCode;
    if (exitCode != 0)
        Console.Error.WriteLine($"[{DateTime.Now:HH:mm:ss}] Process exiting with code {exitCode}");
};

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
    Console.Error.WriteLine($"[{DateTime.Now:HH:mm:ss}] Fatal error: {ex.GetType().Name}: {ex.Message}");
    Console.Error.WriteLine(ex.StackTrace);
    if (ex.InnerException is not null)
        Console.Error.WriteLine($"  Inner: {ex.InnerException.GetType().Name}: {ex.InnerException.Message}");
    if (ex is AggregateException agg)
        foreach (var inner in agg.Flatten().InnerExceptions)
            Console.Error.WriteLine($"  • {inner.GetType().Name}: {inner.Message}");
    return 1;
}
