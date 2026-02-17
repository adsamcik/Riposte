namespace RiposteCli.Tests;

/// <summary>
/// Extended tests for Prompts: language mapping, prompt generation, edge cases.
/// </summary>
public class PromptsExtendedTests
{
    #region Language Name Mapping

    [Theory]
    [InlineData("en", "English")]
    [InlineData("cs", "Czech")]
    [InlineData("de", "German")]
    [InlineData("es", "Spanish")]
    [InlineData("fr", "French")]
    [InlineData("it", "Italian")]
    [InlineData("ja", "Japanese")]
    [InlineData("ko", "Korean")]
    [InlineData("pl", "Polish")]
    [InlineData("pt", "Portuguese")]
    [InlineData("ru", "Russian")]
    [InlineData("uk", "Ukrainian")]
    [InlineData("zh", "Chinese (Simplified)")]
    [InlineData("zh-TW", "Chinese (Traditional)")]
    public void GetLanguageName_KnownCodes(string code, string expectedName)
    {
        Assert.Equal(expectedName, Prompts.GetLanguageName(code));
    }

    [Theory]
    [InlineData("xx")]
    [InlineData("unknown")]
    [InlineData("")]
    [InlineData("ar")]
    [InlineData("hi")]
    public void GetLanguageName_UnknownCode_ReturnsCodeItself(string code)
    {
        Assert.Equal(code, Prompts.GetLanguageName(code));
    }

    #endregion

    #region Single Language Prompt

    [Fact]
    public void GetSystemPrompt_SingleLanguage_ContainsLanguageName()
    {
        var prompt = Prompts.GetSystemPrompt(["en"]);
        Assert.Contains("English", prompt);
        Assert.Contains("en", prompt);
    }

    [Fact]
    public void GetSystemPrompt_SingleLanguage_ContainsRequiredFields()
    {
        var prompt = Prompts.GetSystemPrompt(["en"]);
        Assert.Contains("emojis", prompt);
        Assert.Contains("title", prompt);
        Assert.Contains("description", prompt);
        Assert.Contains("tags", prompt);
        Assert.Contains("searchPhrases", prompt);
        Assert.Contains("basedOn", prompt);
    }

    [Fact]
    public void GetSystemPrompt_SingleLanguage_DoesNotContainLocalizations()
    {
        var prompt = Prompts.GetSystemPrompt(["en"]);
        Assert.DoesNotContain("localizations", prompt);
    }

    [Fact]
    public void GetSystemPrompt_NonEnglishSingle_ContainsCorrectLanguage()
    {
        var prompt = Prompts.GetSystemPrompt(["cs"]);
        Assert.Contains("Czech", prompt);
        Assert.Contains("cs", prompt);
        Assert.DoesNotContain("English", prompt);
    }

    [Fact]
    public void GetSystemPrompt_SingleLanguage_ContainsJsonExample()
    {
        var prompt = Prompts.GetSystemPrompt(["en"]);
        Assert.Contains("JSON", prompt);
        Assert.Contains("valid JSON", prompt);
    }

    #endregion

    #region Multilingual Prompt

    [Fact]
    public void GetSystemPrompt_TwoLanguages_ContainsBoth()
    {
        var prompt = Prompts.GetSystemPrompt(["en", "cs"]);
        Assert.Contains("English", prompt);
        Assert.Contains("Czech", prompt);
    }

    [Fact]
    public void GetSystemPrompt_TwoLanguages_ContainsLocalizationsField()
    {
        var prompt = Prompts.GetSystemPrompt(["en", "cs"]);
        Assert.Contains("localizations", prompt);
    }

    [Fact]
    public void GetSystemPrompt_ThreeLanguages_ListsAllAdditional()
    {
        var prompt = Prompts.GetSystemPrompt(["en", "cs", "de"]);
        Assert.Contains("English", prompt);
        Assert.Contains("Czech", prompt);
        Assert.Contains("German", prompt);
        Assert.Contains("PRIMARY LANGUAGE", prompt);
        Assert.Contains("ADDITIONAL LANGUAGES", prompt);
    }

    [Fact]
    public void GetSystemPrompt_Multilingual_PrimaryIsFirst()
    {
        var prompt = Prompts.GetSystemPrompt(["de", "en", "cs"]);
        Assert.Contains("PRIMARY LANGUAGE: German (de)", prompt);
        Assert.Contains("English (en)", prompt);
        Assert.Contains("Czech (cs)", prompt);
    }

    [Fact]
    public void GetSystemPrompt_Multilingual_ContainsRequiredFields()
    {
        var prompt = Prompts.GetSystemPrompt(["en", "cs"]);
        Assert.Contains("emojis", prompt);
        Assert.Contains("title", prompt);
        Assert.Contains("description", prompt);
        Assert.Contains("tags", prompt);
        Assert.Contains("searchPhrases", prompt);
        Assert.Contains("basedOn", prompt);
        Assert.Contains("localizations", prompt);
    }

    #endregion

    #region Edge Cases

    [Fact]
    public void GetSystemPrompt_EmptyLanguageList_DefaultsToEnglish()
    {
        var prompt = Prompts.GetSystemPrompt([]);
        Assert.Contains("English", prompt);
    }

    [Fact]
    public void GetSystemPrompt_UnknownLanguage_UsesCodeAsName()
    {
        var prompt = Prompts.GetSystemPrompt(["xx"]);
        // Unknown code "xx" should be used as-is since GetLanguageName returns it
        Assert.Contains("xx", prompt);
    }

    [Fact]
    public void GetSystemPrompt_ManyLanguages()
    {
        var prompt = Prompts.GetSystemPrompt(["en", "cs", "de", "es", "fr", "it", "ja"]);
        Assert.Contains("English", prompt);
        Assert.Contains("Czech", prompt);
        Assert.Contains("German", prompt);
        Assert.Contains("Spanish", prompt);
        Assert.Contains("French", prompt);
        Assert.Contains("Italian", prompt);
        Assert.Contains("Japanese", prompt);
    }

    [Fact]
    public void GetSystemPrompt_ChineseTraditional_HandlesHyphenCode()
    {
        var prompt = Prompts.GetSystemPrompt(["en", "zh-TW"]);
        Assert.Contains("Chinese (Traditional)", prompt);
        Assert.Contains("zh-TW", prompt);
    }

    #endregion
}
