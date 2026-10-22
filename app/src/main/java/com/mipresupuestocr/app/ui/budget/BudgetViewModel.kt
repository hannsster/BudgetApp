package com.mipresupuestocr.app.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.BudgetPeriod
import com.mipresupuestocr.domain.model.BudgetSummary
import com.mipresupuestocr.domain.model.Category
import com.mipresupuestocr.domain.model.Money
import com.mipresupuestocr.domain.model.Transaction
import com.mipresupuestocr.domain.repository.CategoryRepository
import com.mipresupuestocr.domain.repository.SettingsRepository
import com.mipresupuestocr.domain.repository.TransactionRepository
import com.mipresupuestocr.domain.usecase.BudgetPeriodResolver
import com.mipresupuestocr.domain.usecase.BudgetProgressCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class BudgetUiState(
    val isLoading: Boolean = true,
    val period: BudgetPeriod? = null,
    val summary: BudgetSummary? = null,
    val transactions: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val error: AppError? = null,
    val isSaving: Boolean = false,
)

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
    private val periodResolver: BudgetPeriodResolver,
    private val progressCalculator: BudgetProgressCalculator,
    private val clock: Clock,
) : ViewModel() {

    private data class Resolved(
        val period: BudgetPeriod?,
        val summary: BudgetSummary?,
        val transactions: List<Transaction>,
        val categories: List<Category>,
        val error: AppError?,
    )

    private val selectedPeriod = MutableStateFlow<BudgetPeriod?>(null)
    private val error = MutableStateFlow<AppError?>(null)
    private val isSaving = MutableStateFlow(false)

    private val resolved = combine(
        settingsRepository.observeSettings(),
        transactionRepository.observeAll(),
        categoryRepository.observeAll(),
        selectedPeriod,
    ) { settings, transactions, categories, period ->
        val effectivePeriod = period
            ?.takeIf { it.type == settings.periodType }
            ?: periodResolver.resolve(settings, LocalDate.now(clock)).getOrNull()

        if (effectivePeriod == null) {
            Resolved(null, null, emptyList(), categories, AppError.MissingPaydayAnchor)
        } else {
            val inPeriod = transactions
                .filter { effectivePeriod.contains(it.date) }
                .sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.id })
            Resolved(
                period = effectivePeriod,
                summary = progressCalculator.calculate(effectivePeriod, transactions, categories),
                transactions = inPeriod,
                categories = categories,
                error = null,
            )
        }
    }

    val uiState: StateFlow<BudgetUiState> = combine(resolved, error, isSaving) { state, currentError, saving ->
        BudgetUiState(
            isLoading = false,
            period = state.period,
            summary = state.summary,
            transactions = state.transactions,
            categories = state.categories,
            error = currentError ?: state.error,
            isSaving = saving,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BudgetUiState(),
    )

    fun previousPeriod() = shiftPeriod { periodResolver.previous(it) }

    fun nextPeriod() = shiftPeriod { periodResolver.next(it) }

    fun setTarget(category: Category, target: Money?) {
        viewModelScope.launch {
            isSaving.value = true
            runCatching { categoryRepository.upsert(category.copy(budgetTarget = target)) }
                .onFailure { error.value = AppError.Unknown }
            isSaving.value = false
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            runCatching { transactionRepository.delete(id) }
                .onFailure { error.value = AppError.Unknown }
        }
    }

    fun dismissError() {
        error.value = null
    }

    private fun shiftPeriod(transform: (BudgetPeriod) -> BudgetPeriod) {
        viewModelScope.launch {
            val settings = settingsRepository.current()
            val current = selectedPeriod.value
                ?: periodResolver.resolve(settings, LocalDate.now(clock)).getOrNull()
                ?: return@launch
            selectedPeriod.value = transform(current)
        }
    }
}
