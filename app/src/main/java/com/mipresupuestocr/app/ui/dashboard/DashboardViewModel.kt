package com.mipresupuestocr.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipresupuestocr.app.ui.util.parseAmount
import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.AppResult
import com.mipresupuestocr.domain.model.BalanceCorrection
import com.mipresupuestocr.domain.model.BudgetPeriod
import com.mipresupuestocr.domain.model.Category
import com.mipresupuestocr.domain.model.CorrectionKind
import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.DashboardSnapshot
import com.mipresupuestocr.domain.model.ExchangeRate
import com.mipresupuestocr.domain.model.Holding
import com.mipresupuestocr.domain.model.Money
import com.mipresupuestocr.domain.model.Quote
import com.mipresupuestocr.domain.model.Transaction
import com.mipresupuestocr.domain.model.UserSettings
import com.mipresupuestocr.domain.repository.BalanceCorrectionRepository
import com.mipresupuestocr.domain.repository.CategoryRepository
import com.mipresupuestocr.domain.repository.HoldingRepository
import com.mipresupuestocr.domain.repository.QuoteRepository
import com.mipresupuestocr.domain.repository.RateRepository
import com.mipresupuestocr.domain.repository.SettingsRepository
import com.mipresupuestocr.domain.repository.TransactionRepository
import com.mipresupuestocr.domain.usecase.BudgetPeriodResolver
import com.mipresupuestocr.domain.usecase.BuildDashboardUseCase
import com.mipresupuestocr.domain.usecase.ClearBalanceCorrectionUseCase
import com.mipresupuestocr.domain.usecase.RefreshExchangeRateUseCase
import com.mipresupuestocr.domain.usecase.RefreshQuotesUseCase
import com.mipresupuestocr.domain.usecase.SetAvailableBalanceUseCase
import com.mipresupuestocr.domain.usecase.SetIncomeBalanceUseCase
import com.mipresupuestocr.domain.usecase.SetSpentBalanceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = true,
    val snapshot: DashboardSnapshot? = null,
    val period: BudgetPeriod? = null,
    val isRefreshing: Boolean = false,
    val error: AppError? = null,
    val message: DashboardMessage? = null,
    val usingCachedRate: Boolean = false,
)

