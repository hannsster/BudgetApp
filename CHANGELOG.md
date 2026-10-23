# Changelog

All notable changes are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.5.1] - 2026-10-23

### Added

- The net worth total also counts money recorded with **Sin cuenta**: a paycheck the budget shows as
  available now lands in *Patrimonio total*, added once — anything already assigned to an account was
  counted through that account's balance and is not added again.

### Changed

- Version bumped to 1.5.1.

## [1.5.0] - 2026-10-23

### Added

- Transfers between accounts: a **Transferir** button on the Cuentas screen moves money from one account to
  another. A transfer changes both balances but is recorded separately, so it never counts as income or
  spending. Recent transfers are listed and can be removed, which puts both balances back.
- **Patrimonio total** at the top of the dashboard: account balances plus the portfolio at the current
  USD/CRC rate, with the two parts broken out below. When part of the portfolio cannot be valued the card says
  the total is a lower bound instead of hiding it.
- `account_transfers` table and a version 3 → 4 Room migration.

### Changed

- Deleting an account also removes the transfers that touched it, since one side of them would have nowhere
  to land; its entries are still kept.
- Version bumped to 1.5.0.

## [1.4.0] - 2026-10-23

### Added

- Accounts: keep money in named places — checking, savings, cash or investment — and see the balance of each
  plus the total. Each account has an optional starting balance, so a savings account does not start at zero
  just because the app only saw this month's entries.
- An entry can be assigned to an account; its balance adds income and subtracts expenses recorded for it.
- **Cuentas** card on the dashboard and a dedicated screen to create, edit and delete accounts. Deleting an
  account keeps its entries and only detaches them.
- `accounts` table, an `accountId` column on entries and a real version 2 → 3 Room migration.

### Changed

- Migrations are now required and registered explicitly instead of falling back to a destructive one, so a
  future schema bump can never silently wipe the data an update is meant to keep.
- Version bumped to 1.4.0 (versionCode 5).

## [1.3.0] - 2026-10-22

### Added

- In-app updates: **Settings → Updates** checks the project's GitHub Releases feed, downloads the APK and
  hands it to Android's installer. Data is preserved because the app is replaced, not reinstalled.
- `AppVersion` parsing and comparison, a GitHub release client, a downloader with progress, a FileProvider for
  the downloaded APK and the `REQUEST_INSTALL_PACKAGES` permission.
- A permanent release keystore (`release.jks`, git-ignored) so builds from this machine can update installs;
  a CI keystore is supplied through the four `RELEASE_*` secrets.

### Changed

- New visual language: Windows 11-style flat surfaces — cards on the lighter base layer with hairline borders
  and 8 dp corners, 4 dp controls, flat app bars — over Catppuccin palettes (Mocha dark, Latte light).
- Dark is now the default theme; dynamic colour is off by default so the palette is not replaced by the
  wallpaper one.
- Release signing fails loudly when a keystore is configured but missing, instead of silently shipping a
  debug-signed APK.
- Version bumped to 1.3.0 (versionCode 4).

## [1.2.0] - 2026-10-22

### Added

- Balance corrections: tap the available, income or spending figure on the dashboard, or a category on the
  budget screen, and type the total you know is right. The difference is stored per period as a correction, so
  the transaction history stays exactly what happened and later entries keep adding up. Corrected figures are
  marked "Ajustado" and can be removed again.
- `balance_corrections` table with a real version 1 → 2 Room migration, covered by a migration test that keeps
  existing budgets and entries.

### Fixed

- Adding a new ETF position showed the spinner forever with no form, because a new holding started in the
  loading state that only the edit path ever cleared.

### Changed

- All numbers are limited to two decimals: prices and exchange rates no longer show four, share counts are
  capped at two, and money keeps its currency's two decimals.
- Version bumped to 1.2.0 (versionCode 3).

## [1.1.0] - 2026-10-22

### Added

- Installable APK distribution: pushing a `v*` tag runs `.github/workflows/release.yml`, which tests,
  builds, verifies and publishes the APK to GitHub Releases with a SHA-256 checksum. Nobody needs a build
  toolchain to use the app.
- Market-data key entry in **Settings → Market data**, stored on the device, masked while typing and never
  displayed again. ETF prices no longer require editing `local.properties` and rebuilding.
- Optional `RELEASE_*` signing properties, with a debug-signing fallback so a release build is always
  installable, and `appVersionName`/`appVersionCode` Gradle overrides for tagged builds.
- `MarketDataConfigTest` and a fake key source covering runtime key resolution and fallback order.

### Changed

- The market-data key is resolved at runtime; a build-time `TWELVE_DATA_API_KEY` is now only a fallback for
  maintainer-built, preconfigured APKs.
- Missing-key messaging points to Settings instead of telling the user to rebuild.
- README and user guide lead with installing the APK; building from source is documented as a contributor path.
- Version bumped to 1.1.0 (versionCode 2).

## [1.0.0] - 2026-10-22

### Added

- Local-first budgeting with income/expense entries, custom categories and per-category targets.
- Monthly calendar and payday-anchored biweekly budget periods.
- 50/30/20 calculator with monthly, biweekly and weekly income input plus an optional custom split.
- Manual ETF holdings with valuation in USD and CRC, allocation, unrealized gain/loss and per-position freshness.
- Automatic USD/CRC reference rate from open.er-api.com with cached fallback and optional manual rate.
- ETF quote refresh through Twelve Data, with a clear setup state when no API key is configured.
- Dashboard, budget, calculator, portfolio and settings screens plus transaction and holding forms.
- WorkManager periodic refresh every six hours on unmetered/available networks.
- Material 3 theme with dynamic colour, dark mode and accessible typography.
- English and Costa Rican Spanish string resources.
- Domain, data-layer and parsing unit tests; a Compose UI test for shared components.
- GitHub Actions workflow for unit tests, lint and debug APK builds.
- Documentation set: architecture, data flow, API setup, user guide, privacy, roadmap, testing, contributing.

### Known limitations

- Harvesting official BAC or central-bank rates is not implemented; the app labels the fetched rate as a
  reference rate and allows a manual override.
- Holdings are entered manually. There is no brokerage integration, CSV import or IBKR access.
- Single-device storage only; no backup, sync or account system.
- Recurring transactions are not implemented yet.
