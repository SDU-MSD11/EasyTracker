package com.sdumsd11.easytracker.data.fake

import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.domain.model.Balance
import com.sdumsd11.easytracker.domain.model.Expense
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Host tests for [FakeExpenseRepository] offline-first behavior. Pure JVM: no Android
 * framework, no Robolectric, no new test deps (`runBlocking` + [Dispatchers.Unconfined]
 * only; `SyncSimulator(latencyMs = 0)` and `baseDelayMs = 0` keep sync immediate).
 */
class FakeExpenseRepositoryTest {

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

    private fun expense(
        id: String,
        cents: Long = 900L,
        payer: String = "a",
        split: List<String> = listOf("a", "b", "c"),
    ) = Expense(
        id = id,
        groupId = "g1",
        payerId = payer,
        amountCents = cents,
        description = "dinner",
        splitAmong = split,
        createdAt = 1L,
    )

    private fun repo(
        sync: SyncSimulator = SyncSimulator(latencyMs = 0L),
        maxAttempts: Int = 5,
    ) = FakeExpenseRepository(
        scope = scope,
        notifications = notifications,
        queue = RetryQueue(scope, notifications::notifyRetrySuccess, baseDelayMs = 0L, maxAttempts = maxAttempts),
        sync = sync,
    )

    private suspend fun awaitTrue(timeoutMs: Long = 5_000L, condition: () -> Boolean) {
        withTimeout(timeoutMs) { while (!condition()) yield() }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun successExpenses(r: FakeExpenseRepository): List<Expense> =
        (r.observeExpenses("g1").first { it is RequestStatus.Success } as RequestStatus.Success<List<Expense>>).data

    @Test
    fun `observeExpenses emits Pending then Success`() = runBlocking {
        val emissions = repo().observeExpenses("g1").take(2).toList()
        assertTrue(emissions[0] is RequestStatus.Pending)
        assertTrue(emissions[1] is RequestStatus.Success)
        assertEquals(
            emptyList<Expense>(),
            (emissions[1] as RequestStatus.Success<List<Expense>>).data,
        )
    }

    @Test
    fun `offline write applies locally immediately and shows pending`() = runBlocking {
        val r = repo()
        r.setOnline(false)
        val result = r.addExpense(expense("e1"))
        assertTrue(result is RequestStatus.Success)
        awaitTrue { r.pendingSync.value == 1 }
        // Local state is the source of truth: visible before any sync completes.
        assertEquals(listOf("e1"), successExpenses(r).map { it.id })
        val balances = r.observeBalances("g1").first { it is RequestStatus.Success }
            as RequestStatus.Success<List<Balance>>
        assertEquals(
            listOf(Balance("a", 600L), Balance("b", -300L), Balance("c", -300L)),
            balances.data,
        )
        // Nothing flushed yet, so no premature retry-success notification.
        assertTrue(notifications.sentOf("retry-success").isEmpty())
    }

    @Test
    fun `reconnect flushes queue and notifies retry success`() = runBlocking {
        val r = repo()
        r.setOnline(false)
        val added = r.addExpense(expense("e9")) as RequestStatus.Success<Expense>
        awaitTrue { r.pendingSync.value == 1 }
        r.setOnline(true)
        val label = "add-expense:${added.data.id}"
        withTimeout(5_000L) {
            while (notifications.sentOf("retry-success").none { it.message == label }) yield()
        }
        awaitTrue { r.pendingSync.value == 0 }
        assertEquals(listOf("e9"), successExpenses(r).map { it.id })
    }

    @Test
    fun `failed sync never blocks writes and keeps local truth`() = runBlocking {
        val r = repo(sync = SyncSimulator(failNext = Int.MAX_VALUE, latencyMs = 0L), maxAttempts = 2)
        r.setOnline(false)
        val result = r.addExpense(expense("e-fail"))
        assertTrue(result is RequestStatus.Success)
        awaitTrue { r.pendingSync.value == 1 }
        r.setOnline(true)
        // Dropped after maxAttempts: queue drains with no retry-success, expense stays.
        awaitTrue { r.pendingSync.value == 0 }
        assertTrue(notifications.sentOf("retry-success").isEmpty())
        assertEquals(listOf("e-fail"), successExpenses(r).map { it.id })
    }

    @Test
    fun `deleteExpense unknown id fails without throwing`() = runBlocking {
        assertTrue(repo().deleteExpense("missing") is RequestStatus.Failed)
    }
}
