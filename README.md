# MiPresupuesto CR

A local-first Android budgeting and ETF portfolio app built for Costa Rica. Budget in colones, plan with
the 50/30/20 rule, track USD-listed ETFs, and see everything converted with a reference USD/CRC rate.

- **Base currency:** CRC (₡). **Secondary display currency:** USD.
- **Storage:** on-device only (Room + DataStore). No account, no backend, no cloud backup.
- **Languages:** Costa Rican Spanish (default) and English.
- **Design:** original Material 3 interface. No third-party branding, icons or layouts are reused.

## Screenshots

> Placeholder — add PNGs to `docs/screenshots/` after your first device run and reference them here.

| Dashboard | Budget | Calculator |
| --- | --- | --- |
| _dashboard.png_ | _budget.png_ | _calculator.png_ |

| Portfolio | Settings | Add entry |
| --- | --- | --- |
| _portfolio.png_ | _settings.png_ | _transaction.png_ |

## Features

- **Budget management** — income and expense entries, custom categories, per-category targets, monthly
  calendar periods or payday-anchored 14-day cycles.
- **50/30/20 calculator** — needs / wants / savings for monthly, biweekly and weekly income, with an optional
  custom split that can be saved as the default.
- **ETF portfolio** — manually entered tickers, units, optional average purchase price and notes; market value
  in USD and CRC, allocation percentage, unrealised gain/loss, last update and data source.
- **Automatic USD/CRC conversion** — reference rate fetched from a provider, cached for offline use, with a
  clearly labelled manual override.
- **Background refresh** — WorkManager refreshes rates and quotes every six hours when a connection exists.
- **Privacy-first defaults** — app data is excluded from backup; no analytics, tracking or ads.

## Tech stack

| Area | Choice |
| --- | --- |
| Language | Kotlin 2.0 |
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Architecture | Clean Architecture with MVVM, unidirectional data flow |
| Async | Coroutines and Flow |
| Persistence | Room, DataStore Preferences |
| Networking | Retrofit + OkHttp + kotlinx.serialization |
| DI | Hilt (including Hilt WorkManager) |
| Background | WorkManager |
| Testing | JUnit 4, kotlinx-coroutines-test, MockWebServer, Compose UI test |
| CI | GitHub Actions |

## Repository layout

```
app/                         Android application module
  src/main/java/com/mipresupuestocr/app/
    data/local               Room entities, DAOs, caches, category seeding
    data/remote              Exchange-rate and market-data providers
    data/repository          Repository implementations
    data/settings            DataStore-backed settings
    di                       Hilt modules
    ui                       Compose screens, view models, navigation, theme
    worker                   Periodic refresh worker and scheduler
  src/test                   JVM unit tests
  src/androidTest            Compose UI tests (device/emulator)
domain/                      Pure Kotlin domain module (no Android dependencies)
docs/                        Architecture, data flow, API setup, user guide, privacy, roadmap, testing
gradle/libs.versions.toml    Version catalog
scripts/                     Repository bootstrap helpers
.github/workflows/android.yml CI pipeline
```

## Requirements

- JDK 17
- Android Studio Ladybug or newer, Android SDK Platform 35 and Build Tools 35.0.0
- An Android device or emulator running API 26+

## Setup

1. Clone the repository.
2. Create `local.properties` in the project root (git-ignored):

   ```properties
   sdk.dir=/path/to/Android/Sdk
   ```

3. Optional — enable automatic ETF prices. Get a free Twelve Data key from
   <https://twelvedata.com/> and add it to `local.properties`:

   ```properties
   TWELVE_DATA_API_KEY=your_key_here
   ```

   Without a key the app still runs: the portfolio shows a setup card and you can record positions without a
   price. The USD/CRC reference rate needs **no key**.

4. Build and install:

   ```bash
   ./gradlew assembleDebug
   ./gradlew installDebug
   ```

The key is injected into `BuildConfig` at build time. It is never committed and never printed. See
[docs/API_SETUP.md](docs/API_SETUP.md) for details and for swapping providers.

## Configuration and API providers

| Purpose | Provider | Key | Notes |
| --- | --- | --- | --- |
| USD/CRC reference rate | `open.er-api.com` (ExchangeRate-API open access) | none | Daily update, attribution required, hourly-friendly |
| ETF quotes | Twelve Data | `TWELVE_DATA_API_KEY` | Free plan: 8 credits/minute, 800/day; ETF coverage explicitly included |

Both sit behind interfaces (`ExchangeRateProvider`, `MarketQuoteProvider`), so another provider can be added
without touching the UI or repositories. Details and error handling: [docs/API_SETUP.md](docs/API_SETUP.md).

## Testing

```bash
./gradlew :domain:test            # pure business rules: 50/30/20, conversion, budgets, valuation
./gradlew :app:testDebugUnitTest  # providers, repositories, parsing
./gradlew :app:connectedDebugAndroidTest  # Compose UI tests (needs a device/emulator)
```

Unit tests never touch the network: provider tests use MockWebServer and repositories use in-memory fakes.
See [docs/TESTING.md](docs/TESTING.md) for the scenario matrix.

## Build variants

```bash
./gradlew assembleDebug     # debug build, applicationId suffix .debug
./gradlew assembleRelease   # minified, unsigned release build
./gradlew lintDebug         # Android lint
```

Release signing is intentionally not configured in version control. Add your own signing config or sign the
bundle in your CI provider; never commit keystores.

## CI/CD

[`.github/workflows/android.yml`](.github/workflows/android.yml) runs on every push and pull request:

1. Sets up JDK 17 (Temurin) with Gradle caching.
2. Runs `:domain:test` and `:app:testDebugUnitTest`.
3. Runs `lintDebug`.
4. Builds `assembleDebug` and uploads the APK plus test reports as artifacts.

No production key is required: tests use fakes and MockWebServer. If you want CI builds to include your market
key, add a repository secret named `TWELVE_DATA_API_KEY`; otherwise CI builds simply ship without it.

## Security and privacy

- All budgeting and portfolio data stays in the app's private storage.
- Cloud backup and device-transfer extraction are disabled in `data_extraction_rules.xml`.
- No analytics, crash-reporting or advertising SDKs.
- `local.properties`, keystores, `.env` files and build outputs are git-ignored.
- The HTTP logger redacts `apikey`/`token` query parameters and is limited to basic request lines in debug.
- A compiled client-side key is extractable. The free Twelve Data plan is intended for personal/development
  use; for production distribution, proxy requests through a backend you control.

Full details: [docs/PRIVACY.md](docs/PRIVACY.md).

## Financial disclaimer

MiPresupuesto CR is for **personal budgeting and informational purposes only**. It does not provide
financial, investment, tax or legal advice. Exchange rates and ETF prices are third-party reference data and
may be delayed, incomplete or wrong. The app is not affiliated with any bank, broker or data provider, and the
reference rate is **not** an official Banco de Costa Rica or central-bank rate. Verify all figures before making
financial decisions.

## License

MIT — see [LICENSE](LICENSE).
