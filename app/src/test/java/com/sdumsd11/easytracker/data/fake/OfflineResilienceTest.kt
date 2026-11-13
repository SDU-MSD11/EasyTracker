package com.sdumsd11.easytracker.data.fake

import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.domain.model.Settlement
import com.sdumsd11.easytracker.domain.model.User
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

/**
 * Executable NFR/QA verification for the offline acceptance checks.
 *
 * Maps to manual checks 1/2/3/5 (see report): auto-retry on reconnect, non-blocking
 * writes while offline, cached reads while offline, retry-success notification.
 * Check 4 (processing screen) is covered by the instrumented [PaymentStatusTest];
 * VM-level status emission needs a Main dispatcher and stays manual/unit-out-of-scope.
 *
 * Pure JVM, no new deps: [Dispatchers.Unconfined] + zero-delay queue/sync makes
 * background work eager; [awaitTrue] only yields to scheduled coroutines, no sleeps.
 */
class OfflineResilienceTest {

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

    private fun queue() = RetryQueue(scope, notifications::notifyRetrySuccess, baseDelayMs = 0L)

    private suspend fun awaitTrue(timeoutMs: Long = 5_000L, condition: () -> Boolean) {
        withTimeout(timeoutMs) { while (!condition()) yield() }
    }

    private fun groupRepo(sync: SyncSimulator = SyncSimulator(latencyMs = 0L)) =
        FakeGroupRepository(scope, notifications, queue(), sync)

    private fun expenseRepo(sync: SyncSimulator = SyncSimulator(latencyMs = 0L)) =
        FakeExpenseRepository(scope, notifications, queue(), sync)

    private fun userRepo(sync: SyncSimulator = SyncSimulator(latencyMs = 0L)) =
        FakeUserRepository(
            scope = scope,
            notifications = notifications,
            queue = queue(),
            sync = sync,
        )

    // Check 1: disconnect -> request parks -> auto-retries when connection returns.
    @Test
    fun `offline group write parks and auto-retries on reconnect`() = runBlocking {
        val repo = groupRepo()
        repo.setOnline(false)

        val created = repo.createGroup("Flatmates", "rent", "me", listOf("anna"))
        assertTrue(created is RequestStatus.Success)
        awaitTrue { repo.pendingSync.value == 1 }
        assertTrue(notifications.sentOf("retry-success").isEmpty())

        repo.setOnline(true)
        val id = (created as RequestStatus.Success).data.id
        awaitTrue { notifications.sentOf("retry-success").any { it.message == "create-group:$id" } }
        awaitTrue { repo.pendingSync.value == 0 }
    }

    // Check 1 variant: transient failures before success still converge after reconnect.
    @Test
    fun `flaky sync converges after reconnect`() = runBlocking {
        val repo = expenseRepo(SyncSimulator(failNext = 2, latencyMs = 0L))
        repo.setOnline(false)
        val result = repo.recordSettlement("g1", Settlement("me", "anna", 500L))
        assertTrue(result is RequestStatus.Success)
        repo.setOnline(true)
        awaitTrue { notifications.sentOf("retry-success").isNotEmpty() }
        awaitTrue { repo.pendingSync.value == 0 }
    }

    // Check 2: force a request fail (airplane mode) -> send-money flow doesn't block.
    @Test
    fun `send-money returns immediately while offline and balance updates locally`() = runBlocking {
        val repo = expenseRepo()
        repo.setOnline(false)

        // Returns Success without touching the network: no throw, no suspension on sync.
        val result = repo.recordSettlement("g1", Settlement(fromId = "me", toId = "anna", amountCents = 500L))
        assertTrue(result is RequestStatus.Success)
        awaitTrue { repo.pendingSync.value == 1 }

        // Local state is the source of truth: balances derive from the cached expense.
        val balances = repo.observeBalances("g1").first { it is RequestStatus.Success } as RequestStatus.Success
        assertTrue(balances.data.any { it.netCents != 0L })
        val expenses = repo.observeExpenses("g1").first { it is RequestStatus.Success } as RequestStatus.Success
        assertEquals(1, expenses.data.size)
    }

    // Check 2 variant (profile-save touched flow): save applies locally while offline.
    @Test
    fun `profile save applies locally while offline and syncs on reconnect`() = runBlocking {
        val repo = userRepo()
        val me = User("me", "Me", "me@x.y")
        assertTrue(repo.saveCurrentProfile(me) is RequestStatus.Success)

        repo.setOnline(false)
        assertTrue(repo.saveCurrentProfile(me.copy(name = "Me Updated")) is RequestStatus.Success)
        val current = repo.observeCurrentProfile().first { it is RequestStatus.Success } as RequestStatus.Success
        assertEquals("Me Updated", current.data!!.name)

        repo.setOnline(true)
        awaitTrue { notifications.sentOf("retry-success").any { it.message == "upsert-user:me" } }
    }

    // Check 3: internet off -> previously retrieved data still visible.
    @Test
    fun `cached group list stays visible while offline`() = runBlocking {
        val repo = groupRepo()
        val created = repo.createGroup("Flatmates", "rent", "me", emptyList())
        assertTrue(created is RequestStatus.Success)

        repo.setOnline(false)
        val cached = repo.observeGroups().first { it is RequestStatus.Success } as RequestStatus.Success
        assertEquals(listOf("Flatmates"), cached.data.map { it.name })
    }

    // Check 5: popup/sound hook fires when a previously failed request succeeds in background.
    @Test
    fun `retry-success notification recorded after background flush`() = runBlocking {
        val repo = groupRepo()
        repo.setOnline(false)
        val created = repo.createGroup("Trip", "travel", "me", emptyList()) as RequestStatus.Success
        repo.setOnline(true)

        awaitTrue { notifications.sentOf("retry-success").any { it.message == "create-group:${created.data.id}" } }
    }
}
