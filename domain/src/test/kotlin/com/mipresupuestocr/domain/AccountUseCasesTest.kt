package com.mipresupuestocr.domain

import com.mipresupuestocr.domain.model.Account
import com.mipresupuestocr.domain.model.AccountType
import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.Money
import com.mipresupuestocr.domain.model.Transaction
import com.mipresupuestocr.domain.model.TransactionType
import com.mipresupuestocr.domain.usecase.CalculateAccountBalancesUseCase
import com.mipresupuestocr.domain.usecase.SaveAccountUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AccountUseCasesTest {

    private val calculate = CalculateAccountBalancesUseCase()

    private val checking = Account(
        id = 1,
        name = "Corriente",
        type = AccountType.CHECKING,
        openingBalance = Money.ofMajor("100000", CurrencyCode.CRC),
    )
    private val savings = Account(
        id = 2,
        name = "Ahorros",
        type = AccountType.SAVINGS,
        openingBalance = Money.ofMajor("500000", CurrencyCode.CRC),
    )

    private fun entry(
        amount: String,
        type: TransactionType,
        accountId: Long?,
        currency: CurrencyCode = CurrencyCode.CRC,
    ) = Transaction(
        type = type,
        amount = Money.ofMajor(amount, currency),
        date = LocalDate.of(2026, 10, 5),
        accountId = accountId,
    )

    @Test
    fun `balance is opening balance plus income minus expenses`() {
        val balances = calculate(
            listOf(checking, savings),
            listOf(
                entry("25000", TransactionType.INCOME, accountId = 1),
                entry("40000", TransactionType.EXPENSE, accountId = 1),
                entry("1000", TransactionType.EXPENSE, accountId = 2),
            ),
        )

        val byName = balances.associateBy { it.account.name }
        assertEquals(85_000_00L, byName.getValue("Corriente").balance.amountMinor)
        assertEquals(499_000_00L, byName.getValue("Ahorros").balance.amountMinor)
        assertEquals(2, byName.getValue("Corriente").entryCount)
        assertEquals(1, byName.getValue("Ahorros").entryCount)
    }

    @Test
    fun `an overdrawing account shows a negative balance`() {
        val balances = calculate(
            listOf(checking),
            listOf(entry("150000", TransactionType.EXPENSE, accountId = 1)),
        )

        assertTrue(balances.single().balance.isNegative)
        assertEquals(-50_000_00L, balances.single().balance.amountMinor)
    }

    @Test
    fun `entries without an account do not land on any balance`() {
        val balances = calculate(
            listOf(checking),
            listOf(entry("9000", TransactionType.EXPENSE, accountId = null)),
        )

        assertEquals(100_000_00L, balances.single().balance.amountMinor)
        assertEquals(0, balances.single().entryCount)
    }

    @Test
    fun `an entry in another currency is not added to a colón balance`() {
        val balances = calculate(
            listOf(checking),
            listOf(entry("50", TransactionType.INCOME, accountId = 1, currency = CurrencyCode.USD)),
        )

        assertEquals(100_000_00L, balances.single().balance.amountMinor)
    }

    @Test
    fun `saving trims the name and keeps a zero opening balance`() = runTest {
        val repository = FakeAccountRepository()
        val save = SaveAccountUseCase(repository)

        val result = save(Account(name = "  Ahorro viaje  ", type = AccountType.SAVINGS))

        val id = result.valueOrThrow()
        val stored = repository.getById(id)!!
        assertEquals("Ahorro viaje", stored.name)
        assertEquals(0L, stored.openingBalance.amountMinor)
    }

    @Test
    fun `blank account names are rejected`() = runTest {
        val result = SaveAccountUseCase(FakeAccountRepository())(Account(name = "   "))

        assertEquals(AppError.InvalidAccountName, result.errorOrNull())
    }

    @Test
    fun `a negative opening balance is rejected`() = runTest {
        val account = Account(
            name = "Corriente",
            openingBalance = Money.ofMajor("-10", CurrencyCode.CRC),
        )

        val result = SaveAccountUseCase(FakeAccountRepository())(account)

        assertEquals(AppError.InvalidAmount, result.errorOrNull())
    }

    @Test
    fun `accounts are listed by their sort order`() {
        val second = checking.copy(id = 3, name = "Tercera", sortOrder = 2)
        val first = checking.copy(id = 4, name = "Primera", sortOrder = 1)

        val balances = calculate(listOf(second, first), emptyList())

        assertEquals(listOf("Primera", "Tercera"), balances.map { it.account.name })
        assertNull(balances.firstOrNull { it.account.id == 99L })
    }
}
