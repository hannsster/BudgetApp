package com.mipresupuestocr.app.ui.holdings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipresupuestocr.app.ui.navigation.Routes
import com.mipresupuestocr.app.ui.util.parseDecimal
import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.AppResult
import com.mipresupuestocr.domain.model.Holding
import com.mipresupuestocr.domain.repository.HoldingRepository
import com.mipresupuestocr.domain.usecase.DeleteHoldingUseCase
import com.mipresupuestocr.domain.usecase.RefreshQuotesUseCase
import com.mipresupuestocr.domain.usecase.UpsertHoldingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class HoldingFormState(
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val ticker: String = "",
    val sharesText: String = "",
    val averagePriceText: String = "",
    val notes: String = "",
    val error: AppError? = null,
    val isSaving: Boolean = false,
    val finished: Boolean = false,
    val deleted: Boolean = false,
)

@HiltViewModel
class AddEditHoldingViewModel @Inject constructor(
    private val holdingRepository: HoldingRepository,
    private val upsertHolding: UpsertHoldingUseCase,
    private val deleteHolding: DeleteHoldingUseCase,
    private val refreshQuotes: RefreshQuotesUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val holdingId: Long =
        savedStateHandle.get<Long>(Routes.HOLDING_ID_ARG) ?: Routes.NEW_TRANSACTION_ID

    // A new holding has nothing to load, so it must not start in the loading state; an existing
    // one stays on the spinner until its row arrives (see init).
    private val _uiState = MutableStateFlow(
        HoldingFormState(isEditing = holdingId > 0, isLoading = holdingId > 0),
    )
    val uiState: StateFlow<HoldingFormState> = _uiState.asStateFlow()

    init {
        if (holdingId > 0) {
            viewModelScope.launch {
                val existing = holdingRepository.getById(holdingId)
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        ticker = existing?.ticker.orEmpty(),
                        sharesText = existing?.shares?.stripTrailingZeros()?.toPlainString().orEmpty(),
                        averagePriceText = existing?.averagePriceUsd
                            ?.stripTrailingZeros()?.toPlainString().orEmpty(),
                        notes = existing?.notes.orEmpty(),
                    )
                }
            }
        }
    }

    fun setTicker(value: String) = _uiState.update {
        it.copy(ticker = value.uppercase().take(12), error = null)
    }

    fun setShares(value: String) = _uiState.update { it.copy(sharesText = value, error = null) }

    fun setAveragePrice(value: String) = _uiState.update {
        it.copy(averagePriceText = value, error = null)
    }

    fun setNotes(value: String) = _uiState.update { it.copy(notes = value.take(240)) }

    fun save() {
        val shares = parseDecimal(_uiState.value.sharesText)
        if (shares == null || shares.signum() <= 0) {
            _uiState.update { it.copy(error = AppError.InvalidShares) }
            return
        }
        val averagePriceText = _uiState.value.averagePriceText
        val averagePrice: BigDecimal? = if (averagePriceText.isBlank()) {
            null
        } else {
            parseDecimal(averagePriceText)?.takeIf { it.signum() > 0 }
                ?: run {
                    _uiState.update { it.copy(error = AppError.InvalidAmount) }
                    return
                }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val holding = Holding(
                id = if (holdingId > 0) holdingId else 0L,
                ticker = _uiState.value.ticker,
                shares = shares,
                averagePriceUsd = averagePrice,
                notes = _uiState.value.notes,
            )

            when (val result = upsertHolding(holding)) {
                is AppResult.Success -> {
                    // Best effort: a missing API key must not block saving the position.
                    runCatching { refreshQuotes() }
                    _uiState.update { it.copy(isSaving = false, finished = true) }
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(isSaving = false, error = result.error)
                }
            }
        }
    }

    fun delete() {
        if (holdingId <= 0) return
        viewModelScope.launch {
            deleteHolding(holdingId)
            _uiState.update { it.copy(deleted = true) }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
