package com.sdumsd11.easytracker.domain

import com.sdumsd11.easytracker.domain.model.Expense
import com.sdumsd11.easytracker.domain.model.Group
import com.sdumsd11.easytracker.domain.model.UserId

/** One group with the current user's net (positive = owed to them), in minor units. */
data class GroupWithNet(val group: Group, val netCents: Long)

/** Joined (member) vs Free (visible but not a member, e.g. invite-only) slice. */
data class GroupSplit(val joined: List<GroupWithNet>, val free: List<GroupWithNet>)

/** Per-group nets for [myId] derived from cached expenses via [DebtCalculator]. Pure. */
fun groupNets(groups: List<Group>, expenses: List<Expense>, myId: UserId): List<GroupWithNet> {
    val byGroup = expenses.groupBy(Expense::groupId)
    return groups.map { g ->
        GroupWithNet(g, DebtCalculator.calculateBalances(byGroup[g.id].orEmpty())[myId] ?: 0L)
    }
}

/** Joined = accepted member. Blank [myId] (no session) counts as not-joined. */
fun isJoined(group: Group, myId: UserId): Boolean = myId.isNotBlank() && myId in group.memberIds

/** Exhaustive tab split: every listed group lands in exactly one tab. Pure. */
fun splitGroups(summaries: List<GroupWithNet>, myId: UserId): GroupSplit {
    val (joined, free) = summaries.partition { isJoined(it.group, myId) }
    return GroupSplit(joined, free)
}
