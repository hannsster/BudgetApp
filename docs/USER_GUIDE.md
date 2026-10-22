# User guide

MiPresupuesto CR helps you plan a budget in colones and keep an eye on USD-listed ETFs, with a reference
USD/CRC rate so both currencies make sense together.

## First launch

- The app seeds a set of starter categories (housing, groceries, transport, utilities, health, dining,
  entertainment, shopping, savings, investing, debt payments, salary) in your device language. No targets or
  amounts are invented — you set those.
- Nothing leaves the device. There is no account and no cloud backup.

## Dashboard

- **Period header:** use the arrows to walk month by month (or 14-day cycle by cycle).
- **Available:** income recorded in the period minus expenses, with a progress bar for how much of the income is
  already spent.
- **USD/CRC rate:** current reference rate, its source, and its timestamp. "Manual rate" appears if you entered
  your own rate; "Stale data" appears when the cached rate is older than 48 hours.
- **Portfolio:** total value in USD and CRC, unrealized gain or loss (only when every priced holding has an
  average purchase price), and when quotes were last updated.
- **Category targets:** progress for categories that have a target.
- Pull the refresh icon in the top bar (or the refresh action on the portfolio) to update rates and quotes.

## Recording income and expenses

1. Tap **New entry**.
2. Choose **Expense** or **Income**.
3. Enter the amount in colones. Both `1234,56` and `1234.56` work; `350 000` and `1.234,56` are accepted too.
4. Pick a category (optional), the date, and an optional note.
5. Save. Tap an entry in the Budget screen to edit or delete it.

## Setting a budget target

1. Open **Budget**.
2. Tap a category row.
3. Enter the amount in colones and save. Use **Delete** in that dialog to remove the target.

Targets apply to every period of the selected type, not just the current month.

## Monthly vs biweekly budgeting

- **Monthly** follows the calendar month.
- **Biweekly** follows 14-day windows anchored on your first payday. Open **Settings → Budget period**, choose
  **Biweekly** and pick your first payday.

Costa Rican biweekly payrolls usually land on the same weekday every two weeks; setting the first one is enough
for the app to calculate every following window.

## 50/30/20 calculator

1. Enter your **net income per payment**.
2. Choose **Monthly**, **Biweekly** or **Weekly**.
3. Read the suggested split: 50 % needs, 30 % wants, 20 % savings/investing/debt repayment.
4. The monthly equivalent uses 26 biweekly payments or 52 weekly payments per year.
5. Optionally edit the three percentages (they must total 100) and tap **Use this split** to make it the default
   used elsewhere in the app. **Reset to 50/30/20** restores the standard split.

## Tracking ETFs

1. Open **Portfolio → Add ETF**.
2. Enter the ticker exactly as listed (for example `VTI`), the number of units, the optional average purchase
   price in USD, and optional notes.
3. Save. If market data is configured, the app fetches the latest price for that ticker.

Each position shows units × price, value in USD and CRC, allocation share, unrealized gain/loss (when a purchase
price exists), the data source and the quote timestamp.

To enable automatic prices you need a free Twelve Data key in `local.properties` — see
[API_SETUP.md](API_SETUP.md). Without a key the positions remain editable and are listed as **No price**; totals
only include priced positions.

## Exchange rate and manual override

- The reference rate is fetched automatically and cached.
- If you prefer to budget with your bank's rate, open **Settings → Manual rate**, enter colones per dollar and
  save. The app labels it **Manual rate** everywhere and never presents it as an official rate.
- Remove the manual rate to go back to the reference rate.
- When a refresh fails, the last saved rate stays visible and the UI marks it stale rather than showing an error
  instead of numbers.

## Themes, language and accessibility

- **Settings → Theme:** System, Light or Dark. Android 12+ also uses your wallpaper's dynamic colours.
- **Language:** follows the device language. Spanish (Costa Rica) is the default; English is included.
- Text scales with the system font size, and icons have content descriptions for screen readers.

## Background refresh

Rates and prices refresh every six hours when a connection is available. You can always refresh manually.

## Troubleshooting

| Symptom | What to do |
| --- | --- |
| "Market data key missing" | Add `TWELVE_DATA_API_KEY` to `local.properties` and rebuild |
| "Key not configured" but you set it | Rebuild after editing `local.properties`; the value is compile-time |
| A ticker stays "No price" | Check the symbol; the app only supports USD-listed instruments |
| Rate shows "Stale data" | Refresh manually; if offline, the last saved rate stays |
| Biweekly shows a payday error | Set your first payday in Settings |
| Amount rejected | Use digits with at most two decimals, for example `1500` or `1500,75` |

## What the app does not do

- It does not connect to banks or brokers, and it never asks for banking or IBKR credentials.
- It does not provide financial, investment, tax or legal advice.
- It does not sync or back up to the cloud.
