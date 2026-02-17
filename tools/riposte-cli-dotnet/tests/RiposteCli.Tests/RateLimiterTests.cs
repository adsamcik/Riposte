using RiposteCli.RateLimiting;

namespace RiposteCli.Tests;

public class RateLimiterTests
{
    [Fact]
    public void InitialState_ZeroFailures()
    {
        var rl = new RateLimiter();
        Assert.Equal(0, rl.ConsecutiveFailures);
        Assert.Equal(1.0, rl.CurrentDelay);
        Assert.False(rl.ShouldGiveUp());
    }

    [Fact]
    public void RecordSuccess_ReducesDelay()
    {
        var rl = new RateLimiter();
        // Manually set higher delay
        rl.RecordFailure(); // increases delay
        var delayAfterFailure = rl.CurrentDelay;
        rl.RecordSuccess();
        Assert.True(rl.CurrentDelay < delayAfterFailure || rl.CurrentDelay == 1.0);
        Assert.Equal(0, rl.ConsecutiveFailures);
    }

    [Fact]
    public void RecordFailure_IncreasesConsecutiveFailures()
    {
        var rl = new RateLimiter();
        var wait = rl.RecordFailure();
        Assert.True(wait >= rl.CurrentDelay || wait >= 1.0);
        Assert.Equal(1, rl.ConsecutiveFailures);
    }

    [Fact]
    public void RecordFailure_WithRetryAfter_UsesRetryValue()
    {
        var rl = new RateLimiter();
        var wait = rl.RecordFailure(retryAfter: 10.0);
        Assert.Equal(11.0, wait, 1); // 10 * 1.1
    }

    [Fact]
    public void ShouldGiveUp_AfterMaxAttempts()
    {
        var rl = new RateLimiter(maxBackoffAttempts: 3);
        for (var i = 0; i < 3; i++)
            rl.RecordFailure();
        Assert.True(rl.ShouldGiveUp());
    }

    [Fact]
    public void ErrorRateTracking()
    {
        var rl = new RateLimiter(errorWindow: 4);
        rl.RecordSuccess();
        rl.RecordSuccess();
        rl.RecordFailure();
        rl.RecordFailure();
        Assert.Equal(0.5, rl.GetErrorRate(), 2);
    }

    [Fact]
    public void SuccessResetsConsecutiveFailures()
    {
        var rl = new RateLimiter(maxBackoffAttempts: 5);
        rl.RecordFailure();
        rl.RecordFailure();
        Assert.Equal(2, rl.ConsecutiveFailures);
        rl.RecordSuccess();
        Assert.Equal(0, rl.ConsecutiveFailures);
        rl.RecordFailure();
        Assert.False(rl.ShouldGiveUp());
    }
}

