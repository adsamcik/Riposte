namespace RiposteCli;

using RiposteCli.Services;

/// <summary>
/// System prompts for meme analysis and language mapping.
/// </summary>
public static class Prompts
{
    private static readonly Dictionary<string, string> LanguageNames = new()
    {
        ["en"] = "English",
        ["cs"] = "Czech",
        ["de"] = "German",
        ["es"] = "Spanish",
        ["fr"] = "French",
        ["it"] = "Italian",
        ["ja"] = "Japanese",
        ["ko"] = "Korean",
        ["pl"] = "Polish",
        ["pt"] = "Portuguese",
        ["ru"] = "Russian",
        ["uk"] = "Ukrainian",
        ["zh"] = "Chinese (Simplified)",
        ["zh-TW"] = "Chinese (Traditional)",
    };

    public static string GetLanguageName(string code) =>
        LanguageNames.GetValueOrDefault(code, code);

    public static string GetSystemPrompt(IReadOnlyList<string> languages)
    {
        var primaryLang = languages.Count > 0 ? languages[0] : "en";
        var primaryName = GetLanguageName(primaryLang);

        if (languages.Count <= 1)
            return GetSingleLanguagePrompt(primaryName, primaryLang);

        var additionalLangs = languages.Skip(1).ToList();
        return GetMultilingualPrompt(primaryName, primaryLang, additionalLangs);
    }

    private static string GetSingleLanguagePrompt(string primaryName, string primaryLang)
    {
        return $$"""
            You are a meme analysis assistant. Analyze the provided meme image and return a JSON object with the following fields.

            IMPORTANT: All text fields (title, description, tags, searchPhrases) must be in {{primaryName}} ({{primaryLang}}).

            Fields:
            1. "emojis": An array of 1-8 Unicode emoji characters that best represent the mood, emotion, or theme of the meme. Order from most significant/relevant to least significant. All emojis should be relevant.

            2. "title": A simple, descriptive title in {{primaryName}} that plainly describes the meme content (max 50 characters). Don't try to be clever or catchy - just describe what's in the image.

            3. "description": A thorough description in {{primaryName}} covering: what's literally in the image, the mood or emotion it conveys, and any themes or cultural references it relates to (e.g., programming, Witcher, Harry Potter, GTA, science, etc.). If there is text visible in the image, incorporate it naturally into your description.

            4. "tags": An array of 8-15 lowercase keywords/tags in {{primaryName}} covering: subject matter, emotion/mood, synonyms, common slang, meme format name if recognizable, and related cultural references.

            5. "searchPhrases": An array of 2-3 short natural language phrases in {{primaryName}} someone might type when searching for this meme.

            6. "basedOn": If the image is based on a recognizable meme template, franchise, video game, movie, TV show, or other cultural reference, provide its name. Use the most commonly known name. If the source is not recognizable or the image is original content, omit this field or set to null.

            7. "emotions": An object describing the emotional content and meme usage:
               - "primary": The single most dominant emotion/mood. Choose from: joy, amusement, humor, sarcasm, irony, absurdity, cringe, wholesome, love, excitement, pride, triumph, gratitude, surprise, confusion, nostalgia, relatability, sadness, anger, fear, disgust, frustration, anxiety, disappointment, embarrassment, determination, serenity, awe.
               - "secondary": Array of 1-4 additional emotions that also apply from the same list.
               - "sentiment": Overall sentiment — one of: "positive", "negative", "neutral", "mixed".
               - "intensity": How strongly the emotion is expressed — one of: "low", "medium", "high".
               - "memeUsage": Array of 2-4 short phrases in {{primaryName}} describing WHEN or HOW someone would use this meme in conversation (e.g., "when something unexpectedly funny happens", "me on Monday morning", "reaction to bad news", "that feeling when you ace an exam").

            Respond ONLY with valid JSON, no markdown or explanation. Example:
            {"emojis": ["😂", "🐱", "💻", "🤷"], "title": "Confused cat at computer", "description": "A cat sitting at a desk staring at a screen full of code with a bewildered expression.", "tags": ["cat", "programming", "confused", "funny", "code", "developer", "humor", "relatable", "reaction"], "searchPhrases": ["confused programmer cat", "code works no idea why"], "basedOn": "Programmer humor", "emotions": {"primary": "humor", "secondary": ["confusion", "relatability", "absurdity"], "sentiment": "positive", "intensity": "medium", "memeUsage": ["when code works but you have no idea why", "debugging at 3am", "reaction to confusing error messages"] } }
            """;
    }

