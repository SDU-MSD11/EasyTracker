package com.sdumsd11.easytracker.data

import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.core.safeApiCall
import com.sdumsd11.easytracker.data.local.EasyTrackerDatabase
import com.sdumsd11.easytracker.data.local.ExpenseEntity
import com.sdumsd11.easytracker.data.local.toDomain
import com.sdumsd11.easytracker.data.local.toEntity
import com.sdumsd11.easytracker.data.outbox.Outbox
import com.sdumsd11.easytracker.data.outbox.paymentMirror
import com.sdumsd11.easytracker.data.prefs.UserPreferences
import com.sdumsd11.easytracker.data.remote.ApiService
import com.sdumsd11.easytracker.data.remote.CreateExpenseRequest
import com.sdumsd11.easytracker.data.remote.PatchExpenseRequest
import com.sdumsd11.easytracker.data.remote.PaymentRequest
import com.sdumsd11.easytracker.data.remote.toDomain
import com.sdumsd11.easytracker.di.IoDispatcher
import com.sdumsd11.easytracker.domain.DebtCalculator
import com.sdumsd11.easytracker.domain.model.Balance
import com.sdumsd11.easytracker.domain.model.Expense
import com.sdumsd11.easytracker.domain.model.ExpenseId
import com.sdumsd11.easytracker.domain.model.GroupId
import com.sdumsd11.easytracker.domain.model.Settlement
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Offline-first ExpenseRepository: Room cache drives reads; balances/settlements
 * derive via [DebtCalculator] like the fakes. Writes go to the network first then the
 * cache. [recordSettlement] POSTs to `/payments` (server truth) and mirrors the
 * repayment locally with the same id the server returns as txId, so the next
 * expenses refresh (which now folds payments in with `kind: "settlement"`) upserts
 * over the mirror instead of duplicating it. No Hilt binding yet — arch owner wires it.
 *
 * Fault tolerance: retryable failures ([addExpense]/[recordSettlement]) write a
 * client-id Room mirror immediately (stays visible offline) and park the op in
 * [Outbox] (WorkManager, `CONNECTED` + exponential backoff); the suspend call
 * still returns [RequestStatus.Failed] at once so the UI never blocks.
 * Payment mirrors reuse the existing row for the same settlement identity, and
 * the stable `payment-<group>-<from>-<to>-<amount>` op key keeps double-tap /
 * retry from minting duplicate charges.
 *
 * Reconciliation: every successful per-group refresh prunes client-mirror rows
 * ([ExpenseEntity.isLocalMirror]) absent from the server snapshot, so a
 * dropped op (past `OUTBOX_MAX_ATTEMPTS`) stops diverging balances. Server
 * rows are never pruned.
 */
