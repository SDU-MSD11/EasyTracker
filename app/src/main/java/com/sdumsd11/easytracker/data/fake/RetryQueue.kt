package com.sdumsd11.easytracker.data.fake

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Offline-first sync queue shared by the fake repositories.
 *
 * - `enqueue` returns immediately; work runs on [scope] (never blocks UI).
 * - Ops run with exponential backoff (`baseDelayMs * 2^attempt`, [maxAttempts]).
 * - While [online] is false, ops park in [pendingCount] and flush on reconnect.
 * - [onFlushed] fires on eventual success (fakes pass `notifyRetrySuccess`).
 */
class RetryQueue(
    private val scope: CoroutineScope,
    private val onFlushed: suspend (label: String) -> Unit = {},
    private val baseDelayMs: Long = 1_000L,
    private val maxAttempts: Int = 5,
) {
    @Volatile
    var online: Boolean = true
        private set

    private val lock = Any()
    private val pending = mutableListOf<Pair<String, suspend () -> Boolean>>()
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount

    fun enqueue(label: String, op: suspend () -> Boolean) {
        scope.launch {
            if (!online) {
                park(label, op)
                return@launch
            }
            runWithBackoff(label, op)
        }
    }

    fun setOnline(value: Boolean) {
        online = value
        if (value) scope.launch { flush() }
    }

    private fun park(label: String, op: suspend () -> Boolean) {
        synchronized(lock) { pending += label to op }
        _pendingCount.value = synchronized(lock) { pending.size }
    }

    private suspend fun flush() {
        while (true) {
            val next = synchronized(lock) {
                if (pending.isEmpty()) null else pending.removeAt(0)
            } ?: break
            _pendingCount.value = synchronized(lock) { pending.size }
            runWithBackoff(next.first, next.second)
        }
    }

    private suspend fun runWithBackoff(label: String, op: suspend () -> Boolean) {
        var attempt = 0
        while (true) {
            val ok = try {
                op()
            } catch (_: Exception) {
                false
            }
            if (ok) {
                onFlushed(label)
                return
            }
            if (!online) {
                park(label, op) // went offline mid-retry; resume on reconnect
                return
            }
            if (++attempt >= maxAttempts) return // dropped; local state stays source of truth
            delay(baseDelayMs shl (attempt - 1).coerceAtMost(10))
        }
    }
}

/**
 * Deterministic stand-in for network sync in fakes/tests.
 * Set [failNext] to force that many transient failures before succeeding.
 */
class SyncSimulator(var failNext: Int = 0, private val latencyMs: Long = 250L) {
    suspend fun sync(): Boolean {
        if (failNext > 0) {
            failNext--
            return false
        }
        delay(latencyMs)
        return true
    }
}
