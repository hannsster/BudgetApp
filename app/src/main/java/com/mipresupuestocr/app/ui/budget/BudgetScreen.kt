package com.mipresupuestocr.app.ui.budget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.mipresupuestocr.app.ui.components.TargetProgress
import com.mipresupuestocr.app.ui.util.MoneyFormat
import com.mipresupuestocr.app.ui.util.parseAmount
import com.mipresupuestocr.domain.model.CategoryBucket
import com.mipresupuestocr.domain.model.CategoryProgress
import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.Money
import com.mipresupuestocr.domain.model.Transaction
import com.mipresupuestocr.domain.model.TransactionType
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    onAddTransaction: () -> Unit,
    onEditTransaction: (Long) -> Unit,
    viewModel: BudgetViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editingCategory by remember { mutableStateOf<CategoryProgress?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.budget_title)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddTransaction,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.budget_add_transaction)) },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }

        val summary = state.summary
        val categoryNames = state.categories.associate { it.id to it.name }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = viewModel::previousPeriod) {
                        Icon(
                            Icons.Outlined.ChevronLeft,
                            contentDescription = stringResource(R.string.cd_previous_period),
                        )
                    }
                    Text(
                        text = state.period?.let {
                            stringResource(
                                R.string.dashboard_period,
                                MoneyFormat.formatDate(it.start),
                                MoneyFormat.formatDate(it.endInclusive),
                            )
                        }.orEmpty(),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(onClick = viewModel::nextPeriod) {
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = stringResource(R.string.cd_next_period),
                        )
                    }
                }
            }

            state.error?.let { error ->
                item { ErrorBanner(error = error, onRetry = viewModel::dismissError) }
            }

            if (summary != null) {
                item {
                    SectionCard {
                        KeyValueRow(
                            label = stringResource(R.string.dashboard_income),
                            value = MoneyFormat.format(summary.income),
                            emphasize = true,
                        )
                        KeyValueRow(
                            label = stringResource(R.string.dashboard_spent),
                            value = MoneyFormat.format(summary.spent),
                        )
                        KeyValueRow(
                            label = stringResource(R.string.dashboard_remaining),
                            value = MoneyFormat.format(summary.remaining),
                            emphasize = true,
                        )
                        if (summary.plannedExpenses.isPositive) {
                            KeyValueRow(
                                label = stringResource(R.string.budget_planned_label),
                                value = MoneyFormat.format(summary.plannedExpenses),
                            )
                        }
                        if (summary.uncategorizedSpent.isPositive) {
                            Text(
                                text = stringResource(
                                    R.string.budget_uncategorized,
                                    MoneyFormat.format(summary.uncategorizedSpent),
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item {
                    SectionCard {
                        CardTitle(stringResource(R.string.dashboard_categories_title))
                        val progressRows = summary.categoryProgress.filter {
                            it.category.bucket != CategoryBucket.INCOME
                        }
                        if (progressRows.isEmpty()) {
                            Text(
                                text = stringResource(R.string.budget_empty_categories),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        progressRows.forEach { progress ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editingCategory = progress }
                                    .padding(vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (progress.target != null) {
                                    TargetProgress(
                                        label = progress.category.name,
                                        supporting = stringResource(
                                            R.string.budget_target_progress,
                                            MoneyFormat.format(progress.spent),
                                            MoneyFormat.format(progress.target!!),
                                        ),
                                        fraction = progress.progressFraction,
                                    )
                                    val remaining = progress.remaining
                                    if (remaining != null && remaining.isNegative) {
                                        Text(
                                            text = stringResource(
                                                R.string.budget_over,
                                                MoneyFormat.format(remaining.abs()),
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = progress.category.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Text(
                                            text = MoneyFormat.format(progress.spent),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                    Text(
                                        text = stringResource(R.string.budget_no_target) +
                                            " · " + stringResource(R.string.action_set_target),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (progress.isAdjusted) {
                                    Text(
                                        text = stringResource(R.string.balance_adjusted_chip),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }

                item { Text(stringResource(R.string.budget_income_title), style = MaterialTheme.typography.titleMedium) }

                val incomeRows = state.transactions.filter { it.type == TransactionType.INCOME }
                if (incomeRows.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.budget_no_transactions),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(incomeRows.size) { index ->
                        TransactionRow(
                            transaction = incomeRows[index],
                            categoryName = categoryNames[incomeRows[index].categoryId],
                            onClick = { onEditTransaction(incomeRows[index].id) },
                        )
                    }
                }

                item { Text(stringResource(R.string.budget_expenses_title), style = MaterialTheme.typography.titleMedium) }

                val expenseRows = state.transactions.filter { it.type == TransactionType.EXPENSE }
                if (expenseRows.isEmpty()) {
                    item {
                        EmptyState(
                            title = stringResource(R.string.budget_no_transactions),
                            body = stringResource(R.string.budget_add_expense),
                            actionLabel = stringResource(R.string.budget_add_expense),
                            onAction = onAddTransaction,
                        )
                    }
                } else {
                    items(expenseRows.size) { index ->
                        TransactionRow(
                            transaction = expenseRows[index],
                            categoryName = categoryNames[expenseRows[index].categoryId],
                            onClick = { onEditTransaction(expenseRows[index].id) },
                        )
                    }
                }
            }
        }
    }

    editingCategory?.let { progress ->
        CategoryDialog(
            progress = progress,
            onDismiss = { editingCategory = null },
            onSave = { target, spent ->
                viewModel.setTarget(progress.category, target)
                if (spent != null) viewModel.setCategoryBalance(progress.category.id, spent)
                editingCategory = null
            },
            onClearCorrection = {
                viewModel.setCategoryBalance(progress.category.id, null)
                editingCategory = null
            },
        )
    }
}

@Composable
private fun TransactionRow(
    transaction: Transaction,
    categoryName: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.note.ifBlank {
                    categoryName ?: stringResource(R.string.transaction_no_category)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = MoneyFormat.formatDate(transaction.date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = MoneyFormat.format(transaction.amount),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (transaction.type == TransactionType.INCOME) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
    HorizontalDivider()
}

@Composable
private fun CategoryDialog(
    progress: CategoryProgress,
    onDismiss: () -> Unit,
    onSave: (Money?, Money?) -> Unit,
    onClearCorrection: () -> Unit,
) {
    val category = progress.category
    var targetText by remember {
        mutableStateOf(category.budgetTarget?.let(::plainAmount).orEmpty())
    }
    var spentText by remember { mutableStateOf(plainAmount(progress.spent)) }

    val targetValue = targetText.takeIf { it.isNotBlank() }?.let(::parseAmount)
    val spentValue = parseAmount(spentText)
    val canSave = (targetText.isBlank() || targetValue != null) &&
        spentValue != null &&
        spentValue.signum() >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.budget_set_target_title, category.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it },
                    label = { Text(stringResource(R.string.budget_target_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.budget_target_dialog_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = spentText,
                    onValueChange = { spentText = it },
                    label = { Text(stringResource(R.string.budget_balance_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.budget_balance_helper),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (progress.isAdjusted) {
                    Text(
                        text = stringResource(R.string.balance_adjusted_notice),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val target = targetValue?.takeIf { it.signum() > 0 }
                        ?.let { Money.of(it, CurrencyCode.CRC) }
                    val spent = spentValue?.let { Money.of(it, CurrencyCode.CRC) }
                    onSave(target, spent)
                },
                enabled = canSave,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                if (progress.isAdjusted) {
                    TextButton(onClick = onClearCorrection) {
                        Text(stringResource(R.string.balance_clear))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

private fun plainAmount(money: Money): String =
    BigDecimal.valueOf(money.amountMinor, money.currency.decimals)
        .stripTrailingZeros()
        .toPlainString()