@Singleton
class ApiExpenseRepository @Inject constructor(
    private val api: ApiService,
    db: EasyTrackerDatabase,
    private val prefs: UserPreferences,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val outbox: Outbox,
) : ExpenseRepository {
    private val expenses = db.expenseDao()
    private val cachedGroups = db.groupDao()
    private val clear401: suspend () -> Unit = { prefs.clearAuth() }
    // Singleton-lived: background refreshes never block collectors (cache emits first).
    private val scope = CoroutineScope(SupervisorJob() + io)
    private val itemRefresh = KeyedRefreshSingleFlight()
    private val allRefresh = RefreshSingleFlight()
    // Last network failure per pass; an empty cache + a recorded failure emits Failed
    // (a warm cache always wins, so offline reads stay visible).
    private val itemErrors = MutableStateFlow<Map<String, RequestStatus.Failed>>(emptyMap())
    private val allError = MutableStateFlow<RequestStatus.Failed?>(null)

    // Cache-first: Room emits immediately, per-group refresh runs in the background
    // single-flighted (observeBalances/Settlements re-collect this flow — one shared pass).
    // Empty cache + failed refresh surfaces Failed (retryable) instead of fake empty Success.
    override fun observeExpenses(groupId: GroupId): Flow<RequestStatus<List<Expense>>> =
        combine(expenses.observeByGroup(groupId), itemErrors) { rows, errs ->
            val list = rows.map { it.toDomain() }
            val err = errs[groupId]
            if (list.isEmpty() && err != null) err else RequestStatus.Success(list)
        }.onStart {
            itemRefresh.launchIn(scope, groupId) { fetchExpenses(groupId) }
        }.flowOn(io)

    override fun observeBalances(groupId: GroupId): Flow<RequestStatus<List<Balance>>> =
        observeExpenses(groupId).map { status ->
            when (status) {
                is RequestStatus.Success -> RequestStatus.Success(
                    DebtCalculator.calculateBalances(status.data)
                        .map { (id, net) -> Balance(id, net) }
                        .sortedByDescending { it.netCents },
                )
                is RequestStatus.Pending -> status
                is RequestStatus.Failed -> status
            }
        }

    override fun observeSettlements(groupId: GroupId): Flow<RequestStatus<List<Settlement>>> =
        observeExpenses(groupId).map { status ->
            when (status) {
                is RequestStatus.Success -> RequestStatus.Success(
                    DebtCalculator.minimizeSettlements(DebtCalculator.calculateBalances(status.data)),
                )
                is RequestStatus.Pending -> status
                is RequestStatus.Failed -> status
            }
        }

    override suspend fun addExpense(expense: Expense): RequestStatus<Expense> = withContext(io) {
        if (expense.amountCents < 0L) return@withContext RequestStatus.Failed("Amount must be >= 0")
        when (val r = safeApiCall(clear401) {
            api.createExpense(
                expense.groupId,
                CreateExpenseRequest(
                    payerId = expense.payerId,
                    amountCents = expense.amountCents,
                    description = expense.description,
                    splitAmong = expense.splitAmong,
                    receiptUri = expense.receiptUri,
                ),
            )
        }) {
            is RequestStatus.Success -> {
                val stored = r.data.toDomain()
                expenses.upsert(stored.toEntity())
                RequestStatus.Success(stored)
            }
            // Retryable (e.g. temp network loss): mirror locally so the row stays
            // visible offline, park the op in the Outbox, fail fast (non-blocking).
            is RequestStatus.Failed -> if (r.retryable) {
                val mirror = expense.copy(
                    id = UUID.randomUUID().toString(),
                    createdAt = expense.createdAt.takeIf { it != 0L } ?: System.currentTimeMillis(),
                )
                expenses.upsert(mirror.toEntity(isLocalMirror = true))
                outbox.enqueueExpense(mirror)
                r
            } else {
                r
            }
            is RequestStatus.Pending -> r
        }
    }

    // Cache-only by design: no global history route exists, so collecting must never fan out
    // N per-group calls (Account chips derive from this cache). History refreshes explicitly.
    // Empty cache + failed explicit refresh surfaces Failed instead of fake empty Success.
    override fun observeAllExpenses(): Flow<RequestStatus<List<Expense>>> =
        combine(expenses.observeAll(), allError) { rows, err ->
            val list = rows.map { it.toDomain() }
            if (list.isEmpty() && err != null) err else RequestStatus.Success(list)
        }.flowOn(io)

    override suspend fun updateExpense(
        expenseId: ExpenseId,
        amountCents: Long?,
        description: String?,
        splitAmong: List<String>?,
        receiptUri: String?,
    ): RequestStatus<Expense> = withContext(io) {
        if (amountCents != null && amountCents < 0L) return@withContext RequestStatus.Failed("Amount must be >= 0")
        val body = PatchExpenseRequest(amountCents, description, splitAmong, receiptUri)
        when (val r = safeApiCall(clear401) { api.patchExpense(expenseId, body) }) {
            is RequestStatus.Success -> {
                val stored = r.data.toDomain()
                expenses.upsert(stored.toEntity())
                RequestStatus.Success(stored)
            }
            is RequestStatus.Failed -> r
            is RequestStatus.Pending -> r
        }
    }

    override suspend fun deleteExpense(expenseId: ExpenseId): RequestStatus<Unit> = withContext(io) {
        when (val r = safeApiCall(clear401) { api.deleteExpense(expenseId) }) {
            is RequestStatus.Success -> {
                expenses.deleteById(expenseId)
                RequestStatus.Success(Unit)
            }
            is RequestStatus.Failed -> r
            is RequestStatus.Pending -> r
        }
    }

    override suspend fun recordSettlement(groupId: GroupId, settlement: Settlement): RequestStatus<Expense> =
        withContext(io) {
            // Backend derives the payer (from) from the JWT; toId/amount are the body.
            when (val r = safeApiCall(clear401) {
                api.recordPayment(groupId, PaymentRequest(settlement.toId, settlement.amountCents))
            }) {
                is RequestStatus.Success -> {
                    val mirror = paymentMirror(r.data.txId, groupId, settlement)
                    expenses.upsert(mirror.toEntity())
                    RequestStatus.Success(mirror)
                }
                // Retryable: reuse the existing client mirror for this settlement
                // identity when present (upsert over it, same stable op key), so
                // retry/double-tap never stacks duplicate mirror rows or charges.
                is RequestStatus.Failed -> if (r.retryable) {
                    val reusedId = findReusablePaymentMirror(expenses.localMirrors(groupId), settlement)?.id
                    val mirror = Expense(
                        id = reusedId ?: UUID.randomUUID().toString(),
                        groupId = groupId,
                        payerId = settlement.fromId,
                        amountCents = settlement.amountCents,
                        description = "Settlement",
                        splitAmong = listOf(settlement.toId),
                        createdAt = System.currentTimeMillis(),
                        isSettlement = true,
                    )
                    expenses.upsert(mirror.toEntity(isLocalMirror = true))
                    outbox.enqueuePayment(groupId, settlement, mirror)
                    r
                } else {
                    r
                }
                is RequestStatus.Pending -> r
            }
        }

    /** Explicit per-group refresh for the Detail first-paint gate; bypasses the throttle. */
    override suspend fun refreshExpenses(groupId: GroupId): Boolean =
        itemRefresh.tryRun(groupId, force = true) { fetchExpenses(groupId) }

    private suspend fun fetchExpenses(groupId: GroupId): Boolean {
        if (prefs.authToken.first().isNullOrBlank()) return true
        return when (val r = safeApiCall(clear401) { api.getExpenses(groupId) }) {
            // Signed out while in flight (signOut clears tables): never repopulate after the wipe.
            is RequestStatus.Success -> if (prefs.authToken.first().isNullOrBlank()) {
                true
            } else {
                itemErrors.update { it - groupId }
                r.data.forEach { expenses.upsert(it.toDomain().toEntity()) }
                // Dropped-op reconciliation: prune client mirrors the server never
                // saw (op dropped past OUTBOX_MAX_ATTEMPTS); the worker re-adds a
                // still-pending op's row on success, so pruning is self-healing.
                val serverIds = r.data.map { it.id }.toSet()
                staleLocalMirrorIds(expenses.localMirrors(groupId), serverIds)
                    .forEach { expenses.deleteById(it) }
                true
            }
            is RequestStatus.Failed -> {
                itemErrors.update { it + (groupId to r) }
                false
            }
            is RequestStatus.Pending -> true
        }
    }

    /**
     * Explicit cross-group refresh: single-flighted, max [MAX_PARALLEL_GROUP_FETCH]
     * concurrent per-group fetches. Each group fetch shares the item gate with
     * Detail/background passes, so Groups + History + Account never open duplicate
     * sockets for the same group.
     */
    override suspend fun refreshAllExpenses(): Boolean = allRefresh.tryRun(force = true) {
        if (prefs.authToken.first().isNullOrBlank()) return@tryRun true
        val ids = cachedGroups.observeAll().first().map { it.id }
        val gate = Semaphore(MAX_PARALLEL_GROUP_FETCH)
        val results = coroutineScope {
            ids.map { id ->
                async { gate.withPermit { itemRefresh.tryRun(id, block = { fetchExpenses(id) }) } }
            }.awaitAll()
        }
        val ok = results.all { it }
        allError.value = if (ok) null else itemErrors.value.values.firstOrNull()
        ok
    }

    private companion object {
        const val MAX_PARALLEL_GROUP_FETCH = 4
    }
}

/**
 * Existing client mirror for the same settlement identity (same payer, recipient
 * and amount, settlement-shaped), if any. Compared on domain shape so the
 * storage encoding stays an impl detail.
 */
internal fun findReusablePaymentMirror(
    mirrors: List<ExpenseEntity>,
    settlement: Settlement,
): ExpenseEntity? = mirrors.filter { it.isSettlement }.firstOrNull {
    val e = it.toDomain()
    e.payerId == settlement.fromId &&
        e.amountCents == settlement.amountCents &&
        e.description == "Settlement" &&
        e.splitAmong == listOf(settlement.toId)
}

/** Client-mirror ids missing from the latest server snapshot — safe to prune. */
internal fun staleLocalMirrorIds(
    mirrors: List<ExpenseEntity>,
    serverIds: Set<String>,
): List<String> = mirrors.filter { it.id !in serverIds }.map { it.id }
