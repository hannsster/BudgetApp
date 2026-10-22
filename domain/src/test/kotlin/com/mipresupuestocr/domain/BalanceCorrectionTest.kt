package com.mipresupuestocr.domain

import com.mipresupuestocr.domain.model.BalanceCorrection
import com.mipresupuestocr.domain.model.BudgetPeriod
import com.mipresupuestocr.domain.model.Category
import com.mipresupuestocr.domain.model.CategoryBucket
import com.mipresupuestocr.domain.model.CorrectionKind
import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.Money
import com.mipresupuestocr.domain.model.PeriodType
import com.mipresupuestocr.domain.model.Transaction
import com.mipresupuestocr.domain.model.TransactionType
import com.mipresupuestocr.domain.usecase.BudgetProgressCalculator
import com.mipresupuestocr.domain.usecase.ClearBalanceCorrectionUseCase
import com.mipresupuestocr.domain.usecase.SetAvailableBalanceUseCase
import com.mipresupuestocr.domain.usecase.SetCategoryBalanceUseCase
import com.mipresupuestocr.domain.usecase.SetIncomeBalanceUseCase
import com.mipresupuestocr.domain.usecase.SetSpentBalanceUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * A clerical error has to be fixable by typing the right total, without a confusing extra entry and
 * without breaking the arithmetic of the period.
 */
class BalanceCorrectionTest {

    private val period = BudgetPeriod(
        type = PeriodType.MONTHLY,
        start = LocalDate.of(2026, 10, 1),
        endInclusive = LocalDate.of(2026, 10, 31),
    )
    private val groceries = Category(id = 1, name = "Supermercado", bucket = CategoryBucket.NEEDS, sortOrder = 1)
    private val dining = Category(id = 2, name = "Restaurantes", bucket = CategoryBucket.WANTS, sortOrder = 2)
    private val categories = listOf(groceries, dining)

    private val salary = Transaction(
        id = 1,
        type = TransactionType.INCOME,
        amount = Money.ofMajor("500000.00", CurrencyCode.CRC),
        date = LocalDate.of(2026, 10, 1),
    )
    private val groceriesEntry = Transaction(
        id = 2,
        type = TransactionType.EXPENSE,
        amount = Money.ofMajor("50000.00", CurrencyCode.CRC),
        categoryId = groceries.id,
        date = LocalDate.of(2026, 10, 3),
    )
    private val diningEntry = Transaction(
        id = 3,
        type = TransactionType.EXPENSE,
        amount = Money.ofMajor("20000.00", CurrencyCode.CRC),
        categoryId = dining.id,
        date = LocalDate.of(2026, 10, 4),
    )
    private val entries = listOf(salary, groceriesEntry, diningEntry)
    private val calculator = BudgetProgressCalculator()

    private fun summary(corrections: List<BalanceCorrection>) =
        calculator.calculate(period, entries, categories, corrections)

    @Test
    fun `without corrections the summary is plain sums`() {
        val summary = summary(emptyList())

        assertEquals(Money.ofMajor("500000.00", CurrencyCode.CRC), summary.income)
        assertEquals(Money.ofMajor("70000.00", CurrencyCode.CRC), summary.spent)
        assertEquals(Money.ofMajor("430000.00", CurrencyCode.CRC), summary.remaining)
        assertFalse(summary.incomeAdjusted)
        assertFalse(summary.categoryProgress.first { it.category.id == groceries.id }.isAdjusted)
    }

    @Test
    fun `income correction moves income and remaining together`() {
        val correction = BalanceCorrection(
            periodStart = period.start,
            kind = CorrectionKind.INCOME,
            amountMinor = 100_000_00L,
        )

        val summary = summary(listOf(correction))

        assertEquals(Money.ofMajor("600000.00", CurrencyCode.CRC), summary.income)
        assertEquals(Money.ofMajor("70000.00", CurrencyCode.CRC), summary.spent)
        assertEquals(Money.ofMajor("530000.00", CurrencyCode.CRC), summary.remaining)
        assertTrue(summary.incomeAdjusted)
    }

    @Test
    fun `category correction changes that category and the total spent`() {
        val correction = BalanceCorrection(
            periodStart = period.start,
            kind = CorrectionKind.SPENT,
            categoryId = groceries.id,
            amountMinor = -20_000_00L,
        )

        val summary = summary(listOf(correction))
        val groceriesProgress = summary.categoryProgress.first { it.category.id == groceries.id }

        assertEquals(Money.ofMajor("30000.00", CurrencyCode.CRC), groceriesProgress.spent)
        assertTrue(groceriesProgress.isAdjusted)
        assertEquals(Money.ofMajor("50000.00", CurrencyCode.CRC), summary.spent)
        assertEquals(Money.ofMajor("450000.00", CurrencyCode.CRC), summary.remaining)
    }

