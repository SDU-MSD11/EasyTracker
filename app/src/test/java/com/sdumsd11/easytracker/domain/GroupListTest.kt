package com.sdumsd11.easytracker.domain

import com.sdumsd11.easytracker.domain.model.Expense
import com.sdumsd11.easytracker.domain.model.Group
import com.sdumsd11.easytracker.domain.model.InviteStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Host tests for the Groups tab split + per-group nets (pure JVM, no Android/VM). */
class GroupListTest {

    private val me = "me"
    private val other = "other"
    private val joined = Group(id = "g1", name = "Flatmates", ownerId = me, memberIds = listOf(me, other))
    private val invited = Group(
        id = "g2",
        name = "Trip",
        ownerId = other,
        memberIds = listOf(other),
        invites = mapOf(me to InviteStatus.PENDING),
    )

    private fun expense(groupId: String, payer: String, cents: Long) = Expense(
        id = "$groupId-$payer-$cents",
        groupId = groupId,
        payerId = payer,
        amountCents = cents,
        description = "d",
        splitAmong = listOf(me, other),
    )

    @Test
    fun `nets derive per group for me`() {
        val nets = groupNets(
            listOf(joined, invited),
            listOf(expense("g1", me, 100L), expense("g2", other, 60L)),
            me,
        )
        assertEquals(50L, nets.first { it.group.id == "g1" }.netCents)
        assertEquals(-30L, nets.first { it.group.id == "g2" }.netCents)
    }

    @Test
    fun `no expenses means zero nets`() {
        assertEquals(listOf(0L), groupNets(listOf(joined), emptyList(), me).map(GroupWithNet::netCents))
    }

    @Test
    fun `joined is member, free is invited-only`() {
        val split = splitGroups(groupNets(listOf(joined, invited), emptyList(), me), me)
        assertEquals(listOf("g1"), split.joined.map { it.group.id })
        assertEquals(listOf("g2"), split.free.map { it.group.id })
    }

    @Test
    fun `split is exhaustive and blank id joins nothing`() {
        val split = splitGroups(groupNets(listOf(joined, invited), emptyList(), ""), "")
        assertTrue(split.joined.isEmpty())
        assertEquals(2, split.free.size)
        assertTrue(isJoined(joined, me))
        assertFalse(isJoined(invited, me))
    }
}
