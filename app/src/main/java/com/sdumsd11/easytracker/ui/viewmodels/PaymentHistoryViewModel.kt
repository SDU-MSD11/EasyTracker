package com.sdumsd11.easytracker.ui.viewmodels

import androidx.lifecycle.viewModelScope
import com.sdumsd11.easytracker.core.BaseViewModel
import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.data.ExpenseRepository
import com.sdumsd11.easytracker.di.IoDispatcher
import com.sdumsd11.easytracker.domain.model.Expense
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Cross-group payment history. The cache flow never triggers network (collecting it from
 * Account chips is free); freshness comes only from explicit [refresh]/[retry].
 */
@HiltViewModel
class PaymentHistoryViewModel @Inject constructor(
    private val expenses: ExpenseRepository,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : BaseViewModel() {

    val history: StateFlow<RequestStatus<List<Expense>>> = expenses.observeAllExpenses()
        .flowOn(io)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RequestStatus.Pending)

    /** Last explicit-refresh failure (null when fresh); the nav error slot reads this. */
    private val _refreshError = MutableStateFlow<String?>(null)
    val refreshError: StateFlow<String?> = _refreshError.asStateFlow()

    /** First successful explicit refresh; the nav hasLoaded gate reads this. */
    private val _refreshedOnce = MutableStateFlow(false)
    val refreshedOnce: StateFlow<Boolean> = _refreshedOnce.asStateFlow()

    private var lastRefreshMs = 0L

    /** Explicit history refresh (History first paint / retry). Debounced + repo single-flighted. */
    fun refresh() {
        val now = System.currentTimeMillis()
        if (now - lastRefreshMs < 1_000L) return
        lastRefreshMs = now
        viewModelScope.launch(io) {
            // Boolean contract: false = network failed (cached history still shows).
            val ok = runCatching { expenses.refreshAllExpenses() }.getOrDefault(false)
            if (ok) {
                _refreshedOnce.value = true
                _refreshError.value = null
            } else {
                _refreshError.value = "Couldn't refresh history."
            }
        }
    }

    /** Error-state retry: forces one explicit refresh (re-collecting the cache needs no re-subscribe). */
    fun retry() {
        lastRefreshMs = 0L
        _refreshError.value = null
        refresh()
    }
}
