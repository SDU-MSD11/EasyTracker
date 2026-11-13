package com.sdumsd11.easytracker.data

import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.domain.model.Balance
import com.sdumsd11.easytracker.domain.model.Expense
import com.sdumsd11.easytracker.domain.model.ExpenseId
import com.sdumsd11.easytracker.domain.model.GroupId
import com.sdumsd11.easytracker.domain.model.Settlement
import kotlinx.coroutines.flow.Flow

/** Payment-history + balances routes. Balances/settlements are derived from expenses. */
interface ExpenseRepository {
    /** Payment history for a group, newest first. */
    fun observeExpenses(groupId: GroupId): Flow<RequestStatus<List<Expense>>>

    /** Per-user nets (positive = owed to them), derived via DebtCalculator. */
    fun observeBalances(groupId: GroupId): Flow<RequestStatus<List<Balance>>>

    /** Minimal repayments to clear the group, derived via DebtCalculator. */
    fun observeSettlements(groupId: GroupId): Flow<RequestStatus<List<Settlement>>>

    suspend fun addExpense(expense: Expense): RequestStatus<Expense>

    /**
     * PATCH /api/expenses/:id; null fields are left unchanged. Null [receiptUri]
     * keeps the stored receipt (backend null also means "no change" here — clearing
     * a receipt needs a dedicated contract).
     */
    suspend fun updateExpense(
        expenseId: ExpenseId,
        amountCents: Long? = null,
        description: String? = null,
        splitAmong: List<String>? = null,
        receiptUri: String? = null,
    ): RequestStatus<Expense>

    /** Cross-group payment history, newest first (Room cache; never triggers network). */
    fun observeAllExpenses(): Flow<RequestStatus<List<Expense>>>

    /**
     * Explicit cross-group refresh (parallel per-group fetch, single-flighted,
     * bypasses the background throttle). Returns false when any group fetch
     * failed — the flow surfaces it when the cache is empty.
     * History calls this on first paint/retry; Account chips only collect the cache.
     */
    suspend fun refreshAllExpenses(): Boolean

    /**
     * Explicit per-group refresh (single-flighted, bypasses the background
     * throttle); false on network failure. Detail calls this on first paint
     * while the cache flow stays the instant source of truth.
     */
    suspend fun refreshExpenses(groupId: GroupId): Boolean

    suspend fun deleteExpense(expenseId: ExpenseId): RequestStatus<Unit>

    /**
     * Records a repayment as a balancing expense
     * (`payer = fromId`, split among `[toId]`), so balances update through the same
     * pure path and payment history shows the settlement. Triggers debt-update notify.
     */
    suspend fun recordSettlement(groupId: GroupId, settlement: Settlement): RequestStatus<Expense>
}
