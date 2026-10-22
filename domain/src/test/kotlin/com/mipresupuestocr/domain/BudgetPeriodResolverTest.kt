package com.mipresupuestocr.domain

import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.BudgetPeriod
import com.mipresupuestocr.domain.model.PeriodType
import com.mipresupuestocr.domain.usecase.BudgetPeriodResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BudgetPeriodResolverTest {

    private val resolver = BudgetPeriodResolver()

    @Test
    fun `monthly period spans the calendar month`() {
        val period = resolver.resolve(PeriodType.MONTHLY, LocalDate.of(2026, 2, 15)).getOrNull()!!

        assertEquals(LocalDate.of(2026, 2, 1), period.start)
        assertEquals(LocalDate.of(2026, 2, 28), period.endInclusive)
        assertEquals(28, period.dayCount)
    }

    @Test
    fun `monthly period handles a leap february`() {
        val period = resolver.resolve(PeriodType.MONTHLY, LocalDate.of(2028, 2, 10)).getOrNull()!!
        assertEquals(LocalDate.of(2028, 2, 29), period.endInclusive)
    }

    @Test
    fun `biweekly period is anchored on the payday`() {
        val anchor = LocalDate.of(2026, 1, 2)
        val period = resolver.resolve(PeriodType.BIWEEKLY, LocalDate.of(2026, 1, 20), anchor).getOrNull()!!

        assertEquals(LocalDate.of(2026, 1, 16), period.start)
        assertEquals(LocalDate.of(2026, 1, 29), period.endInclusive)
    }

    @Test
    fun `biweekly period works before the anchor date`() {
        val anchor = LocalDate.of(2026, 1, 16)
        val period = resolver.resolve(PeriodType.BIWEEKLY, LocalDate.of(2026, 1, 2), anchor).getOrNull()!!

        assertEquals(LocalDate.of(2026, 1, 2), period.start)
        assertEquals(LocalDate.of(2026, 1, 15), period.endInclusive)
    }

    @Test
    fun `biweekly period requires a payday anchor`() {
        val result = resolver.resolve(PeriodType.BIWEEKLY, LocalDate.of(2026, 1, 20), null)
        assertEquals(AppError.MissingPaydayAnchor, result.errorOrNull())
    }

    @Test
    fun `period membership includes both ends`() {
        val period = BudgetPeriod(PeriodType.MONTHLY, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31))
        assertTrue(period.contains(LocalDate.of(2026, 3, 1)))
        assertTrue(period.contains(LocalDate.of(2026, 3, 31)))
        assertFalse(period.contains(LocalDate.of(2026, 4, 1)))
    }

    @Test
    fun `navigation moves monthly and biweekly periods`() {
        val monthly = resolver.resolve(PeriodType.MONTHLY, LocalDate.of(2026, 12, 5)).getOrNull()!!
        val january = resolver.next(monthly)
        assertEquals(LocalDate.of(2027, 1, 1), january.start)
        assertEquals(LocalDate.of(2026, 11, 1), resolver.previous(monthly).start)

        val biweekly = BudgetPeriod(PeriodType.BIWEEKLY, LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 15))
        assertEquals(LocalDate.of(2026, 1, 16), resolver.next(biweekly).start)
        assertEquals(LocalDate.of(2025, 12, 19), resolver.previous(biweekly).start)
    }
}
