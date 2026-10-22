package com.mipresupuestocr.domain

import com.mipresupuestocr.domain.model.BudgetPeriod
import com.mipresupuestocr.domain.model.Category
import com.mipresupuestocr.domain.model.CategoryBucket
import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.Money
import com.mipresupuestocr.domain.model.PeriodType
import com.mipresupuestocr.domain.model.Transaction
import com.mipresupuestocr.domain.model.TransactionType
import com.mipresupuestocr.domain.usecase.BudgetProgressCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BudgetProgressCalculatorTest {

    private val calculator = BudgetProgressCalculator()
    private val period = BudgetPeriod(
        PeriodType.MONTHLY,
        LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 1, 31),
    )

    private val rent = Category(
        id = 1,
        name = "Alquiler",
        bucket = CategoryBucket.NEEDS,
        budgetTarget = Money.ofMajor("300000.00", CurrencyCode.CRC),
        sortOrder = 0,
    )
    private val groceries = Category(
        id = 2,
        name = "Supermercado",
        bucket = CategoryBucket.NEEDS,
        budgetTarget = null,
        sortOrder = 1,
    )

    private fun tx(
        type: TransactionType,
        amount: String,
        categoryId: Long?,
        day: Int = 10,
        currency: CurrencyCode = CurrencyCode.CRC,
    ) = Transaction(
        type = type,
        amount = Money.ofMajor(amount, currency),
        categoryId = categoryId,
        date = LocalDate.of(2026, 1, day),
    )

    @Test
    fun `sums income spent and remaining for the period`() {
        val summary = calculator.calculate(
            period = period,
            transactions = listOf(
                tx(TransactionType.INCOME, "1000000.00", null, day = 1),
                tx(TransactionType.EXPENSE, "250000.00", rent.id),
                tx(TransactionType.EXPENSE, "50000.00", groceries.id),
            ),
            categories = listOf(rent, groceries),
        )

        assertEquals(Money.ofMajor("1000000.00", CurrencyCode.CRC), summary.income)
        assertEquals(Money.ofMajor("300000.00", CurrencyCode.CRC), summary.spent)
        assertEquals(Money.ofMajor("700000.00", CurrencyCode.CRC), summary.remaining)
        assertEquals(Money.ofMajor("300000.00", CurrencyCode.CRC), summary.plannedExpenses)
    }

    @Test
    fun `computes per category progress and remaining target`() {
        val summary = calculator.calculate(
            period = period,
            transactions = listOf(tx(TransactionType.EXPENSE, "250000.00", rent.id)),
            categories = listOf(rent, groceries),
        )

        val rentProgress = summary.categoryProgress.first { it.category.id == rent.id }
        assertEquals(0.8333, rentProgress.progressFraction!!, 0.0001)
        assertEquals(Money.ofMajor("50000.00", CurrencyCode.CRC), rentProgress.remaining)
        assertFalse(rentProgress.isOverBudget)

        val groceriesProgress = summary.categoryProgress.first { it.category.id == groceries.id }
        assertNull(groceriesProgress.progressFraction)
        assertNull(groceriesProgress.remaining)
        assertTrue(groceriesProgress.spent.isZero)
    }

    @Test
    fun `flags a category that exceeded its target`() {
        val summary = calculator.calculate(
            period = period,
            transactions = listOf(tx(TransactionType.EXPENSE, "350000.00", rent.id)),
            categories = listOf(rent),
        )

        val progress = summary.categoryProgress.single()
        assertTrue(progress.isOverBudget)
        assertEquals(Money.ofMajor("-50000.00", CurrencyCode.CRC), progress.remaining)
    }

    @Test
    fun `ignores transactions outside the period and other currencies`() {
        val summary = calculator.calculate(
            period = period,
            transactions = listOf(
                tx(TransactionType.EXPENSE, "250000.00", rent.id),
                tx(TransactionType.EXPENSE, "90000.00", rent.id, day = 1).copy(date = LocalDate.of(2025, 12, 31)),
                tx(TransactionType.EXPENSE, "100.00", rent.id, currency = CurrencyCode.USD),
            ),
            categories = listOf(rent),
        )

        assertEquals(Money.ofMajor("250000.00", CurrencyCode.CRC), summary.spent)
    }

    @Test
    fun `reports uncategorized spending separately`() {
        val summary = calculator.calculate(
            period = period,
            transactions = listOf(
                tx(TransactionType.EXPENSE, "15000.00", null),
                tx(TransactionType.EXPENSE, "5000.00", 99),
            ),
            categories = listOf(rent),
        )

        assertEquals(Money.ofMajor("20000.00", CurrencyCode.CRC), summary.uncategorizedSpent)
        assertEquals(Money.ofMajor("20000.00", CurrencyCode.CRC), summary.spent)
    }

    @Test
    fun `empty period is reported as empty`() {
        val summary = calculator.calculate(period, emptyList(), listOf(groceries))
        assertTrue(summary.isEmpty)
    }

    @Test
    fun `a configured target means the budget is not empty`() {
        val summary = calculator.calculate(period, emptyList(), listOf(rent))
        assertFalse(summary.isEmpty)
    }
}
