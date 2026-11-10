package com.sdumsd11.easytracker.data

import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.core.safeApiCall
import com.sdumsd11.easytracker.data.local.toDomain
import com.sdumsd11.easytracker.data.local.toEntity
import com.sdumsd11.easytracker.data.prefs.UserPreferences
import com.sdumsd11.easytracker.data.remote.AddMemberRequest
import com.sdumsd11.easytracker.data.remote.ApiService
import com.sdumsd11.easytracker.data.remote.CreateGroupRequest
import com.sdumsd11.easytracker.data.remote.InviteRespondRequest
import com.sdumsd11.easytracker.data.remote.toDomain
import com.sdumsd11.easytracker.di.IoDispatcher
import com.sdumsd11.easytracker.domain.model.Group
import com.sdumsd11.easytracker.domain.model.GroupId
import com.sdumsd11.easytracker.domain.model.UserId
import com.sdumsd11.easytracker.data.local.EasyTrackerDatabase
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
 * Offline-first GroupRepository: Room cache is the source of truth for reads
 * (stays visible offline); every read triggers a best-effort network refresh,
 * writes go to the network first then update the cache. 401 clears the token.
 * Fake* repos stay for tests. No Hilt binding yet — arch owner wires it.
 */
@Singleton
class ApiGroupRepository @Inject constructor(
    private val api: ApiService,
    db: EasyTrackerDatabase,
    private val prefs: UserPreferences,
    @IoDispatcher private val io: CoroutineDispatcher,
) : GroupRepository {
    private val groups = db.groupDao()
    private val users = db.userDao()
    private val clear401: suspend () -> Unit = { prefs.clearAuth() }

    override fun observeGroups(): Flow<RequestStatus<List<Group>>> =
        groups.observeAll().map { rows ->
            RequestStatus.Success(rows.map { it.toDomain() }.sortedBy(Group::name)) as RequestStatus<List<Group>>
        }.onStart {
            emit(RequestStatus.Pending)
            runCatching { refreshGroups() }
        }.flowOn(io)

    override fun observeGroup(groupId: GroupId): Flow<RequestStatus<Group?>> =
        groups.observeById(groupId).map { row ->
            RequestStatus.Success(row?.toDomain()) as RequestStatus<Group?>
        }.onStart {
            emit(RequestStatus.Pending)
            runCatching { refreshGroup(groupId) }
        }.flowOn(io)

    override suspend fun createGroup(
        name: String,
        description: String,
        ownerId: UserId,
        memberIds: List<UserId>,
    ): RequestStatus<Group> = withContext(io) {
        if (name.isBlank()) return@withContext RequestStatus.Failed("Group name must not be blank")
        val emails = memberIds.filter { it != ownerId }.mapNotNull { emailOf(it) }
        when (val r = safeApiCall(clear401) {
            api.createGroup(CreateGroupRequest(name.trim(), description, emails))
        }) {
            is RequestStatus.Success -> {
                val group = r.data.toDomain()
                groups.upsert(group.toEntity())
                RequestStatus.Success(group)
            }
            is RequestStatus.Failed -> r
            is RequestStatus.Pending -> r
        }
    }

    override suspend fun addMember(groupId: GroupId, userId: UserId): RequestStatus<Unit> = withContext(io) {
        val email = emailOf(userId) ?: return@withContext RequestStatus.Failed("No email for member")
        when (val r = safeApiCall(clear401) { api.addMember(groupId, AddMemberRequest(email)) }) {
            is RequestStatus.Success -> {
                runCatching { refreshGroup(groupId) }
                RequestStatus.Success(Unit)
            }
            is RequestStatus.Failed -> r
            is RequestStatus.Pending -> r
        }
    }

    override suspend fun removeMember(groupId: GroupId, userId: UserId): RequestStatus<Unit> = withContext(io) {
        when (val r = safeApiCall(clear401) { api.removeMember(groupId, userId) }) {
            is RequestStatus.Success -> {
                runCatching { refreshGroup(groupId) }
                RequestStatus.Success(Unit)
            }
            is RequestStatus.Failed -> r
            is RequestStatus.Pending -> r
        }
    }

    override suspend fun respondToInvite(groupId: GroupId, userId: UserId, accept: Boolean): RequestStatus<Unit> =
        withContext(io) {
            // userId is the local caller; backend derives the invitee from the JWT.
            when (val r = safeApiCall(clear401) { api.respondToInvite(groupId, InviteRespondRequest(accept)) }) {
                is RequestStatus.Success -> {
                    runCatching { refreshGroup(groupId) }
                    RequestStatus.Success(Unit)
                }
                is RequestStatus.Failed -> r
                is RequestStatus.Pending -> r
            }
        }

    override suspend fun deleteGroup(groupId: GroupId): RequestStatus<Unit> = withContext(io) {
        when (val r = safeApiCall(clear401) { api.deleteGroup(groupId) }) {
            is RequestStatus.Success -> {
                groups.deleteById(groupId)
                RequestStatus.Success(Unit)
            }
            is RequestStatus.Failed -> r
            is RequestStatus.Pending -> r
        }
    }

    private suspend fun refreshGroups() {
        when (val r = safeApiCall(clear401) { api.getGroups() }) {
            is RequestStatus.Success -> r.data.forEach { groups.upsert(it.toDomain().toEntity()) }
            else -> Unit
        }
    }

    private suspend fun refreshGroup(groupId: GroupId) {
        when (val r = safeApiCall(clear401) { api.getGroup(groupId) }) {
            is RequestStatus.Success -> groups.upsert(r.data.toDomain().toEntity())
            else -> Unit
        }
    }

    /** Backend invites by email; domain contact carries the email (see RemoteMappers). */
    private suspend fun emailOf(userId: UserId): String? =
        users.observeById(userId).first()?.contact?.takeIf { it.contains("@") }
            ?: userId.takeIf { it.contains("@") }
}
