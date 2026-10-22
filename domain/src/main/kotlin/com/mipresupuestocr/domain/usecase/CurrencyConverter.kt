package com.mipresupuestocr.domain.usecase

import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.AppResult
import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.ExchangeRate
import com.mipresupuestocr.domain.model.Money
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Converts between the two supported currencies using a USD/CRC [ExchangeRate].
 *
 * Direction is derived from the rate itself: moving to the quote currency multiplies, moving back
 * to the base currency divides.
 */
class CurrencyConverter {

    fun convert(
        amount: Money,
        target: CurrencyCode,
        rate: ExchangeRate,
    ): AppResult<Money> {
        if (rate.rate.compareTo(BigDecimal.ZERO) <= 0) return AppResult.failure(AppError.InvalidRate)
        if (amount.currency == target) return AppResult.success(amount)

        val converted = when {
            amount.currency == rate.base && target == rate.quote ->
                amount.asDecimal.multiply(rate.rate)

            amount.currency == rate.quote && target == rate.base ->
                amount.asDecimal.divide(rate.rate, amount.currency.decimals + 8, RoundingMode.HALF_UP)

            else -> return AppResult.failure(AppError.UnsupportedCurrency)
        }
        return AppResult.success(Money.of(converted, target))
    }
}
