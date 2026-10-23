package com.mipresupuestocr.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY dateEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query(
        "SELECT * FROM transactions WHERE dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay " +
            "ORDER BY dateEpochDay DESC, id DESC",
    )
    fun observeBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<TransactionEntity>>

    @Query(
        "SELECT * FROM transactions WHERE dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay " +
            "ORDER BY dateEpochDay DESC, id DESC",
    )
    suspend fun getBetween(fromEpochDay: Long, toEpochDay: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TransactionEntity): Long

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CategoryEntity>)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface BalanceCorrectionDao {
    @Query("SELECT * FROM balance_corrections")
    fun observeAll(): Flow<List<BalanceCorrectionEntity>>

    @Query("SELECT * FROM balance_corrections")
    suspend fun getAll(): List<BalanceCorrectionEntity>

    @Query(
        "DELETE FROM balance_corrections WHERE periodStartEpochDay = :periodStartEpochDay " +
            "AND kind = :kind AND categoryId IS :categoryId",
    )
    suspend fun clear(periodStartEpochDay: Long, kind: String, categoryId: Long?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BalanceCorrectionEntity)
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY sortOrder ASC, name ASC")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AccountEntity): Long

    @Query("UPDATE transactions SET accountId = NULL WHERE accountId = :id")
    suspend fun detachTransactions(id: Long)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AccountTransferDao {
    @Query("SELECT * FROM account_transfers ORDER BY dateEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<AccountTransferEntity>>

    @Query("SELECT * FROM account_transfers ORDER BY dateEpochDay DESC, id DESC")
    suspend fun getAll(): List<AccountTransferEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AccountTransferEntity): Long

    @Query("DELETE FROM account_transfers WHERE id = :id")
    suspend fun delete(id: Long)

    /** A transfer whose account is gone has nowhere to land, so it is removed with that account. */
    @Query("DELETE FROM account_transfers WHERE fromAccountId = :accountId OR toAccountId = :accountId")
    suspend fun deleteForAccount(accountId: Long)
}

@Dao
interface HoldingDao {
    @Query("SELECT * FROM holdings ORDER BY ticker ASC")
    fun observeAll(): Flow<List<HoldingEntity>>

    @Query("SELECT * FROM holdings ORDER BY ticker ASC")
    suspend fun getAll(): List<HoldingEntity>

    @Query("SELECT * FROM holdings WHERE id = :id")
    suspend fun getById(id: Long): HoldingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HoldingEntity): Long

    @Query("DELETE FROM holdings WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface RateCacheDao {
    @Query("SELECT * FROM rate_cache")
    fun observeAll(): Flow<List<RateCacheEntity>>

    @Query("SELECT * FROM rate_cache WHERE cacheKey = :key")
    suspend fun get(key: String): RateCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RateCacheEntity)

    @Query("DELETE FROM rate_cache WHERE cacheKey = :key")
    suspend fun delete(key: String)
}

@Dao
interface QuoteCacheDao {
    @Query("SELECT * FROM quote_cache")
    fun observeAll(): Flow<List<QuoteCacheEntity>>

    @Query("SELECT * FROM quote_cache")
    suspend fun getAll(): List<QuoteCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<QuoteCacheEntity>)

    @Query("DELETE FROM quote_cache")
    suspend fun clear()
}
