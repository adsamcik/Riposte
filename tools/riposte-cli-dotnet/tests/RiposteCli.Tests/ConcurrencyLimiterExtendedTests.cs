using RiposteCli.RateLimiting;

namespace RiposteCli.Tests;

public class ConcurrencyLimiterExtendedTests
{
    #region Pause/Resume

    [Fact]
    public async Task RecordRateLimit_PausesThenResumes()
    {
        var rl = new RateLimiter(minDelay: 0.01, maxDelay: 0.05, jitterFactor: 0);
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4, rateLimiter: rl);

        Assert.False(limiter.IsPaused);

        // Record rate limit — should pause briefly then resume
        await limiter.RecordRateLimitAsync(retryAfter: 0.01);

        // After the wait completes, should be unpaused
        Assert.False(limiter.IsPaused);
    }

    [Fact]
    public async Task RecordRateLimit_ReturnsPauseTime()
    {
        var rl = new RateLimiter(minDelay: 0.01, maxDelay: 0.05, jitterFactor: 0);
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4, rateLimiter: rl);

        var waitTime = await limiter.RecordRateLimitAsync(retryAfter: 0.01);

        Assert.True(waitTime > 0);
    }

    #endregion

    #region ShouldGiveUp

    [Fact]
    public async Task ShouldGiveUp_DelegatesToRateLimiter()
    {
        var rl = new RateLimiter(maxBackoffAttempts: 2, minDelay: 0.01, maxDelay: 0.05, jitterFactor: 0);
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4, rateLimiter: rl);

        Assert.False(limiter.ShouldGiveUp());

        await limiter.RecordRateLimitAsync(retryAfter: 0.01);
        Assert.False(limiter.ShouldGiveUp());

        await limiter.RecordRateLimitAsync(retryAfter: 0.01);
        Assert.True(limiter.ShouldGiveUp());
    }

    #endregion

    #region Concurrency Reduction — Sequential Rate Limits

    [Fact]
    public async Task MultipleRateLimits_ReduceConcurrencyProgressively()
    {
        var rl = new RateLimiter(minDelay: 0.01, maxDelay: 0.05, jitterFactor: 0);
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4, minConcurrency: 1, rateLimiter: rl);

        Assert.Equal(4, limiter.CurrentConcurrency);

        await limiter.RecordRateLimitAsync(retryAfter: 0.01);
        Assert.Equal(3, limiter.CurrentConcurrency);

        await limiter.RecordRateLimitAsync(retryAfter: 0.01);
        Assert.Equal(2, limiter.CurrentConcurrency);

        await limiter.RecordRateLimitAsync(retryAfter: 0.01);
        Assert.Equal(1, limiter.CurrentConcurrency);

        // Should not go below min
        await limiter.RecordRateLimitAsync(retryAfter: 0.01);
        Assert.Equal(1, limiter.CurrentConcurrency);
    }

    #endregion

    #region Concurrency Restoration

    [Fact]
    public async Task SuccessesRestore_OnlyAfterThreshold()
    {
        var rl = new RateLimiter(minDelay: 0.01, maxDelay: 0.05, jitterFactor: 0);
        var limiter = new ConcurrencyLimiter(
            maxConcurrency: 4, minConcurrency: 1,
            rateLimiter: rl, restoreThreshold: 5);

        // Reduce to 3
        await limiter.RecordRateLimitAsync(retryAfter: 0.01);
        Assert.Equal(3, limiter.CurrentConcurrency);

        // 4 successes — not yet enough
        for (var i = 0; i < 4; i++)
            await limiter.RecordSuccessAsync();
        Assert.Equal(3, limiter.CurrentConcurrency);

        // 5th success triggers restore
        await limiter.RecordSuccessAsync();
        Assert.Equal(4, limiter.CurrentConcurrency);
    }

    [Fact]
    public async Task SuccessCountResetsOnRateLimit()
    {
        var rl = new RateLimiter(minDelay: 0.01, maxDelay: 0.05, jitterFactor: 0);
        var limiter = new ConcurrencyLimiter(
            maxConcurrency: 4, minConcurrency: 1,
            rateLimiter: rl, restoreThreshold: 3);

        // Reduce to 3
        await limiter.RecordRateLimitAsync(retryAfter: 0.01);
        Assert.Equal(3, limiter.CurrentConcurrency);

        // 2 successes
        await limiter.RecordSuccessAsync();
        await limiter.RecordSuccessAsync();

        // Rate limit again — resets success counter, reduces to 2
        await limiter.RecordRateLimitAsync(retryAfter: 0.01);
        Assert.Equal(2, limiter.CurrentConcurrency);

        // Need full threshold of successes again
        for (var i = 0; i < 3; i++)
            await limiter.RecordSuccessAsync();
        Assert.Equal(3, limiter.CurrentConcurrency);
    }

    [Fact]
    public async Task ConcurrencyDoesNotRestoreAboveMax()
    {
        var rl = new RateLimiter(minDelay: 0.01, maxDelay: 0.05, jitterFactor: 0);
        var limiter = new ConcurrencyLimiter(
            maxConcurrency: 2, minConcurrency: 1,
            rateLimiter: rl, restoreThreshold: 2);

        // Already at max — successes shouldn't increase beyond max
        for (var i = 0; i < 10; i++)
            await limiter.RecordSuccessAsync();

        Assert.Equal(2, limiter.CurrentConcurrency);
    }

    #endregion

    #region Server Errors

    [Fact]
    public async Task ServerError_DoesNotReduceBeforeThreshold()
    {
        var rl = new RateLimiter(minDelay: 0.01, maxDelay: 0.05, jitterFactor: 0);
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4, rateLimiter: rl);

        await limiter.RecordServerErrorAsync();
        Assert.Equal(4, limiter.CurrentConcurrency);

        await limiter.RecordServerErrorAsync();
        Assert.Equal(4, limiter.CurrentConcurrency);
    }

    [Fact]
    public async Task ServerError_ReducesAfterThirdConsecutive()
    {
        var rl = new RateLimiter(minDelay: 0.01, maxDelay: 0.05, jitterFactor: 0);
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4, rateLimiter: rl);

        await limiter.RecordServerErrorAsync();
        await limiter.RecordServerErrorAsync();
        await limiter.RecordServerErrorAsync();

        Assert.Equal(3, limiter.CurrentConcurrency);
    }

    [Fact]
    public async Task ServerError_ReturnsWaitTime()
    {
        var rl = new RateLimiter(minDelay: 0.01, jitterFactor: 0);
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4, rateLimiter: rl);

        var waitTime = await limiter.RecordServerErrorAsync();
        Assert.True(waitTime >= 0);
    }

    [Fact]
    public async Task ServerError_SuccessResetsCounts()
    {
        var rl = new RateLimiter(minDelay: 0.01, maxDelay: 0.05, jitterFactor: 0);
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4, rateLimiter: rl);

        await limiter.RecordServerErrorAsync();
        await limiter.RecordServerErrorAsync();
        Assert.Equal(4, limiter.CurrentConcurrency);

        // Success resets the consecutive failure count
        await limiter.RecordSuccessAsync();

        // Two more server errors — shouldn't reduce because consecutive count was reset
        await limiter.RecordServerErrorAsync();
        await limiter.RecordServerErrorAsync();
        Assert.Equal(4, limiter.CurrentConcurrency);
    }

    #endregion

    #region Acquire/Release — Concurrency

    [Fact]
    public async Task AcquireRelease_TracksActiveCount()
    {
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4);

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
    public async Task Release_WithoutAcquire_ThrowsSemaphoreFullException()
    {
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4);

        // Release without acquire — semaphore cannot exceed max count
        await Assert.ThrowsAsync<SemaphoreFullException>(
            async () => await limiter.ReleaseAsync());
    }

    [Fact]
    public async Task ConcurrentWorkers_RespectSemaphoreLimit()
    {
        var limiter = new ConcurrencyLimiter(maxConcurrency: 2);
        var maxObserved = 0;
        var currentActive = 0;
        var lockObj = new object();

        async Task Worker()
        {
            await limiter.AcquireAsync();
            lock (lockObj)
            {
                currentActive++;
                if (currentActive > maxObserved) maxObserved = currentActive;
            }

            await Task.Delay(20); // Small work simulation

            lock (lockObj) { currentActive--; }
            await limiter.ReleaseAsync();
        }

        await Task.WhenAll(
            Worker(), Worker(), Worker(), Worker(), Worker());

        Assert.True(maxObserved <= 2,
            $"Max concurrent was {maxObserved}, but limit is 2");
    }

    #endregion

    #region RateLimiter Property

    [Fact]
    public void RateLimiter_ExposesUnderlyingLimiter()
    {
        var rl = new RateLimiter();
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4, rateLimiter: rl);

        Assert.Same(rl, limiter.RateLimiter);
    }

    [Fact]
    public void RateLimiter_CreatesDefaultIfNotProvided()
    {
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4);
        Assert.NotNull(limiter.RateLimiter);
    }

    #endregion
}
