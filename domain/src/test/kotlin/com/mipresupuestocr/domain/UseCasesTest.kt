package com.mipresupuestocr.domain

import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.AppResult
import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.Holding
import com.mipresupuestocr.domain.model.ManualRateInput
import com.mipresupuestocr.domain.model.Money
import com.mipresupuestocr.domain.model.Quote
import com.mipresupuestocr.domain.model.Transaction
import com.mipresupuestocr.domain.model.TransactionType
import com.mipresupuestocr.domain.usecase.RefreshExchangeRateUseCase
import com.mipresupuestocr.domain.usecase.RefreshQuotesUseCase
import com.mipresupuestocr.domain.usecase.SaveTransactionUseCase
import com.mipresupuestocr.domain.usecase.SetManualRateUseCase
import com.mipresupuestocr.domain.usecase.UpsertHoldingUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class UseCasesTest {

    private val asOf: Instant = Instant.parse("2026-10-22T20:00:00Z")

    private fun quote(ticker: String, price: String) = Quote(
        ticker = ticker,
        priceUsd = BigDecimal(price),
        asOf = asOf,
        source = "Test feed",
    )

    @Test
    fun `empty portfolio skips the network call`() = runTest {
        val quotes = FakeQuoteRepository { AppResult.success(emptyMap()) }
        val useCase = RefreshQuotesUseCase(quotes, FakeHoldingRepository())

        val result = useCase().valueOrThrow()

        assertTrue(result.skipped)
        assertEquals(0, quotes.callCount)
    }

    @Test
    fun `refresh requests every held ticker once`() = runTest {
        val holdings = FakeHoldingRepository(
            listOf(
                Holding(ticker = "vti", shares = BigDecimal("2")),
                Holding(ticker = "VTI", shares = BigDecimal("3")),
                Holding(ticker = "QQQ", shares = BigDecimal("1")),
            ),
        )
        val quotes = FakeQuoteRepository {
            AppResult.success(mapOf("VTI" to quote("VTI", "280"), "QQQ" to quote("QQQ", "420")))
        }

        val result = RefreshQuotesUseCase(quotes, holdings)().valueOrThrow()

        assertEquals(listOf("VTI", "QQQ"), quotes.lastTickers)
        assertEquals(2, result.updated.size)
        assertTrue(result.failedTickers.isEmpty())
        assertEquals(false, result.skipped)
    }

    @Test
    fun `unknown tickers are reported as failed`() = runTest {
        val holdings = FakeHoldingRepository(
            listOf(
                Holding(ticker = "VTI", shares = BigDecimal("2")),
                Holding(ticker = "NOPE", shares = BigDecimal("4")),
            ),
        )
        val quotes = FakeQuoteRepository {
            AppResult.success(mapOf("VTI" to quote("VTI", "280")))
        }

        val result = RefreshQuotesUseCase(quotes, holdings)().valueOrThrow()

        assertEquals(listOf("NOPE"), result.failedTickers)
    }

    @Test
    fun `provider failure propagates as a domain error`() = runTest {
        val holdings = FakeHoldingRepository(listOf(Holding(ticker = "VTI", shares = BigDecimal("1"))))
        val quotes = FakeQuoteRepository { AppResult.failure(AppError.QuotaExceeded) }

        val result = RefreshQuotesUseCase(quotes, holdings)()

        assertEquals(AppError.QuotaExceeded, result.errorOrNull())
    }

    @Test
    fun `transactions must have a positive amount`() = runTest {
        val repository = FakeTransactionRepository()
        val useCase = SaveTransactionUseCase(repository)

        val result = useCase(
            Transaction(
                type = TransactionType.EXPENSE,
                amount = Money.zero(CurrencyCode.CRC),
                date = LocalDate.of(2026, 10, 1),
            ),
        )

        assertEquals(AppError.InvalidAmount, result.errorOrNull())
        assertTrue(repository.upserted.isEmpty())
    }

    @Test
    fun `transaction notes are trimmed and capped`() = runTest {
        val repository = FakeTransactionRepository()
        val useCase = SaveTransactionUseCase(repository)

        useCase(
            Transaction(
                type = TransactionType.EXPENSE,
                amount = Money.ofMajor("1000.00", CurrencyCode.CRC),
                date = LocalDate.of(2026, 10, 1),
                note = "  almuerzo  ",
            ),
        )

        assertEquals("almuerzo", repository.upserted.single().note)
    }

    @Test
    fun `holdings normalise the ticker and clear a zero average price`() = runTest {
        val repository = FakeHoldingRepository()
        val useCase = UpsertHoldingUseCase(repository)

        useCase(
            Holding(
                ticker = "  vti ",
                shares = BigDecimal("3.5"),
                averagePriceUsd = BigDecimal.ZERO,
                notes = " long term ",
            ),
        ).valueOrThrow()

        val saved = repository.getAll().single()
        assertEquals("VTI", saved.ticker)
        assertEquals(null, saved.averagePriceUsd)
        assertEquals("long term", saved.notes)
    }

    @Test
    fun `invalid tickers and share counts are rejected`() = runTest {
        val useCase = UpsertHoldingUseCase(FakeHoldingRepository())

        assertEquals(
            AppError.InvalidTicker("bad ticker"),
            useCase(Holding(ticker = "bad ticker", shares = BigDecimal.ONE)).errorOrNull(),
        )
        assertEquals(
            AppError.InvalidShares,
            useCase(Holding(ticker = "VTI", shares = BigDecimal.ZERO)).errorOrNull(),
        )
        assertEquals(
            AppError.InvalidAmount,
            useCase(
                Holding(ticker = "VTI", shares = BigDecimal.ONE, averagePriceUsd = BigDecimal("-1")),
            ).errorOrNull(),
        )
    }

    @Test
    fun `rate refresh falls back to the cached rate`() = runTest {
        val cached = com.mipresupuestocr.domain.model.ExchangeRate(
            base = CurrencyCode.USD,
            quote = CurrencyCode.CRC,
            rate = BigDecimal("505.10"),
            asOf = asOf,
            source = "Manual",
            isManual = true,
        )
        val repository = FakeRateRepository { AppResult.failure(AppError.Network) }
            .apply { manualRate = cached }

        val result = RefreshExchangeRateUseCase(repository)().valueOrThrow()

        assertTrue(result.usedCache)
        assertEquals(cached, result.rate)
    }

    @Test
    fun `rate refresh fails when nothing is cached`() = runTest {
        val repository = FakeRateRepository { AppResult.failure(AppError.InvalidApiKey) }

        assertEquals(AppError.InvalidApiKey, RefreshExchangeRateUseCase(repository)().errorOrNull())
    }

    @Test
    fun `quote refresh falls back to cached quotes`() = runTest {
        val cached = mapOf("VTI" to quote("VTI", "270"))
        val holdings = FakeHoldingRepository(listOf(Holding(ticker = "VTI", shares = BigDecimal.ONE)))
        val quotes = FakeQuoteRepository(
            response = { AppResult.failure(AppError.Timeout) },
            cached = cached,
        )

        val result = RefreshQuotesUseCase(quotes, holdings)().valueOrThrow()

        assertTrue(result.usedCache)
        assertEquals(cached, result.updated)
        assertTrue(result.failedTickers.isEmpty())
    }

    @Test
    fun `manual rate must be positive`() = runTest {
        val useCase = SetManualRateUseCase(FakeRateRepository { AppResult.failure(AppError.Network) })

        val result = useCase(ManualRateInput(BigDecimal.ZERO, asOf))

        assertEquals(AppError.InvalidRate, result.errorOrNull())
    }
}
