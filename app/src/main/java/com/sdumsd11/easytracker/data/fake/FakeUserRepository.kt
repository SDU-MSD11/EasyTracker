package com.sdumsd11.easytracker.data.fake

import com.sdumsd11.easytracker.data.NotificationService
import com.sdumsd11.easytracker.data.UserRepository
import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.domain.model.NotificationPrefs
import com.sdumsd11.easytracker.domain.model.User
import com.sdumsd11.easytracker.domain.model.UserId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

/**
 * In-memory UserRepository. The real impl backs [observeCurrentProfile]/
 * [saveCurrentProfile] with `data.prefs.UserPreferences` (DataStore);
 * member cache reads go through Room (see `data.local`).
 */
class FakeUserRepository(
    scope: CoroutineScope,
    private val notifications: NotificationService,
    private val queue: RetryQueue = RetryQueue(scope, notifications::notifyRetrySuccess),
    private val sync: SyncSimulator = SyncSimulator(),
    initial: List<User> = emptyList(),
    currentUserId: UserId? = null,
) : UserRepository {

    private val users = MutableStateFlow(initial.associateBy(User::id))
    private val currentId = MutableStateFlow(currentUserId)

    /** QA hook: pending background syncs; drives offline/retry UI. */
    val pendingSync = queue.pendingCount

    fun setOnline(online: Boolean) = queue.setOnline(online)

    override fun observeUsers(): Flow<RequestStatus<List<User>>> =
        users.map { RequestStatus.Success(it.values.sortedBy(User::name)) as RequestStatus<List<User>> }
            .onStart { emit(RequestStatus.Pending) }

    override fun observeUser(userId: UserId): Flow<RequestStatus<User?>> =
        users.map { RequestStatus.Success(it[userId]) as RequestStatus<User?> }
            .onStart { emit(RequestStatus.Pending) }

    override suspend fun upsertUser(user: User): RequestStatus<User> {
        if (user.name.isBlank()) return RequestStatus.Failed("User name must not be blank")
        users.update { it + (user.id to user) }
        queue.enqueue("upsert-user:${user.id}") { sync.sync() }
        return RequestStatus.Success(user)
    }

    override suspend fun updateNotificationPrefs(userId: UserId, prefs: NotificationPrefs): RequestStatus<Unit> {
        val current = users.value[userId] ?: return RequestStatus.Failed("User not found")
        users.update { it + (userId to current.copy(notificationPrefs = prefs)) }
        queue.enqueue("prefs:$userId") { sync.sync() }
        return RequestStatus.Success(Unit)
    }

    override fun observeCurrentProfile(): Flow<RequestStatus<User?>> =
        users.map { RequestStatus.Success(it[currentId.value]) as RequestStatus<User?> }
            .onStart { emit(RequestStatus.Pending) }

    override suspend fun saveCurrentProfile(user: User): RequestStatus<Unit> =
        when (val result = upsertUser(user)) {
            is RequestStatus.Success -> {
                currentId.value = user.id
                RequestStatus.Success(Unit)
            }
            is RequestStatus.Failed -> result
            is RequestStatus.Pending -> result
        }
}
