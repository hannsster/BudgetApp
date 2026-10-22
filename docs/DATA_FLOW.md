# Data flow

This document traces how data moves through the app for each feature. All flows are cold Room/DataStore flows
collected by view models with `stateIn(WhileSubscribed(5_000))`.

## Storage map

| Data | Store | Notes |
| --- | --- | --- |
| Transactions | Room `transactions` | amount in minor units, ISO currency, epoch day |
| Categories + targets | Room `categories` | localized defaults seeded once, targets optional |
| ETF holdings | Room `holdings` | `BigDecimal` stored as plain strings |
| Reference/manual rate | Room `rate_cache` | one row per source key |
| ETF quotes | Room `quote_cache` | one row per ticker |
| Preferences | DataStore `user_settings` | period type, payday anchor, split, theme, flags |

## Dashboard

```
TransactionRepository.observeAll() ─┐
CategoryRepository.observeAll()  ───┤
HoldingRepository.observeAll()   ───┼─ combine ─┐
QuoteRepository.observeQuotes()  ───┤           │
SettingsRepository.observeSettings()┘           ├─ combine → BuildDashboardUseCase
RateRepository.observeActiveRate() ─────────────┘        │
                                                          ▼
                                        DashboardUiState(snapshot, period, …)
```

`BuildDashboardUseCase` resolves the period, aggregates the budget and valuates the portfolio. It returns
`Failure(MissingPaydayAnchor)` when biweekly is selected without an anchor, which the UI surfaces as a banner
linking to settings.

Manual refresh (`DashboardViewModel.refresh`) runs the rate refresh and quote refresh use cases in sequence and
maps their outcomes to a snackbar message: updated, updated from cache, or "no price for …".

## Budget

```
Settings + transactions + categories + selectedPeriod
        → BudgetProgressCalculator.calculate(...)
        → BudgetUiState(summary, transactions, categories)
```

- Period navigation is local view-model state; the transactions flow is global and filtered in the calculator.
- Setting a target writes the whole category row back (`Category.copy(budgetTarget = …)`).
- Deleting an entry deletes by id; the list re-emits.

## 50/30/20 calculator

Pure UI state: no persistence except when the user taps **Use this split**, which writes `BudgetSplit` to
DataStore. The result is recomputed on every keystroke:

```
incomeText ─parseAmount→ Money(CRC) ─┐
frequency ───────────────────────────┼→ FiftyThirtyTwentyCalculator → result | AppError
split fields ─BudgetSplit────────────┘
```

## Portfolio

```
holdings ─┐
quotes   ─┼→ PortfolioValuator.valuate(...) → PortfolioValuation → PortfolioUiState
rate     ─┘
```

Valuation rules:

1. Holdings with no cached quote, or with zero units, are listed in `unpricedTickers` and excluded from totals.
2. `marketValueUsd = shares × price`, rounded once to cents.
3. `marketValueCrc` and `totalCrc` require a USD/CRC rate; otherwise they are `null` and the UI shows "no price".
4. Allocation is the position value divided by the priced total.
5. Total unrealized gain is only shown when **every** priced position has an average purchase price, so a partial
   cost basis can never masquerade as a total.

Saving a position triggers a best-effort quote refresh; a missing API key does not block the save.

## Exchange rate refresh

```
RefreshExchangeRateUseCase
   → RateRepository.refresh()
       → ExchangeRateProvider.getLatestRate(USD, CRC)
       → success: RateCache.saveReference(rate)
       → failure: error returned unchanged
   → on failure: RateCache.activeRateSnapshot() → RateRefreshResult(usedCache = true)
```

`RateRepository.observeActiveRate()` always emits manual rate first, then the cached reference rate. A rate older
than 48 hours is flagged as stale by `BuildDashboardUseCase`.

## Quote refresh

```
RefreshQuotesUseCase
   → HoldingRepository.getAll()  (empty portfolio ⇒ no network call, skipped = true)
   → QuoteRepository.refresh(tickers)
       → MarketQuoteProvider.getQuotes(tickers)   (sequential, per-ticker)
       → success: QuoteCache.save(quotes)
   → failedTickers = requested − returned
   → on total failure: cachedQuotes(tickers) → QuoteRefreshResult(usedCache = true)
```

## Background work

```
MainActivity.onCreate → RefreshScheduler.schedulePeriodic()
WorkManager (6h, network required, exponential backoff)
   → RefreshWorker (HiltWorker)
       → refresh rate → record lastRateRefresh
       → refresh quotes → record lastQuoteRefresh
       → Result.retry() only for retryable failures
```

## First-run seeding

`MainViewModel` calls `DefaultCategorySeeder.seedIfNeeded()` once:

1. If `setupCompleted` is already true, do nothing.
2. If the category table is empty, insert localized defaults (no targets, no amounts).
3. Mark `setupCompleted` so a user who deletes a default category does not get it back.

## Deletion and privacy

All data is removed by uninstalling the app or clearing app storage. Cloud backup and device transfer are
disabled by `app/src/main/res/xml/data_extraction_rules.xml`.
