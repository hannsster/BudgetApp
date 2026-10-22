# Architecture

## Goals

1. **Correct money math.** Budgeting and valuation rules are isolated, pure and unit tested.
2. **Local-first and private.** No backend, no account, no telemetry.
3. **Swappable data sources.** Exchange-rate and market-data providers sit behind interfaces.
4. **Predictable UI state.** Every screen renders an immutable state object produced by one view model.
5. **Boring, standard Android.** Room, Retrofit, Hilt, WorkManager, Compose and Material 3 as intended.

## Modules

### `:domain` — pure Kotlin

No Android or framework dependencies (only `kotlinx-coroutines-core` and the JDK).

```
domain/
  model/        Money, Transaction, Category, BudgetPeriod, BudgetSplit, Holding, Quote,
                ExchangeRate, PortfolioValuation, AppResult/AppError, UserSettings
  repository/   Repository contracts + narrow RateCache/QuoteCache contracts
  provider/     ExchangeRateProvider, MarketQuoteProvider
  usecase/      FiftyThirtyTwentyCalculator, CurrencyConverter, BudgetPeriodResolver,
                BudgetProgressCalculator, PortfolioValuator, BuildDashboardUseCase,
                SaveTransactionUseCase, UpsertHoldingUseCase, Refresh*UseCase, SetManualRateUseCase
```

Why a separate module: the calculators, the dashboard assembly and the refresh fallback logic can be tested on
the JVM in milliseconds, with no emulator and no Android SDK behaviour leaking into the tests.

### `:app` — Android

```
data/local        Room database, DAOs, entities, mappers, Room-backed rate/quote caches,
                  localized first-run category seeding
data/remote       open.er-api.com and Twelve Data Retrofit services, DTOs, providers, error mapping
data/repository   Repository implementations over DAOs/providers/caches
data/settings     DataStore-backed UserSettings repository
di                Hilt modules (database, network, bindings, use cases)
ui/theme          Material 3 colour scheme, dynamic colour, dark mode
ui/components     Shared Compose building blocks (cards, progress, empty state, error banner)
ui/navigation     Routes, top-level destinations, NavHost, bottom bar
ui/dashboard      Dashboard state + screen
ui/budget         Budget state + screen (targets, period navigation, entries)
ui/calculator     50/30/20 state + screen
ui/portfolio      Portfolio state + screen
ui/settings       Settings state + screen
ui/transactions   Add/edit entry form
ui/holdings       Add/edit ETF position form
ui/util           Costa Rica currency/date formatting and amount parsing
worker            WorkManager refresh worker + periodic scheduler
```

## Layering rules

- UI depends on domain types only. It never imports Room or Retrofit types.
- Repositories implement domain interfaces and translate between domain models and entities/DTOs.
- The domain layer never depends on the app layer.
- View models combine repository flows and pure use cases; they do not hold Android `Context`.

## State and data flow

Every screen follows unidirectional data flow:

```
Room / DataStore / providers
        │  Flow<List<…>>, AppResult<…>
        ▼
Repository implementations
        ▼
View model (combine → immutable UiState)
        ▼
Compose screen (collectAsStateWithLifecycle)
        │  user intents
        ▼
View model functions → use cases → repositories
```

- UI state classes are immutable `data class`es with explicit defaults for the loading state.
- One-shot events (snackbar messages) are separate nullable fields that the screen consumes and clears.
- Errors are modelled as `AppError` values, never as English strings, so the UI can localize them.

## Money and numbers

- Amounts are `Money(amountMinor: Long, currency)`: integer minor units, no floating point drift.
- Percentages are integers that must total 100; rounding is half-up and the savings bucket absorbs the
  remainder so `needs + wants + savings == income` always holds.
- Shares and prices are `BigDecimal`; totals convert to `Money` exactly once.
- CRC values are always formatted with Costa Rican conventions (period thousands, comma decimals), while USD
  uses US conventions, regardless of the selected UI language.

## Error model

`AppError` covers transport, provider, data-quality and validation failures:

`MissingApiKey`, `Network`, `Timeout`, `RateLimited`, `QuotaExceeded`, `InvalidApiKey`, `EmptyResponse`,
`MalformedResponse`, `UnsupportedCurrency`, `UnsupportedAsset`, `InvalidTicker`, `UnknownSymbol`,
`InvalidRate`, `InvalidAmount`, `InvalidIncome`, `InvalidSplit`, `InvalidShares`, `MissingPaydayAnchor`,
`NoData`, `Unknown`.

Providers map failures without carrying raw exception text, because provider messages and URLs can contain the
API key. The HTTP logger redacts query parameters named `apikey`, `api_key`, `key` and `token`.

## Offline behaviour

- The active exchange rate is the manual rate if present, otherwise the last cached reference rate.
- Quotes are cached per ticker in Room and always rendered, even when a refresh fails.
- A refresh that fails reports `AppError` and, when a cached value exists, `Refresh*UseCase` returns the cached
  value with `usedCache = true`; the UI shows a "stale data" chip and a notice instead of pretending success.
- WorkManager refreshes every six hours with a network constraint and exponential backoff. Non-retryable
  failures (missing/invalid key, exhausted quota, unknown symbol) return success so the scheduler does not spin.

## Periods

- **Monthly:** the calendar month of the reference date.
- **Biweekly:** 14-day windows anchored on the user's first payday. The window containing a date is
  `anchor + 14 × floor((date − anchor) / 14)`, using floor division so dates before the anchor still resolve.
- Category targets apply to every period of the selected type; they are not per-month copies.

## Extending the app

- **New exchange-rate provider:** implement `ExchangeRateProvider`, bind it in `BindingsModule`, optionally add a
  Retrofit service in `di/AppModule`. Nothing else changes.
- **New market-data provider:** implement `MarketQuoteProvider`; the refresh use case and cache already handle
  partial success, unknown symbols and missing keys.
- **Recurring transactions:** add a rule entity and a `RecurringTransactionWorker`; the domain summary already
  works from a plain transaction list.
- **IBKR CSV import:** add a parser use case that produces `Holding` values; keep credentials out of the app.
- **Cloud backup:** would require an explicit backend decision and encryption design; it is deliberately out of
  scope for 1.0.0.
