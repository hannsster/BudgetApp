package com.mipresupuestocr.app.data.remote

import com.mipresupuestocr.app.data.settings.MarketDataKeySource
import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.AppResult
import com.mipresupuestocr.domain.model.Quote
import com.mipresupuestocr.domain.provider.MarketQuoteProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import java.math.BigDecimal
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Twelve Data quote endpoint.
 * Docs: https://twelvedata.com/docs
 *
 * The free plan is intended for development and small personal use. The key is entered by the user
 * in Settings and stored on the device, never committed, and never logged.
 */
@Serializable
data class TwelveDataQuoteDto(
    val symbol: String? = null,
    val name: String? = null,
    val currency: String? = null,
    val close: String? = null,
    val price: String? = null,
    val datetime: String? = null,
    val timestamp: Long? = null,
    val status: String? = null,
    val code: Int? = null,
    val message: String? = null,
)

interface TwelveDataService {
    @GET("quote")
    suspend fun quote(
        @Query("symbol") symbol: String,
        @Query("apikey") apiKey: String,
    ): Response<TwelveDataQuoteDto>
}

/**
 * Resolves the market-data key at runtime.
 *
 * The key normally comes from Settings and is stored on the device, so a person who installs the
 * APK never builds anything. [buildTimeKey] is only a fallback for maintainer-built APKs that ship
 * preconfigured from CI; it is empty in normal builds.
 */
class MarketDataConfig(
    private val keySource: MarketDataKeySource,
    private val buildTimeKey: String = "",
) {
    val providerName: String get() = PROVIDER_NAME

    val hasBuildTimeKey: Boolean get() = buildTimeKey.isNotBlank()

    fun observeConfigured(): Flow<Boolean> =
        keySource.observe().map { stored -> stored.isNotBlank() || hasBuildTimeKey }

    /** The effective key, or null when the app has no key at all. */
    suspend fun apiKey(): String? =
        keySource.read().ifBlank { buildTimeKey }.takeIf { it.isNotBlank() }

    suspend fun saveApiKey(value: String) = keySource.write(value)

    suspend fun clearApiKey() = keySource.clear()

    companion object {
        const val PROVIDER_NAME = "Twelve Data"

        /** Where a user gets a free key. Shown in Settings; never fetched automatically. */
        const val SIGNUP_URL = "https://twelvedata.com/pricing"
    }
}

@Singleton
class TwelveDataMarketQuoteProvider @Inject constructor(
    private val service: TwelveDataService,
    private val config: MarketDataConfig,
) : MarketQuoteProvider {

    override val sourceName: String = MarketDataConfig.PROVIDER_NAME

    override val requiresApiKey: Boolean = true

    override suspend fun getQuote(ticker: String): AppResult<Quote> {
        val symbol = ticker.trim().uppercase(Locale.ROOT)
        if (symbol.isEmpty()) return AppResult.failure(AppError.InvalidTicker(ticker))
        val apiKey = config.apiKey() ?: return AppResult.failure(AppError.MissingApiKey)

        val response = try {
            service.quote(symbol, apiKey)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            return AppResult.failure(error.toAppError())
        }

        if (!response.isSuccessful) return AppResult.failure(httpStatusError(response.code()))
        val body = response.body() ?: return AppResult.failure(AppError.EmptyResponse)
        if (body.status == "error") return AppResult.failure(mapApiError(body))

        val price = (body.price ?: body.close)?.let { raw ->
            runCatching { BigDecimal(raw) }.getOrNull()
        } ?: return AppResult.failure(AppError.UnknownSymbol)
        if (price.signum() <= 0) return AppResult.failure(AppError.InvalidRate)

        val currency = body.currency
        if (currency != null && !currency.equals("USD", ignoreCase = true)) {
            return AppResult.failure(AppError.UnsupportedAsset)
        }

        val asOf = body.timestamp?.takeIf { it > 0 }?.let { Instant.ofEpochSecond(it) }
            ?: return AppResult.failure(AppError.MalformedResponse)

        return AppResult.success(
            Quote(
                ticker = body.symbol?.uppercase(Locale.ROOT) ?: symbol,
                priceUsd = price,
                asOf = asOf,
                source = sourceName,
            ),
        )
    }

    /**
     * Quotes are fetched one symbol at a time: the free plan allows 8 API credits per minute, so
     * sequential calls stay predictable. Partial success is returned so one bad ticker does not
     * hide the rest of the portfolio.
     */
    override suspend fun getQuotes(tickers: List<String>): AppResult<Map<String, Quote>> {
        if (tickers.isEmpty()) return AppResult.success(emptyMap())

        val quotes = LinkedHashMap<String, Quote>()
        var firstError: AppError? = null

        for (ticker in tickers.map { it.trim().uppercase(Locale.ROOT) }.distinct()) {
            when (val result = getQuote(ticker)) {
                is AppResult.Success -> quotes[result.value.ticker] = result.value
                is AppResult.Failure -> if (firstError == null) firstError = result.error
            }
        }

        return if (quotes.isNotEmpty()) {
            AppResult.success(quotes)
        } else {
            AppResult.failure(firstError ?: AppError.EmptyResponse)
        }
    }

    private fun mapApiError(dto: TwelveDataQuoteDto): AppError {
        val message = dto.message?.lowercase(Locale.ROOT).orEmpty()
        return when {
            dto.code == 429 -> AppError.RateLimited
            dto.code == 401 || dto.code == 403 -> AppError.InvalidApiKey
            dto.code == 404 -> AppError.UnknownSymbol
            message.contains("not found") || message.contains("invalid symbol") -> AppError.UnknownSymbol
            message.contains("apikey") || message.contains("api key") -> AppError.MissingApiKey
            message.contains("limit") -> AppError.RateLimited
            else -> AppError.Unknown
        }
    }

    companion object {
        const val BASE_URL = "https://api.twelvedata.com/"
    }
}
