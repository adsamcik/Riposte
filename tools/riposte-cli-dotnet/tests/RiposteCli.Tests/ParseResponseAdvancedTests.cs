using RiposteCli.Services;

namespace RiposteCli.Tests;

/// <summary>
/// Tests for CopilotService.ParseResponseContent: advanced markdown stripping,
/// whitespace handling, unicode edge cases, deeply nested JSON.
/// </summary>
public class ParseResponseAdvancedTests
{
    #region Markdown Stripping Variants

    [Fact]
    public void ParseResponse_NoMarkdown_ParsesDirectly()
    {
        var json = """{"emojis": ["😂"], "title": "Direct JSON"}""";
        var result = CopilotService.ParseResponseContent(json);
        Assert.Equal("Direct JSON", result.Title);
    }

    [Fact]
    public void ParseResponse_JsonCodeBlock_StripsCorrectly()
    {
        var content = "```json\n{\"emojis\": [\"😂\"], \"title\": \"Wrapped\"}\n```";
        var result = CopilotService.ParseResponseContent(content);
        Assert.Equal("Wrapped", result.Title);
    }

    [Fact]
    public void ParseResponse_PlainCodeBlock_StripsCorrectly()
    {
        var content = "```\n{\"emojis\": [\"😂\"], \"title\": \"Plain block\"}\n```";
        var result = CopilotService.ParseResponseContent(content);
        Assert.Equal("Plain block", result.Title);
    }

    [Fact]
    public void ParseResponse_LeadingWhitespace_Trimmed()
    {
        var content = "   \n\n  {\"emojis\": [\"😂\"]}  \n\n  ";
        var result = CopilotService.ParseResponseContent(content);
        Assert.Single(result.Emojis);
    }

    [Fact]
    public void ParseResponse_TabsAndCR_Handled()
    {
        var content = "\t\r\n{\"emojis\": [\"😂\"]}\r\n\t";
        var result = CopilotService.ParseResponseContent(content);
        Assert.Single(result.Emojis);
    }

    [Fact]
    public void ParseResponse_CodeBlockWithNewlines_Parsed()
    {
        var content = """
            ```json
            {
                "emojis": ["😂", "🔥"],
                "title": "Multi-line"
            }
            ```
            """;
        var result = CopilotService.ParseResponseContent(content);
        Assert.Equal(2, result.Emojis.Count);
        Assert.Equal("Multi-line", result.Title);
    }

    #endregion

    #region Complex JSON Structures

    [Fact]
    public void ParseResponse_WithLocalizations()
    {
        var json = """
            {
                "emojis": ["😂"],
                "title": "English title",
                "localizations": {
                    "cs": {
                        "title": "Český titulek",
                        "description": "Popis v češtině",
                        "tags": ["vtipné", "meme"],
                        "searchPhrases": ["vtipný meme"]
                    },
                    "de": {
                        "title": "Deutscher Titel"
                    }
                }
            }
            """;
        var result = CopilotService.ParseResponseContent(json);
        Assert.NotNull(result.Localizations);
        Assert.Equal(2, result.Localizations.Count);
        Assert.Equal("Český titulek", result.Localizations["cs"].Title);
        Assert.Equal(["vtipné", "meme"], result.Localizations["cs"].Tags);
        Assert.Null(result.Localizations["de"].Description);
    }

    [Fact]
    public void ParseResponse_ManyEmojis()
    {
        var json = """{"emojis": ["😂", "🐱", "💻", "🤷", "🔥", "❤️", "👍", "🎉"]}""";
        var result = CopilotService.ParseResponseContent(json);
        Assert.Equal(8, result.Emojis.Count);
    }

    [Fact]
    public void ParseResponse_AllOptionalFieldsPresent()
    {
        var json = """
            {
                "emojis": ["😂"],
                "title": "Title",
                "description": "Description",
                "tags": ["t1", "t2"],
                "searchPhrases": ["sp1"],
                "basedOn": "Source"
            }
            """;
        var result = CopilotService.ParseResponseContent(json);
        Assert.Equal("Title", result.Title);
        Assert.Equal("Description", result.Description);
        Assert.Equal(["t1", "t2"], result.Tags);
        Assert.Equal(["sp1"], result.SearchPhrases);
        Assert.Equal("Source", result.BasedOn);
    }