sealed interface DashboardMessage {
    data object Refreshed : DashboardMessage
    data object RefreshedFromCache : DashboardMessage
    data object BalanceAdjusted : DashboardMessage
    data class MissingPrices(val tickers: List<String>) : DashboardMessage
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    holdingRepository: HoldingRepository,
    rateRepository: RateRepository,
    quoteRepository: QuoteRepository,
    private val settingsRepository: SettingsRepository,
    correctionRepository: BalanceCorrectionRepository,
    private val buildDashboard: BuildDashboardUseCase,
    private val refreshRates: RefreshExchangeRateUseCase,
    private val refreshQuotes: RefreshQuotesUseCase,
    private val setIncome: SetIncomeBalanceUseCase,
    private val setSpent: SetSpentBalanceUseCase,
    private val setAvailable: SetAvailableBalanceUseCase,
    private val clearCorrection: ClearBalanceCorrectionUseCase,
    private val periodResolver: BudgetPeriodResolver,
    private val clock: Clock,
) : ViewModel() {

    private data class Bundle(
        val settings: UserSettings,
        val transactions: List<Transaction>,
        val categories: List<Category>,
        val holdings: List<Holding>,
        val quotes: Map<String, Quote>,
        val corrections: List<BalanceCorrection>,
    )

    private data class Resolved(
        val snapshot: DashboardSnapshot?,
        val error: AppError?,
        val period: BudgetPeriod?,
        val usedCache: Boolean = false,
    )

    private val selectedPeriod = MutableStateFlow<BudgetPeriod?>(null)
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<AppError?>(null)
    private val message = MutableStateFlow<DashboardMessage?>(null)
    private val usingCachedRate = MutableStateFlow(false)

    private val bundles: Flow<Bundle> = combine(
        combine(
            settingsRepository.observeSettings(),
            transactionRepository.observeAll(),
            categoryRepository.observeAll(),
            holdingRepository.observeAll(),
            quoteRepository.observeQuotes(),
        ) { settings, transactions, categories, holdings, quotes ->
            Bundle(settings, transactions, categories, holdings, quotes, emptyList())
        },
        correctionRepository.observeAll(),
    ) { bundle, corrections -> bundle.copy(corrections = corrections) }

    private val withRate: Flow<Pair<Bundle, ExchangeRate?>> =
        combine(bundles, rateRepository.observeActiveRate()) { bundle, rate -> bundle to rate }

    private val resolved: Flow<Resolved> = combine(withRate, selectedPeriod) { (bundle, rate), period ->
        val effectivePeriod = period
            ?.takeIf { it.type == bundle.settings.periodType }
            ?: periodResolver.resolve(bundle.settings, LocalDate.now(clock)).getOrNull()
            ?: return@combine Resolved(null, AppError.MissingPaydayAnchor, null)

        when (
            val result = buildDashboard.build(
                settings = bundle.settings,
                referenceDate = effectivePeriod.start,
                now = clock.instant(),
                transactions = bundle.transactions,
                categories = bundle.categories,
                holdings = bundle.holdings,
                quotes = bundle.quotes,
                rate = rate,
                corrections = bundle.corrections,
            )
        ) {
            is AppResult.Success -> Resolved(result.value, null, effectivePeriod)
            is AppResult.Failure -> Resolved(null, result.error, effectivePeriod)
        }
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        resolved,
        refreshing,
        error,
        message,
        usingCachedRate,
    ) { resolvedState, isRefreshing, currentError, currentMessage, cached ->
        DashboardUiState(
            isLoading = false,
            snapshot = resolvedState.snapshot,
            period = resolvedState.period,
            isRefreshing = isRefreshing,
            error = currentError ?: resolvedState.error,
            message = currentMessage,
            usingCachedRate = cached,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(),
    )

    init {
        viewModelScope.launch {
            // First load with no cached rate: fetch the public reference rate once.
            if (rateRepository.activeRateSnapshot() == null) {
                refresh()
            }
        }
    }

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            error.value = null
            usingCachedRate.value = false
            var partial: List<String> = emptyList()
            var usedCache = false

            when (val rateResult = refreshRates()) {
                is AppResult.Success -> {
                    usedCache = rateResult.value.usedCache
                    usingCachedRate.value = usedCache
                }

                is AppResult.Failure -> error.value = rateResult.error
            }

            when (val quoteResult = refreshQuotes()) {
                is AppResult.Success -> {
                    partial = quoteResult.value.failedTickers
                    usedCache = usedCache || quoteResult.value.usedCache
                }

                is AppResult.Failure -> if (error.value == null) error.value = quoteResult.error
            }

            message.value = when {
                partial.isNotEmpty() -> DashboardMessage.MissingPrices(partial)
                usedCache -> DashboardMessage.RefreshedFromCache
                error.value != null -> null
                else -> DashboardMessage.Refreshed
            }
            refreshing.value = false
        }
    }

    fun previousPeriod() = shiftPeriod { period -> periodResolver.previous(period) }

    fun nextPeriod() = shiftPeriod { period -> periodResolver.next(period) }

    /** Corrects a period figure to the exact total the user typed; the history is untouched. */
    fun setIncomeBalance(text: String) = applyBalance(text) { period, target -> setIncome(period, target) }

    fun setSpentBalance(text: String) = applyBalance(text) { period, target -> setSpent(period, target) }

    fun setAvailableBalance(text: String) = applyBalance(text) { period, target -> setAvailable(period, target) }

    fun clearIncomeCorrection() = clearCorrection(CorrectionKind.INCOME, null)

    fun clearSpentCorrection() = clearCorrection(CorrectionKind.SPENT, null)

    private fun applyBalance(
        text: String,
        action: suspend (BudgetPeriod, Money) -> AppResult<Unit>,
    ) {
        val amount = parseAmount(text)
        if (amount == null || amount.signum() < 0) {
            error.value = AppError.InvalidAmount
            return
        }
        viewModelScope.launch {
            val period = periodForAction()
            if (period == null) {
                error.value = AppError.MissingPaydayAnchor
                return@launch
            }
            error.value = null
            when (val result = action(period, Money.of(amount, CurrencyCode.CRC))) {
                is AppResult.Success -> message.value = DashboardMessage.BalanceAdjusted
                is AppResult.Failure -> error.value = result.error
            }
        }
    }

    private fun clearCorrection(kind: CorrectionKind, categoryId: Long?) {
        viewModelScope.launch {
            val period = periodForAction() ?: return@launch
            error.value = null
            runCatching { clearCorrection(period, kind, categoryId) }
                .onSuccess { message.value = DashboardMessage.BalanceAdjusted }
                .onFailure { error.value = AppError.Unknown }
        }
    }

    private suspend fun periodForAction(): BudgetPeriod? = uiState.value.period
        ?: periodResolver.resolve(settingsRepository.current(), LocalDate.now(clock)).getOrNull()

    fun dismissError() {
        error.value = null
    }

    fun consumeMessage() {
        message.value = null
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
