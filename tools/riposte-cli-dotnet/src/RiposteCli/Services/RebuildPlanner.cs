using RiposteCli.Models;

namespace RiposteCli.Services;

/// <summary>
/// Scope of rebuild needed for a single image.
/// </summary>
public enum RebuildScope
{
    /// <summary>No changes needed — skip entirely.</summary>
    Skip,
    /// <summary>Full rebuild — all fields regenerated.</summary>
    Full,
    /// <summary>Partial rebuild — only specific field groups need regeneration.</summary>
    Partial,
}

/// <summary>
/// Rebuild plan for a single image.
/// </summary>
public sealed class ImageRebuildPlan
{
    public required string ImagePath { get; init; }
    public required RebuildScope Scope { get; init; }

    /// <summary>
    /// Field groups that need regeneration (only populated when Scope == Partial).
    /// </summary>
    public IReadOnlyList<string> AffectedGroups { get; init; } = [];

    /// <summary>
    /// Field groups that exist in the image's manifest but are no longer in the current
    /// prompt config (e.g., a language was removed, or a field group was deleted).
    /// These should be stripped from the sidecar without an API call.
    /// </summary>
    public IReadOnlyList<string> RemovedGroups { get; init; } = [];

    /// <summary>
    /// Whether optimized images need to be regenerated (resize/format change).
    /// True even when Scope == Skip (sidecar is fine but optimization changed).
    /// </summary>
    public bool NeedsReoptimization { get; init; }

    /// <summary>
    /// Whether the sidecar has stale fields that should be stripped.
    /// </summary>
    public bool NeedsStripping => RemovedGroups.Count > 0;

    /// <summary>
    /// Human-readable reason for the rebuild decision.
    /// </summary>
    public string Reason { get; init; } = "";
}

/// <summary>
/// Determines which images need rebuilding and at what granularity
/// by comparing current prompt hashes against the build manifest.
/// </summary>
public static class RebuildPlanner
{
    /// <summary>
    /// Create a rebuild plan for a list of images given the current configuration.
    /// </summary>
    public static List<ImageRebuildPlan> Plan(
        IReadOnlyList<string> imagePaths,
        BuildManifest manifest,
        Dictionary<string, string> currentPromptHashes,
        string currentModel,
        string currentSchemaVersion,
        string outputDir,
        OptimizationConfig? optimizationConfig = null)
    {
        var plans = new List<ImageRebuildPlan>(imagePaths.Count);
        var currentOptFingerprint = optimizationConfig?.Fingerprint();

        foreach (var imagePath in imagePaths)
        {
            plans.Add(PlanForImage(imagePath, manifest, currentPromptHashes, currentModel, currentSchemaVersion, outputDir, currentOptFingerprint));
        }

        return plans;
    }

