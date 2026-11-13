package com.sdumsd11.easytracker.ui.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sdumsd11.easytracker.core.BaseViewModel
import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.data.ExpenseRepository
import com.sdumsd11.easytracker.data.GroupRepository
import com.sdumsd11.easytracker.data.UserRepository
import com.sdumsd11.easytracker.data.prefs.UserPreferences
import com.sdumsd11.easytracker.di.IoDispatcher
import com.sdumsd11.easytracker.domain.DebtCalculator
import com.sdumsd11.easytracker.domain.GroupHistoryEntry
import com.sdumsd11.easytracker.domain.GroupHistoryKind
import com.sdumsd11.easytracker.domain.debtLine
import com.sdumsd11.easytracker.domain.displayNameOrRef
import com.sdumsd11.easytracker.domain.groupHistory
import com.sdumsd11.easytracker.domain.memberShortRef
import com.sdumsd11.easytracker.domain.model.DebtSummary
import com.sdumsd11.easytracker.domain.model.Expense
import com.sdumsd11.easytracker.domain.model.Group
import com.sdumsd11.easytracker.domain.model.User
import com.sdumsd11.easytracker.domain.model.UserId
import com.sdumsd11.easytracker.domain.settlementPaidProgress
import com.sdumsd11.easytracker.domain.shortIdRef
import com.sdumsd11.easytracker.navigation.Routes
import com.sdumsd11.easytracker.ui.screens.GroupHistoryUi
import com.sdumsd11.easytracker.ui.screens.HistoryKind
import com.sdumsd11.easytracker.ui.screens.MemberUi
import com.sdumsd11.easytracker.ui.screens.PaidProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * First-paint gate: a missing group / empty debt summary stays Loading until the
 * first explicit refresh lands (cold cache must not flash "all settled up" before
 * the network answers). Loaded data paints instantly; failures surface, never spin.
 */
internal fun detailFirstLoad(
    group: RequestStatus<Group?>,
    summary: RequestStatus<DebtSummary>,
    refreshedOnce: Boolean,
): Boolean = group is RequestStatus.Pending || summary is RequestStatus.Pending ||
    (!refreshedOnce && (
        (group is RequestStatus.Success && group.data == null) ||
            (summary is RequestStatus.Success && summary.data.settlements.isEmpty())
    ))

