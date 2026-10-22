package com.mipresupuestocr.app.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.AppResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import java.math.BigDecimal
import java.time.Instant

/** Contract tests for the Twelve Data quote provider; no real network access. */
class TwelveDataProviderTest {

    private val server = MockWebServer()
    private lateinit var service: TwelveDataService

    private fun provider(apiKey: String = "test-key") =
        TwelveDataMarketQuoteProvider(service, MarketDataConfig(apiKey))

    @Before
    fun setUp() {
        server.start()
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            isLenient = true
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        service = retrofit.create(TwelveDataService::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `maps a successful quote`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "symbol": "VTI",
                  "name": "Vanguard Total Stock Market ETF",
                  "currency": "USD",
                  "close": "280.12",
                  "datetime": "2026-10-22 15:59:00",
                  "timestamp": 1761148740,
                  "status": "ok"
                }
                """.trimIndent(),
            ),
        )

        val quote = provider().getQuote("vti").getOrNull()

        assertEquals("VTI", quote?.ticker)
        assertEquals(0, quote?.priceUsd?.compareTo(BigDecimal("280.12")))
        assertEquals(Instant.ofEpochSecond(1761148740), quote?.asOf)
        assertEquals("Twelve Data", quote?.source)
    }

    @Test
    fun `missing api key fails without a network call`() = runTest {
        val result = provider(apiKey = "").getQuote("VTI")

        assertEquals(AppError.MissingApiKey, result.errorOrNull())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `unknown symbol is mapped from an error payload`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"code":400,"message":"**symbol** not found: NOPE","status":"error"}""",
            ),
        )

        val result = provider().getQuote("NOPE")

        assertEquals(AppError.UnknownSymbol, result.errorOrNull())
    }

    @Test
    fun `quota responses are mapped to RateLimited`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))

        val result = provider().getQuote("VTI")

        assertEquals(AppError.RateLimited, result.errorOrNull())
    }

    @Test
    fun `invalid key responses are mapped to InvalidApiKey`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))

        val result = provider().getQuote("VTI")

        assertEquals(AppError.InvalidApiKey, result.errorOrNull())
    }

    @Test
    fun `missing timestamp is treated as malformed data`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"symbol":"VTI","currency":"USD","close":"280.12","status":"ok"}""",
            ),
        )

        val result = provider().getQuote("VTI")

        assertEquals(AppError.MalformedResponse, result.errorOrNull())
    }

    @Test
    fun `non usd instruments are rejected`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "symbol": "XETRA-ETF",
                  "currency": "EUR",
                  "close": "100.0",
                  "timestamp": 1761148740,
                  "status": "ok"
                }
                """.trimIndent(),
            ),
        )

        val result = provider().getQuote("XETRA-ETF")

        assertEquals(AppError.UnsupportedAsset, result.errorOrNull())
    }

    @Test
    fun `batch quotes keep partial successes`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"symbol":"VTI","currency":"USD","close":"280.12","timestamp":1761148740,"status":"ok"}""",
            ),
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"code":400,"message":"**symbol** not found: NOPE","status":"error"}""",
            ),
        )

        val result = provider().getQuotes(listOf("VTI", "NOPE"))

        val quotes = (result as AppResult.Success).value
        assertEquals(setOf("VTI"), quotes.keys)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `batch failure with no successes reports the first error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))

        val result = provider().getQuotes(listOf("VTI", "QQQ"))

        assertEquals(AppError.RateLimited, result.errorOrNull())
    }

    @Test
    fun `empty ticker list makes no request`() = runTest {
        val result = provider().getQuotes(emptyList())

        assertTrue(result is AppResult.Success)
        assertEquals(0, server.requestCount)
    }
}
