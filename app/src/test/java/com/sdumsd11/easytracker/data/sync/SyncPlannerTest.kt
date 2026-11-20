package com.sdumsd11.easytracker.data.sync

import com.sdumsd11.easytracker.domain.model.Expense
import com.sdumsd11.easytracker.domain.model.Group
import com.sdumsd11.easytracker.domain.model.InviteStatus
import com.sdumsd11.easytracker.domain.model.NotificationPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [planSyncNotifications]: cached-vs-server diff, per-kind
 * pref gating, own-action suppression, and per-run burst capping.
 */
class SyncPlannerTest {

    private val allOn = NotificationPrefs(newExpense = true, reminders = true, settlementUpdates = true)

    private fun expense(
        id: String,
        payer: String = "bob",
        group: String = "g1",
        settlement: Boolean = false,
        split: List<String> = listOf("bob", "me"),
    ) = Expense(
        id = id,
        groupId = group,
        payerId = payer,
        amountCents = 500L,
        description = "dinner",
        splitAmong = split,
        isSettlement = settlement,
    )

    private fun group(id: String = "g1", inviteMe: InviteStatus? = null) = Group(
        id = id,
        name = "Trip",
        ownerId = "bob",
        memberIds = listOf("bob", "me"),
        invites = if (inviteMe == null) emptyMap() else mapOf("me" to inviteMe),
    )

    @Test
    fun `new expense from another payer plans new-expense`() {
        val planned = planSyncNotifications(
            me = "me", cachedExpenseIds = emptySet(), seenInviteGroupIds = emptySet(),
            serverExpenses = listOf(expense("e1")), serverGroups = listOf(group()),
            prefs = allOn,
        )
        assertEquals(1, planned.size)
        assertEquals(PlannedKind.NEW_EXPENSE, planned[0].kind)
        assertEquals("e1", planned[0].expense?.id)
        assertEquals("g1", planned[0].groupId)
    }

    @Test
    fun `own actions never notify`() {
        val planned = planSyncNotifications(
            me = "me", cachedExpenseIds = emptySet(), seenInviteGroupIds = emptySet(),
            serverExpenses = listOf(
                expense("e1", payer = "me"),
                expense("e2", payer = "me", settlement = true, split = listOf("bob")),
            ),
            serverGroups = listOf(group()),
            prefs = allOn,
        )
        assertTrue(planned.isEmpty())
    }

    @Test
    fun `already-seen expenses stay silent`() {
        val planned = planSyncNotifications(
            me = "me", cachedExpenseIds = setOf("e1"), seenInviteGroupIds = emptySet(),
            serverExpenses = listOf(expense("e1")), serverGroups = listOf(group()),
            prefs = allOn,
        )
        assertTrue(planned.isEmpty())
    }

    @Test
    fun `settlement paying me plans debt-update, others stay silent`() {
        val planned = planSyncNotifications(
            me = "me", cachedExpenseIds = emptySet(), seenInviteGroupIds = emptySet(),
            serverExpenses = listOf(
                // bob -> me: affects me, notify.
                expense("s1", payer = "bob", settlement = true, split = listOf("me")),
                // bob -> ann: not mine, silent.
                expense("s2", payer = "bob", settlement = true, split = listOf("ann")),
            ),
            serverGroups = listOf(group()),
            prefs = allOn,
        )
        assertEquals(listOf(PlannedKind.DEBT_UPDATE), planned.map { it.kind })
        assertEquals("s1", planned[0].expense?.id)
    }

    @Test
    fun `unseen pending invite plans invite, seen stays silent`() {
        val fresh = planSyncNotifications(
            me = "me", cachedExpenseIds = emptySet(), seenInviteGroupIds = emptySet(),
            serverExpenses = emptyList(), serverGroups = listOf(group(inviteMe = InviteStatus.PENDING)),
            prefs = allOn,
        )
        assertEquals(listOf(PlannedKind.INVITE), fresh.map { it.kind })
        assertEquals("g1", fresh[0].groupId)

        val seen = planSyncNotifications(
            me = "me", cachedExpenseIds = emptySet(), seenInviteGroupIds = setOf("g1"),
            serverExpenses = emptyList(), serverGroups = listOf(group(inviteMe = InviteStatus.PENDING)),
            prefs = allOn,
        )
        assertTrue(seen.isEmpty())
    }

