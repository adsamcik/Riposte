using RiposteCli.RateLimiting;

namespace RiposteCli.Tests;

/// <summary>
/// Tests for RateLimiter: ShouldGiveUp, GetErrorRate, backoff calculations, WaitIfNeeded.
/// </summary>
public class RateLimiterBackoffTests
{
    #region ShouldGiveUp

    [Fact]
    public void ShouldGiveUp_NoFailures_ReturnsFalse()
    {
        var rl = new RateLimiter(maxBackoffAttempts: 8);
        Assert.False(rl.ShouldGiveUp());
    }

    [Fact]
    public void ShouldGiveUp_BelowThreshold_ReturnsFalse()
    {
        var rl = new RateLimiter(maxBackoffAttempts: 8);
        for (var i = 0; i < 7; i++)
            rl.RecordFailure();
        Assert.False(rl.ShouldGiveUp());
    }

    [Fact]
    public void ShouldGiveUp_AtThreshold_ReturnsTrue()
    {
        var rl = new RateLimiter(maxBackoffAttempts: 8);
        for (var i = 0; i < 8; i++)
            rl.RecordFailure();
        Assert.True(rl.ShouldGiveUp());
    }

    [Fact]
    public void ShouldGiveUp_AboveThreshold_StillTrue()
    {
        var rl = new RateLimiter(maxBackoffAttempts: 3);
        for (var i = 0; i < 10; i++)
            rl.RecordFailure();
        Assert.True(rl.ShouldGiveUp());
    }

    [Fact]
    public void ShouldGiveUp_AfterSuccessReset_ReturnsFalse()
    {
        var rl = new RateLimiter(maxBackoffAttempts: 3);
        for (var i = 0; i < 5; i++)
            rl.RecordFailure();
        Assert.True(rl.ShouldGiveUp());

        rl.RecordSuccess();
        Assert.False(rl.ShouldGiveUp());
    }

    #endregion

    #region GetErrorRate

    [Fact]
    public void GetErrorRate_NoResults_ReturnsZero()
    {
        var rl = new RateLimiter();
        Assert.Equal(0.0, rl.GetErrorRate());
    }

    [Fact]
    public void GetErrorRate_AllSuccesses_ReturnsZero()
    {
        var rl = new RateLimiter(errorWindow: 10);
        for (var i = 0; i < 5; i++)
            rl.RecordSuccess();
        Assert.Equal(0.0, rl.GetErrorRate());
    }

    [Fact]
    public void GetErrorRate_AllFailures_ReturnsOne()
    {
        var rl = new RateLimiter(errorWindow: 10, maxBackoffAttempts: 100);
        for (var i = 0; i < 5; i++)
            rl.RecordFailure();
        Assert.Equal(1.0, rl.GetErrorRate());
    }

    [Fact]
    public void GetErrorRate_Mixed_ReturnsCorrectRatio()
    {
        var rl = new RateLimiter(errorWindow: 10, maxBackoffAttempts: 100);
        // 3 successes, 2 failures = 5 total, 2/5 = 0.4
        rl.RecordSuccess();
        rl.RecordSuccess();
        rl.RecordSuccess();
        rl.RecordFailure();
        rl.RecordFailure();
        Assert.Equal(0.4, rl.GetErrorRate(), precision: 2);
    }

    [Fact]
    public void GetErrorRate_WindowOverflow_DropsOldest()
    {
        var rl = new RateLimiter(errorWindow: 3, maxBackoffAttempts: 100);
        rl.RecordFailure(); // [F]
        rl.RecordFailure(); // [F,F]
        rl.RecordFailure(); // [F,F,F]
        Assert.Equal(1.0, rl.GetErrorRate());

        rl.RecordSuccess(); // [F,F,S] (oldest F dropped)
        // Window should be [F,F,S] -> 2/3
        Assert.Equal(2.0 / 3, rl.GetErrorRate(), precision: 2);

        rl.RecordSuccess(); // [F,S,S]
        Assert.Equal(1.0 / 3, rl.GetErrorRate(), precision: 2);

        rl.RecordSuccess(); // [S,S,S]
        Assert.Equal(0.0, rl.GetErrorRate());
    }

    #endregion

    #region Backoff Calculation

    [Fact]
    public void RecordFailure_FirstFailure_ReturnsPositiveWait()
    {
        var rl = new RateLimiter(minDelay: 1.0, jitterFactor: 0);
        var wait = rl.RecordFailure();
        Assert.True(wait >= 1.0, $"First failure wait was {wait}");
    }

    [Fact]
    public void RecordFailure_ExponentiallyIncreasing()
    {
        var rl = new RateLimiter(minDelay: 1.0, maxDelay: 1000.0, jitterFactor: 0);
        var wait1 = rl.RecordFailure();
        rl.RecordSuccess(); // reset
        rl = new RateLimiter(minDelay: 1.0, maxDelay: 1000.0, jitterFactor: 0);
        rl.RecordFailure();
        var wait2 = rl.RecordFailure();
        Assert.True(wait2 >= wait1, $"Second failure {wait2} should be >= first {wait1}");
    }

