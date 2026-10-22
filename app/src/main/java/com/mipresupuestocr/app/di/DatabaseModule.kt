package com.mipresupuestocr.app.di

import android.content.Context
import androidx.room.Room
import com.mipresupuestocr.app.data.local.AppDatabase
import com.mipresupuestocr.app.data.local.BalanceCorrectionDao
import com.mipresupuestocr.app.data.local.CategoryDao
import com.mipresupuestocr.app.data.local.HoldingDao
import com.mipresupuestocr.app.data.local.QuoteCacheDao
import com.mipresupuestocr.app.data.local.RateCacheDao
import com.mipresupuestocr.app.data.local.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // Every version bump needs a migration here; never drop a user's budget.
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideTransactionDao(database: AppDatabase): TransactionDao = database.transactionDao()

    @Provides
    fun provideCategoryDao(database: AppDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideHoldingDao(database: AppDatabase): HoldingDao = database.holdingDao()

    @Provides
    fun provideRateCacheDao(database: AppDatabase): RateCacheDao = database.rateCacheDao()

    @Provides
    fun provideQuoteCacheDao(database: AppDatabase): QuoteCacheDao = database.quoteCacheDao()

    @Provides
    fun provideBalanceCorrectionDao(database: AppDatabase): BalanceCorrectionDao =
        database.balanceCorrectionDao()
}
