package com.mipresupuestocr.app.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mipresupuestocr.app.ui.budget.BudgetScreen
import com.mipresupuestocr.app.ui.calculator.CalculatorScreen
import com.mipresupuestocr.app.ui.dashboard.DashboardScreen
import com.mipresupuestocr.app.ui.holdings.AddEditHoldingScreen
import com.mipresupuestocr.app.ui.portfolio.PortfolioScreen
import com.mipresupuestocr.app.ui.settings.SettingsScreen
import com.mipresupuestocr.app.ui.transactions.AddEditTransactionScreen

@Composable
fun MiPresupuestoApp(
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevelDestinations = TopLevelDestination.entries

    fun navigateToTopLevel(destination: TopLevelDestination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (topLevelDestinations.any { it.route == currentRoute }) {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = { navigateToTopLevel(destination) },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Dashboard.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable(TopLevelDestination.Dashboard.route) {
                DashboardScreen(
                    onAddTransaction = { navController.navigate(Routes.addEditTransaction()) },
                    onOpenBudget = { navigateToTopLevel(TopLevelDestination.Budget) },
                    onOpenPortfolio = { navigateToTopLevel(TopLevelDestination.Portfolio) },
                )
            }

            composable(TopLevelDestination.Budget.route) {
                BudgetScreen(
                    onAddTransaction = { navController.navigate(Routes.addEditTransaction()) },
                    onEditTransaction = { id -> navController.navigate(Routes.addEditTransaction(id)) },
                )
            }

            composable(TopLevelDestination.Calculator.route) {
                CalculatorScreen()
            }

            composable(TopLevelDestination.Portfolio.route) {
                PortfolioScreen(
                    onAddHolding = { navController.navigate(Routes.addEditHolding()) },
                    onEditHolding = { id -> navController.navigate(Routes.addEditHolding(id)) },
                )
            }

            composable(TopLevelDestination.Settings.route) {
                SettingsScreen()
            }

            composable(
                route = Routes.ADD_EDIT_TRANSACTION,
                arguments = listOf(
                    navArgument(Routes.TRANSACTION_ID_ARG) {
                        type = NavType.LongType
                        defaultValue = Routes.NEW_TRANSACTION_ID
                    },
                ),
            ) {
                AddEditTransactionScreen(onFinished = { navController.popBackStack() })
            }

            composable(
                route = Routes.ADD_EDIT_HOLDING,
                arguments = listOf(
                    navArgument(Routes.HOLDING_ID_ARG) {
                        type = NavType.LongType
                        defaultValue = Routes.NEW_TRANSACTION_ID
                    },
                ),
            ) {
                AddEditHoldingScreen(onFinished = { navController.popBackStack() })
            }
        }
    }
}
