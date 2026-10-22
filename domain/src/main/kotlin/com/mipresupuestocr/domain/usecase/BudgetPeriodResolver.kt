package com.mipresupuestocr.domain.usecase

import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.AppResult
import com.mipresupuestocr.domain.model.BudgetPeriod
import com.mipresupuestocr.domain.model.PeriodType
import com.mipresupuestocr.domain.model.UserSettings
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Resolves the budgeting window that contains a reference date.
 *
 * Monthly periods follow the calendar month. Biweekly periods are 14-day windows anchored on the
 * user's first known payday, matching how Costa Rican biweekly payrolls typically fall.
 */
class BudgetPeriodResolver {

    fun resolve(
        type: PeriodType,
        referenceDate: LocalDate,
        paydayAnchor: LocalDate? = null,
    ): AppResult<BudgetPeriod> = when (type) {
        PeriodType.MONTHLY -> AppResult.success(
            BudgetPeriod(
                type = PeriodType.MONTHLY,
                start = referenceDate.withDayOfMonth(1),
                endInclusive = referenceDate.withDayOfMonth(referenceDate.lengthOfMonth()),
            ),
        )

        PeriodType.BIWEEKLY -> {
            if (paydayAnchor == null) {
                AppResult.failure(AppError.MissingPaydayAnchor)
            } else {
                val daysSinceAnchor = ChronoUnit.DAYS.between(paydayAnchor, referenceDate)
                val cycles = Math.floorDiv(daysSinceAnchor, DAYS_PER_CYCLE.toLong())
                val start = paydayAnchor.plusDays(cycles * DAYS_PER_CYCLE)
                AppResult.success(
                    BudgetPeriod(
                        type = PeriodType.BIWEEKLY,
                        start = start,
                        endInclusive = start.plusDays((DAYS_PER_CYCLE - 1).toLong()),
                    ),
                )
            }
        }
    }

    fun resolve(settings: UserSettings, referenceDate: LocalDate): AppResult<BudgetPeriod> =
        resolve(settings.periodType, referenceDate, settings.paydayAnchor)

    fun previous(period: BudgetPeriod): BudgetPeriod = when (period.type) {
        PeriodType.MONTHLY -> {
            val start = period.start.minusMonths(1).withDayOfMonth(1)
            BudgetPeriod(PeriodType.MONTHLY, start, start.withDayOfMonth(start.lengthOfMonth()))
        }

        PeriodType.BIWEEKLY -> BudgetPeriod(
            type = PeriodType.BIWEEKLY,
            start = period.start.minusDays(DAYS_PER_CYCLE.toLong()),
            endInclusive = period.endInclusive.minusDays(DAYS_PER_CYCLE.toLong()),
        )
    }

    fun next(period: BudgetPeriod): BudgetPeriod = when (period.type) {
        PeriodType.MONTHLY -> {
            val start = period.start.plusMonths(1).withDayOfMonth(1)
            BudgetPeriod(PeriodType.MONTHLY, start, start.withDayOfMonth(start.lengthOfMonth()))
        }

        PeriodType.BIWEEKLY -> BudgetPeriod(
            type = PeriodType.BIWEEKLY,
            start = period.start.plusDays(DAYS_PER_CYCLE.toLong()),
            endInclusive = period.endInclusive.plusDays(DAYS_PER_CYCLE.toLong()),
        )
    }

    companion object {
        const val DAYS_PER_CYCLE = 14
    }
}
