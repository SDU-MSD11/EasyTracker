package com.sdumsd11.easytracker.data.local

import com.sdumsd11.easytracker.domain.model.Expense
import com.sdumsd11.easytracker.domain.model.Group
import com.sdumsd11.easytracker.domain.model.InviteStatus
import com.sdumsd11.easytracker.domain.model.NotificationPrefs
import com.sdumsd11.easytracker.domain.model.User

/**
 * ASCII unit separator (code 31): safe list delimiter since
 * names/contacts may contain commas. Built from the code point
 * so no invisible literal sits in source.
 */
private val SEP = 31.toChar()

private fun List<String>.encode(): String = joinToString(SEP.toString())

private fun String.decodeList(): List<String> = if (isEmpty()) emptyList() else split(SEP)

private fun Map<String, InviteStatus>.encodeInvites(): String =
    entries.joinToString(";") { "${it.key}=${it.value.name}" }

private fun String.decodeInvites(): Map<String, InviteStatus> =
    if (isEmpty()) {
        emptyMap()
    } else {
        split(";").mapNotNull { part ->
            val kv = part.split("=", limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            runCatching { kv[0] to InviteStatus.valueOf(kv[1]) }.getOrNull()
        }.toMap()
    }

fun User.toEntity(): UserEntity = UserEntity(
    id = id,
    name = name,
    contact = contact,
    notifyNewExpense = notificationPrefs.newExpense,
    notifyReminders = notificationPrefs.reminders,
    notifySettlements = notificationPrefs.settlementUpdates,
)

fun UserEntity.toDomain(): User = User(
    id = id,
    name = name,
    contact = contact,
    notificationPrefs = NotificationPrefs(notifyNewExpense, notifyReminders, notifySettlements),
)

fun Group.toEntity(): GroupEntity = GroupEntity(
    id = id,
    name = name,
    description = description,
    ownerId = ownerId,
    memberIds = memberIds.encode(),
    invites = invites.encodeInvites(),
)

fun GroupEntity.toDomain(): Group = Group(
    id = id,
    name = name,
    description = description,
    ownerId = ownerId,
    memberIds = memberIds.decodeList(),
    invites = invites.decodeInvites(),
)

fun Expense.toEntity(): ExpenseEntity = ExpenseEntity(
    id = id,
    groupId = groupId,
    payerId = payerId,
    amountCents = amountCents,
    description = description,
    splitAmong = splitAmong.encode(),
    receiptUri = receiptUri,
    createdAt = createdAt,
)

fun ExpenseEntity.toDomain(): Expense = Expense(
    id = id,
    groupId = groupId,
    payerId = payerId,
    amountCents = amountCents,
    description = description,
    // Legacy rows may predate splitAmong; fall back to the payer so domain invariant holds.
    splitAmong = splitAmong.decodeList().ifEmpty { listOf(payerId) },
    receiptUri = receiptUri,
    createdAt = createdAt,
)
