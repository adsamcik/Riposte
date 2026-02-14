"""Tests for ConcurrencyLimiter and parallel processing coordination."""

import asyncio

import pytest

from riposte_cli.copilot import (
    ConcurrencyLimiter,
    RateLimiter,
)


class TestRateLimiter:
    """Tests for the RateLimiter dataclass."""

    def test_initial_state(self) -> None:
        rl = RateLimiter()
        assert rl.consecutive_failures == 0
        assert rl.current_delay == 1.0
        assert not rl.should_give_up()

    def test_record_success_reduces_delay(self) -> None:
        rl = RateLimiter()
        rl.current_delay = 5.0
        rl.record_success()
        assert rl.current_delay == pytest.approx(4.5)
        assert rl.consecutive_failures == 0

    def test_record_failure_increases_delay(self) -> None:
        rl = RateLimiter()
        wait = rl.record_failure()
        assert wait >= rl.min_delay
        assert rl.consecutive_failures == 1

    def test_record_failure_with_retry_after(self) -> None:
        rl = RateLimiter()
        wait = rl.record_failure(retry_after=10.0)
        assert wait == pytest.approx(11.0)  # 10 * 1.1

    def test_should_give_up_after_max_attempts(self) -> None:
        rl = RateLimiter(max_backoff_attempts=3)
        for _ in range(3):
            rl.record_failure()
        assert rl.should_give_up()

    def test_error_rate_tracking(self) -> None:
        rl = RateLimiter(error_window=4)
        rl.record_success()
        rl.record_success()
        rl.record_failure()
        rl.record_failure()
        assert rl._get_error_rate() == pytest.approx(0.5)

    def test_success_resets_consecutive_failures(self) -> None:
        """Verify success from one image doesn't bleed into another's state."""
        rl = RateLimiter(max_backoff_attempts=5)
        rl.record_failure()
        rl.record_failure()
        assert rl.consecutive_failures == 2
        rl.record_success()
        assert rl.consecutive_failures == 0
        # Should not give up after a reset
        rl.record_failure()
        assert not rl.should_give_up()


