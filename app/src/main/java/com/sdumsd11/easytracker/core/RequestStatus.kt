package com.sdumsd11.easytracker.core

/**
 * Reliability contract (spec QA): every repo/VM op exposes its status as data.
 * Failed requests surface here — they never block the UI and never throw to composables.
 * VMs expose these as `StateFlow`; outbox jobs (RetryQueue today, WorkManager when wired)
 * map completion to this.
 */
sealed interface RequestStatus<out T> {
    data object Pending : RequestStatus<Nothing>

    data class Success<T>(val data: T) : RequestStatus<T>

    data class Failed(val message: String?, val retryable: Boolean = true) : RequestStatus<Nothing>
}
