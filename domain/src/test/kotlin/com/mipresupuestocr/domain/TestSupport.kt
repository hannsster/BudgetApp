package com.mipresupuestocr.domain

import com.mipresupuestocr.domain.model.Account
import com.mipresupuestocr.domain.model.AccountTransfer
import com.mipresupuestocr.domain.model.AppResult
import com.mipresupuestocr.domain.model.BalanceCorrection
import com.mipresupuestocr.domain.model.Category
import com.mipresupuestocr.domain.model.CorrectionKind
import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.ExchangeRate
import com.mipresupuestocr.domain.model.Holding
import com.mipresupuestocr.domain.model.ManualRateInput
import com.mipresupuestocr.domain.model.Quote
import com.mipresupuestocr.domain.model.Transaction
import com.mipresupuestocr.domain.model.UserSettings
import com.mipresupuestocr.domain.repository.AccountRepository
import com.mipresupuestocr.domain.repository.BalanceCorrectionRepository
import com.mipresupuestocr.domain.repository.CategoryRepository
import com.mipresupuestocr.domain.repository.HoldingRepository
import com.mipresupuestocr.domain.repository.QuoteRepository
import com.mipresupuestocr.domain.repository.RateRepository
import com.mipresupuestocr.domain.repository.SettingsRepository
import com.mipresupuestocr.domain.repository.TransactionRepository
import com.mipresupuestocr.domain.repository.TransferRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

fun <T> AppResult<T>.valueOrThrow(): T = when (this) {
    is AppResult.Success -> value
    is AppResult.Failure -> throw AssertionError("Expected success but was $error")
}

class FakeTransactionRepository(initial: List<Transaction> = emptyList()) : TransactionRepository {
    private val state = MutableStateFlow(initial)
    val upserted = mutableListOf<Transaction>()

    override fun observeAll(): Flow<List<Transaction>> = state

    override fun observeBetween(start: LocalDate, endInclusive: LocalDate): Flow<List<Transaction>> =
        state.map { rows -> rows.filter { !it.date.isBefore(start) && !it.date.isAfter(endInclusive) } }

    override suspend fun getBetween(start: LocalDate, endInclusive: LocalDate): List<Transaction> =
        state.value.filter { !it.date.isBefore(start) && !it.date.isAfter(endInclusive) }

    override suspend fun getById(id: Long): Transaction? = state.value.firstOrNull { it.id == id }

    override suspend fun upsert(transaction: Transaction): Long {
        upserted += transaction
        val id = if (transaction.id == 0L) (state.value.maxOfOrNull { it.id } ?: 0L) + 1 else transaction.id
        state.value = state.value.filterNot { it.id == id } + transaction.copy(id = id)
        return id
    }

    override suspend fun delete(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }
}

class FakeTransferRepository(initial: List<AccountTransfer> = emptyList()) : TransferRepository {
    private val state = MutableStateFlow(initial)
    val saved = mutableListOf<AccountTransfer>()

    override fun observeAll(): Flow<List<AccountTransfer>> = state

    /** Current rows, so a test can feed them straight into the balance calculation. */
    fun current(): List<AccountTransfer> = state.value

    override suspend fun save(transfer: AccountTransfer): Long {
        saved += transfer
        val id = if (transfer.id == 0L) (state.value.maxOfOrNull { it.id } ?: 0L) + 1 else transfer.id
        state.value = state.value.filterNot { it.id == id } + transfer.copy(id = id)
        return id
    }

    override suspend fun delete(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }
}

class FakeAccountRepository(initial: List<Account> = emptyList()) : AccountRepository {
    private val state = MutableStateFlow(initial)

    val deleted = mutableListOf<Long>()

    override fun observeAll(): Flow<List<Account>> = state

    override suspend fun getById(id: Long): Account? = state.value.firstOrNull { it.id == id }

    override suspend fun upsert(account: Account): Long {
        val id = if (account.id == 0L) (state.value.maxOfOrNull { it.id } ?: 0L) + 1 else account.id
        state.value = state.value.filterNot { it.id == id } + account.copy(id = id)
        return id
    }

