package com.mipresupuestocr.domain.usecase

import com.mipresupuestocr.domain.model.AccountTransfer
import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.AppResult
import com.mipresupuestocr.domain.model.Money
import com.mipresupuestocr.domain.repository.TransferRepository
import java.time.LocalDate

/**
 * Moves money from one account to another.
 *
 * Rejected inputs never reach the database: a transfer to the same account would balance out to
 * nothing while pretending to be a movement, and a zero or negative amount is a typo rather than a
 * transfer. The budget is untouched either way, since a transfer is neither income nor spending.
 */
class TransferBetweenAccountsUseCase(private val repository: TransferRepository) {

    suspend operator fun invoke(
        fromAccountId: Long,
        toAccountId: Long,
        amount: Money,
        date: LocalDate,
        note: String = "",
    ): AppResult<Long> {
        if (fromAccountId == toAccountId) return AppResult.failure(AppError.SameAccountTransfer)
        if (!amount.isPositive) return AppResult.failure(AppError.InvalidAmount)

        return AppResult.success(
            repository.save(
                AccountTransfer(
                    fromAccountId = fromAccountId,
                    toAccountId = toAccountId,
                    amount = amount,
                    date = date,
                    note = note.trim().take(MAX_NOTE_LENGTH),
                ),
            ),
        )
    }

    private companion object {
        const val MAX_NOTE_LENGTH = 120
    }
}

/** Removing a transfer puts both balances back the way they were. */
class DeleteAccountTransferUseCase(private val repository: TransferRepository) {
    suspend operator fun invoke(id: Long) = repository.delete(id)
}
