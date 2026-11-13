package com.sdumsd11.easytracker.core

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Session-scoped theme holder (process lifetime). Plain MVVM state shared by
 * MainActivity (applies) and ProfileViewModel (toggles) — both read the same
 * @Singleton instead of entry-scoped copies. Handoff (data owner): back this with a
 * DataStore key in UserPreferences when the persisted theme contract lands.
 */
@Singleton
class ThemeSettings @Inject constructor() {
    private val _darkTheme = MutableStateFlow(false)
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    fun setDarkTheme(dark: Boolean) {
        _darkTheme.value = dark
    }
}
