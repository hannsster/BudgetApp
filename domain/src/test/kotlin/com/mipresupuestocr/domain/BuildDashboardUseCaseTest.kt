package com.mipresupuestocr.domain

import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.Category
import com.mipresupuestocr.domain.model.CategoryBucket
import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.ExchangeRate
import com.mipresupuestocr.domain.model.Holding
import com.mipresupuestocr.domain.model.Money
import com.mipresupuestocr.domain.model.PeriodType
import com.mipresupuestocr.domain.model.Quote
import com.mipresupuestocr.domain.model.Transaction
import com.mipresupuestocr.domain.model.TransactionType
import com.mipresupuestocr.domain.model.UserSettings
import com.mipresupuestocr.domain.usecase.BuildDashboardUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class BuildDashboardUseCaseTest {

    private val useCase = BuildDashboardUseCase()
    private val now: Instant = Instant.parse("2026-10-22T20:00:00Z")

    private val groceries = Category(
        id = 1,
        name = "Supermercado",
        bucket = CategoryBucket.NEEDS,
        budgetTarget = Money.ofMajor("200000.00", CurrencyCode.CRC),
    )

    private val transactions = listOf(
        Transaction(
            type = TransactionType.INCOME,
            amount = Money.ofMajor("900000.00", CurrencyCode.CRC),
            date = LocalDate.of(2026, 10, 1),
        ),
        Transaction(
            type = TransactionType.EXPENSE,
            amount = Money.ofMajor("150000.00", CurrencyCode.CRC),
            categoryId = 1,
            date = LocalDate.of(2026, 10, 5),
        ),
    )

    private val holdings = listOf(Holding(id = 1, ticker = "VTI", shares = BigDecimal("2")))
    private val quotes = mapOf(
        "VTI" to Quote("VTI", BigDecimal("280"), now.minusSeconds(600), "Test feed"),
    )
    private val rate = ExchangeRate(
        base = CurrencyCode.USD,
        quote = CurrencyCode.CRC,
        rate = BigDecimal("512.34"),
        asOf = now.minusSeconds(3600),
        source = "Test feed",
    )

    @Test
    fun `builds a monthly dashboard snapshot`() {
        val snapshot = useCase.build(
            settings = UserSettings(periodType = PeriodType.MONTHLY),
            referenceDate = LocalDate.of(2026, 10, 22),
            now = now,
            transactions = transactions,
            categories = listOf(groceries),
            holdings = holdings,
            quotes = quotes,
            rate = rate,
        ).valueOrThrow()

        assertEquals(LocalDate.of(2026, 10, 1), snapshot.period.start)
        assertEquals(Money.ofMajor("750000.00", CurrencyCode.CRC), snapshot.budget.remaining)
        assertEquals(Money.ofMajor("560.00", CurrencyCode.USD), snapshot.portfolio.totalUsd)
        assertEquals(Money.ofMajor("286910.40", CurrencyCode.CRC), snapshot.portfolio.totalCrc)
        assertFalse(snapshot.rateIsStale)
        assertEquals(now.minusSeconds(600), snapshot.lastQuoteRefresh)
    }

    @Test
    fun `flags a reference rate older than two days`() {
        val staleRate = rate.copy(asOf = now.minusSeconds(60L * 60L * 72L))

        val snapshot = useCase.build(
            settings = UserSettings(),
            referenceDate = LocalDate.of(2026, 10, 22),
            now = now,
            transactions = emptyList(),
            categories = emptyList(),
            holdings = emptyList(),
            quotes = emptyMap(),
            rate = staleRate,
        ).valueOrThrow()

        assertTrue(snapshot.rateIsStale)
    }

    @Test
    fun `biweekly settings without an anchor fail`() {
        val result = useCase.build(
            settings = UserSettings(periodType = PeriodType.BIWEEKLY, paydayAnchor = null),
            referenceDate = LocalDate.of(2026, 10, 22),
            now = now,
            transactions = emptyList(),
            categories = emptyList(),
            holdings = emptyList(),
            quotes = emptyMap(),
            rate = null,
        )

        assertEquals(AppError.MissingPaydayAnchor, result.errorOrNull())
    }

    @Test
    fun `biweekly settings anchor on the configured payday`() {
        val snapshot = useCase.build(
            settings = UserSettings(
                periodType = PeriodType.BIWEEKLY,
                paydayAnchor = LocalDate.of(2026, 10, 2),
            ),
            referenceDate = LocalDate.of(2026, 10, 22),
            now = now,
            transactions = transactions,
            categories = listOf(groceries),
            holdings = emptyList(),
            quotes = emptyMap(),
            rate = rate,
        ).valueOrThrow()

        assertEquals(LocalDate.of(2026, 10, 16), snapshot.period.start)
        assertEquals(LocalDate.of(2026, 10, 29), snapshot.period.endInclusive)
    }
}
