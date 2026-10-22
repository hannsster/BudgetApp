# Changelog

All notable changes are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
