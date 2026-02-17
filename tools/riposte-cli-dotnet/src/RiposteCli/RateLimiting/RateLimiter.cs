namespace RiposteCli.RateLimiting;

/// <summary>
/// Adaptive rate limiter with exponential backoff for API errors.
/// </summary>
public sealed class RateLimiter
{
    private readonly double _minDelay;
    private readonly double _maxDelay;
    private readonly double _baseBackoff;
    private readonly int _maxBackoffAttempts;
    private readonly double _jitterFactor;
    private readonly int _errorWindow;
    private readonly double _errorThreshold;
    private readonly Random _random = new();
    private readonly List<bool> _recentResults = [];
    private readonly object _lock = new();

    private double _currentDelay;
    private double _lastRequestTime;

    public int ConsecutiveFailures { get; private set; }
    public double CurrentDelay => _currentDelay;

    public RateLimiter(
        double minDelay = 1.0,
        double maxDelay = 300.0,
        double baseBackoff = 2.0,
        int maxBackoffAttempts = 8,
        double jitterFactor = 0.25,
        int errorWindow = 10,
        double errorThreshold = 0.3)
    {
        _minDelay = minDelay;
        _maxDelay = maxDelay;
        _baseBackoff = baseBackoff;
        _maxBackoffAttempts = maxBackoffAttempts;
        _jitterFactor = jitterFactor;
        _errorWindow = errorWindow;
        _errorThreshold = errorThreshold;
        _currentDelay = minDelay;
    }

    public void RecordSuccess()
    {
        lock (_lock)
        {
            ConsecutiveFailures = 0;
            _currentDelay = Math.Max(_minDelay, _currentDelay * 0.9);
            AddResult(true);
        }
    }

    public double RecordFailure(double? retryAfter = null, bool isServerError = false)
    {
        lock (_lock)
        {
            ConsecutiveFailures++;
            AddResult(false);

            if (retryAfter.HasValue)
            {
                var waitTime = retryAfter.Value * 1.1;
                _currentDelay = Math.Max(_currentDelay, waitTime);
                return waitTime;
            }

            var exponent = Math.Min(ConsecutiveFailures, _maxBackoffAttempts);
            var baseWait = isServerError
                ? Math.Pow(_baseBackoff, exponent * 0.5)
                : Math.Pow(_baseBackoff, exponent);

            var jitter = baseWait * _jitterFactor * (_random.NextDouble() * 2 - 1);
            var wait = Math.Clamp(baseWait + jitter, _minDelay, _maxDelay);

            _currentDelay = Math.Max(_currentDelay, wait);
            return wait;
        }
    }

    public bool ShouldGiveUp()
    {
        lock (_lock)
        {
            return ConsecutiveFailures >= _maxBackoffAttempts;
        }
    }

    public async Task WaitIfNeededAsync()
    {
        double delayMs;
        lock (_lock)
        {
            var now = GetMonotonicTime();
            var elapsed = now - _lastRequestTime;

            var delay = _currentDelay;
            var errorRate = GetErrorRate();
            if (errorRate > _errorThreshold)
                delay *= 1 + errorRate;

            if (elapsed < delay)
                delayMs = (delay - elapsed) * 1000;
            else
                delayMs = 0;

            _lastRequestTime = GetMonotonicTime() + delayMs / 1000;
        }

        if (delayMs > 0)
            await Task.Delay(TimeSpan.FromMilliseconds(delayMs));
    }

    public double GetErrorRate()
    {
        // Caller must hold _lock or call from within lock
        if (_recentResults.Count == 0) return 0.0;
        var failures = _recentResults.Count(r => !r);
        return (double)failures / _recentResults.Count;
    }

    private void AddResult(bool success)
    {
        _recentResults.Add(success);
        if (_recentResults.Count > _errorWindow)
            _recentResults.RemoveAt(0);
    }

    private static double GetMonotonicTime() =>
        Environment.TickCount64 / 1000.0;
}
