package com.mipresupuestocr.app.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipresupuestocr.app.R
import com.mipresupuestocr.app.ui.components.CardTitle
import com.mipresupuestocr.app.ui.components.EmptyState
import com.mipresupuestocr.app.ui.components.ErrorBanner
import com.mipresupuestocr.app.ui.components.LoadingBox
import com.mipresupuestocr.app.ui.components.SectionCard
import com.mipresupuestocr.app.ui.util.MoneyFormat
import com.mipresupuestocr.app.ui.util.accountTypeLabel
import com.mipresupuestocr.app.ui.util.parseAmount
import com.mipresupuestocr.domain.model.Account
import com.mipresupuestocr.domain.model.AccountBalance
import com.mipresupuestocr.domain.model.AccountTransfer
import com.mipresupuestocr.domain.model.AccountType
import com.mipresupuestocr.domain.model.Money
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AccountBalance?>(null) }
    var transferring by remember { mutableStateOf(false) }
    var deletingTransfer by remember { mutableStateOf<AccountTransfer?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        val pending = state.message ?: return@LaunchedEffect
        val text = when (pending) {
            AccountsMessage.TransferSaved -> context.getString(R.string.transfer_saved)
            AccountsMessage.TransferDeleted -> context.getString(R.string.transfer_deleted)
        }
        snackbarHostState.showSnackbar(text)
        viewModel.consumeMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.accounts_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.accounts_add)) },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { error ->
                item { ErrorBanner(error = error, onRetry = viewModel::dismissError) }
            }

            if (state.balances.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.accounts_empty_title),
                        body = stringResource(R.string.accounts_empty_body),
                        actionLabel = stringResource(R.string.accounts_add),
                        onAction = { creating = true },
                    )
                }
            } else {
                item {
                    SectionCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CardTitle(stringResource(R.string.accounts_total))
                            if (state.balances.size >= 2) {
                                FilledTonalButton(onClick = { transferring = true }) {
                                    Icon(
                                        imageVector = Icons.Outlined.SwapHoriz,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.transfer_action))
                                }
                            }
                        }
                        Text(
                            text = MoneyFormat.format(state.total),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = pluralStringResource(
                                R.plurals.accounts_summary,
                                state.balances.size,
                                state.balances.size,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                items(state.balances, key = { it.account.id }) { row ->
                    SectionCard(modifier = Modifier.clickable { editing = row }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = row.account.name,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = accountTypeLabel(row.account.type),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = MoneyFormat.format(row.balance),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (row.balance.isNegative) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                        Text(
                            text = pluralStringResource(
                                R.plurals.accounts_entries,
                                row.entryCount,
                                row.entryCount,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (state.transfers.isNotEmpty()) {
                    item {
                        TransfersCard(
                            transfers = state.transfers.take(MAX_VISIBLE_TRANSFERS),
                            accountNames = state.balances.associate { it.account.id to it.account.name },
                            onDelete = { deletingTransfer = it },
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        AccountDialog(
            account = null,
            onDismiss = { creating = false },
            onSave = { name, type, opening ->
                viewModel.save(0L, name, type, opening)
                creating = false
            },
            onDelete = null,
        )
    }

    editing?.let { row ->
        AccountDialog(
            account = row.account,
            onDismiss = { editing = null },
            onSave = { name, type, opening ->
                viewModel.save(row.account.id, name, type, opening)
                editing = null
            },
            onDelete = {
                viewModel.delete(row.account.id)
                editing = null
            },
        )
    }

    if (transferring) {
        TransferDialog(
            accounts = state.balances.map { it.account },
            onDismiss = { transferring = false },
            onConfirm = { fromId, toId, amount ->
                viewModel.transfer(fromId, toId, amount)
                transferring = false
            },
        )
    }

    deletingTransfer?.let { transfer ->
        AlertDialog(
            onDismissRequest = { deletingTransfer = null },
            title = { Text(stringResource(R.string.transfer_delete_title)) },
            text = { Text(stringResource(R.string.transfer_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTransfer(transfer.id)
                        deletingTransfer = null
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingTransfer = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** How many transfers the accounts screen lists; the balances above them already sum them all. */
private const val MAX_VISIBLE_TRANSFERS = 5

/** Recent transfers, so a mistyped one can be found and removed instead of being invisible. */
@Composable
private fun TransfersCard(
    transfers: List<AccountTransfer>,
    accountNames: Map<Long, String>,
    onDelete: (AccountTransfer) -> Unit,
) {
    SectionCard {
        CardTitle(stringResource(R.string.transfers_title))
        transfers.forEach { transfer ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            R.string.transfer_between,
                            accountNames[transfer.fromAccountId] ?: "—",
                            accountNames[transfer.toAccountId] ?: "—",
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = MoneyFormat.formatDate(transfer.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = MoneyFormat.format(transfer.amount),
                    style = MaterialTheme.typography.bodyLarge,
                )
                IconButton(onClick = { onDelete(transfer) }) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.cd_delete_transfer),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Moves money between two accounts. The amount defaults to blank and the two sides cannot be the
 * same account, so the common mistake is caught before anything is written.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransferDialog(
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onConfirm: (fromId: Long, toId: Long, amount: String) -> Unit,
) {
    var fromId by remember { mutableStateOf(accounts.first().id) }
    var toId by remember { mutableStateOf(accounts.getOrNull(1)?.id ?: accounts.first().id) }
    var amount by remember { mutableStateOf("") }

    val parsed = parseAmount(amount)
    val sameAccount = fromId == toId
    val canSave = !sameAccount && parsed != null && parsed.signum() > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.transfer_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.transfer_from),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { account ->
                        FilterChip(
                            selected = fromId == account.id,
                            onClick = { fromId = account.id },
                            label = { Text(account.name) },
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.transfer_to),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { account ->
                        FilterChip(
                            selected = toId == account.id,
                            onClick = { toId = account.id },
                            label = { Text(account.name) },
                        )
                    }
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.transfer_amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = if (sameAccount) {
                        stringResource(R.string.transfer_same_account)
                    } else {
                        stringResource(R.string.transfer_amount_helper)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (sameAccount) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(fromId, toId, amount) },
                enabled = canSave,
            ) { Text(stringResource(R.string.transfer_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountDialog(
    account: Account?,
    onDismiss: () -> Unit,
    onSave: (name: String, type: AccountType, openingBalance: String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember(account) { mutableStateOf(account?.name.orEmpty()) }
    var type by remember(account) { mutableStateOf(account?.type ?: AccountType.CHECKING) }
    var opening by remember(account) {
        mutableStateOf(account?.openingBalance?.let(::plainAmount).orEmpty())
    }
    var confirmingDelete by remember { mutableStateOf(false) }

    val openingValue = if (opening.isBlank()) BigDecimal.ZERO else parseAmount(opening)
    val canSave = name.isNotBlank() && openingValue != null && openingValue.signum() >= 0

    if (confirmingDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.accounts_delete_title)) },
            text = { Text(stringResource(R.string.accounts_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        onDelete()
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (account == null) R.string.accounts_new_title else R.string.accounts_edit_title,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.accounts_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = stringResource(R.string.accounts_type),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccountType.entries.forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            label = { Text(accountTypeLabel(option)) },
                        )
                    }
                }

                OutlinedTextField(
                    value = opening,
                    onValueChange = { opening = it },
                    label = { Text(stringResource(R.string.accounts_opening_balance)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.accounts_opening_helper),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), type, opening) },
                enabled = canSave,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(
                        onClick = { confirmingDelete = true },
                    ) {
                        Text(
                            text = stringResource(R.string.action_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}

private fun plainAmount(money: Money): String =
    BigDecimal.valueOf(money.amountMinor, money.currency.decimals)
        .stripTrailingZeros()
        .toPlainString()
