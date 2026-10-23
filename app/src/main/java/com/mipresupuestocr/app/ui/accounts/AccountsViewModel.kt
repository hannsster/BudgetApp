package com.mipresupuestocr.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipresupuestocr.app.ui.util.parseAmount
import com.mipresupuestocr.domain.model.Account
import com.mipresupuestocr.domain.model.AccountBalance
import com.mipresupuestocr.domain.model.AccountTransfer
import com.mipresupuestocr.domain.model.AccountType
import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.AppResult
import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.Money
import com.mipresupuestocr.domain.repository.AccountRepository
import com.mipresupuestocr.domain.repository.TransactionRepository
import com.mipresupuestocr.domain.repository.TransferRepository
import com.mipresupuestocr.domain.usecase.CalculateAccountBalancesUseCase
import com.mipresupuestocr.domain.usecase.DeleteAccountTransferUseCase
import com.mipresupuestocr.domain.usecase.DeleteAccountUseCase
import com.mipresupuestocr.domain.usecase.SaveAccountUseCase
import com.mipresupuestocr.domain.usecase.TransferBetweenAccountsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class AccountsUiState(
    val isLoading: Boolean = true,
    val balances: List<AccountBalance> = emptyList(),
    val total: Money = Money.zero(CurrencyCode.CRC),
    val transfers: List<AccountTransfer> = emptyList(),
    val error: AppError? = null,
    val message: AccountsMessage? = null,
)

sealed interface AccountsMessage {
    data object TransferSaved : AccountsMessage
    data object TransferDeleted : AccountsMessage
}

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    transactionRepository: TransactionRepository,
    transferRepository: TransferRepository,
    private val saveAccount: SaveAccountUseCase,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val transferBetweenAccounts: TransferBetweenAccountsUseCase,
    private val deleteTransferUseCase: DeleteAccountTransferUseCase,
    calculateBalances: CalculateAccountBalancesUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val error = MutableStateFlow<AppError?>(null)
    private val message = MutableStateFlow<AccountsMessage?>(null)

    private data class Balances(
        val rows: List<AccountBalance>,
        val transfers: List<AccountTransfer>,
    )

    private val balances = combine(
        accountRepository.observeAll(),
        transactionRepository.observeAll(),
        transferRepository.observeAll(),
    ) { accounts, transactions, transfers ->
        Balances(
            rows = calculateBalances(accounts, transactions, transfers),
            transfers = transfers,
        )
    }

    val uiState: StateFlow<AccountsUiState> = combine(
        balances,
        error,
        message,
    ) { current, currentError, currentMessage ->
        AccountsUiState(
            isLoading = false,
            balances = current.rows,
            total = Money(current.rows.sumOf { it.balance.amountMinor }, CurrencyCode.CRC),
            transfers = current.transfers,
            error = currentError,
            message = currentMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AccountsUiState(),
    )

    /**
     * Creates or updates an account. A blank starting balance means zero, so the user only has to
     * type a figure when the account already held money.
     */
    fun save(id: Long, name: String, type: AccountType, openingBalanceText: String) {
        val opening = if (openingBalanceText.isBlank()) {
            BigDecimal.ZERO
        } else {
            parseAmount(openingBalanceText)
        }
        if (opening == null) {
            error.value = AppError.InvalidAmount
            return
        }

        viewModelScope.launch {
            error.value = null
            val existing = if (id > 0) accountRepository.getById(id) else null
            val account = Account(
                id = id,
                name = name,
                type = type,
                openingBalance = Money.of(opening, CurrencyCode.CRC),
                sortOrder = existing?.sortOrder ?: 0,
            )
            when (val result = saveAccount(account)) {
                is AppResult.Failure -> error.value = result.error
                is AppResult.Success -> Unit
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            error.value = null
            runCatching { deleteAccountUseCase(id) }
                .onFailure { error.value = AppError.Unknown }
        }
    }

    /**
     * Moves money between two accounts. A bad amount is reported before anything is written; a
     * transfer to the same account is refused by the use case.
     */
    fun transfer(fromAccountId: Long, toAccountId: Long, amountText: String) {
        val amount = parseAmount(amountText)
        if (amount == null) {
            error.value = AppError.InvalidAmount
            return
        }

        viewModelScope.launch {
            error.value = null
            val result = transferBetweenAccounts(
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                amount = Money.of(amount, CurrencyCode.CRC),
                date = LocalDate.now(clock),
            )
            when (result) {
                is AppResult.Success -> message.value = AccountsMessage.TransferSaved
                is AppResult.Failure -> error.value = result.error
            }
        }
    }

    fun deleteTransfer(id: Long) {
        viewModelScope.launch {
            error.value = null
            runCatching { deleteTransferUseCase(id) }
                .onSuccess { message.value = AccountsMessage.TransferDeleted }
                .onFailure { error.value = AppError.Unknown }
        }
    }

    fun dismissError() {
        error.value = null
    }

    fun consumeMessage() {
        message.value = null
    }
}
