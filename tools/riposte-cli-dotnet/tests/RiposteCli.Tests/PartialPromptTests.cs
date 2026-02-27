namespace RiposteCli.Tests;

public class PartialPromptTests
{
    private static readonly string[] DefaultLanguages = ["en"];

    // --- GetPartialPrompt: Core group only ---

    [Fact]
    public void GetPartialPrompt_CoreGroupOnly_ContainsEmojisTitleDescription()
    {
        var result = Prompts.GetPartialPrompt([PromptHasher.GroupCore], DefaultLanguages);

        Assert.Contains("emojis", result);
        Assert.Contains("title", result);
        Assert.Contains("description", result);
    }

    [Fact]
    public void GetPartialPrompt_CoreGroupOnly_DoesNotContainOtherGroups()
    {
        var result = Prompts.GetPartialPrompt([PromptHasher.GroupCore], DefaultLanguages);

        Assert.DoesNotContain("\"tags\"", result);
        Assert.DoesNotContain("\"basedOn\"", result);
        Assert.DoesNotContain("\"emotions\"", result);
    }

    // --- GetPartialPrompt: Search group only ---

    [Fact]
    public void GetPartialPrompt_SearchGroupOnly_ContainsTagsAndSearchPhrases()
    {
        var result = Prompts.GetPartialPrompt([PromptHasher.GroupSearch], DefaultLanguages);

        Assert.Contains("tags", result);
        Assert.Contains("searchPhrases", result);
    }

    [Fact]
    public void GetPartialPrompt_SearchGroupOnly_DoesNotContainOtherGroups()
    {
        var result = Prompts.GetPartialPrompt([PromptHasher.GroupSearch], DefaultLanguages);

        Assert.DoesNotContain("\"emojis\"", result);
        Assert.DoesNotContain("\"basedOn\"", result);
    }

    // --- GetPartialPrompt: Cultural group only ---

    [Fact]
    public void GetPartialPrompt_CulturalGroupOnly_ContainsBasedOn()
    {
        var result = Prompts.GetPartialPrompt([PromptHasher.GroupCultural], DefaultLanguages);

        Assert.Contains("basedOn", result);
    }

    [Fact]
    public void GetPartialPrompt_CulturalGroupOnly_DoesNotContainOtherGroups()
    {
        var result = Prompts.GetPartialPrompt([PromptHasher.GroupCultural], DefaultLanguages);

        Assert.DoesNotContain("\"emojis\"", result);
        Assert.DoesNotContain("\"tags\"", result);
    }

    // --- GetPartialPrompt: Emotions group only ---

    [Fact]
    public void GetPartialPrompt_EmotionsGroupOnly_ContainsEmotionFields()
    {
        var result = Prompts.GetPartialPrompt([PromptHasher.GroupEmotions], DefaultLanguages);

        Assert.Contains("emotions", result);
        Assert.Contains("primary", result);
        Assert.Contains("secondary", result);
        Assert.Contains("sentiment", result);
    }

    [Fact]
    public void GetPartialPrompt_EmotionsGroupOnly_DoesNotContainOtherGroups()
    {
        var result = Prompts.GetPartialPrompt([PromptHasher.GroupEmotions], DefaultLanguages);

        Assert.DoesNotContain("\"emojis\"", result);
        Assert.DoesNotContain("\"tags\"", result);
    }

    // --- GetPartialPrompt: Localization group ---

    [Fact]
    public void GetPartialPrompt_LocalizationGroup_ContainsLanguageNameAndCode()
    {
        var group = PromptHasher.LocalizationGroup("cs");
        var result = Prompts.GetPartialPrompt([group], ["en", "cs"]);

        Assert.Contains("Czech", result);
        Assert.Contains("cs", result);
    }

    // --- GetPartialPrompt: Multiple groups ---

    [Fact]
    public void GetPartialPrompt_MultipleGroups_AllRequestedGroupsPresent()
    {
        var result = Prompts.GetPartialPrompt(
            [PromptHasher.GroupCore, PromptHasher.GroupSearch, PromptHasher.GroupEmotions],
            DefaultLanguages);

        Assert.Contains("emojis", result);
        Assert.Contains("title", result);
        Assert.Contains("tags", result);
        Assert.Contains("searchPhrases", result);
        Assert.Contains("emotions", result);
        Assert.Contains("primary", result);
    }

    // --- GetPartialPrompt: Instruction content ---

    [Fact]
    public void GetPartialPrompt_ContainsOnlyTheFollowingFieldsInstruction()
    {
        var result = Prompts.GetPartialPrompt([PromptHasher.GroupCore], DefaultLanguages);

        Assert.Contains("ONLY the following fields", result);
    }

    [Fact]
    public void GetPartialPrompt_ContainsValidJsonInstruction()
    {
        var result = Prompts.GetPartialPrompt([PromptHasher.GroupCore], DefaultLanguages);

        Assert.Contains("valid JSON", result);
    }

    // --- GetPartialPrompt: Language parameter reflected ---

    [Fact]
    public void GetPartialPrompt_LanguageReflectedInLanguageDependentGroups()
    {
        var result = Prompts.GetPartialPrompt([PromptHasher.GroupCore], ["cs"]);

        Assert.Contains("Czech", result);
    }

    // --- GetSystemPrompt: Single language ---

    [Fact]
    public void GetSystemPrompt_SingleLanguage_DoesNotContainLocalizations()
    {
        var result = Prompts.GetSystemPrompt(["en"]);

        Assert.DoesNotContain("localizations", result);
    }

    // --- GetSystemPrompt: Multi-language ---

    [Fact]
    public void GetSystemPrompt_MultiLanguage_ContainsLocalizationsSection()
    {
        var result = Prompts.GetSystemPrompt(["en", "cs"]);

        Assert.Contains("localizations", result);
    }

    [Fact]
    public void GetSystemPrompt_MultiLanguage_ContainsAdditionalLanguages()
    {
        var result = Prompts.GetSystemPrompt(["en", "cs", "de"]);

        Assert.Contains("Czech", result);
        Assert.Contains("German", result);
    }

    // --- GetSystemPrompt: All field groups represented ---

    [Fact]
    public void GetSystemPrompt_ContainsAllFieldGroups()
    {
        var result = Prompts.GetSystemPrompt(["en"]);

        Assert.Contains("emojis", result);
        Assert.Contains("title", result);
        Assert.Contains("description", result);
        Assert.Contains("tags", result);
        Assert.Contains("searchPhrases", result);
        Assert.Contains("basedOn", result);
        Assert.Contains("emotions", result);
    }

    // --- GetSystemPrompt: Language name in prompt ---

    [Fact]
    public void GetSystemPrompt_LanguageNameAppearsInPrompt()
    {
        var result = Prompts.GetSystemPrompt(["en"]);
        Assert.Contains("English", result);
    }

    [Fact]
    public void GetSystemPrompt_CzechLanguageNameAppearsInPrompt()
    {
        var result = Prompts.GetSystemPrompt(["cs"]);
        Assert.Contains("Czech", result);
    }

    // --- GetLanguageName ---

    [Theory]
    [InlineData("en", "English")]
    [InlineData("cs", "Czech")]
    [InlineData("de", "German")]
    public void GetLanguageName_KnownCodes_ReturnsCorrectNames(string code, string expected)
    {
        var result = Prompts.GetLanguageName(code);

        Assert.Equal(expected, result);
    }

    [Fact]
    public void GetLanguageName_UnknownCode_ReturnsCodeItself()
    {
        var result = Prompts.GetLanguageName("xx");

        Assert.Equal("xx", result);
    }
}
