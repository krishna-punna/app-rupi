# app-rupi: Android App Requirements

Draft, 5 Oct 2026. Living version: [Claude Doc](https://claude.ai/code/artifact/c55dca37-2297-42ec-8421-ec97a79ad442).

## Overview

app-rupi lets Krishna log a rupee expense in under 10 seconds from the phone, and see today, this week and this month at a glance, using the same Spring Boot backend and MySQL data as the [Daily Rupi](https://github.com/krishna-punna/daily-rupi) web app.

The content below is based on what the web app on `main` does as of 5 Oct 2026 (login, expenses, master data, budgets, Today/Week/Month summary) plus its README roadmap. A web dashboard is in progress and is listed here as a later phase.

**Goals**

- Fast expense entry on the phone, including when there is no network.
- Same data as the web app: an expense added on the phone shows on the web and the other way round.
- Feature parity with the web app for daily use (expenses, summary, budgets); master data editing can follow.

**Non-goals for the first release**

- iOS, tablets and wear devices.
- Multiple users or shared household accounts (the backend is single user today).
- Bank SMS reading, receipt scanning and bank account linking.
- Reports and recurring expenses, which do not exist on the backend yet.

## Features

Priority: P1 = first release, P2 = soon after, P3 = later. Every rule in the web app's README applies unchanged, because the API enforces it.

| ID | Feature | Requirement | Priority |
| --- | --- | --- | --- |
| A1 | Log in | Username and password. Show one generic error for any failure. After 5 wrong attempts the backend locks login for 15 minutes; show that as a plain message. | P1 |
| A2 | Forced password change | If the account still has its temporary password, go straight to Change password. New password: at least 12 characters, must not contain the username. After the change, return to Log in. | P1 |
| A3 | Stay logged in | Keep the user logged in across app restarts until they log out or the session expires (see API integration). | P1 |
| A4 | App lock | Ask for fingerprint, face or device PIN when the app opens or returns after 5 minutes in the background. Can be turned off in Settings. | P2 |
| A5 | Log out | Ends the server session and clears all local data and credentials on the phone. | P1 |
| E1 | Add expense | Pick category, sub category and item (searchable), amount in ₹ with up to 2 decimals, date and time defaulting to now, payment method (UPI, Cash, Debit Card, Credit Card, Net Banking, Wallet, Auto Debit, Cheque), optional note up to 255 characters. Future dates refused. | P1 |
| E2 | Quick add | Remember the last 5 items used and offer them as one-tap chips at the top of Add expense; default payment method = last one used. | P1 |
| E3 | Expense list | Newest first, grouped by day with a day total, loads more on scroll (20 per page). | P1 |
| E4 | Edit and delete | Tap an expense to edit it; delete asks for confirmation and offers Undo for 5 seconds. | P1 |
| E5 | Summary bar | Today, This week (Monday start) and This month totals, always visible on the Expenses screen, refreshed after every add, edit or delete. | P1 |
| B1 | Budgets view | Pick a month; per category show budget, spent, left and a usage bar, overspends in red, unbudgeted spending shown separately, plus month totals. | P1 |
| B2 | Set budgets | Set or remove a category's monthly budget; copy last month's budgets in one tap (skips inactive categories, never overwrites). | P2 |
| M1 | Master data view | Browse and search the category, sub category and item tree, active and inactive. | P2 |
| M2 | Master data edit | Add, rename, activate, deactivate and delete, with the same rules as the web (items with expenses can only be deactivated; parents with children cannot be deleted). | P2 |
| D1 | Dashboard | Whatever the web dashboard (in progress) shows, adapted to a phone screen. | P3 |
| N1 | Daily reminder | Optional notification at a chosen time (default 9 pm) if nothing was logged today. | P2 |
| W1 | Home screen widget | Shows today's total and an Add expense button. | P3 |
| X1 | Export | Share the month's expenses as CSV. Needs a new backend endpoint or is built from local data. | P3 |

## Screens and navigation

The app opens on Expenses, with a bottom bar for Expenses, Budgets and More, and a floating Add button on Expenses. Log in and Change password sit outside the bottom bar.

| Screen | Reached from | Shows and does | Features |
| --- | --- | --- | --- |
| Server setup | First launch only | Enter the backend address; test the connection | A1 |
| Log in | Launch when logged out | Username, password, error and lockout messages | A1 |
| Change password | After login with a temporary password; More | Current, new and confirm password with the rules shown | A2 |
| Expenses (home) | Bottom bar | Summary bar, day-grouped list, pending-sync markers, Add button | E3, E4, E5 |
| Add / edit expense | Add button, tapping an expense, widget | Quick-add chips, item picker, amount keypad, date and time, payment method, note | E1, E2, E4 |
| Item picker | Add / edit expense | Search across all levels, or drill category → sub category → item | E1 |
| Budgets | Bottom bar | Month switcher, per-category bars, totals, set budget, copy last month | B1, B2 |
| Master data | More | Searchable tree with add, rename, status and delete actions | M1, M2 |
| Settings | More | App lock, reminder time, server address, log out, app version | A4, A5, N1 |
| Dashboard | Bottom bar (when built) | Phone version of the web dashboard | D1 |

Amounts are shown in Indian format (₹12,34,567.50) and dates as 5 Oct 2026. The app follows the system light or dark theme.

## API integration

The app uses the existing Daily Rupi REST endpoints unchanged for data, but the backend needs changes before a phone can use it at all: it must be reachable, served over HTTPS, and offer a login a mobile client can keep.

**Endpoints used as they are**

| Feature | Endpoints |
| --- | --- |
| Log in, log out, password | `GET /api/auth/csrf`, `POST /api/auth/login` (form fields), `POST /api/auth/logout`, `GET /api/auth/me`, `PUT /api/auth/password` |
| Expenses | `GET /api/expenses?page=&size=`, `GET /api/expenses/summary`, `POST /api/expenses`, `PUT /api/expenses/{id}`, `DELETE /api/expenses/{id}`, `GET /api/payment-methods` |
| Budgets | `GET /api/budgets/{yyyy-MM}`, `PUT` and `DELETE /api/budgets/{yyyy-MM}/categories/{id}`, `POST /api/budgets/{yyyy-MM}/copy-previous` |
| Master data | `GET /api/master-data?includeInactive=true` and the add, rename, status and delete endpoints under `/api/master-data` |

**How auth works today, and what it means for the app**

- The backend issues a server-side session in an HttpOnly, Secure, SameSite=Strict cookie that expires after 30 minutes idle. Every write needs the `XSRF-TOKEN` cookie echoed in an `X-XSRF-TOKEN` header.
- An Android HTTP client (OkHttp with a cookie jar) can do all of this: call `/api/auth/csrf`, log in, store both cookies, and send the header on writes. This is enough for a first build against a test server.
- The 30 minute session means the user would log in again almost every time they open the app, which breaks A3. Storing the password to log in silently is not acceptable.

**Backend changes needed (in daily-rupi)**

1. **Reachability.** The server listens on `127.0.0.1` only (`application.yml`), so a phone cannot reach it. It needs to be hosted somewhere the phone can reach (a small cloud VM, or a home server behind a tunnel such as Tailscale), with MySQL alongside.
2. **HTTPS.** Required for a hosted server; the Secure cookie flag and Android's cleartext block both assume it.
3. **Mobile login.** Recommended: add token endpoints for the app, `POST /api/auth/token` returning a short-lived access token (15 minutes) and a long-lived refresh token (30 days, rotated on use, revocable on logout), sent as `Authorization: Bearer`. Bearer requests skip CSRF, since CSRF only protects cookies. The web app keeps its cookie session. The forced password change and lockout rules apply to token login too.
4. **Sync support** (see Offline and sync): a client-generated id on expenses so a retried create is not saved twice, and an `updatedAt` filter so the app can fetch only what changed.
5. **Data ownership.** Expenses, budgets and master data have no `user_id` column, so all data is shared by every account. That is fine for one person; a second user would need a schema change first.

`spentAt` is a local date-time with no time zone, stored exactly as entered. The app sends the phone's local time in the same format (`2026-10-05T18:30:00`) and assumes both devices are on IST.

## Offline and sync

Adding, editing and deleting expenses must work with no network and sync automatically later; everything else may be read-only offline.

| Area | Offline behaviour |
| --- | --- |
| Add, edit, delete expense | Works fully. Saved to the phone first, marked "Not synced", sent when the network returns. |
| Expense list and summary | Shows the last synced data plus local changes. Summary totals include unsynced expenses. |
| Master data and payment methods | Cached copy used for the item picker; refreshed on every app open when online. Editing is online only. |
| Budgets | Last loaded months shown read-only; setting budgets is online only. |
| Log in, password change | Online only. |

**Sync rules**

1. Local changes are sent in the order they were made, using Android WorkManager so they go out even if the app is closed.
2. Each new expense carries an id made on the phone, so a create that is retried after a dropped connection is saved once.
3. If the server refuses a change (for example the item was made inactive on the web in the meantime), the expense stays on the phone flagged "Needs attention" with the server's reason, and the user fixes or deletes it.
4. If the same expense was edited on both web and phone, the last one saved to the server wins. For one person this conflict is rare and does not justify a merge screen.
5. Pull-to-refresh and app open fetch changes since the last sync.
6. Logging out with unsynced changes warns first and names how many will be lost.

## Non-functional requirements and tech stack

Recommended stack: native Kotlin with Jetpack Compose, which fits a Java and Spring developer and gives the best offline and widget support.

| Area | Requirement |
| --- | --- |
| Platform | Android 8.0 (API 26) and newer; phones only; portrait first |
| Language and UI | Kotlin, Jetpack Compose, Material 3 |
| Architecture | MVVM with a repository layer; Hilt for dependency injection |
| Networking | Retrofit with OkHttp and kotlinx.serialization; amounts as `BigDecimal` strings, never floating point |
| Local data | Room database for cached and pending expenses; WorkManager for sync |
| Secrets | Tokens in Android Keystore-backed encrypted storage; no password stored on the phone |
| Security | HTTPS only (cleartext disabled), no backups of the local database, screenshots allowed |
| Speed | Cold start to Expenses under 2 seconds; saving an expense feels instant because it is local first |
| Accessibility | TalkBack labels on all controls, 48dp touch targets, works at 200% font size |
| Quality | Unit tests for view models and sync; one UI test for the add-expense flow; CI build on GitHub Actions |
| Distribution | Signed APK installed directly to start; Play Store (internal testing track) optional later |

The alternative is Flutter or React Native, which would also give iOS later. Neither is recommended unless iOS is wanted, since the existing frontend is Angular and would share no code with either.

## Release phases

1. **Backend ready for mobile:** hosting, HTTPS, token login, client ids and change-since filter on expenses.
2. **Release 1 (P1):** log in, forced password change, expenses with quick add, summary bar, budgets view, offline add and sync.
3. **Release 2 (P2):** app lock, budget editing, master data, daily reminder.
4. **Release 3 (P3):** dashboard, home screen widget, CSV export, and recurring expenses and reports once the backend has them.

## Open questions

- [ ] Where should the backend be hosted so the phone can reach it: a cloud VM, or the home PC through a tunnel?
- [ ] Is token login (recommended) acceptable, or should the app keep cookie sessions and ask for the password every 30 minutes?
- [ ] Is offline entry a must for Release 1, or can the first build be online only?
- [ ] Will anyone other than Krishna use it? If yes, data needs a `user_id` before mobile work starts.
- [ ] Native Kotlin only, or is iOS wanted later (which would favour Flutter)?
- [ ] Play Store listing, or a directly installed APK?
- [ ] Should the app keep the web's rule that future dates are refused, or allow planned expenses?
- [ ] Which dashboard views from the web should come to the phone once that work lands?
