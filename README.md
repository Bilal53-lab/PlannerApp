# PlannerApp

Two offline Android apps, each a single-page web UI inside a small native WebView shell.

## My Workspace (`app/`, `MyWorkspace.apk`)

Tasks, notes, short- and long-term goals, projects, calendar, focus timer, analytics and reports.

## Ledger (`expenses/`, `Ledger.apk`)

Offline expense and budget tracker: income and spending, categories, monthly
budgets with progress, multiple accounts, recurring bills, a spending donut,
a six-month income-vs-spending trend, monthly statements, CSV export and backup.

## Lumen PDF (`pdfreader/`, `LumenPDF.apk`)

PDF reader with a library, search, page thumbnails, chapters, bookmarks, drawing and highlighting,
reading themes, auto-scroll, read aloud and reading statistics. Rendering uses
[PDF.js](https://mozilla.github.io/pdf.js/) 3.11 (Apache 2.0), bundled in `pdfreader/src/main/assets`.

## Build

Needs JDK 17+, Gradle 8.9+ and the Android SDK (platform 35, build-tools 35.0.0).

```bash
gradle assembleDebug
```

APKs are written to `app/build/outputs/apk/debug/` and `pdfreader/build/outputs/apk/debug/`.
Both are debug-signed and run on Android 7.0+.
