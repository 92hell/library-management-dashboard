# Library Management System

A full-stack library website: members browse the catalogue and request books online, librarians
run the desk, approve requests and track loans, and everyone gets notified when something needs
their attention.

**Stack:** Java 17 · Spring Boot 3.5 (Web, Data JPA, Security, Validation) · Flyway · MariaDB ·
React 18 · Vite · React Router 6 · Bootstrap 5 / reactstrap.

> Originally a job-interview assignment (simple CRUD). Since rewritten to be secure, tested and
> closer to a real library system - see [Roadmap](docs/ROADMAP.md) for what comes next.

## Features

**Customers (members with an online account)**
- Register online, or link an existing library card from the desk (card ID + the email or phone on file)
- Browse and search the catalogue with live availability; request books
- *My loans*: pending requests, books on loan with due dates, overdue warnings, history; cancel a request
- Notifications: request approved / declined (with reason), book due soon, book overdue
- Profile: contact details, library card ID, password change

**Librarians (staff)**
- Dashboard: pending requests, overdue loans, due today, books on loan
- Approve or decline borrow requests; mark books returned
- Desk checkout for walk-in members who have no online account; record past loans
- Manage books, authors, members (each with a library card ID) and librarian accounts
- Notified of every new borrow request

**Rules enforced by the API**
- A book can only be lent to one member at a time (row lock against races)
- At most 3 open requests/loans per customer; no duplicate request for the same book
- Due date defaults to 14 days; dates are validated (no future borrow date, no return before borrow)
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

## Configuration

All settings have defaults in `src/main/resources/application.yml` and can be overridden with
environment variables:

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | `jdbc:mariadb://localhost:3306/library_sandbox`, `library`, empty | Database |
| `LIBRARIAN_USERNAME`, `LIBRARIAN_PASSWORD` | `librarian`, generated | First staff account (created if missing) |
| `DEMO_MODE`, `DEMO_PASSWORD` | `false`, `demo-password` | Sample data + demo logins shown on the login page |
| `LOAN_PERIOD_DAYS`, `LOAN_MAX_OPEN_PER_MEMBER`, `LOAN_DUE_SOON_DAYS` | `14`, `3`, `2` | Lending rules |
| `LOAN_REMINDER_CRON` | `0 0 8 * * *` | Daily due-soon / overdue reminder job (`-` disables) |
| `APP_BASE_URL` | `http://localhost:8080` | Used for links in emails / messages |
| `NOTIFY_EMAIL_ENABLED`, `NOTIFY_EMAIL_FROM` + `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | off | Email notifications over SMTP |
| `NOTIFY_SMS_*`, `NOTIFY_WHATSAPP_*` (`ENABLED`, `PROVIDER`, `SENDER`, `API_URL`, `API_KEY`) | off | SMS / WhatsApp channels - dry run (logged) until a provider is integrated |
| `SESSION_COOKIE_SECURE` | `false` | Set `true` behind HTTPS |
| `LOG_PATH` | `logs` | Log folder |

A database created by the first version is adopted through a Flyway baseline (the original schema
counts as V1; V2+ are applied on top). That path is not exercised by the test suite, so back the
database up before the first start.

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
`grep <request-id> logs/*.log`. Passwords and tokens are never logged; phone numbers are masked.

## Security

- Session cookie login (`HttpOnly`, `SameSite=Lax`), new session id on login, 30-minute timeout
- CSRF protection with the cookie-to-header pattern (`XSRF-TOKEN` → `X-XSRF-TOKEN`), token rotated on login
- Role-based access: customers only reach the catalogue and their own data (`/api/my/**`)
- Passwords hashed with BCrypt; 10+ characters; login throttled after 5 failures per username and address
- Request DTOs with validation (clients cannot set ids, flags or audit fields); RFC 7807 errors without internals
- Content-Security-Policy, `X-Frame-Options: DENY`, `nosniff`, strict referrer policy
- Linking a walk-in card needs the card ID **and** matching email/phone; wrong attempts count towards the lockout

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
```

## Tests

```bash
mvn clean verify
```

```bash
cd frontend && npm test && npm run lint
```

The backend tests boot the whole application against H2 in MariaDB mode with the real migrations
and Hibernate schema validation. [docs/TESTING.md](docs/TESTING.md) lists every test by name -
it is generated from the test code (`python3 scripts/generate-test-catalog.py`), so it doubles as
an auditable specification.
