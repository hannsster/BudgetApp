package com.mipresupuestocr.app.data.repository

import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.AppResult
import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.ExchangeRate
import com.mipresupuestocr.domain.model.ManualRateInput
import com.mipresupuestocr.domain.repository.RateCache
import com.mipresupuestocr.domain.provider.ExchangeRateProvider
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

class DefaultRateRepositoryTest {

    private class FakeRateCache : RateCache {
        val rates = MutableStateFlow<List<ExchangeRate>>(emptyList())

        override fun observeRates(): Flow<List<ExchangeRate>> = rates

        override suspend fun reference(): ExchangeRate? = rates.value.firstOrNull { !it.isManual }

        override suspend fun manual(): ExchangeRate? = rates.value.firstOrNull { it.isManual }

        override suspend fun saveReference(rate: ExchangeRate) {
            rates.value = rates.value.filterNot { !it.isManual } + rate
        }

        override suspend fun saveManual(rate: ExchangeRate) {
            rates.value = rates.value.filterNot { it.isManual } + rate
        }

        override suspend fun clearManual() {
            rates.value = rates.value.filterNot { it.isManual }
        }
    }

    private class FakeProvider(var result: AppResult<ExchangeRate>) : ExchangeRateProvider {
        override val sourceName: String = "fake-provider"
        override suspend fun getLatestRate(base: CurrencyCode, quote: CurrencyCode): AppResult<ExchangeRate> = result
        override suspend fun getSupportedCurrencies(): AppResult<Set<CurrencyCode>> =
            AppResult.success(setOf(CurrencyCode.USD, CurrencyCode.CRC))
    }

    private val asOf: Instant = Instant.parse("2026-10-22T12:00:00Z")

    private fun rate(
        value: String = "512.34",
        source: String = "fake-provider",
        isManual: Boolean = false,
    ) = ExchangeRate(
        base = CurrencyCode.USD,
        quote = CurrencyCode.CRC,
        rate = BigDecimal(value),
        asOf = asOf,
        source = source,
        isManual = isManual,
    )

    @Test
    fun `refresh stores the fetched reference rate`() = runTest {
        val cache = FakeRateCache()
        val repository = DefaultRateRepository(FakeProvider(AppResult.success(rate())), cache)

        val result = repository.refresh()

        assertEquals(rate(), result.getOrNull())
        assertEquals(rate(), cache.reference())
    }

    @Test
    fun `refresh failure keeps the cached rate intact`() = runTest {
        val cache = FakeRateCache().apply { saveReference(rate(value = "500.00")) }
        val repository = DefaultRateRepository(FakeProvider(AppResult.failure(AppError.Network)), cache)

        val result = repository.refresh()

        assertEquals(AppError.Network, result.errorOrNull())
        assertEquals(rate(value = "500.00"), cache.reference())
    }

    @Test
    fun `manual rate takes priority over the reference rate`() = runTest {
        val cache = FakeRateCache().apply {
            saveReference(rate(value = "512.34"))
            saveManual(rate(value = "505.00", source = "Manual", isManual = true))
        }
        val repository = DefaultRateRepository(FakeProvider(AppResult.success(rate())), cache)

        val active = repository.observeActiveRate().first()

        assertEquals(BigDecimal("505.00"), active?.rate)
        assertTrue(active?.isManual == true)
    }

    @Test
    fun `clearing the manual rate falls back to the reference rate`() = runTest {
        val cache = FakeRateCache().apply {
            saveReference(rate(value = "512.34"))
        }
        val repository = DefaultRateRepository(FakeProvider(AppResult.success(rate())), cache)

        repository.setManualRate(ManualRateInput(BigDecimal("505.00"), asOf))
        assertEquals(BigDecimal("505.00"), repository.activeRateSnapshot()?.rate)

        repository.clearManualRate()

        assertEquals(BigDecimal("512.34"), repository.activeRateSnapshot()?.rate)
    }

    @Test
    fun `manual rates are marked as manual`() = runTest {
        val repository = DefaultRateRepository(FakeProvider(AppResult.success(rate())), FakeRateCache())

        val saved = repository.setManualRate(ManualRateInput(BigDecimal("510.10"), asOf)).getOrNull()

        assertTrue(saved?.isManual == true)
        assertEquals("Manual", saved?.source)
    }

    @Test
    fun `no cached rate means no active rate`() = runTest {
        val repository = DefaultRateRepository(FakeProvider(AppResult.success(rate())), FakeRateCache())

        assertNull(repository.observeActiveRate().first())
    }
}
