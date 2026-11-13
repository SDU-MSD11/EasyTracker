package com.sdumsd11.easytracker.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.ui.StatusEvent
import com.sdumsd11.easytracker.ui.components.BackgroundSuccessHost
import com.sdumsd11.easytracker.ui.statusBus
import com.sdumsd11.easytracker.domain.accountStats
import com.sdumsd11.easytracker.domain.debtLine
import com.sdumsd11.easytracker.domain.displayNameOrRef
import com.sdumsd11.easytracker.domain.model.NotificationPrefs
import com.sdumsd11.easytracker.domain.model.User
import com.sdumsd11.easytracker.domain.splitGroups
import com.sdumsd11.easytracker.ui.screens.AccountScreen
import com.sdumsd11.easytracker.ui.screens.CreateGroupScreen
import com.sdumsd11.easytracker.ui.screens.EditGroupScreen
import com.sdumsd11.easytracker.ui.screens.EditProfileScreen
import com.sdumsd11.easytracker.ui.screens.GroupDetailScreen
import com.sdumsd11.easytracker.ui.screens.GroupSummary
import com.sdumsd11.easytracker.ui.screens.GroupsOverviewScreen
import com.sdumsd11.easytracker.ui.screens.LoginScreen
import com.sdumsd11.easytracker.ui.screens.MakePaymentScreen
import com.sdumsd11.easytracker.ui.screens.PaymentFailedScreen
import com.sdumsd11.easytracker.ui.screens.PaymentHistoryScreen
import com.sdumsd11.easytracker.ui.screens.PaymentSuccessScreen
import com.sdumsd11.easytracker.ui.screens.ReceiptScreen
import com.sdumsd11.easytracker.ui.screens.RegisterScreen
import com.sdumsd11.easytracker.ui.screens.SettingsScreen
import com.sdumsd11.easytracker.ui.screens.SetupDebtScreen
import com.sdumsd11.easytracker.ui.screens.SplashScreen
import com.sdumsd11.easytracker.ui.screens.TxStatus
import com.sdumsd11.easytracker.ui.screens.TxUi
import com.sdumsd11.easytracker.ui.viewmodels.AuthViewModel
import com.sdumsd11.easytracker.ui.viewmodels.GroupDetailViewModel
import com.sdumsd11.easytracker.ui.viewmodels.GroupsViewModel
import com.sdumsd11.easytracker.ui.viewmodels.MakePaymentViewModel
import com.sdumsd11.easytracker.ui.viewmodels.PaymentHistoryViewModel
import com.sdumsd11.easytracker.ui.viewmodels.ProfileViewModel
import com.sdumsd11.easytracker.ui.viewmodels.SetupDebtViewModel
import kotlinx.coroutines.launch

@Composable
fun rememberEasyNavController(): NavHostController = rememberNavController()

/**
 * Real graph over [Routes] (LAW — patterns/arg keys unchanged). Bottom tabs on the
 * Groups/Account/Settings roots only; flows carry Back buttons. Screens stay lambda-driven,
 * each lambda is a single `navController` call.
 *
 * Auth: Splash routes by session (Groups if logged in else Login); Groups/Account redirect
 * to Login on unauthenticated VM state; auth success pops Login inclusively.
 */
