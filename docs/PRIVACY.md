# Privacy and security

## Summary

MiPresupuesto CR is local-first. Your budgeting data, holdings and preferences are stored only in the app's
private storage on your device. There is no account, no backend, no analytics and no advertising.

## What is stored, and where

| Data | Storage | Protection |
| --- | --- | --- |
| Transactions, categories, holdings | Room database in app-private storage | Android app sandbox |
| Cached exchange rate and quotes | Room database | Android app sandbox |
| Preferences (period, payday, split, theme) | DataStore | Android app sandbox |
| Market-data API key | Compiled into the APK at build time | See the caveat below |

Cloud backup and device-to-device transfer are explicitly disabled
(`app/src/main/res/xml/data_extraction_rules.xml`), so this data is not copied to Google Drive or a new device
by the system.

## Network access

The only permissions requested are `INTERNET` and `ACCESS_NETWORK_STATE`. The app contacts exactly two hosts:

1. `https://open.er-api.com` — USD/CRC reference rate (no key, no identifiers sent).
2. `https://api.twelvedata.com` — ETF quotes (the API key and ticker symbols are sent).

No personal or financial data is sent to either provider: requests contain only the base currency, the ticker
symbols you hold, and the API key. There is no telemetry endpoint, crash reporter or ad SDK.

## API keys

- The market-data key is typed in **Settings → Market data** and stored in the app's private DataStore
  (`/data/data/com.mipresupuestocr.app`), which other apps cannot read on a non-rooted device. It is masked while
  typing, never shown again, never logged, and excluded from backup by the app's data-extraction rules.
- A build-time key from `local.properties` (git-ignored) or a CI environment variable is only a fallback for
  preconfigured builds. It is never committed and never displayed in the UI.
- The HTTP logger redacts `apikey`, `api_key`, `key` and `token` query parameters and logs at most basic request
  lines in debug builds.
- **Caveat:** a key stored on a device can be read by anyone with full control of that device (root, ADB backup),
  and a key compiled into the APK can be extracted from it. Treat it as a personal credential, use a plan
  intended for personal use, and rotate it if it leaks. For a widely distributed app, proxy requests through a
  backend you control.
- Never paste a key into a chat, an issue, a commit or a screenshot.

## What the app deliberately does not do

- It does not ask for banking credentials, IBKR credentials, card numbers or identity documents.
- It does not read SMS, contacts, location or other apps' data.
- It does not use `EncryptedSharedPreferences` because it stores no device secrets; if you add such a secret
  later, put it in the Android Keystore-backed storage.
- It does not implement an app lock in 1.0.0. Device lock is the protection boundary; see the roadmap for PIN
  and biometric options.

## Permissions and exports

No file export, sharing or clipboard features exist in 1.0.0, so the app cannot leak data through those paths.

## Deleting your data

Uninstalling the app or clearing its storage in Android settings removes the database, cache and preferences.
There is no server-side copy to delete.

## Third-party terms

The rate provider requires attribution and forbids redistribution; the market-data provider's free plan is for
development and personal use. Review their terms before publishing a build that shows their data to end users.

## Not financial advice

The app is a budgeting tool. It does not provide financial, investment, tax or legal advice, and all rates and
prices are third-party reference data that may be delayed or incorrect.
