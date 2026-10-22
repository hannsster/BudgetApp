package com.mipresupuestocr.app.data.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory stand-in for the DataStore-backed key store, so JVM tests need no Android runtime. */
class FakeMarketDataKeySource(initial: String = "") : MarketDataKeySource {

    private val state = MutableStateFlow(initial)

    override fun observe(): Flow<String> = state.asStateFlow()

    override suspend fun read(): String = state.value

    override suspend fun write(value: String) {
        state.value = value.trim()
    }

    override suspend fun clear() {
        state.value = ""
    }
}
