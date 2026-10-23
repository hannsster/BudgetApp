package com.mipresupuestocr.domain

import com.mipresupuestocr.domain.model.Account
import com.mipresupuestocr.domain.model.AccountBalance
import com.mipresupuestocr.domain.model.AccountType
import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.Money
import com.mipresupuestocr.domain.model.PortfolioValuation
import com.mipresupuestocr.domain.usecase.CalculateAccountBalancesUseCase
import com.mipresupuestocr.domain.usecase.CalculateNetWorthUseCase
import com.mipresupuestocr.domain.usecase.DeleteAccountTransferUseCase
import com.mipresupuestocr.domain.usecase.TransferBetweenAccountsUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TransferUseCasesTest {

    private val calculateBalances = CalculateAccountBalancesUseCase()
    private val calculateNetWorth = CalculateNetWorthUseCase()
    private val today = LocalDate.of(2026, 10, 20)

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
        openingBalance = Money.ofMajor("50000", CurrencyCode.CRC),
    )

    private fun portfolio(
        totalCrc: Money?,
        unpriced: List<String> = emptyList(),
        isEmpty: Boolean = false,
    ) = PortfolioValuation(
        positions = emptyList(),
        totalUsd = Money.zero(CurrencyCode.USD),
        totalCrc = totalCrc,
        totalCostUsd = null,
        totalUnrealizedPnlUsd = null,
        totalUnrealizedPnlPercent = null,
        unpricedTickers = unpriced,
        isEmpty = isEmpty,
    )

    @Test
    fun `moving money lowers the source and raises the destination`() = runTest {
        val repository = FakeTransferRepository()

        TransferBetweenAccountsUseCase(repository)(
            fromAccountId = checking.id,
            toAccountId = savings.id,
            amount = Money.ofMajor("25000", CurrencyCode.CRC),
            date = today,
        ).valueOrThrow()

        val balances = calculateBalances(listOf(checking, savings), emptyList(), repository.current())
            .associateBy { it.account.name }
        assertEquals(75_000_00L, balances.getValue("Corriente").balance.amountMinor)
        assertEquals(75_000_00L, balances.getValue("Ahorros").balance.amountMinor)
    }

    @Test
    fun `a transfer counts as a movement on both accounts`() = runTest {
        val repository = FakeTransferRepository()

        TransferBetweenAccountsUseCase(repository)(1, 2, Money.ofMajor("1000", CurrencyCode.CRC), today)
            .valueOrThrow()

        val balances = calculateBalances(listOf(checking, savings), emptyList(), repository.current())
        assertTrue(balances.all { it.entryCount == 1 })
    }

    @Test
    fun `total stays the same because a transfer is not income or spending`() = runTest {
        val repository = FakeTransferRepository()
        TransferBetweenAccountsUseCase(repository)(1, 2, Money.ofMajor("30000", CurrencyCode.CRC), today)
            .valueOrThrow()

        val total = calculateBalances(listOf(checking, savings), emptyList(), repository.current())
            .sumOf { it.balance.amountMinor }

        assertEquals(150_000_00L, total)
    }

    @Test
    fun `transferring to the same account is rejected`() = runTest {
        val repository = FakeTransferRepository()

        val result = TransferBetweenAccountsUseCase(repository)(
            fromAccountId = 1,
            toAccountId = 1,
            amount = Money.ofMajor("5000", CurrencyCode.CRC),
            date = today,
        )

        assertEquals(AppError.SameAccountTransfer, result.errorOrNull())
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun `a zero or negative transfer is rejected`() = runTest {
        val repository = FakeTransferRepository()
        val useCase = TransferBetweenAccountsUseCase(repository)

        val zero = useCase(1, 2, Money.ofMajor("0", CurrencyCode.CRC), today)
        val negative = useCase(1, 2, Money.ofMajor("-500", CurrencyCode.CRC), today)

        assertEquals(AppError.InvalidAmount, zero.errorOrNull())
        assertEquals(AppError.InvalidAmount, negative.errorOrNull())
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun `deleting a transfer puts both balances back`() = runTest {
        val repository = FakeTransferRepository()
        val save = TransferBetweenAccountsUseCase(repository)
        val id = save(1, 2, Money.ofMajor("20000", CurrencyCode.CRC), today).valueOrThrow()

        DeleteAccountTransferUseCase(repository)(id)

        val balances = calculateBalances(listOf(checking, savings), emptyList(), repository.current())
            .associateBy { it.account.name }
        assertEquals(100_000_00L, balances.getValue("Corriente").balance.amountMinor)
        assertEquals(50_000_00L, balances.getValue("Ahorros").balance.amountMinor)
    }

    @Test
    fun `net worth adds the portfolio to the account balances`() {
        val balances = listOf(
            AccountBalance(checking, Money.ofMajor("100000", CurrencyCode.CRC), 0),
            AccountBalance(savings, Money.ofMajor("50000", CurrencyCode.CRC), 0),
        )

        val netWorth = calculateNetWorth(
            balances,
            portfolio(totalCrc = Money.ofMajor("250000", CurrencyCode.CRC)),
        )

        assertEquals(150_000_00L, netWorth.accountsTotal.amountMinor)
        assertEquals(400_000_00L, netWorth.total.amountMinor)
        assertFalse(netWorth.isPartial)
    }

    @Test
    fun `a portfolio without a rate still shows the accounts total`() {
        val balances = listOf(AccountBalance(checking, Money.ofMajor("100000", CurrencyCode.CRC), 0))

        val netWorth = calculateNetWorth(balances, portfolio(totalCrc = null))

        assertEquals(100_000_00L, netWorth.total.amountMinor)
        assertTrue("the portfolio is left out, so the figure is partial", netWorth.isPartial)
    }

    @Test
    fun `unpriced holdings mark the total as partial`() {
        val balances = listOf(AccountBalance(checking, Money.ofMajor("100000", CurrencyCode.CRC), 0))

        val netWorth = calculateNetWorth(
            balances,
            portfolio(totalCrc = Money.ofMajor("50000", CurrencyCode.CRC), unpriced = listOf("VTI")),
        )

        assertEquals(150_000_00L, netWorth.total.amountMinor)
        assertTrue(netWorth.isPartial)
    }

    @Test
    fun `no holdings means nothing is missing`() {
        val balances = listOf(AccountBalance(checking, Money.ofMajor("100000", CurrencyCode.CRC), 0))

        val netWorth = calculateNetWorth(balances, portfolio(totalCrc = null, isEmpty = true))

        assertEquals(100_000_00L, netWorth.total.amountMinor)
        assertFalse(netWorth.isPartial)
    }
}
