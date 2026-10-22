package com.mipresupuestocr.domain.usecase

import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.AppResult
import com.mipresupuestocr.domain.model.Holding
import com.mipresupuestocr.domain.model.Transaction
import com.mipresupuestocr.domain.repository.HoldingRepository
import com.mipresupuestocr.domain.repository.TransactionRepository
import java.util.Locale

class SaveTransactionUseCase(private val repository: TransactionRepository) {

    suspend operator fun invoke(transaction: Transaction): AppResult<Long> {
        if (!transaction.amount.isPositive) return AppResult.failure(AppError.InvalidAmount)
        val normalized = transaction.copy(note = transaction.note.trim().take(MAX_NOTE_LENGTH))
        return AppResult.success(repository.upsert(normalized))
    }

    private companion object {
        const val MAX_NOTE_LENGTH = 240
    }
}

class DeleteTransactionUseCase(private val repository: TransactionRepository) {
    suspend operator fun invoke(id: Long) = repository.delete(id)
}

class UpsertHoldingUseCase(private val repository: HoldingRepository) {

    suspend operator fun invoke(holding: Holding): AppResult<Long> {
        val ticker = holding.ticker.trim().uppercase(Locale.ROOT)
        if (!TICKER_PATTERN.matches(ticker)) {
            return AppResult.failure(AppError.InvalidTicker(holding.ticker))
        }
        if (holding.shares.signum() <= 0) return AppResult.failure(AppError.InvalidShares)
        val averagePrice = holding.averagePriceUsd
        if (averagePrice != null && averagePrice.signum() < 0) {
            return AppResult.failure(AppError.InvalidAmount)
        }

        val normalized = holding.copy(
            ticker = ticker,
            averagePriceUsd = averagePrice?.takeIf { it.signum() > 0 },
            notes = holding.notes.trim().take(MAX_NOTE_LENGTH),
        )
        return AppResult.success(repository.upsert(normalized))
    }

    private companion object {
        /** US-listed ETF tickers: 1-10 characters, letters/digits/periods/dashes. */
        val TICKER_PATTERN = Regex("^[A-Z][A-Z0-9.\\-]{0,9}$")
        const val MAX_NOTE_LENGTH = 240
    }
}

class DeleteHoldingUseCase(private val repository: HoldingRepository) {
    suspend operator fun invoke(id: Long) = repository.delete(id)
}
