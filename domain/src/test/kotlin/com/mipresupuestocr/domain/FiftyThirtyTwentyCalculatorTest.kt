package com.mipresupuestocr.domain

import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.AppResult
import com.mipresupuestocr.domain.model.BudgetSplit
import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.IncomeFrequency
import com.mipresupuestocr.domain.model.Money
import com.mipresupuestocr.domain.usecase.FiftyThirtyTwentyCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FiftyThirtyTwentyCalculatorTest {

    private val calculator = FiftyThirtyTwentyCalculator()
    private val crc = CurrencyCode.CRC

    @Test
    fun `splits monthly income into 50 30 20`() {
        val result = calculator.calculate(Money.ofMajor("1000000.00", crc)).valueOrThrow()

        assertEquals(Money.ofMajor("500000.00", crc), result.allocationPerEvent.needs)
        assertEquals(Money.ofMajor("300000.00", crc), result.allocationPerEvent.wants)
        assertEquals(Money.ofMajor("200000.00", crc), result.allocationPerEvent.savings)
        assertEquals(Money.ofMajor("1000000.00", crc), result.monthlyIncome)
    }

    @Test
    fun `normalises biweekly pay to a monthly figure`() {
        val result = calculator.calculate(
            income = Money.ofMajor("350000.00", crc),
            frequency = IncomeFrequency.BIWEEKLY,
        ).valueOrThrow()

        // 350 000 x 26 / 12 = 758 333.33
        assertEquals(Money.ofMajor("758333.33", crc), result.monthlyIncome)
        assertEquals(Money.ofMajor("175000.00", crc), result.allocationPerEvent.needs)
        assertEquals(Money.ofMajor("379166.67", crc), result.monthlyAllocation.needs)
    }

    @Test
    fun `normalises weekly pay to a monthly figure`() {
        val result = calculator.calculate(
            income = Money.ofMajor("100000.00", crc),
            frequency = IncomeFrequency.WEEKLY,
        ).valueOrThrow()

        // 100 000 x 52 / 12 = 433 333.33
        assertEquals(Money.ofMajor("433333.33", crc), result.monthlyIncome)
    }

    @Test
    fun `rounding remainder keeps the buckets equal to income`() {
        val income = Money.ofMajor("1000.01", crc)
        val result = calculator.calculate(income).valueOrThrow()

        assertEquals(income, result.allocationPerEvent.total)
        assertEquals(Money.ofMajor("200.00", crc), result.allocationPerEvent.savings)
    }

    @Test
    fun `supports a custom split that totals one hundred`() {
        val result = calculator.calculate(
            income = Money.ofMajor("1000.00", crc),
            split = BudgetSplit(needs = 60, wants = 20, savings = 20),
        ).valueOrThrow()

        assertEquals(Money.ofMajor("600.00", crc), result.allocationPerEvent.needs)
        assertEquals(Money.ofMajor("200.00", crc), result.allocationPerEvent.wants)
        assertEquals(Money.ofMajor("200.00", crc), result.allocationPerEvent.savings)
    }

    @Test
    fun `rejects a split that does not total one hundred`() {
        val result = calculator.calculate(
            income = Money.ofMajor("1000.00", crc),
            split = BudgetSplit(needs = 50, wants = 30, savings = 30),
        )

        assertTrue(result is AppResult.Failure)
        assertEquals(AppError.InvalidSplit, result.errorOrNull())
    }

    @Test
    fun `rejects zero and negative income`() {
        assertEquals(AppError.InvalidIncome, calculator.calculate(Money.zero(crc)).errorOrNull())
        assertEquals(
            AppError.InvalidIncome,
            calculator.calculate(Money.ofMajor("-10.00", crc)).errorOrNull(),
        )
    }

}
