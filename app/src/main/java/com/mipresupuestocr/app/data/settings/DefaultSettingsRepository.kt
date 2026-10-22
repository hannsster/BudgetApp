package com.mipresupuestocr.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mipresupuestocr.domain.model.BudgetSplit
import com.mipresupuestocr.domain.model.PeriodType
import com.mipresupuestocr.domain.model.ThemeMode
import com.mipresupuestocr.domain.model.UserSettings
import com.mipresupuestocr.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

private object Keys {
    val periodType = stringPreferencesKey("period_type")
    val paydayAnchor = longPreferencesKey("payday_anchor_epoch_day")
    val splitNeeds = intPreferencesKey("split_needs")
    val splitWants = intPreferencesKey("split_wants")
    val splitSavings = intPreferencesKey("split_savings")
    val themeMode = stringPreferencesKey("theme_mode")
    val setupCompleted = booleanPreferencesKey("setup_completed")
    val lastRateRefresh = longPreferencesKey("last_rate_refresh")
    val lastQuoteRefresh = longPreferencesKey("last_quote_refresh")
}

@Singleton
class DefaultSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    override fun observeSettings(): Flow<UserSettings> =
        context.settingsDataStore.data.map { it.toSettings() }

    override suspend fun update(transform: (UserSettings) -> UserSettings) {
        context.settingsDataStore.edit { preferences ->
            preferences.write(transform(preferences.toSettings()))
        }
    }

    override suspend fun current(): UserSettings = observeSettings().first()

    private fun Preferences.toSettings(): UserSettings = UserSettings(
        periodType = this[Keys.periodType]
            ?.let { runCatching { PeriodType.valueOf(it) }.getOrNull() }
            ?: PeriodType.MONTHLY,
        paydayAnchor = this[Keys.paydayAnchor]?.let(LocalDate::ofEpochDay),
        split = BudgetSplit(
            needs = this[Keys.splitNeeds] ?: BudgetSplit.FIFTY_THIRTY_TWENTY.needs,
            wants = this[Keys.splitWants] ?: BudgetSplit.FIFTY_THIRTY_TWENTY.wants,
            savings = this[Keys.splitSavings] ?: BudgetSplit.FIFTY_THIRTY_TWENTY.savings,
        ),
        themeMode = this[Keys.themeMode]
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM,
        setupCompleted = this[Keys.setupCompleted] ?: false,
        lastRateRefresh = this[Keys.lastRateRefresh]?.let(Instant::ofEpochMilli),
        lastQuoteRefresh = this[Keys.lastQuoteRefresh]?.let(Instant::ofEpochMilli),
    )

    private fun androidx.datastore.preferences.core.MutablePreferences.write(settings: UserSettings) {
        this[Keys.periodType] = settings.periodType.name
        settings.paydayAnchor?.let { this[Keys.paydayAnchor] = it.toEpochDay() }
        this[Keys.splitNeeds] = settings.split.needs
        this[Keys.splitWants] = settings.split.wants
        this[Keys.splitSavings] = settings.split.savings
        this[Keys.themeMode] = settings.themeMode.name
        this[Keys.setupCompleted] = settings.setupCompleted
        settings.lastRateRefresh?.let { this[Keys.lastRateRefresh] = it.toEpochMilli() }
        settings.lastQuoteRefresh?.let { this[Keys.lastQuoteRefresh] = it.toEpochMilli() }
    }
}
