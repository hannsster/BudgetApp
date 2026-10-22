# API setup

The app talks to two external services, each behind an interface and each optional in the sense that the app
still works (with cached or manually entered data) when it cannot reach them.

## 1. USD/CRC reference rate — open.er-api.com

| Item | Value |
| --- | --- |
| Provider | ExchangeRate-API "Open Access" (`open.er-api.com`) |
| Endpoint used | `GET https://open.er-api.com/v6/latest/USD` |
| API key | **Not required** |
| Update cadence | Daily (the service publishes `time_last_update_unix`) |
| Terms | Attribution required; caching and personal/commercial conversion use permitted; redistribution not permitted; excessive polling is rate limited (HTTP 429) |

The app uses the daily update timestamp as the rate timestamp and stores the provider name for attribution.
The value is presented as a **reference** rate. It is a blended market rate from the provider's sources —
**not** an official Banco de Costa Rica, BCCR or central-bank rate, and the app never claims otherwise.

If a different rate source is required:

1. Create a class implementing `com.mipresupuestocr.domain.provider.ExchangeRateProvider`.
2. Map failures to `AppError` values (`RateLimited`, `InvalidApiKey`, `Timeout`, `MalformedResponse`, …).
3. Bind it in `app/src/main/java/com/mipresupuestocr/app/di/BindingsModule.kt`.
4. If it needs a key, reuse `MarketDataConfig`/`MarketDataKeySource` so the user can paste it in Settings, or add
   a `buildConfigField` in `app/build.gradle.kts` read from `local.properties`.

The repository, cache, dashboard and tests do not change.

## 2. ETF quotes — Twelve Data

| Item | Value |
| --- | --- |
| Provider | Twelve Data |
| Endpoint used | `GET https://api.twelvedata.com/quote?symbol=VTI&apikey=…` |
| API key | Required, entered by the user in Settings (no rebuild needed) |
| Free plan | 8 API credits/minute, 800/day; ETF coverage for US-listed instruments is explicitly included |
| Data | `symbol`, `currency`, `close`, `timestamp`, `status` |
| Terms | Free plan is for development/evaluation. Review display, redistribution and commercial-use terms before publishing an app that shows the data to end users |

### Getting a key (the normal path — no build required)

1. Create an account at <https://twelvedata.com/> and copy the API key from the dashboard.
2. Install the app from the latest GitHub Release.
3. Open **Ajustes → Datos de mercado** (Settings → Market data), paste the key and tap **Guardar clave**.
4. Tap **Actualizar precios** to fetch quotes immediately.

The key is stored in the app's private DataStore on that device. It is masked while typing, never echoed back,
never logged, and never compiled into the APK. This is why a person who installs the released APK does not need
to build the app or touch `local.properties`.

### Key resolution order

`MarketDataConfig.apiKey()` returns the first non-blank value of:

1. the key saved in Settings (device storage), then
2. `BuildConfig.TWELVE_DATA_API_KEY`, a build-time value for maintainer-built, preconfigured APKs.

To preconfigure a build of your own, put `TWELVE_DATA_API_KEY=…` in `local.properties` (git-ignored) or export
it as an environment variable before running Gradle; the CI workflow forwards a repository secret of the same
name, and works fine when the secret is absent. A key entered in Settings always overrides the built-in one.

**Never** commit the key, paste it into an issue, or print `BuildConfig` values. A compiled-in key is extractable
from the APK, and a key on the device is extractable by anyone with full control of that device; treat it as a
personal-use credential.

### Request behaviour

- Symbols are fetched **sequentially**, one per ticker, to stay within the free plan's 8 credits/minute.
- A partial batch is accepted: symbols that succeed are cached, symbols that fail are reported as
  `failedTickers` and shown in the portfolio ("No price: …").
- An empty portfolio makes **no** network call.
- Non-USD instruments are rejected with `UnsupportedAsset` rather than converted without a rate.

## 3. App updates — GitHub Releases API

| Item | Value |
| --- | --- |
| Provider | GitHub REST API |
| Endpoint used | `GET https://api.github.com/repos/hannsster/BudgetApp/releases/latest` |
| API key | **Not required** (unauthenticated, 60 requests/hour per IP) |
| Download | the release asset named `*.apk`, via its `browser_download_url` |
| Data | `tag_name`, `assets[].name`, `assets[].browser_download_url`, `assets[].size`, `html_url` |

The check compares `tag_name` with `BuildConfig.VERSION_NAME` numerically (`AppVersion`), ignores drafts and
prereleases, and only offers a release that actually carries an APK asset. The download streams into
`cacheDir/updates/`; a partial file is deleted on failure so it can never be installed.

Installing goes through `FileProvider` + `ACTION_VIEW` with the package-archive MIME type. Android requires the
new APK to be signed with the same key as the installed app, so builds published from this repository are signed
with its own keystore (see the README's release section). To point the app at a fork, change `UpdateConfig`.

## Error handling matrix

| Situation | Detection | `AppError` | UI behaviour |
| --- | --- | --- | --- |
| No network / DNS failure | `IOException`, `UnknownHostException` | `Network` | Error banner, cached data stays visible, retry available |
| Timeout | `SocketTimeoutException` | `Timeout` | Same as network |
| HTTP 429 | status code | `RateLimited` | Banner asks to retry later; backoff on background work |
| Quota / plan exhausted | provider payload or HTTP 403 | `QuotaExceeded` / `InvalidApiKey` | Banner, no retry loop |
| Invalid key | HTTP 401/403, provider error payload | `InvalidApiKey` | Banner; settings still show key as configured |
| Missing key | no key in Settings and no `BuildConfig` value | `MissingApiKey` | Portfolio setup card points to Settings → Market data |
| Unknown ticker | provider error payload / 404 | `UnknownSymbol` | Ticker listed as failed, other quotes unaffected |
| Non-USD instrument | `currency` field | `UnsupportedAsset` | Ticker listed as failed |
| Empty body | null body | `EmptyResponse` | Banner |
| Unparseable JSON | `SerializationException` | `MalformedResponse` | Banner |
| Currency not offered | missing entry in `rates` | `UnsupportedCurrency` | Banner |
| Stale data | timestamp older than 48 h (FX) | — | "Stale data" chip, values still shown |

No provider error message is passed through to the UI or logs; only the mapped enum is used.

## Background refresh

`RefreshWorker` runs every six hours with a `NetworkType.CONNECTED` constraint:

- Retryable: `Network`, `Timeout`, `RateLimited`, `Unknown`, and cached-fallback results.
- Non-retryable: missing/invalid key, exhausted quota, unsupported currency/asset, malformed response,
  unknown symbol. These return success so WorkManager does not spin.

## Attribution

The exchange-rate provider's terms require attribution. The dashboard and settings screens display the source
name (`open.er-api.com`) with the rate. Keep that visible if you change the design.
