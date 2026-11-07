package com.sdumsd11.easytracker.data.fake

import com.sdumsd11.easytracker.data.NotificationService
import com.sdumsd11.easytracker.data.SentNotification
import com.sdumsd11.easytracker.domain.model.Balance
import com.sdumsd11.easytracker.domain.model.Expense
import com.sdumsd11.easytracker.domain.model.GroupId
import com.sdumsd11.easytracker.domain.model.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory NotificationService: records everything for UI display and tests. */
class FakeNotificationService : NotificationService {
    private val _sent = MutableStateFlow<List<SentNotification>>(emptyList())
    override fun observeSent(): Flow<List<SentNotification>> = _sent

    /** Test helper: all messages of one kind. */
    fun sentOf(kind: String): List<SentNotification> = _sent.value.filter { it.kind == kind }

    private fun record(notification: SentNotification) {
        _sent.update { it + notification }
    }

    override suspend fun sendReminder(toUserId: UserId, message: String) {
        record(SentNotification(kind = "reminder", toUserId = toUserId, message = message))
    }

    override suspend fun notifyNewExpense(groupId: GroupId, expense: Expense) {
        record(
            SentNotification(
                kind = "new-expense",
                toUserId = null, // fan-out per member happens in the real impl
                message = "${expense.description}: ${expense.amountCents}c in $groupId",
            ),
        )
    }

    override suspend fun notifyDebtUpdate(userId: UserId, balance: Balance) {
        record(
            SentNotification(
                kind = "debt-update",
                toUserId = userId,
                message = "Net ${balance.netCents}c",
            ),
        )
    }

    override suspend fun notifyRetrySuccess(label: String) {
        record(SentNotification(kind = "retry-success", toUserId = null, message = label))
    }
}
