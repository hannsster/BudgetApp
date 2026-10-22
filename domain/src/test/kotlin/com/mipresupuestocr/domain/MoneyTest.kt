package com.mipresupuestocr.domain

import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class MoneyTest {

    @Test
    fun `adds amounts in the same currency`() {
        val sum = Money.ofMajor("1500.25", CurrencyCode.CRC) + Money.ofMajor("499.75", CurrencyCode.CRC)
        assertEquals(Money.ofMajor("2000.00", CurrencyCode.CRC), sum)
    }

    @Test
    fun `rounds half up when building from a decimal`() {
        assertEquals(1L, Money.of(BigDecimal("0.005"), CurrencyCode.CRC).amountMinor)
        assertEquals(0L, Money.of(BigDecimal("0.004"), CurrencyCode.CRC).amountMinor)
    }

    @Test
    fun `percentages round to the minor unit`() {
        val income = Money.ofMajor("1000.01", CurrencyCode.CRC)
        assertEquals(Money.ofMajor("500.01", CurrencyCode.CRC), income.percent(50))
        assertEquals(Money.ofMajor("300.00", CurrencyCode.CRC), income.percent(30))
    }

    @Test
    fun `multiplies with a decimal factor`() {
        val value = Money.ofMajor("100.00", CurrencyCode.CRC).times(BigDecimal("2.5"))
        assertEquals(Money.ofMajor("250.00", CurrencyCode.CRC), value)
    }

    @Test
    fun `converts to a decimal in major units`() {
        assertEquals(BigDecimal("1234.56"), Money.ofMajor("1234.56", CurrencyCode.CRC).asDecimal)
    }

    @Test
    fun `compares and clamps negative amounts`() {
        val a = Money.ofMajor("10.00", CurrencyCode.CRC)
        val b = Money.ofMajor("20.00", CurrencyCode.CRC)
        assertTrue(a < b)
        assertTrue((-a).isNegative)
        assertTrue((-a).coerceAtLeastZero().isZero)
    }
}
