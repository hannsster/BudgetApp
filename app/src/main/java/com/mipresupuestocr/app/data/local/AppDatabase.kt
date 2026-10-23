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
        AccountEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun holdingDao(): HoldingDao
    abstract fun rateCacheDao(): RateCacheDao
    abstract fun quoteCacheDao(): QuoteCacheDao
    abstract fun balanceCorrectionDao(): BalanceCorrectionDao
    abstract fun accountDao(): AccountDao

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

        /**
         * Version 3 adds accounts and the entry-to-account link. Existing entries are untouched and
         * simply start out unassigned, so an update never loses a budget.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `accounts` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`openingBalanceMinor` INTEGER NOT NULL, " +
                        "`currency` TEXT NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL)",
                )
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `accountId` INTEGER")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transactions_accountId` " +
                        "ON `transactions` (`accountId`)",
                )
            }
        }
    }
}
