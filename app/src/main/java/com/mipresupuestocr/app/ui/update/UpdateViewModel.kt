package com.mipresupuestocr.app.ui.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipresupuestocr.app.BuildConfig
import com.mipresupuestocr.app.data.remote.AvailableUpdate
import com.mipresupuestocr.app.data.update.UpdateRepository
import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class UpdatePhase { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY }

data class UpdateUiState(
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val update: AvailableUpdate? = null,
    val progress: Float = 0f,
    val bytesRead: Long = 0L,
    val totalBytes: Long = 0L,
    val apkPath: String? = null,
    val error: AppError? = null,
)

/**
 * Checks GitHub Releases for a newer build and downloads its APK. Installing is done by the screen
 * through the system package installer, so the user always confirms and app data is preserved.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val repository: UpdateRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    fun check() {
        if (_uiState.value.phase == UpdatePhase.CHECKING || _uiState.value.phase == UpdatePhase.DOWNLOADING) {
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(phase = UpdatePhase.CHECKING, error = null) }
            when (val result = repository.checkForUpdate(BuildConfig.VERSION_NAME)) {
                is AppResult.Success -> _uiState.update { current ->
                    val update = result.value
                    current.copy(
                        phase = if (update == null) UpdatePhase.UP_TO_DATE else UpdatePhase.AVAILABLE,
                        update = update,
                        apkPath = null,
                        progress = 0f,
                    )
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(phase = UpdatePhase.IDLE, error = result.error)
                }
            }
        }
    }

    fun download() {
        val update = _uiState.value.update ?: return
        if (_uiState.value.phase == UpdatePhase.DOWNLOADING) return

        // One cached file per version; a newer release simply writes a different name.
        val target = File(context.cacheDir, "updates/mipresupuesto-cr-${update.version}.apk")

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    phase = UpdatePhase.DOWNLOADING,
                    progress = 0f,
                    bytesRead = 0L,
                    totalBytes = update.sizeBytes,
                    error = null,
                )
            }
            val result = repository.download(update, target) { read, total ->
                _uiState.update { state ->
                    state.copy(
                        bytesRead = read,
                        totalBytes = total,
                        progress = if (total > 0) (read.toFloat() / total).coerceIn(0f, 1f) else 0f,
                    )
                }
            }
            when (result) {
                is AppResult.Success -> _uiState.update {
                    it.copy(phase = UpdatePhase.READY, apkPath = result.value.absolutePath, progress = 1f)
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(phase = UpdatePhase.AVAILABLE, error = result.error)
                }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
