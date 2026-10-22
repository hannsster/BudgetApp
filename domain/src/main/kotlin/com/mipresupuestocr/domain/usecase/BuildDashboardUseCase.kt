package com.mipresupuestocr.domain.usecase

import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.AppResult
import com.mipresupuestocr.domain.model.BalanceCorrection
import com.mipresupuestocr.domain.model.Category
import com.mipresupuestocr.domain.model.DashboardSnapshot
import com.mipresupuestocr.domain.model.ExchangeRate
import com.mipresupuestocr.domain.model.Holding
import com.mipresupuestocr.domain.model.Quote
import com.mipresupuestocr.domain.model.Transaction
import com.mipresupuestocr.domain.model.UserSettings
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Combines budget and portfolio data into one immutable snapshot for the dashboard. Pure function
 * of its inputs, so it is fully unit testable.
 */
class BuildDashboardUseCase(
    private val periodResolver: BudgetPeriodResolver = BudgetPeriodResolver(),
    private val progressCalculator: BudgetProgressCalculator = BudgetProgressCalculator(),
    private val portfolioValuator: PortfolioValuator = PortfolioValuator(),
) {

    fun build(
        settings: UserSettings,
        referenceDate: LocalDate,
        now: Instant,
        transactions: List<Transaction>,
        categories: List<Category>,
        holdings: List<Holding>,
        quotes: Map<String, Quote>,
        rate: ExchangeRate?,
        corrections: List<BalanceCorrection> = emptyList(),
    ): AppResult<DashboardSnapshot> {
        val periodResult = periodResolver.resolve(settings, referenceDate)
        val period = periodResult.getOrNull()
            ?: return AppResult.failure(periodResult.errorOrNull() ?: AppError.Unknown)

        val budget = progressCalculator.calculate(period, transactions, categories, corrections)
        val portfolio = portfolioValuator.valuate(holdings, quotes, rate)

        return AppResult.success(
            DashboardSnapshot(
                period = period,
                budget = budget,
                rate = rate,
                rateIsStale = rate?.isStale(now, RATE_STALE_AFTER) ?: false,
                portfolio = portfolio,
                lastQuoteRefresh = quotes.values.maxOfOrNull { it.asOf },
            ),
        )
    }

    companion object {
        /** A daily-published reference rate older than two days is surfaced as stale. */
        val RATE_STALE_AFTER: Duration = Duration.ofHours(48)
    }
}
