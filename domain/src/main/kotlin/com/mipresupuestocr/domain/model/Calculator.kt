package com.mipresupuestocr.domain.model

/** How often the net income entered in the 50/30/20 calculator is received. */
enum class IncomeFrequency { MONTHLY, BIWEEKLY, WEEKLY }

/**
 * A budgeting split expressed in whole percentages. [FIFTY_THIRTY_TWENTY] is the default and the
 * percentages must always total 100.
 */
data class BudgetSplit(
    val needs: Int,
    val wants: Int,
    val savings: Int,
) {
    val totalPercent: Int get() = needs + wants + savings

    val isValid: Boolean
        get() = needs >= 0 && wants >= 0 && savings >= 0 && totalPercent == 100

    companion object {
        val FIFTY_THIRTY_TWENTY = BudgetSplit(needs = 50, wants = 30, savings = 20)
    }
}

data class BudgetAllocation(
    val needs: Money,
    val wants: Money,
    val savings: Money,
) {
    val total: Money get() = needs + wants + savings
}

data class FiftyThirtyTwentyResult(
    val frequency: IncomeFrequency,
    /** Income for a single pay event, exactly as entered. */
    val incomePerEvent: Money,
    val allocationPerEvent: BudgetAllocation,
    /** Income normalised to a monthly figure (biweekly x26/12, weekly x52/12). */
    val monthlyIncome: Money,
    val monthlyAllocation: BudgetAllocation,
    val split: BudgetSplit,
)
