namespace RiposteCli.Tests;

public class PromptsTests
{
    #region GetLanguageName

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
    public void GetLanguageName_KnownCode_ReturnsLocalizedName(string code, string expected)
    {
        Assert.Equal(expected, Prompts.GetLanguageName(code));
    }

    [Theory]
    [InlineData("xx")]
    [InlineData("tlh")]
    [InlineData("")]
    public void GetLanguageName_UnknownCode_ReturnsCodeItself(string code)
    {
        Assert.Equal(code, Prompts.GetLanguageName(code));
    }

    #endregion

    #region GetSystemPrompt — Single Language

    [Fact]
    public void GetSystemPrompt_SingleLanguage_ContainsPrimaryLanguage()
    {
        var prompt = Prompts.GetSystemPrompt(["en"]);

        Assert.Contains("English", prompt);
        Assert.Contains("(en)", prompt);
        Assert.DoesNotContain("ADDITIONAL LANGUAGES", prompt);
        Assert.DoesNotContain("localizations", prompt);
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
    public void GetSystemPrompt_SingleLanguage_RequestsJsonOnly()
    {
        var prompt = Prompts.GetSystemPrompt(["en"]);

        Assert.Contains("Respond ONLY with valid JSON", prompt);
    }

    [Fact]
    public void GetSystemPrompt_NonEnglishSingleLanguage_UsesCorrectLanguage()
    {
        var prompt = Prompts.GetSystemPrompt(["cs"]);

        Assert.Contains("Czech", prompt);
        Assert.Contains("(cs)", prompt);
    }

    [Fact]
    public void GetSystemPrompt_EmptyList_DefaultsToEnglish()
    {
        var prompt = Prompts.GetSystemPrompt([]);

        Assert.Contains("English", prompt);
    }

    #endregion

    #region GetSystemPrompt — Multilingual

    [Fact]
    public void GetSystemPrompt_MultiLanguage_ContainsPrimaryAndAdditional()
    {
        var prompt = Prompts.GetSystemPrompt(["en", "cs", "de"]);

        Assert.Contains("PRIMARY LANGUAGE: English (en)", prompt);
        Assert.Contains("ADDITIONAL LANGUAGES:", prompt);
        Assert.Contains("Czech (cs)", prompt);
        Assert.Contains("German (de)", prompt);
    }

    [Fact]
    public void GetSystemPrompt_MultiLanguage_ContainsLocalizationsField()
    {
        var prompt = Prompts.GetSystemPrompt(["en", "cs"]);

        Assert.Contains("localizations", prompt);
    }

    [Fact]
    public void GetSystemPrompt_MultiLanguage_StillContainsAllBaseFields()
    {
        var prompt = Prompts.GetSystemPrompt(["en", "fr"]);

        Assert.Contains("emojis", prompt);
        Assert.Contains("title", prompt);
        Assert.Contains("description", prompt);
        Assert.Contains("tags", prompt);
        Assert.Contains("searchPhrases", prompt);
        Assert.Contains("basedOn", prompt);
    }

    [Fact]
    public void GetSystemPrompt_TwoLanguages_UsesMultilingualPrompt()
    {
        var prompt = Prompts.GetSystemPrompt(["en", "ja"]);

        Assert.Contains("multilingual", prompt);
        Assert.Contains("Japanese (ja)", prompt);
    }

    [Fact]
    public void GetSystemPrompt_UnknownAdditionalLanguage_UsesCodeAsName()
    {
        var prompt = Prompts.GetSystemPrompt(["en", "xx"]);

        Assert.Contains("xx (xx)", prompt);
    }

    #endregion
}
