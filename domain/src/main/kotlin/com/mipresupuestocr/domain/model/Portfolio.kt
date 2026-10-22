package com.mipresupuestocr.domain.model

import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

/**
 * A manually entered ETF position. Holdings are user data: nothing is seeded or inferred.
 */
data class Holding(
    val id: Long = 0L,
    val ticker: String,
    val shares: BigDecimal,
    val averagePriceUsd: BigDecimal? = null,
    val notes: String = "",
    val createdAt: Instant? = null,
)

/** A latest-known quote for a ticker, always expressed in USD. */
data class Quote(
    val ticker: String,
    val priceUsd: BigDecimal,
    val asOf: Instant,
    val source: String,
)

/**
 * A USD/CRC reference rate. [isManual] marks a user-entered rate (for example a rate published by
 * the user's bank) so the UI never presents it as an official bank rate.
 */
data class ExchangeRate(
    val base: CurrencyCode,
    val quote: CurrencyCode,
    val rate: BigDecimal,
    val asOf: Instant,
    val source: String,
    val isManual: Boolean = false,
) {
    fun isStale(now: Instant, maxAge: Duration): Boolean =
        Duration.between(asOf, now) > maxAge
}

data class PositionValuation(
    val holdingId: Long,
    val ticker: String,
    val shares: BigDecimal,
    val priceUsd: BigDecimal,
    val marketValueUsd: Money,
    val marketValueCrc: Money?,
    /** Share of the priced portfolio, 0..100. */
    val allocationPercent: Double,
    val costBasisUsd: Money?,
    val unrealizedPnlUsd: Money?,
    val unrealizedPnlPercent: Double?,
    val asOf: Instant?,
    val source: String?,
)

data class PortfolioValuation(
    val positions: List<PositionValuation>,
    val totalUsd: Money,
    val totalCrc: Money?,
    val totalCostUsd: Money?,
    val totalUnrealizedPnlUsd: Money?,
    val totalUnrealizedPnlPercent: Double?,
    /** Tickers that have no quote yet, so they are excluded from totals. */
    val unpricedTickers: List<String>,
    val isEmpty: Boolean,
)
