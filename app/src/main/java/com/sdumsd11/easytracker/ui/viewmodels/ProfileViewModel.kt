package com.sdumsd11.easytracker.ui.viewmodels

import androidx.lifecycle.viewModelScope
import com.sdumsd11.easytracker.core.BaseViewModel
import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.core.ThemeSettings
import com.sdumsd11.easytracker.data.UserRepository
import com.sdumsd11.easytracker.di.IoDispatcher
import com.sdumsd11.easytracker.domain.model.NotificationPrefs
import com.sdumsd11.easytracker.domain.model.User
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

/** Device profile + notification prefs (DataStore-backed in the real repo). */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val users: UserRepository,
    private val theme: ThemeSettings,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : BaseViewModel() {

    val profile: StateFlow<RequestStatus<User?>> = users.observeCurrentProfile()
        .flowOn(io)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RequestStatus.Pending)

    /** Null while idle so the form shows no spinner on first paint. */
    private val _saveResult = MutableStateFlow<RequestStatus<Unit>?>(null)
    val saveResult: StateFlow<RequestStatus<Unit>?> = _saveResult.asStateFlow()

    /** Persists name/contact onto the current profile (no PATCH /me route exists). */
    fun save(name: String, contact: String) = viewModelScope.launch(io) {
        _saveResult.value = RequestStatus.Pending
        _saveResult.value = runRequest {
            require(name.isNotBlank()) { "Name must not be blank." }
            val current = (users.observeCurrentProfile().first { it is RequestStatus.Success }
                as RequestStatus.Success).data ?: throw IllegalStateException("Not logged in.")
            when (val r = users.saveCurrentProfile(current.copy(name = name.trim(), contact = contact.trim()))) {
                is RequestStatus.Success -> Unit
                is RequestStatus.Failed -> throw IllegalStateException(r.message ?: "Save failed.")
                is RequestStatus.Pending -> throw IllegalStateException("Save pending.")
            }
        }
    }

    fun clearSaveResult() {
        _saveResult.value = null
    }

    fun updatePrefs(prefs: NotificationPrefs) = viewModelScope.launch(io) {
        val id = (profile.value as? RequestStatus.Success)?.data?.id ?: return@launch
        users.updateNotificationPrefs(id, prefs)
    }

    /** App-wide dark theme (session-scoped until the prefs key lands — see ThemeSettings). */
    val darkTheme: StateFlow<Boolean> = theme.darkTheme

    fun setDarkTheme(dark: Boolean) = theme.setDarkTheme(dark)
}
