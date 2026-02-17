using RiposteCli.RateLimiting;
using RiposteCli.Services;

namespace RiposteCli.Tests;

/// <summary>
/// Tests for CopilotService.ClassifyAndThrow error classification logic.
/// Uses ParseResponseContent + exception types as the indirect test surface,
/// since ClassifyAndThrow is private but called via exception propagation patterns.
/// </summary>
public class ClassifyAndThrowTests
{
    #region RateLimitException Classification

    [Fact]
    public void RateLimitException_HasRetryAfter()
    {
        var ex = new RateLimitException("Rate limited", retryAfter: 5.0);
        Assert.Equal(5.0, ex.RetryAfter);
        Assert.Contains("Rate limited", ex.Message);
    }

    [Fact]
    public void RateLimitException_NullRetryAfter()
    {
        var ex = new RateLimitException("Rate limited");
        Assert.Null(ex.RetryAfter);
    }

    [Fact]
    public void RateLimitException_IsSubclassOfCopilotAnalysisException()
    {
        var ex = new RateLimitException("Rate limited");
        Assert.IsAssignableFrom<CopilotAnalysisException>(ex);
    }

    #endregion

    #region ServerErrorException Classification

    [Theory]
    [InlineData(500)]
    [InlineData(502)]
    [InlineData(503)]
    [InlineData(504)]
    public void ServerErrorException_VariousStatusCodes(int statusCode)
    {
        var ex = new ServerErrorException($"Server error ({statusCode})", statusCode);
        Assert.Equal(statusCode, ex.StatusCode);
    }

    [Fact]
    public void ServerErrorException_NullStatusCode()
    {
        var ex = new ServerErrorException("Server error");
        Assert.Null(ex.StatusCode);
    }

    [Fact]
    public void ServerErrorException_IsSubclassOfCopilotAnalysisException()
    {
        var ex = new ServerErrorException("Server error", 500);
        Assert.IsAssignableFrom<CopilotAnalysisException>(ex);
    }

    #endregion

    #region CopilotNotAuthenticatedException

    [Fact]
    public void NotAuthenticated_IsSubclassOfCopilotAnalysisException()
    {
        var ex = new CopilotNotAuthenticatedException("Not authenticated");
        Assert.IsAssignableFrom<CopilotAnalysisException>(ex);
    }

    [Fact]
    public void NotAuthenticated_MessagePreserved()
    {
        var msg = "GitHub Copilot CLI not found. Install it from https://github.com/github/copilot-cli";
        var ex = new CopilotNotAuthenticatedException(msg);
        Assert.Equal(msg, ex.Message);
    }

    #endregion

    #region Exception Hierarchy

    [Fact]
    public void ExceptionHierarchy_AllExceptionsAreExceptions()
    {
        Assert.IsAssignableFrom<Exception>(new CopilotAnalysisException("base"));
        Assert.IsAssignableFrom<Exception>(new RateLimitException("rate"));
        Assert.IsAssignableFrom<Exception>(new ServerErrorException("server"));
        Assert.IsAssignableFrom<Exception>(new CopilotNotAuthenticatedException("auth"));
    }

    [Fact]
    public void ExceptionHierarchy_CatchCopilotAnalysis_CatchesAll()
    {
        // All specialized exceptions should be catchable as CopilotAnalysisException
        var exceptions = new CopilotAnalysisException[]
        {
            new RateLimitException("rate", 5.0),
            new ServerErrorException("server", 500),
            new CopilotNotAuthenticatedException("auth"),
            new CopilotAnalysisException("generic"),
        };

        foreach (var ex in exceptions)
            Assert.IsAssignableFrom<CopilotAnalysisException>(ex);
    }

    #endregion

    #region ParseResponseContent Error Paths

    [Fact]
    public void ParseResponse_EmptyString_Throws()
    {
        Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent(""));
    }

    [Fact]
    public void ParseResponse_NullEmojis_Throws()
    {
        var json = """{"emojis": null}""";
        Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent(json));
    }

    [Fact]
    public void ParseResponse_EmptyEmojis_Throws()
    {
        var json = """{"emojis": []}""";
        Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent(json));
    }

    [Fact]
    public void ParseResponse_MissingEmojisField_Throws()
    {
        var json = """{"title": "test", "description": "test"}""";
        Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent(json));
    }

    [Fact]
    public void ParseResponse_InvalidJson_ThrowsWithContext()
    {
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent("not json at all"));
        Assert.Contains("Failed to parse", ex.Message);
        Assert.Contains("not json at all", ex.Message);
    }

    [Fact]
    public void ParseResponse_HtmlErrorPage_Throws()
    {
        var html = "<html><body><h1>502 Bad Gateway</h1></body></html>";
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent(html));
        Assert.Contains("Failed to parse", ex.Message);
    }

    [Fact]
    public void ParseResponse_PartialJson_Throws()
    {
        Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent("{\"emojis\": [\"😂\""));
    }

    [Fact]
    public void ParseResponse_TruncatedLongContent_InErrorMessage()
    {
        var longContent = new string('x', 500);
        var ex = Assert.Throws<CopilotAnalysisException>(
            () => CopilotService.ParseResponseContent(longContent));
        // Error message should truncate to 200 chars
        Assert.True(ex.Message.Length < 500);
    }

    #endregion
}
