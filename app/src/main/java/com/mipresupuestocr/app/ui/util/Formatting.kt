package com.mipresupuestocr.app.ui.util

import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

/**
 * Formatting is deliberately explicit: colón amounts always use Costa Rican conventions
 * (period for thousands, comma for decimals) no matter which language the UI is in.
 */
object MoneyFormat {

    val CostaRica: Locale = Locale("es", "CR")
    val UnitedStates: Locale = Locale.US

    fun format(amount: Money): String {
        val locale = if (amount.currency == CurrencyCode.CRC) CostaRica else UnitedStates
        val format = NumberFormat.getCurrencyInstance(locale)
        format.currency = Currency.getInstance(amount.currency.isoCode)
        format.minimumFractionDigits = amount.currency.decimals
        format.maximumFractionDigits = amount.currency.decimals
        return format.format(amount.asDecimal)
    }

    fun formatValue(value: BigDecimal, currency: CurrencyCode): String =
        format(Money.of(value, currency))

    /** Price per unit. Always two decimals, matching every other number in the app. */
    fun formatPrice(value: BigDecimal): String {
        val format = NumberFormat.getNumberInstance(UnitedStates)
        format.minimumFractionDigits = 2
        format.maximumFractionDigits = 2
        return format.format(value)
    }

    /** Exchange rates, quoted with the colón's two decimals. */
    fun formatRate(value: BigDecimal): String {
        val format = NumberFormat.getNumberInstance(CostaRica)
        format.minimumFractionDigits = 2
        format.maximumFractionDigits = 2
        return format.format(value)
    }

    fun formatPercent(value: Double): String {
        val format = NumberFormat.getNumberInstance(UnitedStates)
        format.minimumFractionDigits = 1
        format.maximumFractionDigits = 1
        return "${format.format(value)}%"
    }

    /** Whole units stay whole ("3"), fractional ones are capped at two decimals ("3.25"). */
    fun formatShares(value: BigDecimal): String {
        val format = NumberFormat.getNumberInstance(UnitedStates)
        format.minimumFractionDigits = 0
        format.maximumFractionDigits = 2
        return format.format(value)
    }

    fun formatDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)

    fun formatDateTime(
        instant: Instant,
        locale: Locale = Locale.getDefault(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(zone)
        .format(instant)
}

/**
 * Parses a user-typed amount such as `350 000`, `1.234,56` or `1234.56`.
 *
 * Spaces are ignored. The last separator is treated as the decimal separator only when it is
 * followed by one or two digits; otherwise separators are treated as thousands separators.
 */
fun parseAmount(input: String): BigDecimal? {
    val normalized = normalizeNumber(input, maxDecimals = 2) ?: return null
    return runCatching { BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP) }.getOrNull()
}

/** Parses a plain decimal such as a share count or an ETF price in USD. */
fun parseDecimal(input: String): BigDecimal? {
    val normalized = normalizeNumber(input, maxDecimals = 6) ?: return null
    return runCatching { BigDecimal(normalized) }.getOrNull()
}

fun parsePercent(input: String): Int? {
    val value = input.trim().toIntOrNull() ?: return null
    return value.takeIf { it in 0..100 }
}

private fun normalizeNumber(input: String, maxDecimals: Int): String? {
    val cleaned = input.replace(" ", "").replace("\u00A0", "").trim()
    if (cleaned.isEmpty()) return null
    if (cleaned.any { !it.isDigit() && it != '.' && it != ',' }) return null

    val lastSeparator = cleaned.indexOfLast { it == '.' || it == ',' }
    val digitsAfter = if (lastSeparator >= 0) cleaned.length - lastSeparator - 1 else 0
    val decimalChar = if (digitsAfter in 1..maxDecimals) cleaned.getOrNull(lastSeparator) else null
    // Two decimal separators (for example "12.3.4") are a typo, not a number.
    if (decimalChar != null && cleaned.count { it == decimalChar } > 1) return null

    val normalized = buildString {
        cleaned.forEachIndexed { index, char ->
            when {
                char.isDigit() -> append(char)
                index == lastSeparator && char == decimalChar -> append('.')
            }
        }
    }
    if (normalized.isEmpty() || normalized == ".") return null
    return normalized
}