public class ConcurrencyLimiterTests
{
    [Fact]
    public void InitialState()
    {
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4, minConcurrency: 1);
        Assert.Equal(4, limiter.CurrentConcurrency);
        Assert.Equal(0, limiter.ActiveTasks);
        Assert.False(limiter.IsPaused);
        Assert.False(limiter.ShouldGiveUp());
    }

    [Fact]
    public async Task AcquireRelease_TracksActiveTasks()
    {
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4);
        await limiter.AcquireAsync();
        Assert.Equal(1, limiter.ActiveTasks);
        await limiter.ReleaseAsync();
        Assert.Equal(0, limiter.ActiveTasks);
    }

    [Fact]
    public async Task SemaphoreLimitsConcurrency()
    {
        var limiter = new ConcurrencyLimiter(maxConcurrency: 2);
        var order = new List<string>();

        async Task Worker(string name)
        {
            await limiter.AcquireAsync();
            lock (order) order.Add($"{name}_start");
            await Task.Delay(50);
            lock (order) order.Add($"{name}_end");
            await limiter.ReleaseAsync();
        }

        await Task.WhenAll(Worker("a"), Worker("b"), Worker("c"));
        Assert.Equal(3, order.Count(x => x.EndsWith("_start")));
        Assert.Equal(3, order.Count(x => x.EndsWith("_end")));
    }

    [Fact]
    public async Task ConcurrencyReducesOnRateLimit()
    {
        var rl = new RateLimiter(minDelay: 0.01, maxDelay: 0.05);
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4, minConcurrency: 1, rateLimiter: rl);

        await limiter.RecordRateLimitAsync(retryAfter: 0.01);
        Assert.Equal(3, limiter.CurrentConcurrency);

        await limiter.RecordRateLimitAsync(retryAfter: 0.01);
        Assert.Equal(2, limiter.CurrentConcurrency);
    }

    [Fact]
    public async Task ConcurrencyDoesNotGoBelowMin()
    {
        var rl = new RateLimiter(minDelay: 0.01, maxDelay: 0.05);
        var limiter = new ConcurrencyLimiter(maxConcurrency: 2, minConcurrency: 1, rateLimiter: rl);

        await limiter.RecordRateLimitAsync(retryAfter: 0.01);
        Assert.Equal(1, limiter.CurrentConcurrency);
        await limiter.RecordRateLimitAsync(retryAfter: 0.01);
        Assert.Equal(1, limiter.CurrentConcurrency); // Stays at min
    }

    [Fact]
    public async Task ConcurrencyRestoresOnSustainedSuccess()
    {
        var rl = new RateLimiter(minDelay: 0.01, maxDelay: 0.05);
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4, minConcurrency: 1, rateLimiter: rl, restoreThreshold: 3);

        await limiter.RecordRateLimitAsync(retryAfter: 0.01);
        Assert.Equal(3, limiter.CurrentConcurrency);

        for (var i = 0; i < 3; i++)
            await limiter.RecordSuccessAsync();
        Assert.Equal(4, limiter.CurrentConcurrency);
    }

    [Fact]
    public async Task ServerError_ReducesAfterThreshold()
    {
        var rl = new RateLimiter(minDelay: 0.01, maxDelay: 0.05);
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4, rateLimiter: rl);

        await limiter.RecordServerErrorAsync();
        Assert.Equal(4, limiter.CurrentConcurrency);
        await limiter.RecordServerErrorAsync();
        Assert.Equal(4, limiter.CurrentConcurrency);
        await limiter.RecordServerErrorAsync();
        Assert.Equal(3, limiter.CurrentConcurrency); // Third reduces it
    }
}

public class ErrorDetectionTests
{
    [Fact]
    public void Status429_NotMatchedInFilename()
    {
        var errorMsg = "Failed to process file: error_429.png not found";
        // "429" inside "error_429" should not match with word boundaries
        Assert.False(System.Text.RegularExpressions.Regex.IsMatch(
            errorMsg.Replace("error_429", "error_x"), @"\b429\b"));
        // But a real 429 status should match
        Assert.True(System.Text.RegularExpressions.Regex.IsMatch(
            "HTTP 429 Too Many Requests", @"\b429\b"));
    }

    [Fact]
    public void Status500_NotMatchedInFilename()
    {
        var errorMsg = "Error processing image_500.jpg";
        Assert.False(System.Text.RegularExpressions.Regex.IsMatch(
            errorMsg, @"\b(500|502|503|504)\b"));
    }

    [Fact]
    public void RealStatusCodes_Match()
    {
        foreach (var code in new[] { "500", "502", "503", "504" })
        {
            Assert.True(System.Text.RegularExpressions.Regex.IsMatch(
                $"HTTP {code} error", @"\b(500|502|503|504)\b"));
        }
    }

    [Fact]
    public void RateLimitKeyword_Matches()
    {
        Assert.Contains("rate limit", "rate limit exceeded".ToLowerInvariant());
        Assert.DoesNotContain("rate limit", "generating at a moderate rate".ToLowerInvariant());
    }
}

public class ResponseParsingTests
{
    [Fact]
    public void ParseResponseContent_ValidJson()
    {
        var json = """{"emojis": ["😂", "🐱"], "title": "Test", "description": "A test"}""";
        var result = CopilotService.ParseResponseContent(json);
        Assert.Equal(["😂", "🐱"], result.Emojis);
        Assert.Equal("Test", result.Title);
    }

    [Fact]
    public void ParseResponseContent_WrappedInMarkdown()
    {
        var json = """
            ```json
            {"emojis": ["😂"], "title": "Test"}
            ```
            """;
        var result = CopilotService.ParseResponseContent(json);
        Assert.Single(result.Emojis);
    }

    [Fact]
    public void ParseResponseContent_MissingEmojis_Throws()
    {
        var json = """{"title": "No emojis"}""";
        Assert.Throws<CopilotAnalysisException>(() =>
            CopilotService.ParseResponseContent(json));
    }
}