    @Test
    fun `declined invite never notifies`() {
        val planned = planSyncNotifications(
            me = "me", cachedExpenseIds = emptySet(), seenInviteGroupIds = emptySet(),
            serverExpenses = emptyList(), serverGroups = listOf(group(inviteMe = InviteStatus.DECLINED)),
            prefs = allOn,
        )
        assertTrue(planned.isEmpty())
    }

    @Test
    fun `per-kind prefs gate their kind only`() {
        val server = listOf(
            expense("e1"),
            expense("s1", payer = "bob", settlement = true, split = listOf("me")),
        )
        val groups = listOf(group("g2", InviteStatus.PENDING))
        fun run(prefs: NotificationPrefs) = planSyncNotifications(
            me = "me", cachedExpenseIds = emptySet(), seenInviteGroupIds = emptySet(),
            serverExpenses = server, serverGroups = groups, prefs = prefs,
        )
        assertEquals(
            setOf(PlannedKind.DEBT_UPDATE, PlannedKind.INVITE),
            run(allOn.copy(newExpense = false)).map { it.kind }.toSet(),
        )
        assertEquals(
            setOf(PlannedKind.NEW_EXPENSE, PlannedKind.INVITE),
            run(allOn.copy(settlementUpdates = false)).map { it.kind }.toSet(),
        )
        assertEquals(
            setOf(PlannedKind.NEW_EXPENSE, PlannedKind.DEBT_UPDATE),
            run(allOn.copy(reminders = false)).map { it.kind }.toSet(),
        )
        assertTrue(run(NotificationPrefs(false, false, false)).isEmpty())
    }

    @Test
    fun `burst in one group collapses to a single summary`() {
        val server = (1..7).map { expense("e$it") }
        val planned = planSyncNotifications(
            me = "me", cachedExpenseIds = emptySet(), seenInviteGroupIds = emptySet(),
            serverExpenses = server, serverGroups = listOf(group()),
            prefs = allOn, maxPerRun = 5,
        )
        assertEquals(1, planned.size)
        assertEquals(PlannedKind.SUMMARY, planned[0].kind)
        assertEquals(7, planned[0].summaryCount)
        assertEquals("g1", planned[0].groupId)
    }

    @Test
    fun `under the cap everything passes through uncollapsed`() {
        val server = (1..3).map { expense("e$it") }
        val planned = planSyncNotifications(
            me = "me", cachedExpenseIds = emptySet(), seenInviteGroupIds = emptySet(),
            serverExpenses = server, serverGroups = listOf(group()),
            prefs = allOn, maxPerRun = 5,
        )
        assertEquals(3, planned.size)
        assertTrue(planned.all { it.kind == PlannedKind.NEW_EXPENSE })
    }

    @Test
    fun `multi-group overflow collapses each group then truncates`() {
        val server = (1..4).flatMap { n -> (1..3).map { expense("g$n-e$it", group = "g$n") } }
        val groups = (1..4).map { group("g$it") }
        val planned = planSyncNotifications(
            me = "me", cachedExpenseIds = emptySet(), seenInviteGroupIds = emptySet(),
            serverExpenses = server, serverGroups = groups,
            prefs = allOn, maxPerRun = 3,
        )
        assertEquals(3, planned.size)
        assertTrue(planned.all { it.kind == PlannedKind.SUMMARY && it.summaryCount == 3 })
    }

    @Test
    fun `pendingInvites lists only my pending groups`() {
        val groups = listOf(
            group("g1", InviteStatus.PENDING),
            group("g2", InviteStatus.ACCEPTED),
            group("g3", InviteStatus.DECLINED),
            group("g4"),
        )
        assertEquals(listOf("g1"), pendingInvites(groups, "me"))
        assertTrue(pendingInvites(groups, "stranger").isEmpty())
    }
}
