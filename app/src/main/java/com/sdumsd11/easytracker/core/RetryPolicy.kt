package com.sdumsd11.easytracker.core

import kotlin.math.pow

/**
 * Durable writer retries live in [com.sdumsd11.easytracker.data.outbox.Outbox]
 * (WorkManager: `CONNECTED` gate + exponential backoff, survives process death;
 * the Hilt worker factory in [com.sdumsd11.easytracker.EasyTrackerApp] serves it).
 * [com.sdumsd11.easytracker.data.fake.RetryQueue] remains the in-memory stand-in
 * for fakes/tests only. Pure in-memory delays ([delayFor]) stay here so VMs can show
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
