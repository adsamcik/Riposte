using RiposteCli.RateLimiting;
using RiposteCli.Services;

namespace RiposteCli.Tests;

/// <summary>
/// Tests for exception types: RateLimitException, ServerErrorException,
/// CopilotNotAuthenticatedException properties and behavior.
/// Also tests error classification patterns matching what ClassifyAndThrow does.
/// </summary>
public class ExceptionClassificationTests
{
    #region RateLimitException Properties

    [Theory]
    [InlineData(0.5)]
    [InlineData(5.0)]
    [InlineData(30.0)]
    [InlineData(60.0)]
    public void RateLimitException_RetryAfter_PreservesValue(double retryAfter)
    {
        var ex = new RateLimitException("Rate limit exceeded", retryAfter);
        Assert.Equal(retryAfter, ex.RetryAfter);
    }

    [Fact]
    public void RateLimitException_DefaultRetryAfter_IsNull()
    {
        var ex = new RateLimitException("Rate limited");
        Assert.Null(ex.RetryAfter);
    }

    [Fact]
    public void RateLimitException_MessageContainsContext()
    {
        var ex = new RateLimitException("Rate limit exceeded. Retry after 5.0s", 5.0);
        Assert.Contains("Rate limit", ex.Message);
        Assert.Contains("5.0", ex.Message);
    }

    #endregion

    #region ServerErrorException Properties

    [Theory]
    [InlineData(500, "Internal Server Error")]
    [InlineData(502, "Bad Gateway")]
    [InlineData(503, "Service Unavailable")]
    [InlineData(504, "Gateway Timeout")]
    public void ServerErrorException_StatusCode_PreservesValue(int statusCode, string desc)
    {
        var ex = new ServerErrorException($"Server error ({statusCode} - {desc})", statusCode);
        Assert.Equal(statusCode, ex.StatusCode);
        Assert.Contains(statusCode.ToString(), ex.Message);
    }

    [Fact]
    public void ServerErrorException_DefaultStatusCode_IsNull()
    {
        var ex = new ServerErrorException("Server error");
        Assert.Null(ex.StatusCode);
    }

    #endregion

    #region Error Classification Patterns (simulating ClassifyAndThrow)

    [Theory]
    [InlineData("rate limit exceeded")]
    [InlineData("Rate Limit Exceeded")]
    [InlineData("HTTP 429 rate limit")]
    [InlineData("429 Too Many Requests")]
    [InlineData("request was rate limited")]
    public void ClassifyPattern_RateLimit_Detected(string message)
    {
        // Simulate the classification logic from ClassifyAndThrow
        var lower = message.ToLowerInvariant();
        var isRateLimit = lower.Contains("rate limit") ||
            System.Text.RegularExpressions.Regex.IsMatch(message, @"\b429\b");
        Assert.True(isRateLimit, $"Should classify '{message}' as rate limit");
    }

    [Theory]
    [InlineData("HTTP 500 Internal Server Error")]
    [InlineData("502 Bad Gateway")]
    [InlineData("503 Service Unavailable")]
    [InlineData("504 Gateway Timeout")]
    public void ClassifyPattern_ServerError_StatusCode_Detected(string message)
    {
        var match = System.Text.RegularExpressions.Regex.Match(message, @"\b(500|502|503|504)\b");
        Assert.True(match.Success, $"Should detect server status code in '{message}'");
    }

    [Theory]
    [InlineData("internal server error occurred")]
    [InlineData("bad gateway response")]
    [InlineData("gateway timeout from upstream")]
    [InlineData("service unavailable, try again later")]
    public void ClassifyPattern_ServerError_TextMatch_Detected(string message)
    {
        var lower = message.ToLowerInvariant();
        var isServerError = lower.Contains("internal server") ||
            lower.Contains("bad gateway") ||
            lower.Contains("gateway timeout") ||
            lower.Contains("service unavailable");
        Assert.True(isServerError, $"Should classify '{message}' as server error");
    }

    [Theory]
    [InlineData("Unknown error")]
    [InlineData("Connection refused")]
    [InlineData("DNS resolution failed")]
    [InlineData("Something went wrong")]
    public void ClassifyPattern_GenericError_NeitherRateLimitNorServer(string message)
    {
        var lower = message.ToLowerInvariant();
        var isRateLimit = lower.Contains("rate limit") ||
            System.Text.RegularExpressions.Regex.IsMatch(message, @"\b429\b");
        var serverMatch = System.Text.RegularExpressions.Regex.Match(message, @"\b(500|502|503|504)\b");
        var isServerText = lower.Contains("internal server") ||
            lower.Contains("bad gateway") ||
            lower.Contains("gateway timeout") ||
            lower.Contains("service unavailable");

        Assert.False(isRateLimit, $"'{message}' should NOT be rate limit");
        Assert.False(serverMatch.Success, $"'{message}' should NOT have server status code");
        Assert.False(isServerText, $"'{message}' should NOT be server text error");
    }

    #endregion

    #region RateLimiter Integration with Error Classification

    [Fact]
    public void RecordFailure_WithRetryAfter_UpdatesDelay()
    {
        var rl = new RateLimiter(jitterFactor: 0);
        var wait = rl.RecordFailure(retryAfter: 10.0);
        // retryAfter * 1.1 = 11.0
        Assert.Equal(11.0, wait, precision: 1);
        Assert.Equal(1, rl.ConsecutiveFailures);
    }

    [Fact]
    public void RecordFailure_ServerError_HalfExponent()
    {
        var rl1 = new RateLimiter(minDelay: 1.0, baseBackoff: 2.0, jitterFactor: 0);
        var rl2 = new RateLimiter(minDelay: 1.0, baseBackoff: 2.0, jitterFactor: 0);

        // Regular: exponent = 1, so 2^1 = 2
        var regularWait = rl1.RecordFailure();

        // Server: exponent = 1 * 0.5, so 2^0.5 ≈ 1.414
        var serverWait = rl2.RecordFailure(isServerError: true);

        Assert.True(regularWait >= serverWait,
            $"Regular wait {regularWait} should be >= server wait {serverWait}");
    }

    #endregion

    #region Exception Catch Semantics

    [Fact]
    public void CatchBlock_CopilotAnalysis_CatchesRateLimit()
    {
        Exception thrown = new RateLimitException("test", 5.0);
        var caught = false;
        try { throw thrown; }
        catch (CopilotAnalysisException) { caught = true; }
        Assert.True(caught);
    }

    [Fact]
    public void CatchBlock_CopilotAnalysis_CatchesServerError()
    {
        Exception thrown = new ServerErrorException("test", 500);
        var caught = false;
        try { throw thrown; }
        catch (CopilotAnalysisException) { caught = true; }
        Assert.True(caught);
    }

    [Fact]
    public void CatchBlock_CopilotAnalysis_CatchesNotAuthenticated()
    {
        Exception thrown = new CopilotNotAuthenticatedException("test");
        var caught = false;
        try { throw thrown; }
        catch (CopilotAnalysisException) { caught = true; }
        Assert.True(caught);
    }

    [Fact]
    public void CatchBlock_RateLimit_DoesNotCatchServerError()
    {
        Exception thrown = new ServerErrorException("test", 500);
        var caughtAsRateLimit = false;
        var caughtAsGeneral = false;
        try { throw thrown; }
        catch (RateLimitException) { caughtAsRateLimit = true; }
        catch (CopilotAnalysisException) { caughtAsGeneral = true; }
        Assert.False(caughtAsRateLimit);
        Assert.True(caughtAsGeneral);
    }

    #endregion
}
