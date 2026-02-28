using System.Text.Json;
using RiposteCli.Models;

namespace RiposteCli.Tests;

public class ValueConstraintTests
{
    [Theory]
    [InlineData("positive")]
    [InlineData("negative")]
    [InlineData("neutral")]
    [InlineData("mixed")]
    public void ParseResponseContent_ValidSentimentValues_ParseSuccessfully(string sentiment)
    {
        var result = ParseWithEmotions(new EmotionMetadata
        {
            Primary = "humor",
            Sentiment = sentiment,
            Intensity = "medium",
        });

        Assert.Equal(sentiment, result.Emotions!.Sentiment);
    }

    [Fact]
    public void ParseResponseContent_InvalidSentiment_ParsesWithoutValidation()
    {
        var result = ParseWithEmotions(new EmotionMetadata
        {
            Primary = "humor",
            Sentiment = "happy",
            Intensity = "medium",
        });

        Assert.Equal("happy", result.Emotions!.Sentiment);
    }

    [Fact]
    public void ParseResponseContent_EmptySentiment_ParsesSuccessfully()
    {
        var result = ParseWithEmotions(new EmotionMetadata
        {
            Primary = "humor",
            Sentiment = "",
            Intensity = "medium",
        });

        Assert.Equal(string.Empty, result.Emotions!.Sentiment);
    }

    [Theory]
    [InlineData("low")]
    [InlineData("medium")]
    [InlineData("high")]
    public void ParseResponseContent_ValidIntensityValues_ParseSuccessfully(string intensity)
    {
        var result = ParseWithEmotions(new EmotionMetadata
        {
            Primary = "humor",
            Sentiment = "positive",
            Intensity = intensity,
        });

        Assert.Equal(intensity, result.Emotions!.Intensity);
    }

    [Fact]
    public void ParseResponseContent_InvalidIntensity_ParsesWithoutValidation()
    {
        var result = ParseWithEmotions(new EmotionMetadata
        {
            Primary = "humor",
            Sentiment = "positive",
            Intensity = "extreme",
        });

        Assert.Equal("extreme", result.Emotions!.Intensity);
    }

    [Fact]
    public void ParseResponseContent_NullSecondaryEmotions_IsAllowed()
    {
        var result = ParseWithJson(
            JsonSerializer.Serialize(new
            {
                emojis = new[] { "😂" },
                emotions = new
                {
                    primary = "humor",
                    secondary = (string[]?)null,
                    sentiment = "positive",
                    intensity = "medium",
                },
            }));

        Assert.Null(result.Emotions!.Secondary);
    }

    [Fact]
    public void ParseResponseContent_EmptySecondaryEmotions_IsAllowed()
    {
        var result = ParseWithEmotions(new EmotionMetadata
        {
            Primary = "humor",
            Secondary = [],
            Sentiment = "positive",
            Intensity = "medium",
        });

        Assert.Empty(result.Emotions!.Secondary!);
    }

    [Fact]
    public void ParseResponseContent_NullMemeUsage_IsAllowed()
    {
        var result = ParseWithEmotions(new EmotionMetadata
        {
            Primary = "humor",
            Sentiment = "positive",
            Intensity = "medium",
            MemeUsage = null,
        });

        Assert.Null(result.Emotions!.MemeUsage);
    }

    [Fact]
    public void ParseResponseContent_EmptyMemeUsage_IsAllowed()
    {
        var result = ParseWithEmotions(new EmotionMetadata
        {
            Primary = "humor",
            Sentiment = "positive",
            Intensity = "medium",
            MemeUsage = [],
        });

        Assert.Empty(result.Emotions!.MemeUsage!);
    }

    [Fact]
    public void ParseResponseContent_VeryLongPrimaryEmotion_ParsesSuccessfully()
    {
        var longPrimary = new string('a', 1000);
        var result = ParseWithEmotions(new EmotionMetadata
        {
            Primary = longPrimary,
            Sentiment = "positive",
            Intensity = "medium",
        });

        Assert.Equal(longPrimary, result.Emotions!.Primary);
    }

