using System.CommandLine;
using System.IO.Compression;
using System.Diagnostics;
using GitHub.Copilot.SDK;
using RiposteCli.Models;
using RiposteCli.RateLimiting;
using RiposteCli.Services;
using Spectre.Console;

namespace RiposteCli.Commands;

public static class AnnotateCommand
{
    public static Command Create()
    {
        var folderArg = new Argument<DirectoryInfo>("folder") { Description = "Path to a directory containing images to annotate" };
        var zipOpt = new Option<bool>("--zip") { Description = "Bundle images and sidecars into a .meme.zip file" };
        var outputOpt = new Option<DirectoryInfo?>("--output", "-o") { Description = "Output directory for sidecar files" };
        var modelOpt = new Option<string>("--model", "-m") { Description = "Model to use for analysis", DefaultValueFactory = _ => "gpt-5-mini" };
        var languagesOpt = new Option<string>("--languages", "-l") { Description = "Comma-separated BCP 47 language codes (e.g., 'en,cs,de')", DefaultValueFactory = _ => "en" };
        var forceOpt = new Option<bool>("--force", "-f") { Description = "Force regeneration of all sidecars" };
        var continueOpt = new Option<bool>("--continue") { Description = "Only process images without existing sidecars" };
        var addNewOpt = new Option<bool>("--add-new") { Description = "Alias for --continue" };
        var noDedupOpt = new Option<bool>("--no-dedup") { Description = "Disable duplicate detection" };
        var thresholdOpt = new Option<int>("--similarity-threshold") { Description = "Max Hamming distance for near-duplicate detection (0-256)", DefaultValueFactory = _ => 10 };
        var dryRunOpt = new Option<bool>("--dry-run") { Description = "Show what would be processed" };
        var verboseOpt = new Option<bool>("--verbose", "-v") { Description = "Show detailed progress" };
        var concurrencyOpt = new Option<int>("--concurrency", "-j") { Description = "Max parallel API requests (1-10)", DefaultValueFactory = _ => 4 };

        var command = new Command("annotate", "Annotate images in a folder with AI-generated metadata")
        {
            folderArg, zipOpt, outputOpt, modelOpt, languagesOpt, forceOpt,
            continueOpt, addNewOpt, noDedupOpt, thresholdOpt, dryRunOpt, verboseOpt, concurrencyOpt,
        };

        command.SetAction(async (parseResult, cancellationToken) =>
        {
            var folder = parseResult.GetValue(folderArg)!;
            var createZip = parseResult.GetValue(zipOpt);
            var output = parseResult.GetValue(outputOpt);
            var model = parseResult.GetValue(modelOpt)!;
            var languages = parseResult.GetValue(languagesOpt)!;
            var force = parseResult.GetValue(forceOpt);
            var continueMissing = parseResult.GetValue(continueOpt);
            var addNew = parseResult.GetValue(addNewOpt);
            var noDedup = parseResult.GetValue(noDedupOpt);
            var threshold = parseResult.GetValue(thresholdOpt);
            var dryRun = parseResult.GetValue(dryRunOpt);
            var verbose = parseResult.GetValue(verboseOpt);
            var concurrency = Math.Clamp(parseResult.GetValue(concurrencyOpt), 1, 10);

            await ExecuteAsync(folder, createZip, output, model, languages, force,
                continueMissing, addNew, noDedup, threshold, dryRun, verbose, concurrency);
        });

        return command;
    }

