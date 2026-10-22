package com.mipresupuestocr.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * User preferences. Kept intentionally small: anything that is not a preference (holdings,
 * transactions, cached rates) lives in the database.
 */
data class UserSettings(
    val periodType: PeriodType = PeriodType.MONTHLY,
    /** First known payday; anchors the 14-day biweekly cycle. */
    val paydayAnchor: LocalDate? = null,
    val split: BudgetSplit = BudgetSplit.FIFTY_THIRTY_TWENTY,
    /** Dark is the default: the app is designed around the Catppuccin Mocha palette. */
    val themeMode: ThemeMode = ThemeMode.DARK,
    /** True once the localized default categories have been inserted. */
    val setupCompleted: Boolean = false,
    val lastRateRefresh: Instant? = null,
    val lastQuoteRefresh: Instant? = null,
)

/** What a portfolio refresh actually achieved, including tickers the provider rejected. */
data class QuoteRefreshResult(
    val updated: Map<String, Quote>,
    val failedTickers: List<String>,
    /** True when there were no holdings, so no network call was made. */
    val skipped: Boolean,
    /** True when the provider failed and previously cached quotes are being shown. */
    val usedCache: Boolean = false,
)

/** Result of a rate refresh; [usedCache] means the provider failed and a saved rate is shown. */
data class RateRefreshResult(
    val rate: ExchangeRate,
    val usedCache: Boolean,
)

/** Everything the dashboard needs, assembled from reactive data sources. */
data class DashboardSnapshot(
    val period: BudgetPeriod,
    val budget: BudgetSummary,
    val rate: ExchangeRate?,
    val rateIsStale: Boolean,
    val portfolio: PortfolioValuation,
    val lastQuoteRefresh: Instant?,
)

/** Manual rate input, kept separate from the fetched reference rate. */
data class ManualRateInput(
    val rate: BigDecimal,
    val asOf: Instant,
)
