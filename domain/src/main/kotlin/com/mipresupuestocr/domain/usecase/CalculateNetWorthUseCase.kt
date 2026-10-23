package com.mipresupuestocr.domain.usecase

import com.mipresupuestocr.domain.model.AccountBalance
import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.Money
import com.mipresupuestocr.domain.model.NetWorth
import com.mipresupuestocr.domain.model.PortfolioValuation
import com.mipresupuestocr.domain.model.Transaction
import com.mipresupuestocr.domain.model.TransactionType

/**
 * Adds up everything the user holds: account balances, the portfolio converted to colones, and the
 * money that is only in "available" because no account was chosen for it.
 *
 * Holdings that cannot be valued (no quote, or no USD/CRC rate) are left out of the total and
 * flagged through [NetWorth.isPartial] rather than being guessed at.
 */
class CalculateNetWorthUseCase {

    operator fun invoke(
        balances: List<AccountBalance>,
        portfolio: PortfolioValuation,
        transactions: List<Transaction>,
    ): NetWorth {
        val accountsTotal = Money(balances.sumOf { it.balance.amountMinor }, CurrencyCode.CRC)
        val portfolioCrc = portfolio.totalCrc
        val unassigned = unassignedMoney(transactions)
        val hasUnpricedHoldings = portfolio.unpricedTickers.isNotEmpty()

        return NetWorth(
            accountsTotal = accountsTotal,
            portfolioCrc = portfolioCrc,
            unassigned = unassigned,
            total = accountsTotal + (portfolioCrc ?: Money.zero(CurrencyCode.CRC)) + unassigned,
            isPartial = !portfolio.isEmpty && (portfolioCrc == null || hasUnpricedHoldings),
        )
    }

    /**
     * Income minus expenses for entries recorded without an account: the paycheck the budget shows
     * as available that no account owns yet. Entries assigned to an account are skipped on purpose,
     * because their effect already sits in that account's balance and adding them again would
     * count the same money twice.
     */
    private fun unassignedMoney(transactions: List<Transaction>): Money {
        val minor = transactions
            .filter { it.accountId == null && it.amount.currency == CurrencyCode.CRC }
            .sumOf { entry ->
                when (entry.type) {
                    TransactionType.INCOME -> entry.amount.amountMinor
                    TransactionType.EXPENSE -> -entry.amount.amountMinor
                }
            }
        return Money(minor, CurrencyCode.CRC)
    }
}
