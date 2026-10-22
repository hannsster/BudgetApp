package com.mipresupuestocr.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.ui.graphics.vector.ImageVector
import com.mipresupuestocr.app.R

enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Dashboard("dashboard", R.string.nav_dashboard, Icons.Outlined.Dashboard),
    Budget("budget", R.string.nav_budget, Icons.Outlined.AccountBalanceWallet),
    Calculator("calculator", R.string.nav_calculator, Icons.Outlined.Calculate),
    Portfolio("portfolio", R.string.nav_portfolio, Icons.Outlined.ShowChart),
    Settings("settings", R.string.nav_settings, Icons.Outlined.Settings),
}

object Routes {
    const val TRANSACTION_ID_ARG = "transactionId"
    const val HOLDING_ID_ARG = "holdingId"

    const val ADD_EDIT_TRANSACTION = "transaction?$TRANSACTION_ID_ARG={$TRANSACTION_ID_ARG}"
    const val ADD_EDIT_HOLDING = "holding?$HOLDING_ID_ARG={$HOLDING_ID_ARG}"

    const val NEW_TRANSACTION_ID = -1L

    fun addEditTransaction(transactionId: Long = NEW_TRANSACTION_ID): String =
        "transaction?$TRANSACTION_ID_ARG=$transactionId"

    fun addEditHolding(holdingId: Long = NEW_TRANSACTION_ID): String =
        "holding?$HOLDING_ID_ARG=$holdingId"
}
