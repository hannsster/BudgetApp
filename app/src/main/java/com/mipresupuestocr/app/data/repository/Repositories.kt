package com.mipresupuestocr.app.data.repository

import com.mipresupuestocr.app.data.local.CategoryDao
import com.mipresupuestocr.app.data.local.HoldingDao
import com.mipresupuestocr.app.data.local.TransactionDao
import com.mipresupuestocr.app.data.local.toDomain
import com.mipresupuestocr.app.data.local.toEntity
import com.mipresupuestocr.domain.model.AppResult
import com.mipresupuestocr.domain.model.Category
import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.ExchangeRate
import com.mipresupuestocr.domain.model.Holding
import com.mipresupuestocr.domain.model.ManualRateInput
import com.mipresupuestocr.domain.model.Quote
import com.mipresupuestocr.domain.model.Transaction
import com.mipresupuestocr.domain.provider.ExchangeRateProvider
import com.mipresupuestocr.domain.provider.MarketQuoteProvider
import com.mipresupuestocr.domain.repository.CategoryRepository
import com.mipresupuestocr.domain.repository.HoldingRepository
import com.mipresupuestocr.domain.repository.QuoteCache
import com.mipresupuestocr.domain.repository.QuoteRepository
import com.mipresupuestocr.domain.repository.RateCache
import com.mipresupuestocr.domain.repository.RateRepository
import com.mipresupuestocr.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultTransactionRepository @Inject constructor(
    private val dao: TransactionDao,
) : TransactionRepository {

    override fun observeAll(): Flow<List<Transaction>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeBetween(start: LocalDate, endInclusive: LocalDate): Flow<List<Transaction>> =
        dao.observeBetween(start.toEpochDay(), endInclusive.toEpochDay())
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(id: Long): Transaction? = dao.getById(id)?.toDomain()

    override suspend fun upsert(transaction: Transaction): Long = dao.upsert(transaction.toEntity())

    override suspend fun delete(id: Long) = dao.delete(id)
}

@Singleton
class DefaultCategoryRepository @Inject constructor(
    private val dao: CategoryDao,
) : CategoryRepository {

    override fun observeAll(): Flow<List<Category>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getAll(): List<Category> = dao.getAll().map { it.toDomain() }

    override suspend fun upsert(category: Category): Long = dao.upsert(category.toEntity())

    override suspend fun replaceAll(categories: List<Category>) {
        dao.insertAll(categories.map { it.toEntity() })
    }

    override suspend fun delete(id: Long) = dao.delete(id)
}

@Singleton
class DefaultHoldingRepository @Inject constructor(
    private val dao: HoldingDao,
) : HoldingRepository {

    override fun observeAll(): Flow<List<Holding>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getAll(): List<Holding> = dao.getAll().map { it.toDomain() }

    override suspend fun getById(id: Long): Holding? = dao.getById(id)?.toDomain()

    override suspend fun upsert(holding: Holding): Long = dao.upsert(holding.toEntity())

    override suspend fun delete(id: Long) = dao.delete(id)
}

/**
 * Offline-first rate repository: the active rate is the user's manual rate when present, otherwise
 * the last cached reference rate. Refreshing never invents a rate; failures are reported and the
 * cached value stays on screen.
 */
@Singleton
class DefaultRateRepository @Inject constructor(
    private val provider: ExchangeRateProvider,
    private val cache: RateCache,
) : RateRepository {

    override fun observeActiveRate(): Flow<ExchangeRate?> = cache.observeRates().map { rates ->
        rates.firstOrNull { it.isManual } ?: rates.firstOrNull { !it.isManual }
    }

    override fun observeReferenceRate(): Flow<ExchangeRate?> = cache.observeRates().map { rates ->
        rates.firstOrNull { !it.isManual }
    }

    override suspend fun refresh(): AppResult<ExchangeRate> {
        val result = provider.getLatestRate(CurrencyCode.USD, CurrencyCode.CRC)
        if (result is AppResult.Success) {
            cache.saveReference(result.value)
        }
        return result
    }

    override suspend fun activeRateSnapshot(): ExchangeRate? =
        cache.manual() ?: cache.reference()

    override suspend fun setManualRate(input: ManualRateInput): AppResult<ExchangeRate> {
        val rate = ExchangeRate(
            base = CurrencyCode.USD,
            quote = CurrencyCode.CRC,
            rate = input.rate,
            asOf = input.asOf,
            source = MANUAL_SOURCE,
            isManual = true,
        )
        cache.saveManual(rate)
        return AppResult.success(rate)
    }

    override suspend fun clearManualRate() {
        cache.clearManual()
    }

    private companion object {
        const val MANUAL_SOURCE = "Manual"
    }
}

@Singleton
class DefaultQuoteRepository @Inject constructor(
    private val provider: MarketQuoteProvider,
    private val cache: QuoteCache,
) : QuoteRepository {

    override fun observeQuotes(): Flow<Map<String, Quote>> = cache.observeQuotes().map { quotes ->
        quotes.associateBy { it.ticker }
    }

    override suspend fun refresh(tickers: List<String>): AppResult<Map<String, Quote>> {
        if (tickers.isEmpty()) return AppResult.success(emptyMap())

        val result = provider.getQuotes(tickers)
        if (result is AppResult.Success) {
            cache.save(result.value.values)
        }
        return result
    }

    override suspend fun cachedQuotes(tickers: List<String>): Map<String, Quote> {
        if (tickers.isEmpty()) return emptyMap()
        val wanted = tickers.map { it.uppercase() }.toSet()
        return cache.all()
            .filter { it.ticker.uppercase() in wanted }
            .associateBy { it.ticker.uppercase() }
    }
}
