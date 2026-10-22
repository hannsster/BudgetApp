package com.mipresupuestocr.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipresupuestocr.app.data.local.DefaultCategorySeeder
import com.mipresupuestocr.domain.model.UserSettings
import com.mipresupuestocr.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val seeder: DefaultCategorySeeder,
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsRepository.observeSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserSettings(),
        )

    init {
        viewModelScope.launch {
            runCatching { seeder.seedIfNeeded() }
        }
    }
}
