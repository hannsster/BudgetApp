package com.mipresupuestocr.domain.usecase

import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.AppResult
import com.mipresupuestocr.domain.model.BudgetAllocation
import com.mipresupuestocr.domain.model.BudgetSplit
import com.mipresupuestocr.domain.model.FiftyThirtyTwentyResult
import com.mipresupuestocr.domain.model.IncomeFrequency
import com.mipresupuestocr.domain.model.Money
import java.math.BigDecimal

/**
 * Splits net income into needs / wants / savings.
 *
 * Rounding rule: needs and wants are rounded to the minor unit first and savings absorbs the
 * remainder, so the three buckets always add back up to the income that was entered.
 */
class FiftyThirtyTwentyCalculator {

    fun calculate(
        income: Money,
        frequency: IncomeFrequency = IncomeFrequency.MONTHLY,
        split: BudgetSplit = BudgetSplit.FIFTY_THIRTY_TWENTY,
    ): AppResult<FiftyThirtyTwentyResult> {
        if (!income.isPositive) return AppResult.failure(AppError.InvalidIncome)
        if (!split.isValid) return AppResult.failure(AppError.InvalidSplit)

        val monthlyIncome = toMonthly(income, frequency)
        return AppResult.success(
            FiftyThirtyTwentyResult(
                frequency = frequency,
                incomePerEvent = income,
                allocationPerEvent = allocate(income, split),
                monthlyIncome = monthlyIncome,
                monthlyAllocation = allocate(monthlyIncome, split),
                split = split,
            ),
        )
    }

    /** Monthly equivalent: 26 biweekly paychecks and 52 weekly paychecks per year. */
    fun toMonthly(income: Money, frequency: IncomeFrequency): Money = when (frequency) {
        IncomeFrequency.MONTHLY -> income
        IncomeFrequency.BIWEEKLY -> income.times(BigDecimal(26)).div(BigDecimal(12))
        IncomeFrequency.WEEKLY -> income.times(BigDecimal(52)).div(BigDecimal(12))
    }

    fun allocate(income: Money, split: BudgetSplit): BudgetAllocation {
        val needs = income.percent(split.needs)
        val wants = income.percent(split.wants)
        val savings = income - needs - wants
        return BudgetAllocation(needs = needs, wants = wants, savings = savings)
    }
}
