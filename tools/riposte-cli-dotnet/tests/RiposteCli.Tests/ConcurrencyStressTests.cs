using System.Collections.Concurrent;
using System.Diagnostics;
using RiposteCli.RateLimiting;

namespace RiposteCli.Tests;

public class ConcurrencyStressTests
{
    [Fact]
    public async Task ConcurrencyLimiter_50ConcurrentAcquireReleaseCycles_AllCompleteWithoutExceptions()
    {
        var limiter = new ConcurrencyLimiter(maxConcurrency: 8, minConcurrency: 1);
        var completed = 0;

        var tasks = Enumerable.Range(0, 50).Select(async _ =>
        {
            await limiter.AcquireAsync();
            try
            {
                await Task.Delay(2);
                Interlocked.Increment(ref completed);
            }
            finally
            {
                await limiter.ReleaseAsync();
            }
        });

        await Task.WhenAll(tasks);

        Assert.Equal(50, completed);
        Assert.Equal(0, limiter.ActiveTasks);
    }

    [Fact]
    public async Task ConcurrencyLimiter_ConcurrentWorkersWithRateLimitEvents_ReduceConcurrencyUnderLoadWithoutCrashing()
    {
        var limiter = new ConcurrencyLimiter(
            maxConcurrency: 6,
            minConcurrency: 1,
            rateLimiter: new RateLimiter(minDelay: 0.001, maxDelay: 0.05, jitterFactor: 0),
            restoreThreshold: 1000);
        var errors = new ConcurrentBag<Exception>();

        var workers = Enumerable.Range(0, 10).Select(async workerId =>
        {
            for (var i = 0; i < 20; i++)
            {
                try
                {
                    if ((workerId + i) % 5 == 0)
                    {
                        await limiter.RecordRateLimitAsync(retryAfter: 0.001);
                    }
                    else
                    {
                        await limiter.AcquireAsync();
                        try
                        {
                            await Task.Delay(1);
                        }
                        finally
                        {
                            await limiter.ReleaseAsync();
                        }
                    }
                }
                catch (Exception ex)
                {
                    errors.Add(ex);
                }
            }
        });

        await Task.WhenAll(workers);

        Assert.Empty(errors);
        Assert.InRange(limiter.CurrentConcurrency, 1, 6);
        Assert.Equal(0, limiter.ActiveTasks);
    }

    [Fact]
    public async Task ConcurrencyLimiter_ReduceToMinThenTwentySuccesses_GraduallyRestoresToMax()
    {
        var limiter = new ConcurrencyLimiter(
            maxConcurrency: 5,
            minConcurrency: 1,
            rateLimiter: new RateLimiter(minDelay: 0.001, maxDelay: 0.05, jitterFactor: 0),
            restoreThreshold: 4);

        for (var i = 0; i < 10; i++)
            await limiter.RecordRateLimitAsync(retryAfter: 0.001);

        Assert.Equal(1, limiter.CurrentConcurrency);

        var observed = new List<int> { limiter.CurrentConcurrency };
        foreach (var _ in Enumerable.Range(0, 20))
        {
            await limiter.RecordSuccessAsync();
            observed.Add(limiter.CurrentConcurrency);
        }

        for (var i = 1; i < observed.Count; i++)
            Assert.True(observed[i] >= observed[i - 1], "Concurrency should not decrease during success recovery.");

        Assert.Equal(5, limiter.CurrentConcurrency);
    }

    [Fact]
    public async Task ConcurrencyLimiter_ConcurrentRecordRateLimit_GlobalPauseAppliesToAllWorkers()
    {
        var limiter = new ConcurrencyLimiter(
            maxConcurrency: 4,
            minConcurrency: 1,
            rateLimiter: new RateLimiter(minDelay: 0.001, maxDelay: 0.2, jitterFactor: 0));

        var sw = Stopwatch.StartNew();
        var waits = await Task.WhenAll(
            Enumerable.Range(0, 8).Select(_ => limiter.RecordRateLimitAsync(retryAfter: 0.05)));
        sw.Stop();

        Assert.All(waits, wait => Assert.True(wait >= 0.05));
        // Use generous lower bound - CI machines can be slow
        Assert.True(sw.Elapsed >= TimeSpan.FromMilliseconds(30),
            $"Expected at least 30ms total, got {sw.ElapsedMilliseconds}ms");
        Assert.False(limiter.IsPaused);
    }

