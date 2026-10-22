package com.mipresupuestocr.app.data.remote

import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.AppResult
import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.ExchangeRate
import com.mipresupuestocr.domain.provider.ExchangeRateProvider
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ExchangeRate-API open access endpoint (no key required, attribution required).
 * Docs: https://www.exchangerate-api.com/docs/free
 */
@Serializable
data class LatestRatesDto(
    val result: String? = null,
    val provider: String? = null,
    @SerialName("time_last_update_unix") val updatedAtUnix: Long? = null,
    @SerialName("base_code") val baseCode: String? = null,
    val rates: Map<String, Double> = emptyMap(),
    @SerialName("error-type") val errorType: String? = null,
)

interface OpenErApiService {
    @GET("v6/latest/{base}")
    suspend fun latest(@Path("base") base: String): Response<LatestRatesDto>
}

/**
 * Reference USD/CRC rate provider. This is a blended market reference rate: it is deliberately
 * never presented as an official central-bank or BAC rate.
 */
@Singleton
class OpenErApiExchangeRateProvider @Inject constructor(
    private val service: OpenErApiService,
) : ExchangeRateProvider {

    override val sourceName: String = SOURCE_NAME

    override suspend fun getLatestRate(
        base: CurrencyCode,
        quote: CurrencyCode,
    ): AppResult<ExchangeRate> {
        if (base != CurrencyCode.USD) return AppResult.failure(AppError.UnsupportedCurrency)

        val response = try {
            service.latest(base.isoCode)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            return AppResult.failure(error.toAppError())
        }

        if (!response.isSuccessful) {
            return AppResult.failure(
                if (response.code() == 404) AppError.UnsupportedCurrency else httpStatusError(response.code()),
            )
        }
        val body = response.body() ?: return AppResult.failure(AppError.EmptyResponse)
        if (body.result != null && body.result != "success") {
            return AppResult.failure(mapResultError(body.errorType))
        }

        val value = body.rates[quote.isoCode] ?: return AppResult.failure(AppError.UnsupportedCurrency)
        val updatedAt = body.updatedAtUnix?.takeIf { it > 0 }
            ?: return AppResult.failure(AppError.MalformedResponse)
        if (value <= 0.0) return AppResult.failure(AppError.InvalidRate)

        return AppResult.success(
            ExchangeRate(
                base = base,
                quote = quote,
                rate = BigDecimal.valueOf(value),
                asOf = Instant.ofEpochSecond(updatedAt),
                source = sourceName,
            ),
        )
    }

    override suspend fun getSupportedCurrencies(): AppResult<Set<CurrencyCode>> {
        val response = try {
            service.latest(CurrencyCode.USD.isoCode)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            return AppResult.failure(error.toAppError())
        }
        if (!response.isSuccessful) return AppResult.failure(httpStatusError(response.code()))
        val body = response.body() ?: return AppResult.failure(AppError.EmptyResponse)
        if (body.rates.isEmpty()) return AppResult.failure(AppError.EmptyResponse)

        return AppResult.success(
            body.rates.keys.mapNotNull(CurrencyCode::fromIsoCode).toSet(),
        )
    }

    private fun mapResultError(type: String?): AppError = when (type) {
        "invalid-key" -> AppError.InvalidApiKey
        "quota-reached" -> AppError.QuotaExceeded
        "unsupported-code" -> AppError.UnsupportedCurrency
        "malformed-request" -> AppError.MalformedResponse
        "unknown-code" -> AppError.Unknown
        else -> AppError.MalformedResponse
    }

    companion object {
        const val SOURCE_NAME = "open.er-api.com"
        const val BASE_URL = "https://open.er-api.com/"
    }
}
