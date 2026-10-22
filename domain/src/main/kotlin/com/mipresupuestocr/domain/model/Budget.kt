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
    /** True when the spent figure was corrected by hand rather than summed from entries. */
    val isAdjusted: Boolean = false,
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
    /** True when the income figure was corrected by hand rather than summed from entries. */
    val incomeAdjusted: Boolean = false,
) {
    /** True when the user has recorded nothing and defined no targets yet. */
    val isEmpty: Boolean
        get() = income.isZero && spent.isZero && categoryProgress.none { it.target != null }
}

enum class CorrectionKind { INCOME, SPENT }

/**
 * A hand correction of one period figure, stored as a delta instead of an extra entry.
 *
 * The user types the total they know is right (for example the real spent amount after fixing a
 * typo); the app keeps the difference here, so the visible history stays exactly what happened and
 * later entries keep adding up correctly. A correction in the income slot also moves the
 * remaining/available figure, because available is income minus spent.
 */
data class BalanceCorrection(
    val periodStart: LocalDate,
    val kind: CorrectionKind,
    /** Null for the periods income and unallocated spending slots. */
    val categoryId: Long? = null,
    val amountMinor: Long,
    val currency: CurrencyCode = CurrencyCode.CRC,
)
