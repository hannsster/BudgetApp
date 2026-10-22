# Roadmap

Scope is deliberately small for 1.0.0: a trustworthy offline budgeting core with honest data freshness.

## Shipped in 1.0.0

- Budget entries, categories, per-category targets.
- Monthly and payday-anchored biweekly periods.
- 50/30/20 calculator with custom split and monthly normalisation.
- Manual ETF holdings, USD/CRC valuation, allocation and unrealized gain/loss.
- Reference USD/CRC rate with cache fallback and manual override.
- Twelve Data quotes with missing-key, partial-batch and unknown-symbol handling.
- Background refresh, Material 3 theme (dynamic colour, dark mode), English + Costa Rican Spanish.
- Unit tests for all calculators, providers and repositories; GitHub Actions CI.

## 1.1 — Budgeting depth

- Recurring transactions (rent, salary) with a WorkManager scheduler.
- Per-period target overrides instead of one target for all periods.
- Category management UI (add, rename, reorder, archive).
- CSV export of transactions for personal backups (explicit user action, no cloud).

## 1.2 — Lock and privacy options

- Optional app lock: PIN hashed with an Android Keystore-backed key, or `BiometricPrompt` with PIN fallback.
- Screenshot protection flag on portfolio and budget screens.
- "Hide amounts" quick toggle for public settings.

## 1.3 — Broker import

- IBKR CSV import (positions and cost basis) through a parser with preview and validation.
- Generic CSV mapping for other brokers.
- No credential-based brokerage integration; official APIs only, and only with explicit user authorisation.

## 1.4 — Insights

- Monthly trend charts for spending and savings rate.
- Category comparison between periods.
- 50/30/20 adherence view based on category buckets.
- Optional locale-aware month naming and Costa Rican holiday handling.

## 2.0 — Optional encrypted sync (only if users ask for it)

- End-to-end encrypted backup with a user-held passphrase.
- Requires a backend or user-provided storage, a threat model and a migration path; not part of the local-first
  default.

## Explicitly out of scope

- Scraping bank or broker sites, or automating bank logins.
- Presenting any rate as an official BAC/BCCR rate without a verified, permitted source.
- Investment, tax or legal advice, projections or recommendations.
- Copying another app's branding, icons, layouts or proprietary assets.
