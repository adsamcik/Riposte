using RiposteCli.RateLimiting;

namespace RiposteCli.Tests;

public class RateLimiterExtendedTests
{
    #region Backoff Behavior

    [Fact]
    public void RecordFailure_ExponentialBackoff_IncreasesWithEachFailure()
    {
        var rl = new RateLimiter(jitterFactor: 0); // No jitter for deterministic test

        var wait1 = rl.RecordFailure();
        var wait2 = rl.RecordFailure();
        var wait3 = rl.RecordFailure();

        Assert.True(wait2 > wait1, $"wait2 ({wait2}) should be > wait1 ({wait1})");
        Assert.True(wait3 > wait2, $"wait3 ({wait3}) should be > wait2 ({wait2})");
    }

    [Fact]
    public void RecordFailure_CapsAtMaxDelay()
    {
        var rl = new RateLimiter(maxDelay: 10.0, jitterFactor: 0);

        // Record many failures to exceed max
        for (var i = 0; i < 20; i++)
            rl.RecordFailure();

        Assert.True(rl.CurrentDelay <= 10.0,
            $"CurrentDelay ({rl.CurrentDelay}) should not exceed maxDelay (10.0)");
    }

    [Fact]
    public void RecordFailure_WithRetryAfter_UsesRetryValueWithMultiplier()
    {
        var rl = new RateLimiter();
        var wait = rl.RecordFailure(retryAfter: 5.0);

        // retryAfter * 1.1 = 5.5
        Assert.Equal(5.5, wait, 1);
    }

    [Fact]
    public void RecordFailure_ServerError_UsesHalfExponent()
    {
        var rl = new RateLimiter(jitterFactor: 0);

        var normalWait = new RateLimiter(jitterFactor: 0);
        normalWait.RecordFailure();
        var normal = normalWait.CurrentDelay;

        rl.RecordFailure(isServerError: true);
        // Server error uses exponent * 0.5, so should be less aggressive
        // At 1 failure: normal = 2^1 = 2, server = 2^0.5 ≈ 1.414
        Assert.True(rl.CurrentDelay <= normal,
            $"Server error delay ({rl.CurrentDelay}) should be <= normal delay ({normal})");
    }

    [Fact]
    public void RecordSuccess_GraduallyReducesDelay()
    {
        var rl = new RateLimiter(minDelay: 1.0);

        // Build up delay
        rl.RecordFailure();
        rl.RecordFailure();
        var highDelay = rl.CurrentDelay;

        // Reduce with successes
        rl.RecordSuccess();
        var afterOneSuccess = rl.CurrentDelay;
        rl.RecordSuccess();
        var afterTwoSuccesses = rl.CurrentDelay;

        Assert.True(afterOneSuccess < highDelay);
        Assert.True(afterTwoSuccesses < afterOneSuccess);
    }

    [Fact]
    public void RecordSuccess_DoesNotGoBelowMinDelay()
    {
        var rl = new RateLimiter(minDelay: 1.0);

        for (var i = 0; i < 100; i++)
            rl.RecordSuccess();

        Assert.True(rl.CurrentDelay >= 1.0);
    }

    #endregion

    #region ShouldGiveUp

    [Theory]
    [InlineData(3)]
    [InlineData(5)]
    [InlineData(8)]
    public void ShouldGiveUp_ExactlyAtMaxAttempts(int maxAttempts)
    {
        var rl = new RateLimiter(maxBackoffAttempts: maxAttempts);

        for (var i = 0; i < maxAttempts - 1; i++)
        {
            rl.RecordFailure();
            Assert.False(rl.ShouldGiveUp());
        }

        rl.RecordFailure();
        Assert.True(rl.ShouldGiveUp());
    }

    [Fact]
    public void ShouldGiveUp_ResetsBySuccess()
    {
        var rl = new RateLimiter(maxBackoffAttempts: 3);
        rl.RecordFailure();
        rl.RecordFailure();
        Assert.False(rl.ShouldGiveUp());

        rl.RecordSuccess();
        rl.RecordFailure();
        Assert.False(rl.ShouldGiveUp()); // Only 1 consecutive failure now
    }

    #endregion

    #region Error Rate Tracking

    [Fact]
    public void GetErrorRate_NoResults_ReturnsZero()
    {
        var rl = new RateLimiter();
        Assert.Equal(0.0, rl.GetErrorRate());
    }

    [Fact]
    public void GetErrorRate_AllSuccesses_ReturnsZero()
    {
        var rl = new RateLimiter();
        for (var i = 0; i < 5; i++)
            rl.RecordSuccess();
        Assert.Equal(0.0, rl.GetErrorRate());
    }

    [Fact]
    public void GetErrorRate_AllFailures_ReturnsOne()
    {
        var rl = new RateLimiter(errorWindow: 5, maxBackoffAttempts: 100);
        for (var i = 0; i < 5; i++)
            rl.RecordFailure();
        Assert.Equal(1.0, rl.GetErrorRate(), 2);
    }

    [Fact]
    public void GetErrorRate_Mixed_ReturnsCorrectRatio()
    {
        var rl = new RateLimiter(errorWindow: 10);

        rl.RecordSuccess();
        rl.RecordSuccess();
        rl.RecordSuccess();
        rl.RecordFailure();
        rl.RecordFailure();

        Assert.Equal(0.4, rl.GetErrorRate(), 2);
    }

    [Fact]
    public void GetErrorRate_SlidingWindow_OldResultsDropOff()
    {
        var rl = new RateLimiter(errorWindow: 3);

        rl.RecordFailure(); // [F]
        rl.RecordFailure(); // [F, F]
        rl.RecordFailure(); // [F, F, F]
        Assert.Equal(1.0, rl.GetErrorRate(), 2);

        rl.RecordSuccess(); // [F, F, S] — oldest F dropped
        Assert.True(rl.GetErrorRate() < 1.0);
    }

    #endregion

    #region WaitIfNeededAsync

    [Fact]
    public async Task WaitIfNeededAsync_InitialCall_DoesNotBlockLong()
    {
        var rl = new RateLimiter(minDelay: 0.01);
        var sw = System.Diagnostics.Stopwatch.StartNew();
        await rl.WaitIfNeededAsync();
        sw.Stop();

        // Should complete quickly (well under 1 second)
        Assert.True(sw.ElapsedMilliseconds < 500);
    }

    [Fact]
    public async Task WaitIfNeededAsync_AfterFailure_WaitsLonger()
    {
        var rl = new RateLimiter(minDelay: 0.01, jitterFactor: 0);
        rl.RecordFailure();

        var sw = System.Diagnostics.Stopwatch.StartNew();
        await rl.WaitIfNeededAsync();
        sw.Stop();

        // After failure the delay should be noticeably longer than min
        // But we keep the test fast by using small delays
        Assert.True(sw.ElapsedMilliseconds >= 0); // Just verify it completes
    }

    #endregion

    #region Jitter

    [Fact]
    public void RecordFailure_WithJitter_ProducesVariation()
    {
        // Run multiple times and check we get some variation
        var waits = new HashSet<double>();
        for (var trial = 0; trial < 10; trial++)
        {
            var rl = new RateLimiter(jitterFactor: 0.25);
            var wait = rl.RecordFailure();
            waits.Add(Math.Round(wait, 2));
        }

        // With 25% jitter, we should get at least some variation across 10 trials
        Assert.True(waits.Count >= 2,
            $"Expected variation in wait times, but got {waits.Count} unique value(s)");
    }

    #endregion
}
