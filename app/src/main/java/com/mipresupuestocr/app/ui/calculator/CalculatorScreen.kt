package com.mipresupuestocr.app.ui.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipresupuestocr.app.R
import com.mipresupuestocr.app.ui.components.CardTitle
import com.mipresupuestocr.app.ui.components.KeyValueRow
import com.mipresupuestocr.app.ui.components.SectionCard
import com.mipresupuestocr.app.ui.util.MoneyFormat
import com.mipresupuestocr.domain.model.IncomeFrequency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val appliedText = stringResource(R.string.calculator_split_applied)

    LaunchedEffect(state.splitSaved) {
        if (state.splitSaved) {
            snackbarHostState.showSnackbar(appliedText)
            viewModel.consumeSplitSaved()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.calculator_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard {
                    OutlinedTextField(
                        value = state.incomeText,
                        onValueChange = viewModel::setIncome,
                        label = { Text(stringResource(R.string.calculator_income)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.calculator_frequency),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    FrequencySelector(
                        selected = state.frequency,
                        onSelect = viewModel::setFrequency,
                    )
                }
            }

            item {
                SectionCard {
                    Text(
                        text = stringResource(R.string.calculator_result),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val result = state.result
                    if (result == null) {
                        Text(
                            text = state.error?.let { error ->
                                when (error) {
                                    com.mipresupuestocr.domain.model.AppError.InvalidSplit ->
                                        stringResource(R.string.calculator_invalid_split)

                                    com.mipresupuestocr.domain.model.AppError.InvalidIncome ->
                                        stringResource(R.string.error_invalid_income)

                                    else -> stringResource(
                                        com.mipresupuestocr.app.ui.components.errorRes(error),
                                    )
                                }
                            } ?: stringResource(R.string.calculator_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.error != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    } else {
                        KeyValueRow(
                            label = "${stringResource(R.string.calculator_needs)} (${result.split.needs}%)",
                            value = MoneyFormat.format(result.allocationPerEvent.needs),
                        )
                        KeyValueRow(
                            label = "${stringResource(R.string.calculator_wants)} (${result.split.wants}%)",
                            value = MoneyFormat.format(result.allocationPerEvent.wants),
                        )
                        KeyValueRow(
                            label = "${stringResource(R.string.calculator_savings)} (${result.split.savings}%)",
                            value = MoneyFormat.format(result.allocationPerEvent.savings),
                        )
                        HorizontalDivider()
                        KeyValueRow(
                            label = stringResource(R.string.calculator_total),
                            value = MoneyFormat.format(result.allocationPerEvent.total),
                            emphasize = true,
                        )
                        Text(
                            text = stringResource(
                                R.string.calculator_monthly_value,
                                MoneyFormat.format(result.monthlyIncome),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.calculator_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                SectionCard {
                    CardTitle(stringResource(R.string.calculator_custom_split))
                    Text(
                        text = stringResource(R.string.calculator_split_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PercentField(
                            value = state.needsText,
                            onValueChange = viewModel::setNeedsPercent,
                            label = stringResource(R.string.calculator_split_needs),
                            modifier = Modifier.weight(1f),
                        )
                        PercentField(
                            value = state.wantsText,
                            onValueChange = viewModel::setWantsPercent,
                            label = stringResource(R.string.calculator_split_wants),
                            modifier = Modifier.weight(1f),
                        )
                        PercentField(
                            value = state.savingsText,
                            onValueChange = viewModel::setSavingsPercent,
                            label = stringResource(R.string.calculator_split_savings),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = viewModel::resetSplit) {
                            Text(stringResource(R.string.calculator_reset_split))
                        }
                        TextButton(
                            onClick = viewModel::applySplit,
                            enabled = state.splitValid,
                        ) {
                            Text(stringResource(R.string.calculator_apply_split))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FrequencySelector(
    selected: IncomeFrequency,
    onSelect: (IncomeFrequency) -> Unit,
) {
    val options = listOf(
        IncomeFrequency.MONTHLY to R.string.calculator_monthly,
        IncomeFrequency.BIWEEKLY to R.string.calculator_biweekly,
        IncomeFrequency.WEEKLY to R.string.calculator_weekly,
    )

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (frequency, labelRes) ->
            SegmentedButton(
                selected = selected == frequency,
                onClick = { onSelect(frequency) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(stringResource(labelRes))
            }
        }
    }
}

@Composable
private fun PercentField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
