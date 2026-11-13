package com.sdumsd11.easytracker.data

import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.core.safeApiCall
import com.sdumsd11.easytracker.data.local.EasyTrackerDatabase
import com.sdumsd11.easytracker.data.local.toDomain
import com.sdumsd11.easytracker.data.local.toEntity
import com.sdumsd11.easytracker.data.prefs.UserPreferences
import com.sdumsd11.easytracker.data.remote.ApiService
import com.sdumsd11.easytracker.data.remote.LoginRequest
import com.sdumsd11.easytracker.data.remote.RegisterRequest
import com.sdumsd11.easytracker.data.remote.toDomain
import com.sdumsd11.easytracker.di.IoDispatcher
import com.sdumsd11.easytracker.domain.model.NotificationPrefs
import com.sdumsd11.easytracker.domain.model.User
import com.sdumsd11.easytracker.domain.model.UserId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/**
 * UserRepository: member cache reads come from Room (backend has no user-list
 * route, so [upsertUser] is local-only); the device profile is DataStore-backed
 * (source of truth, survives restart) with Room as mirror. No PATCH /me route
 * exists, so /me sync is explicit via [refreshMe] and never clobbers local
 * name/contact. Auth extras ([register]/[login]/[signOut])
 * persist the JWT via [UserPreferences]. 401 clears the token.
 */
@Singleton
class ApiUserRepository @Inject constructor(
    private val api: ApiService,
    private val db: EasyTrackerDatabase,
    private val prefs: UserPreferences,
    @IoDispatcher private val io: CoroutineDispatcher,
) : UserRepository {
    private val users = db.userDao()
    private val clear401: suspend () -> Unit = { prefs.clearAuth() }

    override fun observeUsers(): Flow<RequestStatus<List<User>>> =
        users.observeAll().map { rows ->
            RequestStatus.Success(rows.map { it.toDomain() }.sortedBy(User::name)) as RequestStatus<List<User>>
        }.onStart { emit(RequestStatus.Pending) }.flowOn(io)

    override fun observeUser(userId: UserId): Flow<RequestStatus<User?>> =
        users.observeById(userId).map { row ->
            RequestStatus.Success(row?.toDomain()) as RequestStatus<User?>
        }.onStart { emit(RequestStatus.Pending) }.flowOn(io)

    override suspend fun upsertUser(user: User): RequestStatus<User> = withContext(io) {
        if (user.name.isBlank()) return@withContext RequestStatus.Failed("User name must not be blank")
        users.upsert(user.toEntity())
        RequestStatus.Success(user)
    }

    override suspend fun updateNotificationPrefs(userId: UserId, value: NotificationPrefs): RequestStatus<Unit> =
        withContext(io) {
            val current = users.observeById(userId).first() ?: return@withContext RequestStatus.Failed("User not found")
            users.upsert(current.toDomain().copy(notificationPrefs = value).toEntity())
            if (prefs.profile.first()?.id == userId) prefs.saveNotificationPrefs(value)
            RequestStatus.Success(Unit)
        }

    override fun observeCurrentProfile(): Flow<RequestStatus<User?>> =
        prefs.profile.map { RequestStatus.Success(it) as RequestStatus<User?> }
            .onStart { emit(RequestStatus.Pending) }.flowOn(io)

    /** Local-only save (no PATCH /me route): DataStore first, Room mirror second. */
    override suspend fun saveCurrentProfile(user: User): RequestStatus<Unit> = withContext(io) {
        if (user.name.isBlank()) return@withContext RequestStatus.Failed("Name must not be blank.")
        prefs.saveProfile(user)
        users.upsert(user.toEntity())
        RequestStatus.Success(Unit)
    }

    /** POST /auth/register — stores token + profile on success. */
    suspend fun register(name: String, email: String, password: String): RequestStatus<User> = withContext(io) {
        when (val r = safeApiCall { api.register(RegisterRequest(name.trim(), email.trim(), password)) }) {
            is RequestStatus.Success -> persistAuth(r.data.token, r.data.user.toDomain())
            is RequestStatus.Failed -> r
            is RequestStatus.Pending -> r
        }
    }

    /** POST /auth/login — stores token + profile on success. */
    suspend fun login(email: String, password: String): RequestStatus<User> = withContext(io) {
        when (val r = safeApiCall { api.login(LoginRequest(email.trim(), password)) }) {
            is RequestStatus.Success -> persistAuth(r.data.token, r.data.user.toDomain())
            is RequestStatus.Failed -> r
            is RequestStatus.Pending -> r
        }
    }

    /**
     * GET /me — explicit best-effort sync (never auto-run from [observeCurrentProfile]).
     * No PATCH /me route exists, so DataStore stays source of truth for name/contact:
     * a local profile keeps its name/contact and only the Room mirror is refreshed.
     */
    suspend fun refreshMe(): RequestStatus<User> = withContext(io) {
        when (val r = safeApiCall(clear401) { api.me() }) {
            is RequestStatus.Success -> {
                // Signed out while in flight: never rewrite the profile after the wipe.
                if (prefs.authToken.first().isNullOrBlank()) {
                    return@withContext RequestStatus.Failed("Signed out", retryable = false)
                }
                val keep = prefs.profile.first()
                val user = keep?.copy(id = r.data.id) ?: r.data.toDomain()
                prefs.saveProfile(user)
                users.upsert(user.toEntity())
                RequestStatus.Success(user)
            }
            is RequestStatus.Failed -> r
            is RequestStatus.Pending -> r
        }
    }

    /** Sign-out: clears DataStore session + profile and wipes the Room cache. */
    suspend fun signOut(): Unit = withContext(io) {
        prefs.clear()
        db.clearAllTables()
    }

    private suspend fun persistAuth(token: String, user: User): RequestStatus<User> {
        // toDomain folds the backend email into contact, so contact carries the email here.
        prefs.saveAuth(token, user.id, user.contact)
        val keep = prefs.profile.first()?.notificationPrefs ?: NotificationPrefs()
        val merged = user.copy(notificationPrefs = keep)
        prefs.saveProfile(merged)
        users.upsert(merged.toEntity())
        return RequestStatus.Success(merged)
    }
}
