package com.sdumsd11.easytracker.data

import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.domain.model.Group
import com.sdumsd11.easytracker.domain.model.GroupId
import com.sdumsd11.easytracker.domain.model.UserId
import kotlinx.coroutines.flow.Flow

/** Group list + group-detail routes. Implementations stay offline-first (Room cache / fakes). */
interface GroupRepository {
    /** All groups, for the group list route. */
    fun observeGroups(): Flow<RequestStatus<List<Group>>>

    /** Single group detail (members + invites included). */
    fun observeGroup(groupId: GroupId): Flow<RequestStatus<Group?>>

    /**
     * Explicit list refresh (single-flighted, bypasses the background throttle).
     * Returns false when the network failed — the flow surfaces it when the cache
     * is empty. Groups calls this on first paint while the cache flow stays the
     * instant source of truth.
     */
    suspend fun refreshGroups(): Boolean

    /**
     * Explicit single-group refresh (single-flighted, bypasses the background
     * throttle); false on network failure. Detail calls on first paint.
     */
    suspend fun refreshGroup(groupId: GroupId): Boolean

    suspend fun createGroup(
        name: String,
        description: String,
        ownerId: UserId,
        memberIds: List<UserId>,
    ): RequestStatus<Group>

    suspend fun addMember(groupId: GroupId, userId: UserId): RequestStatus<Unit>

    suspend fun removeMember(groupId: GroupId, userId: UserId): RequestStatus<Unit>

    suspend fun respondToInvite(groupId: GroupId, userId: UserId, accept: Boolean): RequestStatus<Unit>

    /** PATCH /api/groups/:id (owner only); null fields are left unchanged. */
    suspend fun updateGroup(groupId: GroupId, name: String?, description: String?): RequestStatus<Group>

    suspend fun deleteGroup(groupId: GroupId): RequestStatus<Unit>
}