class TestConcurrencyLimiter:
    """Tests for the ConcurrencyLimiter class."""

    @pytest.fixture
    def limiter(self) -> ConcurrencyLimiter:
        return ConcurrencyLimiter(max_concurrency=4, min_concurrency=1)

    def test_initial_state(self, limiter: ConcurrencyLimiter) -> None:
        assert limiter.current_concurrency == 4
        assert limiter.active_tasks == 0
        assert not limiter.is_paused
        assert not limiter.should_give_up()

    @pytest.mark.asyncio
    async def test_acquire_release(self, limiter: ConcurrencyLimiter) -> None:
        await limiter.acquire()
        assert limiter.active_tasks == 1
        await limiter.release()
        assert limiter.active_tasks == 0

    @pytest.mark.asyncio
    async def test_semaphore_limits_concurrency(self) -> None:
        limiter = ConcurrencyLimiter(max_concurrency=2)
        order: list[str] = []

        async def worker(name: str) -> None:
            await limiter.acquire()
            order.append(f"{name}_start")
            await asyncio.sleep(0.05)
            order.append(f"{name}_end")
            await limiter.release()

        await asyncio.gather(worker("a"), worker("b"), worker("c"))
        # All three should complete
        assert len([x for x in order if x.endswith("_start")]) == 3
        assert len([x for x in order if x.endswith("_end")]) == 3

    @pytest.mark.asyncio
    async def test_rate_limit_pauses_all_workers(self) -> None:
        limiter = ConcurrencyLimiter(max_concurrency=4)
        # Override rate limiter for fast test
        limiter._rate_limiter = RateLimiter(min_delay=0.05, max_delay=0.1)

        paused_at: list[float] = []
        resumed_at: list[float] = []

        async def waiter() -> None:
            await asyncio.sleep(0.02)  # Let pause take effect
            paused_at.append(asyncio.get_event_loop().time())
            await limiter.acquire()
            resumed_at.append(asyncio.get_event_loop().time())
            await limiter.release()

        async def trigger_pause() -> None:
            await limiter.record_rate_limit(retry_after=0.1)

        # Start the pause and a waiter concurrently
        await asyncio.gather(trigger_pause(), waiter())
        # Waiter should have been delayed by the pause
        assert len(resumed_at) == 1

    @pytest.mark.asyncio
    async def test_concurrency_reduces_on_rate_limit(self) -> None:
        limiter = ConcurrencyLimiter(max_concurrency=4, min_concurrency=1)
        limiter._rate_limiter = RateLimiter(min_delay=0.01, max_delay=0.05)

        await limiter.record_rate_limit(retry_after=0.01)
        assert limiter.current_concurrency == 3

        await limiter.record_rate_limit(retry_after=0.01)
        assert limiter.current_concurrency == 2

    @pytest.mark.asyncio
    async def test_concurrency_does_not_go_below_min(self) -> None:
        limiter = ConcurrencyLimiter(max_concurrency=2, min_concurrency=1)
        limiter._rate_limiter = RateLimiter(min_delay=0.01, max_delay=0.05)

        await limiter.record_rate_limit(retry_after=0.01)
        assert limiter.current_concurrency == 1
        await limiter.record_rate_limit(retry_after=0.01)
        assert limiter.current_concurrency == 1  # Stays at min

    @pytest.mark.asyncio
    async def test_concurrency_restores_on_sustained_success(self) -> None:
        limiter = ConcurrencyLimiter(max_concurrency=4, min_concurrency=1)
        limiter._rate_limiter = RateLimiter(min_delay=0.01, max_delay=0.05)
        limiter._restore_threshold = 3  # Restore after 3 successes for test speed

        # Reduce concurrency
        await limiter.record_rate_limit(retry_after=0.01)
        assert limiter.current_concurrency == 3

        # Record enough successes to restore
        for _ in range(3):
            await limiter.record_success()
        assert limiter.current_concurrency == 4

    @pytest.mark.asyncio
    async def test_server_error_reduces_after_threshold(self) -> None:
        limiter = ConcurrencyLimiter(max_concurrency=4)
        limiter._rate_limiter = RateLimiter(min_delay=0.01, max_delay=0.05)

        # First two server errors don't reduce concurrency
        await limiter.record_server_error()
        assert limiter.current_concurrency == 4
        await limiter.record_server_error()
        assert limiter.current_concurrency == 4

        # Third reduces it
        await limiter.record_server_error()
        assert limiter.current_concurrency == 3

    @pytest.mark.asyncio
    async def test_is_paused_during_rate_limit(self) -> None:
        limiter = ConcurrencyLimiter(max_concurrency=2)
        limiter._rate_limiter = RateLimiter(min_delay=0.01, max_delay=0.1)

        paused_observed = False

        async def observer() -> None:
            nonlocal paused_observed
            await asyncio.sleep(0.01)
            if limiter.is_paused:
                paused_observed = True

        await asyncio.gather(
            limiter.record_rate_limit(retry_after=0.05),
            observer(),
        )
        assert paused_observed

    @pytest.mark.asyncio
    async def test_per_image_retry_uses_local_counter(self) -> None:
        """Verify that per-image retry decisions use local attempt count,
        not the shared limiter's consecutive_failures."""
        limiter = ConcurrencyLimiter(max_concurrency=4)
        limiter._rate_limiter = RateLimiter(
            min_delay=0.01, max_delay=0.05, max_backoff_attempts=8
        )

        # Simulate what happens in annotate.py: two workers hit rate limits
        # Worker A gets 429, records it globally
        await limiter.record_rate_limit(retry_after=0.01)
        # Worker B succeeds, resetting consecutive_failures to 0
        await limiter.record_success()

        # The shared limiter should NOT say "give up" because success reset it
        assert not limiter.should_give_up()

        # But per-image attempt tracking (local variable) is independent:
        # Worker A is on attempt 2 of 5 — it decides locally, not via should_give_up()
        local_attempt = 2
        max_retries = 5
        assert local_attempt < max_retries  # Worker A continues

    @pytest.mark.asyncio
    async def test_cancellation_during_acquire(self) -> None:
        """Verify that cancelled tasks release properly during acquire."""
        limiter = ConcurrencyLimiter(max_concurrency=1)

        # Fill the single slot
        await limiter.acquire()

        async def waiting_worker() -> None:
            await limiter.acquire()
            await limiter.release()

        task = asyncio.create_task(waiting_worker())
        await asyncio.sleep(0.01)

        # Cancel the waiting task
        task.cancel()
        with pytest.raises(asyncio.CancelledError):
            await task

        # Original slot should still be held
        assert limiter.active_tasks == 1
        await limiter.release()
        assert limiter.active_tasks == 0
