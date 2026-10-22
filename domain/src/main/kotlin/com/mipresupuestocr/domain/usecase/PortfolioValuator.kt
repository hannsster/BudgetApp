package com.mipresupuestocr.domain.usecase

import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.ExchangeRate
import com.mipresupuestocr.domain.model.Holding
import com.mipresupuestocr.domain.model.Money
import com.mipresupuestocr.domain.model.PortfolioValuation
import com.mipresupuestocr.domain.model.PositionValuation
import com.mipresupuestocr.domain.model.Quote
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Valuates manually entered holdings against the latest cached quotes.
 *
 * Holdings without a quote are reported in [PortfolioValuation.unpricedTickers] and excluded from
 * totals; totals in CRC are only produced when a USD/CRC rate is available.
 */
class PortfolioValuator(private val converter: CurrencyConverter = CurrencyConverter()) {

    fun valuate(
        holdings: List<Holding>,
        quotes: Map<String, Quote>,
        usdCrcRate: ExchangeRate?,
    ): PortfolioValuation {
        val quotesByTicker = quotes.entries.associate { (ticker, quote) ->
            ticker.trim().uppercase(Locale.ROOT) to quote
        }

        val priced = mutableListOf<Pair<Holding, Quote>>()
        val unpriced = mutableListOf<String>()
        holdings.forEach { holding ->
            val ticker = holding.ticker.trim().uppercase(Locale.ROOT)
            val quote = quotesByTicker[ticker]
            if (quote != null && holding.shares.signum() > 0) {
                priced += holding to quote
            } else {
                unpriced += ticker
            }
        }

        val totalUsdMinor = priced.sumOf { (holding, quote) ->
            Money.of(holding.shares.multiply(quote.priceUsd), CurrencyCode.USD).amountMinor
        }
        val totalUsd = Money(totalUsdMinor, CurrencyCode.USD)
        val totalCrc = usdCrcRate?.let { rate ->
            converter.convert(totalUsd, CurrencyCode.CRC, rate).getOrNull()
        }

        val positions = priced.map { (holding, quote) ->
            val marketValueUsd = Money.of(holding.shares.multiply(quote.priceUsd), CurrencyCode.USD)
            val marketValueCrc = usdCrcRate?.let { rate ->
                converter.convert(marketValueUsd, CurrencyCode.CRC, rate).getOrNull()
            }
            val averagePrice = holding.averagePriceUsd?.takeIf { it.signum() > 0 }
            val costBasis = averagePrice?.let {
                Money.of(it.multiply(holding.shares), CurrencyCode.USD)
            }
            val unrealizedPnl = costBasis?.let { marketValueUsd - it }

            PositionValuation(
                holdingId = holding.id,
                ticker = holding.ticker.trim().uppercase(Locale.ROOT),
                shares = holding.shares,
                priceUsd = quote.priceUsd,
                marketValueUsd = marketValueUsd,
                marketValueCrc = marketValueCrc,
                allocationPercent = if (totalUsd.isPositive) {
                    percentOf(marketValueUsd.amountMinor, totalUsd.amountMinor)
                } else {
                    0.0
                },
                costBasisUsd = costBasis,
                unrealizedPnlUsd = unrealizedPnl,
                unrealizedPnlPercent = unrealizedPnlPercent(unrealizedPnl, costBasis),
                asOf = quote.asOf,
                source = quote.source,
            )
        }.sortedWith(
            compareByDescending<PositionValuation> { it.marketValueUsd.amountMinor }
                .thenBy { it.ticker },
        )

        val allPositionsHaveCost = positions.isNotEmpty() && positions.all { it.costBasisUsd != null }
        val totalCostUsd = if (allPositionsHaveCost) {
            positions.fold(Money.zero(CurrencyCode.USD)) { acc, position ->
                acc + (position.costBasisUsd ?: Money.zero(CurrencyCode.USD))
            }
        } else {
            null
        }
        val totalUnrealizedPnlUsd = totalCostUsd?.let { totalUsd - it }

        return PortfolioValuation(
            positions = positions,
            totalUsd = totalUsd,
            totalCrc = totalCrc,
            totalCostUsd = totalCostUsd,
            totalUnrealizedPnlUsd = totalUnrealizedPnlUsd,
            totalUnrealizedPnlPercent = unrealizedPnlPercent(totalUnrealizedPnlUsd, totalCostUsd),
            unpricedTickers = unpriced.distinct().sorted(),
            isEmpty = holdings.isEmpty(),
        )
    }

    private fun unrealizedPnlPercent(pnl: Money?, cost: Money?): Double? {
        if (pnl == null || cost == null || !cost.isPositive) return null
        return BigDecimal.valueOf(pnl.amountMinor)
            .multiply(BigDecimal(100))
            .divide(BigDecimal.valueOf(cost.amountMinor), 2, RoundingMode.HALF_UP)
            .toDouble()
    }

    private fun percentOf(part: Long, total: Long): Double =
        BigDecimal.valueOf(part)
            .multiply(BigDecimal(100))
            .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
            .toDouble()
}
