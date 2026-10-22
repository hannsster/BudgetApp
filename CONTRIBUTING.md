# Contributing

Thanks for helping. This project favours small, reviewable changes and honest data handling.

## Before you start

- Open an issue for anything larger than a bug fix or a small UI tweak, so the approach can be agreed first.
- Never include API keys, real account numbers, brokerage credentials or personal financial data in issues,
  commits, tests or screenshots.

## Development setup

1. JDK 17 and Android SDK 35.
2. `local.properties` with `sdk.dir=…` (never committed).
3. Optional: `TWELVE_DATA_API_KEY=…` in `local.properties` for live quotes. Tests do not need it.
4. Run the checks before pushing:

   ```bash
   ./gradlew :domain:test :app:testDebugUnitTest lintDebug assembleDebug
   ```

## Architecture rules

- `:domain` stays free of Android and framework dependencies.
- UI code never imports Room or Retrofit types.
- New external data source → new implementation of `ExchangeRateProvider` or `MarketQuoteProvider`; do not call
  Retrofit services from view models.
- Money stays `Money`/`BigDecimal`; do not introduce `Double` for currency amounts.
- Errors are mapped to `AppError`; never surface raw provider messages, URLs or exception text.
- Keep UI state immutable and produced by a single view model per screen.

## Style

- Kotlin official style, 4-space indent, max line length 120 (see `.editorconfig`).
- Compose functions are PascalCase; one screen per file; shared widgets go in `ui/components`.
- Persist user-facing text in `values/strings.xml` (Spanish) **and** `values-en/strings.xml`.
- Add KDoc for non-obvious domain rules (rounding, period math, fallback behaviour).
- No new dependency without a reason in the pull request description.

## Tests

- Every calculator change needs a unit test in `:domain`.
- Every provider change needs a MockWebServer test.
- Bug fixes include a regression test.
- Never make real network calls in tests.

## Commits and pull requests

- Conventional commits: `feat:`, `fix:`, `test:`, `docs:`, `refactor:`, `chore:`, `ci:`.
- One logical change per pull request; describe the behaviour change and how you verified it.
- Pull request checklist:
  - [ ] Unit tests pass locally.
  - [ ] `lintDebug` passes without new warnings.
  - [ ] Strings added in both locales.
  - [ ] No secrets, keystores or `local.properties` in the diff.
  - [ ] Documentation updated when behaviour or configuration changes.
  - [ ] Financial wording remains neutral (no advice, no guaranteed returns).

## Security reports

Do not open a public issue for a vulnerability. Contact the maintainer privately with reproduction steps and
avoid including real credentials or personal data.
