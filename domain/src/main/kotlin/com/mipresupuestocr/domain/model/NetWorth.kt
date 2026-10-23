package com.mipresupuestocr.domain.model

/**
 * Everything the user holds, added up in colones: the accounts' balances plus the portfolio.
 *
 * [total] always has a value, but it only includes the portfolio when it could be valued (priced
 * quotes and a USD/CRC rate). [isPartial] tells the UI when the figure is a lower bound, so the
 * dashboard can say so instead of quietly showing an amount that looks complete.
 */
data class NetWorth(
    val accountsTotal: Money,
    val portfolioCrc: Money?,
    val total: Money,
    val isPartial: Boolean,
)
