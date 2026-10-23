package com.mipresupuestocr.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipresupuestocr.app.ui.util.parseAmount
import com.mipresupuestocr.domain.model.Account
import com.mipresupuestocr.domain.model.AccountBalance
import com.mipresupuestocr.domain.model.AccountType
import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.AppResult
import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.Money
import com.mipresupuestocr.domain.repository.AccountRepository
import com.mipresupuestocr.domain.repository.TransactionRepository
import com.mipresupuestocr.domain.usecase.CalculateAccountBalancesUseCase
import com.mipresupuestocr.domain.usecase.DeleteAccountUseCase
import com.mipresupuestocr.domain.usecase.SaveAccountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class AccountsUiState(
    val isLoading: Boolean = true,
    val balances: List<AccountBalance> = emptyList(),
    val total: Money = Money.zero(CurrencyCode.CRC),
    val error: AppError? = null,
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    transactionRepository: TransactionRepository,
    private val saveAccount: SaveAccountUseCase,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    calculateBalances: CalculateAccountBalancesUseCase,
) : ViewModel() {

    private val error = MutableStateFlow<AppError?>(null)

    private val balances = combine(
        accountRepository.observeAll(),
        transactionRepository.observeAll(),
    ) { accounts, transactions ->
        calculateBalances(accounts, transactions)
    }

    val uiState: StateFlow<AccountsUiState> = combine(balances, error) { rows, currentError ->
        AccountsUiState(
            isLoading = false,
            balances = rows,
            total = Money(rows.sumOf { it.balance.amountMinor }, CurrencyCode.CRC),
            error = currentError,
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

    fun dismissError() {
        error.value = null
    }
}