    override suspend fun delete(id: Long) {
        deleted += id
        state.value = state.value.filterNot { it.id == id }
    }
}

class FakeBalanceCorrectionRepository(
    initial: List<BalanceCorrection> = emptyList(),
) : BalanceCorrectionRepository {

    private val state = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<BalanceCorrection>> = state

    override suspend fun getAll(): List<BalanceCorrection> = state.value

    override suspend fun set(
        periodStart: LocalDate,
        kind: CorrectionKind,
        categoryId: Long?,
        amountMinor: Long,
        currency: CurrencyCode,
    ) {
        state.value = state.value.filterNot {
            it.periodStart == periodStart && it.kind == kind && it.categoryId == categoryId
        } + if (amountMinor == 0L) {
            emptyList()
        } else {
            listOf(
                BalanceCorrection(
                    periodStart = periodStart,
                    kind = kind,
                    categoryId = categoryId,
                    amountMinor = amountMinor,
                    currency = currency,
                ),
            )
        }
    }

    override suspend fun clear(periodStart: LocalDate, kind: CorrectionKind, categoryId: Long?) {
        state.value = state.value.filterNot {
            it.periodStart == periodStart && it.kind == kind && it.categoryId == categoryId
        }
    }
}

class FakeCategoryRepository(initial: List<Category> = emptyList()) : CategoryRepository {
    private val state = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Category>> = state
    override suspend fun getAll(): List<Category> = state.value
    override suspend fun upsert(category: Category): Long {
        val id = if (category.id == 0L) (state.value.maxOfOrNull { it.id } ?: 0L) + 1 else category.id
        state.value = state.value.filterNot { it.id == id } + category.copy(id = id)
        return id
    }

    override suspend fun replaceAll(categories: List<Category>) {
        state.value = categories
    }

    override suspend fun delete(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }
}

class FakeHoldingRepository(initial: List<Holding> = emptyList()) : HoldingRepository {
    private val state = MutableStateFlow(initial)

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

class FakeQuoteRepository(
    private val cached: Map<String, Quote> = emptyMap(),
    private val response: () -> AppResult<Map<String, Quote>>,
) : QuoteRepository {
    var callCount = 0
    var lastTickers: List<String> = emptyList()

    override fun observeQuotes(): Flow<Map<String, Quote>> = MutableStateFlow(cached)

    override suspend fun refresh(tickers: List<String>): AppResult<Map<String, Quote>> {
        callCount++
        lastTickers = tickers
        return response()
    }

    override suspend fun cachedQuotes(tickers: List<String>): Map<String, Quote> =
        cached.filterKeys { it in tickers }
}

class FakeRateRepository(
    private val refreshResponse: () -> AppResult<ExchangeRate>,
) : RateRepository {
    var refreshCount = 0
    var manualRate: ExchangeRate? = null

    override fun observeActiveRate(): Flow<ExchangeRate?> = MutableStateFlow(manualRate)
    override fun observeReferenceRate(): Flow<ExchangeRate?> = MutableStateFlow(null)

    override suspend fun refresh(): AppResult<ExchangeRate> {
        refreshCount++
        return refreshResponse()
    }

    override suspend fun activeRateSnapshot(): ExchangeRate? = manualRate

    override suspend fun setManualRate(input: ManualRateInput): AppResult<ExchangeRate> {
        val rate = ExchangeRate(
            base = com.mipresupuestocr.domain.model.CurrencyCode.USD,
            quote = com.mipresupuestocr.domain.model.CurrencyCode.CRC,
            rate = input.rate,
            asOf = input.asOf,
            source = "manual",
            isManual = true,
        )
        manualRate = rate
        return AppResult.success(rate)
    }

    override suspend fun clearManualRate() {
        manualRate = null
    }
}

class FakeSettingsRepository(initial: UserSettings = UserSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override fun observeSettings(): Flow<UserSettings> = state
    override suspend fun update(transform: (UserSettings) -> UserSettings) {
        state.value = transform(state.value)
    }

    override suspend fun current(): UserSettings = state.value
}