/**
 * Group detail: group + debt summary derived from expenses via [DebtCalculator].
 * [isOwner] is null until group+profile load — the EditGroup owner-gate redirects
 * only on `false`, never while unknown. groupId comes from the nav arg via SavedStateHandle.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class GroupDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groups: GroupRepository,
    private val expenses: ExpenseRepository,
    users: UserRepository,
    prefs: UserPreferences,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : BaseViewModel() {

    private val groupId: String = savedStateHandle.get<String>(Routes.ArgGroupId).orEmpty()

    private val retryTick = MutableStateFlow(0)
    private val _refreshedOnce = MutableStateFlow(false)
    val refreshedOnce: StateFlow<Boolean> = _refreshedOnce.asStateFlow()

    /** Last explicit-refresh failure (null when fresh); the nav error slot reads this. */
    private val _refreshError = MutableStateFlow<String?>(null)
    val refreshError: StateFlow<String?> = _refreshError.asStateFlow()

    init {
        launchRefresh()
    }

    /** Both refreshes run even if one fails; refreshedOnce lands only when both succeed. */
    private fun launchRefresh() = viewModelScope.launch(io) {
        // Boolean contract: false = network failed (cached data still shows).
        val ok = supervisorScope {
            listOf(
                async { runCatching { groups.refreshGroup(groupId) }.getOrDefault(false) },
                async { runCatching { expenses.refreshExpenses(groupId) }.getOrDefault(false) },
            ).awaitAll().all { it }
        }
        if (ok) {
            _refreshedOnce.value = true
            _refreshError.value = null
        } else {
            _refreshError.value = "Couldn't refresh group data."
        }
    }

    val group: StateFlow<RequestStatus<Group?>> = retryTick
        .flatMapLatest { groups.observeGroup(groupId) }
        .flowOn(io)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RequestStatus.Pending)

    // TODO(api-integration): prefer GET /api/groups/:id/summary when online; keep this pure
    // derivation as the offline path so detail renders from cache.
    private val expensesStatus: Flow<RequestStatus<List<Expense>>> = retryTick
        .flatMapLatest { expenses.observeExpenses(groupId) }

    val summary: StateFlow<RequestStatus<DebtSummary>> = expensesStatus
        .map { status ->
            when (status) {
                is RequestStatus.Success -> RequestStatus.Success(DebtCalculator.summarize(status.data))
                is RequestStatus.Pending -> RequestStatus.Pending
                is RequestStatus.Failed -> RequestStatus.Failed(status.message, status.retryable)
            }
        }
        .flowOn(io)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RequestStatus.Pending)

    /** Missing group / empty summary counts as loading until the first refresh lands. */
    val isLoading: StateFlow<Boolean> =
        combine(group, summary, _refreshedOnce, ::detailFirstLoad)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val isOwner: StateFlow<Boolean?> =
        combine(group, prefs.profile) { g, me ->
            val ownerId = (g as? RequestStatus.Success)?.data?.ownerId ?: return@combine null
            me?.let { it.id == ownerId }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Members with resolved display names. Backend GET /groups/:id returns member
     * UUIDs only (no names/emails, no user-list route), so names resolve locally:
     * cached profile for self, Room-cached users otherwise, short-id fallback for
     * unknown members (never a raw full UUID as the primary line; the full id
     * stays in [MemberUi.id] for debug/contentDescription use).
     */
    val members: StateFlow<List<MemberUi>> =
        combine(group, prefs.profile, users.observeUsers()) { g, me, cached ->
            val grp = (g as? RequestStatus.Success)?.data ?: return@combine emptyList()
            val byId = (cached as? RequestStatus.Success)?.data?.associateBy(User::id).orEmpty()
            grp.memberIds.map { id -> MemberUi(id, displayName(id, me, byId[id]), grp.invites[id]) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Per-group history (debts created + payments made, newest first) with payer/owner
     * display names resolved from [members]. Cache-first; failures pass through and the
     * route merges them with [refreshError]. Refreshed by the shared [launchRefresh].
     */
    val history: StateFlow<RequestStatus<List<GroupHistoryUi>>> =
        combine(expensesStatus, members) { status, memberList ->
            when (status) {
                is RequestStatus.Success -> {
                    val names = memberList.associate { it.id to it.name }
                    RequestStatus.Success(groupHistory(status.data).map { it.toUi(names) })
                }
                is RequestStatus.Pending -> RequestStatus.Pending
                is RequestStatus.Failed -> RequestStatus.Failed(status.message, status.retryable)
            }
        }
            .flowOn(io)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RequestStatus.Pending)

    /**
     * Paid-vs-total per outstanding settlement line, keyed by the exact debt line string
     * the route renders (see [debtLine]) so the screen lookup hits. Empty until the cache
     * emits; no separate refresh — the shared [launchRefresh] covers expenses.
     */
    val debtProgress: StateFlow<Map<String, PaidProgress>> =
        combine(expensesStatus, members) { status, memberList ->
            val list = (status as? RequestStatus.Success)?.data ?: return@combine emptyMap()
            val names = memberList.associate { it.id to it.name }
            settlementPaidProgress(list).associate { p ->
                debtLine(
                    displayNameOrRef(names, p.settlement.fromId),
                    displayNameOrRef(names, p.settlement.toId),
                    p.settlement.amountCents,
                ) to PaidProgress(paidCents = p.paidCents, totalCents = p.totalCents)
            }
        }
            .flowOn(io)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Error-state retry: clears the refresh error, re-subscribes + runs one fresh pass. */
    fun retry() {
        retryTick.value++
        _refreshError.value = null
        launchRefresh()
    }

    /**
     * Owner removes a member (DELETE /api/groups/:id/members/:userId via repo).
     * Null while idle — no spinner on first paint. Success needs no manual refresh:
     * the repo updates the cached group, so [group]/[members] re-emit on their own.
     */
    private val _removeResult = MutableStateFlow<RequestStatus<Unit>?>(null)
    val removeResult: StateFlow<RequestStatus<Unit>?> = _removeResult.asStateFlow()

    fun removeMember(userId: String) =
        viewModelScope.launch(io) {
            _removeResult.value = RequestStatus.Pending
            _removeResult.value = runRequest {
                require(userId.isNotBlank()) { "Select a member to remove." }
                when (val r = groups.removeMember(groupId, userId)) {
                    is RequestStatus.Success -> Unit
                    is RequestStatus.Failed -> throw IllegalStateException(r.message ?: "Remove failed.")
                    is RequestStatus.Pending -> throw IllegalStateException("Remove pending.")
                }
            }
        }

    fun clearRemoveResult() {
        _removeResult.value = null
    }

    /**
     * Owner invites a member by email (POST /api/groups/:id/members via repo).
     * Null while idle. Success needs no manual refresh: the repo updates the
     * cached group, so [group]/[members] re-emit on their own.
     */
    private val _inviteResult = MutableStateFlow<RequestStatus<Unit>?>(null)
    val inviteResult: StateFlow<RequestStatus<Unit>?> = _inviteResult.asStateFlow()

    fun inviteMember(email: String) =
        viewModelScope.launch(io) {
            _inviteResult.value = RequestStatus.Pending
            _inviteResult.value = runRequest {
                val trimmed = email.trim()
                require(trimmed.isNotBlank() && trimmed.contains("@")) { "Enter a valid email address." }
                when (val r = groups.addMember(groupId, trimmed)) {
                    is RequestStatus.Success -> Unit
                    is RequestStatus.Failed -> throw IllegalStateException(r.message ?: "Invite failed.")
                    is RequestStatus.Pending -> throw IllegalStateException("Invite pending.")
                }
            }
        }

    fun clearInviteResult() {
        _inviteResult.value = null
    }

    /**
     * Owner renames the group (PATCH /api/groups/:id via repo). Null while idle.
     * Success needs no manual refresh: the repo updates the cached group, so
     * [group]/[members] re-emit on their own.
     */
    private val _saveResult = MutableStateFlow<RequestStatus<Group>?>(null)
    val saveResult: StateFlow<RequestStatus<Group>?> = _saveResult.asStateFlow()

    fun updateGroup(name: String) =
        viewModelScope.launch(io) {
            _saveResult.value = RequestStatus.Pending
            _saveResult.value = runRequest {
                require(name.isNotBlank()) { "Group name must not be blank." }
                when (val r = groups.updateGroup(groupId, name.trim(), null)) {
                    is RequestStatus.Success -> r.data
                    is RequestStatus.Failed -> throw IllegalStateException(r.message ?: "Save failed.")
                    is RequestStatus.Pending -> throw IllegalStateException("Save pending.")
                }
            }
        }

    fun clearSaveResult() {
        _saveResult.value = null
    }
}

/** Self → cached name ("You" when blank) → contact email → email-like id → short ref. */
private fun displayName(id: UserId, me: User?, cached: User?): String {
    if (id == me?.id) return me.name.ifBlank { "You" }
    if (!cached?.name.isNullOrBlank()) return cached.name
    // Repo-upserted User(name="", contact=email) rows render the full email here;
    // pending email-keyed invites surface as email-like ids and hit the same branch.
    // Never a raw UUID: worst case is the shared memberShortRef (same as displayNameOrRef).
    val email = cached?.contact?.takeIf { it.contains("@") } ?: id.takeIf { it.contains("@") }
    if (email != null) return email
    return memberShortRef(id)
}

/** Domain entry → screen row; txRef pre-shortens the payment id (== txId, no backend tx field). */
private fun GroupHistoryEntry.toUi(nameById: Map<UserId, String>): GroupHistoryUi = GroupHistoryUi(
    id = id,
    kind = if (kind == GroupHistoryKind.PAYMENT) HistoryKind.PAYMENT else HistoryKind.DEBT,
    title = title,
    payerName = displayNameOrRef(nameById, payerId),
    ownerName = displayNameOrRef(nameById, counterpartyId),
    amountCents = amountCents,
    txRef = if (kind == GroupHistoryKind.PAYMENT) shortIdRef(id) else null,
)
