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

@Entity(
    tableName = "transactions",
    indices = [Index("dateEpochDay"), Index("categoryId")],
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
