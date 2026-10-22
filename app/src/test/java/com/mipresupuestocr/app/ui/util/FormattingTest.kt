package com.mipresupuestocr.app.ui.util

import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class FormattingTest {

    @Test
    fun `parses plain integers`() {
        assertEquals(BigDecimal("350000.00"), parseAmount("350000"))
    }

    @Test
    fun `ignores grouping spaces`() {
        assertEquals(BigDecimal("350000.00"), parseAmount("350 000"))
    }

    @Test
    fun `parses costa rican grouping and decimals`() {
        assertEquals(BigDecimal("1234.56"), parseAmount("1.234,56"))
    }

    @Test
    fun `parses dot decimals`() {
        assertEquals(BigDecimal("1234.56"), parseAmount("1234.56"))
    }

    @Test
    fun `parses a single decimal separator`() {
        assertEquals(BigDecimal("12.50"), parseAmount("12,5"))
    }

    @Test
    fun `treats three trailing digits as thousands`() {
        assertEquals(BigDecimal("1234.00"), parseAmount("1.234"))
    }

    @Test
    fun `rejects letters and empty input`() {
        assertNull(parseAmount("abc"))
        assertNull(parseAmount(""))
        assertNull(parseAmount("12.3.4"))
    }

    @Test
    fun `parses decimals for shares and prices`() {
        assertEquals(BigDecimal("3.5"), parseDecimal("3,5"))
        assertEquals(BigDecimal("0.0001").stripTrailingZeros(), parseDecimal("0.0001")?.stripTrailingZeros())
        assertNull(parseDecimal("-5"))
    }

    @Test
    fun `prices always show two decimals`() {
        // NumberFormat rounds half-even, which is what currency formatting does everywhere.
        assertEquals("280.12", MoneyFormat.formatPrice(BigDecimal("280.125")))
        assertEquals("280.13", MoneyFormat.formatPrice(BigDecimal("280.126")))
        assertEquals("3.50", MoneyFormat.formatPrice(BigDecimal("3.50")))
        assertEquals("1,234.00", MoneyFormat.formatPrice(BigDecimal("1234")))
    }

    @Test
    fun `exchange rates show two decimals with colón conventions`() {
        assertEquals("520,12", MoneyFormat.formatRate(BigDecimal("520.1234")))
        assertEquals("500,00", MoneyFormat.formatRate(BigDecimal("500")))
    }

    @Test
    fun `share counts keep at most two decimals`() {
        assertEquals("1.5", MoneyFormat.formatShares(BigDecimal("1.5")))
        assertEquals("3.46", MoneyFormat.formatShares(BigDecimal("3.4567")))
        assertEquals("4", MoneyFormat.formatShares(BigDecimal("4.00")))
    }

    @Test
    fun `money amounts use two decimals in both currencies`() {
        val crc = MoneyFormat.format(Money.ofMajor("1500.50", CurrencyCode.CRC))
        assertTrue(crc, crc.endsWith("50"))
        val usd = MoneyFormat.format(Money.ofMajor("280.10", CurrencyCode.USD))
        assertTrue(usd, usd.endsWith("10"))
    }

    @Test
    fun `percent input is limited to zero through one hundred`() {
        assertEquals(50, parsePercent("50"))
        assertNull(parsePercent("101"))
        assertNull(parsePercent("fifty"))
    }
}
