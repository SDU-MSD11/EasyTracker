package com.sdumsd11.easytracker.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sdumsd11.easytracker.ui.screens.AccountScreen
import com.sdumsd11.easytracker.ui.screens.CreateGroupScreen
import com.sdumsd11.easytracker.ui.screens.EditGroupScreen
import com.sdumsd11.easytracker.ui.screens.EditProfileScreen
import com.sdumsd11.easytracker.ui.screens.GroupDetailScreen
import com.sdumsd11.easytracker.ui.screens.GroupsOverviewScreen
import com.sdumsd11.easytracker.ui.screens.MakePaymentScreen
import com.sdumsd11.easytracker.ui.screens.PaymentFailedScreen
import com.sdumsd11.easytracker.ui.screens.PaymentHistoryScreen
import com.sdumsd11.easytracker.ui.screens.PaymentSuccessScreen
import com.sdumsd11.easytracker.ui.screens.ReceiptScreen
import com.sdumsd11.easytracker.ui.screens.SettingsScreen
import com.sdumsd11.easytracker.ui.screens.SetupDebtScreen
import com.sdumsd11.easytracker.ui.screens.SplashScreen

@Composable
fun rememberEasyNavController(): NavHostController = rememberNavController()

/**
 * Real graph over [Routes] (LAW — patterns/arg keys unchanged). Bottom tabs on the
 * Groups/Account/Settings roots only; flows carry Back buttons. Screens stay lambda-driven,
 * each lambda is a single `navController` call.
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
                SplashScreen(onDone = {
                    navController.navigate(Routes.groups()) {
                        popUpTo(Routes.Splash) { inclusive = true }
                    }
                })
            }
            composable(
                Routes.Groups,
                listOf(navArgument(Routes.ArgTab) { defaultValue = GroupsTab.JOINED }),
            ) {
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
                GroupDetailScreen(
                    groupId = gid,
                    isOwner = true, // stub; VM gates EditGroup for real
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
                EditGroupScreen(
                    groupId = gid,
                    isOwner = true, // stub; VM redirects non-owners to GroupDetail
                    onSetupDebt = { navController.navigate(Routes.setupDebt(it)) },
                    onBack = { navController.popBackStack() },
                )
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
