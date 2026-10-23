package com.mipresupuestocr.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Users already have a version 1 database on their phones. Opening it with version 2 must migrate in
 * place: Room validates the migrated schema against its own expectation, so a mismatch here would be
 * a crash on launch after updating.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    @Test
    fun `migrating from version 1 keeps budgets and entries`() {
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()

        val legacy = SQLiteDatabase.openOrCreateDatabase(file, null)
        VERSION_1_SCHEMA.forEach(legacy::execSQL)
        legacy.version = 1
        legacy.execSQL(
            "INSERT INTO `categories` (id, name, bucket, targetMinor, isDefault, sortOrder) " +
                "VALUES (1, 'Supermercado', 'NEEDS', 15000000, 1, 2)",
        )
        legacy.execSQL(
            "INSERT INTO `transactions` " +
                "(type, amountMinor, currency, categoryId, dateEpochDay, note, createdAtMillis) " +
                "VALUES ('EXPENSE', 1250075, 'CRC', 1, ${LocalDate.of(2026, 10, 5).toEpochDay()}, 'Mercado', 0)",
        )
        legacy.close()

        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        try {
            runBlocking {
                // Touching any DAO opens the database, which runs and validates the migration.
                val categories = database.categoryDao().getAll()
                assertEquals(1, categories.size)
                assertEquals("Supermercado", categories.single().name)

                val entries = database.transactionDao().getBetween(0L, Long.MAX_VALUE)
                assertEquals(1, entries.size)
                assertEquals(1_250_075L, entries.single().amountMinor)

                database.balanceCorrectionDao().insert(
                    BalanceCorrectionEntity(
                        periodStartEpochDay = LocalDate.of(2026, 10, 1).toEpochDay(),
                        kind = "INCOME",
                        currency = "CRC",
                        amountMinor = 500,
                    ),
                )
                assertEquals(1, database.balanceCorrectionDao().getAll().size)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `migrating from version 2 adds accounts without touching entries`() {
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()

        val legacy = SQLiteDatabase.openOrCreateDatabase(file, null)
        VERSION_2_SCHEMA.forEach(legacy::execSQL)
        legacy.version = 2
        legacy.execSQL(
            "INSERT INTO `transactions` " +
                "(id, type, amountMinor, currency, categoryId, dateEpochDay, note, createdAtMillis) " +
                "VALUES (7, 'EXPENSE', 2500000, 'CRC', NULL, ${LocalDate.of(2026, 10, 8).toEpochDay()}, 'Bus', 0)",
        )
        legacy.close()

        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        try {
            runBlocking {
                val entries = database.transactionDao().getBetween(0L, Long.MAX_VALUE)
                assertEquals(1, entries.size)
                assertEquals(2_500_000L, entries.single().amountMinor)
                // Entries that predate accounts simply start out unassigned.
                assertEquals(null, entries.single().accountId)

                val accountId = database.accountDao().upsert(
                    AccountEntity(
                        name = "Ahorros",
                        type = "SAVINGS",
                        openingBalanceMinor = 500_000_00L,
                        currency = "CRC",
                        sortOrder = 0,
                    ),
                )
                assertEquals("Ahorros", database.accountDao().getAll().single().name)

                database.transactionDao().upsert(entries.single().copy(accountId = accountId))
                assertEquals(accountId, database.transactionDao().getById(7L)!!.accountId)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `migrating from version 3 adds transfers and keeps accounts`() {
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()

        val legacy = SQLiteDatabase.openOrCreateDatabase(file, null)
        VERSION_3_SCHEMA.forEach(legacy::execSQL)
        legacy.version = 3
        legacy.execSQL(
            "INSERT INTO `accounts` (id, name, type, openingBalanceMinor, currency, sortOrder) " +
                "VALUES (1, 'Corriente', 'CHECKING', 10000000, 'CRC', 0)",
        )
        legacy.execSQL(
            "INSERT INTO `transactions` " +
                "(id, type, amountMinor, currency, categoryId, dateEpochDay, note, createdAtMillis, accountId) " +
                "VALUES (9, 'EXPENSE', 1500000, 'CRC', NULL, ${LocalDate.of(2026, 10, 9).toEpochDay()}, 'Súper', 0, 1)",
        )
        legacy.close()

        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        try {
            runBlocking {
                val account = database.accountDao().getAll().single()
                assertEquals("Corriente", account.name)
                assertEquals(1L, database.transactionDao().getById(9L)!!.accountId)

                val transferId = database.accountTransferDao().upsert(
                    AccountTransferEntity(
                        fromAccountId = 1L,
                        toAccountId = 2L,
                        amountMinor = 2500000,
                        currency = "CRC",
                        dateEpochDay = LocalDate.of(2026, 10, 12).toEpochDay(),
                    ),
                )
                assertEquals(
                    transferId,
                    database.accountTransferDao().getAll().single().id,
                )
            }
        } finally {
            database.close()
        }
    }

    private companion object {
        /** Exactly what Room created at version 1 (see app/schemas/.../1.json). */
        val VERSION_1_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `bucket` TEXT NOT NULL, `targetMinor` INTEGER, " +
                "`isDefault` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`type` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `currency` TEXT NOT NULL, " +
                "`categoryId` INTEGER, `dateEpochDay` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                "`createdAtMillis` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_transactions_dateEpochDay` " +
                "ON `transactions` (`dateEpochDay`)",
            "CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` " +
                "ON `transactions` (`categoryId`)",
            "CREATE TABLE IF NOT EXISTS `holdings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`ticker` TEXT NOT NULL, `shares` TEXT NOT NULL, `averagePriceUsd` TEXT, " +
                "`notes` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `rate_cache` (`cacheKey` TEXT NOT NULL, `base` TEXT NOT NULL, " +
                "`quote` TEXT NOT NULL, `rate` TEXT NOT NULL, `asOfMillis` INTEGER NOT NULL, " +
                "`source` TEXT NOT NULL, `isManual` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))",
            "CREATE TABLE IF NOT EXISTS `quote_cache` (`ticker` TEXT NOT NULL, " +
                "`priceUsd` TEXT NOT NULL, `asOfMillis` INTEGER NOT NULL, `source` TEXT NOT NULL, " +
                "PRIMARY KEY(`ticker`))",
        )

        /** Version 1 plus the hand corrections table (see app/schemas/.../2.json). */
        val VERSION_2_SCHEMA = VERSION_1_SCHEMA + listOf(
            "CREATE TABLE IF NOT EXISTS `balance_corrections` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`periodStartEpochDay` INTEGER NOT NULL, `kind` TEXT NOT NULL, " +
                "`categoryId` INTEGER, `currency` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_balance_corrections_periodStartEpochDay` " +
                "ON `balance_corrections` (`periodStartEpochDay`)",
        )

        /** Version 2 plus accounts and the entry-to-account column (see app/schemas/.../3.json). */
        val VERSION_3_SCHEMA = VERSION_2_SCHEMA.map { statement ->
            if (statement.startsWith("CREATE TABLE IF NOT EXISTS `transactions`")) {
                statement.replace(
                    "`createdAtMillis` INTEGER NOT NULL)",
                    "`createdAtMillis` INTEGER NOT NULL, `accountId` INTEGER)",
                )
            } else {
                statement
            }
        } + listOf(
            "CREATE INDEX IF NOT EXISTS `index_transactions_accountId` " +
                "ON `transactions` (`accountId`)",
            "CREATE TABLE IF NOT EXISTS `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `type` TEXT NOT NULL, `openingBalanceMinor` INTEGER NOT NULL, " +
                "`currency` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL)",
        )
    }
}
