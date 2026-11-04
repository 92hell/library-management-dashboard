# Library Management System

A full-stack library website: members browse the catalogue and request books online, librarians
run the desk, approve requests and track loans, and everyone gets notified when something needs
their attention.

**Live demo:** [92hell.github.io/library-management-dashboard](https://92hell.github.io/library-management-dashboard/)
(no sign-up: the login page has a librarian and a member demo button). It runs on a free server
that sleeps when idle; that link wakes it up and opens the app as soon as it answers, usually
within a minute.

**Stack:** Java 17 · Spring Boot 3.5 (Web, Data JPA, Security, Validation) · Flyway · MariaDB ·
React 18 · Vite · React Router 6 · Bootstrap 5 / reactstrap.

> Originally a job-interview assignment (simple CRUD). Since rewritten to be secure, tested and
> closer to a real library system - see [Roadmap](docs/ROADMAP.md) for what comes next.

## Features

**Customers (members with an online account)**
- Register online, or link an existing library card from the desk (card ID + the email or phone on file)
- Browse and search the catalogue (title, author, publisher, category or ISBN) with live
  availability; filter by category, author or "on the shelf now" and sort by title, publication
  date or newest additions (filters live in the address, so a filtered list can be shared); each book has its own page with cover, description, shelf and queue; request books
- *My loans*: pending requests, place in the queue, holds ready for pickup, books on loan with due
  dates, overdue warnings, history; cancel a request or hold; renew a loan
- Late fees: what an overdue book costs so far, the balance owed and the fee history
- Notifications: hold ready for pickup, joined the waiting list, hold expired, request declined
  (with reason), book due soon, book overdue, late fee charged
- Profile: contact details, library card ID, password change; print their own library card
- Reminded a month before their library card expires (in the app, and by email/SMS where set up),
  once per expiry date; again before the new date after a renewal

**Librarians (staff)**
- Dashboard: pending requests, overdue loans, due today, books on loan
- Approve or decline borrow requests; approval sets a copy aside ("ready for pickup") or puts the
  member on the waiting list when every copy is out; hand the copy over at the desk
- Holds queue: a returned or newly added copy goes to the next member in line, first come first
  served; uncollected holds expire and pass to the next member
- Renew loans; mark books returned
- Reports for any date range (up to a year): books lent and returned, late-return rate, active
  and new members, fines charged and collected, most borrowed titles, most active members;
  charts of books lent per week and returns per month (on time / late), readable by keyboard and
  with the same numbers as a table;
  CSV downloads (spreadsheet-safe: formulas in data are neutralised; every export is audited)
- Fines desk: late fees charged on return; record a payment (all of it or part of it, as often as
  needed) or waive what is left with a reason (audited); print the signed receipt after a payment
- Desk checkout for walk-in members who have no online account; record past loans
- Physical copies with barcodes: a title can have several copies ("2 of 3 on shelf"); lend a
  scanned copy, return by barcode, look up who has a copy, add or withdraw copies
- Add a book by ISBN: look it up at Open Library to fill in title, author, publisher, year and
  categories; covers come from Open Library (a drawn spine when there is none)
- Manage books, authors, members (each with a library card ID) and librarian accounts
- Library cards: renew a card (valid 12 months), replace a lost or damaged one (new ID, the old one
  kept in the card history and still finds the member), and merge a duplicate member record into
  the right one (loans, fines, receipts and the online account move over); print a member's card at
  its real size (85.6 × 54 mm) with the card ID as a Code 128 barcode for the desk scanner
- Notified of every new borrow request

**For everyone**
- English and Bahasa Indonesia: follows the browser language at first, switch any time (EN / ID in
  the menu and on the sign-in page; remembered). Dates and amounts follow the language, and so do
  messages from the server (errors, validation, notifications): the app sends its language as
  `Accept-Language`; Indonesian server texts are in `src/main/resources/i18n/messages_id.json` and
  `ValidationMessages_id.properties`, and a test fails when one is missing. Emails and the history
  lines on signed receipts stay in English.
- Works on phones: lists turn into cards with their buttons in reach; the browser tests check every
  page at phone size (no sideways scrolling, nothing off screen)
- Accessibility: WCAG 2.1 AA checked automatically (axe) on every main page in the browser tests;
  skip link, focus moves to the page on navigation, form errors and hints read with their field

**Loan receipts (printable evidence)**
- Every status change of a loan is recorded (who, when, what): requested, approved, lent,
  collected, renewed, returned, late fee charged / paid / waived, corrections; every payment is
  listed on the receipt with what is still due
- A receipt is a numbered snapshot (R-2026-000123) of the loan and that history, printable from
  the browser or downloadable as a bilingual PDF (English / Bahasa Indonesia) with the library
  stamp and a QR code. Librarians issue them for any loan, members for their own.
- Each receipt is signed (Ed25519) over its exact text; the QR code opens a public page that
  re-checks the signature and shows the library's record (member name masked). The public key is
  at `/api/receipts/public-key`, so anyone can verify a receipt without trusting the app.
- This proves the receipt came from the library and was not altered. It is *not* a certified
  electronic signature under UU ITE: that needs a Kominfo-registered provider (PSrE, e.g. BSrE or
  Privy), which can be added as another `ReceiptSigner`.

**Rules enforced by the API**
- A copy can only be lent to one member at a time; a title with several copies can be lent to
  several members (row lock against races)
- At most 3 open requests/loans per customer; no duplicate request for the same book
- Due date defaults to 14 days; dates are validated (no future borrow date, no return before borrow)
- A copy coming back skips members who cannot collect it (unpaid fines over the limit, inactive
  or expired card); they keep their place and are served as soon as they pay or renew
- Holds are kept for 3 days; a loan can be renewed once, not when overdue and not while someone is
  waiting for the book
- Late returns cost IDR 1,000 a day, at most IDR 30,000 per loan (also for past loans recorded at
  the desk as returned late); a member owing IDR 20,000 or more
  cannot request, borrow or collect a hold until they pay
- An expired card cannot request, borrow, collect a hold or renew a loan until it is renewed at the
  desk (renewing early adds 12 months to the current expiry); members see the expiry on their
  profile and are warned a month ahead. Cards that existed before this feature run for a year from
  the upgrade. A replaced or merged card ID is never issued again and cannot be used to register
- Soft deletes; authors with books, books on loan and members with open loans cannot be deleted
- Deleting a member disables their online account

## Run it

**Quickest - no database to install** (H2 file database in `./data`, demo data and demo logins):

```bash
cd frontend && npm ci && cd ..
```

```bash
mvn -Pfullstack package
```

```bash
DEMO_MODE=true java -jar target/library-dashboard-1.1.0.jar --spring.profiles.active=h2
```

Open http://localhost:8080 and use **Try as customer** / **Try as librarian** on the login page.

**With MariaDB via Docker:**

```bash
docker compose up --build
```

**Development** (hot reload; Vite proxies `/api` to Spring on :8080):

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=h2 -Dspring-boot.run.arguments=--library.demo.enabled=true
```

```bash
cd frontend && npm run dev
```

Open http://localhost:5173.

On first start the app creates the librarian account `librarian`. Set `LIBRARIAN_PASSWORD`, or
read the generated password from the log.

## Deploy on Render

`render.yaml` describes a public demo on Render's free plan:

1. In the Render dashboard: **New → Blueprint**, connect GitHub and pick this repository.
2. Render shows the `helmi-library-demo` web service; click **Apply**. The first build takes a few minutes.
3. Open the service URL (`https://<name>.onrender.com`) and use the demo buttons on the login page.
   The owner login is `librarian` with the generated `LIBRARIAN_PASSWORD` from the service's
   **Environment** tab.

Free services sleep after about 15 minutes without visitors and take about a minute to wake up;
the demo data resets each time. Every push to `main` redeploys automatically.

**Wake-up page.** A sleeping app cannot show anything while it starts, so the link to share is a
small static page on GitHub Pages (`wake/`, which never sleeps): it asks the app for its icon every
few seconds and opens the app once the icon loads (only the running app serves it, so Render's own
"starting" page does not count). It shows how long it has waited, follows the browser language
(English / Bahasa Indonesia) and offers a direct link after three minutes. To publish it once:
**Settings → Pages → Source: GitHub Actions**; the `Wake-up page` workflow deploys it on every
change to `wake/`. Set `APP_URL` in `wake/wake.js` when deploying the app elsewhere.

## Configuration

All settings have defaults in `src/main/resources/application.yml` and can be overridden with
environment variables:

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | `jdbc:mariadb://localhost:3306/library_sandbox`, `library`, empty | Database |
| `LIBRARIAN_USERNAME`, `LIBRARIAN_PASSWORD` | `librarian`, generated | Owner account: created if missing and re-enabled on every start; when `LIBRARIAN_PASSWORD` is set its password is reset to it (recovery). Without it a password is generated and printed to the console only. **Always set it when hosting.** |
| `DEMO_MODE`, `DEMO_PASSWORD`, `DEMO_RESET_CRON` | `false`, `demo-password`, `0 0 3 * * *` | Public demo - see [Demo mode](#demo-mode) |
| `LOAN_PERIOD_DAYS`, `LOAN_MAX_OPEN_PER_MEMBER`, `LOAN_DUE_SOON_DAYS` | `14`, `3`, `2` | Lending rules |
| `LOAN_PICKUP_DAYS`, `LOAN_MAX_RENEWALS` | `3`, `1` | How long a hold waits at the desk; renewals per loan |
| `BOOK_COVERS`, `ISBN_LOOKUP_URL`, `ISBN_LOOKUP_TIMEOUT` | `true`, `https://openlibrary.org`, `5s` | Open Library covers and ISBN lookup (blank URL turns the lookup off) |
| `VITE_PUBLIC_URL` (frontend build) | `https://helmi-library-demo.onrender.com` (`frontend/.env.production`) | Public address used in link previews (WhatsApp, Slack…); set it when deploying elsewhere. The preview image is `frontend/public/og-image.png`, rendered from `scripts/og-image/`; the site icons are rendered from `frontend/public/favicon.svg` by `scripts/icons/render.mjs` |
| `LIBRARY_NAME`, `PUBLIC_URL` | `Community Library`, request address | Name on receipts and their stamp; base of the QR verification link |
| `RECEIPT_PRIVATE_KEY`, `RECEIPT_PUBLIC_KEY` | generated, kept in the database | Ed25519 receipt signing key (base64 PKCS#8 / X.509); set both in production to keep the key out of the database |
| `library.reports.max-csv-rows` | `20000` | Largest CSV export; a bigger range asks for a shorter one |
| `FINE_PER_DAY`, `FINE_MAX_PER_LOAN`, `FINE_BLOCK_AT`, `FINE_CURRENCY` | `1000`, `30000`, `20000`, `IDR` | Late fees (whole currency units; `FINE_BLOCK_AT=0` never blocks) |
| `MEMBER_CARD_VALIDITY_MONTHS` | `12` | How long a new or renewed library card is valid (`0` = cards do not expire) |
| `MEMBER_CARD_REMINDER_DAYS`, `MEMBER_CARD_REMINDER_CRON` | `30`, `0 5 8 * * *` | Days before a card expires that the member is reminded (`0` = no reminders), and when the daily reminder job runs |
| `LOAN_REMINDER_CRON` | `0 0 8 * * *` | Daily job: expire uncollected holds, due-soon / overdue reminders (`-` disables) |
| `APP_BASE_URL` | `http://localhost:8080` | Used for links in emails / messages |
| `NOTIFY_EMAIL_ENABLED`, `NOTIFY_EMAIL_FROM` + `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | off | Email notifications over SMTP |
| `NOTIFY_SMS_*`, `NOTIFY_WHATSAPP_*` (`ENABLED`, `PROVIDER`, `SENDER`, `API_URL`, `API_KEY`) | off | SMS / WhatsApp channels - dry run (logged) until a provider is integrated |
| `SESSION_COOKIE_SECURE` | `true` | HTTPS-only session cookie (browsers accept it on `http://localhost`); set `false` only for plain-HTTP testing on another host |
| `EDGE_PROXY_RANGES` | Cloudflare's published ranges | CDN servers in front of the host (Render uses Cloudflare): requests relayed by them take the visitor address from `CF-Connecting-IP` |
| `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` | private address ranges | Which proxy addresses are trusted for `X-Forwarded-For` / `-Proto`; only needed if your proxy connects from a public address |
| `LOG_PATH` | `logs` | Log folder |

A database created by the first version is adopted through a Flyway baseline (the original schema
counts as V1; V2+ are applied on top). That path is not exercised by the test suite, so back the
database up before the first start.

## Demo mode

`DEMO_MODE=true` turns the app into a public demo that is safe to publish:

- sample data, plus a demo customer and a demo librarian whose logins are shown on the login page
- the demo accounts can run the desk (catalogue, members, loans, requests) but cannot administer
  accounts or change their own password or profile
- registration is off, so no real personal data is collected; a banner asks visitors not to enter any
- everything except the owner librarian is reset to the sample data at start-up and every night

Never run demo mode with real users.

## Notifications

Every notification is stored for the in-app inbox (bell icon; polled every minute). It is also
handed to the external channels **after** the database transaction commits, on a background
thread, so a rolled-back action never sends a message and a slow mail server never slows a page.

| Channel | Status | Reaches |
|---|---|---|
| In-app | always on | every account |
| Email (SMTP) | implemented, off by default | the email on the member's card (or the librarian's account) |
| SMS / WhatsApp | configurable, **dry run** (logs what it would send, number masked) | the phone on the member's card |

To integrate an SMS or WhatsApp provider, override `deliver()` in `SmsChannel` / `WhatsAppChannel`
(`notification/channel`). Members without an online account only see their loans at the desk.

## Logs

`logs/` (configurable with `LOG_PATH`):

| File | Contents |
|---|---|
| `library.log` | everything at INFO+: one access-log line per API call, business events, warnings |
| `error.log` | WARN and ERROR only - start here when something breaks |
| `audit.log` | who did what: logins (success, failure, lockout), registrations, approvals, returns, account changes |
| `archive/` | rotated files (daily or 10 MB, gzipped; 30 / 90 / 365 days retention) |

Every line carries `[requestId] [user]`. The request id is returned in the `X-Request-Id` response
header (and reused from an incoming one), so a reported problem can be traced with
`grep <request-id> logs/*.log`. Passwords and tokens are never written to the log files (a generated
first-start password goes to the console only); phone numbers are masked; line breaks in messages
are replaced so user input cannot forge log lines.

## Security

- Session cookie login (`HttpOnly`, `Secure`, `SameSite=Lax`), new session id on login, 30-minute timeout
- Every request re-checks the account: disabling it or changing its password ends its other sessions at once
- Behind a hosting proxy the real visitor address and HTTPS are taken from `X-Forwarded-*` - only
  from trusted (private) proxy addresses - so throttling works per visitor and HSTS is sent
- CSRF protection with the cookie-to-header pattern (`XSRF-TOKEN` → `X-XSRF-TOKEN`), token rotated on login
- Role-based access: customers only reach the catalogue and their own data (`/api/my/**`)
- Passwords hashed with BCrypt; 10+ characters; login throttled after 5 failures per username and
  address, and after 20 failures per address across usernames (bounded in memory)
- Request DTOs with validation (clients cannot set ids, flags or audit fields); RFC 7807 errors without internals
- Content-Security-Policy, `X-Frame-Options: DENY`, `nosniff`, strict referrer policy
- Linking a walk-in card needs the card ID **and** matching email/phone
- Registration does not reveal who is a member: card mismatches and "you already have a card" get one
  generic answer, every refused attempt (taken username included) counts towards the address's
  lockout, and at most 3 accounts can be created per address per hour (`library.security.max-signups-per-hour`)
- Lending is consistent under concurrent requests: every change to a loan, its copy or its fee takes
  the book's (or fine's) row lock first and re-reads under it, at READ COMMITTED isolation (tested
  with two threads against real MariaDB)
- Receipts: a receipt opens only for the member named on it (or librarians); the signed text is bound
  to its own row; with a configured signing key no other key is trusted; demo receipts are marked
  and watermarked "DEMO - NOT A REAL RECEIPT"
- CSV exports quote every text cell and neutralise formulas (also for `;`-separated Excel), and are
  capped at 20,000 rows; ISBN lookups are cached and limited to 2 at a time
- Privacy note: book covers load from Open Library / archive.org, which then see the visitor's IP
  address and the ISBN; set `BOOK_COVERS=false` to turn covers off

## Project layout

```
src/main/java/com/helmifr/library/
  author/  book/  member/  loan/      entities, repositories, services, controllers per feature
  account/                            user accounts, registration, profile, staff admin
  notification/  notification/channel/  in-app inbox, email / SMS / WhatsApp channels
  dashboard/  demo/                   librarian summary, demo data
  security/                           Spring Security config, login, CSRF, throttling
  common/                             errors, paging, request logging, audit, helpers
src/main/resources/db/migration/      Flyway migrations (V1 = original schema)
frontend/src/                         pages/, components/, api.js (fetch + CSRF), auth.jsx
frontend/src/i18n/                    language switch; Indonesian texts in id/*.js (English text is the key)
e2e/tests/                            Playwright journeys and WCAG scans (axe)
```

## Tests

```bash
mvn clean verify
```

```bash
cd frontend && npm test && npm run lint
```

The same backend tests against a real MariaDB 11.4 in Docker (Testcontainers starts and removes
the containers; needs Docker):

```bash
mvn -Pmariadb clean verify
```

End-to-end journeys in a real browser (Chromium), against the packaged app on a fresh in-memory
database (the test runner starts it):

```bash
mvn -Pfullstack package -DskipTests
cd e2e && npm ci && npx playwright install chromium && npm test
```

The wake-up page (no dependencies, Node 22):

```bash
node --test wake/*.test.mjs
```

The backend tests boot the whole application against H2 in MariaDB mode with the real migrations
and Hibernate schema validation. [docs/TESTING.md](docs/TESTING.md) lists every test by name -
it is generated from the test code (`python3 scripts/generate-test-catalog.py`), so it doubles as
an auditable specification.
