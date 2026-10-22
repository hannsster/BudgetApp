package com.mipresupuestocr.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipresupuestocr.app.BuildConfig
import com.mipresupuestocr.app.data.remote.MarketDataConfig
import com.mipresupuestocr.app.ui.util.parseDecimal
import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.AppResult
import com.mipresupuestocr.domain.model.ExchangeRate
import com.mipresupuestocr.domain.model.ManualRateInput
import com.mipresupuestocr.domain.model.PeriodType
import com.mipresupuestocr.domain.model.ThemeMode
import com.mipresupuestocr.domain.model.UserSettings
import com.mipresupuestocr.domain.repository.RateRepository
import com.mipresupuestocr.domain.repository.SettingsRepository
import com.mipresupuestocr.domain.usecase.ClearManualRateUseCase
import com.mipresupuestocr.domain.usecase.RefreshExchangeRateUseCase
import com.mipresupuestocr.domain.usecase.RefreshQuotesUseCase
import com.mipresupuestocr.domain.usecase.SetManualRateUseCase
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

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val rate: ExchangeRate? = null,
    val referenceRate: ExchangeRate? = null,
    val marketKeyConfigured: Boolean = false,
    val marketProvider: String = "",
    val marketProviderUrl: String = "",
    val isBusy: Boolean = false,
    val error: AppError? = null,
    val message: SettingsMessage? = null,
    val appVersion: String = BuildConfig.VERSION_NAME,
)

sealed interface SettingsMessage {
    data object RateRefreshed : SettingsMessage
    data object RateFromCache : SettingsMessage
    data object ManualRateSaved : SettingsMessage
    data object ManualRateCleared : SettingsMessage
    data object QuotesRefreshed : SettingsMessage
    data object MarketKeySaved : SettingsMessage
    data object MarketKeyCleared : SettingsMessage
}

private data class SettingsData(
    val settings: UserSettings,
    val rate: ExchangeRate?,
    val reference: ExchangeRate?,
    val keyConfigured: Boolean,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    rateRepository: RateRepository,
    private val refreshRatesUseCase: RefreshExchangeRateUseCase,
    private val refreshQuotesUseCase: RefreshQuotesUseCase,
    private val setManualRate: SetManualRateUseCase,
    private val clearManualRate: ClearManualRateUseCase,
    private val marketDataConfig: MarketDataConfig,
    private val clock: Clock,
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val error = MutableStateFlow<AppError?>(null)
    private val message = MutableStateFlow<SettingsMessage?>(null)

    private val data = combine(
        settingsRepository.observeSettings(),
        rateRepository.observeActiveRate(),
        rateRepository.observeReferenceRate(),
        marketDataConfig.observeConfigured(),
    ) { settings, rate, reference, keyConfigured ->
        SettingsData(settings, rate, reference, keyConfigured)
    }

    val uiState: StateFlow<SettingsUiState> = combine(data, busy, error, message) { current, isBusy, currentError, currentMessage ->
        SettingsUiState(
            settings = current.settings,
            rate = current.rate,
            referenceRate = current.reference,
            marketKeyConfigured = current.keyConfigured,
            marketProvider = marketDataConfig.providerName,
            marketProviderUrl = MarketDataConfig.SIGNUP_URL,
            isBusy = isBusy,
            error = currentError,
            message = currentMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(
            marketKeyConfigured = marketDataConfig.hasBuildTimeKey,
            marketProvider = marketDataConfig.providerName,
            marketProviderUrl = MarketDataConfig.SIGNUP_URL,
        ),
    )

    fun setPeriodType(type: PeriodType) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(periodType = type) }
        }
    }

    fun setPayday(date: LocalDate) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(paydayAnchor = date) }
        }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(themeMode = mode) }
        }
    }

    fun saveManualRate(text: String) {
        val value = parseDecimal(text)
        if (value == null || value.signum() <= 0) {
            error.value = AppError.InvalidRate
            return
        }
        viewModelScope.launch {
            busy.value = true
            error.value = null
            when (val result = setManualRate(ManualRateInput(value, clock.instant()))) {
                is AppResult.Success -> message.value = SettingsMessage.ManualRateSaved
                is AppResult.Failure -> error.value = result.error
            }
            busy.value = false
        }
    }

    fun removeManualRate() {
        viewModelScope.launch {
            error.value = null
            clearManualRate()
            message.value = SettingsMessage.ManualRateCleared
        }
    }

    fun refreshRates() {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            error.value = null
            when (val result = refreshRatesUseCase()) {
                is AppResult.Success -> message.value = if (result.value.usedCache) {
                    SettingsMessage.RateFromCache
                } else {
                    SettingsMessage.RateRefreshed
                }

                is AppResult.Failure -> error.value = result.error
            }
            busy.value = false
        }
    }

    /** Saving a blank value is treated as clearing the key. */
    fun saveMarketDataKey(text: String) {
        val key = text.trim()
        if (key.isEmpty()) {
            clearMarketDataKey()
            return
        }
        viewModelScope.launch {
            busy.value = true
            error.value = null
            runCatching { marketDataConfig.saveApiKey(key) }
                .onSuccess { message.value = SettingsMessage.MarketKeySaved }
                .onFailure { error.value = AppError.Unknown }
            busy.value = false
        }
    }

    fun clearMarketDataKey() {
        viewModelScope.launch {
            error.value = null
            runCatching { marketDataConfig.clearApiKey() }
                .onSuccess { message.value = SettingsMessage.MarketKeyCleared }
                .onFailure { error.value = AppError.Unknown }
        }
    }

    fun refreshQuotes() {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            error.value = null
            when (val result = refreshQuotesUseCase()) {
                is AppResult.Success -> message.value = SettingsMessage.QuotesRefreshed
                is AppResult.Failure -> error.value = result.error
            }
            busy.value = false
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    fun dismissError() {
        error.value = null
    }
}
