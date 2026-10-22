package com.mipresupuestocr.domain.usecase

import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.AppResult
import com.mipresupuestocr.domain.model.ExchangeRate
import com.mipresupuestocr.domain.model.ManualRateInput
import com.mipresupuestocr.domain.model.QuoteRefreshResult
import com.mipresupuestocr.domain.model.RateRefreshResult
import com.mipresupuestocr.domain.repository.HoldingRepository
import com.mipresupuestocr.domain.repository.QuoteRepository
import com.mipresupuestocr.domain.repository.RateRepository
import java.math.BigDecimal
import java.util.Locale

/**
 * Refreshes quotes for every ticker the user actually holds.
 *
 * An empty portfolio short-circuits without a network call, which keeps empty state free and
 * avoids burning provider quota.
 */
class RefreshQuotesUseCase(
    private val quoteRepository: QuoteRepository,
    private val holdingRepository: HoldingRepository,
) {
    suspend operator fun invoke(): AppResult<QuoteRefreshResult> {
        val tickers = holdingRepository.getAll()
            .filter { it.shares.signum() > 0 }
            .map { it.ticker.trim().uppercase(Locale.ROOT) }
            .distinct()

        if (tickers.isEmpty()) {
            return AppResult.success(
                QuoteRefreshResult(updated = emptyMap(), failedTickers = emptyList(), skipped = true),
            )
        }

        return when (val result = quoteRepository.refresh(tickers)) {
            is AppResult.Success -> AppResult.success(
                QuoteRefreshResult(
                    updated = result.value,
                    failedTickers = tickers.filterNot { result.value.containsKey(it) },
                    skipped = false,
                ),
            )

            is AppResult.Failure -> {
                val cached = quoteRepository.cachedQuotes(tickers)
                if (cached.isEmpty()) {
                    result
                } else {
                    AppResult.success(
                        QuoteRefreshResult(
                            updated = cached,
                            failedTickers = tickers.filterNot { cached.containsKey(it) },
                            skipped = false,
                            usedCache = true,
                        ),
                    )
                }
            }
        }
    }
}

class RefreshExchangeRateUseCase(private val rateRepository: RateRepository) {
    suspend operator fun invoke(): AppResult<RateRefreshResult> =
        when (val result = rateRepository.refresh()) {
            is AppResult.Success -> AppResult.success(RateRefreshResult(result.value, usedCache = false))
            is AppResult.Failure -> {
                val cached = rateRepository.activeRateSnapshot()
                if (cached == null) {
                    result
                } else {
                    AppResult.success(RateRefreshResult(cached, usedCache = true))
                }
            }
        }
}

class SetManualRateUseCase(private val rateRepository: RateRepository) {
    suspend operator fun invoke(input: ManualRateInput): AppResult<ExchangeRate> {
        if (input.rate.compareTo(BigDecimal.ZERO) <= 0) {
            return AppResult.failure(AppError.InvalidRate)
        }
        return rateRepository.setManualRate(input)
    }
}

class ClearManualRateUseCase(private val rateRepository: RateRepository) {
    suspend operator fun invoke() = rateRepository.clearManualRate()
}
