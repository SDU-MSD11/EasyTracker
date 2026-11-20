package com.sdumsd11.easytracker.ui.viewmodels

import com.sdumsd11.easytracker.core.RequestStatus
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for the infinite-loading wedge: a refresh pass that HANGS
 * (blackholed backend, no response ever) must settle as a named [RequestStatus.Failed]
 * instead of awaiting forever. The failed pass then clears first paint via the
 * existing gates ([groupsFirstLoad]/[detailFirstLoad] return false for Failed —
 * see [FirstLoadGateTest]) and surfaces through `refreshError`, so the screens'
 * error-first ordering shows Retry instead of spinning.
 *
 * Pure JVM: [boundedRefresh] is a suspend helper with no Android/VM scope, so
 * [runBlocking] suffices (no coroutines-test/Main-dispatcher harness in deps).
 */
class RefreshTimeoutTest {

    @Test
    fun `hung groups pass becomes a named timeout failure`() = runBlocking {
        val result = boundedRefresh("Groups", timeoutMs = 50) {
            delay(5_000)
            RequestStatus.Success(Unit)
        }
        assertTrue(result is RequestStatus.Failed)
        assertEquals("Groups refresh timed out.", (result as RequestStatus.Failed).message)
    }

    @Test
    fun `failed pass keeps its message`() = runBlocking {
        val result = boundedRefresh("Groups", timeoutMs = 1_000) {
            RequestStatus.Failed("offline")
        }
        assertTrue(result is RequestStatus.Failed)
        assertEquals("offline", (result as RequestStatus.Failed).message)
    }

    @Test
    fun `throwing pass surfaces its message`() = runBlocking {
        val result = boundedRefresh("Groups", timeoutMs = 1_000) {
            throw IOException("boom")
        }
        assertTrue(result is RequestStatus.Failed)
        assertEquals("boom", (result as RequestStatus.Failed).message)
    }

    @Test
    fun `successful pass flows through`() = runBlocking {
        val result = boundedRefresh("Groups", timeoutMs = 1_000) {
            RequestStatus.Success(Unit)
        }
        assertTrue(result is RequestStatus.Success)
    }
}
