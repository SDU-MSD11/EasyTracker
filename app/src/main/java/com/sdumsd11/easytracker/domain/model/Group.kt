package com.sdumsd11.easytracker.domain.model

/** Stable group identifier. */
typealias GroupId = String

/** Per-member invite lifecycle. */
enum class InviteStatus { PENDING, ACCEPTED, DECLINED }

/**
 * Pure domain group.
 *
 * @param memberIds accepted members (includes [ownerId] once created).
 * @param invites per-user invite state; absent key = never invited.
 */
data class Group(
    val id: GroupId,
    val name: String,
    val description: String = "",
    val ownerId: UserId,
    val memberIds: List<UserId> = emptyList(),
    val invites: Map<UserId, InviteStatus> = emptyMap(),
)
