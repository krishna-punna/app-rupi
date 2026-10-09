# app-rupi

Android app for [Daily Rupi](https://github.com/krishna-punna/daily-rupi), the personal daily expense tracker.
It talks to the existing Daily Rupi Spring Boot backend: the web app's REST API, plus the sync endpoints from
[daily-rupi#8](https://github.com/krishna-punna/daily-rupi/pull/8) and the date filters from
[daily-rupi#9](https://github.com/krishna-punna/daily-rupi/pull/9), which the backend needs for offline sync.

Requirements: [docs/requirements.md](docs/requirements.md).

## What works

Works against the backend's existing cookie session and CSRF login. Expenses are saved on the phone first and synced in the background.

| Requirement | Status |
| --- | --- |
| Server setup (first launch, and from More) | Done: enter the address, the app checks it is a Daily Rupi server before saving |
| A1 Log in, A2 forced password change, A5 log out | Done |
| A3 Stay logged in | Done within the server's session: the session cookie is kept, encrypted with an Android Keystore key, across restarts. The backend still ends idle sessions after 30 minutes; token login (requirements, backend change 3) is needed to stay logged in longer |
| E1 Add expense, E2 quick add, E3 list, E4 edit and delete with Undo, E5 summary bar | Done; the list shows the last 7 days, or a day picked with **Pick date** while the server is reachable. The summary bar's totals come from the server only, so they are blank offline |
| B1 Budgets view | Done (setting budgets, B2, is still web only) |
| Offline entry and sync | Done: add, edit and delete are saved on the phone (Room) and sent by WorkManager when the server is reachable, oldest first, each new expense with its own id so a retry is saved once. Only the last 7 days of synced expenses stay on the phone (plus the picked day while it is shown); unsynced changes always stay and are listed with "Not synced" or "Needs attention". Offline, the list shows those 7 days. Pull to refresh syncs both ways. The item list and payment methods are cached for offline use. Budgets stay online only |
| Sync screen | Done: from the Expenses top bar (badge with the unsynced count) or More. Shows what has not synced and why, the last sync time, and Push now |
| Offline login | Done: when the server cannot be reached, the last user who logged in online can log in with their password, checked against a salted PBKDF2 hash made on the phone and kept encrypted with a Keystore key. Forgotten on Log out, when the server refuses a login, and after 5 wrong passwords offline. Syncing waits for the next online login |
| A4, M1, M2, N1, D1, W1, X1 | Not started (P2 and P3) |

## Stack

Kotlin, Jetpack Compose with Material 3, MVVM with Hilt, Retrofit + OkHttp + kotlinx.serialization
(amounts are `BigDecimal`, never floating point), Room for expenses kept on the phone, WorkManager for sync,
DataStore for small settings.
Android 8.0 (API 26) and newer.

- `core/`: plain Kotlin, no Android. The API client (cookie jar, CSRF header, session expiry), the
  backend's models, the sync engine (`sync/`), offline login, Indian rupee and date formatting, and the
  expense and password rules.
- `app/`: the Android app. Screens and view models under `ui/`, repositories, session handling and the Room
  database under `data/`, the background sync worker under `sync/`.

## Build

Needs JDK 17 and the Android SDK (platform 35).

```sh
./gradlew :core:test :app:testDebugUnitTest   # unit tests
./gradlew :app:assembleDebug                   # app/build/outputs/apk/debug/app-debug.apk
```

CI runs both on every push and pull request and keeps the debug APK as a build artifact.

## Trying it against the backend on your computer

The backend has no HTTPS yet, so test with a **debug** build, which allows plain `http://`
(release builds are HTTPS only). Start the backend with secure cookies off, otherwise the
session cookie is never sent back over http:

```sh
COOKIE_SECURE=false mvn spring-boot:run
```

- **Emulator:** use server address `http://10.0.2.2:8080`, which reaches the computer's `127.0.0.1`.
- **Phone on the same Wi-Fi:** the backend only listens on `127.0.0.1`. Also set
  `SERVER_ADDRESS=0.0.0.0` when starting it, and use `http://<computer's LAN IP>:8080`.
  Only do this on a network you trust.
