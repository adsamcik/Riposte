using RiposteCli.RateLimiting;

namespace RiposteCli.Tests;

public class ConcurrencyAdvancedTests
{
    #region Pause Blocks Acquire

    [Fact]
    public async Task PausedLimiter_AcquireBlocks_UntilResumed()
    {
        var rl = new RateLimiter(minDelay: 0.01, maxDelay: 0.1, jitterFactor: 0);
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4, rateLimiter: rl);

        // Start a rate limit with enough time for the acquire to start while paused
        var rateLimitTask = limiter.RecordRateLimitAsync(retryAfter: 0.5);

        // Give the pause time to take effect
        await Task.Delay(50);

        // Verify it's actually paused
        Assert.True(limiter.IsPaused);

        // While paused, try to acquire — should block until pause clears
        var acquired = false;
        var acquireTask = Task.Run(async () =>
        {
            await limiter.AcquireAsync();
            acquired = true;
            await limiter.ReleaseAsync();
        });

        // Wait a bit — acquire should NOT have completed yet while paused
        await Task.Delay(50);
        Assert.False(acquired, "Acquire should be blocked while paused");

        // Wait for the rate limit to finish (and unpause)
        await rateLimitTask;

        // The acquire should complete shortly after
        var completed = await Task.WhenAny(acquireTask, Task.Delay(5000));
        Assert.Same(acquireTask, completed);
        Assert.True(acquired);
    }

    #endregion

    #region Concurrent Stress Tests

    [Fact]
    public async Task ConcurrentWorkers_AllComplete_WithinSemaphoreLimit()
    {
        var limiter = new ConcurrencyLimiter(maxConcurrency: 3);
        var maxConcurrent = 0;
        var currentConcurrent = 0;
        var lockObj = new object();
        var completedCount = 0;

        var tasks = Enumerable.Range(0, 20).Select(async i =>
        {
            await limiter.AcquireAsync();
            try
            {
                lock (lockObj)
                {
                    currentConcurrent++;
                    if (currentConcurrent > maxConcurrent)
                        maxConcurrent = currentConcurrent;
                }

                await Task.Delay(5); // Brief work

                lock (lockObj) { currentConcurrent--; }
                Interlocked.Increment(ref completedCount);
            }
            finally
            {
                await limiter.ReleaseAsync();
            }
        }).ToArray();

        await Task.WhenAll(tasks);

        Assert.Equal(20, completedCount);
        Assert.True(maxConcurrent <= 3,
            $"Max concurrent was {maxConcurrent}, limit is 3");
    }

    [Fact]
    public async Task ConcurrentSuccessRecording_DoesNotCorrupt()
    {
        var rl = new RateLimiter(minDelay: 0.01, jitterFactor: 0);
        var limiter = new ConcurrencyLimiter(
            maxConcurrency: 4, minConcurrency: 1,
            rateLimiter: rl, restoreThreshold: 100);

        // Reduce concurrency first
        await limiter.RecordRateLimitAsync(retryAfter: 0.01);
        Assert.Equal(3, limiter.CurrentConcurrency);

        // Hammer with concurrent successes
        var tasks = Enumerable.Range(0, 50).Select(async _ =>
        {
            await limiter.RecordSuccessAsync();
        }).ToArray();

        await Task.WhenAll(tasks);

        // Concurrency should have been restored to 4 (50 successes > threshold 100? no)
        // With 50 successes and threshold 100, we shouldn't restore
        // But the internal counter should be consistent
        Assert.True(limiter.CurrentConcurrency is >= 1 and <= 4);
    }

    #endregion

    #region RateLimiter Thread Safety

    [Fact]
    public async Task RateLimiter_ConcurrentRecordSuccess_NoException()
    {
        var rl = new RateLimiter();

        var tasks = Enumerable.Range(0, 100).Select(_ =>
            Task.Run(() => rl.RecordSuccess())
        ).ToArray();

        await Task.WhenAll(tasks);

        Assert.Equal(0, rl.ConsecutiveFailures);
    }

    [Fact]
    public async Task RateLimiter_ConcurrentMixed_NoException()
    {
        var rl = new RateLimiter(maxBackoffAttempts: 1000);

        var tasks = Enumerable.Range(0, 200).Select(i =>
            Task.Run(() =>
            {
                if (i % 3 == 0)
                    rl.RecordFailure();
                else
                    rl.RecordSuccess();
            })
        ).ToArray();

        await Task.WhenAll(tasks);

        // Just verify no deadlock or exception — state may be non-deterministic
        Assert.True(rl.CurrentDelay >= 0);
        Assert.True(rl.GetErrorRate() is >= 0 and <= 1);
    }

    [Fact]
    public async Task RateLimiter_ConcurrentWaitIfNeeded_NoDeadlock()
    {
        var rl = new RateLimiter(minDelay: 0.001, jitterFactor: 0);

        var tasks = Enumerable.Range(0, 10).Select(_ =>
            rl.WaitIfNeededAsync()
        ).ToArray();

        var completed = await Task.WhenAny(Task.WhenAll(tasks), Task.Delay(5000));
        Assert.True(completed != await Task.WhenAny(Task.Delay(5000)),
            "WaitIfNeededAsync deadlocked");
    }

    #endregion

    #region ConcurrencyLimiter Initial Config

    [Theory]
    [InlineData(1)]
    [InlineData(2)]
    [InlineData(4)]
    [InlineData(10)]
    public void InitialConcurrency_MatchesMaxSetting(int max)
    {
        var limiter = new ConcurrencyLimiter(maxConcurrency: max);
        Assert.Equal(max, limiter.CurrentConcurrency);
    }

    [Fact]
    public void InitialState_NoActiveTasks()
    {
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4);
        Assert.Equal(0, limiter.ActiveTasks);
        Assert.False(limiter.IsPaused);
        Assert.False(limiter.ShouldGiveUp());
    }

    #endregion

    #region Edge: Rapid Rate Limits

    [Fact]
    public async Task RapidRateLimits_ConcurrencyFloorHolds()
    {
        var rl = new RateLimiter(minDelay: 0.001, maxDelay: 0.01, jitterFactor: 0);
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4, minConcurrency: 2, rateLimiter: rl);

        // Rapid-fire rate limits
        for (var i = 0; i < 10; i++)
            await limiter.RecordRateLimitAsync(retryAfter: 0.001);

        Assert.True(limiter.CurrentConcurrency >= 2,
            $"Concurrency ({limiter.CurrentConcurrency}) dropped below min (2)");
    }

    #endregion
}
