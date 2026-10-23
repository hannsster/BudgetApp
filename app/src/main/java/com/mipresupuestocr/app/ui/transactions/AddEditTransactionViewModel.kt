package com.mipresupuestocr.app.ui.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipresupuestocr.app.ui.navigation.Routes
import com.mipresupuestocr.app.ui.util.parseAmount
import com.mipresupuestocr.domain.model.Account
import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.AppResult
import com.mipresupuestocr.domain.model.Category
import com.mipresupuestocr.domain.model.CategoryBucket
import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.Money
import com.mipresupuestocr.domain.model.Transaction
import com.mipresupuestocr.domain.model.TransactionType
import com.mipresupuestocr.domain.repository.AccountRepository
import com.mipresupuestocr.domain.repository.CategoryRepository
import com.mipresupuestocr.domain.repository.TransactionRepository
import com.mipresupuestocr.domain.usecase.DeleteTransactionUseCase
import com.mipresupuestocr.domain.usecase.SaveTransactionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class TransactionFormState(
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val date: LocalDate,
    val note: String = "",
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val error: AppError? = null,
    val isSaving: Boolean = false,
    val finished: Boolean = false,
    val deleted: Boolean = false,
)

@HiltViewModel
class AddEditTransactionViewModel @Inject constructor(
    private val    transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val saveTransaction: SaveTransactionUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
    clock: Clock,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val transactionId: Long =
        savedStateHandle.get<Long>(Routes.TRANSACTION_ID_ARG) ?: Routes.NEW_TRANSACTION_ID

    private val form = MutableStateFlow(
        TransactionFormState(
            date = LocalDate.now(clock),
            isEditing = transactionId > 0,
        ),
    )

    private val allCategories = categoryRepository.observeAll()
    private val allAccounts = accountRepository.observeAll()

    val uiState: StateFlow<TransactionFormState> = combine(
        form,
        allCategories,
        allAccounts,
    ) { state, categories, accounts ->
        state.copy(
            isLoading = false,
            categories = categories.filter { category ->
                when (state.type) {
                    TransactionType.EXPENSE -> category.bucket != CategoryBucket.INCOME
                    TransactionType.INCOME -> category.bucket == CategoryBucket.INCOME
                }
            },
            accounts = accounts,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = form.value,
    )

    init {
        if (transactionId > 0) {
            viewModelScope.launch {
                val existing = transactionRepository.getById(transactionId)
                if (existing != null) {
                    form.update {
                        it.copy(
                            isEditing = true,
                            type = existing.type,
                            amountText = plainAmount(existing.amount),
                            categoryId = existing.categoryId,
                            accountId = existing.accountId,
                            date = existing.date,
                            note = existing.note,
                        )
                    }
                }
            }
        }
    }

    fun setType(type: TransactionType) = form.update {
        if (it.type == type) it else it.copy(type = type, categoryId = null)
    }

    fun setAmount(text: String) = form.update {
        it.copy(amountText = text, error = null)
    }

    fun setCategory(categoryId: Long?) = form.update { it.copy(categoryId = categoryId) }

    fun setAccount(accountId: Long?) = form.update { it.copy(accountId = accountId) }

    fun setDate(date: LocalDate) = form.update { it.copy(date = date) }

    fun setNote(note: String) = form.update { it.copy(note = note.take(240)) }

    fun save() {
        val amount = parseAmount(form.value.amountText)
        if (amount == null || amount.signum() <= 0) {
            form.update { it.copy(error = AppError.InvalidAmount) }
            return
        }

        viewModelScope.launch {
            form.update { it.copy(isSaving = true, error = null) }
            val transaction = Transaction(
                id = if (transactionId > 0) transactionId else 0L,
                type = form.value.type,
                amount = Money.of(amount, CurrencyCode.CRC),
                categoryId = form.value.categoryId,
                date = form.value.date,
                note = form.value.note,
                accountId = form.value.accountId,
            )

            when (val result = saveTransaction(transaction)) {
                is AppResult.Success -> form.update { it.copy(isSaving = false, finished = true) }
                is AppResult.Failure -> form.update { it.copy(isSaving = false, error = result.error) }
            }
        }
    }

    fun delete() {
        if (transactionId <= 0) return
        viewModelScope.launch {
            deleteTransaction(transactionId)
            form.update { it.copy(deleted = true) }
        }
    }

    fun dismissError() = form.update { it.copy(error = null) }

    private fun plainAmount(amount: Money): String =
        BigDecimal.valueOf(amount.amountMinor, amount.currency.decimals)
            .stripTrailingZeros()
            .toPlainString()
}
