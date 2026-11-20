package com.sdumsd11.easytracker.data

import com.sdumsd11.easytracker.core.RequestStatus
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host tests for [RefreshSingleFlight] (blocker 1: entry refreshes rely on the
 * entry-VM debounce + repo single-flight so N collectors share one network pass).
 * Pure JVM: `runBlocking` only, no new test deps.
 *
 * NB: the gate is named `tryRun`, not `run` — a member named `run` loses
 * paren-less trailing-lambda calls (`gate.run { … }`) to the stdlib `run` scope
 * function, which silently runs the block unthrottled instead of gating it.
 */
class RefreshSingleFlightTest {

    @Test
    fun concurrentRunsCollapseToOne() = runBlocking {
        val gate = RefreshSingleFlight(minIntervalMs = 0L)
        val entered = AtomicInteger(0)
        val release = CompletableDeferred<Unit>()
        val first = async(Dispatchers.Default) {
            gate.tryRun {
                entered.incrementAndGet()
                release.await()
                true
            }
        }
        // Wait until the first pass is genuinely in flight before racing it.
        withTimeout(5_000L) { while (entered.get() == 0) delay(1L) }
        val second = async(Dispatchers.Default) {
            gate.tryRun {
                entered.incrementAndGet()
                release.await()
                true
            }
        }
        second.join()
        release.complete(Unit)
        first.join()
        assertEquals(1, entered.get())
    }

    @Test
    fun keysRefreshIndependentlyButThrottleRepeats() = runBlocking {
        val gates = KeyedRefreshSingleFlight(minIntervalMs = 60_000L)
        val ran = AtomicInteger(0)
        gates.tryRun("a", block = { ran.incrementAndGet(); true })
        gates.tryRun("b", block = { ran.incrementAndGet(); true })
        assertEquals(2, ran.get())
        // Same key inside the throttle window: skipped, still 2.
        gates.tryRun("a", block = { ran.incrementAndGet(); true })
        assertEquals(2, ran.get())
        // Zero-throttle gates always run.
        val fresh = KeyedRefreshSingleFlight(minIntervalMs = 0L)
        fresh.tryRun("a", block = { ran.incrementAndGet(); true })
        yield()
        fresh.tryRun("a", block = { ran.incrementAndGet(); true })
        assertEquals(4, ran.get())
    }

    @Test
    fun explicitForceBypassesThrottleWhileBackgroundStaysThrottled() = runBlocking {
        val gate = RefreshSingleFlight(minIntervalMs = 60_000L)
        val ran = AtomicInteger(0)
        gate.tryRun { ran.incrementAndGet(); true }
        // Background repeat inside the window: skipped.
        gate.tryRun { ran.incrementAndGet(); true }
        assertEquals(1, ran.get())
        // Explicit refresh forces a fresh pass.
        gate.tryRun(force = true, block = { ran.incrementAndGet(); true })
        assertEquals(2, ran.get())
    }

    @Test
    fun failureResetsThrottleSoRetryReruns() = runBlocking {
        val gate = RefreshSingleFlight(minIntervalMs = 60_000L)
        val ran = AtomicInteger(0)
        assertEquals(false, gate.tryRun { ran.incrementAndGet(); false })
        // Failed pass did not arm the throttle: immediate retry re-runs.
        assertEquals(false, gate.tryRun { ran.incrementAndGet(); false })
        assertEquals(2, ran.get())
    }

    @Test
    fun throttledSkipsCountAsFresh() = runBlocking {
        val gate = RefreshSingleFlight(minIntervalMs = 60_000L)
        assertEquals(true, gate.tryRun { true })
        // Skipped (block never runs, so its `false` is never observed): still true.
        assertEquals(true, gate.tryRun { false })
    }

    @Test
    fun statusCarriesFailureReason() = runBlocking {
        val gate = RefreshSingleFlight(minIntervalMs = 0L)
        val failed = gate.tryRunStatus { RequestStatus.Failed("boom") }
        assertTrue(failed is RequestStatus.Failed)
        assertEquals("boom", (failed as RequestStatus.Failed).message)
        assertTrue(gate.tryRunStatus { RequestStatus.Success(Unit) } is RequestStatus.Success)
    }

    @Test
    fun statusThrowsBecomeFailedWithMessage() = runBlocking {
        val gate = RefreshSingleFlight(minIntervalMs = 0L)
        val result = gate.tryRunStatus { throw IllegalStateException("cache write failed") }
        assertTrue(result is RequestStatus.Failed)
        assertEquals("cache write failed", (result as RequestStatus.Failed).message)
    }

    @Test
    fun statusCollapsedPassCountsAsFresh() = runBlocking {
        val gate = RefreshSingleFlight(minIntervalMs = 0L)
        val entered = AtomicInteger(0)
        val release = CompletableDeferred<Unit>()
        val first = async(Dispatchers.Default) {
            gate.tryRunStatus {
                entered.incrementAndGet()
                release.await()
                RequestStatus.Success(Unit)
            }
        }
        withTimeout(5_000L) { while (entered.get() == 0) delay(1L) }
        // Collapsed onto the in-flight pass: fresh, block ran once.
        val second = async(Dispatchers.Default) {
            gate.tryRunStatus { RequestStatus.Failed("never runs") }
        }
        assertTrue(second.await() is RequestStatus.Success)
        release.complete(Unit)
        assertTrue(first.await() is RequestStatus.Success)
        assertEquals(1, entered.get())
    }

    @Test
    fun keyedStatusRefreshesIndependently() = runBlocking {
        val gates = KeyedRefreshSingleFlight(minIntervalMs = 0L)
        assertTrue(gates.tryRunStatus("a") { RequestStatus.Success(Unit) } is RequestStatus.Success)
        val failed = gates.tryRunStatus("b") { RequestStatus.Failed("no route") }
        assertEquals("no route", (failed as RequestStatus.Failed).message)
    }
}
