package com.sdumsd11.easytracker.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.data.ExpenseRepository
import com.sdumsd11.easytracker.data.GroupRepository
import com.sdumsd11.easytracker.data.prefs.UserPreferences
import com.sdumsd11.easytracker.data.system.SystemNotificationService
import com.sdumsd11.easytracker.domain.model.Balance
import com.sdumsd11.easytracker.domain.model.NotificationPrefs
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Periodic closed-app poll: refreshes groups + all expenses, diffs the result
 * against the DataStore last-seen snapshot via [planSyncNotifications], and posts
 * one system notification per planned delivery (per-kind NotificationPrefs gates
 * already applied by the planner). Bursts collapse to per-group summaries there.
 *
 * Never notifies for the device user's own actions (planner filters payerId == me).
 * First run with an empty snapshot seeds the baseline silently so install history
 * never re-notifies as "new". Signed out (no token) → success no-op; any failed
 * refresh → [Result.retry] (WorkManager backoff), never a crash.
 *
 * Constraints (CONNECTED) + the 15-minute cadence live in [SyncScheduler].
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val groups: GroupRepository,
    private val expenses: ExpenseRepository,
    private val prefs: UserPreferences,
    private val notifications: SystemNotificationService,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (prefs.authToken.first().isNullOrBlank()) return Result.success()
        val me = prefs.authUserId.first()
            ?: prefs.profile.first()?.id
            ?: return Result.success()

        val seenExpenses = prefs.lastSeenExpenseIds.first()
        val seenPayments = prefs.lastSeenPaymentIds.first()
        val seenInvites = prefs.lastSeenInviteGroupIds.first()

        val groupsResult = runCatching { groups.refreshGroups() }
            .getOrElse { RequestStatus.Failed(it.message) }
        val okExpenses = runCatching { expenses.refreshAllExpenses() }.getOrDefault(false)
        // Signed out while in flight: tables are wiped, stop without repopulating.
        if (prefs.authToken.first().isNullOrBlank()) return Result.success()
        if (groupsResult !is RequestStatus.Success || !okExpenses) return Result.retry()

        val serverExpenses =
            (expenses.observeAllExpenses().first() as? RequestStatus.Success)?.data.orEmpty()
        val serverGroups =
            (groups.observeGroups().first() as? RequestStatus.Success)?.data.orEmpty()

        // Baseline: never notify for pre-install history.
        if (seenExpenses.isEmpty() && seenPayments.isEmpty() && seenInvites.isEmpty()) {
            prefs.saveSyncSnapshot(
                serverExpenses.map { it.id }.toSet(),
                serverExpenses.filter { it.isSettlement }.map { it.id }.toSet(),
                pendingInvites(serverGroups, me).toSet(),
            )
            return Result.success()
        }

        val profilePrefs = prefs.profile.first()?.notificationPrefs ?: NotificationPrefs()
        val plan = planSyncNotifications(me, seenExpenses, seenInvites, serverExpenses, serverGroups, profilePrefs)
        // Persist the snapshot before notifying: a notify-then-crash window must not
        // re-notify. Snapshots are REPLACED with the current server set each run
        // (never unioned), so deleted history can't pin the set unbounded.
        prefs.saveSyncSnapshot(
            serverExpenses.map { it.id }.toSet(),
            serverExpenses.filter { it.isSettlement }.map { it.id }.toSet(),
            pendingInvites(serverGroups, me).toSet(),
        )
        for (planned in plan) {
            when (planned.kind) {
                PlannedKind.NEW_EXPENSE -> planned.expense?.let { notifications.notifyNewExpense(planned.groupId, it) }
                PlannedKind.DEBT_UPDATE -> planned.expense?.let {
                    notifications.notifyDebtUpdate(me, Balance(me, it.amountCents))
                }
                PlannedKind.INVITE -> notifications.sendReminder(
                    me,
                    "You've been invited to join ${planned.groupName.ifBlank { "a group" }}",
                )
                PlannedKind.SUMMARY -> notifications.notifySummary(planned.groupId, planned.groupName, planned.summaryCount)
            }
        }
        return Result.success()
    }
}
