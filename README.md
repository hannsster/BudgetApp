# MiPresupuesto CR

A local-first Android budgeting and ETF portfolio app built for Costa Rica. Budget in colones, plan with
the 50/30/20 rule, track USD-listed ETFs, and see everything converted with a reference USD/CRC rate.

**This repository distributes a ready-to-install APK.** You do not need Android Studio, Gradle, a JDK or any
API key to use the app — download the APK and install it. Building from source is only for people who want to
change the code.

- **Base currency:** CRC (₡). **Secondary display currency:** USD.
- **Storage:** on-device only (Room + DataStore). No account, no backend, no cloud backup.
- **Languages:** Costa Rican Spanish (default) and English.
- **Design:** Windows 11 surface language (flat bordered cards, 8 dp corners, sharp controls) on Catppuccin
  colours, with Mocha as the default dark theme and Latte for light. No third-party branding or assets are
  reused.

## Install the app

1. Open the [latest release](../../releases/latest) and download `MiPresupuestoCR-<version>.apk`.
2. Open the downloaded file on the phone. Android asks once for permission to install from that app
   (browser or file manager); allow it, then confirm the install.
3. Open **MiPresupuesto CR** from the app drawer.

Requirements: Android 8.0 (API 26) or newer. The APK is signed, so it installs on any stock device without
extra steps. Play Protect may ask you to confirm installing an app it has not seen before — that is expected
for an app delivered outside the Play Store.

Each release also publishes a `.sha256` file next to the APK. To check a download on a computer:

```bash
sha256sum -c MiPresupuestoCR-<version>.apk.sha256
```

### Updating without losing anything

Open **Ajustes → Actualizaciones** (Settings → Updates) and tap **Buscar actualizaciones**. When a newer
release exists the app downloads its APK and hands it to Android's installer; the install replaces the app in
place, so budgets, entries and holdings stay exactly as they were.

Two conditions apply, both enforced by Android rather than by the app:

- The update must be signed with the same key as the installed copy. Releases built by the workflow below are
  signed with the project's own keystore; a build made without it is debug-signed and cannot update it.
- The first install of a differently-signed build (for example switching from an early debug-signed APK) still
  needs an uninstall, which erases app data. From then on, updates are in-place.

### Optional: turn on automatic ETF prices

Budgeting, the 50/30/20 calculator and the USD/CRC reference rate work immediately with no setup. Only live
ETF quotes need a free market-data key:

1. Get a free key at <https://twelvedata.com/pricing> (the free plan covers US-listed ETFs).
2. In the app open **Ajustes → Datos de mercado** (Settings → Market data).
3. Paste the key and tap **Guardar clave**. Tap **Actualizar precios** to fetch quotes.

The key is stored only in the app's private storage on that device, is never shown again, and is never sent
anywhere except to the provider you chose. Without a key you can still record positions manually, with or
without a purchase price; prices simply stay blank and are labelled as such.

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
- **Correct a balance without fake entries** — set the available, income, spending or a category's total to the
  right number; the difference is kept as a period correction, marked as adjusted, and removable again.
- **In-app updates** — the app checks its own GitHub Releases feed, downloads the APK and passes it to
  Android's installer, so an update never touches your data.
- **Background refresh** — WorkManager refreshes rates and quotes every six hours when a connection exists.
- **Privacy-first defaults** — app data is excluded from backup; no analytics, tracking or ads.

## How releases are produced

[`.github/workflows/release.yml`](.github/workflows/release.yml) turns a version tag into a downloadable APK:

```bash
git tag -a v1.1.0 -m "MiPresupuesto CR 1.1.0"
git push origin v1.1.0
```

The workflow runs the unit tests, builds `assembleRelease` with the tag as `versionName` and the run number as
`versionCode`, verifies the signature with `apksigner`, prints and publishes a SHA-256 checksum, uploads the APK
as a workflow artifact, and attaches it to a GitHub Release. Tags are the only way a release is published; the
regular [CI workflow](.github/workflows/android.yml) still tests every push and pull request.

Releases are signed with the project's own keystore, which lives outside version control (`release.jks` plus
its four `RELEASE_*` entries in `local.properties`). Lose it and future releases can no longer update installed
copies, so keep a backup of the keystore **and** its password. Without any keystore the build still produces an
installable APK, but debug-signed: a later build from another machine is a different app to Android and will
not update in place. A half-configured keystore path fails the build instead of silently falling back.

