using System.Text.Json;
using RiposteCli.Models;

namespace RiposteCli.Services;

/// <summary>
/// Merges partial analysis results into existing sidecar metadata.
/// Only overwrites fields belonging to the specified field groups.
/// </summary>
public static class SidecarMerger
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
    };

    /// <summary>
    /// Load an existing sidecar from disk.
    /// </summary>
    public static SidecarMetadata? LoadSidecar(string imagePath, string outputDir)
    {
        var sidecarPath = Path.Combine(outputDir, Path.GetFileName(imagePath) + ".json");
        if (!File.Exists(sidecarPath))
            return null;

        var json = File.ReadAllText(sidecarPath);
        return JsonSerializer.Deserialize<SidecarMetadata>(json, JsonOptions);
    }

    /// <summary>
    /// Merge partial analysis results into an existing sidecar, overwriting only the
    /// fields belonging to the specified field groups.
    /// </summary>
    public static SidecarMetadata Merge(
        SidecarMetadata existing,
        AnalysisResult partial,
        IReadOnlyList<string> affectedGroups)
    {
        var emojis = existing.Emojis;
        var title = existing.Title;
        var description = existing.Description;
        var tags = existing.Tags;
        var searchPhrases = existing.SearchPhrases;
        var basedOn = existing.BasedOn;
        var emotions = existing.Emotions;
        var localizations = existing.Localizations is not null
            ? new Dictionary<string, LocalizedContent>(existing.Localizations)
            : new Dictionary<string, LocalizedContent>();

        foreach (var group in affectedGroups)
        {
            switch (group)
            {
                case PromptHasher.GroupCore:
                    emojis = partial.Emojis;
                    title = partial.Title ?? title;
                    description = partial.Description ?? description;
                    break;

                case PromptHasher.GroupSearch:
                    tags = partial.Tags ?? tags;
                    searchPhrases = partial.SearchPhrases ?? searchPhrases;
                    break;

                case PromptHasher.GroupCultural:
                    basedOn = partial.BasedOn;
                    break;

                case PromptHasher.GroupEmotions:
                    emotions = partial.Emotions ?? emotions;
                    break;

                default:
                    if (group.StartsWith(PromptHasher.LocalizationPrefix))
                    {
                        var langCode = group[PromptHasher.LocalizationPrefix.Length..];
                        if (partial.Localizations is not null &&
                            partial.Localizations.TryGetValue(langCode, out var localized))
                        {
                            localizations[langCode] = localized;
                        }
                    }
                    break;
            }
        }

        return new SidecarMetadata
        {
            SchemaVersion = existing.SchemaVersion,
            Emojis = emojis,
            CreatedAt = existing.CreatedAt,
            Title = title,
            Description = description,
            Tags = tags,
            SearchPhrases = searchPhrases,
            PrimaryLanguage = existing.PrimaryLanguage,
            Localizations = localizations.Count > 0 ? localizations : null,
            ContentHash = existing.ContentHash,
            BasedOn = basedOn,
            Emotions = emotions,
        };
    }

    /// <summary>
    /// Strip field groups that are no longer in the current prompt configuration
    /// (e.g., a language was removed).
    /// </summary>
    public static SidecarMetadata StripRemovedGroups(
        SidecarMetadata existing,
        Dictionary<string, string> currentPromptHashes)
    {
        if (existing.Localizations is null || existing.Localizations.Count == 0)
            return existing;

        var currentLangs = currentPromptHashes.Keys
            .Where(k => k.StartsWith(PromptHasher.LocalizationPrefix))
            .Select(k => k[PromptHasher.LocalizationPrefix.Length..])
            .ToHashSet();

        var filtered = existing.Localizations
            .Where(kv => currentLangs.Contains(kv.Key))
            .ToDictionary(kv => kv.Key, kv => kv.Value);

        if (filtered.Count == existing.Localizations.Count)
            return existing;

        return new SidecarMetadata
        {
            SchemaVersion = existing.SchemaVersion,
            Emojis = existing.Emojis,
            CreatedAt = existing.CreatedAt,
            Title = existing.Title,
            Description = existing.Description,
            Tags = existing.Tags,
            SearchPhrases = existing.SearchPhrases,
            PrimaryLanguage = existing.PrimaryLanguage,
            Localizations = filtered.Count > 0 ? filtered : null,
            ContentHash = existing.ContentHash,
            BasedOn = existing.BasedOn,
            Emotions = existing.Emotions,
        };
    }
}
