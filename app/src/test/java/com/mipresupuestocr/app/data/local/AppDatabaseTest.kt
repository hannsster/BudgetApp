package com.mipresupuestocr.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipresupuestocr.app.data.repository.DefaultAccountRepository
import com.mipresupuestocr.domain.model.Account
import com.mipresupuestocr.domain.model.AccountType
import com.mipresupuestocr.domain.model.Category
import com.mipresupuestocr.domain.model.CategoryBucket
import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.ExchangeRate
import com.mipresupuestocr.domain.model.Holding
import com.mipresupuestocr.domain.model.Money
import com.mipresupuestocr.domain.model.Quote
import com.mipresupuestocr.domain.model.Transaction
import com.mipresupuestocr.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Runs the real Room database against an in-memory SQLite instance via Robolectric, so DAO queries,
 * mappers and the cache keys are exercised on the JVM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `transactions round trip with money and dates`() = runTest {
        val dao = database.transactionDao()
        val transaction = Transaction(
            type = TransactionType.EXPENSE,
            amount = Money.ofMajor("12500.75", CurrencyCode.CRC),
            categoryId = 3,
            date = LocalDate.of(2026, 10, 5),
            note = "Supermercado",
        )

        dao.upsert(transaction.toEntity())

        val stored = dao.observeAll().first().single().toDomain()
        assertEquals(transaction.amount, stored.amount)
        assertEquals(transaction.date, stored.date)
        assertEquals(TransactionType.EXPENSE, stored.type)
        assertEquals("Supermercado", stored.note)
        assertEquals(3L, stored.categoryId)
    }

    @Test
    fun `period range query is inclusive and sorted newest first`() = runTest {
        val dao = database.transactionDao()
        listOf(
            LocalDate.of(2026, 9, 30),
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 31),
            LocalDate.of(2026, 11, 1),
        ).forEach { date ->
            dao.upsert(
                Transaction(
                    type = TransactionType.EXPENSE,
                    amount = Money.ofMajor("1000.00", CurrencyCode.CRC),
                    date = date,
                ).toEntity(),
            )
        }

        val rows = dao.observeBetween(
            LocalDate.of(2026, 10, 1).toEpochDay(),
            LocalDate.of(2026, 10, 31).toEpochDay(),
        ).first().map { it.dateEpochDay }

        assertEquals(listOf(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 1)).map { it.toEpochDay() }, rows)
    }

    @Test
    fun `accounts round trip with their type and opening balance`() = runTest {
        val dao = database.accountDao()
        val account = Account(
            name = "Ahorros",
            type = AccountType.SAVINGS,
            openingBalance = Money.ofMajor("750000.50", CurrencyCode.CRC),
            sortOrder = 1,
        )

        val id = dao.upsert(account.toEntity())

        val stored = dao.getAll().single()
        assertEquals(id, stored.id)
        assertEquals(AccountType.SAVINGS, stored.toDomain().type)
        assertEquals(
            Money.ofMajor("750000.50", CurrencyCode.CRC),
            stored.toDomain().openingBalance,
        )
    }

    @Test
    fun `deleting an account keeps its entries and only detaches them`() = runTest {
        val accountDao = database.accountDao()
        val accountId = accountDao.upsert(Account(name = "Corriente").toEntity())
        val transactionDao = database.transactionDao()
        val entryId = transactionDao.upsert(
            Transaction(
                type = TransactionType.EXPENSE,
                amount = Money.ofMajor("12000", CurrencyCode.CRC),
                date = LocalDate.of(2026, 10, 6),
                accountId = accountId,
            ).toEntity(),
        )
        assertEquals(accountId, transactionDao.getById(entryId)!!.accountId)

        DefaultAccountRepository(database).delete(accountId)

        assertTrue(accountDao.getAll().isEmpty())
        val entry = transactionDao.getById(entryId)
        assertEquals("the entry itself is kept", 1, transactionDao.getBetween(0L, Long.MAX_VALUE).size)
        assertNull("the entry loses the account link", entry!!.accountId)
    }

    @Test
    fun `categories keep their bucket and target`() = runTest {
        val dao = database.categoryDao()
        val category = Category(
            name = "Supermercado",
            bucket = CategoryBucket.NEEDS,
            budgetTarget = Money.ofMajor("150000.00", CurrencyCode.CRC),
            isDefault = true,
            sortOrder = 2,
        )

        val id = dao.upsert(category.toEntity())

        val stored = dao.getAll().single()
        assertEquals(id, stored.id)
        assertEquals(CategoryBucket.NEEDS, stored.toDomain().bucket)
        assertEquals(Money.ofMajor("150000.00", CurrencyCode.CRC), stored.toDomain().budgetTarget)
        assertTrue(stored.isDefault)
    }

    @Test
    fun `holdings preserve decimal precision`() = runTest {
        val dao = database.holdingDao()
        val holding = Holding(
            ticker = "VTI",
            shares = BigDecimal("12.3456"),
            averagePriceUsd = BigDecimal("241.19"),
            notes = "long term",
        )

        dao.upsert(holding.toEntity())

        val stored = dao.getAll().single().toDomain()
        assertEquals(0, BigDecimal("12.3456").compareTo(stored.shares))
        assertEquals(0, BigDecimal("241.19").compareTo(stored.averagePriceUsd))
        assertEquals("long term", stored.notes)
    }

    @Test
    fun `rate cache keeps reference and manual rates apart`() = runTest {
        val cache = RoomRateCache(database.rateCacheDao())
        val reference = ExchangeRate(
            base = CurrencyCode.USD,
            quote = CurrencyCode.CRC,
            rate = BigDecimal("512.34"),
            asOf = Instant.ofEpochSecond(1_761_120_000),
            source = "open.er-api.com",
        )
        val manual = reference.copy(rate = BigDecimal("505.00"), source = "Manual", isManual = true)

        cache.saveReference(reference)
        cache.saveManual(manual)

        assertEquals(reference, cache.reference())
        val storedManual = cache.manual()
        assertEquals(0, BigDecimal("505.00").compareTo(storedManual!!.rate))
        assertEquals("Manual", storedManual.source)
        assertTrue(storedManual.isManual)
        assertTrue(cache.observeRates().first().any { it.isManual })

        cache.clearManual()
        assertNull(cache.manual())
        assertEquals(reference, cache.reference())
    }

    @Test
    fun `quote cache upserts by ticker and clears`() = runTest {
        val cache = RoomQuoteCache(database.quoteCacheDao())
        val asOf = Instant.ofEpochSecond(1_761_148_740)
        val first = Quote("VTI", BigDecimal("280.12"), asOf, "Twelve Data")
        val second = Quote("VTI", BigDecimal("281.00"), asOf.plusSeconds(60), "Twelve Data")
        val qqq = Quote("QQQ", BigDecimal("420.55"), asOf, "Twelve Data")

        cache.save(listOf(first, qqq))
        cache.save(listOf(second))

        val all = cache.all().associateBy { it.ticker }
        assertEquals(2, all.size)
        assertEquals(0, BigDecimal("281.00").compareTo(all.getValue("VTI").priceUsd))

        cache.clear()
        assertTrue(cache.all().isEmpty())
    }
}
