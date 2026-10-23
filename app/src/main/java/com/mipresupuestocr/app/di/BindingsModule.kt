package com.mipresupuestocr.app.di

import com.mipresupuestocr.app.data.local.RoomQuoteCache
import com.mipresupuestocr.app.data.local.RoomRateCache
import com.mipresupuestocr.app.data.remote.OpenErApiExchangeRateProvider
import com.mipresupuestocr.app.data.remote.TwelveDataMarketQuoteProvider
import com.mipresupuestocr.app.data.repository.DefaultAccountRepository
import com.mipresupuestocr.app.data.repository.DefaultBalanceCorrectionRepository
import com.mipresupuestocr.app.data.repository.DefaultCategoryRepository
import com.mipresupuestocr.app.data.repository.DefaultHoldingRepository
import com.mipresupuestocr.app.data.repository.DefaultQuoteRepository
import com.mipresupuestocr.app.data.repository.DefaultRateRepository
import com.mipresupuestocr.app.data.repository.DefaultTransactionRepository
import com.mipresupuestocr.app.data.repository.DefaultTransferRepository
import com.mipresupuestocr.app.data.settings.DataStoreMarketDataKeySource
import com.mipresupuestocr.app.data.settings.DefaultSettingsRepository
import com.mipresupuestocr.app.data.settings.MarketDataKeySource
import com.mipresupuestocr.domain.provider.ExchangeRateProvider
import com.mipresupuestocr.domain.provider.MarketQuoteProvider
import com.mipresupuestocr.domain.repository.AccountRepository
import com.mipresupuestocr.domain.repository.BalanceCorrectionRepository
import com.mipresupuestocr.domain.repository.CategoryRepository
import com.mipresupuestocr.domain.repository.HoldingRepository
import com.mipresupuestocr.domain.repository.QuoteCache
import com.mipresupuestocr.domain.repository.QuoteRepository
import com.mipresupuestocr.domain.repository.RateCache
import com.mipresupuestocr.domain.repository.RateRepository
import com.mipresupuestocr.domain.repository.SettingsRepository
import com.mipresupuestocr.domain.repository.TransactionRepository
import com.mipresupuestocr.domain.repository.TransferRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: DefaultTransactionRepository): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: DefaultCategoryRepository): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: DefaultAccountRepository): AccountRepository

    @Binds
    @Singleton
    abstract fun bindTransferRepository(impl: DefaultTransferRepository): TransferRepository

    @Binds
    @Singleton
    abstract fun bindHoldingRepository(impl: DefaultHoldingRepository): HoldingRepository

    @Binds
    @Singleton
    abstract fun bindRateRepository(impl: DefaultRateRepository): RateRepository

    @Binds
    @Singleton
    abstract fun bindQuoteRepository(impl: DefaultQuoteRepository): QuoteRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: DefaultSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindMarketDataKeySource(impl: DataStoreMarketDataKeySource): MarketDataKeySource

    @Binds
    @Singleton
    abstract fun bindBalanceCorrectionRepository(
        impl: DefaultBalanceCorrectionRepository,
    ): BalanceCorrectionRepository

    @Binds
    @Singleton
    abstract fun bindRateCache(impl: RoomRateCache): RateCache

    @Binds
    @Singleton
    abstract fun bindQuoteCache(impl: RoomQuoteCache): QuoteCache

    @Binds
    @Singleton
    abstract fun bindExchangeRateProvider(impl: OpenErApiExchangeRateProvider): ExchangeRateProvider

    @Binds
    @Singleton
    abstract fun bindMarketQuoteProvider(impl: TwelveDataMarketQuoteProvider): MarketQuoteProvider
}
