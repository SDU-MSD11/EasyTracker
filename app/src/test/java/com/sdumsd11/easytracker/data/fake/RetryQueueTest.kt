package com.sdumsd11.easytracker.data.fake

import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Host tests for [RetryQueue]. Pure JVM (no Android, no Robolectric, no new test deps).
 *
 * Determinism: [Dispatchers.Unconfined] runs queued work eagerly and `baseDelayMs = 0`
 * makes backoff a no-op, so background work completes without sleeps. [awaitTrue] only
 * yields to already-scheduled coroutines and fails fast on timeout instead of sleeping.
 */
class RetryQueueTest {

    private lateinit var scope: CoroutineScope
    private lateinit var flushed: MutableList<String>

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        flushed = Collections.synchronizedList(mutableListOf())
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun queue(maxAttempts: Int = 5) =
        RetryQueue(scope, onFlushed = { flushed += it }, baseDelayMs = 0L, maxAttempts = maxAttempts)

    private suspend fun awaitTrue(timeoutMs: Long = 5_000L, condition: () -> Boolean) {
        withTimeout(timeoutMs) { while (!condition()) yield() }
    }

    @Test
    fun `online op succeeds without parking`() = runBlocking {
        val q = queue()
        q.enqueue("op-1") { true }
        awaitTrue { flushed.contains("op-1") }
        assertEquals(0, q.pendingCount.value)
    }

    @Test
    fun `transient failures retry until success`() = runBlocking {
        val q = queue()
        val attempts = AtomicInteger(0)
        q.enqueue("flaky") { attempts.incrementAndGet() >= 3 }
        awaitTrue { flushed.contains("flaky") }
        assertEquals(3, attempts.get())
        assertEquals(0, q.pendingCount.value)
    }

    @Test
    fun `thrown exceptions are retried as failures`() = runBlocking {
        val q = queue()
        val attempts = AtomicInteger(0)
        q.enqueue("throwing") {
            val n = attempts.incrementAndGet()
            if (n < 3) throw IllegalStateException("boom-$n")
            true
        }
        awaitTrue { flushed.contains("throwing") }
        assertEquals(3, attempts.get())
    }

    @Test
    fun `offline ops park and flush in order on reconnect`() = runBlocking {
        val q = queue()
        q.setOnline(false)
        q.enqueue("first") { true }
        q.enqueue("second") { true }
        awaitTrue { q.pendingCount.value == 2 }
        assertTrue(flushed.isEmpty())
        q.setOnline(true)
        awaitTrue { flushed.size == 2 }
        assertEquals(listOf("first", "second"), flushed.toList())
        awaitTrue { q.pendingCount.value == 0 }
    }

    @Test
    fun `persistent failure drops without blocking or parking`() = runBlocking {
        val q = queue(maxAttempts = 3)
        val attempts = AtomicInteger(0)
        // Returns immediately; retries happen in the background, then the op is dropped.
        q.enqueue("doomed") {
            attempts.incrementAndGet()
            false
        }
        awaitTrue { attempts.get() == 3 }
        assertTrue(flushed.isEmpty())
        assertEquals(0, q.pendingCount.value)
    }
}
