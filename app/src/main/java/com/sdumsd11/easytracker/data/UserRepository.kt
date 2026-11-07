package com.sdumsd11.easytracker.data

import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.domain.model.NotificationPrefs
import com.sdumsd11.easytracker.domain.model.User
import com.sdumsd11.easytracker.domain.model.UserId
import kotlinx.coroutines.flow.Flow

/** Member lookup + profile/settings routes. Real impl backs profile calls with DataStore. */
interface UserRepository {
    fun observeUsers(): Flow<RequestStatus<List<User>>>

    fun observeUser(userId: UserId): Flow<RequestStatus<User?>>

    suspend fun upsertUser(user: User): RequestStatus<User>

    suspend fun updateNotificationPrefs(userId: UserId, prefs: NotificationPrefs): RequestStatus<Unit>

    /** Current-device profile (DataStore-backed in the real impl). */
    fun observeCurrentProfile(): Flow<RequestStatus<User?>>

    suspend fun saveCurrentProfile(user: User): RequestStatus<Unit>
}