    private static async Task ExecuteAsync(
        DirectoryInfo folder, bool createZip, DirectoryInfo? output, string model,
        string languages, bool force, bool continueMissing, bool addNew,
        bool noDedup, int threshold, bool dryRun, bool verbose, int concurrency)
    {
        var languageList = languages.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
            .ToList();
        if (languageList.Count == 0) languageList = ["en"];
        var primaryLanguage = languageList[0];

        if (force && (continueMissing || addNew))
        {
            AnsiConsole.MarkupLine("[red]Error: --force cannot be used with --continue or --add-new[/]");
            return;
        }

        var outputDir = output?.FullName ?? folder.FullName;
        Directory.CreateDirectory(outputDir);

        // Find images
        var allImages = SidecarService.GetImagesInFolder(folder.FullName);
        if (allImages.Count == 0)
        {
            AnsiConsole.MarkupLine($"[yellow]No supported images found in {folder.FullName}[/]");
            return;
        }

        // Filter
        var (images, skipped) = SidecarService.FilterImagesByMode(allImages, outputDir, force);

        // Deduplication
        var manifest = ImageHashService.LoadManifest(outputDir);
        var exactDupes = 0;
        var nearDupes = 0;

        if (!noDedup && images.Count > 0)
        {
            AnsiConsole.MarkupLine("[dim]Checking for duplicates...[/]");
            var dedupResult = ImageHashService.Deduplicate(images, manifest,
                detectNearDuplicates: true, similarityThreshold: threshold, verbose: verbose);

            exactDupes = dedupResult.ExactDuplicates.Count;
            nearDupes = dedupResult.NearDuplicates.Count;
            images = dedupResult.UniqueImages;
            ImageHashService.SaveManifest(outputDir, manifest);
        }

        // Show mode and counts
        var modeDesc = force
            ? "[bold red]Force mode[/] - regenerating all sidecars"
            : "[bold]Incremental mode[/] - skipping existing sidecars";
        AnsiConsole.MarkupLine($"\n{modeDesc}");
        AnsiConsole.MarkupLine($"Total images: {allImages.Count}");

        if (languageList.Count == 1)
            AnsiConsole.MarkupLine($"Language: {primaryLanguage}");
        else
            AnsiConsole.MarkupLine($"Languages: {primaryLanguage} (primary), {string.Join(", ", languageList.Skip(1))}");

        if (skipped > 0)
            AnsiConsole.MarkupLine($"[dim]Skipping {skipped} image(s) with existing sidecars[/]");
        if (exactDupes > 0)
            AnsiConsole.MarkupLine($"[dim]Skipping {exactDupes} exact duplicate(s)[/]");
        if (nearDupes > 0)
            AnsiConsole.MarkupLine($"[dim]Skipping {nearDupes} near-duplicate(s)[/]");

        AnsiConsole.MarkupLine($"[bold]Processing {images.Count} image(s)[/]");
        if (images.Count > 0)
            AnsiConsole.MarkupLine($"[dim]Concurrency: {concurrency} parallel workers[/]");
        AnsiConsole.WriteLine();

        if (images.Count == 0)
        {
            AnsiConsole.MarkupLine("[green]✓ All images already have sidecars![/]");
            if (!createZip) return;
        }

        if (dryRun)
        {
            AnsiConsole.MarkupLine("[dim]Dry run - no files will be created[/]\n");
            foreach (var img in images)
            {
                var exists = SidecarService.HasSidecar(img, outputDir);
                var status = exists ? "[yellow]overwrite[/]" : "[green]new[/]";
                AnsiConsole.MarkupLine($"  • {Path.GetFileName(img)} ({status})");
            }
            return;
        }

        var processed = new List<(string Image, string Sidecar)>();
        var errors = new List<(string Image, string Error)>();

        if (images.Count > 0)
        {
            var rateLimiter = new RateLimiter();
            var limiter = new ConcurrencyLimiter(maxConcurrency: concurrency, rateLimiter: rateLimiter);
            var client = new CopilotClient(new CopilotClientOptions());

            try
            {
                await client.StartAsync();

                await AnsiConsole.Progress()
                    .AutoClear(false)
                    .Columns(
                        new SpinnerColumn(),
                        new TaskDescriptionColumn(),
                        new ProgressBarColumn(),
                        new PercentageColumn(),
                        new ElapsedTimeColumn(),
                        new RemainingTimeColumn())
                    .StartAsync(async ctx =>
                    {
                        var task = ctx.AddTask($"Annotating ({concurrency} workers)...", maxValue: images.Count);

                        var semaphore = new SemaphoreSlim(concurrency);
                        var tasks = images.Select(async imagePath =>
                        {
                            await semaphore.WaitAsync();
                            try
                            {
                                var sw = Stopwatch.StartNew();
                                for (var attempt = 0; attempt < 5; attempt++)
                                {
                                    try
                                    {
                                        await limiter.AcquireAsync();
                                        AnalysisResult result;
                                        try
                                        {
                                            result = await CopilotService.AnalyzeImageAsync(
                                                imagePath, model, verbose, languageList, client, rateLimiter);
                                        }
                                        finally
                                        {
                                            await limiter.ReleaseAsync();
                                        }

                                        var contentHash = ImageHashService.GetContentHash(imagePath);
                                        var metadata = SidecarService.CreateMetadata(result, primaryLanguage, contentHash);
                                        var sidecarPath = SidecarService.WriteSidecar(imagePath, metadata, outputDir);

                                        await limiter.RecordSuccessAsync();
                                        var emojis = string.Join(" ", result.Emojis);
                                        AnsiConsole.MarkupLine(
                                            $"  [green]✓[/] {Path.GetFileName(imagePath)} → {emojis} [dim]({sw.Elapsed.TotalSeconds:F1}s)[/]");
                                        task.Increment(1);

                                        lock (processed)
                                            processed.Add((imagePath, sidecarPath));
                                        return;
                                    }
                                    catch (CopilotNotAuthenticatedException ex)
                                    {
                                        AnsiConsole.MarkupLine($"\n[red]Error: {ex.Message}[/]");
                                        Environment.Exit(1);
                                    }
                                    catch (RateLimitException ex)
                                    {
                                        var waitTime = await limiter.RecordRateLimitAsync(ex.RetryAfter);
                                        if (attempt + 1 >= 5)
                                        {
                                            task.Increment(1);
                                            lock (errors)
                                                errors.Add((imagePath, ex.Message));
                                            return;
                                        }
                                        AnsiConsole.MarkupLine(
                                            $"\n[yellow]Rate limit hit — paused {waitTime:F1}s (attempt {attempt + 1}/5, concurrency → {limiter.CurrentConcurrency})[/]");
                                    }
                                    catch (ServerErrorException ex)
                                    {
                                        var waitTime = await limiter.RecordServerErrorAsync();
                                        if (attempt + 1 >= 5)
                                        {
                                            task.Increment(1);
                                            lock (errors)
                                                errors.Add((imagePath, ex.Message));
                                            return;
                                        }
                                        AnsiConsole.MarkupLine(
                                            $"\n[yellow]Server error. Waiting {waitTime:F1}s... (attempt {attempt + 1}/5)[/]");
                                        await Task.Delay(TimeSpan.FromSeconds(waitTime));
                                    }
                                    catch (CopilotAnalysisException ex)
                                    {
                                        AnsiConsole.MarkupLine(
                                            $"  [red]✗[/] {Path.GetFileName(imagePath)}: {ex.Message} [dim]({sw.Elapsed.TotalSeconds:F1}s)[/]");
                                        task.Increment(1);
                                        lock (errors)
                                            errors.Add((imagePath, ex.Message));
                                        return;
                                    }
                                }

                                task.Increment(1);
                                lock (errors)
                                    errors.Add((imagePath, "Exhausted all retries"));
                            }
                            finally
                            {
                                semaphore.Release();
                            }
                        });

                        await Task.WhenAll(tasks);
                    });
            }
            finally
            {
                await client.DisposeAsync();
            }

            // Summary
            AnsiConsole.WriteLine();
            if (processed.Count > 0)
                AnsiConsole.MarkupLine($"[green]✓ Successfully annotated {processed.Count} image(s)[/]");
            if (errors.Count > 0)
                AnsiConsole.MarkupLine($"[red]✗ Failed to annotate {errors.Count} image(s)[/]");
        }

        // Create ZIP bundle
        if (createZip)
        {
            var zipPath = Path.Combine(folder.Parent!.FullName, $"{folder.Name}.meme.zip");
            var bundled = 0;

            using (var zip = ZipFile.Open(zipPath, ZipArchiveMode.Create))
            {
                foreach (var imagePath in allImages)
                {
                    var sidecarPath = Path.Combine(outputDir, Path.GetFileName(imagePath) + ".json");
                    if (File.Exists(sidecarPath))
                    {
                        zip.CreateEntryFromFile(imagePath, Path.GetFileName(imagePath));
                        zip.CreateEntryFromFile(sidecarPath, Path.GetFileName(sidecarPath));
                        bundled++;
                    }
                }
            }

            if (bundled > 0)
            {
                AnsiConsole.MarkupLine($"\n[bold blue]📦 Created bundle: {zipPath}[/]");
                AnsiConsole.MarkupLine($"[dim]{bundled} image(s) bundled. Transfer to your Android device and open with Riposte[/]");
            }
            else
            {
                AnsiConsole.MarkupLine("\n[yellow]No images with sidecars to bundle.[/]");
            }
        }
    }
}
