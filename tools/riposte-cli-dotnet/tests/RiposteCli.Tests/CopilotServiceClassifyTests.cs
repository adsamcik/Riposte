using System.Reflection;
using RiposteCli.RateLimiting;
using RiposteCli.Services;

namespace RiposteCli.Tests;

public class CopilotServiceClassifyTests
{
    private static readonly MethodInfo? ClassifyAndThrowMethod =
        typeof(CopilotService).GetMethod("ClassifyAndThrow", BindingFlags.NonPublic | BindingFlags.Static);

    [Theory]
    [InlineData("rate limit exceeded")]
    [InlineData("RATE LIMIT EXCEEDED")]
    [InlineData("Rate Limit Exceeded")]
    [InlineData("HTTP 429 Too Many Requests")]
    public void ClassifyAndThrow_RateLimitPatterns_ThrowsRateLimitException(string message)
    {
        var ex = InvokeClassifyAndThrow<RateLimitException>(message);

        Assert.Contains("Rate limit exceeded", ex.Message);
        Assert.NotNull(ex.RetryAfter);
    }

    [Theory]
    [InlineData("HTTP 500 Internal Server Error", 500)]
    [InlineData("HTTP 502 Bad Gateway", 502)]
    [InlineData("HTTP 503 Service Unavailable", 503)]
    [InlineData("HTTP 504 Gateway Timeout", 504)]
    public void ClassifyAndThrow_ServerStatusCodePatterns_ThrowsServerErrorException(string message, int expectedCode)
    {
        var ex = InvokeClassifyAndThrow<ServerErrorException>(message);

        Assert.Equal(expectedCode, ex.StatusCode);
        Assert.Contains(expectedCode.ToString(), ex.Message);
    }

    [Theory]
    [InlineData("internal server error")]
    [InlineData("bad gateway response")]
    [InlineData("gateway timeout from upstream")]
    [InlineData("service unavailable right now")]
    public void ClassifyAndThrow_ServerTextPatterns_ThrowsServerErrorException(string message)
    {
        var ex = InvokeClassifyAndThrow<ServerErrorException>(message);

        Assert.Equal(503, ex.StatusCode);
    }

    [Fact]
    public void ClassifyAndThrow_429And500InMessage_PrioritizesRateLimit()
    {
        var ex = InvokeClassifyAndThrow<RateLimitException>("HTTP 429 and HTTP 500 returned by upstream");

        Assert.NotNull(ex.RetryAfter);
    }

    [Fact]
    public void ClassifyAndThrow_GenericMessage_ThrowsCopilotAnalysisException()
    {
        var ex = InvokeClassifyAndThrow<CopilotAnalysisException>("connection reset by peer");

        Assert.Equal("Copilot error: connection reset by peer", ex.Message);
    }

    [Fact]
    public void ClassifyAndThrow_EmptyMessage_ThrowsCopilotAnalysisException()
    {
        var ex = InvokeClassifyAndThrow<CopilotAnalysisException>(string.Empty);

        Assert.Equal("Copilot error: ", ex.Message);
    }

    [Fact]
    public void ParseResponseContent_MinimalWithOnlyEmojis_Parses()
    {
        var result = CopilotService.ParseResponseContent("""{"emojis":["😂"]}""");
        var emojis = Assert.IsType<List<string>>(result.Emojis);

        Assert.Single(emojis);
        Assert.Equal("😂", emojis[0]);
        Assert.Null(result.Title);
        Assert.Null(result.Tags);
    }

    [Fact]
    public void ParseResponseContent_EmojisAndEmotionsOnly_Parses()
    {
        var json = """
            {
                "emojis": ["🔥"],
                "emotions": {
                    "primary": "hype",
                    "sentiment": "positive",
                    "intensity": "high"
                }
            }
            """;

        var result = CopilotService.ParseResponseContent(json);

        Assert.Single(result.Emojis!);
        Assert.NotNull(result.Emotions);
        Assert.Equal("hype", result.Emotions.Primary);
        Assert.Equal("positive", result.Emotions.Sentiment);
        Assert.Null(result.Title);
        Assert.Null(result.Tags);
    }

    [Fact]
    public void ParseResponseContent_DeepLocalizationWithMultipleLanguages_Parses()
    {
        var json = """
            {
                "emojis": ["🌍"],
                "localizations": {
                    "en": {
                        "title": "Global meme",
                        "searchPhrases": ["world meme"]
                    },
                    "cs": {
                        "title": "Globální meme",
                        "description": "Meme pro celý svět"
                    },
                    "ja": {
                        "title": "世界のミーム",
                        "tags": ["ミーム", "面白い"]
                    },
                    "es": {
                        "title": "Meme global",
                        "searchPhrases": ["meme del mundo"]
                    }
                }
            }
            """;

        var result = CopilotService.ParseResponseContent(json);

        Assert.NotNull(result.Localizations);
        Assert.True(result.Localizations.Count >= 3);
        Assert.Equal("Global meme", result.Localizations["en"].Title);
        Assert.Equal("Globální meme", result.Localizations["cs"].Title);
        Assert.Equal("世界のミーム", result.Localizations["ja"].Title);
    }

