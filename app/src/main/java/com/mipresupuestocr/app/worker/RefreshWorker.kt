package com.mipresupuestocr.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.AppResult
import com.mipresupuestocr.domain.model.RateRefreshResult
import com.mipresupuestocr.domain.model.QuoteRefreshResult
import com.mipresupuestocr.domain.repository.SettingsRepository
import com.mipresupuestocr.domain.usecase.RefreshExchangeRateUseCase
import com.mipresupuestocr.domain.usecase.RefreshQuotesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock

/**
 * Periodic refresh of the reference rate and ETF quotes.
 *
 * Non-retryable outcomes (missing or invalid key, exhausted quota, unsupported symbol) are treated
 * as success so the scheduler does not spin.
 */
@HiltWorker
class RefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val refreshRates: RefreshExchangeRateUseCase,
    private val refreshQuotes: RefreshQuotesUseCase,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        var shouldRetry = false

        when (val rateResult = refreshRates()) {
            is AppResult.Success -> {
                shouldRetry = shouldRetry || rateResult.value.usedCache
                if (!rateResult.value.usedCache) {
                    recordRateRefresh(rateResult.value)
                }
            }

            is AppResult.Failure -> shouldRetry = shouldRetry || rateResult.error.isRetryable()
        }

        when (val quoteResult = refreshQuotes()) {
            is AppResult.Success -> {
                shouldRetry = shouldRetry || quoteResult.value.usedCache
                if (!quoteResult.value.skipped && !quoteResult.value.usedCache) {
                    recordQuoteRefresh(quoteResult.value)
                }
            }

            is AppResult.Failure -> shouldRetry = shouldRetry || quoteResult.error.isRetryable()
        }

        return if (shouldRetry) Result.retry() else Result.success()
    }

    private suspend fun recordRateRefresh(result: RateRefreshResult) {
        settingsRepository.update { it.copy(lastRateRefresh = clock.instant()) }
    }

    private suspend fun recordQuoteRefresh(result: QuoteRefreshResult) {
        settingsRepository.update { it.copy(lastQuoteRefresh = clock.instant()) }
    }

    private fun AppError.isRetryable(): Boolean = when (this) {
        AppError.Network,
        AppError.Timeout,
        AppError.RateLimited,
        AppError.Unknown,
        -> true

        AppError.MissingApiKey,
        AppError.InvalidApiKey,
        AppError.QuotaExceeded,
        AppError.UnsupportedCurrency,
        AppError.UnsupportedAsset,
        AppError.EmptyResponse,
        AppError.MalformedResponse,
        AppError.InvalidRate,
        AppError.InvalidAmount,
        AppError.InvalidIncome,
        AppError.InvalidSplit,
        AppError.InvalidShares,
        AppError.InvalidAccountName,
        AppError.NoData,
        AppError.MissingPaydayAnchor,
        -> false

        is AppError.InvalidTicker, AppError.UnknownSymbol -> false
    }

    companion object {
        const val WORK_NAME = "periodic_rate_and_quote_refresh"
        const val INTERVAL_HOURS = 6L
    }
}
