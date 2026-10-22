package com.mipresupuestocr.app.data.local

import com.mipresupuestocr.domain.model.ExchangeRate
import com.mipresupuestocr.domain.model.Quote
import com.mipresupuestocr.domain.repository.QuoteCache
import com.mipresupuestocr.domain.repository.RateCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomRateCache @Inject constructor(
    private val dao: RateCacheDao,
) : RateCache {

    override fun observeRates(): Flow<List<ExchangeRate>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun reference(): ExchangeRate? = dao.get(REFERENCE_KEY)?.toDomain()

    override suspend fun manual(): ExchangeRate? = dao.get(MANUAL_KEY)?.toDomain()

    override suspend fun saveReference(rate: ExchangeRate) {
        dao.upsert(rate.toEntity(REFERENCE_KEY))
    }

    override suspend fun saveManual(rate: ExchangeRate) {
        dao.upsert(rate.toEntity(MANUAL_KEY))
    }

    override suspend fun clearManual() {
        dao.delete(MANUAL_KEY)
    }

    private companion object {
        const val REFERENCE_KEY = "reference"
        const val MANUAL_KEY = "manual"
    }
}

@Singleton
class RoomQuoteCache @Inject constructor(
    private val dao: QuoteCacheDao,
) : QuoteCache {

    override fun observeQuotes(): Flow<List<Quote>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun all(): List<Quote> = dao.getAll().map { it.toDomain() }

    override suspend fun save(quotes: Collection<Quote>) {
        if (quotes.isEmpty()) return
        dao.upsertAll(quotes.map { it.toEntity() })
    }

    override suspend fun clear() = dao.clear()
}
