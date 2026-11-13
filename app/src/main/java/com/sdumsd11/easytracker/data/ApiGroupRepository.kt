package com.sdumsd11.easytracker.data

import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.core.safeApiCall
import com.sdumsd11.easytracker.data.local.toDomain
import com.sdumsd11.easytracker.data.local.toEntity
import com.sdumsd11.easytracker.data.prefs.UserPreferences
import com.sdumsd11.easytracker.data.remote.AddMemberRequest
import com.sdumsd11.easytracker.data.remote.ApiService
import com.sdumsd11.easytracker.data.remote.CreateGroupRequest
import com.sdumsd11.easytracker.data.remote.GroupDto
import com.sdumsd11.easytracker.data.remote.MemberDto
import com.sdumsd11.easytracker.data.remote.InviteRespondRequest
import com.sdumsd11.easytracker.data.remote.PatchGroupRequest
import com.sdumsd11.easytracker.data.remote.toDomain
import com.sdumsd11.easytracker.di.IoDispatcher
import com.sdumsd11.easytracker.domain.model.Group
import com.sdumsd11.easytracker.domain.model.GroupId
import com.sdumsd11.easytracker.domain.model.User
import com.sdumsd11.easytracker.domain.model.UserId
import com.sdumsd11.easytracker.data.local.EasyTrackerDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
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
    // Singleton-lived: background refreshes never block collectors (cache emits first).
    private val scope = CoroutineScope(SupervisorJob() + io)
    private val listRefresh = RefreshSingleFlight()
    private val itemRefresh = KeyedRefreshSingleFlight()
    // Last network failure per pass; an empty cache + a recorded failure emits Failed
    // (a warm cache always wins, so offline reads stay visible).
    private val listError = MutableStateFlow<RequestStatus.Failed?>(null)
    private val itemErrors = MutableStateFlow<Map<String, RequestStatus.Failed>>(emptyMap())

    // Cache-first: Room emits immediately, network refreshes in the background single-flighted,
    // so N collectors/recompositions share one pass and never gate the first frame on RTT.
    // Empty cache + failed refresh surfaces Failed (retryable) instead of a fake empty Success.
    override fun observeGroups(): Flow<RequestStatus<List<Group>>> =
        combine(groups.observeAll(), listError) { rows, err ->
            val list = rows.map { it.toDomain() }.sortedBy(Group::name)
            if (list.isEmpty() && err != null) err else RequestStatus.Success(list)
        }.onStart {
            listRefresh.launchIn(scope) { fetchGroups() }
        }.flowOn(io)

    override fun observeGroup(groupId: GroupId): Flow<RequestStatus<Group?>> =
        combine(groups.observeById(groupId), itemErrors) { row, errs ->
            val group = row?.toDomain()
            val err = errs[groupId]
            if (group == null && err != null) err else RequestStatus.Success(group)
        }.onStart {
            itemRefresh.launchIn(scope, groupId) { fetchGroup(groupId) }
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
                cacheMembers(r.data)
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
                // Direct-add contract: POST returns updated group with new ACCEPTED
                // member; cache it immediately so inviter sees member without refetch.
                // dto.members carries the new member's name/email, so the User row
                // lands too — displayName() resolves instantly.
                groups.upsert(r.data.toDomain().toEntity())
                cacheMembers(r.data)
                RequestStatus.Success(Unit)
            }
            is RequestStatus.Failed -> if (runCatching { refreshGroup(groupId) }.getOrDefault(false)) {
                RequestStatus.Success(Unit)
            } else {
                r
            }
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

    override suspend fun updateGroup(
        groupId: GroupId,
        name: String?,
        description: String?,
    ): RequestStatus<Group> = withContext(io) {
        // Backend PATCH schema is strict + name min(1): blank means "leave unchanged".
        val body = PatchGroupRequest(name?.trim()?.ifEmpty { null }, description)
        if (body.name == null && body.description == null) {
            return@withContext groups.observeById(groupId).first()?.toDomain()
                ?.let { RequestStatus.Success(it) }
                ?: RequestStatus.Failed("Group not found")
        }
        when (val r = safeApiCall(clear401) { api.patchGroup(groupId, body) }) {
            is RequestStatus.Success -> {
                val group = r.data.toDomain()
                groups.upsert(group.toEntity())
                cacheMembers(r.data)
                RequestStatus.Success(group)
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

    /** Explicit refreshes for VM first-paint gates; bypass the background throttle. */
    override suspend fun refreshGroups(): Boolean =
        listRefresh.tryRun(force = true) { fetchGroups() }

    override suspend fun refreshGroup(groupId: GroupId): Boolean =
        itemRefresh.tryRun(groupId, force = true) { fetchGroup(groupId) }

    private suspend fun fetchGroups(): Boolean {
        if (prefs.authToken.first().isNullOrBlank()) return true
        return when (val r = safeApiCall(clear401) { api.getGroups() }) {
            // Signed out while in flight (signOut clears tables): never repopulate after the wipe.
            is RequestStatus.Success -> if (prefs.authToken.first().isNullOrBlank()) {
                true
            } else {
                listError.value = null
                r.data.forEach {
                    groups.upsert(it.toDomain().toEntity())
                    cacheMembers(it)
                }
                true
            }
            is RequestStatus.Failed -> {
                listError.value = r
                false
            }
            is RequestStatus.Pending -> true
        }
    }

    private suspend fun fetchGroup(groupId: GroupId): Boolean {
        if (prefs.authToken.first().isNullOrBlank()) return true
        return when (val r = safeApiCall(clear401) { api.getGroup(groupId) }) {
            // See refreshGroups: no cache writes after sign-out.
            is RequestStatus.Success -> if (prefs.authToken.first().isNullOrBlank()) {
                true
            } else {
                itemErrors.update { it - groupId }
                groups.upsert(r.data.toDomain().toEntity())
                cacheMembers(r.data)
                true
            }
            is RequestStatus.Failed -> {
                itemErrors.update { it + (groupId to r) }
                false
            }
            is RequestStatus.Pending -> true
        }
    }

    /** Backend invites by email; domain contact carries the email (see RemoteMappers). */
    private suspend fun emailOf(userId: UserId): String? =
        users.observeById(userId).first()?.contact?.takeIf { it.contains("@") }
            ?: userId.takeIf { it.contains("@") }

    /** Wire members → Room users so displayName() resolves without a user lookup. */
    private suspend fun cacheMembers(dto: GroupDto) {
        dto.members.forEach {
            if (it.name.isBlank() && it.email.isBlank()) return@forEach
            users.upsert(mergeUser(users.observeById(it.id).first()?.toDomain(), it).toEntity())
        }
    }

    /** Merge wire member over cached row; never clobber a good cached name/contact with blank. */
    private fun mergeUser(existing: User?, wire: MemberDto): User {
        if (existing == null) return User(id = wire.id, name = wire.name, contact = wire.email)
        return existing.copy(
            name = if (wire.name.isNotBlank()) wire.name else existing.name,
            contact = if (wire.email.isNotBlank()) wire.email else existing.contact,
        )
    }
}
