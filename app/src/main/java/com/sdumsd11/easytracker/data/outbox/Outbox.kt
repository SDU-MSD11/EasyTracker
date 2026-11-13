package com.sdumsd11.easytracker.data.outbox

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.data.NotificationService
import com.sdumsd11.easytracker.domain.model.Expense
import com.sdumsd11.easytracker.domain.model.ExpenseId
import com.sdumsd11.easytracker.domain.model.GroupId
import com.sdumsd11.easytracker.domain.model.Settlement
import com.sdumsd11.easytracker.ui.StatusBus
import com.sdumsd11.easytracker.ui.StatusEvent
import com.sdumsd11.easytracker.ui.StatusKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

internal const val OP = "op"
internal const val OP_CREATE_EXPENSE = "create-expense"
internal const val OP_RECORD_PAYMENT = "record-payment"
internal const val KEY = "key"
internal const val MIRROR_ID = "mirror-id"
internal const val GROUP_ID = "group-id"
internal const val PAYER_ID = "payer-id"
internal const val FROM_ID = "from-id"
internal const val TO_ID = "to-id"
internal const val AMOUNT = "amount"
internal const val DESC = "desc"
internal const val SPLIT = "split"
internal const val RECEIPT = "receipt"

/** WorkManager floor (~10s); attempts carry the RetryPolicy spirit (5 tries, exponential). */
internal const val OUTBOX_BACKOFF_MS = 10_000L
internal const val OUTBOX_MAX_ATTEMPTS = 5

/**
 * Durable writer outbox (architecture owner).
 *
 * Mechanism: WorkManager one-time work — not a Room pending-ops table, not the
 * in-memory `fake.RetryQueue` (fakes/tests only, dies with the process). Chosen
 * because everything was already wired (`work-runtime-ktx`, `hilt-work`,
 * `HiltWorkerFactory` in `EasyTrackerApp`): no migration, no new SDK, survives
 * process death/doze, with `CONNECTED` gating + exponential backoff for free.
 *
 * Semantics: repos return [RequestStatus.Failed] immediately (never block UI),
 * keep the local Room mirror as source of truth, and park the op here. Unique
 * work names (`expense-<id>`/`payment-<id>` KEEP) dedupe double-enqueues:
 * at-least-once delivery, last-write-wins. A POST whose response is lost can
 * duplicate a server row — documented; no backend idempotency key exists to do
 * better cheaply. Past [OUTBOX_MAX_ATTEMPTS] the op drops; the mirror is pruned
 * on the next successful refresh (see `ApiExpenseRepository` reconciliation).
 *
 * UI observes [status] (per-op Pending/Success/Failed) and [backgroundSuccess]
 * (one-shot labels for snackbars); [markSuccess] additionally bridges into
 * [StatusBus] (`StatusKind.BackgroundSuccess`), which `BackgroundSuccessHost`
 * (collected once in `AppNavHost`) renders — that is the live consumer of both
 * flows. [NotificationService.notifyRetrySuccess] fires on every background
 * sync so the record survives for tests/previews.
 */
@Singleton
class Outbox @Inject constructor(
    @ApplicationContext context: Context,
    private val notifications: NotificationService,
    private val statusBus: StatusBus,
) {
    private val wm = WorkManager.getInstance(context)

    private val _status = MutableStateFlow<Map<String, RequestStatus<Unit>>>(emptyMap())
    val status: StateFlow<Map<String, RequestStatus<Unit>>> = _status

    // replay = 1: an event emitted while the app is backgrounded (no collector yet)
    // is still delivered to the next BackgroundSuccessHost collection.
    private val _backgroundSuccess = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 16)
    val backgroundSuccess: SharedFlow<String> = _backgroundSuccess

    /** Queue an expense create for [mirror]; returns the op key. */
    fun enqueueExpense(mirror: Expense): String {
        val key = "expense-${mirror.id}"
        enqueue(
            key,
            Data.Builder()
                .putString(OP, OP_CREATE_EXPENSE)
                .putString(KEY, key)
                .putString(MIRROR_ID, mirror.id)
                .putString(GROUP_ID, mirror.groupId)
                .putString(PAYER_ID, mirror.payerId)
                .putLong(AMOUNT, mirror.amountCents)
                .putString(DESC, mirror.description)
                .putStringArray(SPLIT, mirror.splitAmong.toTypedArray())
                .apply { mirror.receiptUri?.let { putString(RECEIPT, it) } }
                .build(),
        )
        return key
    }

    /** Queue a payment for [settlement] (client mirror carries the offline row id). */
    fun enqueuePayment(groupId: GroupId, settlement: Settlement, mirror: Expense): String {
        val key = paymentOpKey(groupId, settlement)
        enqueue(
            key,
            Data.Builder()
                .putString(OP, OP_RECORD_PAYMENT)
                .putString(KEY, key)
                .putString(MIRROR_ID, mirror.id)
                .putString(GROUP_ID, groupId)
                .putString(FROM_ID, settlement.fromId)
                .putString(TO_ID, settlement.toId)
                .putLong(AMOUNT, settlement.amountCents)
                .build(),
        )
        return key
    }

    suspend fun markSuccess(key: String, label: String) {
        _status.update { it + (key to RequestStatus.Success(Unit)) }
        _backgroundSuccess.emit(label)
        notifications.notifyRetrySuccess(label)
        statusBus.notify(StatusEvent(key, label, StatusKind.BackgroundSuccess))
    }

    fun markFailed(key: String, message: String?) {
        _status.update { it + (key to RequestStatus.Failed(message, retryable = false)) }
    }

    private fun enqueue(key: String, data: Data) {
        _status.update { it + (key to RequestStatus.Pending) }
        val req = OneTimeWorkRequestBuilder<OutboxWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, OUTBOX_BACKOFF_MS, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag("easytracker-outbox")
            .build()
        wm.enqueueUniqueWork(key, ExistingWorkPolicy.KEEP, req)
    }
}

/**
 * Stable unique-work key for a payment op. Derived from the settlement identity
 * (group + from + to + amount), NOT from a fresh mirror UUID, so double-tap /
 * PaymentFailed-retry of the same payment maps to the same WorkManager unique
 * work and KEEP dedupes it instead of double-charging.
 *
 * Limitation: two genuinely distinct payments with identical
 * group/from/to/amount collapse to one op while the first work is still
 * enqueued — true idempotency needs a backend idempotency key.
 */
internal fun paymentOpKey(groupId: GroupId, settlement: Settlement): String =
    "payment-$groupId-${settlement.fromId}-${settlement.toId}-${settlement.amountCents}"

/** Server-txId mirror shared by the repo fast path and the worker (refresh upserts over it). */
internal fun paymentMirror(txId: ExpenseId, groupId: GroupId, settlement: Settlement): Expense =
    Expense(
        id = txId,
        groupId = groupId,
        payerId = settlement.fromId,
        amountCents = settlement.amountCents,
        description = "Settlement",
        splitAmong = listOf(settlement.toId),
        createdAt = System.currentTimeMillis(),
        isSettlement = true,
    )