@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    // Global status surface: success confirmations and background-retry wins post a
    // Snackbar here (audible/haptic only for BackgroundSuccess). VMs feed it via
    // @Inject StatusBus; screens stay stateless.
    val context = LocalContext.current
    val statusBus = remember(context) { context.applicationContext.statusBus() }
    val snackbarHostState = remember { SnackbarHostState() }
    val notifyScope = rememberCoroutineScope()
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (route == Routes.Groups || route == Routes.Account || route == Routes.Settings) {
                BottomTabs(selected = route) {
                    navController.navigate(it) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        },
    ) { padding ->
        BackgroundSuccessHost(statusBus, snackbarHostState)
        NavHost(navController, startDestination = Routes.Splash, Modifier.padding(padding)) {
            composable(Routes.Splash) {
                val authVm: AuthViewModel = hiltViewModel()
                val session by authVm.session.collectAsState()
                SplashScreen(onDone = {
                    val dest = if ((session as? RequestStatus.Success)?.data != null) {
                        Routes.groups()
                    } else {
                        Routes.Login
                    }
                    navController.navigate(dest) {
                        popUpTo(Routes.Splash) { inclusive = true }
                    }
                })
            }
            composable(Routes.Login) {
                val authVm: AuthViewModel = hiltViewModel()
                val session by authVm.session.collectAsState()
                val result by authVm.authResult.collectAsState()
                // Self-heal: already authed (e.g. slow token read at Splash) -> straight to Groups.
                ForwardWhenAuthenticated(session, navController)
                LoginScreen(
                    result = result,
                    onSubmit = { email, password -> authVm.login(email, password) },
                    onRegisterClick = { navController.navigate(Routes.Register) },
                    onAuthenticated = {
                        authVm.clearResult()
                        navController.navigate(Routes.groups()) {
                            popUpTo(Routes.Login) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.Register) {
                val authVm: AuthViewModel = hiltViewModel()
                val session by authVm.session.collectAsState()
                val result by authVm.authResult.collectAsState()
                ForwardWhenAuthenticated(session, navController)
                RegisterScreen(
                    result = result,
                    onSubmit = { name, email, password -> authVm.register(name, email, password) },
                    onLoginClick = { navController.popBackStack() },
                    onAuthenticated = {
                        authVm.clearResult()
                        navController.navigate(Routes.groups()) {
                            popUpTo(Routes.Login) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                Routes.Groups,
                listOf(navArgument(Routes.ArgTab) { defaultValue = GroupsTab.JOINED }),
            ) {
                val authVm: AuthViewModel = hiltViewModel()
                val session by authVm.session.collectAsState()
                RequireAuth(session) {
                    navController.navigate(Routes.Login) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                    }
                }
                val groupsVm: GroupsViewModel = hiltViewModel()
                val summariesState by groupsVm.summaries.collectAsState()
                val groupsLoading by groupsVm.isLoading.collectAsState()
                val groupsRefreshError by groupsVm.refreshError.collectAsState()
                val groupsLoaded by groupsVm.refreshedOnce.collectAsState()
                // Tab split is domain-owned (member = Joined, visible-but-not-member = Free);
                // the route passes the ArgTab slice (screen tabs are ui-ux owned).
                val myId = (session as? RequestStatus.Success)?.data?.id.orEmpty()
                val tab = it.arguments?.getString(Routes.ArgTab) ?: GroupsTab.JOINED
                val split = splitGroups(
                    (summariesState as? RequestStatus.Success)?.data.orEmpty(),
                    myId,
                )
                GroupsOverviewScreen(
                    onGroupClick = { navController.navigate(Routes.groupDetail(it)) },
                    onCreateGroup = { navController.navigate(Routes.CreateGroup) },
                    groups = (if (tab == GroupsTab.FREE) split.free else split.joined)
                        .map { GroupSummary(it.group.id, it.group.name, it.netCents) },
                    isLoading = groupsLoading,
                    errorMessage = (summariesState as? RequestStatus.Failed)?.message
                        ?: groupsRefreshError,
                    onRetry = groupsVm::retry,
                    onRefresh = groupsVm::refresh,
                    hasLoaded = groupsLoaded,
                )
            }
            composable(Routes.CreateGroup) {
                val groupsVm: GroupsViewModel = hiltViewModel()
                val createResult by groupsVm.createResult.collectAsState()
                CreateGroupScreen(
                    createResult = createResult,
                    onCreate = groupsVm::createGroup,
                    onCreated = { id ->
                        groupsVm.clearCreateResult()
                        navController.navigate(Routes.groupDetail(id)) {
                            popUpTo(Routes.CreateGroup) { inclusive = true }
                        }
                    },
                    onCancel = { navController.popBackStack() },
                )
            }
            composable(
                Routes.GroupDetail,
                listOf(navArgument(Routes.ArgGroupId) { type = NavType.StringType }),
            ) {
                val gid = it.arguments?.getString(Routes.ArgGroupId).orEmpty()
                val detailVm: GroupDetailViewModel = hiltViewModel()
                val isOwner by detailVm.isOwner.collectAsState()
                val groupState by detailVm.group.collectAsState()
                val summaryState by detailVm.summary.collectAsState()
                val group = (groupState as? RequestStatus.Success)?.data
                val summary = (summaryState as? RequestStatus.Success)?.data
                val members by detailVm.members.collectAsState()
                val removeResult by detailVm.removeResult.collectAsState()
                val detailLoading by detailVm.isLoading.collectAsState()
                val detailRefreshError by detailVm.refreshError.collectAsState()
                val groupHistory by detailVm.history.collectAsState()
                val groupProgress by detailVm.debtProgress.collectAsState()
                val historyLoaded by detailVm.refreshedOnce.collectAsState()
                // Debts render display names (members flow short-id fallback); unknown ids
                // fall back to short-id here too — never a full UUID as the primary line.
                val nameById = members.associate { it.id to it.name }
                GroupDetailScreen(
                    groupId = gid,
                    isOwner = isOwner == true,
                    onMakePayment = { navController.navigate(Routes.makePayment(it)) },
                    onEditGroup = { navController.navigate(Routes.editGroup(it)) },
                    onSetupDebt = { navController.navigate(Routes.setupDebt(it)) },
                    onBack = { navController.popBackStack() },
                    groupName = group?.name,
                    // Names resolved in the VM (cached profile + Room users; short-id fallback).
                    members = members,
                    debts = summary?.settlements?.map { s ->
                        debtLine(
                            displayNameOrRef(nameById, s.fromId),
                            displayNameOrRef(nameById, s.toId),
                            s.amountCents,
                        )
                    } ?: emptyList(),
                    isLoading = detailLoading,
                    errorMessage = (groupState as? RequestStatus.Failed)?.message
                        ?: (summaryState as? RequestStatus.Failed)?.message
                        ?: detailRefreshError,
                    onRetry = detailVm::retry,
                    // Removal stays on this route: success re-emits via members flow,
                    // failure surfaces inline in the Members card with the repo message.
                    removeResult = removeResult,
                    onRemoveMember = detailVm::removeMember,
                    onDismissRemoveResult = detailVm::clearRemoveResult,
                    history = (groupHistory as? RequestStatus.Success)?.data ?: emptyList(),
                    debtProgress = groupProgress,
                    historyIsLoading = groupHistory is RequestStatus.Pending,
                    historyError = (groupHistory as? RequestStatus.Failed)?.message
                        ?: detailRefreshError,
                    historyHasLoaded = historyLoaded,
                )
            }
            composable(
                Routes.EditGroup,
                listOf(navArgument(Routes.ArgGroupId) { type = NavType.StringType }),
            ) {
                val gid = it.arguments?.getString(Routes.ArgGroupId).orEmpty()
                val detailVm: GroupDetailViewModel = hiltViewModel()
                val isOwner by detailVm.isOwner.collectAsState()
                // Owner-gate: VM checks admin and redirects non-admins to GroupDetail;
                // the graph does not gate (deep links must still land safely).
                // Redirect only on `false` — null means group/profile still loading.
                if (isOwner == false) {
                    LaunchedEffect(gid) {
                        navController.navigate(Routes.groupDetail(gid)) {
                            popUpTo(Routes.EditGroup) { inclusive = true }
                        }
                    }
                } else {
                    val members by detailVm.members.collectAsState()
                    val groupState by detailVm.group.collectAsState()
                    val group = (groupState as? RequestStatus.Success)?.data
                    val saveResult by detailVm.saveResult.collectAsState()
                    val inviteResult by detailVm.inviteResult.collectAsState()
                    EditGroupScreen(
                        groupId = gid,
                        isOwner = isOwner == true,
                        onSetupDebt = { navController.navigate(Routes.setupDebt(it)) },
                        onBack = { navController.popBackStack() },
                        members = members,
                        groupName = group?.name,
                        saveResult = saveResult,
                        onSave = detailVm::updateGroup,
                        onSaved = {
                            detailVm.clearSaveResult()
                            navController.popBackStack()
                        },
                        inviteResult = inviteResult,
                        onInvite = detailVm::inviteMember,
                        onDismissInviteResult = detailVm::clearInviteResult,
                    )
                }
            }
            composable(
                Routes.SetupDebt,
                listOf(
                    navArgument(Routes.ArgGroupId) { type = NavType.StringType },
                    navArgument(Routes.ArgDebtId) {
                        type = NavType.StringType
                        nullable = true
                    },
                ),
            ) {
                val authVm: AuthViewModel = hiltViewModel()
                val session by authVm.session.collectAsState()
                val debtVm: SetupDebtViewModel = hiltViewModel()
                val saveResult by debtVm.saveResult.collectAsState()
                val payerId = (session as? RequestStatus.Success)?.data?.id.orEmpty()
                val gid = it.arguments?.getString(Routes.ArgGroupId).orEmpty()
                // Same ArgGroupId key, so this VM resolves members + name for the debt route.
                val detailVm: GroupDetailViewModel = hiltViewModel()
                val participants by detailVm.members.collectAsState()
                val groupState by detailVm.group.collectAsState()
                val debtId = it.arguments?.getString(Routes.ArgDebtId)
                // Edit preload via the existing initialPayerId contract (amount/note fields
                // need screen support — ui-ux owned); blank debtId means create.
                val editing by debtVm.editingExpense(gid, debtId.orEmpty())
                    .collectAsState(initial = RequestStatus.Pending)
                val editingExpense = (editing as? RequestStatus.Success)?.data
                SetupDebtScreen(
                    groupId = gid,
                    debtId = debtId,
                    onDone = {
                        debtVm.clearSaveResult()
                        notifyScope.launch { statusBus.notify(StatusEvent("debt-save", "Debt saved")) }
                        navController.popBackStack()
                    },
                    saveResult = saveResult,
                    onSave = { payer, cents, note -> debtVm.save(gid, payer, cents, note, debtId) },
                    groupName = (groupState as? RequestStatus.Success)?.data?.name,
                    participants = participants,
                    initialPayerId = editingExpense?.payerId ?: payerId.ifBlank { null },
                    onDismissError = debtVm::clearSaveResult,
                )
            }
            composable(
                Routes.MakePayment,
                listOf(navArgument(Routes.ArgGroupId) { type = NavType.StringType }),
            ) {
                val gid = it.arguments?.getString(Routes.ArgGroupId).orEmpty()
                val payVm: MakePaymentViewModel = hiltViewModel()
                val payResult by payVm.payResult.collectAsState()
                // Same ArgGroupId key, so this VM resolves the group for the pay route.
                val detailVm: GroupDetailViewModel = hiltViewModel()
                val participants by detailVm.members.collectAsState()
                val groupState by detailVm.group.collectAsState()
                // Payer is fixed to the session user (screen shows it display-only):
                // resolve the display name from the loaded members, never a raw id.
                val authVm: AuthViewModel = hiltViewModel()
                val session by authVm.session.collectAsState()
                val sessionId = (session as? RequestStatus.Success)?.data?.id
                val payer = sessionId?.let { id -> participants.firstOrNull { it.id == id } }
                MakePaymentScreen(
                    groupId = gid,
                    participants = participants,
                    groupName = (groupState as? RequestStatus.Success)?.data?.name,
                    payer = payer,
                    onResult = { result, ok ->
                        // Clear first: PaymentFailed keeps this entry beneath for retry, and a
                        // stale Success/Failed would refire the screen's result effect on return.
                        payVm.clearPayResult()
                        if (ok) {
                            navController.navigate(
                                Routes.paymentSuccess(result.txId, result.amountCents, result.groupId),
                            ) {
                                // Point-of-no-return: back lands on GroupDetail, never resubmit.
                                popUpTo(Routes.MakePayment) { inclusive = true }
                            }
                        } else {
                            navController.navigate(Routes.paymentFailed()) { launchSingleTop = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
                    payResult = payResult,
                    onPay = { toId, cents -> payVm.pay(gid, toId, cents) },
                )
            }
            composable(
                Routes.PaymentSuccess,
                listOf(
                    navArgument(Routes.ArgTxId) { type = NavType.StringType },
                    navArgument(Routes.ArgAmount) { type = NavType.LongType; defaultValue = 0L },
                    navArgument(Routes.ArgGroupId) { type = NavType.StringType; nullable = true },
                ),
            ) {
                val tx = it.arguments?.getString(Routes.ArgTxId).orEmpty()
                val amount = it.arguments?.getLong(Routes.ArgAmount) ?: 0L
                val gid = it.arguments?.getString(Routes.ArgGroupId).orEmpty()
                // Real payment props ride the route (VM entry is gone after the
                // point-of-no-return pop); the name resolves from the cached groups.
                val groupsVm: GroupsViewModel = hiltViewModel()
                val groupsState by groupsVm.groupsState.collectAsState()
                val groupName = (groupsState as? RequestStatus.Success)?.data
                    ?.firstOrNull { g -> g.id == gid }?.name
                PaymentSuccessScreen(
                    groupId = gid,
                    groupName = groupName,
                    amountCents = amount,
                    txId = tx,
                    onBackToGroup = { navController.popBackStack() },
                    onExportReceipt = { navController.navigate(Routes.receipt(tx, amount, gid)) },
                )
            }
            composable(
                Routes.PaymentFailed,
                listOf(navArgument(Routes.ArgError) { nullable = true }),
            ) {
                // MakePayment stays beneath for retry; groupId recovered from its entry.
                val gid = navController.previousBackStackEntry
                    ?.arguments?.getString(Routes.ArgGroupId).orEmpty()
                PaymentFailedScreen(
                    groupId = gid,
                    onRetry = { navController.popBackStack() },
                    onBackToGroup = { navController.popBackStack(Routes.GroupDetail, inclusive = false) },
                )
            }
            composable(Routes.Account) {
                val authVm: AuthViewModel = hiltViewModel()
                val session by authVm.session.collectAsState()
                RequireAuth(session) {
                    navController.navigate(Routes.Login) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                    }
                }
                val scope = rememberCoroutineScope()
                val groupsVm: GroupsViewModel = hiltViewModel()
                val groupsState by groupsVm.groupsState.collectAsState()
                val historyVm: PaymentHistoryViewModel = hiltViewModel()
                val history by historyVm.history.collectAsState()
                // Freshness: the cache flow never triggers network — an explicit refresh
                // on entry keeps first paint instant while the background pass lands.
                // Debounced in the VM + single-flighted in the repo.
                LaunchedEffect(Unit) { historyVm.refresh() }
                val me = (session as? RequestStatus.Success)?.data
                AccountScreen(
                    onHistory = { navController.navigate(Routes.PaymentHistory) },
                    onSettings = { navController.navigate(Routes.Settings) },
                    onSignOut = { scope.launch { signOut(authVm, navController) } },
                    displayName = me?.name?.ifBlank { null }.orEmpty(),
                    stats = accountStats(
                        groupCount = (groupsState as? RequestStatus.Success)?.data?.size ?: 0,
                        myId = me?.id.orEmpty(),
                        expenses = (history as? RequestStatus.Success)?.data ?: emptyList(),
                    ),
                )
            }
            composable(Routes.PaymentHistory) {
                val historyVm: PaymentHistoryViewModel = hiltViewModel()
                val history by historyVm.history.collectAsState()
                val historyRefreshError by historyVm.refreshError.collectAsState()
                val historyLoaded by historyVm.refreshedOnce.collectAsState()
                // Same explicit-refresh-on-entry as Account (see above): without this,
                // refresh() is only reachable from the error state and a fresh session
                // shows "No activity yet" forever.
                LaunchedEffect(Unit) { historyVm.refresh() }
                val groupsVm: GroupsViewModel = hiltViewModel()
                val groupsState by groupsVm.groupsState.collectAsState()
                val names = (groupsState as? RequestStatus.Success)?.data
                    ?.associate { it.id to it.name }.orEmpty()
                PaymentHistoryScreen(
                    onBack = { navController.popBackStack() },
                    // Stored entries are confirmed ledger charges (server-persisted or
                    // gateway-txId-backed settlements), so all read SUCCESS. Gateway
                    // failures never persist ("nothing charged") — there is no honest
                    // FAILED source until a status contract lands, so none is invented.
                    transactions = (history as? RequestStatus.Success)?.data?.map {
                        TxUi(
                            it.id,
                            it.description.ifBlank { "Expense" },
                            it.amountCents,
                            TxStatus.SUCCESS,
                            names[it.groupId] ?: shortGroupRef(it.groupId),
                        )
                    } ?: emptyList(),
                    isLoading = history is RequestStatus.Pending,
                    errorMessage = (history as? RequestStatus.Failed)?.message
                        ?: historyRefreshError,
                    onRetry = historyVm::retry,
                    onRefresh = historyVm::refresh,
                    hasLoaded = historyLoaded,
                )
            }
            composable(Routes.Settings) {
                val authVm: AuthViewModel = hiltViewModel()
                val scope = rememberCoroutineScope()
                val profileVm: ProfileViewModel = hiltViewModel()
                val profileState by profileVm.profile.collectAsState()
                val prefs = (profileState as? RequestStatus.Success)?.data?.notificationPrefs
                val darkTheme by profileVm.darkTheme.collectAsState()
                SettingsScreen(
                    onEditProfile = { navController.navigate(Routes.EditProfile) },
                    onSignOut = { scope.launch { signOut(authVm, navController) } },
                    newExpense = prefs?.newExpense ?: true,
                    onNewExpenseChange = {
                        profileVm.updatePrefs((prefs ?: NotificationPrefs()).copy(newExpense = it))
                    },
                    reminders = prefs?.reminders ?: false,
                    onRemindersChange = {
                        profileVm.updatePrefs((prefs ?: NotificationPrefs()).copy(reminders = it))
                    },
                    settlements = prefs?.settlementUpdates ?: false,
                    onSettlementsChange = {
                        profileVm.updatePrefs((prefs ?: NotificationPrefs()).copy(settlementUpdates = it))
                    },
                    darkTheme = darkTheme,
                    onDarkThemeChange = profileVm::setDarkTheme,
                )
            }
            composable(Routes.EditProfile) {
                val profileVm: ProfileViewModel = hiltViewModel()
                val profileState by profileVm.profile.collectAsState()
                val saveResult by profileVm.saveResult.collectAsState()
                val me = (profileState as? RequestStatus.Success)?.data
                // key() refreshes initial field values once the profile loads.
                key(me?.id) {
                    EditProfileScreen(
                        onDone = {
                            profileVm.clearSaveResult()
                            notifyScope.launch { statusBus.notify(StatusEvent("profile-save", "Profile saved")) }
                            navController.popBackStack()
                        },
                        onCancel = { navController.popBackStack() },
                        initialName = me?.name.orEmpty(),
                        initialEmail = me?.contact.orEmpty(),
                        saveResult = saveResult,
                        onSave = { name, email -> profileVm.save(name, email) },
                        onDismissError = profileVm::clearSaveResult,
                    )
                }
            }
            composable(
                Routes.Receipt,
                listOf(
                    navArgument(Routes.ArgTxId) { nullable = true },
                    navArgument(Routes.ArgAmount) { type = NavType.LongType; defaultValue = 0L },
                    navArgument(Routes.ArgGroupId) { type = NavType.StringType; nullable = true },
                ),
            ) {
                val tx = it.arguments?.getString(Routes.ArgTxId).orEmpty()
                val amount = it.arguments?.getLong(Routes.ArgAmount) ?: 0L
                val gid = it.arguments?.getString(Routes.ArgGroupId).orEmpty()
                // txId is the Transaction line — the Group line resolves from cached groups.
                val groupsVm: GroupsViewModel = hiltViewModel()
                val groupsState by groupsVm.groupsState.collectAsState()
                val groupName = (groupsState as? RequestStatus.Success)?.data
                    ?.firstOrNull { g -> g.id == gid }?.name
                ReceiptScreen(
                    groupId = gid,
                    groupName = groupName,
                    amountCents = amount,
                    txId = tx,
                    onDone = { navController.popBackStack() },
                )
            }
        }
    }
}

/**
 * Auth-gate: fires [onUnauthenticated] exactly when the session VM reports logged-out.
 * Pending (token read in flight) renders nothing — no flash, no graph hack.
 */
@Composable
private fun RequireAuth(session: RequestStatus<User?>, onUnauthenticated: () -> Unit) {
    if (session is RequestStatus.Success && session.data == null) {
        LaunchedEffect(Unit) { onUnauthenticated() }
    }
}

/**
 * Sign-out: awaits the session + cache clear via [AuthViewModel] before landing on Login
 * (popped, so Back never returns to authenticated screens), closing the race where a
 * stale session re-triggered the Login self-heal or a refresh repopulated Room after
 * `clearAllTables`. Mirrors the RequireAuth redirect (same popUpTo); launchSingleTop
 * dedupes against its auto-redirect.
 */
private suspend fun signOut(authVm: AuthViewModel, navController: NavHostController) {
    authVm.signOut()
    navController.navigate(Routes.Login) {
        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
        launchSingleTop = true
    }
}

/** Fallback group ref for ids missing from the cached groups — never a full UUID. */
private fun shortGroupRef(id: String): String =
    if (id.length > 8) "Group ••••${id.takeLast(4)}" else id

/** Already-authed users never sit on Login/Register (covers the slow-token-read race). */@Composable
private fun ForwardWhenAuthenticated(session: RequestStatus<User?>, navController: NavHostController) {
    if ((session as? RequestStatus.Success)?.data != null) {
        LaunchedEffect(Unit) {
            navController.navigate(Routes.groups()) {
                popUpTo(Routes.Login) { inclusive = true }
            }
        }
    }
}

@Composable
private fun BottomTabs(selected: String?, onTab: (String) -> Unit) {
    // Text glyphs: no icons dep in this slice.
    val tabs = listOf(
        "Groups" to Routes.groups(),
        "Account" to Routes.Account,
        "Settings" to Routes.Settings,
    )
    NavigationBar {
        tabs.forEach { (label, route) ->
            val isSelected = selected == route || (label == "Groups" && selected == Routes.Groups)
            NavigationBarItem(
                selected = isSelected,
                onClick = { if (!isSelected) onTab(route) },
                icon = { Text(label.take(1)) },
                label = { Text(label) },
            )
        }
    }
}
