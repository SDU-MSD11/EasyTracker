package com.sdumsd11.easytracker.data.fake

import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.domain.model.Expense
import com.sdumsd11.easytracker.domain.model.Group
import com.sdumsd11.easytracker.domain.model.Settlement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pins the fakes' mirrors of backend contract guards (pure JVM, no new deps).
 *
 * - Backend derives the payer from the JWT (payer = caller, never blank):
 *   [FakeExpenseRepository] rejects blank payers the same way.
 * - [FakeGroupRepository.addMember] is permissive by design (accepts any id);
 *   the real backend validates via POST /groups/:id/members (404 "user not
 *   found" for unknown emails). This divergence is documented here so no test
 *   mistakes the fake's leniency for contract behavior.
 */
class ContractMirrorTest {

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

    private fun expenseRepo() = FakeExpenseRepository(
        scope = scope,
        notifications = notifications,
        queue = RetryQueue(scope, notifications::notifyRetrySuccess, baseDelayMs = 0L),
        sync = SyncSimulator(latencyMs = 0L),
    )

    private fun groupRepo() = FakeGroupRepository(
        scope = scope,
        notifications = notifications,
        queue = RetryQueue(scope, {}, baseDelayMs = 0L),
        sync = SyncSimulator(latencyMs = 0L),
    )

    @Test
    fun `addExpense rejects blank payer mirroring backend payer-equals-caller`() = runBlocking {
        val result = expenseRepo().addExpense(
            Expense(id = "", groupId = "g1", payerId = "  ", amountCents = 100L, description = "x", splitAmong = listOf("a")),
        )
        assertTrue(result is RequestStatus.Failed)
    }

    @Test
    fun `recordSettlement rejects blank payer via the same guard`() = runBlocking {
        val result = expenseRepo().recordSettlement("g1", Settlement(fromId = "", toId = "anna", amountCents = 500L))
        assertTrue(result is RequestStatus.Failed)
    }

    @Test
    fun `fake addMember is permissive while backend validates unknown emails`() = runBlocking {
        val r = groupRepo()
        val g = (r.createGroup("G", "", "o", emptyList()) as RequestStatus.Success<Group>).data
        // Fake accepts any id without backend validation — the real ApiGroupRepository
        // propagates the backend 404 "user not found" for unknown emails instead.
        assertTrue(r.addMember(g.id, "ghost-unknown-id") is RequestStatus.Success)
    }
}
