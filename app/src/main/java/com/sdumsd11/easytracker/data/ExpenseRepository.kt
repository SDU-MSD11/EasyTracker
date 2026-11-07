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

    suspend fun deleteExpense(expenseId: ExpenseId): RequestStatus<Unit>

    /**
     * Records a repayment as a balancing expense
     * (`payer = fromId`, split among `[toId]`), so balances update through the same
     * pure path and payment history shows the settlement. Triggers debt-update notify.
     */
    suspend fun recordSettlement(groupId: GroupId, settlement: Settlement): RequestStatus<Expense>
}
