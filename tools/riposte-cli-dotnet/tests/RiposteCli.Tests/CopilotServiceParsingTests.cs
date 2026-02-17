using RiposteCli.Services;

namespace RiposteCli.Tests;

public class CopilotServiceParsingTests
{
    #region ParseResponseContent — Valid JSON Variants

    [Fact]
    public void ParseResponseContent_MinimalValidJson()
    {
        var json = """{"emojis": ["😂"]}""";
        var result = CopilotService.ParseResponseContent(json);

        Assert.Single(result.Emojis);
        Assert.Equal("😂", result.Emojis[0]);
    }

    [Fact]
    public void ParseResponseContent_FullJson_AllFieldsParsed()
    {
        var json = """
            {
                "emojis": ["😂", "🐱", "💻"],
                "title": "Cat at Computer",
                "description": "A cat sitting at a desk staring at code",
                "tags": ["cat", "programming", "funny"],
                "searchPhrases": ["programmer cat", "cat coding"],
                "basedOn": "Programmer humor"
            }
            """;
        var result = CopilotService.ParseResponseContent(json);

        Assert.Equal(3, result.Emojis.Count);
        Assert.Equal("Cat at Computer", result.Title);
        Assert.Equal("A cat sitting at a desk staring at code", result.Description);
        Assert.Equal(["cat", "programming", "funny"], result.Tags);
        Assert.Equal(["programmer cat", "cat coding"], result.SearchPhrases);
        Assert.Equal("Programmer humor", result.BasedOn);
    }

    [Fact]
    public void ParseResponseContent_WithLocalizations()
    {
        var json = """
            {
                "emojis": ["😂"],
                "title": "Funny Meme",
                "localizations": {
                    "cs": {
                        "title": "Vtipný Meme",
                        "description": "Vtipný obrázek",
                        "tags": ["vtipný"],
                        "searchPhrases": ["vtipný meme"]
                    }
                }
            }
            """;
        var result = CopilotService.ParseResponseContent(json);

        Assert.NotNull(result.Localizations);
        Assert.True(result.Localizations.ContainsKey("cs"));
        Assert.Equal("Vtipný Meme", result.Localizations["cs"].Title);
        Assert.Equal("Vtipný obrázek", result.Localizations["cs"].Description);
    }

    [Fact]
    public void ParseResponseContent_WithMultipleLocalizations()
    {
        var json = """
            {
                "emojis": ["🌍"],
                "title": "Global Meme",
                "localizations": {
                    "cs": { "title": "Český" },
                    "de": { "title": "Deutsch" },
                    "fr": { "title": "Français" }
                }
            }
            """;
        var result = CopilotService.ParseResponseContent(json);

        Assert.NotNull(result.Localizations);
        Assert.Equal(3, result.Localizations.Count);
        Assert.Equal("Český", result.Localizations["cs"].Title);
        Assert.Equal("Deutsch", result.Localizations["de"].Title);
        Assert.Equal("Français", result.Localizations["fr"].Title);
    }

    [Fact]
    public void ParseResponseContent_ExtraUnknownFields_Ignored()
    {
        var json = """
            {
                "emojis": ["😂"],
                "unknownField": "value",
                "anotherExtra": 42
            }
            """;
        var result = CopilotService.ParseResponseContent(json);

        Assert.Single(result.Emojis);
    }

    [Fact]
    public void ParseResponseContent_UnicodeEmojis_PreservedCorrectly()
    {
        var json = """{"emojis": ["👨‍💻", "🇨🇿", "🏳️‍🌈", "👁️‍🗨️", "🫠"]}""";
        var result = CopilotService.ParseResponseContent(json);

        Assert.Equal(5, result.Emojis.Count);
        Assert.Equal("👨‍💻", result.Emojis[0]);
        Assert.Equal("🇨🇿", result.Emojis[1]);
    }

    [Fact]
    public void ParseResponseContent_EightEmojis_MaxAllowed()
    {
        var json = """{"emojis": ["😀","😁","😂","🤣","😃","😄","😅","😆"]}""";
        var result = CopilotService.ParseResponseContent(json);
        Assert.Equal(8, result.Emojis.Count);
    }

