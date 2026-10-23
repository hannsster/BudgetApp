package com.mipresupuestocr.app.di

import com.mipresupuestocr.domain.repository.AccountRepository
import com.mipresupuestocr.domain.repository.BalanceCorrectionRepository
import com.mipresupuestocr.domain.repository.HoldingRepository
import com.mipresupuestocr.domain.repository.QuoteRepository
import com.mipresupuestocr.domain.repository.RateRepository
import com.mipresupuestocr.domain.repository.TransactionRepository
import com.mipresupuestocr.domain.usecase.BudgetPeriodResolver
import com.mipresupuestocr.domain.usecase.CalculateAccountBalancesUseCase
import com.mipresupuestocr.domain.usecase.DeleteAccountUseCase
import com.mipresupuestocr.domain.usecase.BudgetProgressCalculator
import com.mipresupuestocr.domain.usecase.BuildDashboardUseCase
import com.mipresupuestocr.domain.usecase.ClearBalanceCorrectionUseCase
import com.mipresupuestocr.domain.usecase.ClearManualRateUseCase
import com.mipresupuestocr.domain.usecase.DeleteHoldingUseCase
import com.mipresupuestocr.domain.usecase.DeleteTransactionUseCase
import com.mipresupuestocr.domain.usecase.RefreshExchangeRateUseCase
import com.mipresupuestocr.domain.usecase.RefreshQuotesUseCase
import com.mipresupuestocr.domain.usecase.SaveAccountUseCase
import com.mipresupuestocr.domain.usecase.SaveTransactionUseCase
import com.mipresupuestocr.domain.usecase.SetAvailableBalanceUseCase
import com.mipresupuestocr.domain.usecase.SetCategoryBalanceUseCase
import com.mipresupuestocr.domain.usecase.SetIncomeBalanceUseCase
import com.mipresupuestocr.domain.usecase.SetManualRateUseCase
import com.mipresupuestocr.domain.usecase.SetSpentBalanceUseCase
import com.mipresupuestocr.domain.usecase.UpsertHoldingUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The domain module stays framework-free, so its use cases are provided here rather than being
 * annotated with `@Inject` in :domain.
 */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    fun provideBudgetPeriodResolver(): BudgetPeriodResolver = BudgetPeriodResolver()

    @Provides
    fun provideBudgetProgressCalculator(): BudgetProgressCalculator = BudgetProgressCalculator()

    @Provides
    fun provideBuildDashboardUseCase(
        periodResolver: BudgetPeriodResolver,
        progressCalculator: BudgetProgressCalculator,
    ): BuildDashboardUseCase = BuildDashboardUseCase(periodResolver, progressCalculator)

    @Provides
    fun provideSaveTransactionUseCase(repository: TransactionRepository): SaveTransactionUseCase =
        SaveTransactionUseCase(repository)

    @Provides
    fun provideDeleteTransactionUseCase(repository: TransactionRepository): DeleteTransactionUseCase =
        DeleteTransactionUseCase(repository)

    @Provides
    fun provideSaveAccountUseCase(repository: AccountRepository): SaveAccountUseCase =
        SaveAccountUseCase(repository)

    @Provides
    fun provideDeleteAccountUseCase(repository: AccountRepository): DeleteAccountUseCase =
        DeleteAccountUseCase(repository)

    @Provides
    fun provideCalculateAccountBalancesUseCase(): CalculateAccountBalancesUseCase =
        CalculateAccountBalancesUseCase()

    @Provides
    fun provideUpsertHoldingUseCase(repository: HoldingRepository): UpsertHoldingUseCase =
        UpsertHoldingUseCase(repository)

    @Provides
    fun provideDeleteHoldingUseCase(repository: HoldingRepository): DeleteHoldingUseCase =
        DeleteHoldingUseCase(repository)

    @Provides
    fun provideRefreshQuotesUseCase(
        quoteRepository: QuoteRepository,
        holdingRepository: HoldingRepository,
    ): RefreshQuotesUseCase = RefreshQuotesUseCase(quoteRepository, holdingRepository)

    @Provides
    fun provideRefreshExchangeRateUseCase(rateRepository: RateRepository): RefreshExchangeRateUseCase =
        RefreshExchangeRateUseCase(rateRepository)

    @Provides
    fun provideSetManualRateUseCase(rateRepository: RateRepository): SetManualRateUseCase =
        SetManualRateUseCase(rateRepository)

    @Provides
    fun provideClearManualRateUseCase(rateRepository: RateRepository): ClearManualRateUseCase =
        ClearManualRateUseCase(rateRepository)

    @Provides
    fun provideSetIncomeBalanceUseCase(
        transactionRepository: TransactionRepository,
        correctionRepository: BalanceCorrectionRepository,
    ): SetIncomeBalanceUseCase = SetIncomeBalanceUseCase(transactionRepository, correctionRepository)

    @Provides
    fun provideSetSpentBalanceUseCase(
        transactionRepository: TransactionRepository,
        correctionRepository: BalanceCorrectionRepository,
    ): SetSpentBalanceUseCase = SetSpentBalanceUseCase(transactionRepository, correctionRepository)

    @Provides
    fun provideSetAvailableBalanceUseCase(
        transactionRepository: TransactionRepository,
        correctionRepository: BalanceCorrectionRepository,
    ): SetAvailableBalanceUseCase = SetAvailableBalanceUseCase(transactionRepository, correctionRepository)

    @Provides
    fun provideSetCategoryBalanceUseCase(
        transactionRepository: TransactionRepository,
        correctionRepository: BalanceCorrectionRepository,
    ): SetCategoryBalanceUseCase = SetCategoryBalanceUseCase(transactionRepository, correctionRepository)

    @Provides
    fun provideClearBalanceCorrectionUseCase(
        correctionRepository: BalanceCorrectionRepository,
    ): ClearBalanceCorrectionUseCase = ClearBalanceCorrectionUseCase(correctionRepository)
}
