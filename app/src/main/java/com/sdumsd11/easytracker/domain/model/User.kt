package com.sdumsd11.easytracker.domain.model

/** Stable user identifier. */
typealias UserId = String

/** Per-user notification toggles, persisted via DataStore (see `data.prefs`). */
data class NotificationPrefs(
    val newExpense: Boolean = true,
    val reminders: Boolean = true,
    val settlementUpdates: Boolean = true,
)

/** Pure domain user. No framework types. */
data class User(
    val id: UserId,
    val name: String,
    val contact: String = "",
    val notificationPrefs: NotificationPrefs = NotificationPrefs(),
)
