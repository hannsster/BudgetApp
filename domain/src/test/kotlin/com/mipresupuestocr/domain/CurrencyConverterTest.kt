package com.mipresupuestocr.domain

import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.ExchangeRate
import com.mipresupuestocr.domain.model.Money
import com.mipresupuestocr.domain.usecase.CurrencyConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class CurrencyConverterTest {

    private val converter = CurrencyConverter()
    private val asOf: Instant = Instant.parse("2026-10-22T12:00:00Z")

    private fun rate(
        base: CurrencyCode = CurrencyCode.USD,
        quote: CurrencyCode = CurrencyCode.CRC,
        value: String = "512.34",
    ) = ExchangeRate(
        base = base,
        quote = quote,
        rate = BigDecimal(value),
        asOf = asOf,
        source = "Test",
    )

    @Test
    fun `converts usd to crc`() {
        val crc = converter.convert(Money.ofMajor("100.00", CurrencyCode.USD), CurrencyCode.CRC, rate())
        assertEquals(Money.ofMajor("51234.00", CurrencyCode.CRC), crc.getOrNull())
    }

    @Test
    fun `converts crc back to usd`() {
        val usd = converter.convert(Money.ofMajor("51234.00", CurrencyCode.CRC), CurrencyCode.USD, rate())
        assertEquals(Money.ofMajor("100.00", CurrencyCode.USD), usd.getOrNull())
    }

    @Test
    fun `rounds converted amounts to the target minor unit`() {
        val crc = converter.convert(
            Money.ofMajor("1.00", CurrencyCode.USD),
            CurrencyCode.CRC,
            rate(value = "512.345"),
        )
        assertEquals(Money.ofMajor("512.35", CurrencyCode.CRC), crc.getOrNull())
    }

    @Test
    fun `returns the amount unchanged when currencies match`() {
        val amount = Money.ofMajor("250.00", CurrencyCode.CRC)
        assertEquals(amount, converter.convert(amount, CurrencyCode.CRC, rate()).getOrNull())
    }

    @Test
    fun `rejects a non positive rate`() {
        val result = converter.convert(
            Money.ofMajor("10.00", CurrencyCode.USD),
            CurrencyCode.CRC,
            rate(value = "0"),
        )
        assertTrue(result is com.mipresupuestocr.domain.model.AppResult.Failure)
        assertEquals(AppError.InvalidRate, result.errorOrNull())
    }

    @Test
    fun `rejects a currency the rate does not describe`() {
        val mismatched = ExchangeRate(
            base = CurrencyCode.USD,
            quote = CurrencyCode.USD,
            rate = BigDecimal("1.0"),
            asOf = asOf,
            source = "Test",
        )
        val result = converter.convert(Money.ofMajor("10.00", CurrencyCode.CRC), CurrencyCode.USD, mismatched)
        assertEquals(AppError.UnsupportedCurrency, result.errorOrNull())
    }
}
