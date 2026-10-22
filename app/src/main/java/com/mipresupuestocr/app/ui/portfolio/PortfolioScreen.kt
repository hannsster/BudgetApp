package com.mipresupuestocr.app.ui.portfolio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
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
import com.mipresupuestocr.app.ui.theme.positiveColor
import com.mipresupuestocr.app.ui.util.MoneyFormat
import com.mipresupuestocr.domain.model.Money
import com.mipresupuestocr.domain.model.PositionValuation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    onAddHolding: () -> Unit,
    onEditHolding: (Long) -> Unit,
    viewModel: PortfolioViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<PositionValuation?>(null) }

    LaunchedEffect(state.message) {
        val pending = state.message ?: return@LaunchedEffect
        val text = when (pending) {
            PortfolioMessage.Refreshed -> context.getString(R.string.dashboard_refresh_done)
            PortfolioMessage.RefreshedFromCache -> context.getString(R.string.dashboard_stale)
            is PortfolioMessage.MissingPrices -> context.getString(
                R.string.dashboard_refresh_partial,
                pending.tickers.joinToString(", "),
            )
        }
        snackbarHostState.showSnackbar(text)
        viewModel.consumeMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.portfolio_title)) },
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
                                Icons.Default.Refresh,
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
                onClick = onAddHolding,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.portfolio_add_holding)) },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }

        val valuation = state.valuation

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.marketKeyConfigured) {
                item {
                    SectionCard {
                        CardTitle(stringResource(R.string.portfolio_missing_key_title))
                        Text(
                            text = stringResource(R.string.portfolio_missing_key_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = state.providerName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            state.error?.let { error ->
                item { ErrorBanner(error = error, onRetry = viewModel::refresh) }
            }

            if (valuation != null) {
                item {
                    SectionCard {
                        CardTitle(stringResource(R.string.portfolio_total))
                        Text(
                            text = MoneyFormat.format(valuation.totalUsd),
                            style = MaterialTheme.typography.displaySmall,
                        )
                        Text(
                            text = valuation.totalCrc?.let { MoneyFormat.format(it) }
                                ?: stringResource(R.string.portfolio_no_price),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val pnl = valuation.totalUnrealizedPnlUsd
                        if (pnl != null) {
                            KeyValueRow(
                                label = stringResource(
                                    if (pnl.isNegative) R.string.dashboard_loss else R.string.dashboard_gain,
                                ),
                                value = buildString {
                                    append(MoneyFormat.format(pnl))
                                    valuation.totalUnrealizedPnlPercent?.let {
                                        append(" (").append(MoneyFormat.formatPercent(it)).append(")")
                                    }
                                },
                                emphasize = false,
                            )
                        }
                        state.rate?.let { rate ->
                            KeyValueRow(
                                label = stringResource(R.string.dashboard_fx_title),
                                value = MoneyFormat.formatRate(rate.rate) +
                                    if (rate.isManual) " · " +
                                        stringResource(R.string.dashboard_manual_badge) else "",
                            )
                        }
                    }
                }

                if (valuation.isEmpty) {
                    item {
                        EmptyState(
                            title = stringResource(R.string.portfolio_empty_title),
                            body = stringResource(R.string.portfolio_empty_body),
                            actionLabel = stringResource(R.string.portfolio_add_holding),
                            onAction = onAddHolding,
                        )
                    }
                } else {
                    items(valuation.positions.size) { index ->
                        val position = valuation.positions[index]
                        PositionCard(
                            position = position,
                            onEdit = { onEditHolding(position.holdingId) },
                            onDelete = { pendingDelete = position },
                        )
                    }

                    if (valuation.unpricedTickers.isNotEmpty()) {
                        item {
                            SectionCard {
                                Text(
                                    text = stringResource(
                                        R.string.portfolio_unpriced_banner,
                                        valuation.unpricedTickers.joinToString(", "),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                                TextButton(onClick = viewModel::refresh) {
                                    Text(stringResource(R.string.action_refresh))
                                }
                            }
                        }
                    }
                }

                val lastUpdate = valuation.positions.mapNotNull { it.asOf }.maxOrNull()
                if (lastUpdate != null) {
                    item {
                        Text(
                            text = stringResource(
                                R.string.portfolio_updated,
                                MoneyFormat.formatDateTime(lastUpdate),
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { position ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.portfolio_delete_title)) },
            text = { Text(stringResource(R.string.portfolio_delete_body, position.ticker)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteHolding(position.holdingId)
                        pendingDelete = null
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun PositionCard(
    position: PositionValuation,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    SectionCard(modifier = Modifier.clickable(onClick = onEdit)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(text = position.ticker, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(
                        R.string.portfolio_units_price,
                        MoneyFormat.formatShares(position.shares),
                        MoneyFormat.formatPrice(position.priceUsd),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusChip(MoneyFormat.formatPercent(position.allocationPercent))
        }
        KeyValueRow(
            label = stringResource(R.string.portfolio_value_usd),
            value = MoneyFormat.format(position.marketValueUsd),
            emphasize = true,
        )
        KeyValueRow(
            label = stringResource(R.string.portfolio_value_crc),
            value = position.marketValueCrc?.let { MoneyFormat.format(it) }
                ?: stringResource(R.string.portfolio_no_price),
        )
        val pnl: Money? = position.unrealizedPnlUsd
        if (pnl != null) {
            KeyValueRow(
                label = stringResource(
                    if (pnl.isNegative) R.string.dashboard_loss else R.string.dashboard_gain,
                ),
                value = MoneyFormat.format(pnl) + position.unrealizedPnlPercent
                    ?.let { " (" + MoneyFormat.formatPercent(it) + ")" }.orEmpty(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = position.asOf?.let {
                    stringResource(
                        R.string.portfolio_source,
                        position.source ?: "",
                    ) + " · " + MoneyFormat.formatDateTime(it)
                } ?: stringResource(R.string.portfolio_no_price),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
