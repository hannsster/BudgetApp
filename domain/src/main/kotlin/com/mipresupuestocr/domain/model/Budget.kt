package com.mipresupuestocr.domain.model

import java.time.Instant
import java.time.LocalDate

enum class TransactionType { INCOME, EXPENSE }

/** Where a category sits in the 50/30/20 guidance model. */
enum class CategoryBucket { NEEDS, WANTS, SAVINGS, INCOME, OTHER }

data class Category(
    val id: Long = 0L,
    val name: String,
    val bucket: CategoryBucket,
    val budgetTarget: Money? = null,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
)

data class Transaction(
    val id: Long = 0L,
    val type: TransactionType,
    val amount: Money,
    val categoryId: Long? = null,
    val date: LocalDate,
    val note: String = "",
    val createdAt: Instant? = null,
)

enum class PeriodType { MONTHLY, BIWEEKLY }

/** A budgeting window, inclusive of both [start] and [endInclusive]. */
data class BudgetPeriod(
    val type: PeriodType,
    val start: LocalDate,
    val endInclusive: LocalDate,
) {
    init {
        require(!endInclusive.isBefore(start)) { "Period end must not precede its start" }
    }

    fun contains(date: LocalDate): Boolean =
        !date.isBefore(start) && !date.isAfter(endInclusive)

    val dayCount: Int get() = (endInclusive.toEpochDay() - start.toEpochDay()).toInt() + 1
}

data class CategoryProgress(
    val category: Category,
    val spent: Money,
    val target: Money?,
    /** spent / target, or null when no target is set. Values above 1 mean over budget. */
    val progressFraction: Double?,
    val remaining: Money?,
) {
    val isOverBudget: Boolean get() = progressFraction != null && progressFraction > 1.0
}

data class BudgetSummary(
    val period: BudgetPeriod,
    val income: Money,
    val spent: Money,
    val remaining: Money,
    /** Sum of the per-category targets the user defined for this period. */
    val plannedExpenses: Money,
    val categoryProgress: List<CategoryProgress>,
    val uncategorizedSpent: Money,
) {
    /** True when the user has recorded nothing and defined no targets yet. */
    val isEmpty: Boolean
        get() = income.isZero && spent.isZero && categoryProgress.none { it.target != null }
}
