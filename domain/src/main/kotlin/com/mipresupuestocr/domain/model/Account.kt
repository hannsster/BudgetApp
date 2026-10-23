package com.mipresupuestocr.domain.model

/**
 * Where money sits: a checking account, a savings account, cash in hand, or an investment account.
 *
 * Kept as a closed list so the UI can label every value and so a balance can be read at a glance
 * without asking the user to invent a hierarchy first.
 */
enum class AccountType { CHECKING, SAVINGS, CASH, INVESTMENT }

/**
 * One place the user keeps money.
 *
 * [openingBalance] is the amount the account already held when the user started tracking it; the
 * balance shown in the app is that figure plus every entry assigned to the account, so a savings
 * account does not look empty just because the app only saw this month's entries.
 */
data class Account(
    val id: Long = 0L,
    val name: String,
    val type: AccountType = AccountType.CHECKING,
    val openingBalance: Money = Money.zero(CurrencyCode.CRC),
    val sortOrder: Int = 0,
)

/** An account's current balance and how many entries fed into it. */
data class AccountBalance(
    val account: Account,
    val balance: Money,
    val entryCount: Int,
)
