package com.mipresupuestocr.app.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mipresupuestocr.app.R
import com.mipresupuestocr.domain.model.AccountType

/** Localized label for an account type, shared by the accounts screen and the entry form. */
@Composable
fun accountTypeLabel(type: AccountType): String = stringResource(
    when (type) {
        AccountType.CHECKING -> R.string.account_type_checking
        AccountType.SAVINGS -> R.string.account_type_savings
        AccountType.CASH -> R.string.account_type_cash
        AccountType.INVESTMENT -> R.string.account_type_investment
    },
)
