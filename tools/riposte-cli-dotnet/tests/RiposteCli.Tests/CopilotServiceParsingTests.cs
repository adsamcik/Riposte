using RiposteCli.Models;
using RiposteCli.Services;

namespace RiposteCli.Tests;

public class CopilotServiceParsingTests
{
    #region ParseResponseContent — Valid JSON

    [Fact]
    public void ParseResponseContent_ValidJsonWithAllFields_ParsesCorrectly()
    {
        var json = """
            {
                "emojis": ["😂", "🐱", "💻"],
                "title": "Cat at Computer",
                "description": "A cat sitting at a desk staring at code on a monitor",
                "tags": ["cat", "programming", "funny"],
                "searchPhrases": ["programmer cat", "cat coding meme"],
                "basedOn": "Programmer humor",
                "emotions": {
                    "primary": "humor",
                    "secondary": ["absurdity", "relatability"],
                    "sentiment": "positive",
                    "intensity": "high",
                    "memeUsage": ["when the code finally compiles", "me debugging at 3am"]
                },
                "localizations": {
                    "cs": {
                        "title": "Kočka u počítače",
                        "description": "Kočka sedí u stolu a zírá na kód",
                        "tags": ["kočka", "programování"],
                        "searchPhrases": ["kočka programátor"]
                    }
                }
            }
            """;

        var result = CopilotService.ParseResponseContent(json);

        Assert.Equal(3, result.Emojis!.Count);
        Assert.Equal("😂", result.Emojis[0]);
        Assert.Equal("🐱", result.Emojis[1]);
        Assert.Equal("💻", result.Emojis[2]);
        Assert.Equal("Cat at Computer", result.Title);
        Assert.Equal("A cat sitting at a desk staring at code on a monitor", result.Description);
        Assert.Equal(new[] { "cat", "programming", "funny" }, result.Tags);
        Assert.Equal(new[] { "programmer cat", "cat coding meme" }, result.SearchPhrases);
        Assert.Equal("Programmer humor", result.BasedOn);
        Assert.NotNull(result.Emotions);
        Assert.Equal("humor", result.Emotions.Primary);
        Assert.Equal("positive", result.Emotions.Sentiment);
        Assert.Equal("high", result.Emotions.Intensity);
        Assert.Equal(new[] { "absurdity", "relatability" }, result.Emotions.Secondary);
        Assert.Equal(new[] { "when the code finally compiles", "me debugging at 3am" }, result.Emotions.MemeUsage);
        Assert.NotNull(result.Localizations);
        Assert.True(result.Localizations.ContainsKey("cs"));
        Assert.Equal("Kočka u počítače", result.Localizations["cs"].Title);
    }

    [Fact]
    public void ParseResponseContent_JsonWrappedInJsonCodeBlock_IsUnwrapped()
    {
        var content = "```json\n{\"emojis\": [\"😂\", \"🔥\"], \"title\": \"Wrapped Test\"}\n```";

        var result = CopilotService.ParseResponseContent(content);

        Assert.Equal(2, result.Emojis!.Count);
        Assert.Equal("Wrapped Test", result.Title);
    }

    [Fact]
    public void ParseResponseContent_JsonWrappedInGenericCodeBlock_IsUnwrapped()
    {
        var content = "```\n{\"emojis\": [\"🤣\"], \"title\": \"Generic Block\"}\n```";

        var result = CopilotService.ParseResponseContent(content);

        Assert.Single(result.Emojis!);
        Assert.Equal("Generic Block", result.Title);
    }

    [Fact]
    public void ParseResponseContent_ExtraWhitespaceAndNewlines_HandledCorrectly()
    {
        var content = """
            
            
                {
                    "emojis": ["🎉"],
                    "title": "Whitespace Test"
                }
            
            
            """;

        var result = CopilotService.ParseResponseContent(content);

        Assert.Single(result.Emojis!);
        Assert.Equal("🎉", result.Emojis[0]);
        Assert.Equal("Whitespace Test", result.Title);
    }

    [Fact]
    public void ParseResponseContent_OptionalFieldsNull_DoesNotThrow()
    {
        var json = """{"emojis": ["😎"]}""";

        var result = CopilotService.ParseResponseContent(json);

        Assert.Single(result.Emojis!);
        Assert.Null(result.Title);
        Assert.Null(result.Description);
        Assert.Null(result.Tags);
        Assert.Null(result.SearchPhrases);
        Assert.Null(result.BasedOn);
        Assert.Null(result.Emotions);
        Assert.Null(result.Localizations);
    }

