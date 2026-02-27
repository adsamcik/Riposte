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
        string outputDir)
    {
        var plans = new List<ImageRebuildPlan>(imagePaths.Count);

        foreach (var imagePath in imagePaths)
        {
            plans.Add(PlanForImage(imagePath, manifest, currentPromptHashes, currentModel, currentSchemaVersion, outputDir));
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
        string outputDir)
    {
        var fileName = Path.GetFileName(imagePath);

        // No sidecar exists → full build
        if (!SidecarService.HasSidecar(imagePath, outputDir))
        {
            return new ImageRebuildPlan
            {
                ImagePath = imagePath,
                Scope = RebuildScope.Full,
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
                Reason = "not in build manifest (legacy sidecar)",
            };
        }

        // Content hash changed → full build (image was modified)
        var currentContentHash = ImageHashService.GetContentHash(imagePath);
        if (entry.ContentHash != currentContentHash)
        {
            return new ImageRebuildPlan
            {
                ImagePath = imagePath,
                Scope = RebuildScope.Full,
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
                Reason = $"model changed ({entry.Model} → {currentModel})",
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

        // Check for removed field groups (in manifest but not in current prompts)
        // These don't need a rebuild — they'll be stripped during merge

        if (staleGroups.Count == 0)
        {
            return new ImageRebuildPlan
            {
                ImagePath = imagePath,
                Scope = RebuildScope.Skip,
                Reason = "all field groups up to date",
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
                Reason = $"all base groups stale ({string.Join(", ", reasons)})",
            };
        }

        return new ImageRebuildPlan
        {
            ImagePath = imagePath,
            Scope = RebuildScope.Partial,
            AffectedGroups = staleGroups,
            Reason = string.Join("; ", reasons),
        };
    }

    /// <summary>
    /// Summarize a rebuild plan for display.
    /// </summary>
    public static (int skip, int full, int partial) Summarize(IReadOnlyList<ImageRebuildPlan> plans)
    {
        var skip = plans.Count(p => p.Scope == RebuildScope.Skip);
        var full = plans.Count(p => p.Scope == RebuildScope.Full);
        var partial = plans.Count(p => p.Scope == RebuildScope.Partial);
        return (skip, full, partial);
    }
}