    [Fact]
    public async Task ConcurrencyLimiter_AcquireThenGlobalPause_WorkersBlockThenResumeAfterUnpause()
    {
        var limiter = new ConcurrencyLimiter(
            maxConcurrency: 3,
            minConcurrency: 1,
            rateLimiter: new RateLimiter(minDelay: 0.001, maxDelay: 0.5, jitterFactor: 0));

        await limiter.AcquireAsync();
        // Use a longer pause so timing is reliable across machines
        var pauseTask = limiter.RecordRateLimitAsync(retryAfter: 0.15);

        while (!limiter.IsPaused)
            await Task.Delay(2);

        var workerStartedDuringPause = new ConcurrentBag<bool>();
        var workers = Enumerable.Range(0, 3).Select(async _ =>
        {
            var sw = Stopwatch.StartNew();
            await limiter.AcquireAsync();
            sw.Stop();
            // If the worker waited >= 30ms, the pause blocked it
            workerStartedDuringPause.Add(sw.ElapsedMilliseconds >= 30);
            await limiter.ReleaseAsync();
        }).ToArray();

        // Let workers queue up against the pause barrier
        await Task.Delay(30);

        await pauseTask;
        await limiter.ReleaseAsync();
        await Task.WhenAll(workers);

        // At least one worker should have been blocked by the pause
        Assert.Contains(true, workerStartedDuringPause);
    }

    [Fact]
    public async Task ConcurrencyLimiter_MaxConcurrencyOneWithTwentyTasks_SerializesWithoutOverlap()
    {
        var limiter = new ConcurrencyLimiter(maxConcurrency: 1, minConcurrency: 1);
        var currentInFlight = 0;
        var maxInFlight = 0;

        var tasks = Enumerable.Range(0, 20).Select(async _ =>
        {
            await limiter.AcquireAsync();
            try
            {
                var now = Interlocked.Increment(ref currentInFlight);
                // Thread-safe max update via compare-and-swap
                InterlockedMax(ref maxInFlight, now);
                await Task.Delay(3);
                Interlocked.Decrement(ref currentInFlight);
            }
            finally
            {
                await limiter.ReleaseAsync();
            }
        });

        await Task.WhenAll(tasks);

        Assert.Equal(1, maxInFlight);
        Assert.Equal(0, currentInFlight);
        Assert.Equal(0, limiter.ActiveTasks);
    }

    [Fact]
    public async Task ConcurrencyLimiter_RecordServerError_ReducesConcurrencyAfterThreeFailures()
    {
        var limiter = new ConcurrencyLimiter(
            maxConcurrency: 5,
            minConcurrency: 1,
            rateLimiter: new RateLimiter(minDelay: 0.001, maxDelay: 0.05, jitterFactor: 0));

        var initialConcurrency = limiter.CurrentConcurrency;

        // First two server errors should not reduce concurrency (threshold is 3)
        await limiter.RecordServerErrorAsync();
        await limiter.RecordServerErrorAsync();
        Assert.Equal(initialConcurrency, limiter.CurrentConcurrency);

        // Third consecutive failure triggers reduction
        await limiter.RecordServerErrorAsync();
        Assert.True(limiter.CurrentConcurrency < initialConcurrency,
            $"Expected concurrency < {initialConcurrency}, got {limiter.CurrentConcurrency}");
    }

    [Fact]
    public async Task ConcurrencyLimiter_DoubleRelease_ThrowsSemaphoreFullException()
    {
        var limiter = new ConcurrencyLimiter(maxConcurrency: 2, minConcurrency: 1);
        await limiter.AcquireAsync();

        // Release once (valid)
        await limiter.ReleaseAsync();
        Assert.Equal(0, limiter.ActiveTasks);

        // Second release without matching acquire — semaphore exceeds max count
        await Assert.ThrowsAsync<SemaphoreFullException>(() => limiter.ReleaseAsync());
    }

    [Fact]
    public async Task RateLimiter_100ConcurrentWaitIfNeededAsyncCalls_AllCompleteWithoutException()
    {
        var limiter = new RateLimiter(minDelay: 0.001, maxDelay: 0.05, jitterFactor: 0);
        limiter.RecordFailure();

        var tasks = Enumerable.Range(0, 100).Select(_ => limiter.WaitIfNeededAsync());
        await Task.WhenAll(tasks);

        Assert.True(limiter.CurrentDelay > 0);
    }

    [Fact]
    public void RateLimiter_RecordFailureEightTimes_ShouldGiveUpIsTrue()
    {
        // Sequential calls test the counter contract, not concurrency
        var limiter = new RateLimiter(maxBackoffAttempts: 8, jitterFactor: 0);

        for (var i = 0; i < 8; i++)
            limiter.RecordFailure();

        Assert.Equal(8, limiter.ConsecutiveFailures);
        Assert.True(limiter.ShouldGiveUp());
    }