    [Fact]
    public void ParseResponseContent_LocalizationsParsedCorrectly()
    {
        var json = """
            {
                "emojis": ["🌍"],
                "title": "Global Meme",
                "localizations": {
                    "cs": {
                        "title": "Český meme",
                        "description": "Popis memu",
                        "tags": ["vtipné", "globální"],
                        "searchPhrases": ["světový meme"]
                    },
                    "de": {
                        "title": "Deutsches Meme",
                        "description": "Meme Beschreibung",
                        "tags": ["lustig"],
                        "searchPhrases": ["globales Meme"]
                    }
                }
            }
            """;

        var result = CopilotService.ParseResponseContent(json);

        Assert.NotNull(result.Localizations);
        Assert.Equal(2, result.Localizations.Count);

        var cs = result.Localizations["cs"];
        Assert.Equal("Český meme", cs.Title);
        Assert.Equal("Popis memu", cs.Description);
        Assert.Equal(new[] { "vtipné", "globální" }, cs.Tags);
        Assert.Equal(new[] { "světový meme" }, cs.SearchPhrases);

        var de = result.Localizations["de"];
        Assert.Equal("Deutsches Meme", de.Title);
        Assert.Equal("Meme Beschreibung", de.Description);
    }

    #endregion

    #region ParseResponseContent — Error Cases

    [Fact]
    public void ParseResponseContent_MissingEmojis_ThrowsCopilotAnalysisException()
    {
        var json = """{"title": "No emojis", "tags": ["test"]}""";

        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent(json));

        Assert.Contains("emojis", ex.Message);
    }

    [Fact]
    public void ParseResponseContent_EmptyEmojisArray_ThrowsCopilotAnalysisException()
    {
        var json = """{"emojis": [], "title": "Empty emojis"}""";

        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent(json));

        Assert.Contains("emojis", ex.Message);
    }

    [Fact]
    public void ParseResponseContent_NullResponse_ThrowsCopilotAnalysisException()
    {
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent("null"));

        Assert.Contains("emojis", ex.Message);
    }

    [Fact]
    public void ParseResponseContent_InvalidJson_ThrowsCopilotAnalysisException()
    {
        Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent("this is not json {{{ at all"));
    }

    #endregion

    #region ParsePartialResponse — Valid JSON

    [Fact]
    public void ParsePartialResponse_ValidJsonWithoutEmojis_ParsesOk()
    {
        var json = """
            {
                "title": "Distracted Boyfriend",
                "description": "Man looking at another woman while his girlfriend watches in disbelief",
                "tags": ["distracted", "boyfriend", "jealousy"],
                "searchPhrases": ["looking at other things", "distracted by something new"]
            }
            """;

        var result = CopilotService.ParsePartialResponse(json, ["text"]);

        Assert.Null(result.Emojis);
        Assert.Equal("Distracted Boyfriend", result.Title);
        Assert.Equal("Man looking at another woman while his girlfriend watches in disbelief", result.Description);
        Assert.Equal(new[] { "distracted", "boyfriend", "jealousy" }, result.Tags);
        Assert.Equal(new[] { "looking at other things", "distracted by something new" }, result.SearchPhrases);
    }

    [Fact]
    public void ParsePartialResponse_ValidJsonWithOnlyTags_ParsesOk()
    {
        var json = """
            {
                "tags": ["dank", "relatable", "work", "monday"]
            }
            """;

        var result = CopilotService.ParsePartialResponse(json, ["tags"]);

        Assert.Equal(new[] { "dank", "relatable", "work", "monday" }, result.Tags);
        Assert.Null(result.Emojis);
        Assert.Null(result.Title);
        Assert.Null(result.Description);
        Assert.Null(result.Emotions);
    }

    [Fact]
    public void ParsePartialResponse_ValidJsonWithOnlyEmotions_ParsesOk()
    {
        var json = """
            {
                "emotions": {
                    "primary": "nostalgia",
                    "secondary": ["warmth", "humor"],
                    "sentiment": "positive",
                    "intensity": "medium",
                    "memeUsage": ["when you miss the good old days", "throwback vibes"]
                }
            }
            """;

        var result = CopilotService.ParsePartialResponse(json, ["emotions"]);

        Assert.NotNull(result.Emotions);
        Assert.Equal("nostalgia", result.Emotions.Primary);
        Assert.Equal(new[] { "warmth", "humor" }, result.Emotions.Secondary);
        Assert.Equal("positive", result.Emotions.Sentiment);
        Assert.Equal("medium", result.Emotions.Intensity);
        Assert.Equal(new[] { "when you miss the good old days", "throwback vibes" }, result.Emotions.MemeUsage);
        Assert.Null(result.Emojis);
        Assert.Null(result.Tags);
    }

    [Fact]
    public void ParsePartialResponse_InvalidJson_ThrowsCopilotAnalysisException()
    {
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParsePartialResponse("not valid json {{", ["tags"]));

        Assert.Contains("Failed to parse partial API response", ex.Message);
    }

    [Fact]
    public void ParsePartialResponse_NullResult_ThrowsCopilotAnalysisException()
    {
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParsePartialResponse("null", ["tags"]));

        Assert.Contains("null", ex.Message, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void ParsePartialResponse_CodeBlockWrapping_IsHandled()
    {
        var content = "```json\n{\"tags\": [\"funny\", \"cat\"], \"title\": \"Wrapped Partial\"}\n```";

        var result = CopilotService.ParsePartialResponse(content, ["tags", "text"]);

        Assert.Equal(new[] { "funny", "cat" }, result.Tags);
        Assert.Equal("Wrapped Partial", result.Title);
    }

    #endregion
}