    /// <summary>
    /// Create a rebuild plan for a single image.
    /// </summary>
    public static ImageRebuildPlan PlanForImage(
        string imagePath,
        BuildManifest manifest,
        Dictionary<string, string> currentPromptHashes,
        string currentModel,
        string currentSchemaVersion,
        string outputDir,
        string? currentOptFingerprint = null)
    {
        var fileName = Path.GetFileName(imagePath);

        // No sidecar exists → full build
        if (!SidecarService.HasSidecar(imagePath, outputDir))
        {
            return new ImageRebuildPlan
            {
                ImagePath = imagePath,
                Scope = RebuildScope.Full,
                NeedsReoptimization = true,
                Reason = "no existing sidecar",
            };
        }

        // No manifest entry → full build (legacy sidecar without tracking)
        if (!manifest.Images.TryGetValue(fileName, out var entry))
        {
            return new ImageRebuildPlan
            {
                ImagePath = imagePath,
                Scope = RebuildScope.Full,
                NeedsReoptimization = true,
                Reason = "not in build manifest (legacy sidecar)",
            };
        }

        // Check if optimization config changed for this image
        var needsReopt = currentOptFingerprint is not null && (
            entry.OptimizationFingerprint is null ||
            entry.OptimizationFingerprint != currentOptFingerprint ||
            !entry.HasApiOptimized ||
            !entry.HasBundleOptimized);

        // Content hash changed → full build (image was modified)
        var currentContentHash = ImageHashService.GetContentHash(imagePath);
        if (entry.ContentHash != currentContentHash)
        {
            return new ImageRebuildPlan
            {
                ImagePath = imagePath,
                Scope = RebuildScope.Full,
                NeedsReoptimization = true,
                Reason = "image content changed",
            };
        }

        // Model changed → full build
        if (entry.Model != currentModel)
        {
            return new ImageRebuildPlan
            {
                ImagePath = imagePath,
                Scope = RebuildScope.Full,
                NeedsReoptimization = needsReopt,
                Reason = $"model changed ({entry.Model} → {currentModel})",
            };
        }

        // Schema version changed → full build (legacy sidecar upgrade)
        if (entry.SchemaVersion != currentSchemaVersion)
        {
            return new ImageRebuildPlan
            {
                ImagePath = imagePath,
                Scope = RebuildScope.Full,
                NeedsReoptimization = needsReopt,
                Reason = $"schema version changed ({entry.SchemaVersion} → {currentSchemaVersion})",
            };
        }

        // Compare field-level prompt hashes
        var staleGroups = new List<string>();
        var reasons = new List<string>();

        foreach (var (group, currentHash) in currentPromptHashes)
        {
            if (!entry.FieldHashes.TryGetValue(group, out var storedHash))
            {
                // Field group didn't exist when image was built
                staleGroups.Add(group);
                reasons.Add($"{group}: new field group");
            }
            else if (storedHash != currentHash)
            {
                staleGroups.Add(group);
                reasons.Add($"{group}: prompt changed");
            }
        }

        // Detect removed field groups (in manifest but not in current prompts)
        var removedGroups = entry.FieldHashes.Keys
            .Where(g => !currentPromptHashes.ContainsKey(g))
            .ToList();

        if (removedGroups.Count > 0)
            reasons.Add($"removed: {string.Join(", ", removedGroups)}");

        if (staleGroups.Count == 0)
        {
            return new ImageRebuildPlan
            {
                ImagePath = imagePath,
                Scope = RebuildScope.Skip,
                RemovedGroups = removedGroups,
                NeedsReoptimization = needsReopt,
                Reason = needsReopt
                    ? "sidecar up to date, optimization config changed"
                    : removedGroups.Count > 0
                        ? $"sidecar up to date, stripping: {string.Join(", ", removedGroups)}"
                        : "all field groups up to date",
            };
        }

        // If all base groups are stale, just do a full rebuild (more efficient than N partial calls)
        var staleBaseGroups = staleGroups
            .Where(g => !g.StartsWith(PromptHasher.LocalizationPrefix))
            .ToList();

        if (staleBaseGroups.Count >= PromptHasher.BaseGroups.Count)
        {
            return new ImageRebuildPlan
            {
                ImagePath = imagePath,
                Scope = RebuildScope.Full,
                RemovedGroups = removedGroups,
                NeedsReoptimization = true,
                Reason = $"all base groups stale ({string.Join(", ", reasons)})",
            };
        }

        return new ImageRebuildPlan
        {
            ImagePath = imagePath,
            Scope = RebuildScope.Partial,
            AffectedGroups = staleGroups,
            RemovedGroups = removedGroups,
            NeedsReoptimization = needsReopt,
            Reason = string.Join("; ", reasons),
        };
    }

    /// <summary>
    /// Summarize a rebuild plan for display.
    /// </summary>
    public static (int skip, int full, int partial, int reoptimize) Summarize(IReadOnlyList<ImageRebuildPlan> plans)
    {
        var skip = plans.Count(p => p.Scope == RebuildScope.Skip && !p.NeedsReoptimization);
        var full = plans.Count(p => p.Scope == RebuildScope.Full);
        var partial = plans.Count(p => p.Scope == RebuildScope.Partial);
        var reoptimize = plans.Count(p => p.NeedsReoptimization && p.Scope == RebuildScope.Skip);
        return (skip, full, partial, reoptimize);
    }
}
