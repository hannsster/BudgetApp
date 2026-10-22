package com.mipresupuestocr.app.data.repository

import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.AppResult
import com.mipresupuestocr.domain.model.Quote
import com.mipresupuestocr.domain.provider.MarketQuoteProvider
import com.mipresupuestocr.domain.repository.QuoteCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class DefaultQuoteRepositoryTest {

    private class FakeQuoteCache : QuoteCache {
        val quotes = MutableStateFlow<List<Quote>>(emptyList())

        override fun observeQuotes(): Flow<List<Quote>> = quotes
        override suspend fun all(): List<Quote> = quotes.value
        override suspend fun save(quotes: Collection<Quote>) {
            val merged = this.quotes.value.associateBy { it.ticker }.toMutableMap()
            quotes.forEach { merged[it.ticker] = it }
            this.quotes.value = merged.values.toList()
        }

        override suspend fun clear() {
            quotes.value = emptyList()
        }
    }

    private class FakeProvider(var result: AppResult<Map<String, Quote>>) : MarketQuoteProvider {
        override val sourceName: String = "fake-quotes"
        override val requiresApiKey: Boolean = true
        override suspend fun getQuote(ticker: String): AppResult<Quote> =
            result.map { it.getValue(ticker) }

        override suspend fun getQuotes(tickers: List<String>): AppResult<Map<String, Quote>> = result
    }

    private val asOf: Instant = Instant.parse("2026-10-22T20:00:00Z")

    private fun quote(ticker: String, price: String) = Quote(
        ticker = ticker,
        priceUsd = BigDecimal(price),
        asOf = asOf,
        source = "fake-quotes",
    )

    @Test
    fun `refresh caches the fetched quotes`() = runTest {
        val cache = FakeQuoteCache()
        val quotes = mapOf("VTI" to quote("VTI", "280.12"))
        val repository = DefaultQuoteRepository(FakeProvider(AppResult.success(quotes)), cache)

        val result = repository.refresh(listOf("VTI"))

        assertEquals(quotes, result.getOrNull())
        assertEquals(quotes, repository.observeQuotes().first())
    }

    @Test
    fun `refresh failure leaves cached quotes available`() = runTest {
        val cache = FakeQuoteCache().apply { save(listOf(quote("VTI", "270.00"))) }
        val repository = DefaultQuoteRepository(FakeProvider(AppResult.failure(AppError.Timeout)), cache)

        val result = repository.refresh(listOf("VTI"))

        assertEquals(AppError.Timeout, result.errorOrNull())
        assertEquals("VTI", repository.cachedQuotes(listOf("VTI")).keys.single())
    }

    @Test
    fun `cached quote lookup ignores case and unknown tickers`() = runTest {
        val cache = FakeQuoteCache().apply { save(listOf(quote("VTI", "270.00"))) }
        val repository = DefaultQuoteRepository(FakeProvider(AppResult.success(emptyMap())), cache)

        val cached = repository.cachedQuotes(listOf("vti", "QQQ"))

        assertEquals(setOf("VTI"), cached.keys)
        assertNull(cached["QQQ"])
    }

    @Test
    fun `empty ticker list makes no provider call`() = runTest {
        val provider = FakeProvider(AppResult.failure(AppError.Unknown))
        val repository = DefaultQuoteRepository(provider, FakeQuoteCache())

        val result = repository.refresh(emptyList())

        assertTrue(result is AppResult.Success)
        assertEquals(emptyMap<String, Quote>(), result.getOrNull())
    }
}
