package com.sdumsd11.easytracker.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sdumsd11.easytracker.domain.model.NotificationPrefs
import com.sdumsd11.easytracker.domain.model.User
import com.sdumsd11.easytracker.domain.model.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val KEY_ID = stringPreferencesKey("profile_id")
private val KEY_NAME = stringPreferencesKey("profile_name")
private val KEY_CONTACT = stringPreferencesKey("profile_contact")
private val KEY_NEW_EXPENSE = booleanPreferencesKey("notify_new_expense")
private val KEY_REMINDERS = booleanPreferencesKey("notify_reminders")
private val KEY_SETTLEMENTS = booleanPreferencesKey("notify_settlements")
private val KEY_AUTH_TOKEN = stringPreferencesKey("auth_token")
private val KEY_AUTH_USER_ID = stringPreferencesKey("auth_user_id")
private val KEY_AUTH_EMAIL = stringPreferencesKey("auth_email")

/**
 * Current-device profile + notification settings store.
 * The [DataStore] is Hilt-provided (see `di.AppModule`) — single prefs file, no own delegate.
 * Requires (arch owner): `androidx.datastore:datastore-preferences`. minSdk 24 safe.
 */
class UserPreferences(private val dataStore: DataStore<Preferences>) {

    val profile: Flow<User?> = dataStore.data.map { prefs ->
        val id: UserId = prefs[KEY_ID] ?: return@map null
        User(
            id = id,
            name = prefs[KEY_NAME] ?: "",
            contact = prefs[KEY_CONTACT] ?: "",
            notificationPrefs = NotificationPrefs(
                newExpense = prefs[KEY_NEW_EXPENSE] ?: true,
                reminders = prefs[KEY_REMINDERS] ?: true,
                settlementUpdates = prefs[KEY_SETTLEMENTS] ?: true,
            ),
        )
    }

    suspend fun saveProfile(user: User) {
        dataStore.edit {
            it[KEY_ID] = user.id
            it[KEY_NAME] = user.name
            it[KEY_CONTACT] = user.contact
            it[KEY_NEW_EXPENSE] = user.notificationPrefs.newExpense
            it[KEY_REMINDERS] = user.notificationPrefs.reminders
            it[KEY_SETTLEMENTS] = user.notificationPrefs.settlementUpdates
        }
    }

    suspend fun saveNotificationPrefs(prefs: NotificationPrefs) {
        dataStore.edit {
            it[KEY_NEW_EXPENSE] = prefs.newExpense
            it[KEY_REMINDERS] = prefs.reminders
            it[KEY_SETTLEMENTS] = prefs.settlementUpdates
        }
    }

    /** Bearer JWT + identity. Null token = signed out; interceptor sends no header. */
    val authToken: Flow<String?> = dataStore.data.map { it[KEY_AUTH_TOKEN] }

    val authUserId: Flow<UserId?> = dataStore.data.map { it[KEY_AUTH_USER_ID] }

    val authEmail: Flow<String?> = dataStore.data.map { it[KEY_AUTH_EMAIL] }

    suspend fun saveAuth(token: String, userId: UserId, email: String) {
        dataStore.edit {
            it[KEY_AUTH_TOKEN] = token
            it[KEY_AUTH_USER_ID] = userId
            it[KEY_AUTH_EMAIL] = email
        }
    }

    suspend fun clearAuth() {
        dataStore.edit {
            it.remove(KEY_AUTH_TOKEN)
            it.remove(KEY_AUTH_USER_ID)
            it.remove(KEY_AUTH_EMAIL)
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
