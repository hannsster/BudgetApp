package com.mipresupuestocr.app.di

import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.mipresupuestocr.app.BuildConfig
import com.mipresupuestocr.app.data.remote.MarketDataConfig
import com.mipresupuestocr.app.data.remote.OpenErApiExchangeRateProvider
import com.mipresupuestocr.app.data.remote.OpenErApiService
import com.mipresupuestocr.app.data.remote.TwelveDataMarketQuoteProvider
import com.mipresupuestocr.app.data.remote.TwelveDataService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

private const val EXCHANGE_RETROFIT = "exchangeRateRetrofit"
private const val MARKET_RETROFIT = "marketDataRetrofit"

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logger = HttpLoggingInterceptor { message ->
            Log.d("MiPresupuestoHttp", message.redactSecrets())
        }
        // BASIC never logs bodies; the redaction still protects keys carried in query strings.
        logger.level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }

        return OkHttpClient.Builder()
            .addInterceptor(logger)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named(MARKET_RETROFIT)
    fun provideMarketRetrofit(json: Json, client: OkHttpClient): Retrofit =
        retrofit(TwelveDataMarketQuoteProvider.BASE_URL, json, client)

    @Provides
    @Singleton
    @Named(EXCHANGE_RETROFIT)
    fun provideExchangeRetrofit(json: Json, client: OkHttpClient): Retrofit =
        retrofit(OpenErApiExchangeRateProvider.BASE_URL, json, client)

    @Provides
    @Singleton
    fun provideExchangeService(@Named(EXCHANGE_RETROFIT) retrofit: Retrofit): OpenErApiService =
        retrofit.create(OpenErApiService::class.java)

    @Provides
    @Singleton
    fun provideMarketService(@Named(MARKET_RETROFIT) retrofit: Retrofit): TwelveDataService =
        retrofit.create(TwelveDataService::class.java)

    /**
     * The key comes from local.properties at build time. A blank value is a supported state: the
     * app then shows a setup card instead of failing.
     */
    @Provides
    @Singleton
    fun provideMarketDataConfig(): MarketDataConfig =
        MarketDataConfig(BuildConfig.TWELVE_DATA_API_KEY)

    private fun retrofit(baseUrl: String, json: Json, client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}

private val SECRET_QUERY_PARAM = Regex("((?:apikey|api_key|token|key)=)[^&\\s]+", RegexOption.IGNORE_CASE)

private fun String.redactSecrets(): String = SECRET_QUERY_PARAM.replace(this) { match ->
    "${match.groupValues[1]}<redacted>"
}
