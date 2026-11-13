package com.sdumsd11.easytracker.data.outbox

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.core.safeApiCall
import com.sdumsd11.easytracker.data.local.EasyTrackerDatabase
import com.sdumsd11.easytracker.data.local.toDomain
import com.sdumsd11.easytracker.data.local.toEntity
import com.sdumsd11.easytracker.data.prefs.UserPreferences
import com.sdumsd11.easytracker.data.remote.ApiService
import com.sdumsd11.easytracker.data.remote.CreateExpenseRequest
import com.sdumsd11.easytracker.data.remote.PaymentRequest
import com.sdumsd11.easytracker.data.remote.toDomain
import com.sdumsd11.easytracker.domain.model.Settlement
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Executes one parked writer op when `CONNECTED`. Attempts are bounded by
 * [OUTBOX_MAX_ATTEMPTS] (then the op drops; the Room mirror stays the readable
 * truth, mirroring `fake.RetryQueue` drop semantics). Non-retryable outcomes
 * (validation 4xx, 401/signed-out) fail fast without retry.
 */
@HiltWorker
class OutboxWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val api: ApiService,
    private val db: EasyTrackerDatabase,
    private val prefs: UserPreferences,
    private val outbox: Outbox,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val key = inputData.getString(KEY) ?: return Result.failure()
        if (prefs.authToken.first().isNullOrBlank()) {
            outbox.markFailed(key, "Signed out")
            return Result.failure()
        }
        if (runAttemptCount >= OUTBOX_MAX_ATTEMPTS) {
            outbox.markFailed(key, "Sync failed after $OUTBOX_MAX_ATTEMPTS attempts")
            return Result.failure()
        }
        return when (inputData.getString(OP)) {
            OP_CREATE_EXPENSE -> syncExpense(key)
            OP_RECORD_PAYMENT -> syncPayment(key)
            else -> Result.failure()
        }
    }

    private suspend fun syncExpense(key: String): Result {
        val d = inputData
        val mirrorId = d.getString(MIRROR_ID) ?: return Result.failure()
        val groupId = d.getString(GROUP_ID) ?: return Result.failure()
        val amount = d.getLong(AMOUNT, -1)
        val split = d.getStringArray(SPLIT)?.toList().orEmpty()
        if (amount < 0 || split.isEmpty()) {
            outbox.markFailed(key, "Invalid expense payload")
            return Result.failure()
        }
        return when (
            val r = safeApiCall({ prefs.clearAuth() }) {
                api.createExpense(
                    groupId,
                    CreateExpenseRequest(
                        payerId = d.getString(PAYER_ID),
                        amountCents = amount,
                        description = d.getString(DESC).orEmpty(),
                        splitAmong = split,
                        receiptUri = d.getString(RECEIPT),
                    ),
                )
            }
        ) {
            is RequestStatus.Success -> {
                val server = r.data.toDomain()
                val dao = db.expenseDao()
                if (mirrorId != server.id) dao.deleteById(mirrorId)
                dao.upsert(server.toEntity())
                outbox.markSuccess(key, "Expense synced")
                Result.success()
            }
            is RequestStatus.Failed -> if (r.retryable) Result.retry() else {
                outbox.markFailed(key, r.message)
                Result.failure()
            }
            is RequestStatus.Pending -> Result.retry()
        }
    }

    private suspend fun syncPayment(key: String): Result {
        val d = inputData
        val mirrorId = d.getString(MIRROR_ID) ?: return Result.failure()
        val groupId = d.getString(GROUP_ID) ?: return Result.failure()
        val fromId = d.getString(FROM_ID) ?: return Result.failure()
        val toId = d.getString(TO_ID) ?: return Result.failure()
        val amount = d.getLong(AMOUNT, 0)
        val settlement = runCatching { Settlement(fromId, toId, amount) }.getOrNull()
        if (settlement == null) {
            outbox.markFailed(key, "Invalid payment payload")
            return Result.failure()
        }
        return when (
            val r = safeApiCall({ prefs.clearAuth() }) {
                api.recordPayment(groupId, PaymentRequest(toId, amount))
            }
        ) {
            is RequestStatus.Success -> {
                val dao = db.expenseDao()
                dao.deleteById(mirrorId)
                dao.upsert(paymentMirror(r.data.txId, groupId, settlement).toEntity())
                outbox.markSuccess(key, "Payment synced")
                Result.success()
            }
            is RequestStatus.Failed -> if (r.retryable) Result.retry() else {
                outbox.markFailed(key, r.message)
                Result.failure()
            }
            is RequestStatus.Pending -> Result.retry()
        }
    }
}