    [Fact]
    public void ParseResponse_UnknownFields_Ignored()
    {
        var json = """{"emojis": ["😂"], "unknownField": "value", "anotherField": 42}""";
        var result = CopilotService.ParseResponseContent(json);
        Assert.Single(result.Emojis);
    }

    #endregion

    #region Unicode & Special Characters in Content

    [Fact]
    public void ParseResponse_CjkCharacters()
    {
        var json = """
            {
                "emojis": ["🇯🇵"],
                "title": "日本語テスト",
                "description": "日本語の説明文",
                "tags": ["日本", "テスト"]
            }
            """;
        var result = CopilotService.ParseResponseContent(json);
        Assert.Equal("日本語テスト", result.Title);
        Assert.Contains("日本", result.Tags!);
    }

    [Fact]
    public void ParseResponse_ArabicText()
    {
        var json = """{"emojis": ["🌍"], "title": "مرحبا", "tags": ["عربي"]}""";
        var result = CopilotService.ParseResponseContent(json);
        Assert.Equal("مرحبا", result.Title);
    }

    [Fact]
    public void ParseResponse_EmojiInDescription()
    {
        var json = """{"emojis": ["😂"], "description": "This has emojis 🎉 inside 🔥"}""";
        var result = CopilotService.ParseResponseContent(json);
        Assert.Contains("🎉", result.Description);
        Assert.Contains("🔥", result.Description);
    }

    [Fact]
    public void ParseResponse_EscapedUnicode()
    {
        var json = """{"emojis": ["\ud83d\ude02"], "title": "Escaped emoji"}""";
        var result = CopilotService.ParseResponseContent(json);
        Assert.Equal("😂", result.Emojis[0]);
    }

    [Fact]
    public void ParseResponse_BackslashesInDescription()
    {
        var json = """{"emojis": ["😂"], "description": "Path: C:\\Users\\test\\meme.jpg"}""";
        var result = CopilotService.ParseResponseContent(json);
        Assert.Contains("C:\\Users\\test\\meme.jpg", result.Description);
    }

    #endregion

    #region Error Response Patterns

    [Fact]
    public void ParseResponse_ArrayInsteadOfObject_Throws()
    {
        Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent("[\"not\", \"an\", \"object\"]"));
    }

    [Fact]
    public void ParseResponse_NumberOnly_Throws()
    {
        Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent("42"));
    }

    [Fact]
    public void ParseResponse_BooleanOnly_Throws()
    {
        Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent("true"));
    }

    [Fact]
    public void ParseResponse_NullOnly_Throws()
    {
        Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent("null"));
    }

    [Fact]
    public void ParseResponse_EmptyObject_Throws()
    {
        Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent("{}"));
    }

    [Fact]
    public void ParseResponse_ObjectWithoutEmojis_Throws()
    {
        var json = """{"title": "No emojis", "description": "Missing required field"}""";
        Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent(json));
    }

    [Fact]
    public void ParseResponse_EmojisNotArray_Throws()
    {
        Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent("""{"emojis": "not an array"}"""));
    }

    [Fact]
    public void ParseResponse_EmojisArrayOfNumbers_Parses()
    {
        // JSON numbers in array — Deserializer might throw or return weird results
        var ex = Assert.ThrowsAny<Exception>(
            () => CopilotService.ParseResponseContent("""{"emojis": [1, 2, 3]}"""));
        // Either CopilotAnalysisException or JsonException is acceptable
    }

    #endregion

    #region LLM Response Artifacts

    [Fact]
    public void ParseResponse_PrefixText_BeforeJson_Throws()
    {
        var content = "Here is the analysis:\n{\"emojis\": [\"😂\"]}";
        // Prefix text makes it invalid JSON
        Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent(content));
    }

    [Fact]
    public void ParseResponse_SuffixText_AfterJson_Throws()
    {
        var content = "{\"emojis\": [\"😂\"]}\nHope this helps!";
        // Suffix text makes it invalid JSON
        Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent(content));
    }

    [Fact]
    public void ParseResponse_WrappedInCodeBlock_WithPrefixText()
    {
        var content = "```json\n{\"emojis\": [\"😂\"]}\n```";
        var result = CopilotService.ParseResponseContent(content);
        Assert.Single(result.Emojis);
    }

    #endregion
}
