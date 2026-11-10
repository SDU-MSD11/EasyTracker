package com.sdumsd11.easytracker.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import androidx.compose.material3.Text
import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.domain.model.User
import com.sdumsd11.easytracker.ui.screens.AccountScreen
import com.sdumsd11.easytracker.ui.screens.CreateGroupScreen
import com.sdumsd11.easytracker.ui.screens.EditGroupScreen
import com.sdumsd11.easytracker.ui.screens.EditProfileScreen
import com.sdumsd11.easytracker.ui.screens.GroupDetailScreen
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
import com.sdumsd11.easytracker.ui.viewmodels.AuthViewModel
import com.sdumsd11.easytracker.ui.viewmodels.GroupDetailViewModel

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
    Scaffold(
        modifier = modifier,
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
                GroupsOverviewScreen(
                    onGroupClick = { navController.navigate(Routes.groupDetail(it)) },
                    onCreateGroup = { navController.navigate(Routes.CreateGroup) },
                )
            }
            composable(Routes.CreateGroup) {
                CreateGroupScreen(
                    onCreated = {
                        navController.navigate(Routes.groupDetail(it)) {
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
                GroupDetailScreen(
                    groupId = gid,
                    isOwner = isOwner == true,
                    onMakePayment = { navController.navigate(Routes.makePayment(it)) },
                    onEditGroup = { navController.navigate(Routes.editGroup(it)) },
                    onSetupDebt = { navController.navigate(Routes.setupDebt(it)) },
                    onBack = { navController.popBackStack() },
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
                    EditGroupScreen(
                        groupId = gid,
                        isOwner = isOwner == true,
                        onSetupDebt = { navController.navigate(Routes.setupDebt(it)) },
                        onBack = { navController.popBackStack() },
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
                SetupDebtScreen(
                    groupId = it.arguments?.getString(Routes.ArgGroupId).orEmpty(),
                    debtId = it.arguments?.getString(Routes.ArgDebtId),
                    onDone = { navController.popBackStack() },
                )
            }
            composable(
                Routes.MakePayment,
                listOf(navArgument(Routes.ArgGroupId) { type = NavType.StringType }),
            ) {
                val gid = it.arguments?.getString(Routes.ArgGroupId).orEmpty()
                MakePaymentScreen(
                    groupId = gid,
                    onResult = { _, ok ->
                        if (ok) {
                            navController.navigate(Routes.paymentSuccess(gid)) {
                                // Point-of-no-return: back lands on GroupDetail, never resubmit.
                                popUpTo(Routes.MakePayment) { inclusive = true }
                            }
                        } else {
                            navController.navigate(Routes.paymentFailed()) { launchSingleTop = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Routes.PaymentSuccess,
                listOf(navArgument(Routes.ArgTxId) { type = NavType.StringType }),
            ) {
                val tx = it.arguments?.getString(Routes.ArgTxId).orEmpty()
                PaymentSuccessScreen(
                    // NOTE(txId semantics): stub gateway reuses groupId as txId. The real gateway
                    // returns a distinct txId — then pass it through paymentSuccess() here and
                    // treat ArgTxId as a transaction id (receipt() entry already does).
                    groupId = tx,
                    onBackToGroup = { navController.popBackStack() },
                    onExportReceipt = { navController.navigate(Routes.receipt(it)) },
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
                AccountScreen(
                    onHistory = { navController.navigate(Routes.PaymentHistory) },
                    onSettings = { navController.navigate(Routes.Settings) },
                )
            }
            composable(Routes.PaymentHistory) {
                PaymentHistoryScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.Settings) {
                SettingsScreen(onEditProfile = { navController.navigate(Routes.EditProfile) })
            }
            composable(Routes.EditProfile) {
                EditProfileScreen(onDone = { navController.popBackStack() })
            }
            composable(
                Routes.Receipt,
                listOf(navArgument(Routes.ArgTxId) { nullable = true }),
            ) {
                ReceiptScreen(
                    groupId = it.arguments?.getString(Routes.ArgTxId).orEmpty(),
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

/** Already-authed users never sit on Login/Register (covers the slow-token-read race). */
@Composable
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