    [Fact]
    public void ParseResponseContent_SingleEmoji_IsValid()
    {
        var result = ParseWithJson("""{"emojis":["😂"]}""");
        Assert.Equal(["😂"], result.Emojis);
    }

    [Fact]
    public void ParseResponseContent_MaximumEightEmojis_IsValid()
    {
        var result = ParseWithJson(
            """{"emojis":["😂","🔥","💯","🎉","😎","🤖","🚀","✨"]}""");

        Assert.Equal(8, result.Emojis!.Count);
    }

    [Fact]
    public void ParseResponseContent_MoreThanEightEmojis_ParsesWithoutValidation()
    {
        var emojis = Enumerable.Range(1, 20).Select(i => $"e{i}").ToArray();
        var result = ParseWithJson(JsonSerializer.Serialize(new { emojis }));

        Assert.Equal(20, result.Emojis!.Count);
    }

    [Fact]
    public void ParseResponseContent_EmptyEmojiList_Throws()
    {
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => ParseWithJson("""{"emojis":[]}"""));

        Assert.Contains("emojis", ex.Message);
    }

    [Fact]
    public void ParseResponseContent_EmojiLikeNonEmojiStrings_ParsesWithoutValidation()
    {
        var result = ParseWithJson("""{"emojis":["happy","sad"]}""");
        Assert.Equal(["happy", "sad"], result.Emojis);
    }

    [Fact]
    public void ParseResponseContent_CompoundEmoji_ParsesSuccessfully()
    {
        var result = ParseWithJson("""{"emojis":["🏳️‍🌈"]}""");
        Assert.Equal("🏳️‍🌈", Assert.Single(result.Emojis!));
    }

    [Fact]
    public void ParseResponseContent_TitleLongerThanFiftyChars_ParsesWithoutValidation()
    {
        var longTitle = new string('t', 51);
        var result = ParseWithJson(JsonSerializer.Serialize(new { emojis = new[] { "😂" }, title = longTitle }));

        Assert.Equal(longTitle, result.Title);
    }

    [Fact]
    public void ParseResponseContent_NullTitle_IsAllowed()
    {
        var result = ParseWithJson(
            JsonSerializer.Serialize(new { emojis = new[] { "😂" }, title = (string?)null }));

        Assert.Null(result.Title);
    }

    [Fact]
    public void ParseResponseContent_EmptyTitle_IsAllowed()
    {
        var result = ParseWithJson("""{"emojis":["😂"],"title":""}""");
        Assert.Equal(string.Empty, result.Title);
    }

    [Fact]
    public void ParseResponseContent_DescriptionWithEmbeddedJson_ParsesAsPlainString()
    {
        const string description = """contains {"nested":"json","arr":[1,2]} text""";
        var result = ParseWithJson(
            JsonSerializer.Serialize(new { emojis = new[] { "😂" }, description }));

        Assert.Equal(description, result.Description);
    }

    [Fact]
    public void ParseResponseContent_MoreThanFifteenTags_ParsesWithoutValidation()
    {
        var tags = Enumerable.Range(1, 16).Select(i => $"tag{i}").ToArray();
        var result = ParseWithJson(
            JsonSerializer.Serialize(new { emojis = new[] { "😂" }, tags }));

        Assert.Equal(16, result.Tags!.Count);
    }

    [Fact]
    public void ParseResponseContent_UppercaseTags_AreAllowed()
    {
        var result = ParseWithJson("""{"emojis":["😂"],"tags":["FUNNY","MEME"]}""");
        Assert.Equal(["FUNNY", "MEME"], result.Tags);
    }

    [Fact]
    public void ParseResponseContent_TagsWithSpaces_AreAllowed()
    {
        var result = ParseWithJson("""{"emojis":["😂"],"tags":["very funny","office humor"]}""");
        Assert.Equal(["very funny", "office humor"], result.Tags);
    }

    [Fact]
    public void ParseResponseContent_EmptyTagStringInArray_IsAllowed()
    {
        var result = ParseWithJson("""{"emojis":["😂"],"tags":["","valid"]}""");
        Assert.Equal(["", "valid"], result.Tags);
    }

    [Fact]
    public void Merge_InvalidSentiment_IsPreserved()
    {
        var existing = CreateExisting();
        var partial = new AnalysisResult
        {
            Emotions = new EmotionMetadata
            {
                Primary = "humor",
                Sentiment = "happy",
                Intensity = "medium",
            },
        };

        var merged = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupEmotions]);

        Assert.Equal("happy", merged.Emotions!.Sentiment);
    }

    [Fact]
    public void Merge_ExtraLongTitle_IsPreserved()
    {
        var longTitle = new string('x', 200);
        var existing = CreateExisting();
        var partial = new AnalysisResult { Title = longTitle };

        var merged = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupCore]);

        Assert.Equal(longTitle, merged.Title);
    }

    // --- Required field enforcement (EmotionMetadata.Primary / .Sentiment) ---

    [Fact]
    public void ParseResponseContent_EmotionsMissingPrimary_Throws()
    {
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => ParseWithJson("""{"emojis":["😂"],"emotions":{"sentiment":"positive","intensity":"medium"}}"""));

        Assert.Contains("JSON", ex.Message);
    }

    [Fact]
    public void ParseResponseContent_EmotionsMissingSentiment_Throws()
    {
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => ParseWithJson("""{"emojis":["😂"],"emotions":{"primary":"humor","intensity":"medium"}}"""));

        Assert.Contains("JSON", ex.Message);
    }

    [Fact]
    public void ParseResponseContent_EmotionsMissingBothRequired_Throws()
    {
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => ParseWithJson("""{"emojis":["😂"],"emotions":{"intensity":"medium"}}"""));

        Assert.Contains("JSON", ex.Message);
    }

    // --- Null/absent emotions object on AnalysisResult ---

    [Fact]
    public void ParseResponseContent_NullEmotionsObject_IsAllowed()
    {
        var result = ParseWithJson("""{"emojis":["😂"],"emotions":null}""");
        Assert.Null(result.Emotions);
    }

    [Fact]
    public void ParseResponseContent_AbsentEmotionsObject_IsAllowed()
    {
        var result = ParseWithJson("""{"emojis":["😂"]}""");
        Assert.Null(result.Emotions);
    }

    // --- Valid primary emotion values from prompt spec ---

    [Theory]
    [InlineData("joy")]
    [InlineData("humor")]
    [InlineData("sarcasm")]
    [InlineData("wholesome")]
    [InlineData("sadness")]
    [InlineData("anger")]
    [InlineData("awe")]
    public void ParseResponseContent_ValidPrimaryEmotions_ParseSuccessfully(string primary)
    {
        var result = ParseWithEmotions(new EmotionMetadata
        {
            Primary = primary,
            Sentiment = "positive",
            Intensity = "medium",
        });

        Assert.Equal(primary, result.Emotions!.Primary);
    }

    // --- Intensity default value ---

    [Fact]
    public void ParseResponseContent_IntensityDefaultsToMedium_WhenAbsentFromJson()
    {
        var result = ParseWithJson("""{"emojis":["😂"],"emotions":{"primary":"humor","sentiment":"positive"}}""");
        Assert.Equal("medium", result.Emotions!.Intensity);
    }

    // --- Merge: emotion preservation and replacement ---

    [Fact]
    public void Merge_NullPartialEmotions_PreservesExisting()
    {
        var existing = CreateExisting();
        var partial = new AnalysisResult { Emotions = null };

        var merged = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupEmotions]);

        Assert.NotNull(merged.Emotions);
        Assert.Equal("humor", merged.Emotions!.Primary);
        Assert.Equal("positive", merged.Emotions.Sentiment);
    }

    [Fact]
    public void Merge_EmotionsGroupNotInAffectedGroups_PreservesExisting()
    {
        var existing = CreateExisting();
        var partial = new AnalysisResult
        {
            Emotions = new EmotionMetadata
            {
                Primary = "sadness",
                Sentiment = "negative",
                Intensity = "high",
            },
        };

        var merged = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupCore]);

        Assert.Equal("humor", merged.Emotions!.Primary);
        Assert.Equal("positive", merged.Emotions.Sentiment);
    }

    [Fact]
    public void Merge_ReplacesEmotionsEntirely_WhenGroupIncluded()
    {
        var existing = CreateExisting();
        var partial = new AnalysisResult
        {
            Emotions = new EmotionMetadata
            {
                Primary = "sadness",
                Sentiment = "negative",
                Intensity = "high",
                Secondary = ["anger", "frustration"],
                MemeUsage = ["when everything goes wrong"],
            },
        };

        var merged = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupEmotions]);

        Assert.Equal("sadness", merged.Emotions!.Primary);
        Assert.Equal("negative", merged.Emotions.Sentiment);
        Assert.Equal("high", merged.Emotions.Intensity);
        Assert.Equal(["anger", "frustration"], merged.Emotions.Secondary);
        Assert.Equal(["when everything goes wrong"], merged.Emotions.MemeUsage);
    }

    [Fact]
    public void Merge_PreservesEmotionSubFields_WhenNonEmotionGroupUpdated()
    {
        var existing = new SidecarMetadata
        {
            Emojis = ["😂"],
            Emotions = new EmotionMetadata
            {
                Primary = "humor",
                Sentiment = "positive",
                Intensity = "high",
                Secondary = ["joy", "amusement"],
                MemeUsage = ["when code compiles on first try"],
            },
        };
        var partial = new AnalysisResult { Tags = ["funny", "meme"] };

        var merged = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupSearch]);

        Assert.Equal("high", merged.Emotions!.Intensity);
        Assert.Equal(["joy", "amusement"], merged.Emotions.Secondary);
        Assert.Equal(["when code compiles on first try"], merged.Emotions.MemeUsage);
    }

    // --- ParsePartialResponse: emotion constraints ---

    [Fact]
    public void ParsePartialResponse_EmotionsWithoutEmojis_ParsesSuccessfully()
    {
        var json = JsonSerializer.Serialize(new
        {
            emotions = new EmotionMetadata
            {
                Primary = "humor",
                Sentiment = "positive",
                Intensity = "medium",
            },
        });

        var result = CopilotService.ParsePartialResponse(json, [PromptHasher.GroupEmotions]);

        Assert.Equal("humor", result.Emotions!.Primary);
        Assert.Equal("positive", result.Emotions.Sentiment);
    }

    [Fact]
    public void ParsePartialResponse_EmotionsMissingSentiment_Throws()
    {
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParsePartialResponse(
                """{"emotions":{"primary":"humor","intensity":"medium"}}""",
                [PromptHasher.GroupEmotions]));

        Assert.Contains("JSON", ex.Message);
    }

    [Fact]
    public void ParsePartialResponse_EmotionsMissingPrimary_Throws()
    {
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParsePartialResponse(
                """{"emotions":{"sentiment":"positive","intensity":"medium"}}""",
                [PromptHasher.GroupEmotions]));

        Assert.Contains("JSON", ex.Message);
    }

    private static AnalysisResult ParseWithEmotions(EmotionMetadata emotions) =>
        ParseWithJson(JsonSerializer.Serialize(new
        {
            emojis = new[] { "😂" },
            emotions,
        }));

    private static AnalysisResult ParseWithJson(string json) =>
        CopilotService.ParseResponseContent(json);

    private static SidecarMetadata CreateExisting() => new()
    {
        Emojis = ["😂"],
        Title = "Original title",
        Emotions = new EmotionMetadata
        {
            Primary = "humor",
            Sentiment = "positive",
            Intensity = "medium",
        },
    };
}
