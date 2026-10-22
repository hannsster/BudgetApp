package com.mipresupuestocr.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `percent input is limited to zero through one hundred`() {
        assertEquals(50, parsePercent("50"))
        assertNull(parsePercent("101"))
        assertNull(parsePercent("fifty"))
    }
}
