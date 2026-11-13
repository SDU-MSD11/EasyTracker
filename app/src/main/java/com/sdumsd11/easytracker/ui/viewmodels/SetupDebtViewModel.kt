package com.sdumsd11.easytracker.ui.viewmodels

import androidx.lifecycle.viewModelScope
import com.sdumsd11.easytracker.core.BaseViewModel
import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.data.ExpenseRepository
import com.sdumsd11.easytracker.data.GroupRepository
import com.sdumsd11.easytracker.di.IoDispatcher
import com.sdumsd11.easytracker.domain.model.Expense
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Persists a debt as an expense split among the group members. Null while idle. */
@HiltViewModel
class SetupDebtViewModel @Inject constructor(
    private val expenses: ExpenseRepository,
    private val groups: GroupRepository,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : BaseViewModel() {

    private val _saveResult = MutableStateFlow<RequestStatus<Expense>?>(null)
    val saveResult: StateFlow<RequestStatus<Expense>?> = _saveResult.asStateFlow()

    /**
     * Saves a debt: [debtId] null creates (split among current members), non-null
     * patches that expense (amount + note; members stay as stored).
     */
    fun save(
        groupId: String,
        payerId: String,
        amountCents: Long,
        description: String,
        debtId: String? = null,
    ) = viewModelScope.launch(io) {
        _saveResult.value = RequestStatus.Pending
        _saveResult.value = runRequest {
            require(amountCents > 0L) { "Enter an amount greater than 0." }
            require(payerId.isNotBlank()) { "Not logged in." }
            if (debtId == null) {
                val members = (groups.observeGroup(groupId).first { it is RequestStatus.Success }
                    as RequestStatus.Success).data?.memberIds?.ifEmpty { null }
                    ?: listOf(payerId)
                val expense = Expense(
                    id = "",
                    groupId = groupId,
                    payerId = payerId,
                    amountCents = amountCents,
                    description = description.ifBlank { "Debt" },
                    splitAmong = members,
                )
                when (val r = expenses.addExpense(expense)) {
                    is RequestStatus.Success -> r.data
                    is RequestStatus.Failed -> throw IllegalStateException(r.message ?: "Save failed.")
                    is RequestStatus.Pending -> throw IllegalStateException("Save pending.")
                }
            } else {
                // Blank note keeps the stored text (null = "no change" on this contract).
                when (val r = expenses.updateExpense(
                    debtId,
                    amountCents = amountCents,
                    description = description.takeIf { it.isNotBlank() },
                )) {
                    is RequestStatus.Success -> r.data
                    is RequestStatus.Failed -> throw IllegalStateException(r.message ?: "Save failed.")
                    is RequestStatus.Pending -> throw IllegalStateException("Save pending.")
                }
            }
        }
    }

    /** Existing expense for the edit route (null data = unknown id); drives field preload. */
    fun editingExpense(groupId: String, debtId: String): Flow<RequestStatus<Expense?>> =
        expenses.observeExpenses(groupId).map { status ->
            when (status) {
                is RequestStatus.Success -> RequestStatus.Success(status.data.firstOrNull { it.id == debtId })
                is RequestStatus.Pending -> RequestStatus.Pending
                is RequestStatus.Failed -> RequestStatus.Failed(status.message, status.retryable)
            }
        }

    fun clearSaveResult() {
        _saveResult.value = null
    }
}
