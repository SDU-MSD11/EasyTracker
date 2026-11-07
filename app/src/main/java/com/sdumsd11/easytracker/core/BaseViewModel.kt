package com.sdumsd11.easytracker.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * MVVM base: ops run off-main and failures become [RequestStatus.Failed] — the UI never
 * blocks and never catches. Pass the VM's injected `@IoDispatcher` (DI-free base on purpose).
 */
abstract class BaseViewModel : ViewModel() {
    protected fun <T> launchRequest(
        status: MutableStateFlow<RequestStatus<T>>,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        block: suspend () -> T,
    ) = viewModelScope.launch(dispatcher) {
        status.value = RequestStatus.Pending
        status.value = runRequest(block)
    }

    protected suspend fun <T> runRequest(block: suspend () -> T): RequestStatus<T> =
        try {
            RequestStatus.Success(block())
        } catch (e: Exception) {
            RequestStatus.Failed(e.message)
        }
}
