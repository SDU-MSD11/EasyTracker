package com.sdumsd11.easytracker.ui.viewmodels

import androidx.lifecycle.viewModelScope
import com.sdumsd11.easytracker.core.BaseViewModel
import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.data.ApiUserRepository
import com.sdumsd11.easytracker.data.prefs.UserPreferences
import com.sdumsd11.easytracker.di.IoDispatcher
import com.sdumsd11.easytracker.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Auth session owner. [session] is Pending until the token read lands, Success(null) when
 * logged out, Success(user) when logged in — the nav auth-gate reads exactly this.
 * [authResult] is null while idle so forms don't show a spinner on first paint.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val users: ApiUserRepository,
    private val prefs: UserPreferences,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : BaseViewModel() {

    val session: StateFlow<RequestStatus<User?>> =
        combine(prefs.authToken, prefs.profile) { token, profile ->
            if (token.isNullOrBlank()) RequestStatus.Success(null)
            else RequestStatus.Success(profile)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, RequestStatus.Pending)

    private val _authResult = MutableStateFlow<RequestStatus<User>?>(null)
    val authResult: StateFlow<RequestStatus<User>?> = _authResult.asStateFlow()

    fun login(email: String, password: String) = viewModelScope.launch(io) {
        _authResult.value = RequestStatus.Pending
        _authResult.value = try {
            validateCredentials(email, password)
            users.login(email, password)
        } catch (e: IllegalArgumentException) {
            RequestStatus.Failed(e.message)
        }
    }

    fun register(name: String, email: String, password: String) = viewModelScope.launch(io) {
        _authResult.value = RequestStatus.Pending
        _authResult.value = try {
            require(name.isNotBlank()) { "Name must not be blank." }
            validateCredentials(email, password)
            users.register(name, email, password)
        } catch (e: IllegalArgumentException) {
            RequestStatus.Failed(e.message)
        }
    }

    fun logout() {
        _authResult.value = null
        viewModelScope.launch(io) { users.logout() }
    }

    fun clearResult() {
        _authResult.value = null
    }

    private fun validateCredentials(email: String, password: String) {
        require(email.isNotBlank() && "@" in email) { "Enter a valid email." }
        require(password.length >= 8) { "Password must be at least 8 characters." }
    }
}
