using System.Collections.Concurrent;
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
    /// <summary>Returns a dim HH:mm:ss timestamp prefix for log output.</summary>
    private static string Ts() => $"[dim]{DateTime.Now:HH:mm:ss}[/] ";

    public static Command Create()
    {
        var folderArg = new Argument<DirectoryInfo>("folder") { Description = "Path to a directory containing images to annotate" };
        var zipOpt = new Option<string?>("--zip") { Description = "Create ZIP bundle: 'full' (all images) or 'patch' (only changed/new). Omit value for full.", Arity = ArgumentArity.ZeroOrOne };
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
            var zipRawValue = parseResult.GetValue(zipOpt);
            var zipOptionPresent = parseResult.GetResult(zipOpt) is not null;
            var zipValue = zipOptionPresent ? (zipRawValue ?? "") : null;
            ZipMode? zipMode;
            try
            {
                zipMode = ParseZipMode(zipValue);
            }
            catch (ArgumentException ex)
            {
                AnsiConsole.MarkupLineInterpolated($"[red]Error: {ex.Message}[/]");
                return;
            }
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

            await ExecuteAsync(folder, zipMode, output, model, languages, force,
                continueMissing, addNew, noDedup, threshold, dryRun, verbose, concurrency, cancellationToken);
        });

        return command;
    }

    internal static ZipMode? ParseZipMode(string? value)
    {
        if (value is null) return null;
        return value.ToLowerInvariant() switch
        {
            "" or "full" or "true" => ZipMode.Full,
            "patch" or "delta" => ZipMode.Patch,
            _ => throw new ArgumentException($"Unknown --zip mode: '{value}'. Use 'full' or 'patch'."),
        };
    }

    private static async Task ExecuteAsync(
        DirectoryInfo folder, ZipMode? zipMode, DirectoryInfo? output, string model,
        string languages, bool force, bool continueMissing, bool addNew,
        bool noDedup, int threshold, bool dryRun, bool verbose, int concurrency,
        CancellationToken cancellationToken = default)
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

        // Migrate legacy flat layout to subdirectories
        var migrated = OutputPaths.MigrateLegacyLayout(outputDir);
        if (migrated > 0)
            AnsiConsole.MarkupLine($"[dim]Migrated {migrated} file(s) to subdirectories[/]");

        // Find images
        var allImages = SidecarService.GetImagesInFolder(folder.FullName);
        if (allImages.Count == 0)
        {
            AnsiConsole.MarkupLineInterpolated($"[yellow]No supported images found in {folder.FullName}[/]");
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
        var optimizationConfig = new OptimizationConfig();
        var isNewManifest = buildManifest.Images.Count == 0;

        if (isNewManifest && imagesToProcess.Any(img => SidecarService.HasSidecar(img, outputDir)))
            AnsiConsole.MarkupLine("[dim]No build manifest found — existing sidecars will be fully rebuilt to establish tracking[/]");
        else if (isNewManifest)
            AnsiConsole.MarkupLine("[dim]No build manifest — fresh build[/]");
        else
            AnsiConsole.MarkupLineInterpolated($"[dim]Build manifest: {buildManifest.Images.Count} tracked image(s), model={buildManifest.Model}[/]");

        List<ImageRebuildPlan> plans;
        if (force)
        {
            // Force mode: full rebuild for all images
            plans = imagesToProcess.Select(img => new ImageRebuildPlan
            {
                ImagePath = img,
                Scope = RebuildScope.Full,
                NeedsReoptimization = true,
                Reason = "force mode",
            }).ToList();
        }
        else if (continueMissing || addNew)
        {
            // Continue mode: only images without sidecars
            plans = imagesToProcess.Select(img => !SidecarService.HasSidecar(img, outputDir)
                ? new ImageRebuildPlan { ImagePath = img, Scope = RebuildScope.Full, NeedsReoptimization = true, Reason = "no existing sidecar" }
                : new ImageRebuildPlan { ImagePath = img, Scope = RebuildScope.Skip, Reason = "has sidecar" }
            ).ToList();
        }
        else
        {
            // Smart mode (default): field-level diffing + optimization tracking
            plans = RebuildPlanner.Plan(imagesToProcess, buildManifest, currentPromptHashes, model, currentSchemaVersion, outputDir, optimizationConfig);
        }

        var (skipCount, fullCount, partialCount, reoptCount) = RebuildPlanner.Summarize(plans);
        var workPlans = plans.Where(p => p.Scope != RebuildScope.Skip).ToList();
        var needsOptimization = plans.Where(p => p.NeedsReoptimization).Select(p => p.ImagePath).ToList();

        // Downscale images for API calls (work items + reoptimization-only items)
        Dictionary<string, string>? apiOptimizedMap = null;
        var imagesToOptimize = needsOptimization.Count > 0 ? needsOptimization : workPlans.Select(p => p.ImagePath).ToList();
        if (imagesToOptimize.Count > 0 && !dryRun)
        {
            AnsiConsole.MarkupLine($"{Ts()}[dim]Downscaling images for API (max 1200px, Lanczos3)...[/]");
            var optimizeCount = 0;
            apiOptimizedMap = ImageOptimizer.OptimizeBatchForApi(imagesToOptimize, outputDir, concurrency: concurrency,
                onComplete: (orig, opt) =>
                {
                    var count = Interlocked.Increment(ref optimizeCount);
                    if (verbose)
                    {
                        var label = orig == opt ? "already ≤1200px" : Path.GetFileName(opt);
                        AnsiConsole.MarkupLineInterpolated($"  [dim]Prepared {count}/{imagesToOptimize.Count}: {Path.GetFileName(orig)} → {label}[/]");
                    }
                });
            var resized = apiOptimizedMap.Count(kv => kv.Key != kv.Value);
            AnsiConsole.MarkupLine($"{Ts()}[green]✓ Prepared {apiOptimizedMap.Count} image(s) ({resized} downscaled)[/]");

            // Update manifest for skip+reopt images (no API call, just optimization tracking)
            var skipReoptPlans = plans.Where(p => p.Scope == RebuildScope.Skip && p.NeedsReoptimization).ToList();
            foreach (var plan in skipReoptPlans)
            {
                var fileName = Path.GetFileName(plan.ImagePath);
                if (buildManifest.Images.TryGetValue(fileName, out var entry))
                {
                    entry.OptimizationFingerprint = optimizationConfig.Fingerprint();
                    entry.HasApiOptimized = true;
                }
            }

            if (skipReoptPlans.Count > 0)
                ManifestService.Save(outputDir, buildManifest);
        }

        // Optimize images to WebP for ZIP bundling (full: all images, patch: deferred to bundler)
        Dictionary<string, string>? bundleOptimizedMap = null;
        if (zipMode == ZipMode.Full && !dryRun)
        {
            AnsiConsole.MarkupLine($"{Ts()}[dim]Converting {allImages.Count} image(s) to WebP for ZIP bundle...[/]");
            bundleOptimizedMap = ImageOptimizer.OptimizeBatchForBundle(allImages, outputDir, concurrency: concurrency);
            AnsiConsole.MarkupLine($"{Ts()}[green]✓ Converted {bundleOptimizedMap.Count} image(s) to WebP[/]");
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
        if (reoptCount > 0)
            AnsiConsole.MarkupLine($"[bold]Re-optimize only: {reoptCount} image(s)[/]");

        var stripCount = plans.Count(p => p.NeedsStripping);
        if (stripCount > 0)
            AnsiConsole.MarkupLine($"[bold]Strip removed fields: {stripCount} image(s)[/]");

        // Show reason breakdown for non-skip plans
        if (!force && (fullCount > 0 || partialCount > 0))
        {
            var reasonGroups = plans
                .Where(p => p.Scope != RebuildScope.Skip)
                .GroupBy(p => p.Reason)
                .OrderByDescending(g => g.Count())
                .ToList();

            AnsiConsole.MarkupLine("[dim]Rebuild reasons:[/]");
            foreach (var group in reasonGroups)
            {
                var scope = group.First().Scope == RebuildScope.Full ? "full" : "partial";
                AnsiConsole.MarkupLineInterpolated($"  [dim]{group.Count()}× {scope}: {group.Key}[/]");
            }

            if (verbose)
            {
                AnsiConsole.MarkupLine("[dim]First 10 rebuild targets:[/]");
                foreach (var plan in plans.Where(p => p.Scope != RebuildScope.Skip).Take(10))
                {
                    var scope = plan.Scope == RebuildScope.Full ? "[green]full[/]" : "[yellow]partial[/]";
                    AnsiConsole.MarkupLine($"  [dim]• {Markup.Escape(Path.GetFileName(plan.ImagePath))} — {scope} ({Markup.Escape(plan.Reason)})[/]");
                }
            }
        }

        if (workPlans.Count > 0)
            AnsiConsole.MarkupLine($"[dim]Concurrency: {concurrency} parallel workers[/]");
        AnsiConsole.WriteLine();

        if (workPlans.Count == 0)
        {
            AnsiConsole.MarkupLine("[green]✓ All images up to date![/]");
            if (zipMode is null) return;
        }

        if (dryRun)
        {
            AnsiConsole.MarkupLine("[dim]Dry run — no files will be created[/]\n");
            foreach (var plan in plans)
            {
                var parts = new List<string>();

                parts.Add(plan.Scope switch
                {
                    RebuildScope.Skip when !plan.NeedsReoptimization && !plan.NeedsStripping => "[dim]skip[/]",
                    RebuildScope.Skip => "[dim]skip[/]",
                    RebuildScope.Full => "[green]full[/]",
                    RebuildScope.Partial => $"[yellow]partial ({string.Join(", ", plan.AffectedGroups)})[/]",
                    _ => "[dim]?[/]",
                });

                if (plan.NeedsReoptimization) parts.Add("[cyan]reoptimize[/]");
                if (plan.NeedsStripping) parts.Add($"[red]strip ({string.Join(", ", plan.RemovedGroups)})[/]");

                AnsiConsole.MarkupLine($"  • {Markup.Escape(Path.GetFileName(plan.ImagePath))} — {string.Join(" + ", parts)} [dim]({Markup.Escape(plan.Reason)})[/]");
            }
            if (zipMode is not null)
            {
                // Simulate all work plans succeeding for bundle preview
                var simulatedProcessed = workPlans
                    .Select(p => (p.ImagePath, Path.Combine(outputDir, Path.GetFileName(p.ImagePath) + ".json")))
                    .ToList();
                var wouldBundle = ZipBundler.SelectImagesForBundle(
                    zipMode.Value, allImages, outputDir, plans, simulatedProcessed, buildManifest);
                var modeLabel = zipMode == ZipMode.Patch ? "patch " : "";
                AnsiConsole.MarkupLine($"\n[dim]Would create {modeLabel}bundle with {wouldBundle.Count} image(s)[/]");
                if (verbose)
                {
                    foreach (var img in wouldBundle)
                        AnsiConsole.MarkupLineInterpolated($"  [dim]📦 {Path.GetFileName(img)}[/]");
                }
            }
            return;
        }

        // --- Execute rebuilds ---
        var (processed, errors) = await RunAnnotationLoopAsync(
            workPlans, apiOptimizedMap, buildManifest, outputDir, model, primaryLanguage,
            currentSchemaVersion, currentPromptHashes, optimizationConfig, languageList,
            verbose, concurrency, cancellationToken);

        // Always update manifest global state (even if no work plans ran)
        if (!dryRun)
        {
            buildManifest.Model = model;
            buildManifest.SchemaVersion = currentSchemaVersion;
            buildManifest.PromptHashes = currentPromptHashes;
            buildManifest.Optimization = optimizationConfig;
            ManifestService.Save(outputDir, buildManifest);
        }

        // Strip removed field groups from sidecars
        if (!dryRun)
            StripRemovedFields(plans, buildManifest, outputDir, currentPromptHashes, currentSchemaVersion);

        // Create ZIP bundle
        if (zipMode is not null)
            CreateZipBundle(zipMode.Value, folder, outputDir, allImages, plans, processed.ToList(),
                bundleOptimizedMap, buildManifest, verbose);
    }

    /// <summary>
    /// Execute the annotation loop: process each work plan through the Copilot API,
    /// save sidecars, and update the manifest incrementally.
    /// </summary>
    private static async Task<(ConcurrentBag<(string Image, string Sidecar)> Processed, ConcurrentBag<(string Image, string Error)> Errors)>
        RunAnnotationLoopAsync(
            List<ImageRebuildPlan> workPlans,
            Dictionary<string, string>? apiOptimizedMap,
            BuildManifest buildManifest,
            string outputDir,
            string model,
            string primaryLanguage,
            string currentSchemaVersion,
            Dictionary<string, string> currentPromptHashes,
            OptimizationConfig optimizationConfig,
            List<string> languageList,
            bool verbose,
            int concurrency,
            CancellationToken cancellationToken)
    {
        var processed = new ConcurrentBag<(string Image, string Sidecar)>();
        var errors = new ConcurrentBag<(string Image, string Error)>();

        if (workPlans.Count == 0)
            return (processed, errors);

        var rateLimiter = new RateLimiter();
        var limiter = new ConcurrencyLimiter(maxConcurrency: concurrency, rateLimiter: rateLimiter);
        var client = new CopilotClient(new CopilotClientOptions());
        var manifestMutex = new SemaphoreSlim(1, 1);

        // Set global manifest properties upfront so incremental saves include them
        buildManifest.Model = model;
        buildManifest.SchemaVersion = currentSchemaVersion;
        buildManifest.PromptHashes = currentPromptHashes;
        buildManifest.Optimization = optimizationConfig;

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
                        await semaphore.WaitAsync(cancellationToken);
                        try
                        {
                            await ProcessSingleImageAsync(
                                plan, apiOptimizedMap, buildManifest, outputDir, model, primaryLanguage,
                                currentSchemaVersion, currentPromptHashes, optimizationConfig, languageList,
                                verbose, client, rateLimiter, limiter, manifestMutex,
                                processed, errors, task, cancellationToken);
                        }
                        finally
                        {
                            semaphore.Release();
                        }
                    });

                    await Task.WhenAll(tasks.ToList());
                });
        }
        catch (Exception ex)
        {
            AnsiConsole.MarkupLineInterpolated($"\n{Ts()}[red]Fatal error during annotation: {ex.GetType().Name}: {Markup.Escape(ex.Message)}[/]");
            if (ex is AggregateException agg)
            {
                foreach (var inner in agg.Flatten().InnerExceptions)
                    AnsiConsole.MarkupLineInterpolated($"  [red]• {inner.GetType().Name}: {Markup.Escape(inner.Message)}[/]");
            }
            AnsiConsole.MarkupLine($"[dim]{Markup.Escape(ex.StackTrace ?? "")}[/]");
            throw;
        }
        finally
        {
            // Persist manifest progress even if processing was interrupted
            try { ManifestService.Save(outputDir, buildManifest); }
            catch { /* best-effort — don't mask the original exception */ }

            try
            {
                await client.DisposeAsync();
            }
            catch (Exception disposeEx)
            {
                AnsiConsole.MarkupLineInterpolated(
                    $"\n{Ts()}[yellow]Warning: error during cleanup: {Markup.Escape(disposeEx.Message)}[/]");
            }
        }

        // Summary
        AnsiConsole.WriteLine();
        if (processed.Count > 0)
            AnsiConsole.MarkupLine($"{Ts()}[green]✓ Successfully annotated {processed.Count} image(s)[/]");
        if (errors.Count > 0)
        {
            AnsiConsole.MarkupLine($"{Ts()}[red]✗ Failed to annotate {errors.Count} image(s):[/]");
            foreach (var (img, err) in errors.Take(20))
                AnsiConsole.MarkupLineInterpolated($"  [red]•[/] {Markup.Escape(Path.GetFileName(img))}: {Markup.Escape(err)}");
            if (errors.Count > 20)
                AnsiConsole.MarkupLine($"  [dim]... and {errors.Count - 20} more[/]");
        }

        return (processed, errors);
    }

    /// <summary>
    /// Process a single image: analyze via Copilot, write sidecar, update manifest.
    /// Retries up to 5 times on transient errors.
    /// </summary>
    private static async Task ProcessSingleImageAsync(
        ImageRebuildPlan plan,
        Dictionary<string, string>? apiOptimizedMap,
        BuildManifest buildManifest,
        string outputDir,
        string model,
        string primaryLanguage,
        string currentSchemaVersion,
        Dictionary<string, string> currentPromptHashes,
        OptimizationConfig optimizationConfig,
        List<string> languageList,
        bool verbose,
        CopilotClient client,
        RateLimiter rateLimiter,
        ConcurrencyLimiter limiter,
        SemaphoreSlim manifestMutex,
        ConcurrentBag<(string Image, string Sidecar)> processed,
        ConcurrentBag<(string Image, string Error)> errors,
        ProgressTask task,
        CancellationToken cancellationToken)
    {
        var imagePath = plan.ImagePath;
        var analysisPath = apiOptimizedMap is not null && apiOptimizedMap.TryGetValue(imagePath, out var optPath)
            ? optPath : imagePath;
        var sw = Stopwatch.StartNew();

        for (var attempt = 0; attempt < 5; attempt++)
        {
            cancellationToken.ThrowIfCancellationRequested();
            try
            {
                await limiter.AcquireAsync(cancellationToken);
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

                    await manifestMutex.WaitAsync(cancellationToken);
                    try
                    {
                        ManifestService.RecordImageBuild(buildManifest, Path.GetFileName(imagePath),
                            contentHash, model, currentSchemaVersion, currentPromptHashes, optimizationConfig.Fingerprint());
                        ManifestService.Save(outputDir, buildManifest);
                    }
                    finally { manifestMutex.Release(); }
                }
                else
                {
                    // Partial: merge into existing sidecar
                    var existing = SidecarMerger.LoadSidecar(imagePath, outputDir);
                    if (existing is null)
                    {
                        AnsiConsole.MarkupLineInterpolated(
                            $"{Ts()}  [red]✗[/] {Path.GetFileName(imagePath)}: sidecar disappeared during partial rebuild, re-run to do full rebuild [dim]({sw.Elapsed.TotalSeconds:F1}s)[/]");
                        task.Increment(1);
                        errors.Add((imagePath, "Sidecar disappeared during partial rebuild"));
                        return;
                    }

                    metadata = SidecarMerger.Merge(existing, result, plan.AffectedGroups, currentSchemaVersion);
                    sidecarPath = SidecarService.WriteSidecar(imagePath, metadata, outputDir);

                    await manifestMutex.WaitAsync(cancellationToken);
                    try
                    {
                        ManifestService.RecordPartialBuild(buildManifest, Path.GetFileName(imagePath),
                            contentHash, model, currentSchemaVersion, plan.AffectedGroups, currentPromptHashes, optimizationConfig.Fingerprint());
                        ManifestService.Save(outputDir, buildManifest);
                    }
                    finally { manifestMutex.Release(); }
                }

                await limiter.RecordSuccessAsync();

                var scopeLabel = plan.Scope == RebuildScope.Full ? "" : $" [dim]partial:{string.Join(",", plan.AffectedGroups)}[/]";
                var emojis = string.Join(" ", metadata.Emojis);
                AnsiConsole.MarkupLine(
                    $"{Ts()}  [green]✓[/] {Markup.Escape(Path.GetFileName(imagePath))} → {Markup.Escape(emojis)}{scopeLabel} [dim]({sw.Elapsed.TotalSeconds:F1}s)[/]");
                task.Increment(1);

                processed.Add((imagePath, sidecarPath));
                return;
            }
            catch (OperationCanceledException) { throw; }
            catch (CopilotNotAuthenticatedException ex)
            {
                AnsiConsole.MarkupLineInterpolated($"\n{Ts()}[red]Error: {ex.Message}[/]");
                task.Increment(1);
                errors.Add((imagePath, $"Auth error: {ex.Message}"));
                return;
            }
            catch (RateLimitException ex)
            {
                var waitTime = await limiter.RecordRateLimitAsync(ex.RetryAfter);
                if (attempt + 1 >= 5)
                {
                    task.Increment(1);
                    errors.Add((imagePath, ex.Message));
                    return;
                }
                AnsiConsole.MarkupLine(
                    $"\n{Ts()}[yellow]Rate limit hit — paused {waitTime:F1}s (attempt {attempt + 1}/5, concurrency → {limiter.CurrentConcurrency})[/]");
            }
            catch (ServerErrorException ex)
            {
                var waitTime = await limiter.RecordServerErrorAsync();
                if (attempt + 1 >= 5)
                {
                    task.Increment(1);
                    errors.Add((imagePath, ex.Message));
                    return;
                }
                AnsiConsole.MarkupLine(
                    $"\n{Ts()}[yellow]Server error. Waiting {waitTime:F1}s... (attempt {attempt + 1}/5)[/]");
                await Task.Delay(TimeSpan.FromSeconds(waitTime), cancellationToken);
            }
            catch (ContentRefusedException)
            {
                if (attempt + 1 >= 5)
                {
                    AnsiConsole.MarkupLineInterpolated(
                        $"{Ts()}  [yellow]⚠[/] {Path.GetFileName(imagePath)}: [yellow]Skipped — model refused after {attempt + 1} attempts (content policy)[/] [dim]({sw.Elapsed.TotalSeconds:F1}s)[/]");
                    task.Increment(1);
                    errors.Add((imagePath, "Skipped — model refused to analyze (content policy)"));
                    return;
                }
                AnsiConsole.MarkupLine(
                    $"\n{Ts()}[yellow]Model refused {Path.GetFileName(imagePath)} — retrying (attempt {attempt + 1}/5)[/]");
                await Task.Delay(TimeSpan.FromSeconds(2 * (attempt + 1)), cancellationToken);
            }
            catch (CopilotAnalysisException ex)
            {
                AnsiConsole.MarkupLineInterpolated(
                    $"{Ts()}  [red]✗[/] {Path.GetFileName(imagePath)}: {ex.Message} [dim]({sw.Elapsed.TotalSeconds:F1}s)[/]");
                task.Increment(1);
                errors.Add((imagePath, ex.Message));
                return;
            }
            catch (Exception ex)
            {
                AnsiConsole.MarkupLineInterpolated(
                    $"{Ts()}  [red]✗[/] {Markup.Escape(Path.GetFileName(imagePath))}: Unexpected {ex.GetType().Name}: {Markup.Escape(ex.Message)} [dim]({sw.Elapsed.TotalSeconds:F1}s)[/]");
                task.Increment(1);
                errors.Add((imagePath, $"{ex.GetType().Name}: {ex.Message}"));
                return;
            }
        }

        task.Increment(1);
        errors.Add((imagePath, "Exhausted all retries"));
    }

    /// <summary>
    /// Strip field groups that are no longer in the current prompt configuration
    /// from existing sidecars, and update the manifest accordingly.
    /// </summary>
    private static void StripRemovedFields(
        List<ImageRebuildPlan> plans,
        BuildManifest buildManifest,
        string outputDir,
        Dictionary<string, string> currentPromptHashes,
        string currentSchemaVersion)
    {
        var toStrip = plans.Where(p => p.NeedsStripping).ToList();
        if (toStrip.Count == 0) return;

        var stripped = 0;
        foreach (var plan in toStrip)
        {
            var existing = SidecarMerger.LoadSidecar(plan.ImagePath, outputDir);
            if (existing is null) continue;

            var cleaned = SidecarMerger.StripRemovedGroups(existing, currentPromptHashes, currentSchemaVersion);
            if (!ReferenceEquals(cleaned, existing))
            {
                SidecarService.WriteSidecar(plan.ImagePath, cleaned, outputDir);
                stripped++;

                var fileName = Path.GetFileName(plan.ImagePath);
                if (buildManifest.Images.TryGetValue(fileName, out var entry))
                {
                    foreach (var removed in plan.RemovedGroups)
                        entry.FieldHashes.Remove(removed);
                }
            }
        }

        if (stripped > 0)
        {
            ManifestService.Save(outputDir, buildManifest);
            AnsiConsole.MarkupLine($"[dim]Stripped removed fields from {stripped} sidecar(s)[/]");
        }
    }

    /// <summary>
    /// Create a ZIP bundle (.meme.zip) from processed images and their sidecars.
    /// </summary>
    private static void CreateZipBundle(
        ZipMode zipMode,
        DirectoryInfo folder,
        string outputDir,
        List<string> allImages,
        List<ImageRebuildPlan> plans,
        IReadOnlyList<(string Image, string Sidecar)> processedList,
        Dictionary<string, string>? bundleOptimizedMap,
        BuildManifest buildManifest,
        bool verbose)
    {
        var result = ZipBundler.CreateBundle(
            zipMode, folder, outputDir, allImages, plans, processedList,
            bundleOptimizedMap, buildManifest, verbose);

        if (result.ImageCount > 0)
        {
            ZipBundler.RecordBundledImages(buildManifest, result.BundledImagePaths);
            if (zipMode == ZipMode.Full)
                buildManifest.LastFullBundleAt = DateTimeOffset.UtcNow.ToString("o");
            if (zipMode == ZipMode.Patch)
                buildManifest.LastPatchBundleAt = DateTimeOffset.UtcNow.ToString("o");
            ManifestService.Save(outputDir, buildManifest);

            var modeLabel = zipMode == ZipMode.Patch ? "patch " : "";
            AnsiConsole.MarkupLineInterpolated($"\n{Ts()}[bold blue]📦 Created {modeLabel}bundle: {result.ZipPath}[/]");
            AnsiConsole.MarkupLine($"[dim]{result.ImageCount} image(s) bundled. Transfer to your Android device and open with Riposte[/]");
        }
        else
        {
            AnsiConsole.MarkupLine("\n[yellow]No images with sidecars to bundle.[/]");
        }
    }
}