    @Test
    fun `unallocated correction lands in the uncategorised bucket`() {
        val correction = BalanceCorrection(
            periodStart = period.start,
            kind = CorrectionKind.SPENT,
            categoryId = null,
            amountMinor = 5_000_00L,
        )

        val summary = summary(listOf(correction))

        assertEquals(Money.ofMajor("5000.00", CurrencyCode.CRC), summary.uncategorizedSpent)
        assertEquals(Money.ofMajor("75000.00", CurrencyCode.CRC), summary.spent)
    }

    @Test
    fun `corrections from another period or currency are ignored`() {
        val otherPeriod = BalanceCorrection(
            periodStart = LocalDate.of(2026, 9, 1),
            kind = CorrectionKind.INCOME,
            amountMinor = 99_999_00L,
        )
        val otherCurrency = BalanceCorrection(
            periodStart = period.start,
            kind = CorrectionKind.INCOME,
            amountMinor = 12_345L,
            currency = CurrencyCode.USD,
        )

        val summary = summary(listOf(otherPeriod, otherCurrency))

        assertEquals(Money.ofMajor("500000.00", CurrencyCode.CRC), summary.income)
    }

    @Test
    fun `setting a category total stores only the difference`() = runTest {
        val transactions = FakeTransactionRepository(entries)
        val corrections = FakeBalanceCorrectionRepository()
        val useCase = SetCategoryBalanceUseCase(transactions, corrections)

        useCase(period, groceries.id, Money.ofMajor("32000.00", CurrencyCode.CRC)).valueOrThrow()

        val stored = corrections.getAll().single()
        assertEquals(CorrectionKind.SPENT, stored.kind)
        assertEquals(groceries.id, stored.categoryId)
        assertEquals(-18_000_00L, stored.amountMinor)
        // visible history is untouched
        assertEquals(3, transactions.getBetween(period.start, period.endInclusive).size)
    }

    @Test
    fun `setting the available figure makes remaining match exactly`() = runTest {
        val transactions = FakeTransactionRepository(entries)
        val corrections = FakeBalanceCorrectionRepository()
        val useCase = SetAvailableBalanceUseCase(transactions, corrections)

        useCase(period, Money.ofMajor("400000.00", CurrencyCode.CRC)).valueOrThrow()

        val summary = calculator.calculate(period, entries, categories, corrections.getAll())
        assertEquals(Money.ofMajor("400000.00", CurrencyCode.CRC), summary.remaining)
        assertTrue(summary.incomeAdjusted)
    }

    @Test
    fun `setting income total is exact even after an earlier correction`() = runTest {
        val transactions = FakeTransactionRepository(entries)
        val corrections = FakeBalanceCorrectionRepository()
        val useCase = SetIncomeBalanceUseCase(transactions, corrections)

        useCase(period, Money.ofMajor("450000.00", CurrencyCode.CRC)).valueOrThrow()
        useCase(period, Money.ofMajor("520000.00", CurrencyCode.CRC)).valueOrThrow()

        val summary = calculator.calculate(period, entries, categories, corrections.getAll())
        assertEquals(Money.ofMajor("520000.00", CurrencyCode.CRC), summary.income)
        assertEquals(1, corrections.getAll().size)
    }

    @Test
    fun `setting the spent total accounts for existing category corrections`() = runTest {
        val transactions = FakeTransactionRepository(entries)
        val corrections = FakeBalanceCorrectionRepository()
        SetCategoryBalanceUseCase(transactions, corrections)(
            period,
            groceries.id,
            Money.ofMajor("30000.00", CurrencyCode.CRC),
        ).valueOrThrow()

        SetSpentBalanceUseCase(transactions, corrections)(
            period,
            Money.ofMajor("80000.00", CurrencyCode.CRC),
        ).valueOrThrow()

        val summary = calculator.calculate(period, entries, categories, corrections.getAll())
        assertEquals(Money.ofMajor("80000.00", CurrencyCode.CRC), summary.spent)
    }

    @Test
    fun `clearing a correction returns the figure to the real sum`() = runTest {
        val transactions = FakeTransactionRepository(entries)
        val corrections = FakeBalanceCorrectionRepository()
        SetCategoryBalanceUseCase(transactions, corrections)(
            period,
            groceries.id,
            Money.ofMajor("30000.00", CurrencyCode.CRC),
        ).valueOrThrow()

        ClearBalanceCorrectionUseCase(corrections)(period, CorrectionKind.SPENT, groceries.id)

        val summary = calculator.calculate(period, entries, categories, corrections.getAll())
        val groceriesProgress = summary.categoryProgress.first { it.category.id == groceries.id }
        assertEquals(Money.ofMajor("50000.00", CurrencyCode.CRC), groceriesProgress.spent)
        assertFalse(groceriesProgress.isAdjusted)
    }

    @Test
    fun `setting a total equal to the real sum stores nothing`() = runTest {
        val transactions = FakeTransactionRepository(entries)
        val corrections = FakeBalanceCorrectionRepository()

        SetIncomeBalanceUseCase(transactions, corrections)(
            period,
            Money.ofMajor("500000.00", CurrencyCode.CRC),
        ).valueOrThrow()

        assertTrue(corrections.getAll().isEmpty())
    }
}
