using System.Security.Cryptography;
using System.Text;

namespace RiposteCli.Services;

/// <summary>
/// Computes deterministic SHA-256 hashes of prompt sections per field group.
/// Used by the smart rebuild system to detect which field groups need regeneration.
/// </summary>
public static class PromptHasher
{
    public const string GroupCore = "core";
    public const string GroupSearch = "search";
    public const string GroupCultural = "cultural";
    public const string GroupEmotions = "emotions";
    public const string LocalizationPrefix = "localization:";

    public static string LocalizationGroup(string langCode) => $"{LocalizationPrefix}{langCode}";

    /// <summary>
    /// All non-localization field groups.
    /// </summary>
    public static readonly IReadOnlyList<string> BaseGroups = [GroupCore, GroupSearch, GroupCultural, GroupEmotions];

    /// <summary>
    /// Compute prompt hashes for all field groups given the current language configuration.
    /// </summary>
    public static Dictionary<string, string> ComputeAll(IReadOnlyList<string> languages)
    {
        var primaryLang = languages.Count > 0 ? languages[0] : "en";
        var primaryName = Prompts.GetLanguageName(primaryLang);

        var hashes = new Dictionary<string, string>
        {
            [GroupCore] = Hash(GetCoreSpec(primaryName, primaryLang)),
            [GroupSearch] = Hash(GetSearchSpec(primaryName, primaryLang)),
            [GroupCultural] = Hash(GetCulturalSpec()),
            [GroupEmotions] = Hash(GetEmotionsSpec(primaryName, primaryLang)),
        };

        foreach (var lang in languages.Skip(1))
        {
            var langName = Prompts.GetLanguageName(lang);
            hashes[LocalizationGroup(lang)] = Hash(GetLocalizationSpec(langName, lang));
        }

        return hashes;
    }

    /// <summary>
    /// Core field group: emojis, title, description.
    /// </summary>
    internal static string GetCoreSpec(string primaryName, string primaryLang) => $$"""
        1. "emojis": An array of 1-8 Unicode emoji characters that best represent the mood, emotion, or theme of the meme. Order from most significant/relevant to least significant. All emojis should be relevant.

        2. "title": A simple, descriptive title in {{primaryName}} that plainly describes the meme content (max 50 characters). Don't try to be clever or catchy - just describe what's in the image.

        3. "description": A thorough description in {{primaryName}} covering: what's literally in the image, the mood or emotion it conveys, and any themes or cultural references it relates to (e.g., programming, Witcher, Harry Potter, GTA, science, etc.). If there is text visible in the image, incorporate it naturally into your description.
        """;

    /// <summary>
    /// Search field group: tags, searchPhrases.
    /// </summary>
    internal static string GetSearchSpec(string primaryName, string primaryLang) => $$"""
        4. "tags": An array of 8-15 lowercase keywords/tags in {{primaryName}} covering: subject matter, emotion/mood, synonyms, common slang, meme format name if recognizable, and related cultural references.

        5. "searchPhrases": An array of 2-3 short natural language phrases in {{primaryName}} someone might type when searching for this meme.
        """;

    /// <summary>
    /// Cultural field group: basedOn.
    /// </summary>
    internal static string GetCulturalSpec() => """
        6. "basedOn": If the image is based on a recognizable meme template, franchise, video game, movie, TV show, or other cultural reference, provide its name. Use the most commonly known name. If the source is not recognizable or the image is original content, omit this field or set to null.
        """;

    /// <summary>
    /// Emotions field group: emotions.*.
    /// </summary>
    internal static string GetEmotionsSpec(string primaryName, string primaryLang) => $$"""
        7. "emotions": An object describing the emotional content and meme usage:
           - "primary": The single most dominant emotion/mood. Choose from: joy, amusement, humor, sarcasm, irony, absurdity, cringe, wholesome, love, excitement, pride, triumph, gratitude, surprise, confusion, nostalgia, relatability, sadness, anger, fear, disgust, frustration, anxiety, disappointment, embarrassment, determination, serenity, awe.
           - "secondary": Array of 1-4 additional emotions that also apply from the same list.
           - "sentiment": Overall sentiment — one of: "positive", "negative", "neutral", "mixed".
           - "intensity": How strongly the emotion is expressed — one of: "low", "medium", "high".
           - "memeUsage": Array of 2-4 short phrases in {{primaryName}} describing WHEN or HOW someone would use this meme in conversation (e.g., "when something unexpectedly funny happens", "me on Monday morning", "reaction to bad news", "that feeling when you ace an exam").
        """;

    /// <summary>
    /// Localization spec for a single additional language.
    /// </summary>
    internal static string GetLocalizationSpec(string langName, string langCode) => $$"""
        "localizations": Contains translations for {{langName}} ({{langCode}}). Each value is an object with "title", "description", "tags", and "searchPhrases" fields in {{langName}}.
        """;

    /// <summary>
    /// Compute SHA-256 hash of a string, returned as hex.
    /// </summary>
    public static string Hash(string input)
    {
        var bytes = SHA256.HashData(Encoding.UTF8.GetBytes(input));
        return Convert.ToHexString(bytes).ToLowerInvariant();
    }
}
