package com.sdumsd11.easytracker.ui

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * UI-owned status event bus — the decoupled hook the architecture owner feeds.
 *
 * Screens stay stateless: VMs/repos never touch UI here. When the retry/outbox
 * owner eventually completes a previously failed request in the background, it
 * calls [notify] with [StatusKind.BackgroundSuccess] and the app surfaces a
 * Snackbar + haptic + short beep via `BackgroundSuccessHost` (no new
 * permission: haptics and ToneGenerator are permission-free). Ordinary inline
 * confirmations (profile saved, debt saved) use [StatusKind.Immediate], which
 * shows the Snackbar silently.
 */
enum class StatusKind { Immediate, BackgroundSuccess }

data class StatusEvent(
    val key: String,
    val message: String,
    val kind: StatusKind = StatusKind.Immediate,
)

@Singleton
class StatusBus @Inject constructor() {
    private val _events = MutableSharedFlow<StatusEvent>(replay = 1, extraBufferCapacity = 8)
    val events: SharedFlow<StatusEvent> = _events.asSharedFlow()

    /** Non-suspending emit; drops only if the buffer is full (never blocks callers). */
    fun notify(event: StatusEvent) {
        _events.tryEmit(event)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface StatusBusEntryPoint {
    fun statusBus(): StatusBus
}

/** Hilt accessor for graph-level hosts (AppNavHost) — VMs get [StatusBus] via @Inject. */
fun Context.statusBus(): StatusBus =
    EntryPointAccessors.fromApplication(this, StatusBusEntryPoint::class.java).statusBus()