    [Fact]
    public void RecordFailure_CappedAtMaxDelay()
    {
        var rl = new RateLimiter(minDelay: 1.0, maxDelay: 10.0, jitterFactor: 0, maxBackoffAttempts: 100);
        for (var i = 0; i < 50; i++)
            rl.RecordFailure();
        Assert.True(rl.CurrentDelay <= 10.0, $"Delay {rl.CurrentDelay} exceeded max 10.0");
    }

    [Fact]
    public void RecordFailure_WithRetryAfter_UsesRetryValue()
    {
        var rl = new RateLimiter(jitterFactor: 0);
        var wait = rl.RecordFailure(retryAfter: 30.0);
        // Should be retryAfter * 1.1
        Assert.Equal(33.0, wait, precision: 1);
    }

    [Fact]
    public void RecordFailure_ServerError_SlowerBackoff()
    {
        var rl1 = new RateLimiter(minDelay: 1.0, jitterFactor: 0, maxBackoffAttempts: 100);
        var rl2 = new RateLimiter(minDelay: 1.0, jitterFactor: 0, maxBackoffAttempts: 100);

        // Regular failure: exponent = consecutive failures
        for (var i = 0; i < 3; i++)
            rl1.RecordFailure();

        // Server error: exponent = consecutive * 0.5 (slower growth)
        for (var i = 0; i < 3; i++)
            rl2.RecordFailure(isServerError: true);

        Assert.True(rl1.CurrentDelay >= rl2.CurrentDelay,
            $"Regular delay {rl1.CurrentDelay} should be >= server delay {rl2.CurrentDelay}");
    }

    #endregion

    #region RecordSuccess

    [Fact]
    public void RecordSuccess_ResetsConsecutiveFailures()
    {
        var rl = new RateLimiter();
        rl.RecordFailure();
        rl.RecordFailure();
        Assert.Equal(2, rl.ConsecutiveFailures);

        rl.RecordSuccess();
        Assert.Equal(0, rl.ConsecutiveFailures);
    }

    [Fact]
    public void RecordSuccess_ReducesCurrentDelay()
    {
        var rl = new RateLimiter(minDelay: 1.0, jitterFactor: 0);
        rl.RecordFailure();
        rl.RecordFailure();
        var delayAfterFailure = rl.CurrentDelay;

        rl.RecordSuccess();
        Assert.True(rl.CurrentDelay < delayAfterFailure,
            $"After success, delay {rl.CurrentDelay} should be < {delayAfterFailure}");
    }

    [Fact]
    public void RecordSuccess_FlooredAtMinDelay()
    {
        var rl = new RateLimiter(minDelay: 0.5, jitterFactor: 0);
        for (var i = 0; i < 100; i++)
            rl.RecordSuccess();
        Assert.True(rl.CurrentDelay >= 0.5, $"Delay {rl.CurrentDelay} dropped below min 0.5");
    }

    #endregion

    #region WaitIfNeeded

    [Fact]
    public async Task WaitIfNeeded_NoDelay_CompletesQuickly()
    {
        var rl = new RateLimiter(minDelay: 0.001, jitterFactor: 0);
        var sw = System.Diagnostics.Stopwatch.StartNew();
        await rl.WaitIfNeededAsync();
        sw.Stop();
        Assert.True(sw.ElapsedMilliseconds < 500, $"WaitIfNeeded took {sw.ElapsedMilliseconds}ms");
    }

    [Fact]
    public async Task WaitIfNeeded_WithHighErrorRate_IncreasesDelay()
    {
        var rl = new RateLimiter(minDelay: 0.001, maxDelay: 0.01, errorWindow: 3,
            errorThreshold: 0.3, jitterFactor: 0, maxBackoffAttempts: 100);
        // Create high error rate
        rl.RecordFailure();
        rl.RecordFailure();
        rl.RecordFailure();

        var errorRate = rl.GetErrorRate();
        Assert.Equal(1.0, errorRate);
        // The delay should be increased by (1 + errorRate)
    }

    #endregion

    #region ConsecutiveFailures Property

    [Fact]
    public void ConsecutiveFailures_IncreasesOnFailure()
    {
        var rl = new RateLimiter();
        Assert.Equal(0, rl.ConsecutiveFailures);
        rl.RecordFailure();
        Assert.Equal(1, rl.ConsecutiveFailures);
        rl.RecordFailure();
        Assert.Equal(2, rl.ConsecutiveFailures);
    }

    [Fact]
    public void ConsecutiveFailures_ResetOnSuccess()
    {
        var rl = new RateLimiter();
        rl.RecordFailure();
        rl.RecordFailure();
        rl.RecordSuccess();
        Assert.Equal(0, rl.ConsecutiveFailures);
    }

    #endregion
}
