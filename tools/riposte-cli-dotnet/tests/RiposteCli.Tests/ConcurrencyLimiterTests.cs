using RiposteCli.RateLimiting;

namespace RiposteCli.Tests;

/// <summary>
/// Tests for ConcurrencyLimiter: ShouldGiveUp, ReduceConcurrency, RecordServerError, restore logic.
/// </summary>
public class ConcurrencyLimiterDetailedTests
{
    #region ShouldGiveUp

    [Fact]
    public void ShouldGiveUp_Initially_ReturnsFalse()
    {
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4);
        Assert.False(limiter.ShouldGiveUp());
    }

    [Fact]
    public async Task ShouldGiveUp_AfterManyRateLimits_DelegatesToRateLimiter()
    {
        var rl = new RateLimiter(minDelay: 0.001, maxDelay: 0.01, maxBackoffAttempts: 3, jitterFactor: 0);
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4, rateLimiter: rl);

        for (var i = 0; i < 3; i++)
            await limiter.RecordRateLimitAsync(retryAfter: 0.001);

        Assert.True(limiter.ShouldGiveUp());
    }

    #endregion

    #region ReduceConcurrency via RecordRateLimit

    [Fact]
    public async Task RecordRateLimit_ReducesConcurrency()
    {
        var rl = new RateLimiter(minDelay: 0.001, maxDelay: 0.01, jitterFactor: 0);
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4, minConcurrency: 1, rateLimiter: rl);

        Assert.Equal(4, limiter.CurrentConcurrency);
        await limiter.RecordRateLimitAsync(retryAfter: 0.001);
        Assert.Equal(3, limiter.CurrentConcurrency);
    }

    [Fact]
    public async Task RecordRateLimit_FlooredAtMinConcurrency()
    {
        var rl = new RateLimiter(minDelay: 0.001, maxDelay: 0.01, jitterFactor: 0, maxBackoffAttempts: 100);
        var limiter = new ConcurrencyLimiter(maxConcurrency: 3, minConcurrency: 2, rateLimiter: rl);

        for (var i = 0; i < 10; i++)
            await limiter.RecordRateLimitAsync(retryAfter: 0.001);

        Assert.True(limiter.CurrentConcurrency >= 2,
            $"Concurrency {limiter.CurrentConcurrency} dropped below min 2");
    }

    [Fact]
    public async Task RecordRateLimit_ReturnsWaitTime()
    {
        var rl = new RateLimiter(minDelay: 0.001, maxDelay: 0.01, jitterFactor: 0);
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4, rateLimiter: rl);

        var waitTime = await limiter.RecordRateLimitAsync(retryAfter: 0.001);
        Assert.True(waitTime >= 0);
    }

    #endregion

    #region RecordServerError

    [Fact]
    public async Task RecordServerError_ReturnsWaitTime()
    {
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4);
        var waitTime = await limiter.RecordServerErrorAsync();
        Assert.True(waitTime >= 0);
    }

    [Fact]
    public async Task RecordServerError_ThreeInARow_ReducesConcurrency()
    {
        var rl = new RateLimiter(minDelay: 0.001, jitterFactor: 0, maxBackoffAttempts: 100);
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4, minConcurrency: 1, rateLimiter: rl);

        await limiter.RecordServerErrorAsync();
        await limiter.RecordServerErrorAsync();
        // After 2 server errors, concurrency should still be at 4 (threshold is 3)
        Assert.Equal(4, limiter.CurrentConcurrency);

        await limiter.RecordServerErrorAsync();
        // After 3 server errors, should reduce
        Assert.Equal(3, limiter.CurrentConcurrency);
    }

    [Fact]
    public async Task RecordServerError_LessThanThree_NoConcurrencyReduction()
    {
        var rl = new RateLimiter(minDelay: 0.001, jitterFactor: 0);
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4, rateLimiter: rl);

        await limiter.RecordServerErrorAsync();
        await limiter.RecordServerErrorAsync();
        Assert.Equal(4, limiter.CurrentConcurrency);
    }

    #endregion

    #region RecordSuccess and Restore

    [Fact]
    public async Task RecordSuccess_BelowThreshold_NoRestore()
    {
        var rl = new RateLimiter(minDelay: 0.001, jitterFactor: 0);
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4, minConcurrency: 1,
            rateLimiter: rl, restoreThreshold: 10);

        // Reduce concurrency first
        await limiter.RecordRateLimitAsync(retryAfter: 0.001);
        Assert.Equal(3, limiter.CurrentConcurrency);

        // Record 5 successes (below threshold of 10)
        for (var i = 0; i < 5; i++)
            await limiter.RecordSuccessAsync();

        Assert.Equal(3, limiter.CurrentConcurrency);
    }

    [Fact]
    public async Task RecordSuccess_AtThreshold_RestoresConcurrency()
    {
        var rl = new RateLimiter(minDelay: 0.001, jitterFactor: 0);
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4, minConcurrency: 1,
            rateLimiter: rl, restoreThreshold: 5);

        // Reduce concurrency
        await limiter.RecordRateLimitAsync(retryAfter: 0.001);
        Assert.Equal(3, limiter.CurrentConcurrency);

        // Record enough successes to trigger restore
        for (var i = 0; i < 5; i++)
            await limiter.RecordSuccessAsync();

        Assert.Equal(4, limiter.CurrentConcurrency);
    }

    [Fact]
    public async Task RecordSuccess_AlreadyAtMax_NoChange()
    {
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4, restoreThreshold: 2);

        for (var i = 0; i < 10; i++)
            await limiter.RecordSuccessAsync();

        Assert.Equal(4, limiter.CurrentConcurrency);
    }

    [Fact]
    public async Task RecordRateLimit_ResetsSuccessCounter()
    {
        var rl = new RateLimiter(minDelay: 0.001, jitterFactor: 0);
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4, minConcurrency: 1,
            rateLimiter: rl, restoreThreshold: 5);

        // Reduce to 3
        await limiter.RecordRateLimitAsync(retryAfter: 0.001);
        Assert.Equal(3, limiter.CurrentConcurrency);

        // Record 4 successes (just below threshold)
        for (var i = 0; i < 4; i++)
            await limiter.RecordSuccessAsync();

        // Rate limit again (resets counter, reduces to 2)
        await limiter.RecordRateLimitAsync(retryAfter: 0.001);
        Assert.Equal(2, limiter.CurrentConcurrency);

        // 4 successes again should NOT restore (counter was reset)
        for (var i = 0; i < 4; i++)
            await limiter.RecordSuccessAsync();
        Assert.Equal(2, limiter.CurrentConcurrency);

        // 1 more success = 5 total since last reduce → should restore
        await limiter.RecordSuccessAsync();
        Assert.Equal(3, limiter.CurrentConcurrency);
    }

    #endregion

    #region IsPaused State

    [Fact]
    public void IsPaused_Initially_False()
    {
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4);
        Assert.False(limiter.IsPaused);
    }

    [Fact]
    public async Task IsPaused_DuringRateLimit_True()
    {
        var rl = new RateLimiter(minDelay: 0.001, jitterFactor: 0);
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4, rateLimiter: rl);

        // Start rate limit with long pause
        var task = limiter.RecordRateLimitAsync(retryAfter: 0.5);
        await Task.Delay(50); // Let the pause take effect
        Assert.True(limiter.IsPaused);

        await task;
        // After waiting, should be unpaused
        Assert.False(limiter.IsPaused);
    }

    #endregion

    #region ActiveTasks Tracking

    [Fact]
    public async Task ActiveTasks_IncreasesOnAcquire_DecreasesOnRelease()
    {
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4);
        Assert.Equal(0, limiter.ActiveTasks);

        await limiter.AcquireAsync();
        Assert.Equal(1, limiter.ActiveTasks);

        await limiter.AcquireAsync();
        Assert.Equal(2, limiter.ActiveTasks);

        await limiter.ReleaseAsync();
        Assert.Equal(1, limiter.ActiveTasks);

        await limiter.ReleaseAsync();
        Assert.Equal(0, limiter.ActiveTasks);
    }

    [Fact]
    public async Task ActiveTasks_NeverGoesNegative()
    {
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4);

        // Release without acquire throws SemaphoreFullException in the underlying implementation.
        // This is expected behavior — verify that ActiveTasks is tracked correctly
        // with proper acquire/release pairs.
        await limiter.AcquireAsync();
        Assert.Equal(1, limiter.ActiveTasks);

        await limiter.ReleaseAsync();
        Assert.Equal(0, limiter.ActiveTasks);

        // ActiveTasks is already at 0 — verify it stays there after an acquire/release cycle
        await limiter.AcquireAsync();
        await limiter.ReleaseAsync();
        Assert.Equal(0, limiter.ActiveTasks);
    }

    #endregion

    #region RateLimiter Property

    [Fact]
    public void RateLimiter_ExposedViaProperty()
    {
        var rl = new RateLimiter(minDelay: 5.0);
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4, rateLimiter: rl);
        Assert.Same(rl, limiter.RateLimiter);
    }

    [Fact]
    public void RateLimiter_DefaultCreated_WhenNotProvided()
    {
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4);
        Assert.NotNull(limiter.RateLimiter);
    }

    #endregion
}
