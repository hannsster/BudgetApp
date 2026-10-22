package com.mipresupuestocr.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CategoryEntity::class,
        TransactionEntity::class,
        HoldingEntity::class,
        RateCacheEntity::class,
        QuoteCacheEntity::class,
        BalanceCorrectionEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun holdingDao(): HoldingDao
    abstract fun rateCacheDao(): RateCacheDao
    abstract fun quoteCacheDao(): QuoteCacheDao
    abstract fun balanceCorrectionDao(): BalanceCorrectionDao

    companion object {
        const val NAME = "mipresupuestocr.db"

        /** Version 2 adds hand corrections; existing budgets, entries and holdings are preserved. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `balance_corrections` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`periodStartEpochDay` INTEGER NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`categoryId` INTEGER, " +
                        "`currency` TEXT NOT NULL, " +
                        "`amountMinor` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_balance_corrections_periodStartEpochDay` " +
                        "ON `balance_corrections` (`periodStartEpochDay`)",
                )
            }
        }
    }
}
