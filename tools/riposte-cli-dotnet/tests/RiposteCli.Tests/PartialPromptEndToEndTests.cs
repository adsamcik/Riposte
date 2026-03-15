using RiposteCli.Models;

namespace RiposteCli.Tests;

/// <summary>
/// End-to-end tests for the partial prompt system:
/// prompt generation → (simulated) response parsing → merge.
/// </summary>
public class PartialPromptEndToEndTests
{
    private static readonly string[] EnglishOnly = ["en"];

    // ─── 1. Search-only partial includes "tags" and "searchPhrases" ─────

    [Fact]
    public void GetPartialPrompt_SearchOnly_IncludesTagsAndSearchPhrases()
    {
        var result = Prompts.GetPartialPrompt([PromptHasher.GroupSearch], EnglishOnly);

        Assert.Contains("\"tags\"", result);
        Assert.Contains("\"searchPhrases\"", result);
    }

    // ─── 2. Emotions-only partial includes all emotion subfields ────────

    [Fact]
    public void GetPartialPrompt_EmotionsOnly_IncludesAllSubfields()
    {
        var result = Prompts.GetPartialPrompt([PromptHasher.GroupEmotions], EnglishOnly);

        Assert.Contains("\"primary\"", result);
        Assert.Contains("\"secondary\"", result);
        Assert.Contains("\"sentiment\"", result);
        Assert.Contains("\"intensity\"", result);
        Assert.Contains("\"memeUsage\"", result);
    }

    // ─── 3. Localization partial includes the correct language name ─────

    [Fact]
    public void GetPartialPrompt_Localization_IncludesCorrectLanguageName()
    {
        var group = PromptHasher.LocalizationGroup("de");
        var result = Prompts.GetPartialPrompt([group], ["en", "de"]);

        Assert.Contains("German", result);
        Assert.Contains("\"de\"", result);
    }

    // ─── 4. Merge with extra unexpected fields preserves existing ───────

    [Fact]
    public void Merge_ExtraUnexpectedFields_PreservesExisting()
    {
        var existing = new SidecarMetadata
        {
            SchemaVersion = "1.4",
            Emojis = ["😂", "🔥"],
            Title = "Original Title",
            Description = "Original Description",
            Tags = ["original-tag"],
            SearchPhrases = ["original phrase"],
            BasedOn = "Original Source",
            Emotions = new EmotionMetadata
            {
                Primary = "humor",
                Sentiment = "positive",
            },
        };

        // AI returned more fields than requested (emojis, title, emotions are extras)
        var partial = new AnalysisResult
        {
            Emojis = ["🚀", "🎉"],
            Title = "AI Title",
            Tags = ["ai-tag-1", "ai-tag-2"],
            SearchPhrases = ["ai search"],
            Emotions = new EmotionMetadata
            {
                Primary = "awe",
                Sentiment = "mixed",
            },
        };

        // Only search group was requested
        var result = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupSearch]);

        // Search fields updated
        Assert.Equal(partial.Tags, result.Tags);
        Assert.Equal(partial.SearchPhrases, result.SearchPhrases);

        // Extra fields NOT applied — existing preserved
        Assert.Equal(existing.Emojis, result.Emojis);
        Assert.Equal("Original Title", result.Title);
        Assert.Equal("Original Description", result.Description);
        Assert.Equal("Original Source", result.BasedOn);
        Assert.Equal("humor", result.Emotions!.Primary);
    }

    // ─── 5. Localization-only merge doesn't touch core fields ───────────

    [Fact]
    public void Merge_LocalizationOnly_DoesNotTouchCoreFields()
    {
        var existing = new SidecarMetadata
        {
            SchemaVersion = "1.4",
            Emojis = ["😂"],
            Title = "Core Title",
            Description = "Core Desc",
            Tags = ["core-tag"],
            SearchPhrases = ["core search"],
            BasedOn = "Core Source",
            Emotions = new EmotionMetadata
            {
                Primary = "humor",
                Sentiment = "positive",
            },
        };

        var partial = new AnalysisResult
        {
            Localizations = new Dictionary<string, LocalizedContent>
            {
                ["cs"] = new()
                {
                    Title = "Český titulek",
                    Description = "Český popis",
                    Tags = ["český-tag"],
                    SearchPhrases = ["hledej"],
                },
            },
        };

        var result = SidecarMerger.Merge(
            existing, partial, [PromptHasher.LocalizationGroup("cs")]);

        // All core fields unchanged
        Assert.Equal(existing.Emojis, result.Emojis);
        Assert.Equal("Core Title", result.Title);
        Assert.Equal("Core Desc", result.Description);
        Assert.Equal(existing.Tags, result.Tags);
        Assert.Equal(existing.SearchPhrases, result.SearchPhrases);
        Assert.Equal("Core Source", result.BasedOn);
        Assert.Equal("humor", result.Emotions!.Primary);

        // Localization applied
        Assert.NotNull(result.Localizations);
        Assert.True(result.Localizations!.ContainsKey("cs"));
        Assert.Equal("Český titulek", result.Localizations["cs"].Title);
    }

    // ─── 6. Multiple localization groups both appear in prompt ──────────

    [Fact]
    public void GetPartialPrompt_MultipleLocalizations_BothAppearInPrompt()
    {
        var groups = new[]
        {
            PromptHasher.LocalizationGroup("cs"),
            PromptHasher.LocalizationGroup("de"),
        };

        var result = Prompts.GetPartialPrompt(groups, ["en", "cs", "de"]);

        Assert.Contains("Czech", result);
        Assert.Contains("German", result);
        Assert.Contains("\"cs\"", result);
        Assert.Contains("\"de\"", result);
    }

    // ─── Bonus: Field numbering is sequential in partial prompts ────────

    [Fact]
    public void GetPartialPrompt_SearchOnly_NumbersStartAtOne()
    {
        var result = Prompts.GetPartialPrompt([PromptHasher.GroupSearch], EnglishOnly);

        // Search fields should start at 1, not 4 (their hardcoded number in the full prompt)
        Assert.Contains("1. \"tags\"", result);
        Assert.Contains("2. \"searchPhrases\"", result);
    }

    [Fact]
    public void GetPartialPrompt_MultipleLocalizations_CombinedIntoSingleInstruction()
    {
        var groups = new[]
        {
            PromptHasher.LocalizationGroup("cs"),
            PromptHasher.LocalizationGroup("de"),
        };

        var result = Prompts.GetPartialPrompt(groups, ["en", "cs", "de"]);

        // Should have a single "localizations" instruction, not two separate ones
        var count = result.Split("\"localizations\"").Length - 1;
        Assert.Equal(1, count);
    }

    [Fact]
    public void GetPartialPrompt_CorePlusLocalization_NumbersContinueSequentially()
    {
        var groups = new[]
        {
            PromptHasher.GroupCore,
            PromptHasher.LocalizationGroup("cs"),
        };

        var result = Prompts.GetPartialPrompt(groups, ["en", "cs"]);

        // Core has 3 fields (1, 2, 3), localization should be 4
        Assert.Contains("1. \"emojis\"", result);
        Assert.Contains("2. \"title\"", result);
        Assert.Contains("3. \"description\"", result);
        Assert.Contains("4. \"localizations\"", result);
    }
}
