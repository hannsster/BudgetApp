package com.mipresupuestocr.app.ui.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipresupuestocr.app.data.remote.MarketDataConfig
import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.AppResult
import com.mipresupuestocr.domain.model.ExchangeRate
import com.mipresupuestocr.domain.model.PortfolioValuation
import com.mipresupuestocr.domain.repository.HoldingRepository
import com.mipresupuestocr.domain.repository.QuoteRepository
import com.mipresupuestocr.domain.repository.RateRepository
import com.mipresupuestocr.domain.usecase.PortfolioValuator
import com.mipresupuestocr.domain.usecase.RefreshExchangeRateUseCase
import com.mipresupuestocr.domain.usecase.RefreshQuotesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PortfolioUiState(
    val isLoading: Boolean = true,
    val valuation: PortfolioValuation? = null,
    val rate: ExchangeRate? = null,
    val isRefreshing: Boolean = false,
    val error: AppError? = null,
    val message: PortfolioMessage? = null,
    val marketKeyConfigured: Boolean = true,
    val providerName: String = "",
)

sealed interface PortfolioMessage {
    data object Refreshed : PortfolioMessage
    data object RefreshedFromCache : PortfolioMessage
    data class MissingPrices(val tickers: List<String>) : PortfolioMessage
}

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val holdingRepository: HoldingRepository,
    quoteRepository: QuoteRepository,
    rateRepository: RateRepository,
    private val refreshQuotes: RefreshQuotesUseCase,
    private val refreshRates: RefreshExchangeRateUseCase,
    marketDataConfig: MarketDataConfig,
) : ViewModel() {

    private val valuator = PortfolioValuator()
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<AppError?>(null)
    private val message = MutableStateFlow<PortfolioMessage?>(null)

    private val data = combine(
        holdingRepository.observeAll(),
        quoteRepository.observeQuotes(),
        rateRepository.observeActiveRate(),
    ) { holdings, quotes, rate ->
        Triple(holdings, quotes, rate)
    }

    val uiState: StateFlow<PortfolioUiState> = combine(data, refreshing, error, message) { (holdings, quotes, rate), isRefreshing, currentError, currentMessage ->
        PortfolioUiState(
            isLoading = false,
            valuation = valuator.valuate(holdings, quotes, rate),
            rate = rate,
            isRefreshing = isRefreshing,
            error = currentError,
            message = currentMessage,
            marketKeyConfigured = marketDataConfig.isConfigured,
            providerName = marketDataConfig.providerName,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PortfolioUiState(
            marketKeyConfigured = marketDataConfig.isConfigured,
            providerName = marketDataConfig.providerName,
        ),
    )

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            error.value = null
            var partial: List<String> = emptyList()
            var usedCache = false

            when (val rateResult = refreshRates()) {
                is AppResult.Success -> usedCache = rateResult.value.usedCache
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
                partial.isNotEmpty() -> PortfolioMessage.MissingPrices(partial)
                usedCache -> PortfolioMessage.RefreshedFromCache
                error.value != null -> null
                else -> PortfolioMessage.Refreshed
            }
            refreshing.value = false
        }
    }

    fun deleteHolding(id: Long) {
        viewModelScope.launch {
            runCatching { holdingRepository.delete(id) }
                .onFailure { error.value = AppError.Unknown }
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    fun dismissError() {
        error.value = null
    }
}
