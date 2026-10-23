package com.mipresupuestocr.domain.model

/**
 * Everything the user holds, added up in colones: the accounts' balances, the portfolio, and the
 * money that sits only in "available" because no account was chosen for it.
 *
 * [total] always has a value, but it only includes the portfolio when it could be valued (priced
 * quotes and a USD/CRC rate). [isPartial] tells the UI when the figure is a lower bound, so the
 * dashboard can say so instead of quietly showing an amount that looks complete.
 *
 * [unassigned] is what a paycheck recorded as "sin cuenta" leaves behind: income minus expenses for
 * entries that belong to no account. Adding it keeps that money in the total without counting it
 * twice, since anything assigned to an account is already in that account's balance.
 */
data class NetWorth(
    val accountsTotal: Money,
    val portfolioCrc: Money?,
    val unassigned: Money,
    val total: Money,
    val isPartial: Boolean,
)
