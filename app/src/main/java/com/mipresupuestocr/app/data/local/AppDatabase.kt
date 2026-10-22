package com.mipresupuestocr.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CategoryEntity::class,
        TransactionEntity::class,
        HoldingEntity::class,
        RateCacheEntity::class,
        QuoteCacheEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun holdingDao(): HoldingDao
    abstract fun rateCacheDao(): RateCacheDao
    abstract fun quoteCacheDao(): QuoteCacheDao

    companion object {
        const val NAME = "mipresupuestocr.db"
    }
}
