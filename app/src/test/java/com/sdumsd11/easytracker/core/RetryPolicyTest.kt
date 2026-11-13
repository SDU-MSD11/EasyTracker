package com.sdumsd11.easytracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Table-driven backoff: exponential growth, clamped at maxDelayMs. */
class RetryPolicyTest {

    @Test
    fun `delayFor grows exponentially then caps`() {
        val p = RetryPolicy(baseDelayMs = 2_000, factor = 2.0, maxDelayMs = 60_000)
        assertEquals(2_000L, p.delayFor(0))
        assertEquals(4_000L, p.delayFor(1))
        assertEquals(8_000L, p.delayFor(2))
        assertEquals(60_000L, p.delayFor(10))
        assertEquals(60_000L, p.delayFor(100))
    }

    @Test
    fun `negative attempt clamps to zero`() {
        val p = RetryPolicy(baseDelayMs = 1_000, factor = 2.0, maxDelayMs = 60_000)
        assertEquals(1_000L, p.delayFor(-1))
    }

    @Test
    fun `default matches documented offline-first cadence`() {
        assertEquals(5, RetryPolicy.Default.maxAttempts)
        assertEquals(2_000L, RetryPolicy.Default.baseDelayMs)
        assertTrue(RetryPolicy.Default.maxDelayMs >= RetryPolicy.Default.baseDelayMs)
    }
}
