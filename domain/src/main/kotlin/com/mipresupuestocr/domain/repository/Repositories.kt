package com.mipresupuestocr.domain.repository

import com.mipresupuestocr.domain.model.AppResult
import com.mipresupuestocr.domain.model.Category
import com.mipresupuestocr.domain.model.ExchangeRate
import com.mipresupuestocr.domain.model.Holding
import com.mipresupuestocr.domain.model.ManualRateInput
import com.mipresupuestocr.domain.model.Quote
import com.mipresupuestocr.domain.model.Transaction
import com.mipresupuestocr.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface TransactionRepository {
    fun observeAll(): Flow<List<Transaction>>
    fun observeBetween(start: LocalDate, endInclusive: LocalDate): Flow<List<Transaction>>
    suspend fun getById(id: Long): Transaction?
    suspend fun upsert(transaction: Transaction): Long
    suspend fun delete(id: Long)
}

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    suspend fun getAll(): List<Category>
    suspend fun upsert(category: Category): Long
    suspend fun replaceAll(categories: List<Category>)
    suspend fun delete(id: Long)
}

interface HoldingRepository {
    fun observeAll(): Flow<List<Holding>>
    suspend fun getAll(): List<Holding>
    suspend fun getById(id: Long): Holding?
    suspend fun upsert(holding: Holding): Long
    suspend fun delete(id: Long)
}

/**
 * Exposes both the cached reference rate and an optional user-entered manual rate. The active
 * rate is the manual one when present; otherwise the latest reference rate.
 */
interface RateRepository {
    fun observeActiveRate(): Flow<ExchangeRate?>
    fun observeReferenceRate(): Flow<ExchangeRate?>
    /** Fetches a fresh reference rate. Failures are reported, never swallowed. */
    suspend fun refresh(): AppResult<ExchangeRate>
    /** Latest known rate (manual when present), used as an offline fallback. */
    suspend fun activeRateSnapshot(): ExchangeRate?
    suspend fun setManualRate(input: ManualRateInput): AppResult<ExchangeRate>
    suspend fun clearManualRate()
}

interface QuoteRepository {
    fun observeQuotes(): Flow<Map<String, Quote>>
    /** Fetches quotes for [tickers]. Failures are reported; cached values stay available. */
    suspend fun refresh(tickers: List<String>): AppResult<Map<String, Quote>>
    /** Cached quotes for the requested tickers, used as an offline fallback. */
    suspend fun cachedQuotes(tickers: List<String>): Map<String, Quote>
}

interface SettingsRepository {
    fun observeSettings(): Flow<UserSettings>
    suspend fun update(transform: (UserSettings) -> UserSettings)
    suspend fun current(): UserSettings
}

/** Narrow cache contracts so repositories can be unit tested without Room or Android. */
interface RateCache {
    fun observeRates(): Flow<List<ExchangeRate>>
    suspend fun reference(): ExchangeRate?
    suspend fun manual(): ExchangeRate?
    suspend fun saveReference(rate: ExchangeRate)
    suspend fun saveManual(rate: ExchangeRate)
    suspend fun clearManual()
}

interface QuoteCache {
    fun observeQuotes(): Flow<List<Quote>>
    suspend fun all(): List<Quote>
    suspend fun save(quotes: Collection<Quote>)
    suspend fun clear()
}
