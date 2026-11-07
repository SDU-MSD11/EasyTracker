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

    suspend fun createGroup(
        name: String,
        description: String,
        ownerId: UserId,
        memberIds: List<UserId>,
    ): RequestStatus<Group>

    suspend fun addMember(groupId: GroupId, userId: UserId): RequestStatus<Unit>

    suspend fun removeMember(groupId: GroupId, userId: UserId): RequestStatus<Unit>

    suspend fun respondToInvite(groupId: GroupId, userId: UserId, accept: Boolean): RequestStatus<Unit>

    suspend fun deleteGroup(groupId: GroupId): RequestStatus<Unit>
}
