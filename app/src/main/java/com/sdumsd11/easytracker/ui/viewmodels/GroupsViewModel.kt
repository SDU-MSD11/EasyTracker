package com.sdumsd11.easytracker.ui.viewmodels

import androidx.lifecycle.viewModelScope
import com.sdumsd11.easytracker.core.BaseViewModel
import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.data.GroupRepository
import com.sdumsd11.easytracker.data.prefs.UserPreferences
import com.sdumsd11.easytracker.di.IoDispatcher
import com.sdumsd11.easytracker.domain.model.Group
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Group list + creation. Owner is the current session profile (auth-gate guarantees login). */
@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val groups: GroupRepository,
    private val prefs: UserPreferences,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : BaseViewModel() {

    val groupsState: StateFlow<RequestStatus<List<Group>>> = groups.observeGroups()
        .flowOn(io)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RequestStatus.Pending)

    /** Null while idle so the create sheet shows no spinner on first paint. */
    private val _createResult = MutableStateFlow<RequestStatus<Group>?>(null)
    val createResult: StateFlow<RequestStatus<Group>?> = _createResult.asStateFlow()

    fun createGroup(name: String, description: String, memberIds: List<String> = emptyList()) =
        viewModelScope.launch(io) {
            _createResult.value = RequestStatus.Pending
            _createResult.value = runRequest {
                require(name.isNotBlank()) { "Group name must not be blank." }
                val ownerId = prefs.profile.first()?.id
                    ?: throw IllegalStateException("Not logged in.")
                // TODO(api-integration): POST /api/groups {name, description, memberEmails}.
                when (val r = groups.createGroup(name.trim(), description, ownerId, memberIds)) {
                    is RequestStatus.Success -> r.data
                    is RequestStatus.Failed -> throw IllegalStateException(r.message ?: "Create failed.")
                    is RequestStatus.Pending -> throw IllegalStateException("Create pending.")
                }
            }
        }

    fun clearCreateResult() {
        _createResult.value = null
    }
}
