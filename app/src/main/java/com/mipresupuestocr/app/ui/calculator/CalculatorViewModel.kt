package com.mipresupuestocr.app.ui.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipresupuestocr.app.ui.util.parseAmount
import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.AppResult
import com.mipresupuestocr.domain.model.BudgetSplit
import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.FiftyThirtyTwentyResult
import com.mipresupuestocr.domain.model.IncomeFrequency
import com.mipresupuestocr.domain.model.Money
import com.mipresupuestocr.domain.repository.SettingsRepository
import com.mipresupuestocr.domain.usecase.FiftyThirtyTwentyCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CalculatorUiState(
    val incomeText: String = "",
    val frequency: IncomeFrequency = IncomeFrequency.MONTHLY,
    val needsText: String = "50",
    val wantsText: String = "30",
    val savingsText: String = "20",
    val split: BudgetSplit = BudgetSplit.FIFTY_THIRTY_TWENTY,
    val splitValid: Boolean = true,
    val result: FiftyThirtyTwentyResult? = null,
    val error: AppError? = null,
    val splitSaved: Boolean = false,
)

@HiltViewModel
class CalculatorViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private data class Form(
        val incomeText: String = "",
        val frequency: IncomeFrequency = IncomeFrequency.MONTHLY,
        val needs: String = "50",
        val wants: String = "30",
        val savings: String = "20",
    )

    private val calculator = FiftyThirtyTwentyCalculator()
    private val form = MutableStateFlow(Form())
    private val savedSplit = MutableStateFlow(BudgetSplit.FIFTY_THIRTY_TWENTY)
    private val splitSaved = MutableStateFlow(false)

    val uiState: StateFlow<CalculatorUiState> = combine(form, savedSplit, splitSaved) { current, defaults, wasSaved ->
        val split = BudgetSplit(
            needs = current.needs.toIntOrNull() ?: -1,
            wants = current.wants.toIntOrNull() ?: -1,
            savings = current.savings.toIntOrNull() ?: -1,
        )

        var result: FiftyThirtyTwentyResult? = null
        var error: AppError? = null
        if (current.incomeText.isNotBlank()) {
            val amount = parseAmount(current.incomeText)
            when {
                amount == null || amount.signum() <= 0 -> error = AppError.InvalidIncome
                !split.isValid -> error = AppError.InvalidSplit
                else -> when (
                    val calculated = calculator.calculate(
                        income = Money.of(amount, CurrencyCode.CRC),
                        frequency = current.frequency,
                        split = split,
                    )
                ) {
                    is AppResult.Success -> result = calculated.value
                    is AppResult.Failure -> error = calculated.error
                }
            }
        }

        CalculatorUiState(
            incomeText = current.incomeText,
            frequency = current.frequency,
            needsText = current.needs,
            wantsText = current.wants,
            savingsText = current.savings,
            split = split.takeIf { it.isValid } ?: defaults,
            splitValid = split.isValid,
            result = result,
            error = error,
            splitSaved = wasSaved,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalculatorUiState(),
    )

    init {
        viewModelScope.launch {
            val split = settingsRepository.current().split
            savedSplit.value = split
            form.update {
                it.copy(
                    needs = split.needs.toString(),
                    wants = split.wants.toString(),
                    savings = split.savings.toString(),
                )
            }
        }
    }

    fun setIncome(text: String) = form.update { it.copy(incomeText = text) }

    fun setFrequency(frequency: IncomeFrequency) = form.update { it.copy(frequency = frequency) }

    fun setNeedsPercent(text: String) = form.update { it.copy(needs = text.filter(Char::isDigit).take(3)) }

    fun setWantsPercent(text: String) = form.update { it.copy(wants = text.filter(Char::isDigit).take(3)) }

    fun setSavingsPercent(text: String) =
        form.update { it.copy(savings = text.filter(Char::isDigit).take(3)) }

    fun resetSplit() = form.update {
        it.copy(
            needs = BudgetSplit.FIFTY_THIRTY_TWENTY.needs.toString(),
            wants = BudgetSplit.FIFTY_THIRTY_TWENTY.wants.toString(),
            savings = BudgetSplit.FIFTY_THIRTY_TWENTY.savings.toString(),
        )
    }

    fun applySplit() {
        val split = BudgetSplit(
            needs = form.value.needs.toIntOrNull() ?: return,
            wants = form.value.wants.toIntOrNull() ?: return,
            savings = form.value.savings.toIntOrNull() ?: return,
        )
        if (!split.isValid) return

        viewModelScope.launch {
            settingsRepository.update { it.copy(split = split) }
            savedSplit.value = split
            splitSaved.value = true
        }
    }

    fun consumeSplitSaved() {
        splitSaved.value = false
    }
}
