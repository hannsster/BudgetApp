package com.mipresupuestocr.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val bucket: String,
    val targetMinor: Long? = null,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val type: String,
    val openingBalanceMinor: Long = 0L,
    val currency: String = "CRC",
    val sortOrder: Int = 0,
)

/**
 * Moves money between two accounts. Stored apart from `transactions` so a transfer never appears as
 * income or spending in the budget.
 */
@Entity(
    tableName = "account_transfers",
    indices = [Index("fromAccountId"), Index("toAccountId"), Index("dateEpochDay")],
)
data class AccountTransferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val fromAccountId: Long,
    val toAccountId: Long,
    val amountMinor: Long,
    val currency: String = "CRC",
    val dateEpochDay: Long,
    val note: String = "",
)

@Entity(
    tableName = "transactions",
    indices = [Index("dateEpochDay"), Index("categoryId"), Index("accountId")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: String,
    val amountMinor: Long,
    val currency: String,
    val categoryId: Long? = null,
    val dateEpochDay: Long,
    val note: String = "",
    val createdAtMillis: Long = 0L,
    val accountId: Long? = null,
)

@Entity(tableName = "holdings")
data class HoldingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val ticker: String,
    val shares: String,
    val averagePriceUsd: String? = null,
    val notes: String = "",
    val createdAtMillis: Long = 0L,
)

/**
 * One hand correction per periods figure. Kept out of `transactions` on purpose: the history stays
 * a record of what happened, while these rows explain why a total differs from it.
 */
@Entity(
    tableName = "balance_corrections",
    indices = [Index("periodStartEpochDay")],
)
data class BalanceCorrectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val periodStartEpochDay: Long,
    val kind: String,
    val categoryId: Long? = null,
    val currency: String,
    val amountMinor: Long,
)

/** Cached rates: at most one "reference" row and one "manual" row. */
@Entity(tableName = "rate_cache")
data class RateCacheEntity(
    @PrimaryKey val cacheKey: String,
    val base: String,
    val quote: String,
    val rate: String,
    val asOfMillis: Long,
    val source: String,
    val isManual: Boolean,
)

@Entity(tableName = "quote_cache")
data class QuoteCacheEntity(
    @PrimaryKey val ticker: String,
    val priceUsd: String,
    val asOfMillis: Long,
    val source: String,
)
