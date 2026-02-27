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
        var concurrencyOpt = new Option<int>("--concurrency", "-j") { Description = "Max parallel API requests (1-50)", DefaultValueFactory = _ => 4 };

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
            var concurrency = Math.Clamp(parseResult.GetValue(concurrencyOpt), 1, 50);

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

        // Deduplication (runs on all images before planning)
        var hashManifest = ImageHashService.LoadManifest(outputDir);
        var exactDupes = 0;
        var nearDupes = 0;
        var imagesToProcess = allImages.ToList();

        if (!noDedup)
        {
            AnsiConsole.MarkupLine("[dim]Checking for duplicates...[/]");
            var dedupResult = ImageHashService.Deduplicate(imagesToProcess, hashManifest,
                detectNearDuplicates: true, similarityThreshold: threshold, verbose: verbose);

            exactDupes = dedupResult.ExactDuplicates.Count;
            nearDupes = dedupResult.NearDuplicates.Count;
            imagesToProcess = dedupResult.UniqueImages;
            ImageHashService.SaveManifest(outputDir, hashManifest);
        }

        // --- Smart Rebuild Planning ---
        var currentPromptHashes = PromptHasher.ComputeAll(languageList);
        var buildManifest = ManifestService.Load(outputDir);
        var currentSchemaVersion = "1.4";

        List<ImageRebuildPlan> plans;
        if (force)
        {
            // Force mode: full rebuild for all images
            plans = imagesToProcess.Select(img => new ImageRebuildPlan
            {
                ImagePath = img,
                Scope = RebuildScope.Full,
                Reason = "force mode",
            }).ToList();
        }
        else if (continueMissing || addNew)
        {
            // Continue mode: only images without sidecars
            plans = imagesToProcess.Select(img => !SidecarService.HasSidecar(img, outputDir)
                ? new ImageRebuildPlan { ImagePath = img, Scope = RebuildScope.Full, Reason = "no existing sidecar" }
                : new ImageRebuildPlan { ImagePath = img, Scope = RebuildScope.Skip, Reason = "has sidecar" }
            ).ToList();
        }
        else
        {
            // Smart mode (default): field-level diffing
            plans = RebuildPlanner.Plan(imagesToProcess, buildManifest, currentPromptHashes, model, currentSchemaVersion, outputDir);
        }

        var (skipCount, fullCount, partialCount) = RebuildPlanner.Summarize(plans);
        var workPlans = plans.Where(p => p.Scope != RebuildScope.Skip).ToList();

        // Downscale images for API calls
        Dictionary<string, string>? apiOptimizedMap = null;
        if (workPlans.Count > 0 && !dryRun)
        {
            var imagesToOptimize = workPlans.Select(p => p.ImagePath).ToList();
            AnsiConsole.MarkupLine("[dim]Downscaling images for API (max 1200px, Lanczos3)...[/]");
            var optimizeCount = 0;
            apiOptimizedMap = ImageOptimizer.OptimizeBatchForApi(imagesToOptimize, outputDir, concurrency: concurrency,
                onComplete: (orig, opt) =>
                {
                    var count = Interlocked.Increment(ref optimizeCount);
                    if (verbose)
                    {
                        var label = orig == opt ? "already ≤1200px" : Path.GetFileName(opt);
                        AnsiConsole.MarkupLine($"  [dim]Prepared {count}/{imagesToOptimize.Count}: {Path.GetFileName(orig)} → {label}[/]");
                    }
                });
            var resized = apiOptimizedMap.Count(kv => kv.Key != kv.Value);
            AnsiConsole.MarkupLine($"[green]✓ Prepared {apiOptimizedMap.Count} image(s) ({resized} downscaled)[/]");
        }

        // Optimize all images to WebP for ZIP bundling
        Dictionary<string, string>? bundleOptimizedMap = null;
        if (createZip && !dryRun)
        {
            AnsiConsole.MarkupLine($"[dim]Converting {allImages.Count} image(s) to WebP for ZIP bundle...[/]");
            bundleOptimizedMap = ImageOptimizer.OptimizeBatchForBundle(allImages, outputDir, concurrency: concurrency);
            AnsiConsole.MarkupLine($"[green]✓ Converted {bundleOptimizedMap.Count} image(s) to WebP[/]");
        }

        // Show mode and counts
        string modeDesc;
        if (force)
            modeDesc = "[bold red]Force mode[/] — regenerating all sidecars";
        else if (continueMissing || addNew)
            modeDesc = "[bold]Continue mode[/] — only new images";
        else
            modeDesc = "[bold cyan]Smart mode[/] — field-level rebuild";

        AnsiConsole.MarkupLine($"\n{modeDesc}");
        AnsiConsole.MarkupLine($"Total images: {allImages.Count}");

        if (languageList.Count == 1)
            AnsiConsole.MarkupLine($"Language: {primaryLanguage}");
        else
            AnsiConsole.MarkupLine($"Languages: {primaryLanguage} (primary), {string.Join(", ", languageList.Skip(1))}");

        if (exactDupes > 0)
            AnsiConsole.MarkupLine($"[dim]Skipping {exactDupes} exact duplicate(s)[/]");
        if (nearDupes > 0)
            AnsiConsole.MarkupLine($"[dim]Skipping {nearDupes} near-duplicate(s)[/]");
        if (skipCount > 0)
            AnsiConsole.MarkupLine($"[dim]Skipping {skipCount} up-to-date image(s)[/]");
        if (fullCount > 0)
            AnsiConsole.MarkupLine($"[bold]Full rebuild: {fullCount} image(s)[/]");
        if (partialCount > 0)
            AnsiConsole.MarkupLine($"[bold]Partial rebuild: {partialCount} image(s)[/]");

        if (workPlans.Count > 0)
            AnsiConsole.MarkupLine($"[dim]Concurrency: {concurrency} parallel workers[/]");
        AnsiConsole.WriteLine();

        if (workPlans.Count == 0)
        {
            AnsiConsole.MarkupLine("[green]✓ All images up to date![/]");
            if (!createZip) return;
        }

        if (dryRun)
        {
            AnsiConsole.MarkupLine("[dim]Dry run — no files will be created[/]\n");
            foreach (var plan in plans)
            {
                var icon = plan.Scope switch
                {
                    RebuildScope.Skip => "[dim]skip[/]",
                    RebuildScope.Full => "[green]full[/]",
                    RebuildScope.Partial => $"[yellow]partial ({string.Join(", ", plan.AffectedGroups)})[/]",
                    _ => "[dim]?[/]",
                };
                AnsiConsole.MarkupLine($"  • {Path.GetFileName(plan.ImagePath)} — {icon} [dim]({plan.Reason})[/]");
            }
            return;
        }

        // --- Execute rebuilds ---
        var processed = new List<(string Image, string Sidecar)>();
        var errors = new List<(string Image, string Error)>();

        if (workPlans.Count > 0)
        {
            var rateLimiter = new RateLimiter();
            var limiter = new ConcurrencyLimiter(maxConcurrency: concurrency, rateLimiter: rateLimiter);
            var client = new CopilotClient(new CopilotClientOptions());
            var manifestLock = new object();

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
                        var task = ctx.AddTask($"Annotating ({concurrency} workers)...", maxValue: workPlans.Count);

                        var semaphore = new SemaphoreSlim(concurrency);
                        var tasks = workPlans.Select(async plan =>
                        {
                            await semaphore.WaitAsync();
                            try
                            {
                                var imagePath = plan.ImagePath;
                                var analysisPath = apiOptimizedMap is not null && apiOptimizedMap.TryGetValue(imagePath, out var optPath)
                                    ? optPath : imagePath;
                                var sw = Stopwatch.StartNew();

                                for (var attempt = 0; attempt < 5; attempt++)
                                {
                                    try
                                    {
                                        await limiter.AcquireAsync();
                                        AnalysisResult result;
                                        try
                                        {
                                            if (plan.Scope == RebuildScope.Full)
                                            {
                                                result = await CopilotService.AnalyzeImageAsync(
                                                    analysisPath, model, verbose, languageList, client, rateLimiter);
                                            }
                                            else
                                            {
                                                result = await CopilotService.AnalyzePartialAsync(
                                                    analysisPath, plan.AffectedGroups, model, verbose, languageList, client, rateLimiter);
                                            }
                                        }
                                        finally
                                        {
                                            await limiter.ReleaseAsync();
                                        }

                                        var contentHash = ImageHashService.GetContentHash(imagePath);
                                        SidecarMetadata metadata;
                                        string sidecarPath;

                                        if (plan.Scope == RebuildScope.Full)
                                        {
                                            metadata = SidecarService.CreateMetadata(result, primaryLanguage, contentHash);
                                            sidecarPath = SidecarService.WriteSidecar(imagePath, metadata, outputDir);

                                            lock (manifestLock)
                                                ManifestService.RecordImageBuild(buildManifest, Path.GetFileName(imagePath),
                                                    contentHash, model, currentSchemaVersion, currentPromptHashes);
                                        }
                                        else
                                        {
                                            // Partial: merge into existing sidecar
                                            var existing = SidecarMerger.LoadSidecar(imagePath, outputDir);
                                            if (existing is null)
                                            {
                                                // Sidecar disappeared — fall back to treating as full result
                                                metadata = SidecarService.CreateMetadata(result, primaryLanguage, contentHash);
                                            }
                                            else
                                            {
                                                metadata = SidecarMerger.Merge(existing, result, plan.AffectedGroups);
                                            }
                                            sidecarPath = SidecarService.WriteSidecar(imagePath, metadata, outputDir);

                                            lock (manifestLock)
                                                ManifestService.RecordPartialBuild(buildManifest, Path.GetFileName(imagePath),
                                                    contentHash, model, currentSchemaVersion, plan.AffectedGroups, currentPromptHashes);
                                        }

                                        await limiter.RecordSuccessAsync();

                                        var scopeLabel = plan.Scope == RebuildScope.Full ? "" : $" [dim]partial:{string.Join(",", plan.AffectedGroups)}[/]";
                                        var emojis = string.Join(" ", metadata.Emojis);
                                        AnsiConsole.MarkupLine(
                                            $"  [green]✓[/] {Path.GetFileName(imagePath)} → {emojis}{scopeLabel} [dim]({sw.Elapsed.TotalSeconds:F1}s)[/]");
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

            // Update manifest global state and save
            buildManifest = buildManifest with
            {
                Model = model,
                SchemaVersion = currentSchemaVersion,
                PromptHashes = currentPromptHashes,
            };
            ManifestService.Save(outputDir, buildManifest);

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
            if (File.Exists(zipPath))
                File.Delete(zipPath);
            var bundled = 0;

            using (var zip = ZipFile.Open(zipPath, ZipArchiveMode.Create))
            {
                foreach (var imagePath in allImages)
                {
                    var sidecarPath = Path.Combine(outputDir, Path.GetFileName(imagePath) + ".json");
                    if (File.Exists(sidecarPath))
                    {
                        // Use optimized WebP image if available, otherwise original
                        var bundlePath = bundleOptimizedMap is not null && bundleOptimizedMap.TryGetValue(imagePath, out var optPath)
                            ? optPath : imagePath;
                        var bundleImageName = Path.GetFileName(bundlePath);
                        var bundleSidecarName = bundleImageName + ".json";

                        zip.CreateEntryFromFile(bundlePath, bundleImageName);

                        // If image was optimized (name changed), copy sidecar with matching name
                        if (bundleSidecarName != Path.GetFileName(sidecarPath))
                        {
                            var entry = zip.CreateEntry(bundleSidecarName);
                            using var entryStream = entry.Open();
                            using var sidecarStream = File.OpenRead(sidecarPath);
                            sidecarStream.CopyTo(entryStream);
                        }
                        else
                        {
                            zip.CreateEntryFromFile(sidecarPath, bundleSidecarName);
                        }
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
