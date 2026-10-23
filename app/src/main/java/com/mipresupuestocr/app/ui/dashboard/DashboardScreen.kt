package com.mipresupuestocr.app.ui.dashboard

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipresupuestocr.app.R
import com.mipresupuestocr.app.ui.components.CardTitle
import com.mipresupuestocr.app.ui.components.EmptyState
import com.mipresupuestocr.app.ui.components.ErrorBanner
import com.mipresupuestocr.app.ui.components.KeyValueRow
import com.mipresupuestocr.app.ui.components.LoadingBox
import com.mipresupuestocr.app.ui.components.SectionCard
import com.mipresupuestocr.app.ui.components.StatusChip
import com.mipresupuestocr.app.ui.components.TargetProgress
import com.mipresupuestocr.app.ui.theme.positiveColor
import com.mipresupuestocr.app.ui.util.MoneyFormat
import com.mipresupuestocr.app.ui.util.accountTypeLabel
import com.mipresupuestocr.app.ui.util.parseAmount
import com.mipresupuestocr.domain.model.AccountBalance
import com.mipresupuestocr.domain.model.CategoryProgress
import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.Money
import com.mipresupuestocr.domain.model.NetWorth
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAddTransaction: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenPortfolio: () -> Unit,
    onOpenAccounts: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var balanceField by remember { mutableStateOf<BalanceField?>(null) }

    LaunchedEffect(state.message) {
        val pending = state.message ?: return@LaunchedEffect
        val text = when (pending) {
            DashboardMessage.Refreshed -> context.getString(R.string.dashboard_refresh_done)
            DashboardMessage.RefreshedFromCache -> context.getString(R.string.dashboard_stale)
            DashboardMessage.BalanceAdjusted -> context.getString(R.string.balance_adjusted_saved)
            is DashboardMessage.MissingPrices -> context.getString(
                R.string.dashboard_refresh_partial,
                pending.tickers.joinToString(", "),
            )
        }
        snackbarHostState.showSnackbar(text)
        viewModel.consumeMessage()
    }

    val activeSnapshot = state.snapshot
    balanceField?.let { field ->
        val budget = activeSnapshot?.budget
        if (budget != null) {
            val amount = when (field) {
                BalanceField.INCOME -> budget.income
                BalanceField.SPENT -> budget.spent
                BalanceField.AVAILABLE -> budget.remaining
            }
            BalanceDialog(
                field = field,
                initial = amount,
                isAdjusted = when (field) {
                    BalanceField.INCOME -> budget.incomeAdjusted
                    BalanceField.SPENT -> false
                    BalanceField.AVAILABLE -> budget.incomeAdjusted
                },
                onDismiss = { balanceField = null },
                onSave = { text ->
                    when (field) {
                        BalanceField.INCOME -> viewModel.setIncomeBalance(text)
                        BalanceField.SPENT -> viewModel.setSpentBalance(text)
                        BalanceField.AVAILABLE -> viewModel.setAvailableBalance(text)
                    }
                    balanceField = null
                },
                onClear = {
                    when (field) {
                        BalanceField.INCOME -> viewModel.clearIncomeCorrection()
                        BalanceField.SPENT -> viewModel.clearSpentCorrection()
                        BalanceField.AVAILABLE -> viewModel.clearIncomeCorrection()
                    }
                    balanceField = null
                },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_title)) },
                actions = {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.cd_refresh),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddTransaction,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.budget_add_transaction)) },
            )
        },
    ) { padding ->
        val snapshot = state.snapshot
        if (state.isLoading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val netWorth = state.netWorth
            if (netWorth != null && (state.accounts.isNotEmpty() || snapshot?.portfolio?.isEmpty == false)) {
                item { NetWorthCard(netWorth) }
            }

            item {
                PeriodHeader(
                    periodLabel = state.period?.let { period ->
                        stringResource(
                            R.string.dashboard_period,
                            MoneyFormat.formatDate(period.start),
                            MoneyFormat.formatDate(period.endInclusive),
                        )
                    }.orEmpty(),
                    onPrevious = viewModel::previousPeriod,
                    onNext = viewModel::nextPeriod,
                )
            }

            state.error?.let { error ->
                item {
                    ErrorBanner(error = error, onRetry = viewModel::refresh)
                }
            }

            if (snapshot != null) {
                val budget = snapshot.budget

                item {
                    SectionCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { balanceField = BalanceField.AVAILABLE },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(R.string.dashboard_remaining),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (budget.incomeAdjusted) {
                                        Spacer(Modifier.width(6.dp))
                                        StatusChip(stringResource(R.string.balance_adjusted_chip))
                                    }
                                }
                                Text(
                                    text = MoneyFormat.format(budget.remaining),
                                    style = MaterialTheme.typography.displaySmall,
                                    color = if (budget.remaining.isNegative) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = stringResource(R.string.cd_edit_amount),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (budget.income.isPositive) {
                            val fraction = (budget.spent.amountMinor.toDouble() / budget.income.amountMinor)
                                .coerceIn(0.0, 1.0)
                            LinearProgressIndicator(
                                progress = { fraction.toFloat() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        KeyValueRow(
                            label = stringResource(R.string.dashboard_income),
                            value = MoneyFormat.format(budget.income),
                            adjusted = budget.incomeAdjusted,
                            onClick = { balanceField = BalanceField.INCOME },
                        )
                        KeyValueRow(
                            label = stringResource(R.string.dashboard_spent),
                            value = MoneyFormat.format(budget.spent),
                            onClick = { balanceField = BalanceField.SPENT },
                        )
                        Text(
                            text = stringResource(R.string.balance_adjust_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item {
                    AccountsCard(
                        accounts = state.accounts,
                        onOpenAccounts = onOpenAccounts,
                    )
                }

                item {
                    SectionCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CardTitle(stringResource(R.string.dashboard_fx_title))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (snapshot.rate?.isManual == true) {
                                    StatusChip(stringResource(R.string.dashboard_manual_badge))
                                }
                                if (snapshot.rateIsStale) {
                                    StatusChip(
                                        text = stringResource(R.string.dashboard_stale),
                                        isWarning = true,
                                    )
                                }
                            }
                        }
                        val rate = snapshot.rate
                        if (rate == null) {
                            Text(
                                text = stringResource(R.string.dashboard_not_updated),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = "1 ${rate.base.isoCode} = ${MoneyFormat.formatRate(rate.rate)} ${rate.quote.isoCode}",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                text = stringResource(R.string.dashboard_source, rate.source),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(
                                    R.string.dashboard_updated,
                                    MoneyFormat.formatDateTime(rate.asOf),
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item {
                    PortfolioSummaryCard(
                        snapshot = snapshot,
                        onOpenPortfolio = onOpenPortfolio,
                        onRefresh = viewModel::refresh,
                        isRefreshing = state.isRefreshing,
                    )
                }

                val withTargets = budget.categoryProgress.filter { it.target != null }
                if (withTargets.isNotEmpty()) {
                    item {
                        SectionCard {
                            CardTitle(stringResource(R.string.dashboard_categories_title))
                            withTargets.take(6).forEach { progress ->
                                CategoryRow(progress)
                            }
                            if (budget.uncategorizedSpent.isPositive) {
                                Text(
                                    text = stringResource(
                                        R.string.budget_uncategorized,
                                        MoneyFormat.format(budget.uncategorizedSpent),
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = onOpenBudget) {
                                Text(stringResource(R.string.nav_budget))
                            }
                        }
                    }
                }

                if (budget.isEmpty && snapshot.portfolio.isEmpty) {
                    item {
                        EmptyState(
                            title = stringResource(R.string.dashboard_empty_title),
                            body = stringResource(R.string.dashboard_empty_body),
                            actionLabel = stringResource(R.string.budget_add_expense),
                            onAction = onAddTransaction,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Everything the user owns, on one line: account balances plus the portfolio at the current rate.
 * Shown above the period figures because it answers "how much do I have", not "how is this month".
 */
@Composable
private fun NetWorthCard(netWorth: NetWorth) {
    SectionCard {
        Text(
            text = stringResource(R.string.net_worth_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = MoneyFormat.format(netWorth.total),
            style = MaterialTheme.typography.displayMedium,
            color = if (netWorth.total.isNegative) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
        Text(
            text = stringResource(R.string.net_worth_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
        KeyValueRow(
            label = stringResource(R.string.net_worth_accounts),
            value = MoneyFormat.format(netWorth.accountsTotal),
        )
        KeyValueRow(
            label = stringResource(R.string.net_worth_portfolio),
            value = netWorth.portfolioCrc?.let { MoneyFormat.format(it) }
                ?: stringResource(R.string.net_worth_portfolio_unpriced),
        )
        if (netWorth.isPartial) {
            Text(
                text = stringResource(R.string.net_worth_partial),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun PeriodHeader(
    periodLabel: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.Outlined.ChevronLeft,
                contentDescription = stringResource(R.string.cd_previous_period),
            )
        }
        Text(
            text = periodLabel,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = stringResource(R.string.cd_next_period),
            )
        }
    }
}

@Composable
private fun CategoryRow(progress: CategoryProgress) {
    val target = progress.target ?: return
    TargetProgress(
        label = progress.category.name,
        supporting = stringResource(
            R.string.budget_target_progress,
            MoneyFormat.format(progress.spent),
            MoneyFormat.format(target),
        ),
        fraction = progress.progressFraction,
    )
}

/** Where the money sits, per account. Balances cover all time, not just the visible period. */
@Composable
private fun AccountsCard(
    accounts: List<AccountBalance>,
    onOpenAccounts: () -> Unit,
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardTitle(stringResource(R.string.accounts_title))
            TextButton(onClick = onOpenAccounts) {
                Text(stringResource(R.string.accounts_manage))
            }
        }

        if (accounts.isEmpty()) {
            Text(
                text = stringResource(R.string.accounts_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenAccounts) {
                Text(stringResource(R.string.accounts_add))
            }
        } else {
            accounts.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = row.account.name,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = accountTypeLabel(row.account.type),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = MoneyFormat.format(row.balance),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (row.balance.isNegative) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
            HorizontalDivider()
            KeyValueRow(
                label = stringResource(R.string.accounts_total),
                value = MoneyFormat.format(
                    Money(accounts.sumOf { it.balance.amountMinor }, CurrencyCode.CRC),
                ),
                emphasize = true,
            )
        }
    }
}

@Composable
private fun PortfolioSummaryCard(
    snapshot: com.mipresupuestocr.domain.model.DashboardSnapshot,
    onOpenPortfolio: () -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
) {
    val portfolio = snapshot.portfolio
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardTitle(stringResource(R.string.dashboard_portfolio_title))
            if (portfolio.totalUsd.isPositive) {
                TextButton(onClick = onOpenPortfolio) {
                    Text(stringResource(R.string.nav_portfolio))
                }
            }
        }

        if (portfolio.isEmpty) {
            Text(
                text = stringResource(R.string.portfolio_empty_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenPortfolio) {
                Text(stringResource(R.string.portfolio_add_holding))
            }
        } else {
            KeyValueRow(
                label = stringResource(R.string.dashboard_usd),
                value = MoneyFormat.format(portfolio.totalUsd),
                emphasize = true,
            )
            KeyValueRow(
                label = stringResource(R.string.dashboard_crc),
                value = portfolio.totalCrc?.let { MoneyFormat.format(it) }
                    ?: stringResource(R.string.portfolio_no_price),
            )
            val pnl = portfolio.totalUnrealizedPnlUsd
            val pnlPercent = portfolio.totalUnrealizedPnlPercent
            if (pnl != null && pnlPercent != null) {
                KeyValueRow(
                    label = stringResource(
                        if (pnl.isNegative) R.string.dashboard_loss else R.string.dashboard_gain,
                    ),
                    value = "${MoneyFormat.format(pnl)} (${MoneyFormat.formatPercent(pnlPercent)})",
                    emphasize = false,
                )
            }
            if (portfolio.unpricedTickers.isNotEmpty()) {
                Text(
                    text = pluralStringResource(
                        R.plurals.dashboard_unpriced,
                        portfolio.unpricedTickers.size,
                        portfolio.unpricedTickers.size,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Text(
                text = snapshot.lastQuoteRefresh?.let {
                    stringResource(R.string.dashboard_updated, MoneyFormat.formatDateTime(it))
                } ?: stringResource(R.string.dashboard_not_updated),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isRefreshing) {
                Text(
                    text = stringResource(R.string.dashboard_refresh_running),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (portfolio.unpricedTickers.isNotEmpty()) {
                TextButton(onClick = onRefresh) {
                    Text(stringResource(R.string.action_refresh))
                }
            }
        }
    }
}

private enum class BalanceField { INCOME, SPENT, AVAILABLE }

/**
 * Corrects one period figure. The dialog shows the current value and explains what the correction
 * does; nothing is written to the transaction history.
 */
@Composable
private fun BalanceDialog(
    field: BalanceField,
    initial: Money,
    isAdjusted: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    val initialText = remember(field, initial.amountMinor) {
        BigDecimal.valueOf(initial.amountMinor, initial.currency.decimals)
            .stripTrailingZeros()
            .toPlainString()
    }
    var text by remember(field) { mutableStateOf(initialText) }
    val parsed = parseAmount(text)

    val titleRes = when (field) {
        BalanceField.INCOME -> R.string.balance_income_title
        BalanceField.SPENT -> R.string.balance_spent_title
        BalanceField.AVAILABLE -> R.string.balance_available_title
    }
    val helperRes = when (field) {
        BalanceField.INCOME -> R.string.balance_income_helper
        BalanceField.SPENT -> R.string.balance_spent_helper
        BalanceField.AVAILABLE -> R.string.balance_available_helper
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.balance_amount_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(helperRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text) },
                enabled = parsed != null && parsed.signum() >= 0,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                if (isAdjusted) {
                    TextButton(onClick = onClear) {
                        Text(stringResource(R.string.balance_clear))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}
