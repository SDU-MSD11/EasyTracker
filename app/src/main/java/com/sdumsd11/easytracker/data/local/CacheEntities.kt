package com.sdumsd11.easytracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Offline cache entities. List/Map fields are stored pre-encoded as [String]
 * (see [Mappers.kt]) so no `@TypeConverter` or extra serialization dep is needed.
 *
 * Requires (arch owner): `androidx.room:room-runtime`, `room-ktx`, ksp `room-compiler`.
 * minSdk 24 / targetSdk 37 safe.
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val contact: String,
    val notifyNewExpense: Boolean,
    val notifyReminders: Boolean,
    val notifySettlements: Boolean,
)

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val ownerId: String,
    /** Member ids joined by U+001F (see Mappers). */
    val memberIds: String,
    /** Invites encoded as `uid=STATUS;…` (see Mappers). */
    val invites: String,
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val payerId: String,
    val amountCents: Long,
    val description: String,
    /** Participants joined by U+001F (see Mappers). */
    val splitAmong: String,
    /** Optional receipt attachment reference only. */
    val receiptUri: String?,
    val createdAt: Long,
    /** Stable repayment marker from the wire `kind` field (see Mappers). */
    val isSettlement: Boolean = false,
    /**
     * Client-mirror marker: true only for offline-first rows minted locally on a
     * retryable failure (never for server rows). Lets refresh reconciliation prune
     * dropped-op mirrors without touching confirmed ledger rows.
     */
    val isLocalMirror: Boolean = false,
)
