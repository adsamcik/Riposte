using RiposteCli.RateLimiting;
using RiposteCli.Services;

namespace RiposteCli.Tests;

/// <summary>
/// Regression tests for production bugs found during code audit.
/// Each test reproduces the exact scenario that triggered the bug.
/// </summary>
public class BugfixRegressionTests : IDisposable
{
    private readonly string _tempDir;

    public BugfixRegressionTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), $"riposte-bugfix-{Guid.NewGuid()}");
        Directory.CreateDirectory(_tempDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempDir))
            Directory.Delete(_tempDir, true);
    }

    #region Bug 1: DedupeCommand sidecar path uses flat layout instead of subdirectory

    [Fact]
    public void DeleteImageAndSidecar_FindsSidecarInSubdirectory()
    {
        // Arrange: create image and sidecar in the new subdirectory layout
        var imgPath = Path.Combine(_tempDir, "meme.jpg");
        File.WriteAllBytes(imgPath, "fake image"u8.ToArray());

        var sidecarDir = OutputPaths.GetSidecarDir(_tempDir);
        Directory.CreateDirectory(sidecarDir);
        var sidecarPath = Path.Combine(sidecarDir, "meme.jpg.json");
        File.WriteAllText(sidecarPath, """{"emojis": ["😂"]}""");

        // Act: ResolveSidecarPath should find the sidecar in the subdirectory
        var resolved = SidecarService.ResolveSidecarPath(imgPath, _tempDir);

        // Assert: sidecar was found
        Assert.NotNull(resolved);
        Assert.Equal(sidecarPath, resolved);
    }

    [Fact]
    public void DeleteImageAndSidecar_FindsSidecarInLegacyFlatLayout()
    {
        // Arrange: sidecar in legacy flat location
        var imgPath = Path.Combine(_tempDir, "meme.jpg");
        File.WriteAllBytes(imgPath, "fake image"u8.ToArray());

        var flatSidecarPath = Path.Combine(_tempDir, "meme.jpg.json");
        File.WriteAllText(flatSidecarPath, """{"emojis": ["😂"]}""");

        // Act
        var resolved = SidecarService.ResolveSidecarPath(imgPath, _tempDir);

        // Assert: sidecar found at legacy location
        Assert.NotNull(resolved);
        Assert.Equal(flatSidecarPath, resolved);
    }

    [Fact]
    public void DeleteImageAndSidecar_ReturnsNullWhenNoSidecar()
    {
        var imgPath = Path.Combine(_tempDir, "orphan.jpg");
        File.WriteAllBytes(imgPath, "fake image"u8.ToArray());

        var resolved = SidecarService.ResolveSidecarPath(imgPath, _tempDir);
        Assert.Null(resolved);
    }

    #endregion

    #region Bug 3: ConcurrencyLimiter ReduceConcurrency SemaphoreFullException

    [Fact]
    public async Task ReduceConcurrency_WhenAllPermitsHeld_DoesNotDesync()
    {
        // Arrange: all permits held by workers, then rate limit reduces tracked concurrency
        var rl = new RateLimiter(minDelay: 0.001, maxDelay: 0.01, jitterFactor: 0, maxBackoffAttempts: 100);
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4, minConcurrency: 1, rateLimiter: rl);

        // Acquire all 4 permits (simulating 4 active workers)
        for (var i = 0; i < 4; i++)
            await limiter.AcquireAsync();

        // Record rate limit — ReduceConcurrency can't drain a permit since all are held
        // BUG (before fix): _currentConcurrency decrements but semaphore doesn't change
        await limiter.RecordRateLimitAsync(retryAfter: 0.001);

        // Release all 4 permits
        for (var i = 0; i < 4; i++)
            await limiter.ReleaseAsync();

        // Record enough successes to trigger restore
        // BUG (before fix): Release() would exceed semaphore max → SemaphoreFullException
        for (var i = 0; i < 20; i++)
            await limiter.RecordSuccessAsync();

        // If we got here without SemaphoreFullException, the bug is fixed
        Assert.True(limiter.CurrentConcurrency <= 4);
    }

    [Fact]
    public async Task ReduceConcurrency_MultipleReducesWhileAllHeld_NoException()
    {
        // Stress test: multiple consecutive rate limits while all permits are held
        var rl = new RateLimiter(minDelay: 0.001, maxDelay: 0.01, jitterFactor: 0, maxBackoffAttempts: 100);
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4, minConcurrency: 1, rateLimiter: rl);

        // Hold all permits
        for (var i = 0; i < 4; i++)
            await limiter.AcquireAsync();

        // Multiple rate limits (each tries to reduce concurrency but can't drain permits)
        for (var i = 0; i < 3; i++)
            await limiter.RecordRateLimitAsync(retryAfter: 0.001);

        // Release all
        for (var i = 0; i < 4; i++)
            await limiter.ReleaseAsync();

        // Many successes to trigger restore attempts
        // BUG (before fix): each restore Release() could exceed max
        for (var i = 0; i < 50; i++)
            await limiter.RecordSuccessAsync();

        Assert.True(limiter.CurrentConcurrency >= 1);
        Assert.True(limiter.CurrentConcurrency <= 4);
    }

    [Fact]
    public async Task ReduceConcurrency_WhenPermitsAvailable_ActuallyReduces()
    {
        // Verify the fix doesn't break the normal case where permits ARE available
        var rl = new RateLimiter(minDelay: 0.001, maxDelay: 0.01, jitterFactor: 0);
        var limiter = new ConcurrencyLimiter(maxConcurrency: 4, minConcurrency: 1, rateLimiter: rl);

        // No permits held → ReduceConcurrency should drain one
        await limiter.RecordRateLimitAsync(retryAfter: 0.001);

        Assert.Equal(3, limiter.CurrentConcurrency);
    }

    #endregion

    #region Bug 4: RateLimiter.GetErrorRate thread safety

    [Fact]
    public void GetErrorRate_CalledConcurrently_NoException()
    {
        var rl = new RateLimiter(minDelay: 0.001);

        // Seed some results
        rl.RecordSuccess();
        rl.RecordFailure();
        rl.RecordSuccess();

        // Concurrent reads and writes — before fix, this could throw
        // InvalidOperationException due to collection modified during enumeration
        var exceptions = new List<Exception>();
        var barrier = new Barrier(participantCount: 3);

        var tasks = Enumerable.Range(0, 3).Select(i => Task.Run(() =>
        {
            barrier.SignalAndWait();
            try
            {
                for (var j = 0; j < 100; j++)
                {
                    if (j % 3 == 0)
                        rl.RecordSuccess();
                    else if (j % 3 == 1)
                        rl.RecordFailure();
                    else
                        _ = rl.GetErrorRate();
                }
            }
            catch (Exception ex)
            {
                lock (exceptions)
                    exceptions.Add(ex);
            }
        })).ToArray();

        Task.WaitAll(tasks);

        Assert.Empty(exceptions);
    }

    [Fact]
    public void GetErrorRate_EmptyResults_ReturnsZero()
    {
        var rl = new RateLimiter();
        Assert.Equal(0.0, rl.GetErrorRate());
    }

    [Fact]
    public void GetErrorRate_AllFailures_ReturnsOne()
    {
        var rl = new RateLimiter();
        rl.RecordFailure();
        rl.RecordFailure();
        rl.RecordFailure();
        Assert.Equal(1.0, rl.GetErrorRate());
    }

    [Fact]
    public void GetErrorRate_MixedResults_ReturnsCorrectRate()
    {
        var rl = new RateLimiter(errorWindow: 4);
        rl.RecordSuccess();
        rl.RecordFailure();
        rl.RecordSuccess();
        rl.RecordFailure();
        Assert.Equal(0.5, rl.GetErrorRate());
    }

    #endregion
}
