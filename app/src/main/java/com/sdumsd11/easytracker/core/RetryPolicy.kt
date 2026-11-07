package com.sdumsd11.easytracker.core

import kotlin.math.pow

/**
 * Offline-first retry = [RetryQueue][com.sdumsd11.easytracker.data.fake.RetryQueue], not a VM
 * coroutine scope: `enqueue` returns immediately, ops run with exponential backoff, and pending
 * work parks while offline and flushes on reconnect.
 *
 * A WorkManager outbox is deferred (needs `@HiltWorker` wiring + `androidx.hilt:hilt-compiler`
 * ksp dep): it would survive process death/doze, which RetryQueue does not. The Hilt worker
 * factory in [com.sdumsd11.easytracker.EasyTrackerApp] stays so the deferred slice plugs in
 * without touching the app class. Pure in-memory delays ([delayFor]) stay here so VMs can show
 * countdown/attempt UI without depending on a queue.
 */
data class RetryPolicy(
    val maxAttempts: Int = 5,
    val baseDelayMs: Long = 2_000,
    val factor: Double = 2.0,
    val maxDelayMs: Long = 60_000,
) {
    /** Exponential backoff delay before the given 0-based attempt. */
    fun delayFor(attempt: Int): Long =
        (baseDelayMs * factor.pow(attempt.coerceAtLeast(0))).toLong().coerceAtMost(maxDelayMs)

    companion object {
        val Default = RetryPolicy()
    }
}
