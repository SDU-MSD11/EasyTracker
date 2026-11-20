package com.sdumsd11.easytracker.ui.viewmodels

import androidx.lifecycle.viewModelScope
import com.sdumsd11.easytracker.core.BaseViewModel
import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.data.ExpenseRepository
import com.sdumsd11.easytracker.data.GroupRepository
import com.sdumsd11.easytracker.data.prefs.UserPreferences
import com.sdumsd11.easytracker.di.IoDispatcher
import com.sdumsd11.easytracker.domain.GroupWithNet
import com.sdumsd11.easytracker.domain.groupNets
import com.sdumsd11.easytracker.domain.model.Group
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * First-paint gate: an empty cache stays Loading until the first explicit refresh
 * lands (cold cache must not flash the "No groups yet" empty state before the
 * network answers). Non-empty caches paint instantly; failures surface, never spin.
 */
internal fun groupsFirstLoad(
    status: RequestStatus<List<Group>>,
    refreshedOnce: Boolean,
): Boolean = status is RequestStatus.Pending ||
    (status is RequestStatus.Success && status.data.isEmpty() && !refreshedOnce)

/**
 * Hung-pass bound: a blackholed endpoint (backend never answers, socket accepted)
 * would hang the refresh forever and wedge first paint on Loading. A pass that
 * outlives [timeoutMs] becomes a named failure instead — loading ends when the
 * groups pass settles, one way or another.
 */
internal const val REFRESH_TIMEOUT_MS = 30_000L

internal suspend fun boundedRefresh(
    label: String,
    timeoutMs: Long = REFRESH_TIMEOUT_MS,
    block: suspend () -> RequestStatus<Unit>,
): RequestStatus<Unit> = try {
    withTimeout(timeoutMs) { block() }
} catch (e: TimeoutCancellationException) {
    RequestStatus.Failed("$label refresh timed out.")
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    RequestStatus.Failed(e.message)
}

/** Group list + creation. Owner is the current session profile (auth-gate guarantees login). */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class GroupsViewModel @Inject constructor(
    private val groups: GroupRepository,
    private val expenses: ExpenseRepository,
    private val prefs: UserPreferences,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : BaseViewModel() {

    private val retryTick = MutableStateFlow(0)

    val groupsState: StateFlow<RequestStatus<List<Group>>> = retryTick
        .flatMapLatest { groups.observeGroups() }
        .flowOn(io)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RequestStatus.Pending)

    /**
     * Per-group nets for the session user, derived from cached expenses (cache-only,
     * never triggers network). Unknown user/expenses fall back to 0 — never a stub.
     */
    val summaries: StateFlow<RequestStatus<List<GroupWithNet>>> =
        combine(groupsState, expenses.observeAllExpenses(), prefs.profile) { g, e, me ->
            when (g) {
                is RequestStatus.Success -> RequestStatus.Success(
                    groupNets(g.data, (e as? RequestStatus.Success)?.data.orEmpty(), me?.id.orEmpty()),
                )
                is RequestStatus.Failed -> RequestStatus.Failed(g.message, g.retryable)
                is RequestStatus.Pending -> RequestStatus.Pending
            }
        }.flowOn(io)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RequestStatus.Pending)

    private val _refreshedOnce = MutableStateFlow(false)
    val refreshedOnce: StateFlow<Boolean> = _refreshedOnce.asStateFlow()

    /** One-shot permission prompt state; true once the rationale was answered. Eagerly
     * subscribed so a dismissed prompt stays dismissed on re-entry (no one-frame flash). */
    val notificationPromptSeen: StateFlow<Boolean> = prefs.notificationPromptSeen
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun markNotificationPromptSeen() = viewModelScope.launch(io) {
        prefs.setNotificationPromptSeen()
    }

    /** Last explicit-refresh failure (null when fresh); the nav error slot reads this. */
    private val _refreshError = MutableStateFlow<String?>(null)
    val refreshError: StateFlow<String?> = _refreshError.asStateFlow()

    /** Empty cache counts as loading until the first explicit refresh lands. */
    val isLoading: StateFlow<Boolean> = combine(groupsState, _refreshedOnce, ::groupsFirstLoad)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    init {
        launchRefresh()
    }

    private fun launchRefresh() = viewModelScope.launch(io) {
        // Status contract: repo failures carry the real message (warm caches hide the
        // flow error, so the VM surfaces it). First paint gates on the groups pass
        // alone — an expenses hang/failure only appends context, and a groups
        // failure surfaces immediately instead of wedging on Loading.
        val groupResult = boundedRefresh("Groups") { groups.refreshGroups() }
        val expensesOk = runCatching {
            withTimeout(REFRESH_TIMEOUT_MS) { expenses.refreshAllExpenses() }
        }.getOrDefault(false)
        if (groupResult is RequestStatus.Success) {
            _refreshedOnce.value = true
            _refreshError.value = if (expensesOk) null else "Couldn't refresh expenses."
        } else {
            _refreshError.value = buildList {
                add((groupResult as? RequestStatus.Failed)?.message ?: "Couldn't refresh groups.")
                if (!expensesOk) add("Couldn't refresh expenses.")
            }.joinToString(" ")
        }
    }

    /** Null while idle so the create sheet shows no spinner on first paint. */
    private val _createResult = MutableStateFlow<RequestStatus<Group>?>(null)
    val createResult: StateFlow<RequestStatus<Group>?> = _createResult.asStateFlow()

    fun createGroup(name: String, description: String, memberIds: List<String> = emptyList()) =
        viewModelScope.launch(io) {
            _createResult.value = RequestStatus.Pending
            _createResult.value = runRequest {
                require(name.isNotBlank()) { "Group name must not be blank." }
                val ownerId = prefs.profile.first()?.id
                    ?: throw IllegalStateException("Not logged in.")
                // TODO(api-integration): POST /api/groups {name, description, memberEmails}.
                when (val r = groups.createGroup(name.trim(), description, ownerId, memberIds)) {
                    is RequestStatus.Success -> r.data
                    is RequestStatus.Failed -> throw IllegalStateException(r.message ?: "Create failed.")
                    is RequestStatus.Pending -> throw IllegalStateException("Create pending.")
                }
            }
        }

    fun clearCreateResult() {
        _createResult.value = null
    }

    /** Toolbar refresh: explicit pass (repo single-flighted, bypasses the 5s throttle). */
    private var lastRefreshMs = 0L
    fun refresh() {
        val now = System.currentTimeMillis()
        if (now - lastRefreshMs < 1_000L) return
        lastRefreshMs = now
        _refreshError.value = null
        launchRefresh()
    }

    /** Error-state retry: clears the refresh error, re-subscribes + runs one fresh pass. */
    fun retry() {
        lastRefreshMs = 0L
        retryTick.value++
        refresh()
    }
}
