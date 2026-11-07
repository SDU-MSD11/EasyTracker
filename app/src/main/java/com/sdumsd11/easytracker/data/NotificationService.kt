package com.sdumsd11.easytracker.data

import com.sdumsd11.easytracker.domain.model.Balance
import com.sdumsd11.easytracker.domain.model.Expense
import com.sdumsd11.easytracker.domain.model.GroupId
import com.sdumsd11.easytracker.domain.model.UserId
import kotlinx.coroutines.flow.Flow

/** Record of a delivered in-app notification (observable for UI + tests). */
data class SentNotification(
    val kind: String,
    val toUserId: UserId?,
    val message: String,
    val at: Long = System.currentTimeMillis(),
)

/**
 * In-app notification hook. No FCM: UI calls [sendReminder]/repositories call the
 * `notify*` methods; the fake records everything for UI display and tests.
 */
interface NotificationService {
    /** Manual in-app reminder from UI (e.g. "nudge debtor" button on balances route). */
    suspend fun sendReminder(toUserId: UserId, message: String)

    /** Fired by ExpenseRepository when a new expense lands in [groupId]. */
    suspend fun notifyNewExpense(groupId: GroupId, expense: Expense)

    /** Fired when a user's net position changes (settlement recorded). */
    suspend fun notifyDebtUpdate(userId: UserId, balance: Balance)

    /** Fired by RetryQueue when a queued write eventually syncs after offline/retry. */
    suspend fun notifyRetrySuccess(label: String)

    fun observeSent(): Flow<List<SentNotification>>
}