    #endregion

    #region ParseResponseContent — Markdown Wrapping

    [Fact]
    public void ParseResponseContent_WrappedInJsonCodeBlock()
    {
        var content = """
            ```json
            {"emojis": ["😂"], "title": "Test"}
            ```
            """;
        var result = CopilotService.ParseResponseContent(content);
        Assert.Equal("Test", result.Title);
    }

    [Fact]
    public void ParseResponseContent_WrappedInGenericCodeBlock()
    {
        var content = """
            ```
            {"emojis": ["😂"], "title": "Test"}
            ```
            """;
        var result = CopilotService.ParseResponseContent(content);
        Assert.Equal("Test", result.Title);
    }

    [Fact]
    public void ParseResponseContent_WithLeadingTrailingWhitespace()
    {
        var content = """
            
               {"emojis": ["😂"]}
            
            """;
        var result = CopilotService.ParseResponseContent(content);
        Assert.Single(result.Emojis);
    }

    [Fact]
    public void ParseResponseContent_MultilineJsonInsideCodeBlock()
    {
        var content = """
            ```json
            {
                "emojis": ["😂", "🔥"],
                "title": "Multi-line",
                "description": "A multi-line description\nthat spans lines",
                "tags": ["tag1", "tag2"]
            }
            ```
            """;
        var result = CopilotService.ParseResponseContent(content);
        Assert.Equal(2, result.Emojis.Count);
        Assert.Equal("Multi-line", result.Title);
    }

    #endregion

    #region ParseResponseContent — Error Cases

    [Fact]
    public void ParseResponseContent_EmptyEmojis_Throws()
    {
        var json = """{"emojis": []}""";
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent(json));
        Assert.Contains("emojis", ex.Message);
    }

    [Fact]
    public void ParseResponseContent_MissingEmojisField_Throws()
    {
        var json = """{"title": "No emojis here"}""";
        Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent(json));
    }

    [Fact]
    public void ParseResponseContent_CompleteGarbage_Throws()
    {
        Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent("this is not json at all"));
    }

    [Fact]
    public void ParseResponseContent_EmptyString_Throws()
    {
        Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent(""));
    }

    [Fact]
    public void ParseResponseContent_NullJsonValue_Throws()
    {
        Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent("null"));
    }

    [Fact]
    public void ParseResponseContent_ArrayInsteadOfObject_Throws()
    {
        Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent("""["😂"]"""));
    }

    [Fact]
    public void ParseResponseContent_EmojisAsString_Throws()
    {
        // emojis should be array, not string
        Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent("""{"emojis": "😂"}"""));
    }

    [Fact]
    public void ParseResponseContent_TruncatedJson_Throws()
    {
        Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent("""{"emojis": ["😂"]}extra junk"""));
    }

    #endregion

    #region Error Classification

    [Fact]
    public void ExceptionTypes_RateLimitException_HasRetryAfter()
    {
        var ex = new RateLimitException("rate limited", 5.0);
        Assert.Equal(5.0, ex.RetryAfter);
        Assert.IsAssignableFrom<CopilotAnalysisException>(ex);
    }

    [Fact]
    public void ExceptionTypes_ServerErrorException_HasStatusCode()
    {
        var ex = new ServerErrorException("server error", 503);
        Assert.Equal(503, ex.StatusCode);
        Assert.IsAssignableFrom<CopilotAnalysisException>(ex);
    }

    [Fact]
    public void ExceptionTypes_CopilotNotAuthenticated_InheritsFromAnalysisException()
    {
        var ex = new CopilotNotAuthenticatedException("not logged in");
        Assert.IsAssignableFrom<CopilotAnalysisException>(ex);
    }

    [Fact]
    public void ExceptionTypes_RateLimitException_NullRetryAfter()
    {
        var ex = new RateLimitException("rate limited");
        Assert.Null(ex.RetryAfter);
    }

    [Fact]
    public void ExceptionTypes_ServerErrorException_NullStatusCode()
    {
        var ex = new ServerErrorException("server error");
        Assert.Null(ex.StatusCode);
    }

    #endregion
}