    [Fact]
    public void RateLimiter_SequentialFailuresThenSuccesses_DelayIncreaseThenDecrease()
    {
        var limiter = new RateLimiter(minDelay: 0.05, maxDelay: 1.0, jitterFactor: 0);
        var baseline = limiter.CurrentDelay;

        // All failures - delay must increase
        for (var i = 0; i < 20; i++)
            limiter.RecordFailure();
        var afterFailures = limiter.CurrentDelay;

        // All successes - delay must decrease (or stay same)
        for (var i = 0; i < 20; i++)
            limiter.RecordSuccess();
        var afterSuccesses = limiter.CurrentDelay;

        Assert.True(afterFailures > baseline,
            $"Expected delay to increase from {baseline}, got {afterFailures}");
        Assert.True(afterSuccesses <= afterFailures,
            $"Expected delay to decrease from {afterFailures}, got {afterSuccesses}");
        Assert.True(afterSuccesses >= baseline,
            $"Expected delay >= baseline {baseline}, got {afterSuccesses}");
    }

    [Fact]
    public void RateLimiter_RecordSuccessResetsConsecutiveFailures()
    {
        var limiter = new RateLimiter(maxBackoffAttempts: 8, jitterFactor: 0);

        limiter.RecordFailure();
        limiter.RecordFailure();
        limiter.RecordFailure();
        Assert.Equal(3, limiter.ConsecutiveFailures);

        limiter.RecordSuccess();
        Assert.Equal(0, limiter.ConsecutiveFailures);
        Assert.False(limiter.ShouldGiveUp());
    }

    [Fact]
    public async Task RateLimiter_GetErrorRateDuringConcurrentReadsAndWrites_NoConcurrencyExceptions()
    {
        var limiter = new RateLimiter(errorWindow: 50);
        var errors = new ConcurrentBag<Exception>();
        var cts = new CancellationTokenSource(TimeSpan.FromMilliseconds(300));

        var writers = Enumerable.Range(0, 8).Select(workerId => Task.Run(() =>
        {
            while (!cts.IsCancellationRequested)
            {
                try
                {
                    if (workerId % 2 == 0)
                        limiter.RecordFailure();
                    else
                        limiter.RecordSuccess();
                }
                catch (Exception ex)
                {
                    errors.Add(ex);
                }
            }
        }));

        var readers = Enumerable.Range(0, 8).Select(_ => Task.Run(() =>
        {
            while (!cts.IsCancellationRequested)
            {
                try
                {
                    var errorRate = limiter.GetErrorRate();
                    if (errorRate is < 0 or > 1)
                        errors.Add(new InvalidOperationException($"Invalid error rate {errorRate}."));
                }
                catch (Exception ex)
                {
                    errors.Add(ex);
                }
            }
        }));

        await Task.WhenAll(writers.Concat(readers));

        Assert.Empty(errors);
    }

    [Fact]
    public async Task RateLimiter_WaitIfNeededAsyncWithPositiveDelay_ActuallyWaits()
    {
        var limiter = new RateLimiter(minDelay: 0.08, maxDelay: 1.0, jitterFactor: 0);
        await limiter.WaitIfNeededAsync();

        var sw = Stopwatch.StartNew();
        await limiter.WaitIfNeededAsync();
        sw.Stop();

        // Use generous lower bound - Task.Delay precision is ~15ms on Windows
        Assert.True(sw.ElapsedMilliseconds >= 50,
            $"Expected at least ~50ms delay, got {sw.ElapsedMilliseconds}ms.");
    }

    [Fact]
    public async Task RateLimiter_RecordFailureWithRetryAfter_SetsDelayToAtLeastRetryAfter()
    {
        var limiter = new RateLimiter(minDelay: 0.01, maxDelay: 5.0, jitterFactor: 0);
        var retryAfter = 0.2;

        var wait = await Task.Run(() => limiter.RecordFailure(retryAfter: retryAfter));

        Assert.True(wait >= retryAfter);
        Assert.True(limiter.CurrentDelay >= retryAfter);
    }

    /// <summary>
    /// Thread-safe max update using Interlocked compare-and-swap loop.
    /// </summary>
    private static void InterlockedMax(ref int location, int value)
    {
        int current;
        do
        {
            current = location;
            if (value <= current) return;
        } while (Interlocked.CompareExchange(ref location, value, current) != current);
    }
}