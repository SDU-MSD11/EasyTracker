package com.sdumsd11.easytracker.data.remote

/** Mirrors backend JSON (see backend/README.md + routes/). Field names match the contract. */
data class UserDto(
    val id: String,
    val name: String,
    val email: String = "",
    val contact: String = "",
)

data class AuthResponse(val token: String, val user: UserDto)

data class RegisterRequest(val name: String, val email: String, val password: String)

data class LoginRequest(val email: String, val password: String)

data class InviteDto(val userId: String, val status: String)

data class GroupDto(
    val id: String,
    val name: String,
    val description: String = "",
    val ownerId: String,
    val memberIds: List<String> = emptyList(),
    val invites: List<InviteDto> = emptyList(),
    val createdAt: String? = null,
)

data class CreateGroupRequest(
    val name: String,
    val description: String = "",
    val memberEmails: List<String> = emptyList(),
)

data class PatchGroupRequest(val name: String? = null, val description: String? = null)

data class AddMemberRequest(val email: String)

data class InviteRespondRequest(val accept: Boolean)

data class ExpenseDto(
    val id: String,
    val groupId: String,
    val payerId: String,
    val amountCents: Long = 0L,
    val description: String = "",
    val splitAmong: List<String> = emptyList(),
    val receiptUri: String? = null,
    val createdAt: String? = null,
)

data class CreateExpenseRequest(
    val payerId: String? = null,
    val amountCents: Long,
    val description: String = "",
    val splitAmong: List<String> = emptyList(),
    val receiptUri: String? = null,
)

data class PatchExpenseRequest(
    val amountCents: Long? = null,
    val description: String? = null,
    val splitAmong: List<String>? = null,
    val receiptUri: String? = null,
)

data class SettlementDto(val fromId: String, val toId: String, val amountCents: Long)

data class SummaryDto(
    val balances: Map<String, Long> = emptyMap(),
    val settlements: List<SettlementDto> = emptyList(),
    val totalCents: Long = 0L,
)

data class PaymentRequest(val toId: String, val amountCents: Long)

data class PaymentResponse(val txId: String)
