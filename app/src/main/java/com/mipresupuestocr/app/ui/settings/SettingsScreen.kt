package com.mipresupuestocr.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipresupuestocr.app.R
import com.mipresupuestocr.app.ui.components.CardTitle
import com.mipresupuestocr.app.ui.components.ErrorBanner
import com.mipresupuestocr.app.ui.components.KeyValueRow
import com.mipresupuestocr.app.ui.components.SectionCard
import com.mipresupuestocr.app.ui.components.StatusChip
import com.mipresupuestocr.app.ui.update.InstallOutcome
import com.mipresupuestocr.app.ui.update.UpdatePhase
import com.mipresupuestocr.app.ui.update.UpdateUiState
import com.mipresupuestocr.app.ui.update.UpdateViewModel
import com.mipresupuestocr.app.ui.update.installApk
import com.mipresupuestocr.app.ui.update.openUrl
import com.mipresupuestocr.app.ui.util.MoneyFormat
import com.mipresupuestocr.domain.model.ExchangeRate
import com.mipresupuestocr.domain.model.PeriodType
import com.mipresupuestocr.domain.model.ThemeMode
import com.mipresupuestocr.domain.model.UserSettings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { updateViewModel.check() }
    var showDatePicker by remember { mutableStateOf(false) }
    var manualRateText by remember { mutableStateOf("") }
    var marketKeyText by remember { mutableStateOf("") }

    LaunchedEffect(state.message) {
        val pending = state.message ?: return@LaunchedEffect
        val text = when (pending) {
            SettingsMessage.RateRefreshed -> context.getString(R.string.settings_rate_refresh_done)
            SettingsMessage.RateFromCache -> context.getString(R.string.settings_rate_cache_notice)
            SettingsMessage.ManualRateSaved -> context.getString(R.string.settings_manual_saved)
            SettingsMessage.ManualRateCleared -> context.getString(R.string.settings_manual_cleared)
            SettingsMessage.QuotesRefreshed -> context.getString(R.string.settings_quotes_refresh_done)
            SettingsMessage.MarketKeySaved -> context.getString(R.string.settings_market_key_saved)
            SettingsMessage.MarketKeyCleared -> context.getString(R.string.settings_market_key_cleared)
        }
        snackbarHostState.showSnackbar(text)
        viewModel.consumeMessage()
    }

    LaunchedEffect(state.rate?.isManual, state.rate?.rate) {
        val manual = state.rate?.takeIf { it.isManual }
        if (manual != null && manualRateText.isBlank()) {
            manualRateText = manual.rate.stripTrailingZeros().toPlainString()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { error ->
                item { ErrorBanner(error = error, onRetry = viewModel::dismissError) }
            }

            item {
                PeriodSection(
                    settings = state.settings,
                    onSelectPeriod = { type ->
                        viewModel.setPeriodType(type)
                        if (type == PeriodType.BIWEEKLY && state.settings.paydayAnchor == null) {
                            showDatePicker = true
                        }
                    },
                    onPickDate = { showDatePicker = true },
                )
            }

            item {
                RateSection(
                    rate = state.rate,
                    isBusy = state.isBusy,
                    manualRateText = manualRateText,
                    onManualRateChange = { manualRateText = it },
                    onSaveManual = { viewModel.saveManualRate(manualRateText) },
                    onClearManual = {
                        manualRateText = ""
                        viewModel.removeManualRate()
                    },
                    onRefreshRate = viewModel::refreshRates,
                )
            }

            item {
                MarketDataSection(
                    provider = state.marketProvider,
                    providerUrl = state.marketProviderUrl,
                    isConfigured = state.marketKeyConfigured,
                    isBusy = state.isBusy,
                    keyText = marketKeyText,
                    onKeyChange = { marketKeyText = it },
                    onSaveKey = {
                        viewModel.saveMarketDataKey(marketKeyText)
                        marketKeyText = ""
                    },
                    onClearKey = {
                        marketKeyText = ""
                        viewModel.clearMarketDataKey()
                    },
                    onRefreshQuotes = viewModel::refreshQuotes,
                )
            }

            item {
                SectionCard {
                    CardTitle(stringResource(R.string.settings_theme_title))
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setTheme(mode) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = state.settings.themeMode == mode,
                                onClick = { viewModel.setTheme(mode) },
                            )
                            Text(
                                text = stringResource(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> R.string.settings_theme_system
                                        ThemeMode.LIGHT -> R.string.settings_theme_light
                                        ThemeMode.DARK -> R.string.settings_theme_dark
                                    },
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }

            item {
                SectionCard {
                    CardTitle(stringResource(R.string.settings_data_title))
                    Text(
                        text = stringResource(R.string.settings_data_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                UpdateSection(
                    state = updateState,
                    onCheck = updateViewModel::check,
                    onDownload = updateViewModel::download,
                    onDismissError = updateViewModel::dismissError,
                    onOpenRelease = { url -> openUrl(context, url) },
                    onInstall = { path ->
                        when (installApk(context, path)) {
                            InstallOutcome.LAUNCHED -> Unit
                            InstallOutcome.NEEDS_PERMISSION -> scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.settings_updates_permission),
                                )
                            }

                            InstallOutcome.MISSING_FILE -> scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.settings_updates_missing),
                                )
                            }

                            InstallOutcome.UNAVAILABLE -> scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.settings_updates_unavailable),
                                )
                            }
                        }
                    },
                )
            }

            item {
                SectionCard {
                    CardTitle(stringResource(R.string.settings_about_title))
                    Text(
                        text = stringResource(R.string.settings_version, state.appVersion),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_disclaimer_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(R.string.settings_disclaimer_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = state.settings.paydayAnchor
            ?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant()
            ?.toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            viewModel.setPayday(
                                Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(),
                            )
                        }
                        showDatePicker = false
                    },
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun MarketDataSection(
    provider: String,
    providerUrl: String,
    isConfigured: Boolean,
    isBusy: Boolean,
    keyText: String,
    onKeyChange: (String) -> Unit,
    onSaveKey: () -> Unit,
    onClearKey: () -> Unit,
    onRefreshQuotes: () -> Unit,
) {
    SectionCard {
        CardTitle(stringResource(R.string.settings_market_title))
        KeyValueRow(
            label = stringResource(R.string.dashboard_source, provider),
            value = if (isConfigured) {
                stringResource(R.string.settings_market_key_ok)
            } else {
                stringResource(R.string.settings_market_key_missing)
            },
        )
        Text(
            text = stringResource(R.string.settings_market_key_help, provider, providerUrl),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = keyText,
            onValueChange = onKeyChange,
            label = { Text(stringResource(R.string.settings_market_key_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.settings_market_key_privacy),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSaveKey, enabled = !isBusy && keyText.isNotBlank()) {
                Text(stringResource(R.string.settings_market_key_save))
            }
            if (isConfigured) {
                TextButton(onClick = onClearKey, enabled = !isBusy) {
                    Text(stringResource(R.string.settings_market_key_remove))
                }
            }
        }
        TextButton(onClick = onRefreshQuotes, enabled = !isBusy && isConfigured) {
            Text(stringResource(R.string.settings_prepare_quotes))
        }
        Text(
            text = stringResource(R.string.settings_background_refresh),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PeriodSection(
    settings: UserSettings,
    onSelectPeriod: (PeriodType) -> Unit,
    onPickDate: () -> Unit,
) {
    SectionCard {
        CardTitle(stringResource(R.string.settings_period_title))
        PeriodRow(
            label = stringResource(R.string.settings_monthly),
            selected = settings.periodType == PeriodType.MONTHLY,
            onClick = { onSelectPeriod(PeriodType.MONTHLY) },
        )
        PeriodRow(
            label = stringResource(R.string.settings_biweekly),
            selected = settings.periodType == PeriodType.BIWEEKLY,
            onClick = { onSelectPeriod(PeriodType.BIWEEKLY) },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPickDate)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.settings_payday),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.settings_payday_help),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = settings.paydayAnchor?.let { MoneyFormat.formatDate(it) }
                    ?: stringResource(R.string.settings_payday_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PeriodRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun RateSection(
    rate: ExchangeRate?,
    isBusy: Boolean,
    manualRateText: String,
    onManualRateChange: (String) -> Unit,
    onSaveManual: () -> Unit,
    onClearManual: () -> Unit,
    onRefreshRate: () -> Unit,
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardTitle(stringResource(R.string.settings_rate_title))
            if (rate?.isManual == true) {
                StatusChip(stringResource(R.string.dashboard_manual_badge))
            }
        }

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
                text = stringResource(R.string.dashboard_source, rate.source) + " · " +
                    MoneyFormat.formatDateTime(rate.asOf),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TextButton(onClick = onRefreshRate, enabled = !isBusy) {
            Text(stringResource(R.string.settings_prepare_rate))
        }

        Text(
            text = stringResource(R.string.settings_rate_manual_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.settings_rate_manual_help),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = manualRateText,
            onValueChange = onManualRateChange,
            label = { Text(stringResource(R.string.settings_rate_manual_value)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSaveManual, enabled = !isBusy) {
                Text(stringResource(R.string.action_save_manual_rate))
            }
            if (rate?.isManual == true) {
                TextButton(onClick = onClearManual, enabled = !isBusy) {
                    Text(stringResource(R.string.action_clear_manual_rate))
                }
            }
        }
        Text(
            text = stringResource(R.string.settings_background_refresh),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UpdateSection(
    state: UpdateUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onDismissError: () -> Unit,
    onOpenRelease: (String) -> Unit,
    onInstall: (String) -> Unit,
) {
    SectionCard {
        CardTitle(stringResource(R.string.settings_updates_title))
        KeyValueRow(
            label = stringResource(R.string.settings_updates_current),
            value = state.currentVersion,
        )

        state.error?.let { error -> ErrorBanner(error = error, onRetry = onDismissError) }

        when (state.phase) {
            UpdatePhase.IDLE -> Text(
                text = stringResource(R.string.settings_updates_idle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            UpdatePhase.CHECKING -> Text(
                text = stringResource(R.string.settings_updates_checking),
                style = MaterialTheme.typography.bodyMedium,
            )

            UpdatePhase.UP_TO_DATE -> Text(
                text = stringResource(R.string.settings_updates_up_to_date),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            UpdatePhase.AVAILABLE -> {
                Text(
                    text = stringResource(
                        R.string.settings_updates_available,
                        state.update?.version.orEmpty(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onDownload) {
                        Text(stringResource(R.string.settings_updates_download))
                    }
                    val releaseUrl = state.update?.releaseUrl.orEmpty()
                    if (releaseUrl.isNotBlank()) {
                        TextButton(onClick = { onOpenRelease(releaseUrl) }) {
                            Text(stringResource(R.string.settings_updates_notes))
                        }
                    }
                }
            }

            UpdatePhase.DOWNLOADING -> {
                Text(
                    text = stringResource(
                        R.string.settings_updates_downloading,
                        formatMegabytes(state.bytesRead),
                        formatMegabytes(state.totalBytes),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            UpdatePhase.READY -> {
                Text(
                    text = stringResource(R.string.settings_updates_ready),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Button(onClick = { onInstall(state.apkPath.orEmpty()) }) {
                    Text(stringResource(R.string.settings_updates_install))
                }
            }
        }

        TextButton(
            onClick = onCheck,
            enabled = state.phase != UpdatePhase.CHECKING && state.phase != UpdatePhase.DOWNLOADING,
        ) {
            Text(stringResource(R.string.settings_updates_check))
        }

        Text(
            text = stringResource(R.string.settings_updates_signature),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatMegabytes(bytes: Long): String =
    String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