    [Fact]
    public void ParseResponseContent_CompoundEmojis_Parses()
    {
        var result = CopilotService.ParseResponseContent("""{"emojis":["🏳️‍🌈","👨‍👩‍👧‍👦","👋🏽"]}""");

        Assert.Equal(3, result.Emojis!.Count);
        Assert.Equal("🏳️‍🌈", result.Emojis[0]);
        Assert.Equal("👨‍👩‍👧‍👦", result.Emojis[1]);
        Assert.Equal("👋🏽", result.Emojis[2]);
    }

    // --- Null rateLimiter fallback behavior ---

    [Theory]
    [InlineData("rate limit exceeded")]
    [InlineData("HTTP 429 Too Many Requests")]
    public void ClassifyAndThrow_NullRateLimiter_RateLimit_RetryAfterDefaultsTo5(string message)
    {
        var ex = InvokeClassifyAndThrow<RateLimitException>(message, rateLimiter: null);

        Assert.Equal(5.0, ex.RetryAfter);
    }

    [Fact]
    public void ClassifyAndThrow_NullRateLimiter_ServerError_MessageContainsDefault()
    {
        var ex = InvokeClassifyAndThrow<ServerErrorException>("HTTP 500 Internal Server Error", rateLimiter: null);

        Assert.Equal(500, ex.StatusCode);
        Assert.Contains("5.0s", ex.Message);
    }

    // --- RateLimiter interaction tests ---

    [Fact]
    public void ClassifyAndThrow_WithRateLimiter_RateLimit_CallsRecordFailure()
    {
        var rl = new RateLimiter();
        Assert.Equal(0, rl.ConsecutiveFailures);

        var ex = InvokeClassifyAndThrow<RateLimitException>("rate limit exceeded", rl);

        Assert.Equal(1, rl.ConsecutiveFailures);
        Assert.NotNull(ex.RetryAfter);
        Assert.True(ex.RetryAfter > 0);
    }

    [Fact]
    public void ClassifyAndThrow_WithRateLimiter_ServerStatusCode_CallsRecordFailure()
    {
        var rl = new RateLimiter();

        var ex = InvokeClassifyAndThrow<ServerErrorException>("HTTP 503 Service Unavailable", rl);

        Assert.Equal(1, rl.ConsecutiveFailures);
        Assert.Equal(503, ex.StatusCode);
    }

    [Fact]
    public void ClassifyAndThrow_WithRateLimiter_ServerTextPattern_CallsRecordFailure()
    {
        var rl = new RateLimiter();

        var ex = InvokeClassifyAndThrow<ServerErrorException>("internal server error", rl);

        Assert.Equal(1, rl.ConsecutiveFailures);
        Assert.Equal(503, ex.StatusCode);
    }

    [Fact]
    public void ClassifyAndThrow_WithRateLimiter_RetryAfterReflectsRateLimiterValue()
    {
        var rl = new RateLimiter();

        var ex = InvokeClassifyAndThrow<RateLimitException>("rate limit exceeded", rl);

        // RateLimiter first failure with default params: baseWait=2^1=2.0 ± jitter
        Assert.NotNull(ex.RetryAfter);
        Assert.NotEqual(5.0, ex.RetryAfter); // Not the null-rateLimiter default
        Assert.InRange(ex.RetryAfter!.Value, 0.1, 300.0);
    }

    // --- Regex edge cases ---

    [Fact]
    public void ClassifyAndThrow_Standalone429_ThrowsRateLimitException()
    {
        var ex = InvokeClassifyAndThrow<RateLimitException>("429");

        Assert.NotNull(ex.RetryAfter);
    }

    [Fact]
    public void ClassifyAndThrow_429WithPunctuation_ThrowsRateLimitException()
    {
        var ex = InvokeClassifyAndThrow<RateLimitException>("status code: 429.");

        Assert.NotNull(ex.RetryAfter);
    }

    [Fact]
    public void ClassifyAndThrow_429EmbeddedInWord_DoesNotMatchRateLimit()
    {
        // \b429\b requires word boundaries — digits inside a word should not match
        var ex = InvokeClassifyAndThrow<CopilotAnalysisException>("error429code");

        Assert.Contains("error429code", ex.Message);
    }

    [Fact]
    public void ClassifyAndThrow_429AdjacentToLetters_DoesNotMatchRateLimit()
    {
        // "http429" has no word boundary between 'p' and '4'
        var ex = InvokeClassifyAndThrow<CopilotAnalysisException>("http429");

        Assert.Contains("http429", ex.Message);
    }

    // --- Helpers ---

    private static TException InvokeClassifyAndThrow<TException>(string message) where TException : Exception
        => InvokeClassifyAndThrow<TException>(message, rateLimiter: null);

    private static TException InvokeClassifyAndThrow<TException>(string message, RateLimiter? rateLimiter)
        where TException : Exception
    {
        Assert.NotNull(ClassifyAndThrowMethod);

        var invocationException = Assert.Throws<TargetInvocationException>(
            () => ClassifyAndThrowMethod!.Invoke(null, [new Exception(message), rateLimiter]));

        Assert.NotNull(invocationException.InnerException);
        return Assert.IsType<TException>(invocationException.InnerException);
    }
}
