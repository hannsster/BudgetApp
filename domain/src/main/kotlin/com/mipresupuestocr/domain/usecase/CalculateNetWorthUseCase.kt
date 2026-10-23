package com.mipresupuestocr.domain.usecase

import com.mipresupuestocr.domain.model.AccountBalance
import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.Money
import com.mipresupuestocr.domain.model.NetWorth
import com.mipresupuestocr.domain.model.PortfolioValuation

/**
 * Adds up everything the user holds: account balances plus the portfolio converted to colones.
 *
 * Holdings that cannot be valued (no quote, or no USD/CRC rate) are left out of the total and
 * flagged through [NetWorth.isPartial] rather than being guessed at.
 */
class CalculateNetWorthUseCase {

    operator fun invoke(
        balances: List<AccountBalance>,
        portfolio: PortfolioValuation,
    ): NetWorth {
        val accountsTotal = Money(balances.sumOf { it.balance.amountMinor }, CurrencyCode.CRC)
        val portfolioCrc = portfolio.totalCrc
        val hasUnpricedHoldings = portfolio.unpricedTickers.isNotEmpty()

        return NetWorth(
            accountsTotal = accountsTotal,
            portfolioCrc = portfolioCrc,
            total = accountsTotal + (portfolioCrc ?: Money.zero(CurrencyCode.CRC)),
            isPartial = !portfolio.isEmpty && (portfolioCrc == null || hasUnpricedHoldings),
        )
    }
}
