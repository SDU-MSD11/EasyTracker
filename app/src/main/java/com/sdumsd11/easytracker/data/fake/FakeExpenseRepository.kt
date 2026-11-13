package com.sdumsd11.easytracker.data.fake

import com.sdumsd11.easytracker.data.ExpenseRepository
import com.sdumsd11.easytracker.data.NotificationService
import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.domain.DebtCalculator
import com.sdumsd11.easytracker.domain.model.Balance
import com.sdumsd11.easytracker.domain.model.Expense
import com.sdumsd11.easytracker.domain.model.ExpenseId
import com.sdumsd11.easytracker.domain.model.GroupId
import com.sdumsd11.easytracker.domain.model.Settlement
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

/**
 * In-memory ExpenseRepository. Balances/settlements are derived from the same
 * [DebtCalculator] pure functions the real Room-backed impl will use, so UI
 * behavior matches once the cache impl lands.
 */
class FakeExpenseRepository(
    scope: CoroutineScope,
    private val notifications: NotificationService,
    private val queue: RetryQueue = RetryQueue(scope, notifications::notifyRetrySuccess),
    private val sync: SyncSimulator = SyncSimulator(),
    initial: List<Expense> = emptyList(),
) : ExpenseRepository {

    private val expenses = MutableStateFlow(initial.associateBy(Expense::id))

    /** QA hook: pending background syncs; drives offline/retry UI. */
    val pendingSync = queue.pendingCount

    fun setOnline(online: Boolean) = queue.setOnline(online)

    override fun observeExpenses(groupId: GroupId): Flow<RequestStatus<List<Expense>>> =
        expenses.map { all ->
            RequestStatus.Success(
                all.values.filter { it.groupId == groupId }.sortedByDescending(Expense::createdAt),
            ) as RequestStatus<List<Expense>>
        }.onStart { emit(RequestStatus.Pending) }

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

    override suspend fun addExpense(expense: Expense): RequestStatus<Expense> {
        if (expense.amountCents < 0L) return RequestStatus.Failed("Amount must be >= 0")
        val stored = expense.copy(
            id = expense.id.ifBlank { UUID.randomUUID().toString() },
            createdAt = expense.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
        )
        expenses.update { it + (stored.id to stored) }
        queue.enqueue("add-expense:${stored.id}") { sync.sync() }
        notifications.notifyNewExpense(stored.groupId, stored)
        return RequestStatus.Success(stored)
    }

    override suspend fun updateExpense(
        expenseId: ExpenseId,
        amountCents: Long?,
        description: String?,
        splitAmong: List<String>?,
        receiptUri: String?,
    ): RequestStatus<Expense> {
        if (amountCents != null && amountCents < 0L) return RequestStatus.Failed("Amount must be >= 0")
        val current = expenses.value[expenseId] ?: return RequestStatus.Failed("Expense not found")
        val updated = current.copy(
            amountCents = amountCents ?: current.amountCents,
            description = description ?: current.description,
            splitAmong = splitAmong ?: current.splitAmong,
            receiptUri = receiptUri ?: current.receiptUri,
        )
        expenses.update { it + (expenseId to updated) }
        queue.enqueue("update-expense:$expenseId") { sync.sync() }
        return RequestStatus.Success(updated)
    }

    override fun observeAllExpenses(): Flow<RequestStatus<List<Expense>>> =
        expenses.map { all ->
            RequestStatus.Success(
                all.values.sortedByDescending(Expense::createdAt),
            ) as RequestStatus<List<Expense>>
        }.onStart { emit(RequestStatus.Pending) }

    // In-memory: nothing to fetch; collecting the cache never triggers network either.
    override suspend fun refreshAllExpenses(): Boolean = true

    override suspend fun refreshExpenses(groupId: GroupId): Boolean = true

    override suspend fun deleteExpense(expenseId: ExpenseId): RequestStatus<Unit> {
        val removed = expenses.value.containsKey(expenseId)
        expenses.update { it - expenseId }
        queue.enqueue("delete-expense:$expenseId") { sync.sync() }
        return if (removed) RequestStatus.Success(Unit) else RequestStatus.Failed("Expense not found")
    }

    override suspend fun recordSettlement(groupId: GroupId, settlement: Settlement): RequestStatus<Expense> {
        val result = addExpense(
            Expense(
                id = "",
                groupId = groupId,
                payerId = settlement.fromId,
                amountCents = settlement.amountCents,
                description = "Settlement",
                splitAmong = listOf(settlement.toId),
                // Fake mirrors the wire contract: payment rows carry the settlement kind.
                isSettlement = true,
            ),
        )
        if (result is RequestStatus.Success) {
            val nets = DebtCalculator.calculateBalances(
                expenses.value.values.filter { it.groupId == groupId },
            )
            nets[settlement.fromId]?.let { notifications.notifyDebtUpdate(settlement.fromId, Balance(settlement.fromId, it)) }
            nets[settlement.toId]?.let { notifications.notifyDebtUpdate(settlement.toId, Balance(settlement.toId, it)) }
        }
        return result
    }
}