    private static string GetMultilingualPrompt(string primaryName, string primaryLang, IReadOnlyList<string> additionalLangs)
    {
        var additionalDesc = string.Join(", ", additionalLangs.Select(lang => $"{GetLanguageName(lang)} ({lang})"));

        return $$"""
            You are a meme analysis assistant. Analyze the provided meme image and return a JSON object with multilingual content.

            PRIMARY LANGUAGE: {{primaryName}} ({{primaryLang}})
            ADDITIONAL LANGUAGES: {{additionalDesc}}

            Fields:
            1. "emojis": An array of 1-8 Unicode emoji characters that best represent the mood, emotion, or theme of the meme. Order from most significant/relevant to least significant. All emojis should be relevant.

            2. "title": A simple, descriptive title in {{primaryName}} that plainly describes the meme content (max 50 characters). Don't try to be clever or catchy - just describe what's in the image.

            3. "description": A thorough description in {{primaryName}} covering: what's literally in the image, the mood or emotion it conveys, and any themes or cultural references it relates to (e.g., programming, Witcher, Harry Potter, GTA, science, etc.). If there is text visible in the image, incorporate it naturally into your description.

            4. "tags": An array of 8-15 lowercase keywords/tags in {{primaryName}} covering: subject matter, emotion/mood, synonyms, common slang, meme format name if recognizable, and related cultural references.

            5. "searchPhrases": An array of 2-3 short natural language phrases in {{primaryName}} someone might type when searching for this meme.

            6. "basedOn": If the image is based on a recognizable meme template, franchise, video game, movie, TV show, or other cultural reference, provide its name. Use the most commonly known name. If the source is not recognizable or the image is original content, omit this field or set to null.

            7. "emotions": An object describing the emotional content and meme usage:
               - "primary": The single most dominant emotion/mood. Choose from: joy, amusement, humor, sarcasm, irony, absurdity, cringe, wholesome, love, excitement, pride, triumph, gratitude, surprise, confusion, nostalgia, relatability, sadness, anger, fear, disgust, frustration, anxiety, disappointment, embarrassment, determination, serenity, awe.
               - "secondary": Array of 1-4 additional emotions that also apply from the same list.
               - "sentiment": Overall sentiment — one of: "positive", "negative", "neutral", "mixed".
               - "intensity": How strongly the emotion is expressed — one of: "low", "medium", "high".
               - "memeUsage": Array of 2-4 short phrases in {{primaryName}} describing WHEN or HOW someone would use this meme (e.g., "when something unexpectedly funny happens", "me on Monday morning").

            8. "localizations": An object containing translations for each additional language. Each key is a language code, and each value is an object with "title", "description", "tags", and "searchPhrases" fields in that language.

            Respond ONLY with valid JSON, no markdown or explanation.
            """;
    }

    // --- Partial prompts for smart rebuild ---

    /// <summary>
    /// Build a partial prompt requesting only the specified field groups.
    /// </summary>
    public static string GetPartialPrompt(IReadOnlyList<string> fieldGroups, IReadOnlyList<string> languages)
    {
        var primaryLang = languages.Count > 0 ? languages[0] : "en";
        var primaryName = GetLanguageName(primaryLang);

        var sb = new System.Text.StringBuilder();
        sb.AppendLine("You are a meme analysis assistant. I already have partial metadata for this meme image. I need you to generate ONLY the following fields.\n");

        var fieldNumber = 1;
        foreach (var group in fieldGroups)
        {
            var spec = group switch
            {
                PromptHasher.GroupCore => PromptHasher.GetCoreSpec(primaryName, primaryLang),
                PromptHasher.GroupSearch => PromptHasher.GetSearchSpec(primaryName, primaryLang),
                PromptHasher.GroupCultural => PromptHasher.GetCulturalSpec(),
                PromptHasher.GroupEmotions => PromptHasher.GetEmotionsSpec(primaryName, primaryLang),
                _ when group.StartsWith(PromptHasher.LocalizationPrefix) =>
                    GetLocalizationPartialSpec(group, languages),
                _ => null,
            };

            if (spec is not null)
            {
                sb.AppendLine(spec.Trim());
                sb.AppendLine();
                fieldNumber++;
            }
        }

        sb.AppendLine("Respond ONLY with valid JSON containing just the requested fields, no markdown or explanation.");
        return sb.ToString();
    }

    private static string GetLocalizationPartialSpec(string group, IReadOnlyList<string> languages)
    {
        var langCode = group[PromptHasher.LocalizationPrefix.Length..];
        var langName = GetLanguageName(langCode);
        return $$"""
            "localizations": An object with key "{{langCode}}" containing translations in {{langName}}. The value is an object with "title", "description", "tags", and "searchPhrases" fields, all in {{langName}}.
            """;
    }
}
