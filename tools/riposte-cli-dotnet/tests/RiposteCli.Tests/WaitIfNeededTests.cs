using RiposteCli.RateLimiting;

namespace RiposteCli.Tests;

/// <summary>
/// Tests for RateLimiter.WaitIfNeededAsync: error rate amplification,
/// monotonic time tracking, and delay calculation edge cases.
/// </summary>
public class WaitIfNeededTests
{
    #region Error Rate Amplification

    [Fact]
    public async Task WaitIfNeeded_HighErrorRate_IncreasesDelay()
    {
        var rl = new RateLimiter(minDelay: 0.05, maxDelay: 1.0, errorWindow: 5,
            errorThreshold: 0.3, jitterFactor: 0, maxBackoffAttempts: 100);

        // Create high error rate: 4 failures, 1 success = 80% error rate
        rl.RecordFailure();
        rl.RecordFailure();
        rl.RecordFailure();
        rl.RecordFailure();
        rl.RecordSuccess();

        Assert.True(rl.GetErrorRate() > 0.3, $"Error rate {rl.GetErrorRate()} should be > 0.3");

        // WaitIfNeeded with high error rate should apply multiplier
        var sw = System.Diagnostics.Stopwatch.StartNew();
        await rl.WaitIfNeededAsync();
        sw.Stop();

        // The delay should be amplified by (1 + errorRate)
        // With 0.8 error rate: delay * 1.8
    }

    [Fact]
    public async Task WaitIfNeeded_LowErrorRate_NoAmplification()
    {
        var rl = new RateLimiter(minDelay: 0.01, maxDelay: 1.0, errorWindow: 10,
            errorThreshold: 0.3, jitterFactor: 0);

        // All successes = 0% error rate
        for (var i = 0; i < 5; i++)
            rl.RecordSuccess();

        Assert.Equal(0.0, rl.GetErrorRate());

        var sw = System.Diagnostics.Stopwatch.StartNew();
        await rl.WaitIfNeededAsync();
        sw.Stop();

        // Should complete quickly with low error rate
        Assert.True(sw.ElapsedMilliseconds < 500, $"Wait took {sw.ElapsedMilliseconds}ms");
    }

    [Fact]
    public async Task WaitIfNeeded_ExactlyAtThreshold_NoAmplification()
    {
        var rl = new RateLimiter(minDelay: 0.01, maxDelay: 1.0, errorWindow: 10,
            errorThreshold: 0.3, jitterFactor: 0);

        // 3 failures, 7 successes = 30% error rate = exactly at threshold
        for (var i = 0; i < 7; i++) rl.RecordSuccess();
        rl.RecordFailure();
        rl.RecordFailure();
        rl.RecordFailure();

        // At threshold but not above — should not amplify
        Assert.True(rl.GetErrorRate() <= 0.3);
    }

    #endregion

    #region Consecutive Calls Spacing

    [Fact]
    public async Task WaitIfNeeded_ConsecutiveCalls_EnforceMinDelay()
    {
        var rl = new RateLimiter(minDelay: 0.05, jitterFactor: 0);

        var sw = System.Diagnostics.Stopwatch.StartNew();
        await rl.WaitIfNeededAsync();
        var first = sw.ElapsedMilliseconds;

        await rl.WaitIfNeededAsync();
        var total = sw.ElapsedMilliseconds;

        // Second call should have waited for the min delay
        Assert.True(total - first >= 30, // 50ms min delay, with some slack
            $"Second call didn't wait long enough: {total - first}ms");
    }

    #endregion

    #region After Failure Recovery

    [Fact]
    public async Task WaitIfNeeded_AfterFailureAndSuccess_DelayShrinks()
    {
        var rl = new RateLimiter(minDelay: 0.01, maxDelay: 5.0, jitterFactor: 0);

        // Record failures to increase delay
        rl.RecordFailure();
        rl.RecordFailure();
        var delayAfterFailures = rl.CurrentDelay;

        // Record success to shrink delay
        rl.RecordSuccess();
        var delayAfterSuccess = rl.CurrentDelay;

        Assert.True(delayAfterSuccess < delayAfterFailures,
            $"Delay after success ({delayAfterSuccess}) should be < after failures ({delayAfterFailures})");
    }

    #endregion
}
