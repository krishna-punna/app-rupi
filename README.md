# app-rupi

Android app for [Daily Rupi](https://github.com/krishna-punna/daily-rupi), the personal daily expense tracker.
It talks to the existing Daily Rupi Spring Boot backend, using its REST API unchanged.

Requirements: [docs/requirements.md](docs/requirements.md).

## What works

First build, online only, against the backend's existing cookie session and CSRF login.

| Requirement | Status |
| --- | --- |
| Server setup (first launch, and from More) | Done: enter the address, the app checks it is a Daily Rupi server before saving |
| A1 Log in, A2 forced password change, A5 log out | Done |
| A3 Stay logged in | Done within the server's session: the session cookie is kept, encrypted with an Android Keystore key, across restarts. The backend still ends idle sessions after 30 minutes; token login (requirements, backend change 3) is needed to stay logged in longer |
| E1 Add expense, E2 quick add, E3 list, E4 edit and delete with Undo, E5 summary bar | Done |
| B1 Budgets view | Done (setting budgets, B2, is still web only) |
| Offline entry and sync | Not yet: needs the backend's client ids and changed-since filter first (requirements, backend change 4) |
| A4, M1, M2, N1, D1, W1, X1 | Not started (P2 and P3) |

## Stack

Kotlin, Jetpack Compose with Material 3, MVVM with Hilt, Retrofit + OkHttp + kotlinx.serialization
(amounts are `BigDecimal`, never floating point), DataStore for small settings.
Android 8.0 (API 26) and newer.

- `core/`: plain Kotlin, no Android. The API client (cookie jar, CSRF header, session expiry), the
  backend's models, Indian rupee and date formatting, and the expense and password rules.
- `app/`: the Android app. Screens and view models under `ui/`, repositories and session handling under `data/`.

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
