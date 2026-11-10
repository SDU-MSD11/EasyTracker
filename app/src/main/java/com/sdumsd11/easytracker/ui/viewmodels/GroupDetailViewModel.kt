package com.sdumsd11.easytracker.ui.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sdumsd11.easytracker.core.BaseViewModel
import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.data.ExpenseRepository
import com.sdumsd11.easytracker.data.GroupRepository
import com.sdumsd11.easytracker.data.prefs.UserPreferences
import com.sdumsd11.easytracker.di.IoDispatcher
import com.sdumsd11.easytracker.domain.DebtCalculator
import com.sdumsd11.easytracker.domain.model.DebtSummary
import com.sdumsd11.easytracker.domain.model.Group
import com.sdumsd11.easytracker.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Group detail: group + debt summary derived from expenses via [DebtCalculator].
 * [isOwner] is null until group+profile load — the EditGroup owner-gate redirects
 * only on `false`, never while unknown. groupId comes from the nav arg via SavedStateHandle.
 */
@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    groups: GroupRepository,
    expenses: ExpenseRepository,
    prefs: UserPreferences,
    @IoDispatcher io: CoroutineDispatcher,
) : BaseViewModel() {

    private val groupId: String = savedStateHandle.get<String>(Routes.ArgGroupId).orEmpty()

    val group: StateFlow<RequestStatus<Group?>> = groups.observeGroup(groupId)
        .flowOn(io)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RequestStatus.Pending)

    // TODO(api-integration): prefer GET /api/groups/:id/summary when online; keep this pure
    // derivation as the offline path so detail renders from cache.
    val summary: StateFlow<RequestStatus<DebtSummary>> = expenses.observeExpenses(groupId)
        .map { status ->
            when (status) {
                is RequestStatus.Success -> RequestStatus.Success(DebtCalculator.summarize(status.data))
                is RequestStatus.Pending -> RequestStatus.Pending
                is RequestStatus.Failed -> RequestStatus.Failed(status.message, status.retryable)
            }
        }
        .flowOn(io)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RequestStatus.Pending)

    val isOwner: StateFlow<Boolean?> =
        combine(group, prefs.profile) { g, me ->
            val ownerId = (g as? RequestStatus.Success)?.data?.ownerId ?: return@combine null
            me?.let { it.id == ownerId }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
