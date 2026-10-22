package com.mipresupuestocr.domain.usecase

import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.AppResult
import com.mipresupuestocr.domain.model.BudgetPeriod
import com.mipresupuestocr.domain.model.CorrectionKind
import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.Money
import com.mipresupuestocr.domain.model.TransactionType
import com.mipresupuestocr.domain.repository.BalanceCorrectionRepository
import com.mipresupuestocr.domain.repository.TransactionRepository

/**
 * Sets a period figure to an exact total, correcting a clerical error without adding an entry.
 *
 * Each use case reads the real entries for the period, works out the difference between what the
 * app computed and the total the user typed, and stores only that difference as a correction. The
 * visible history is untouched; the totals simply stop being wrong.
 *
 * Available is always income minus spent, so [SetAvailableBalanceUseCase] corrects the income side
 * and the caller is expected to say so in the UI.
 */
class SetIncomeBalanceUseCase(
    private val transactionRepository: TransactionRepository,
    private val correctionRepository: BalanceCorrectionRepository,
) {
    suspend operator fun invoke(period: BudgetPeriod, target: Money): AppResult<Unit> {
        if (!target.isValidCorrection()) return AppResult.failure(AppError.InvalidAmount)
        val income = transactionRepository.sumInPeriod(period, TransactionType.INCOME, target.currency)
        correctionRepository.set(
            periodStart = period.start,
            kind = CorrectionKind.INCOME,
            amountMinor = target.amountMinor - income,
            currency = target.currency,
        )
        return AppResult.success(Unit)
    }
}

/** Sets the period's total spending; the difference lands in the uncategorised bucket. */
class SetSpentBalanceUseCase(
    private val transactionRepository: TransactionRepository,
    private val correctionRepository: BalanceCorrectionRepository,
) {
    suspend operator fun invoke(period: BudgetPeriod, target: Money): AppResult<Unit> {
        if (!target.isValidCorrection()) return AppResult.failure(AppError.InvalidAmount)
        val spent = transactionRepository.sumInPeriod(period, TransactionType.EXPENSE, target.currency) +
            correctionRepository.spentCorrectionsTotal(period, target.currency)
        correctionRepository.set(
            periodStart = period.start,
            kind = CorrectionKind.SPENT,
            amountMinor = target.amountMinor - spent,
            currency = target.currency,
        )
        return AppResult.success(Unit)
    }
}

/**
 * Sets the remaining/available figure. Because available = income − spent, the difference is
 * stored as an income correction; the income total shown to the user changes with it.
 */
class SetAvailableBalanceUseCase(
    private val transactionRepository: TransactionRepository,
    private val correctionRepository: BalanceCorrectionRepository,
) {
    suspend operator fun invoke(period: BudgetPeriod, target: Money): AppResult<Unit> {
        if (!target.isValidCorrection()) return AppResult.failure(AppError.InvalidAmount)
        val income = transactionRepository.sumInPeriod(period, TransactionType.INCOME, target.currency)
        val spent = transactionRepository.sumInPeriod(period, TransactionType.EXPENSE, target.currency) +
            correctionRepository.spentCorrectionsTotal(period, target.currency)
        correctionRepository.set(
            periodStart = period.start,
            kind = CorrectionKind.INCOME,
            amountMinor = target.amountMinor + spent - income,
            currency = target.currency,
        )
        return AppResult.success(Unit)
    }
}

/** Sets one category's spent balance to the exact total the user typed. */
class SetCategoryBalanceUseCase(
    private val transactionRepository: TransactionRepository,
    private val correctionRepository: BalanceCorrectionRepository,
) {
    suspend operator fun invoke(period: BudgetPeriod, categoryId: Long, target: Money): AppResult<Unit> {
        if (!target.isValidCorrection()) return AppResult.failure(AppError.InvalidAmount)
        if (categoryId <= 0L) return AppResult.failure(AppError.NoData)
        val spent = transactionRepository.getBetween(period.start, period.endInclusive)
            .filter {
                it.type == TransactionType.EXPENSE &&
                    it.amount.currency == target.currency &&
                    it.categoryId == categoryId
            }
            .sumOf { it.amount.amountMinor }
        correctionRepository.set(
            periodStart = period.start,
            kind = CorrectionKind.SPENT,
            categoryId = categoryId,
            amountMinor = target.amountMinor - spent,
            currency = target.currency,
        )
        return AppResult.success(Unit)
    }
}

/** Drops a correction, so the figure goes back to the sum of the real entries. */
class ClearBalanceCorrectionUseCase(
    private val correctionRepository: BalanceCorrectionRepository,
) {
    suspend operator fun invoke(
        period: BudgetPeriod,
        kind: CorrectionKind,
        categoryId: Long? = null,
    ) {
        correctionRepository.clear(period.start, kind, categoryId)
    }
}

private fun Money.isValidCorrection(): Boolean = currency == CurrencyCode.CRC && !isNegative

private suspend fun TransactionRepository.sumInPeriod(
    period: BudgetPeriod,
    type: TransactionType,
    currency: CurrencyCode,
): Long = getBetween(period.start, period.endInclusive)
    .filter { it.type == type && it.amount.currency == currency }
    .sumOf { it.amount.amountMinor }

private suspend fun BalanceCorrectionRepository.spentCorrectionsTotal(
    period: BudgetPeriod,
    currency: CurrencyCode,
): Long = getAll()
    .filter {
        it.periodStart == period.start &&
            it.kind == CorrectionKind.SPENT &&
            it.currency == currency
    }
    .sumOf { it.amountMinor }
