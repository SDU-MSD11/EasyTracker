package com.sdumsd11.easytracker.data.fake

import com.sdumsd11.easytracker.data.GroupRepository
import com.sdumsd11.easytracker.data.NotificationService
import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.domain.model.Group
import com.sdumsd11.easytracker.domain.model.GroupId
import com.sdumsd11.easytracker.domain.model.InviteStatus
import com.sdumsd11.easytracker.domain.model.UserId
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

/**
 * In-memory GroupRepository. Writes apply to local state immediately (offline-first,
 * so previously retrieved data stays visible); sync is simulated via [sync] and
 * retried in the background through [queue] with eventual-success notification.
 */
class FakeGroupRepository(
    scope: CoroutineScope,
    private val notifications: NotificationService,
    private val queue: RetryQueue = RetryQueue(scope, notifications::notifyRetrySuccess),
    private val sync: SyncSimulator = SyncSimulator(),
    initial: List<Group> = emptyList(),
) : GroupRepository {

    private val groups = MutableStateFlow(initial.associateBy(Group::id))

    /** QA hook: pending background syncs; drives offline/retry UI. */
    val pendingSync = queue.pendingCount

    fun setOnline(online: Boolean) = queue.setOnline(online)

    // In-memory: no network; explicit refreshes are no-ops (cache is already the truth).
    override suspend fun refreshGroups(): Boolean = true

    override suspend fun refreshGroup(groupId: GroupId): Boolean = true

    override fun observeGroups(): Flow<RequestStatus<List<Group>>> =
        groups.map { RequestStatus.Success(it.values.sortedBy(Group::name)) as RequestStatus<List<Group>> }
            .onStart { emit(RequestStatus.Pending) }

    override fun observeGroup(groupId: GroupId): Flow<RequestStatus<Group?>> =
        groups.map { RequestStatus.Success(it[groupId]) as RequestStatus<Group?> }
            .onStart { emit(RequestStatus.Pending) }

    override suspend fun createGroup(
        name: String,
        description: String,
        ownerId: UserId,
        memberIds: List<UserId>,
    ): RequestStatus<Group> {
        if (name.isBlank()) return RequestStatus.Failed("Group name must not be blank")
        val group = Group(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            description = description,
            ownerId = ownerId,
            memberIds = (memberIds + ownerId).distinct(),
        )
        groups.update { it + (group.id to group) }
        queue.enqueue("create-group:${group.id}") { sync.sync() }
        return RequestStatus.Success(group)
    }

    override suspend fun addMember(groupId: GroupId, userId: UserId): RequestStatus<Unit> =
        mutate(groupId, "add-member:$groupId:$userId") {
            it.copy(
                memberIds = (it.memberIds + userId).distinct(),
                invites = it.invites + (userId to InviteStatus.ACCEPTED),
            )
        }

    override suspend fun removeMember(groupId: GroupId, userId: UserId): RequestStatus<Unit> =
        mutate(groupId, "remove-member:$groupId:$userId") {
            it.copy(memberIds = it.memberIds - userId)
        }

    override suspend fun respondToInvite(groupId: GroupId, userId: UserId, accept: Boolean): RequestStatus<Unit> =
        mutate(groupId, "invite:$groupId:$userId") {
            val status = if (accept) InviteStatus.ACCEPTED else InviteStatus.DECLINED
            it.copy(
                invites = it.invites + (userId to status),
                memberIds = if (accept) (it.memberIds + userId).distinct() else it.memberIds - userId,
            )
        }

    override suspend fun updateGroup(
        groupId: GroupId,
        name: String?,
        description: String?,
    ): RequestStatus<Group> {
        val current = groups.value[groupId] ?: return RequestStatus.Failed("Group not found")
        val updated = current.copy(
            name = name?.trim()?.ifEmpty { null } ?: current.name,
            description = description ?: current.description,
        )
        groups.update { it + (groupId to updated) }
        queue.enqueue("update-group:$groupId") { sync.sync() }
        return RequestStatus.Success(updated)
    }

    override suspend fun deleteGroup(groupId: GroupId): RequestStatus<Unit> {
        val removed = groups.value.containsKey(groupId)
        groups.update { it - groupId }
        queue.enqueue("delete-group:$groupId") { sync.sync() }
        return if (removed) RequestStatus.Success(Unit) else RequestStatus.Failed("Group not found")
    }

    private fun mutate(groupId: GroupId, label: String, transform: (Group) -> Group): RequestStatus<Unit> {
        val current = groups.value[groupId] ?: return RequestStatus.Failed("Group not found")
        groups.update { it + (groupId to transform(current)) }
        queue.enqueue(label) { sync.sync() }
        return RequestStatus.Success(Unit)
    }
}
