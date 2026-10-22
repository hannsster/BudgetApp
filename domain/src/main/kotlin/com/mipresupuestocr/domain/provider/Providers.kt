package com.mipresupuestocr.domain.provider

import com.mipresupuestocr.domain.model.AppResult
import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.ExchangeRate
import com.mipresupuestocr.domain.model.Quote

/**
 * Abstraction over a USD/CRC rate source. Swap the implementation without touching the UI or the
 * repositories.
 */
interface ExchangeRateProvider {
    val sourceName: String

    suspend fun getLatestRate(base: CurrencyCode, quote: CurrencyCode): AppResult<ExchangeRate>

    suspend fun getSupportedCurrencies(): AppResult<Set<CurrencyCode>>
}

/**
 * Abstraction over an ETF quote source. Implementations must map transport failures onto
 * [com.mipresupuestocr.domain.model.AppError] values without leaking request URLs.
 */
interface MarketQuoteProvider {
    val sourceName: String

    /** True when [getQuote] cannot work until the user supplies a key in local.properties. */
    val requiresApiKey: Boolean

    suspend fun getQuote(ticker: String): AppResult<Quote>

    suspend fun getQuotes(tickers: List<String>): AppResult<Map<String, Quote>>
}
