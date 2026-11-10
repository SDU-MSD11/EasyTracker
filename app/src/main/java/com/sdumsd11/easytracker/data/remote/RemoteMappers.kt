package com.sdumsd11.easytracker.data.remote

import com.sdumsd11.easytracker.domain.model.Balance
import com.sdumsd11.easytracker.domain.model.DebtSummary
import com.sdumsd11.easytracker.domain.model.Expense
import com.sdumsd11.easytracker.domain.model.Group
import com.sdumsd11.easytracker.domain.model.InviteStatus
import com.sdumsd11.easytracker.domain.model.Settlement
import com.sdumsd11.easytracker.domain.model.User
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val ISO_FORMATS = listOf(
    "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
    "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
    "yyyy-MM-dd'T'HH:mm:ssXXX",
    "yyyy-MM-dd'T'HH:mm:ss.SSS",
    "yyyy-MM-dd'T'HH:mm:ss",
).map { pattern ->
    SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
}

/** minSdk 24-safe ISO-8601 parse (no java.time desugaring needed); 0L when unparseable. */
fun String?.toEpochMillis(): Long {
    if (this == null) return 0L
    val normalized = trim().let { if (it.endsWith("Z")) it.dropLast(1) + "+00:00" else it }
    for (format in ISO_FORMATS) {
        @Suppress("SimpleDateFormat")
        runCatching { return (format.clone() as SimpleDateFormat).parse(normalized)?.time ?: 0L }
    }
    return 0L
}

/** Domain contact carries the email when backend contact is blank (domain User has no email). */
fun UserDto.toDomain(): User = User(id = id, name = name, contact = contact.ifEmpty { email })

fun GroupDto.toDomain(): Group = Group(
    id = id,
    name = name,
    description = description,
    ownerId = ownerId,
    memberIds = memberIds,
    invites = invites.mapNotNull { invite ->
        runCatching { invite.userId to InviteStatus.valueOf(invite.status) }.getOrNull()
    }.toMap(),
)

fun ExpenseDto.toDomain(): Expense = Expense(
    id = id,
    groupId = groupId,
    payerId = payerId,
    amountCents = amountCents,
    description = description,
    splitAmong = splitAmong.ifEmpty { listOf(payerId) },
    receiptUri = receiptUri,
    createdAt = createdAt.toEpochMillis(),
)

fun SettlementDto.toDomain(): Settlement = Settlement(fromId = fromId, toId = toId, amountCents = amountCents)

fun SummaryDto.toDomain(): DebtSummary = DebtSummary(
    balances = balances.map { (id, net) -> Balance(id, net) }.sortedByDescending { it.netCents },
    settlements = settlements.map { it.toDomain() },
    totalCents = totalCents,
)
