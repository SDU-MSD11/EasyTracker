package com.sdumsd11.easytracker.data.fake

import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.domain.model.Expense
import com.sdumsd11.easytracker.domain.model.Settlement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Settlement path for [FakeExpenseRepository]: mirror expense + debt notifications. */
class FakeExpenseSettlementTest {

    private lateinit var scope: CoroutineScope
    private lateinit var notifications: FakeNotificationService

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        notifications = FakeNotificationService()
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun repo() = FakeExpenseRepository(
        scope = scope,
        notifications = notifications,
        queue = RetryQueue(scope, notifications::notifyRetrySuccess, baseDelayMs = 0L),
        sync = SyncSimulator(latencyMs = 0L),
    )

    @Test
    fun `recordSettlement stores payer-from split-to mirror`() = runBlocking {
        val r = repo()
        val res = r.recordSettlement("g1", Settlement("a", "b", 500L))
        assertTrue(res is RequestStatus.Success)
        val stored = (res as RequestStatus.Success<Expense>).data
        assertEquals("a", stored.payerId)
        assertEquals(listOf("b"), stored.splitAmong)
        assertEquals("Settlement", stored.description)
        assertTrue(stored.isSettlement)
        val all = r.observeExpenses("g1").first { it is RequestStatus.Success } as RequestStatus.Success
        assertEquals(listOf(stored.id), all.data.map { it.id })
    }

    @Test
    fun `recordSettlement notifies both parties debt update`() = runBlocking {
        val r = repo()
        r.recordSettlement("g1", Settlement("a", "b", 500L))
        withTimeout(5_000L) {
            while (notifications.sentOf("debt-update").size < 2) yield()
        }
        val ids = notifications.sentOf("debt-update").map { it.toUserId }.toSet()
        assertEquals(setOf("a", "b"), ids)
        assertEquals(1, notifications.sentOf("new-expense").size)
    }

    @Test
    fun `expense invariant rejects negative and blank split`() = runBlocking {
        runCatching { Expense("neg", "g1", "a", -1L, "d", listOf("a")) }
            .onSuccess { throw AssertionError("expected require() to fail") }
        runCatching { Expense("e", "g1", "a", 0L, "d", emptyList()) }
            .onSuccess { throw AssertionError("expected require() to fail") }
        val r = repo()
        val blankId = r.addExpense(Expense("", "g1", "a", 10L, "d", listOf("a")))
        assertTrue(blankId is RequestStatus.Success)
        assertTrue((blankId as RequestStatus.Success<Expense>).data.id.isNotBlank())
    }
}
