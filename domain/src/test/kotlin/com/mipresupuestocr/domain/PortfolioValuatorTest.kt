package com.mipresupuestocr.domain

import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.ExchangeRate
import com.mipresupuestocr.domain.model.Holding
import com.mipresupuestocr.domain.model.Money
import com.mipresupuestocr.domain.model.Quote
import com.mipresupuestocr.domain.usecase.PortfolioValuator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class PortfolioValuatorTest {

    private val valuator = PortfolioValuator()
    private val asOf: Instant = Instant.parse("2026-10-22T20:00:00Z")
    private val rate = ExchangeRate(
        base = CurrencyCode.USD,
        quote = CurrencyCode.CRC,
        rate = BigDecimal("512.34"),
        asOf = asOf,
        source = "Test feed",
    )

    private fun quote(ticker: String, price: String) = Quote(
        ticker = ticker,
        priceUsd = BigDecimal(price),
        asOf = asOf,
        source = "Test feed",
    )

    private val vti = Holding(id = 1, ticker = "VTI", shares = BigDecimal("10"), averagePriceUsd = BigDecimal("250"))
    private val qqq = Holding(id = 2, ticker = "QQQ", shares = BigDecimal("5"), averagePriceUsd = BigDecimal("400"))

    @Test
    fun `values positions and totals in usd and crc`() {
        val valuation = valuator.valuate(
            holdings = listOf(vti, qqq),
            quotes = mapOf("VTI" to quote("VTI", "280"), "QQQ" to quote("QQQ", "420")),
            usdCrcRate = rate,
        )

        assertEquals(Money.ofMajor("4900.00", CurrencyCode.USD), valuation.totalUsd)
        assertEquals(Money.ofMajor("2510466.00", CurrencyCode.CRC), valuation.totalCrc)
        assertEquals(2, valuation.positions.size)

        val vtiPosition = valuation.positions.first { it.ticker == "VTI" }
        assertEquals(Money.ofMajor("2800.00", CurrencyCode.USD), vtiPosition.marketValueUsd)
        assertEquals(Money.ofMajor("2500.00", CurrencyCode.USD), vtiPosition.costBasisUsd)
        assertEquals(Money.ofMajor("300.00", CurrencyCode.USD), vtiPosition.unrealizedPnlUsd)
        assertEquals(12.0, vtiPosition.unrealizedPnlPercent!!, 0.001)
        assertEquals(57.14, vtiPosition.allocationPercent, 0.001)
    }

    @Test
    fun `computes totals for cost and unrealized gain`() {
        val valuation = valuator.valuate(
            holdings = listOf(vti, qqq),
            quotes = mapOf("VTI" to quote("VTI", "280"), "QQQ" to quote("QQQ", "420")),
            usdCrcRate = rate,
        )

        assertEquals(Money.ofMajor("4500.00", CurrencyCode.USD), valuation.totalCostUsd)
        assertEquals(Money.ofMajor("400.00", CurrencyCode.USD), valuation.totalUnrealizedPnlUsd)
        assertEquals(8.89, valuation.totalUnrealizedPnlPercent!!, 0.001)
    }

    @Test
    fun `reports holdings without quotes and excludes them from totals`() {
        val spy = Holding(id = 3, ticker = "SPY", shares = BigDecimal("2"))
        val valuation = valuator.valuate(
            holdings = listOf(vti, spy),
            quotes = mapOf("VTI" to quote("VTI", "280")),
            usdCrcRate = rate,
        )

        assertEquals(listOf("SPY"), valuation.unpricedTickers)
        assertEquals(Money.ofMajor("2800.00", CurrencyCode.USD), valuation.totalUsd)
        assertEquals(1, valuation.positions.size)
        assertEquals(100.0, valuation.positions.single().allocationPercent, 0.001)
    }

    @Test
    fun `omits crc totals when no rate is available`() {
        val valuation = valuator.valuate(
            holdings = listOf(vti),
            quotes = mapOf("VTI" to quote("VTI", "280")),
            usdCrcRate = null,
        )

        assertNull(valuation.totalCrc)
        assertNull(valuation.positions.single().marketValueCrc)
        assertEquals(Money.ofMajor("2800.00", CurrencyCode.USD), valuation.totalUsd)
    }

    @Test
    fun `empty holdings produce an empty valuation`() {
        val valuation = valuator.valuate(emptyList(), emptyMap(), rate)

        assertTrue(valuation.isEmpty)
        assertTrue(valuation.positions.isEmpty())
        assertEquals(Money.zero(CurrencyCode.USD), valuation.totalUsd)
        assertEquals(Money.zero(CurrencyCode.CRC), valuation.totalCrc)
        assertNull(valuation.totalUnrealizedPnlUsd)
    }

    @Test
    fun `total gain is withheld when a position has no cost basis`() {
        val noCost = Holding(id = 4, ticker = "VOO", shares = BigDecimal("1"))
        val valuation = valuator.valuate(
            holdings = listOf(vti, noCost),
            quotes = mapOf(
                "VTI" to quote("VTI", "280"),
                "VOO" to quote("VOO", "500"),
            ),
            usdCrcRate = rate,
        )

        assertNull(valuation.totalCostUsd)
        assertNull(valuation.totalUnrealizedPnlUsd)
        assertNull(valuation.positions.first { it.ticker == "VOO" }.unrealizedPnlUsd)
    }

    @Test
    fun `ticker lookup is case and whitespace insensitive`() {
        val lowerCase = Holding(id = 5, ticker = " vti ", shares = BigDecimal("1"))
        val valuation = valuator.valuate(
            holdings = listOf(lowerCase),
            quotes = mapOf("VTI" to quote("VTI", "280")),
            usdCrcRate = rate,
        )

        assertEquals("VTI", valuation.positions.single().ticker)
        assertEquals(Money.ofMajor("280.00", CurrencyCode.USD), valuation.totalUsd)
    }
}
