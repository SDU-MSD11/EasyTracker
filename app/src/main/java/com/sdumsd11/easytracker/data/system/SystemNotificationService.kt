package com.sdumsd11.easytracker.data.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.sdumsd11.easytracker.R
import com.sdumsd11.easytracker.data.NotificationService
import com.sdumsd11.easytracker.data.SentNotification
import com.sdumsd11.easytracker.domain.model.Balance
import com.sdumsd11.easytracker.domain.model.Expense
import com.sdumsd11.easytracker.domain.model.GroupId
import com.sdumsd11.easytracker.domain.model.UserId
import com.sdumsd11.easytracker.navigation.Routes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Deep-link scheme for notification taps. Full contract in [tapFor]. */
const val DEEP_LINK_SCHEME = "easytracker"

/** Intent extra carrying the in-app route string (see [Routes]); ui-ux owner consumes it. */
const val EXTRA_ROUTE = "route"

private const val CHANNEL_DEBTS = "easytracker-debts"
private const val CHANNEL_PAYMENTS = "easytracker-payments"
private const val CHANNEL_REMINDERS = "easytracker-reminders"

/**
 * Real [NotificationService]: posts to the system tray (closed-app path) and records
 * every delivery in [observeSent] for UI/test observability (same kinds as the fake:
 * `reminder`, `new-expense`, `debt-update`, `retry-success`, plus `summary`).
 *
 * Per-kind enable flags live outside: SyncWorker reads NotificationPrefs and only
 * calls the matching method, so this class posts unconditionally when invoked.
 *
 * Deep-link contract (agreed with ui-ux owner): each notification carries a content
 * intent with action VIEW and data Uri `easytracker://<route>`, where `<route>` is
 * a full [Routes] string (`groups/<id>`, `account/history`, `groups?...`). The same
 * route string rides as the [EXTRA_ROUTE] extra. MainActivity declares the
 * `easytracker` scheme filter; the nav graph's `navDeepLink` entries route it.
 */
@Singleton
class SystemNotificationService @Inject constructor(
    @ApplicationContext private val context: Context,
) : NotificationService {
    private val manager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    private val _sent = MutableStateFlow<List<SentNotification>>(emptyList())
    override fun observeSent(): Flow<List<SentNotification>> = _sent

    /** Test/UI helper: all messages of one kind. */
    fun sentOf(kind: String): List<SentNotification> = _sent.value.filter { it.kind == kind }

    override suspend fun sendReminder(toUserId: UserId, message: String) {
        record("reminder", toUserId, message)
        post(
            channel = CHANNEL_REMINDERS,
            title = "Reminder",
            text = message,
            icon = R.drawable.group,
            id = ("reminder" + toUserId + message).hashCode(),
            route = Routes.groups(),
        )
    }

    override suspend fun notifyNewExpense(groupId: GroupId, expense: Expense) {
        val text = "${expense.description.ifBlank { "Expense" }} • ${cents(expense.amountCents)}"
        record("new-expense", null, "${expense.description}: ${expense.amountCents}c in $groupId")
        post(
            channel = CHANNEL_DEBTS,
            title = "New expense",
            text = text,
            icon = R.drawable.receipt,
            id = ("expense" + expense.id).hashCode(),
            route = Routes.groupDetail(groupId),
        )
    }

    override suspend fun notifyDebtUpdate(userId: UserId, balance: Balance) {
        val text = "You received ${cents(balance.netCents)} from a settlement"
        record("debt-update", userId, "Net ${balance.netCents}c")
        post(
            channel = CHANNEL_PAYMENTS,
            title = "Payment update",
            text = text,
            icon = R.drawable.payments,
            id = ("debt" + userId + balance.netCents).hashCode(),
            route = Routes.PaymentHistory,
        )
    }

    override suspend fun notifyRetrySuccess(label: String) {
        record("retry-success", null, label)
        post(
            channel = CHANNEL_PAYMENTS,
            title = "Sync recovered",
            text = label,
            icon = R.drawable.payments,
            id = ("retry" + label).hashCode(),
            route = Routes.PaymentHistory,
        )
    }

    /** Collapsed per-group burst from SyncWorker ("N new updates in <group>"). */
    suspend fun notifySummary(groupId: GroupId, groupName: String, count: Int) {
        val name = groupName.ifBlank { "a group" }
        val text = "$count new updates in $name"
        record("summary", null, "$count updates in $groupId")
        post(
            channel = CHANNEL_DEBTS,
            title = "New activity",
            text = text,
            icon = R.drawable.receipt,
            id = ("summary" + groupId).hashCode(),
            route = Routes.groupDetail(groupId),
        )
    }

    private fun record(kind: String, toUserId: UserId?, message: String) {
        _sent.update { it + SentNotification(kind, toUserId, message) }
    }

    private fun post(channel: String, title: String, text: String, icon: Int, id: Int, route: String) {
        ensureChannels()
        manager.notify(
            id,
            NotificationCompat.Builder(context, channel)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(tapFor(route))
                .setAutoCancel(true)
                .build(),
        )
    }

    /** Single content-intent contract: VIEW + `easytracker://<route>` data Uri with
     * the route string also in [EXTRA_ROUTE]. MainActivity's `easytracker` scheme
     * filter resolves it; the nav graph's `navDeepLink` entries route it. */
    private fun tapFor(route: String): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, "$DEEP_LINK_SCHEME://$route".toUri())
            .putExtra(EXTRA_ROUTE, route)
        return PendingIntent.getActivity(
            context,
            route.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channels = listOf(
            NotificationChannel(CHANNEL_DEBTS, "New debts & expenses", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "New expenses posted in your groups" },
            NotificationChannel(CHANNEL_PAYMENTS, "Settlement & payment updates", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Settlements and payment sync activity" },
            NotificationChannel(CHANNEL_REMINDERS, "Reminders & invites", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Group invites and balance reminders" },
        )
        manager.createNotificationChannels(channels)
    }
}

/** Euro amount from minor units (mirrors AmountText/formatCentsPlain without a composable). */
internal fun cents(amountCents: Long): String {
    val sign = if (amountCents < 0L) "-" else ""
    val abs = kotlin.math.abs(amountCents)
    return "$sign€${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
}
