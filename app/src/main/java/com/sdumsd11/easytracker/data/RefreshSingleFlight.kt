package com.sdumsd11.easytracker.data

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Collapses concurrent refresh triggers into one in-flight run (plus a short repeat
 * throttle), so N collectors/recompositions share a single background network pass.
 * Triggers never block collection — cache flows stay instant.
 *
 * Blocks report success. Explicit refreshes pass `force = true` to bypass the
 * throttle (background passes stay throttled); in-flight passes still collapse onto
 * each other so an explicit retry never opens a duplicate socket. Throttled/in-flight
 * skips count as fresh (`true`); failures reset the throttle so an immediate retry
 * re-runs instead of being swallowed.
 *
 * Named `tryRun` (not `run`) on purpose: a member named `run` loses paren-less
 * trailing-lambda calls (`gate.run { … }`) to the stdlib `run` scope function,
 * which silently runs the block unthrottled instead of gating it.
 */
internal class RefreshSingleFlight(private val minIntervalMs: Long = 5_000) {
    private val mutex = Mutex()
    private var inFlight = false
    private var lastStartMs = 0L

    /** Runs [block] unless one is already in flight or inside the throttle window. */
    suspend fun tryRun(force: Boolean = false, block: suspend () -> Boolean): Boolean {
        val go = mutex.withLock {
            val now = System.currentTimeMillis()
            if (inFlight || (!force && now - lastStartMs < minIntervalMs)) return@withLock false
            inFlight = true
            lastStartMs = now
            true
        }
        if (!go) return true
        val ok = try {
            block()
        } catch (_: Exception) {
            false
        }
        mutex.withLock {
            inFlight = false
            if (!ok) lastStartMs = 0L
        }
        return ok
    }

    /** Fire-and-forget [tryRun] from a cold-flow `onStart`: never suspends the collector. */
    fun launchIn(scope: CoroutineScope, block: suspend () -> Boolean) {
        scope.launch { runCatching { tryRun(block = block) } }
    }
}

/** Per-key [RefreshSingleFlight] (per-group detail/expense refreshes dedupe independently). */
internal class KeyedRefreshSingleFlight(private val minIntervalMs: Long = 5_000) {
    private val gates = ConcurrentHashMap<String, RefreshSingleFlight>()

    fun launchIn(scope: CoroutineScope, key: String, block: suspend () -> Boolean) {
        gates.getOrPut(key) { RefreshSingleFlight(minIntervalMs) }.launchIn(scope, block)
    }

    /** Suspending [RefreshSingleFlight.tryRun]: awaits the pass instead of fire-and-forget. */
    suspend fun tryRun(key: String, force: Boolean = false, block: suspend () -> Boolean): Boolean =
        gates.getOrPut(key) { RefreshSingleFlight(minIntervalMs) }.tryRun(force, block)
}
