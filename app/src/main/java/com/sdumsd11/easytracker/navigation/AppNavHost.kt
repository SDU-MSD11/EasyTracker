package com.sdumsd11.easytracker.navigation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.sdumsd11.easytracker.R
import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.ui.StatusEvent
import com.sdumsd11.easytracker.ui.components.BackgroundSuccessHost
import com.sdumsd11.easytracker.ui.components.NotificationPermissionPrompt
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    // Global status surface: success confirmations and background-retry wins post a
    // Snackbar here (audible/haptic only for BackgroundSuccess). VMs feed it via
    // @Inject StatusBus; screens stay stateless.
    val context = LocalContext.current
    val statusBus = remember(context) { context.applicationContext.statusBus() }
    val snackbarHostState = remember { SnackbarHostState() }
    val notifyScope = rememberCoroutineScope()
    // Notification taps arrive as VIEW intents (`easytracker://<route>`, singleTop +
    // scheme filter in the manifest). NavHost handles the activity intent on first
    // composition and re-delivers warm taps itself — no manual handleDeepLink here
    // (that double-navigated). Valid taps land via the graph's navDeepLink entries
    // (cold start builds Splash -> destination, warm taps append on top); missing or
    // malformed Uris are ignored so Splash routing runs as the fallback.
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
                // Branding only — routing is session-driven below (single owner).
                SplashScreen(onDone = {})
                val firstPaintMs = remember { System.currentTimeMillis() }
                // Exactly-once across effect restarts; the popUpTo collapses stray duplicates.
                var splashRouted by rememberSaveable { mutableStateOf(false) }
                // Session-driven gate: navigate exactly once when the session settles
                // (Success(user) -> Groups, Success(null) -> Login) after the branding
                // dwell. Keying on `session` restarts the bounded wait on every emission,
                // so the common path never pays the timeout; a session that never
                // settles falls back to Login instead of wedging startup.
                LaunchedEffect(session) {
                    val dest = awaitSplashDestination(authVm.session, firstPaintMs)
                    if (!shouldRouteFromSplash(
                            navController.currentBackStackEntry?.destination?.route,
                            splashRouted,
                        )
                    ) return@LaunchedEffect
                    splashRouted = true
                    navController.navigate(dest) {
                        popUpTo(Routes.Splash) { inclusive = true }
                    }
                }
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
                deepLinks = listOf(
                    navDeepLink { uriPattern = "easytracker://groups" },
                    navDeepLink { uriPattern = "easytracker://groups?tab={tab}" },
                ),
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
                val promptSeen by groupsVm.notificationPromptSeen.collectAsState()
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
                    // First Groups paint after login: one-shot inline rationale card.
                    topBanner = {
                        NotificationPermissionPrompt(
                            promptSeen = promptSeen,
                            onPromptSeen = groupsVm::markNotificationPromptSeen,
                            onOpenSettings = { navController.navigate(Routes.Settings) },
                        )
                    },
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
                deepLinks = listOf(
                    navDeepLink { uriPattern = "easytracker://groups/{groupId}" },
                ),
            ) {
                val authVm: AuthViewModel = hiltViewModel()
                val session by authVm.session.collectAsState()
                RequireAuth(session) {
                    navController.navigate(Routes.Login) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
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
                // Payer is fixed to the session user (screen shows it display-only):
                // the backend derives the payer from the JWT, so the dropdown value is
                // never trusted — onSave always passes the session id (null until the
                // session loads; the screen stays disabled, never another member).
                // No owner gate here: any member can set up a debt.
                val sessionId = (session as? RequestStatus.Success)?.data?.id
                val effectivePayer = sessionId?.takeIf { it.isNotBlank() }
                SetupDebtScreen(
                    groupId = gid,
                    debtId = debtId,
                    onDone = {
                        debtVm.clearSaveResult()
                        notifyScope.launch { statusBus.notify(StatusEvent("debt-save", "Debt saved")) }
                        navController.popBackStack()
                    },
                    saveResult = saveResult,
                    onSave = { _, cents, note -> debtVm.save(gid, effectivePayer.orEmpty(), cents, note, debtId) },
                    groupName = (groupState as? RequestStatus.Success)?.data?.name,
                    participants = participants,
                    initialPayerId = editingExpense?.payerId ?: effectivePayer,
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
            composable(
                Routes.PaymentHistory,
                deepLinks = listOf(
                    navDeepLink { uriPattern = "easytracker://account/history" },
                ),
            ) {
                val authVm: AuthViewModel = hiltViewModel()
                val session by authVm.session.collectAsState()
                RequireAuth(session) {
                    navController.navigate(Routes.Login) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
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
            composable(
                Routes.Settings,
                deepLinks = listOf(
                    navDeepLink { uriPattern = "easytracker://settings" },
                ),
            ) {
                val authVm: AuthViewModel = hiltViewModel()
                val session by authVm.session.collectAsState()
                RequireAuth(session) {
                    navController.navigate(Routes.Login) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                val scope = rememberCoroutineScope()
                val profileVm: ProfileViewModel = hiltViewModel()
                val profileState by profileVm.profile.collectAsState()
                val prefs = (profileState as? RequestStatus.Success)?.data?.notificationPrefs
                val darkTheme by profileVm.darkTheme.collectAsState()
                // Re-read the OS grant when returning from system settings; null below 33.
                var resumeTick by remember { mutableIntStateOf(0) }
                val lifecycleOwner = context as? LifecycleOwner
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) resumeTick++
                    }
                    lifecycleOwner?.lifecycle?.addObserver(observer)
                    onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
                }
                val systemNotificationsOn: Boolean? = remember(resumeTick) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        null
                    } else {
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) == PackageManager.PERMISSION_GRANTED
                    }
                }
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
                    systemNotificationsOn = systemNotificationsOn,
                    onOpenSystemSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            },
                        )
                    },
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
                        initialContact = me?.contact.orEmpty(),
                        saveResult = saveResult,
                        onSave = { name, contact -> profileVm.save(name, contact) },
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
                deepLinks = listOf(
                    navDeepLink { uriPattern = "easytracker://receipts" },
                    navDeepLink {
                        uriPattern = "easytracker://receipts?txId={txId}&amountCents={amountCents}&groupId={groupId}"
                    },
                ),
            ) {
                val authVm: AuthViewModel = hiltViewModel()
                val session by authVm.session.collectAsState()
                RequireAuth(session) {
                    navController.navigate(Routes.Login) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
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

/** Minimum Splash branding dwell from first paint; the gate never routes earlier. */
internal const val SPLASH_MIN_DWELL_MS = 800L

/**
 * Hung-session bound: the token read is async DataStore IO — if it never lands
 * (slow disk, a thrown read killing the share), startup must still route. Past this
 * the gate treats the session as logged-out.
 */
internal const val SPLASH_SESSION_TIMEOUT_MS = 5_000L

/**
 * Splash routing decision (pure timing/flow logic, no nav): awaits the settled session
 * up to [timeoutMs], enforces the branding dwell from [startedMs], then maps
 * user -> Groups, anything else (logged-out OR still-unsettled) -> Login. Never
 * suspends past timeout + dwell, so a wedged session read can never wedge startup.
 */
internal suspend fun awaitSplashDestination(
    sessions: Flow<RequestStatus<User?>>,
    startedMs: Long,
    nowMs: () -> Long = System::currentTimeMillis,
    timeoutMs: Long = SPLASH_SESSION_TIMEOUT_MS,
    minDwellMs: Long = SPLASH_MIN_DWELL_MS,
): String {
    val settled = withTimeoutOrNull(timeoutMs) {
        sessions.first { it is RequestStatus.Success }
    } as? RequestStatus.Success
    val remain = minDwellMs - (nowMs() - startedMs)
    if (remain > 0) delay(remain)
    val user = settled?.data
    return if (user != null) Routes.groups() else Routes.Login
}

/**
 * Splash routing guard (pure, unit-tested): route only once and only while the
 * current entry is still Splash. A handled deep-link tap already landed elsewhere,
 * so its stack must not be disturbed; any other 2-entry stack with Splash on top
 * (the old `size > 1` SKIP wedge) still routes — the popUpTo collapses duplicates.
 */
internal fun shouldRouteFromSplash(currentRoute: String?, alreadyRouted: Boolean): Boolean =
    !alreadyRouted && currentRoute == Routes.Splash

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
    // (iconRes, label, route) — 48dp min touch targets via NavigationBarItem defaults.
    val tabs = listOf(
        Triple(R.drawable.group, "Groups", Routes.groups()),
        Triple(R.drawable.person, "Account", Routes.Account),
        Triple(R.drawable.settings, "Settings", Routes.Settings),
    )
    NavigationBar {
        tabs.forEach { (iconRes, label, route) ->
            val isSelected = selected == route || (label == "Groups" && selected == Routes.Groups)
            NavigationBarItem(
                selected = isSelected,
                onClick = { if (!isSelected) onTab(route) },
                icon = {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = { Text(label) },
            )
        }
    }
}
