# Testing

## Principles

- Business rules are tested in the pure `:domain` module, fast and without Android.
- Network code is tested against MockWebServer; unit tests never reach the internet.
- Repositories are tested with in-memory fakes for providers and caches.
- Formatting and parsing heuristics get their own tests because they are user-facing.
- Instrumented Compose tests cover shared, stateless components.

## Commands

```bash
./gradlew :domain:test                     # domain calculators and use cases
./gradlew :app:testDebugUnitTest           # providers, repositories, parsing
./gradlew :app:connectedDebugAndroidTest   # Compose UI tests (device/emulator required)
./gradlew lintDebug                        # Android lint
./gradlew assembleDebug                    # compiles everything, produces app/build/outputs/apk/debug
```

Run everything CI runs locally with:

```bash
./gradlew :domain:test :app:testDebugUnitTest lintDebug assembleDebug
```

HTML reports: `domain/build/reports/tests/test/index.html` and
`app/build/reports/tests/testDebugUnitTest/index.html`. Lint: `app/build/reports/lint-results-debug.html`.

## What is covered

### 50/30/20 (`FiftyThirtyTwentyCalculatorTest`)

- 50/30/20 split of monthly income.
- Biweekly → monthly normalisation (×26/12) and weekly (×52/12).
- Rounding remainder lands in savings and the buckets still sum to the income.
- Custom split support and rejection when percentages do not total 100.
- Zero and negative income rejected.

### Currency conversion (`CurrencyConverterTest`)

- USD → CRC multiplication and CRC → USD division.
- Rounding to the target minor unit.
- Same-currency passthrough.
- Non-positive rate rejected.
- Currency not described by the rate rejected.

### Budget progress (`BudgetProgressCalculatorTest`)

- Income, spent and remaining aggregation for a period.
- Category progress fraction, remaining target and over-budget flags.
- Transactions outside the period and in other currencies ignored.
- Uncategorized spending reported separately.
- Empty-state detection (no entries and no targets).

### Periods (`BudgetPeriodResolverTest`)

- Calendar month, leap February, biweekly windows anchored on payday, dates before the anchor.
- Missing anchor error.
- Previous/next navigation for both period types.

### Portfolio valuation (`PortfolioValuatorTest`)

- USD and CRC totals, per-position value, cost basis, gain and gain percentage.
- Allocation percentages.
- Unpriced holdings excluded from totals and reported.
- CRC totals omitted when no rate is available.
- Empty portfolio.
- Total gain withheld when any position lacks a cost basis.
- Case/whitespace-insensitive ticker lookup.

### Dashboard assembly (`BuildDashboardUseCaseTest`)

- Monthly snapshot composition, stale-rate detection, biweekly anchor error and window resolution.

### Use cases (`UseCasesTest`)

- Empty portfolio skips the network call.
- Tickers deduplicated and uppercased.
- Unknown tickers reported as failed.
- Provider failure propagation.
- Rate and quote cached fallbacks (`usedCache = true`) and failure when nothing is cached.
- Transaction validation, note trimming.
- Holding normalization, invalid ticker/share/price rejection.
- Manual rate validation.

### Providers (`OpenErApiProviderTest`, `TwelveDataProviderTest`)

- Successful mapping including timestamps and source names.
- 429 → `RateLimited`, 401/403 → `InvalidApiKey`, 404 → unknown symbol/unsupported currency.
- Malformed JSON, empty body, missing timestamp.
- Missing API key fails before any request is made.
- Unknown ticker error payloads, non-USD instruments.
- Partial batch success and batch failure with no successes.
- Empty ticker list makes no request.

### Repositories (`DefaultRateRepositoryTest`, `DefaultQuoteRepositoryTest`)

- Refresh writes to the cache; failures leave the cache intact.
- Manual rate precedence, clearing falls back to the reference rate.
- Cached quote lookup ignoring case and unknown tickers.
- Empty ticker list short-circuits.

### Room storage (`AppDatabaseTest`, Robolectric)

Runs the real Room database against in-memory SQLite on the JVM:

- Transaction insert/read round trip including money, date, category and note mapping.
- Inclusive, newest-first period range query.
- Category bucket and target persistence.
- `BigDecimal` precision for shares and average price.
- Reference vs manual rate cache keys, precedence and clearing.
- Quote cache upsert by ticker and clearing.

### Parsing (`FormattingTest`)

- Costa Rican and US amount formats, grouping spaces, three-digit grouping heuristics, rejection of letters and
  malformed separators, share/price decimals, percentage bounds.

### Compose UI (`CommonComponentsTest`, instrumented)

- Empty state renders its copy and invokes its action.
- Category target row renders label and amounts.

## Conventions for new tests

- Name tests with backticked sentences describing behaviour, not implementation.
- No real network calls, no sleeps, no reliance on the system clock where a value can be passed in.
- Prefer fakes over mocks unless verifying interactions (for example "no provider call").
- Assert domain outcomes (`AppError`, `Money`) rather than formatted strings, except in formatting tests.
- Add a regression test with every bug fix.

## CI

`.github/workflows/android.yml` runs `:domain:test`, `:app:testDebugUnitTest`, `lintDebug` and `assembleDebug` on
every push and pull request, uploads the APK and the test/lint reports, and requires no production secrets.
