namespace RiposteCli.RateLimiting;

/// <summary>
/// Manages concurrent API requests with adaptive backpressure.
/// Coordinates multiple async workers via a SemaphoreSlim and a global pause event.
/// </summary>
public sealed class ConcurrencyLimiter
{
    private readonly int _maxConcurrency;
    private readonly int _minConcurrency;
    private readonly SemaphoreSlim _semaphore;
    private readonly SemaphoreSlim _greenLight = new(1, 1); // Acts as async ManualResetEvent
    private readonly RateLimiter _rateLimiter;
    private readonly SemaphoreSlim _lockObj = new(1, 1);
    private readonly int _restoreThreshold;

    private int _currentConcurrency;
    private int _activeTasks;
    private int _successesSinceLastReduce;
    private double _pauseUntil;
    private volatile bool _isPaused;

    public int CurrentConcurrency => _currentConcurrency;
    public int ActiveTasks => _activeTasks;
    public bool IsPaused => _isPaused;
    public RateLimiter RateLimiter => _rateLimiter;

    public ConcurrencyLimiter(
        int maxConcurrency = 4,
        int minConcurrency = 1,
        RateLimiter? rateLimiter = null,
        int restoreThreshold = 10)
    {
        _maxConcurrency = maxConcurrency;
        _minConcurrency = minConcurrency;
        _currentConcurrency = maxConcurrency;
        _semaphore = new SemaphoreSlim(maxConcurrency, maxConcurrency);
        _rateLimiter = rateLimiter ?? new RateLimiter();
        _restoreThreshold = restoreThreshold;
    }

    public async Task AcquireAsync()
    {
        // Wait for pause to clear
        while (_isPaused)
            await Task.Delay(10);

        await _semaphore.WaitAsync();
        await _lockObj.WaitAsync();
        try
        {
            _activeTasks++;
        }
        finally
        {
            _lockObj.Release();
        }
    }

    public async Task ReleaseAsync()
    {
        _semaphore.Release();
        await _lockObj.WaitAsync();
        try
        {
            _activeTasks = Math.Max(0, _activeTasks - 1);
        }
        finally
        {
            _lockObj.Release();
        }
    }

    public async Task RecordSuccessAsync()
    {
        await _lockObj.WaitAsync();
        try
        {
            _rateLimiter.RecordSuccess();
            _successesSinceLastReduce++;

            if (_successesSinceLastReduce >= _restoreThreshold
                && _currentConcurrency < _maxConcurrency)
            {
                _currentConcurrency++;
                _semaphore.Release(); // Add a slot
                _successesSinceLastReduce = 0;
            }
        }
        finally
        {
            _lockObj.Release();
        }
    }

    public async Task<double> RecordRateLimitAsync(double? retryAfter = null)
    {
        double waitTime;
        await _lockObj.WaitAsync();
        try
        {
            waitTime = _rateLimiter.RecordFailure(retryAfter: retryAfter);
            _successesSinceLastReduce = 0;
            ReduceConcurrency();

            var now = GetMonotonicTime();
            var newPauseUntil = now + waitTime;
            if (newPauseUntil > _pauseUntil)
            {
                _pauseUntil = newPauseUntil;
            }
            else
            {
                waitTime = Math.Max(0, _pauseUntil - now);
            }
        }
        finally
        {
            _lockObj.Release();
        }

        _isPaused = true;
        await Task.Delay(TimeSpan.FromSeconds(waitTime));

        await _lockObj.WaitAsync();
        try
        {
            if (GetMonotonicTime() >= _pauseUntil)
                _isPaused = false;
        }
        finally
        {
            _lockObj.Release();
        }

        return waitTime;
    }

    public async Task<double> RecordServerErrorAsync()
    {
        double waitTime;
        await _lockObj.WaitAsync();
        try
        {
            waitTime = _rateLimiter.RecordFailure(isServerError: true);
            _successesSinceLastReduce = 0;
            if (_rateLimiter.ConsecutiveFailures >= 3)
                ReduceConcurrency();
        }
        finally
        {
            _lockObj.Release();
        }

        return waitTime;
    }

    public bool ShouldGiveUp() => _rateLimiter.ShouldGiveUp();

    private void ReduceConcurrency()
    {
        if (_currentConcurrency > _minConcurrency)
        {
            _currentConcurrency--;
            // Try to drain one permit
            if (_semaphore.CurrentCount > 0)
                _semaphore.Wait(0);
        }
    }

    private static double GetMonotonicTime() =>
        Environment.TickCount64 / 1000.0;
}
