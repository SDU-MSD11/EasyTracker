package com.sdumsd11.easytracker.data.sync

import com.sdumsd11.easytracker.domain.model.Expense
import com.sdumsd11.easytracker.domain.model.ExpenseId
import com.sdumsd11.easytracker.domain.model.Group
import com.sdumsd11.easytracker.domain.model.GroupId
import com.sdumsd11.easytracker.domain.model.InviteStatus
import com.sdumsd11.easytracker.domain.model.NotificationPrefs
import com.sdumsd11.easytracker.domain.model.UserId

/** Max system notifications posted per sync run; overflow collapses to per-group summaries. */
const val MAX_NOTIFICATIONS_PER_RUN = 5

/** Planned delivery kinds; SyncWorker maps each to the matching NotificationService call. */
enum class PlannedKind { NEW_EXPENSE, DEBT_UPDATE, INVITE, SUMMARY }

/**
 * One planned delivery. [expense] rides along for NEW_EXPENSE/DEBT_UPDATE so the
 * worker posts without re-resolving; SUMMARY carries the collapsed [summaryCount].
 */
data class PlannedNotification(
    val kind: PlannedKind,
    val groupId: GroupId,
    val groupName: String = "",
    val expense: Expense? = null,
    val summaryCount: Int = 0,
)

/**
 * Pure sync diff: server-vs-last-seen snapshots + [prefs] gates → planned deliveries.
 * No Android, no I/O — unit-test directly.
 *
 * - New non-settlement expenses with another payer → NEW_EXPENSE (iff newExpense).
 * - New settlements where I am the recipient (`splitAmong` contains me, payer is
 *   someone else) → DEBT_UPDATE (iff settlementUpdates).
 * - Groups PENDING-inviting me that I haven't seen → INVITE (iff reminders).
 * - Never notifies for my own actions (payerId == me).
 * - Total capped at [maxPerRun]: overflow collapses per group into one SUMMARY
 *   ("N new updates in <group>"); singletons pass through untouched.
 */
fun planSyncNotifications(
    me: UserId,
    cachedExpenseIds: Set<ExpenseId>,
    seenInviteGroupIds: Set<GroupId>,
    serverExpenses: List<Expense>,
    serverGroups: List<Group>,
    prefs: NotificationPrefs,
    maxPerRun: Int = MAX_NOTIFICATIONS_PER_RUN,
): List<PlannedNotification> {
    val names = serverGroups.associate { it.id to it.name }
    val fresh = serverExpenses.filter { it.id !in cachedExpenseIds && it.payerId != me }
    val planned = mutableListOf<PlannedNotification>()
    if (prefs.newExpense) {
        fresh.filter { !it.isSettlement }
            .forEach { planned += PlannedNotification(PlannedKind.NEW_EXPENSE, it.groupId, names[it.groupId].orEmpty(), it) }
    }
    if (prefs.settlementUpdates) {
        fresh.filter { it.isSettlement && me in it.splitAmong }
            .forEach { planned += PlannedNotification(PlannedKind.DEBT_UPDATE, it.groupId, names[it.groupId].orEmpty(), it) }
    }
    if (prefs.reminders) {
        serverGroups
            .filter { it.id !in seenInviteGroupIds && it.invites[me] == InviteStatus.PENDING }
            .forEach { planned += PlannedNotification(PlannedKind.INVITE, it.id, it.name) }
    }
    if (planned.size <= maxPerRun) return planned
    return planned.groupBy { it.groupId }.flatMap { (gid, items) ->
        if (items.size == 1) items
        else listOf(PlannedNotification(PlannedKind.SUMMARY, gid, names[gid].orEmpty(), summaryCount = items.size))
    }.take(maxPerRun)
}

/** Group ids currently PENDING-inviting [me] — the invite half of the sync snapshot. */
internal fun pendingInvites(serverGroups: List<Group>, me: UserId): List<GroupId> =
    serverGroups.filter { it.invites[me] == InviteStatus.PENDING }.map { it.id }
