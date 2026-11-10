package com.sdumsd11.easytracker.data

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
import com.sdumsd11.easytracker.di.IoDispatcher
import com.sdumsd11.easytracker.domain.DebtCalculator
import com.sdumsd11.easytracker.domain.model.Balance
import com.sdumsd11.easytracker.domain.model.Expense
import com.sdumsd11.easytracker.domain.model.ExpenseId
import com.sdumsd11.easytracker.domain.model.GroupId
import com.sdumsd11.easytracker.domain.model.Settlement
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/**
 * Offline-first ExpenseRepository: Room cache drives reads; balances/settlements
 * derive via [DebtCalculator] like the fakes. Writes hit the network then the
 * cache. [recordSettlement] POSTs to `/payments` (server truth) and mirrors the
 * repayment as a local settlement expense so history + derived balances match.
 * No Hilt binding yet — arch owner wires it.
 */
@Singleton
class ApiExpenseRepository @Inject constructor(
    private val api: ApiService,
    db: EasyTrackerDatabase,
    private val prefs: UserPreferences,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ExpenseRepository {
    private val expenses = db.expenseDao()
    private val clear401: suspend () -> Unit = { prefs.clearAuth() }

    override fun observeExpenses(groupId: GroupId): Flow<RequestStatus<List<Expense>>> =
        expenses.observeByGroup(groupId).map { rows ->
            RequestStatus.Success(rows.map { it.toDomain() }) as RequestStatus<List<Expense>>
        }.onStart {
            emit(RequestStatus.Pending)
            runCatching { refreshExpenses(groupId) }
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
                    val mirror = Expense(
                        id = r.data.txId,
                        groupId = groupId,
                        payerId = settlement.fromId,
                        amountCents = settlement.amountCents,
                        description = "Settlement",
                        splitAmong = listOf(settlement.toId),
                        createdAt = System.currentTimeMillis(),
                    )
                    expenses.upsert(mirror.toEntity())
                    RequestStatus.Success(mirror)
                }
                is RequestStatus.Failed -> r
                is RequestStatus.Pending -> r
            }
        }

    private suspend fun refreshExpenses(groupId: GroupId) {
        when (val r = safeApiCall(clear401) { api.getExpenses(groupId) }) {
            is RequestStatus.Success -> r.data.forEach { expenses.upsert(it.toDomain().toEntity()) }
            else -> Unit
        }
    }
}
