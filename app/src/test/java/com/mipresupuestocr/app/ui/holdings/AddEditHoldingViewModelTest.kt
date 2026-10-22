package com.mipresupuestocr.app.ui.holdings

import androidx.lifecycle.SavedStateHandle
import com.mipresupuestocr.app.ui.navigation.Routes
import com.mipresupuestocr.domain.model.Holding
import com.mipresupuestocr.domain.repository.HoldingRepository
import com.mipresupuestocr.domain.usecase.DeleteHoldingUseCase
import com.mipresupuestocr.domain.usecase.RefreshQuotesUseCase
import com.mipresupuestocr.domain.usecase.UpsertHoldingUseCase
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

/**
 * A new holding used to start in the loading state and never leave it, so the "add ETF" screen spun
 * forever with no form. These tests pin both entry paths.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddEditHoldingViewModelTest {

    private val repository = FakeHoldingRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(holdingId: Long): AddEditHoldingViewModel =
        AddEditHoldingViewModel(
            holdingRepository = repository,
            upsertHolding = UpsertHoldingUseCase(repository),
            deleteHolding = DeleteHoldingUseCase(repository),
            refreshQuotes = mockk<RefreshQuotesUseCase>(relaxed = true),
            savedStateHandle = SavedStateHandle(mapOf(Routes.HOLDING_ID_ARG to holdingId)),
        )

    @Test
    fun `new holding shows the form immediately`() = runTest {
        val viewModel = viewModel(Routes.NEW_TRANSACTION_ID)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isEditing)
        assertEquals("", state.ticker)
    }

    @Test
    fun `existing holding loads its values and stops loading`() = runTest {
        val stored = repository.upsert(
            Holding(ticker = "VTI", shares = BigDecimal("1.5"), notes = "long term"),
        )

        val viewModel = viewModel(stored)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.isEditing)
        assertEquals("VTI", state.ticker)
        assertEquals("1.5", state.sharesText)
        assertEquals("long term", state.notes)
    }
}

private class FakeHoldingRepository : HoldingRepository {
    private val state = MutableStateFlow<List<Holding>>(emptyList())

    override fun observeAll(): Flow<List<Holding>> = state
    override suspend fun getAll(): List<Holding> = state.value
    override suspend fun getById(id: Long): Holding? = state.value.firstOrNull { it.id == id }

    override suspend fun upsert(holding: Holding): Long {
        val id = if (holding.id == 0L) (state.value.maxOfOrNull { it.id } ?: 0L) + 1 else holding.id
        state.value = state.value.filterNot { it.id == id } + holding.copy(id = id)
        return id
    }

    override suspend fun delete(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }
}
