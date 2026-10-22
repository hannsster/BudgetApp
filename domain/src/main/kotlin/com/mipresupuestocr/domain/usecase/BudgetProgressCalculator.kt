package com.mipresupuestocr.domain.usecase

import com.mipresupuestocr.domain.model.BalanceCorrection
import com.mipresupuestocr.domain.model.BudgetPeriod
import com.mipresupuestocr.domain.model.BudgetSummary
import com.mipresupuestocr.domain.model.Category
import com.mipresupuestocr.domain.model.CategoryBucket
import com.mipresupuestocr.domain.model.CategoryProgress
import com.mipresupuestocr.domain.model.CorrectionKind
import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.Money
import com.mipresupuestocr.domain.model.Transaction
import com.mipresupuestocr.domain.model.TransactionType

/**
 * Aggregates the transactions inside a period into a budget summary.
 *
 * Amounts in currencies other than [currency] are ignored so a mixed-currency history can never
 * silently distort the colón totals. Hand corrections from [BalanceCorrection] are applied on top
 * of the entries: they move a figure to what the user knows is right, without inventing a
 * transaction, and every derived total (spent, remaining) is computed from the corrected figures so
 * the arithmetic still holds.
 */
class BudgetProgressCalculator {

    fun calculate(
        period: BudgetPeriod,
        transactions: List<Transaction>,
        categories: List<Category>,
        corrections: List<BalanceCorrection> = emptyList(),
        currency: CurrencyCode = CurrencyCode.CRC,
    ): BudgetSummary {
        val inPeriod = transactions.filter { it.amount.currency == currency && period.contains(it.date) }
        val relevantCorrections = corrections.filter {
            it.currency == currency && it.periodStart == period.start
        }

        val incomeDelta = relevantCorrections
            .filter { it.kind == CorrectionKind.INCOME }
            .sumOf { it.amountMinor }

        val spentDeltaByCategory = relevantCorrections
            .filter { it.kind == CorrectionKind.SPENT && it.categoryId != null }
            .groupBy { it.categoryId }
            .mapValues { (_, rows) -> rows.sumOf { it.amountMinor } }

        val unallocatedSpentDelta = relevantCorrections
            .filter { it.kind == CorrectionKind.SPENT && it.categoryId == null }
            .sumOf { it.amountMinor }
        val totalSpentDelta = spentDeltaByCategory.values.sum() + unallocatedSpentDelta

        val incomeMinor = inPeriod.filter { it.type == TransactionType.INCOME }.sumOf { it.amount.amountMinor } +
            incomeDelta
        val expenses = inPeriod.filter { it.type == TransactionType.EXPENSE }
        val spentMinor = expenses.sumOf { it.amount.amountMinor } + totalSpentDelta

        val expenseCategories = categories
            .filter { it.bucket != CategoryBucket.INCOME }
            .sortedWith(compareBy({ it.sortOrder }, { it.name.lowercase() }))

        val spentByCategory = expenses
            .groupBy { it.categoryId }
            .mapValues { (_, rows) -> rows.sumOf { it.amount.amountMinor } }

        val categoryProgress = expenseCategories.map { category ->
            val adjustment = spentDeltaByCategory[category.id] ?: 0L
            val spent = Money((spentByCategory[category.id] ?: 0L) + adjustment, currency)
            val target = category.budgetTarget?.takeIf { it.currency == currency && it.isPositive }
            CategoryProgress(
                category = category,
                spent = spent,
                target = target,
                progressFraction = target?.let { spent.amountMinor.toDouble() / it.amountMinor },
                remaining = target?.minus(spent),
                isAdjusted = adjustment != 0L,
            )
        }

        val knownCategoryIds = expenseCategories.map { it.id }.toSet()
        val uncategorizedMinor = expenses
            .filter { it.categoryId == null || it.categoryId !in knownCategoryIds }
            .sumOf { it.amount.amountMinor } + unallocatedSpentDelta

        val plannedMinor = categoryProgress.mapNotNull { it.target?.amountMinor }.sumOf { it }

        return BudgetSummary(
            period = period,
            income = Money(incomeMinor, currency),
            spent = Money(spentMinor, currency),
            remaining = Money(incomeMinor - spentMinor, currency),
            plannedExpenses = Money(plannedMinor, currency),
            categoryProgress = categoryProgress,
            uncategorizedSpent = Money(uncategorizedMinor, currency),
            incomeAdjusted = incomeDelta != 0L,
        )
    }
}
