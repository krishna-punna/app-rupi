package com.dailyrupi.app.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dailyrupi.app.ui.auth.ChangePasswordScreen
import com.dailyrupi.app.ui.auth.ServerSetupScreen
import com.dailyrupi.app.ui.budgets.BudgetsScreen
import com.dailyrupi.app.ui.expenses.ExpenseEditScreen
import com.dailyrupi.app.ui.expenses.ExpensesScreen
import com.dailyrupi.app.ui.more.MoreScreen
import com.dailyrupi.app.ui.sync.SyncScreen

object Routes {
    const val EXPENSES = "expenses"
    const val BUDGETS = "budgets"
    const val MORE = "more"
    const val NEW_EXPENSE = "new-expense"
    const val EDIT_EXPENSE = "expense/{key}"
    const val PASSWORD = "password"
    const val SERVER = "server"
    const val SYNC = "sync"

    fun editExpense(key: String) = "expense/$key"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.EXPENSES, "Expenses", Icons.Filled.Home),
    Tab(Routes.BUDGETS, "Budgets", Icons.AutoMirrored.Filled.List),
    Tab(Routes.MORE, "More", Icons.Filled.Menu),
)

/** The logged-in app: Expenses, Budgets and More in a bottom bar, with Add / edit expense on top. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = tabs.any { it.route == currentRoute }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navController.openTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.EXPENSES,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.EXPENSES) {
                ExpensesScreen(
                    onAdd = { navController.navigate(Routes.NEW_EXPENSE) },
                    onOpen = { navController.navigate(Routes.editExpense(it)) },
                    onSync = { navController.navigate(Routes.SYNC) },
                )
            }
            composable(Routes.BUDGETS) { BudgetsScreen() }
            composable(Routes.MORE) {
                MoreScreen(
                    onSync = { navController.navigate(Routes.SYNC) },
                    onChangePassword = { navController.navigate(Routes.PASSWORD) },
                    onChangeServer = { navController.navigate(Routes.SERVER) },
                )
            }
            composable(Routes.NEW_EXPENSE) {
                ExpenseEditScreen(onDone = { navController.popBackStack() })
            }
            composable(
                Routes.EDIT_EXPENSE,
                arguments = listOf(navArgument("key") { type = NavType.StringType }),
            ) {
                ExpenseEditScreen(onDone = { navController.popBackStack() })
            }
            composable(Routes.SYNC) {
                SyncScreen(
                    onBack = { navController.popBackStack() },
                    onOpen = { navController.navigate(Routes.editExpense(it)) },
                )
            }
            composable(Routes.PASSWORD) {
                ChangePasswordScreen(forced = false, onBack = { navController.popBackStack() })
            }
            composable(Routes.SERVER) {
                ServerSetupScreen(
                    onDone = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun NavHostController.openTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
