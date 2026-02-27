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
    /// (e.g., a language was removed, or a field group was deleted from the schema).
    /// </summary>
    public static SidecarMetadata StripRemovedGroups(
        SidecarMetadata existing,
        Dictionary<string, string> currentPromptHashes)
    {
        var changed = false;

        // Strip removed localizations
        Dictionary<string, LocalizedContent>? filteredLocalizations = existing.Localizations;
        if (existing.Localizations is { Count: > 0 })
        {
            var currentLangs = currentPromptHashes.Keys
                .Where(k => k.StartsWith(PromptHasher.LocalizationPrefix))
                .Select(k => k[PromptHasher.LocalizationPrefix.Length..])
                .ToHashSet();

            filteredLocalizations = existing.Localizations
                .Where(kv => currentLangs.Contains(kv.Key))
                .ToDictionary(kv => kv.Key, kv => kv.Value);

            if (filteredLocalizations.Count != existing.Localizations.Count)
                changed = true;
        }

        // Strip removed base field groups
        var hasCore = currentPromptHashes.ContainsKey(PromptHasher.GroupCore);
        var hasSearch = currentPromptHashes.ContainsKey(PromptHasher.GroupSearch);
        var hasCultural = currentPromptHashes.ContainsKey(PromptHasher.GroupCultural);
        var hasEmotions = currentPromptHashes.ContainsKey(PromptHasher.GroupEmotions);

        // If a base group was removed, null out its fields
        var emojis = existing.Emojis;
        var title = hasCore ? existing.Title : null;
        var description = hasCore ? existing.Description : null;
        var tags = hasSearch ? existing.Tags : null;
        var searchPhrases = hasSearch ? existing.SearchPhrases : null;
        var basedOn = hasCultural ? existing.BasedOn : null;
        var emotions = hasEmotions ? existing.Emotions : null;

        if (!hasCore && (existing.Title is not null || existing.Description is not null)) changed = true;
        if (!hasSearch && (existing.Tags is not null || existing.SearchPhrases is not null)) changed = true;
        if (!hasCultural && existing.BasedOn is not null) changed = true;
        if (!hasEmotions && existing.Emotions is not null) changed = true;

        if (!changed)
            return existing;

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
            Localizations = filteredLocalizations is { Count: > 0 } ? filteredLocalizations : null,
            ContentHash = existing.ContentHash,
            BasedOn = basedOn,
            Emotions = emotions,
        };
    }
}
