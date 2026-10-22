package com.mipresupuestocr.domain.usecase

import com.mipresupuestocr.domain.model.BudgetPeriod
import com.mipresupuestocr.domain.model.BudgetSummary
import com.mipresupuestocr.domain.model.Category
import com.mipresupuestocr.domain.model.CategoryBucket
import com.mipresupuestocr.domain.model.CategoryProgress
import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.Money
import com.mipresupuestocr.domain.model.Transaction
import com.mipresupuestocr.domain.model.TransactionType

/**
 * Aggregates the transactions inside a period into a budget summary.
 *
 * Amounts in currencies other than [currency] are ignored so a mixed-currency history can never
 * silently distort the colón totals.
 */
class BudgetProgressCalculator {

    fun calculate(
        period: BudgetPeriod,
        transactions: List<Transaction>,
        categories: List<Category>,
        currency: CurrencyCode = CurrencyCode.CRC,
    ): BudgetSummary {
        val inPeriod = transactions.filter { it.amount.currency == currency && period.contains(it.date) }
        val incomeMinor = inPeriod.filter { it.type == TransactionType.INCOME }.sumOf { it.amount.amountMinor }
        val expenses = inPeriod.filter { it.type == TransactionType.EXPENSE }
        val spentMinor = expenses.sumOf { it.amount.amountMinor }

        val expenseCategories = categories
            .filter { it.bucket != CategoryBucket.INCOME }
            .sortedWith(compareBy({ it.sortOrder }, { it.name.lowercase() }))

        val spentByCategory = expenses
            .groupBy { it.categoryId }
            .mapValues { (_, rows) -> rows.sumOf { it.amount.amountMinor } }

        val categoryProgress = expenseCategories.map { category ->
            val spent = Money(spentByCategory[category.id] ?: 0L, currency)
            val target = category.budgetTarget?.takeIf { it.currency == currency && it.isPositive }
            CategoryProgress(
                category = category,
                spent = spent,
                target = target,
                progressFraction = target?.let { spent.amountMinor.toDouble() / it.amountMinor },
                remaining = target?.minus(spent),
            )
        }

        val knownCategoryIds = expenseCategories.map { it.id }.toSet()
        val uncategorizedMinor = expenses
            .filter { it.categoryId == null || it.categoryId !in knownCategoryIds }
            .sumOf { it.amount.amountMinor }

        val plannedMinor = categoryProgress.mapNotNull { it.target?.amountMinor }.sumOf { it }

        return BudgetSummary(
            period = period,
            income = Money(incomeMinor, currency),
            spent = Money(spentMinor, currency),
            remaining = Money(incomeMinor - spentMinor, currency),
            plannedExpenses = Money(plannedMinor, currency),
            categoryProgress = categoryProgress,
            uncategorizedSpent = Money(uncategorizedMinor, currency),
        )
    }
}
