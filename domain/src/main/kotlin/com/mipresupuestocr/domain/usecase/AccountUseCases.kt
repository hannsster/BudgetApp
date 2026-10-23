package com.mipresupuestocr.domain.usecase

import com.mipresupuestocr.domain.model.Account
import com.mipresupuestocr.domain.model.AccountBalance
import com.mipresupuestocr.domain.model.AccountTransfer
import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.AppResult
import com.mipresupuestocr.domain.model.Money
import com.mipresupuestocr.domain.model.Transaction
import com.mipresupuestocr.domain.model.TransactionType
import com.mipresupuestocr.domain.repository.AccountRepository

class SaveAccountUseCase(private val repository: AccountRepository) {

    suspend operator fun invoke(account: Account): AppResult<Long> {
        val name = account.name.trim()
        if (name.isEmpty()) return AppResult.failure(AppError.InvalidAccountName)
        if (account.openingBalance.isNegative) return AppResult.failure(AppError.InvalidAmount)

        val normalized = account.copy(name = name.take(MAX_NAME_LENGTH))
        return AppResult.success(repository.upsert(normalized))
    }

    private companion object {
        const val MAX_NAME_LENGTH = 40
    }
}

/**
 * Deleting an account never deletes history: the repository only detaches its entries, so the
 * budget totals stay exactly as they were.
 */
class DeleteAccountUseCase(private val repository: AccountRepository) {
    suspend operator fun invoke(id: Long) = repository.delete(id)
}

/**
 * Current balance per account: its opening balance plus its income, minus its expenses, plus money
 * transferred in and minus money transferred out.
 *
 * Entries in another currency are ignored instead of being added to a balance they do not belong
 * to; the app records entries in colones, so this only guards against future mixed-currency data.
 */
class CalculateAccountBalancesUseCase {

    operator fun invoke(
        accounts: List<Account>,
        transactions: List<Transaction>,
        transfers: List<AccountTransfer> = emptyList(),
    ): List<AccountBalance> {
        val byAccount = transactions
            .filter { it.accountId != null }
            .groupBy { it.accountId }

        return accounts
            .sortedBy { it.sortOrder }
            .map { account ->
                val currency = account.openingBalance.currency
                val entries = byAccount[account.id].orEmpty().filter { it.amount.currency == currency }
                val entryDelta = entries.sumOf { entry ->
                    when (entry.type) {
                        TransactionType.INCOME -> entry.amount.amountMinor
                        TransactionType.EXPENSE -> -entry.amount.amountMinor
                    }
                }
                val incoming = transfers.filter {
                    it.toAccountId == account.id && it.amount.currency == currency
                }
                val outgoing = transfers.filter {
                    it.fromAccountId == account.id && it.amount.currency == currency
                }
                val transferDelta =
                    incoming.sumOf { it.amount.amountMinor } - outgoing.sumOf { it.amount.amountMinor }

                AccountBalance(
                    account = account,
                    balance = account.openingBalance + Money(entryDelta + transferDelta, currency),
                    entryCount = entries.size + incoming.size + outgoing.size,
                )
            }
    }
}
