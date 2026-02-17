using System.CommandLine;
using RiposteCli.Services;
using Spectre.Console;

namespace RiposteCli.Commands;

public static class DedupeCommand
{
    public static Command Create()
    {
        var folderArg = new Argument<DirectoryInfo>("folder") { Description = "Path to a directory containing images to deduplicate" };
        var outputOpt = new Option<DirectoryInfo?>("--output", "-o") { Description = "Directory where sidecar files are stored" };
        var thresholdOpt = new Option<int>("--similarity-threshold") { Description = "Max Hamming distance for near-duplicate detection (0-256)", DefaultValueFactory = _ => 10 };
        var noNearOpt = new Option<bool>("--no-near") { Description = "Only remove exact duplicates" };
        var dryRunOpt = new Option<bool>("--dry-run") { Description = "Show duplicates without deleting anything" };
        var yesOpt = new Option<bool>("--yes", "-y") { Description = "Skip confirmation prompt" };
        var verboseOpt = new Option<bool>("--verbose", "-v") { Description = "Show detailed output" };

        var command = new Command("dedupe", "Find and remove duplicate images in a folder")
        {
            folderArg, outputOpt, thresholdOpt, noNearOpt, dryRunOpt, yesOpt, verboseOpt,
        };

        command.SetAction((parseResult) =>
        {
            var folder = parseResult.GetValue(folderArg)!;
            var output = parseResult.GetValue(outputOpt);
            var threshold = parseResult.GetValue(thresholdOpt);
            var noNear = parseResult.GetValue(noNearOpt);
            var dryRun = parseResult.GetValue(dryRunOpt);
            var yes = parseResult.GetValue(yesOpt);
            var verbose = parseResult.GetValue(verboseOpt);
            Execute(folder, output, threshold, noNear, dryRun, yes, verbose);
        });

        return command;
    }

    private static void Execute(
        DirectoryInfo folder, DirectoryInfo? output, int threshold,
        bool noNear, bool dryRun, bool yes, bool verbose)
    {
        var outputDir = output?.FullName ?? folder.FullName;
        var detectNear = !noNear;

        var images = SidecarService.GetImagesInFolder(folder.FullName);
        if (images.Count == 0)
        {
            AnsiConsole.MarkupLine($"[yellow]No supported images found in {folder.FullName}[/]");
            return;
        }

        AnsiConsole.MarkupLine($"[dim]Scanning {images.Count} image(s) for duplicates...[/]");

        var manifest = ImageHashService.LoadManifest(outputDir);
        var result = ImageHashService.Deduplicate(images, manifest,
            detectNearDuplicates: detectNear, similarityThreshold: threshold, verbose: verbose);
        ImageHashService.SaveManifest(outputDir, manifest);

        var exactDupes = result.ExactDuplicates;
        var nearDupes = result.NearDuplicates;

        if (exactDupes.Count == 0 && nearDupes.Count == 0)
        {
            AnsiConsole.MarkupLine("[green]✓ No duplicates found![/]");
            return;
        }

        // Display results
        if (exactDupes.Count > 0)
        {
            var table = new Table().Title("Exact Duplicates").ShowRowSeparators();
            table.AddColumn(new TableColumn("[red]Duplicate (will be removed)[/]"));
            table.AddColumn(new TableColumn("[green]Original (will be kept)[/]"));
            foreach (var (dupe, original) in exactDupes)
                table.AddRow(Path.GetFileName(dupe), Path.GetFileName(original));
            AnsiConsole.Write(table);
            AnsiConsole.WriteLine();
        }

        if (nearDupes.Count > 0)
        {
            var table = new Table().Title("Near Duplicates").ShowRowSeparators();
            table.AddColumn(new TableColumn("[red]Duplicate (will be removed)[/]"));
            table.AddColumn(new TableColumn("[green]Original (will be kept)[/]"));
            table.AddColumn(new TableColumn("[yellow]Distance[/]") { Alignment = Justify.Right });
            foreach (var (dupe, original, distance) in nearDupes)
                table.AddRow(Path.GetFileName(dupe), Path.GetFileName(original), distance.ToString());
            AnsiConsole.Write(table);
            AnsiConsole.WriteLine();
        }

        var total = exactDupes.Count + nearDupes.Count;
        AnsiConsole.MarkupLine($"[bold]Found {total} duplicate(s)[/]: {exactDupes.Count} exact, {nearDupes.Count} near");

        if (dryRun)
        {
            AnsiConsole.MarkupLine("[dim]Dry run — no files were deleted[/]");
            return;
        }

        // Confirm
        if (!yes)
        {
            if (!AnsiConsole.Confirm($"Delete {total} duplicate image(s) and their sidecars?", defaultValue: false))
                return;
        }

        // Delete duplicates
        var deletedCount = 0;
        foreach (var (dupe, _) in exactDupes)
            deletedCount += DeleteImageAndSidecar(dupe, outputDir, verbose);
        foreach (var (dupe, _, _) in nearDupes)
            deletedCount += DeleteImageAndSidecar(dupe, outputDir, verbose);

        // Clean up manifest
        foreach (var (dupe, _) in exactDupes)
            manifest.Remove(Path.GetFileName(dupe));
        foreach (var (dupe, _, _) in nearDupes)
            manifest.Remove(Path.GetFileName(dupe));
        ImageHashService.SaveManifest(outputDir, manifest);

        AnsiConsole.MarkupLine($"\n[green]✓ Removed {total} duplicate(s) ({deletedCount} file(s) deleted)[/]");
    }

    private static int DeleteImageAndSidecar(string imagePath, string outputDir, bool verbose)
    {
        var deleted = 0;
        var sidecarPath = Path.Combine(outputDir, Path.GetFileName(imagePath) + ".json");

        if (File.Exists(imagePath))
        {
            File.Delete(imagePath);
            deleted++;
            if (verbose) AnsiConsole.MarkupLine($"  [dim]Deleted {Path.GetFileName(imagePath)}[/]");
        }

        if (File.Exists(sidecarPath))
        {
            File.Delete(sidecarPath);
            deleted++;
            if (verbose) AnsiConsole.MarkupLine($"  [dim]Deleted {Path.GetFileName(sidecarPath)}[/]");
        }

        return deleted;
    }
}