For CI, add four repository secrets:

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `RELEASE_KEYSTORE_PASSWORD` | store password |
| `RELEASE_KEY_ALIAS` | key alias |
| `RELEASE_KEY_PASSWORD` | key password |

```bash
keytool -genkeypair -v -keystore release.jks -alias mipresupuesto \
  -keyalg RSA -keysize 4096 -validity 10000
```

Keep `release.jks` and its passwords backed up outside this repository; losing them means future releases
cannot update installed copies. Keystores, `*.jks`, `local.properties` and `.env` files are git-ignored.

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
    data/settings            DataStore-backed settings and market-data key
    di                       Hilt modules
    ui                       Compose screens, view models, navigation, theme
    worker                   Periodic refresh worker and scheduler
  src/test                   JVM unit tests
  src/androidTest            Compose UI tests (device/emulator)
domain/                      Pure Kotlin domain module (no Android dependencies)
docs/                        Architecture, data flow, API setup, user guide, privacy, roadmap, testing
gradle/libs.versions.toml    Version catalog
scripts/                     Repository bootstrap helpers
.github/workflows/           CI (android.yml) and APK releases (release.yml)
```

## Configuration and API providers

| Purpose | Provider | Key | Notes |
| --- | --- | --- | --- |
| USD/CRC reference rate | `open.er-api.com` (ExchangeRate-API open access) | none | Daily update, attribution required, hourly-friendly |
| ETF quotes | Twelve Data | entered in Settings | Free plan: 8 credits/minute, 800/day; ETF coverage explicitly included |
| App updates | GitHub Releases API (`api.github.com`) | none | Public read-only check, 60 requests/hour per IP |

Both sit behind interfaces (`ExchangeRateProvider`, `MarketQuoteProvider`), so another provider can be added
without touching the UI or repositories. Details and error handling: [docs/API_SETUP.md](docs/API_SETUP.md).

## Building from source (contributors only)

Daily use needs none of this. If you want to modify the app:

1. Install JDK 17 and Android Studio Ladybug or newer with SDK Platform 35 and Build Tools 35.0.0.
2. Clone the repository and create `local.properties` (git-ignored) pointing at your SDK:

   ```properties
   sdk.dir=/path/to/Android/Sdk
   ```

3. Build and install:

   ```bash
   ./gradlew assembleDebug
   ./gradlew installDebug
   ```

4. Optional, for a developer's own device: a key in `local.properties` (`TWELVE_DATA_API_KEY=...`) preconfigures
   the app so you do not have to paste it on the settings screen. The key entered in Settings always wins, so
   shipped APKs do not need a build-time secret at all.

```bash
./gradlew assembleDebug     # debug build, applicationId suffix .debug
./gradlew assembleRelease   # minified release build, signed as described above
./gradlew lintDebug         # Android lint
```

### Testing

```bash
./gradlew :domain:test            # pure business rules: 50/30/20, conversion, budgets, valuation
./gradlew :app:testDebugUnitTest  # providers, repositories, parsing, key resolution
./gradlew :app:connectedDebugAndroidTest  # Compose UI tests (needs a device/emulator)
```

Unit tests never touch the network: provider tests use MockWebServer and repositories use in-memory fakes.
See [docs/TESTING.md](docs/TESTING.md) for the scenario matrix.

## Security and privacy

- All budgeting and portfolio data stays in the app's private storage.
- The market-data key is typed in Settings, stored in the app's private DataStore, masked on entry and never
  displayed again, logged or committed.
- Cloud backup and device-transfer extraction are disabled in `data_extraction_rules.xml`.
- No analytics, crash-reporting or advertising SDKs.
- `local.properties`, keystores, `.env` files and build outputs are git-ignored.
- The HTTP logger redacts `apikey`/`token` query parameters and is limited to basic request lines in debug.
- A key held on the device is extractable by anyone with full control of that device. The free Twelve Data plan
  is intended for personal use; for distribution at scale, proxy requests through a backend you control.

Full details: [docs/PRIVACY.md](docs/PRIVACY.md).

## Financial disclaimer

MiPresupuesto CR is for **personal budgeting and informational purposes only**. It does not provide
financial, investment, tax or legal advice. Exchange rates and ETF prices are third-party reference data and
may be delayed, incomplete or wrong. The app is not affiliated with any bank, broker or data provider, and the
reference rate is **not** an official Banco de Costa Rica or central-bank rate. Verify all figures before making
financial decisions.

## License

MIT — see [LICENSE](LICENSE).
