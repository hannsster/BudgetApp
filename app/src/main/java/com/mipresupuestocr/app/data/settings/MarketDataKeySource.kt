package com.mipresupuestocr.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the market-data key the user pastes into Settings.
 *
 * The app ships as an installable APK, so the key cannot come from a build-time property for the
 * person using it. It lives only in this app's private storage (`/data/data/<package>`), is never
 * echoed back to the screen, logged, or included in backups beyond the normal app data rules.
 */
interface MarketDataKeySource {
    fun observe(): Flow<String>
    suspend fun read(): String
    suspend fun write(value: String)
    suspend fun clear()
}

private val Context.marketDataStore: DataStore<Preferences> by preferencesDataStore(name = "market_data")

private val API_KEY = stringPreferencesKey("twelve_data_api_key")

@Singleton
class DataStoreMarketDataKeySource @Inject constructor(
    @ApplicationContext private val context: Context,
) : MarketDataKeySource {

    override fun observe(): Flow<String> = context.marketDataStore.data.map { it[API_KEY].orEmpty() }

    override suspend fun read(): String = observe().first().trim()

    override suspend fun write(value: String) {
        val key = value.trim()
        context.marketDataStore.edit { preferences ->
            if (key.isEmpty()) {
                preferences.remove(API_KEY)
            } else {
                preferences[API_KEY] = key
            }
        }
    }

    override suspend fun clear() {
        context.marketDataStore.edit { preferences -> preferences.remove(API_KEY) }
    }
}
