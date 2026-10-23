package com.mipresupuestocr.app.ui.accounts

import com.mipresupuestocr.domain.model.Account
import com.mipresupuestocr.domain.model.AccountTransfer
import com.mipresupuestocr.domain.model.AccountType
import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.Money
import com.mipresupuestocr.domain.model.Transaction
import com.mipresupuestocr.domain.repository.AccountRepository
import com.mipresupuestocr.domain.repository.TransactionRepository
import com.mipresupuestocr.domain.repository.TransferRepository
import com.mipresupuestocr.domain.usecase.CalculateAccountBalancesUseCase
import com.mipresupuestocr.domain.usecase.DeleteAccountTransferUseCase
import com.mipresupuestocr.domain.usecase.DeleteAccountUseCase
import com.mipresupuestocr.domain.usecase.SaveAccountUseCase
import com.mipresupuestocr.domain.usecase.TransferBetweenAccountsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The transfer button is the one place where two account balances change at once, so these tests pin
 * the wiring between the dialog input, the use case and the figures the screen shows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountsViewModelTest {

    private val clock = Clock.fixed(
        Instant.parse("2026-10-20T15:00:00Z"),
        ZoneId.of("UTC"),
    )
    private val today = LocalDate.of(2026, 10, 20)

    private val accountRepository = FakeAccountsRepository(
        listOf(
            Account(
                id = 1,
                name = "Corriente",
                type = AccountType.CHECKING,
                openingBalance = Money.ofMajor("1000.00", CurrencyCode.CRC),
            ),
            Account(
                id = 2,
                name = "Ahorros",
                type = AccountType.SAVINGS,
                openingBalance = Money.ofMajor("500.00", CurrencyCode.CRC),
            ),
        ),
    )
    private val transactionRepository = FakeTransactionsRepository()
    private val transferRepository = FakeTransfersRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * The screen's state starts as a loading placeholder; wait for the first real emission so an
     * assertion never reads the placeholder, and fail fast instead of hanging if one never arrives.
     */
    private suspend fun AccountsViewModel.awaitState(): AccountsUiState =
        withTimeout(30_000) { uiState.first { !it.isLoading } }

    private fun viewModel() = AccountsViewModel(
        accountRepository = accountRepository,
        transactionRepository = transactionRepository,
        transferRepository = transferRepository,
        saveAccount = SaveAccountUseCase(accountRepository),
        deleteAccountUseCase = DeleteAccountUseCase(accountRepository),
        transferBetweenAccounts = TransferBetweenAccountsUseCase(transferRepository),
        deleteTransferUseCase = DeleteAccountTransferUseCase(transferRepository),
        calculateBalances = CalculateAccountBalancesUseCase(),
        clock = clock,
    )

    @Test
    fun `transferring moves the amount between the two balances`() = runTest {
        val viewModel = viewModel()

        viewModel.transfer(fromAccountId = 1, toAccountId = 2, amountText = "250,50")
        val state = viewModel.awaitState()

        val balances = state.balances.associateBy { it.account.name }
        assertEquals(74_950L, balances.getValue("Corriente").balance.amountMinor)
        assertEquals(75_050L, balances.getValue("Ahorros").balance.amountMinor)
        assertEquals("the total does not change", 150_000L, state.total.amountMinor)
        assertEquals(AccountsMessage.TransferSaved, state.message)

        val stored = transferRepository.rows.single()
        assertEquals(1L, stored.fromAccountId)
        assertEquals(2L, stored.toAccountId)
        assertEquals(25_050L, stored.amount.amountMinor)
        assertEquals(today, stored.date)
    }

    @Test
    fun `an unreadable amount is rejected without writing anything`() = runTest {
        val viewModel = viewModel()

        viewModel.transfer(fromAccountId = 1, toAccountId = 2, amountText = "no sé")
        val state = viewModel.awaitState()

        assertEquals(AppError.InvalidAmount, state.error)
        assertTrue(transferRepository.rows.isEmpty())
        assertNull(state.message)
    }

    @Test
    fun `transferring to the same account is refused`() = runTest {
        val viewModel = viewModel()

        viewModel.transfer(fromAccountId = 1, toAccountId = 1, amountText = "100")
        val state = viewModel.awaitState()

        assertEquals(AppError.SameAccountTransfer, state.error)
        assertTrue(transferRepository.rows.isEmpty())
    }

    @Test
    fun `deleting a transfer puts both balances back`() = runTest {
        val viewModel = viewModel()
        viewModel.transfer(fromAccountId = 1, toAccountId = 2, amountText = "100")
        val transferId = viewModel.awaitState().transfers.single().id

        viewModel.deleteTransfer(transferId)
        val state = viewModel.awaitState()

        val balances = state.balances.associateBy { it.account.name }
        assertEquals(100_000L, balances.getValue("Corriente").balance.amountMinor)
        assertEquals(50_000L, balances.getValue("Ahorros").balance.amountMinor)
        assertTrue(state.transfers.isEmpty())
        assertEquals(AccountsMessage.TransferDeleted, state.message)
    }

    @Test
    fun `transfers are listed newest first`() = runTest {
        val viewModel = viewModel()
        viewModel.transfer(1, 2, "10")
        viewModel.transfer(2, 1, "20")

        val state = viewModel.awaitState()

        assertEquals(2, state.transfers.size)
        assertEquals("the newest transfer is listed first", 2L, state.transfers.first().fromAccountId)
        assertNull(state.error)
    }

    private class FakeAccountsRepository(initial: List<Account>) : AccountRepository {
        private val state = MutableStateFlow(initial)

        override fun observeAll(): Flow<List<Account>> = state

        override suspend fun getById(id: Long): Account? = state.value.firstOrNull { it.id == id }

        override suspend fun upsert(account: Account): Long {
            val id = if (account.id == 0L) (state.value.maxOfOrNull { it.id } ?: 0L) + 1 else account.id
            state.value = state.value.filterNot { it.id == id } + account.copy(id = id)
            return id
        }

        override suspend fun delete(id: Long) {
            state.value = state.value.filterNot { it.id == id }
        }
    }

    private class FakeTransactionsRepository : TransactionRepository {
        private val state = MutableStateFlow<List<Transaction>>(emptyList())

        override fun observeAll(): Flow<List<Transaction>> = state

        override fun observeBetween(start: LocalDate, endInclusive: LocalDate): Flow<List<Transaction>> = state

        override suspend fun getBetween(start: LocalDate, endInclusive: LocalDate): List<Transaction> =
            state.value

        override suspend fun getById(id: Long): Transaction? = state.value.firstOrNull { it.id == id }

        override suspend fun upsert(transaction: Transaction): Long = 0L

        override suspend fun delete(id: Long) = Unit
    }

    private class FakeTransfersRepository : TransferRepository {
        private val state = MutableStateFlow<List<AccountTransfer>>(emptyList())

        val rows: List<AccountTransfer> get() = state.value

        override fun observeAll(): Flow<List<AccountTransfer>> = state

        override suspend fun save(transfer: AccountTransfer): Long {
            val id = if (transfer.id == 0L) (state.value.maxOfOrNull { it.id } ?: 0L) + 1 else transfer.id
            state.value = listOf(transfer.copy(id = id)) + state.value.filterNot { it.id == id }
            return id
        }

        override suspend fun delete(id: Long) {
            state.value = state.value.filterNot { it.id == id }
        }
    }
}
