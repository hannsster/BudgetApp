package com.mipresupuestocr.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Currency codes the app understands today.
 *
 * CRC is the base currency; USD is the secondary display currency. Amounts are stored as
 * minor units (Long) so that budgeting math never accumulates floating point error.
 */
enum class CurrencyCode(val isoCode: String, val decimals: Int, val symbol: String) {
    CRC("CRC", 2, "₡"),
    USD("USD", 2, "$");

    companion object {
        fun fromIsoCode(code: String): CurrencyCode? =
            entries.firstOrNull { it.isoCode.equals(code.trim(), ignoreCase = true) }
    }
}

/** An immutable monetary amount expressed in minor units of [currency]. */
data class Money(val amountMinor: Long, val currency: CurrencyCode) : Comparable<Money> {

    val asDecimal: BigDecimal
        get() = BigDecimal.valueOf(amountMinor, currency.decimals)

    val isZero: Boolean get() = amountMinor == 0L
    val isNegative: Boolean get() = amountMinor < 0L
    val isPositive: Boolean get() = amountMinor > 0L

    operator fun plus(other: Money): Money = Money(amountMinor + otherMinor(other), currency)

    operator fun minus(other: Money): Money = Money(amountMinor - otherMinor(other), currency)

    operator fun unaryMinus(): Money = Money(-amountMinor, currency)

    operator fun times(factor: BigDecimal): Money = of(asDecimal.multiply(factor), currency)

    operator fun times(factor: Int): Money = times(BigDecimal.valueOf(factor.toLong()))

    operator fun div(divisor: BigDecimal): Money {
        require(divisor.compareTo(BigDecimal.ZERO) != 0) { "Cannot divide money by zero" }
        return of(asDecimal.divide(divisor, currency.decimals + 4, RoundingMode.HALF_UP), currency)
    }

    /** Returns [percent] of this amount, rounded half-up to the currency's minor unit. */
    fun percent(percent: Int): Money =
        times(BigDecimal.valueOf(percent.toLong()).divide(BigDecimal.valueOf(100)))

    fun abs(): Money = if (isNegative) -this else this

    /** Clamps this amount to zero, useful for "remaining" figures that must not display negative. */
    fun coerceAtLeastZero(): Money = if (isNegative) zero(currency) else this

    override fun compareTo(other: Money): Int {
        require(other.currency == currency) {
            "Cannot compare $currency with ${other.currency}"
        }
        return amountMinor.compareTo(other.amountMinor)
    }

    override fun toString(): String = "${asDecimal.toPlainString()} ${currency.isoCode}"

    private fun otherMinor(other: Money): Long {
        require(other.currency == currency) {
            "Currency mismatch: $currency vs ${other.currency}"
        }
        return other.amountMinor
    }

    companion object {
        fun zero(currency: CurrencyCode): Money = Money(0L, currency)

        /** Builds an amount from a major-unit value such as `512.34`, rounding half-up. */
        fun of(value: BigDecimal, currency: CurrencyCode): Money =
            Money(value.toMinorUnits(currency), currency)

        fun ofMajor(value: String, currency: CurrencyCode): Money =
            of(BigDecimal(value.trim()), currency)
    }
}

/** Converts a major-unit decimal to minor units, rounding half-up. */
fun BigDecimal.toMinorUnits(currency: CurrencyCode): Long =
    setScale(currency.decimals, RoundingMode.HALF_UP)
        .movePointRight(currency.decimals)
        .setScale(0, RoundingMode.HALF_UP)
        .longValueExact()
