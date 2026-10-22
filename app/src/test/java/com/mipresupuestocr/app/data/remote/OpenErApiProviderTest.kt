package com.mipresupuestocr.app.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.AppResult
import com.mipresupuestocr.domain.model.CurrencyCode
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
import java.time.Instant

/** Contract tests for the reference-rate provider; no real network access. */
class OpenErApiProviderTest {

    private val server = MockWebServer()
    private lateinit var provider: OpenErApiExchangeRateProvider

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
        provider = OpenErApiExchangeRateProvider(retrofit.create(OpenErApiService::class.java))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `maps a successful response to a domain rate`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "result": "success",
                  "provider": "https://www.exchangerate-api.com",
                  "time_last_update_unix": 1761120000,
                  "base_code": "USD",
                  "rates": {"USD": 1, "CRC": 512.34}
                }
                """.trimIndent(),
            ),
        )

        val result = provider.getLatestRate(CurrencyCode.USD, CurrencyCode.CRC)

        val rate = (result as AppResult.Success).value
        assertEquals(0, rate.rate.compareTo(java.math.BigDecimal("512.34")))
        assertEquals(Instant.ofEpochSecond(1761120000), rate.asOf)
        assertEquals(CurrencyCode.USD, rate.base)
        assertEquals(CurrencyCode.CRC, rate.quote)
        assertEquals(OpenErApiExchangeRateProvider.SOURCE_NAME, rate.source)
    }

    @Test
    fun `reports a rate limit as RateLimited`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))

        val result = provider.getLatestRate(CurrencyCode.USD, CurrencyCode.CRC)

        assertEquals(AppError.RateLimited, result.errorOrNull())
    }

    @Test
    fun `reports an invalid key from the error payload`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"result": "error", "error-type": "invalid-key"}""",
            ),
        )

        val result = provider.getLatestRate(CurrencyCode.USD, CurrencyCode.CRC)

        assertEquals(AppError.InvalidApiKey, result.errorOrNull())
    }

    @Test
    fun `reports malformed json instead of crashing`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>not json</html>"))

        val result = provider.getLatestRate(CurrencyCode.USD, CurrencyCode.CRC)

        assertEquals(AppError.MalformedResponse, result.errorOrNull())
    }

    @Test
    fun `reports an unsupported quote currency`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "result": "success",
                  "time_last_update_unix": 1761120000,
                  "base_code": "USD",
                  "rates": {"USD": 1}
                }
                """.trimIndent(),
            ),
        )

        val result = provider.getLatestRate(CurrencyCode.USD, CurrencyCode.CRC)

        assertEquals(AppError.UnsupportedCurrency, result.errorOrNull())
    }

    @Test
    fun `reports an empty body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        val result = provider.getLatestRate(CurrencyCode.USD, CurrencyCode.CRC)

        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun `lists supported currencies`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "result": "success",
                  "time_last_update_unix": 1761120000,
                  "rates": {"USD": 1, "CRC": 512.34, "EUR": 0.92}
                }
                """.trimIndent(),
            ),
        )

        val currencies = provider.getSupportedCurrencies().getOrNull()

        assertEquals(setOf(CurrencyCode.USD, CurrencyCode.CRC), currencies)
    }
}
