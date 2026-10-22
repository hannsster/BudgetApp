package com.mipresupuestocr.app.data.remote

import com.mipresupuestocr.app.data.settings.FakeMarketDataKeySource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The installed APK has no build-time key, so the key a user types in Settings must be the one the
 * provider actually uses. These tests pin that behaviour, plus the fallback for preconfigured builds.
 */
class MarketDataConfigTest {

    @Test
    fun `key saved in settings is the one used`() = runTest {
        val source = FakeMarketDataKeySource()
        val config = MarketDataConfig(source, buildTimeKey = "")

        config.saveApiKey("  device-key  ")

        assertEquals("device-key", config.apiKey())
        assertTrue(config.observeConfigured().first())
    }

    @Test
    fun `no key anywhere means not configured`() = runTest {
        val config = MarketDataConfig(FakeMarketDataKeySource(), buildTimeKey = "")

        assertNull(config.apiKey())
        assertFalse(config.observeConfigured().first())
    }

    @Test
    fun `build time key is only a fallback`() = runTest {
        val config = MarketDataConfig(FakeMarketDataKeySource(), buildTimeKey = "ci-key")
        assertEquals("ci-key", config.apiKey())

        config.saveApiKey("device-key")
        assertEquals("device-key", config.apiKey())
    }

    @Test
    fun `clearing the key falls back and reports unconfigured`() = runTest {
        val config = MarketDataConfig(FakeMarketDataKeySource(), buildTimeKey = "")
        config.saveApiKey("device-key")

        config.clearApiKey()

        assertNull(config.apiKey())
        assertFalse(config.observeConfigured().first())
    }

    @Test
    fun `a blank save removes the key`() = runTest {
        val source = FakeMarketDataKeySource()
        val config = MarketDataConfig(source, buildTimeKey = "")
        config.saveApiKey("device-key")

        config.saveApiKey("   ")

        assertEquals("", source.read())
        assertNull(config.apiKey())
    }
}
