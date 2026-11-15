# BioPulse — Biomedical Intelligence Dashboard

Academic patient monitoring, historical analytics and recorded simulation. Java 21 / Spring Boot / Maven backend with explicit JDBC, MySQL 8, Flyway, HikariCP, Spring Security and authenticated WebSockets. React frontend implements the ten-screen Framer handoff in `design/framer/` with responsive desktop/phone layouts.

**Research only.** The seed contains synthetic identities, numerical fixtures and illustrative uncalibrated waveforms. Neither rules nor experimental model outputs are clinically validated. There are no treatment recommendations. No public clinical dataset or physical hardware connection is bundled.

## Run with Docker

Install Docker with a working Linux engine. Copy `.env.example` to `.env` and fill `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD` and `DEMO_PASSWORD` with your own strong passwords. Demo password must have at least 12 characters. `.env` is ignored by Git.

```sh
docker compose up --build -d
```

Open **http://localhost:8080**. Compose builds the frontend into the application JAR, migrates MySQL and optionally seeds the database. Database storage persists in the `mysql_data` volume; the database port is not exposed. `COOKIE_SECURE=false` is for local HTTP. Configure HTTPS, `COOKIE_SECURE=true` and an explicit `ALLOWED_ORIGINS` allowlist for a hosted environment.

Demo accounts (all use your runtime `DEMO_PASSWORD`):

| Account | Role | Scope |
| --- | --- | --- |
| `admin@biopulse.local` | ADMIN | All wards, registration, ingestion, simulation, auditing |
| `doctor@biopulse.local` | DOCTOR | Ward B, observations, reports, alerts, rule creation |
| `nurse@biopulse.local` | NURSE | Ward B, observations, reports, alert acknowledgment/resolution |

`SEED_DEMO=true` seeds only a database with no users. Changing the environment password does not change existing hashes. Disable seeding for a non-demo database. Provision real accounts and `user_wards` grants through your administrative process using BCrypt hashes; no public registration endpoint exists.

## Run locally

Prerequisites: **JDK 21**, MySQL **8.0.16+** or **8.4**, Node **22.12+** (24 supported). Maven Wrapper downloads Maven 3.9.11; global Maven is unnecessary. Set `JAVA_HOME` to JDK 21. Create a dedicated UTF-8 database and application user with privileges for Flyway migrations.

The shared migrations create MySQL triggers. On servers with binary logging enabled, the database administrator must enable `log_bin_trust_function_creators=1` for migration, or use an appropriately privileged migration account. The application runtime account should not receive broad server privileges solely for this setting.

PowerShell, in the repository root:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
$env:DB_URL = 'jdbc:mysql://localhost:3306/biomedical_dashboard?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
$env:DB_USER = 'biopulse'
$env:DB_PASSWORD = Read-Host 'MySQL password'
$env:SEED_DEMO = 'true'
$env:DEMO_PASSWORD = Read-Host 'Choose a demo password of at least 12 characters'
./mvnw.cmd spring-boot:run
```

In another terminal:

```powershell
cd frontend
npm ci
npm run dev
```

Open **http://127.0.0.1:5173**. Vite proxies API, WebSocket and `/simulator` traffic to port 8080. Monitoring and Profile link to the bundled simulator at `/simulator/index.html`, sharing login and backend routing. The workstation follows the newest replay for the selected admission; its selector can pin another session or show latest observations across sources. Source provenance distinguishes generated fixtures from imported research data. Both `localhost` and `127.0.0.1` development origins are explicitly allowed by default. Environment variables are loaded by the OS/Spring; `.env` is consumed automatically by Compose only.

For a bundled JAR, `scripts/build.ps1` or `scripts/build.sh` builds the frontend, runs Maven verification, copies frontend output into the JAR, and packages it. Stop a running JAR before rebuilding on Windows. Start with `java -jar target/biopulse-1.0.0.jar` using the same database environment.

### Host BioPulse on this PC for the same Wi-Fi

On Windows, install and start Docker Desktop, connect the PC to a **Private** Wi-Fi network, then run this once from the repository folder in PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-lan.ps1
```

The launcher creates missing local database secrets in the ignored `.env`, asks Windows for elevation once to add a firewall rule restricted to TCP 8080 from `LocalSubnet` on Private networks, builds the existing Docker image, starts MySQL and BioPulse, waits for the API health check, and prints the current Wi-Fi address. On a fresh database only, it also prints the generated synthetic admin login. Keep that password private and change it before sharing access beyond a trusted demo network.

Open the printed `http://PC-WIFI-IP:8080` address on devices connected to the same network. The Java server serves the built React site and its API/WebSocket under the same origin; devices do not need port 5173 or direct database access. MySQL changes stay in the local `biopulse_mysql_data` Docker volume across restarts. Keep this PC, Docker Desktop and the Wi-Fi connection on while people use the site. Start the launcher again after reconnecting to Wi-Fi to print the new address. Stop the containers with `docker compose -p biopulse stop`; this preserves the database.

The first firewall setup requires a Private Windows network profile and an elevation approval. The launcher refuses to open a LAN rule on a Public network. A guest Wi-Fi setting that isolates connected devices must be disabled in the router/access point. This is plain HTTP for a trusted local demo network; do not use it over public Wi-Fi or with real patient data. The database and API remain on the hosting PC; this does not publish BioPulse to the internet.

For frontend-only development, the existing Vite `npm run dev:lan` command remains available; it is separate from this all-in-one LAN host.

## Verify

```powershell
./mvnw.cmd test                 # unit tests
./mvnw.cmd verify               # unit + MySQL Testcontainers integration tests; needs Docker
cd frontend
npm ci
npm run build
npx playwright install chromium
# Start a seeded backend, then supply its DEMO_PASSWORD in this terminal:
npm test                       # real browser tests, desktop + phone Chromium
```

`ApplicationIT` starts MySQL 8.4 with Testcontainers by default. Without Docker, supply `TEST_DB_URL`, `TEST_DB_USER`, `TEST_DB_PASSWORD` for a **dedicated test database**. This runs the same tests against real MySQL, including migrations and JDBC transactions. Never point those variables at a valuable database: the suite seeds and writes test records. A fresh database gives reproducible fixture IDs. Testcontainers remains the default CI path; a missing Docker engine fails verification instead of silently skipping tests.

GitHub Actions runs Java 21 compilation, unit tests, Testcontainers integration tests, frontend production build and browser workflows on every push/PR. [Verification evidence and limitations](docs/VERIFICATION.md) records what was actually executed.

## API and streaming

[OpenAPI contract](docs/openapi.yaml), also served at `/openapi.yaml`, and [example HTTP requests](docs/requests.http). The OpenAPI document uses JSON syntax, which is valid YAML. All functional routes are under `/api/v1`; protected routes require session authentication.

1. `GET /api/v1/session` returns a CSRF token and sets a CSRF cookie.
2. `POST /api/v1/session` with `username`, `password` and `X-XSRF-TOKEN` authenticates and rotates the session ID. Preserve cookies.
3. Include `X-XSRF-TOKEN` on subsequent writes. `POST /api/v1/logout` invalidates the session.

History and alert responses contain `{items,page,size,hasNext}`. Ranges use ISO 8601 UTC timestamps with **inclusive start / exclusive end**, limited to 31 days per request. Historical ordering is `(observedAt,id)`; alert ordering is `(triggeredAt DESC,id DESC)`. Server timestamps use MySQL microsecond precision; numerical ingestion normalizes nanoseconds down to microseconds before evaluation.

Ingest observations through the ADMIN-only internal batch endpoint. Register a source, optional recording, active admission and assigned capable device first. Dataset/simulation observations require a recording; source IDs and recording ranges must agree. Missing/invalid observations carry `value:null` and an explicit quality flag. Valid finite recorded numbers are stored independently of analytics. Units are canonical: `bpm`, `%`, `breaths/min`, `mmHg`, `Cel`, `mV`, `a.u.` as defined in `MeasurementCode`. Unsupported channels are rejected; absent channels remain missing.

Connect to `/ws/monitoring?admissionId=1` with the authenticated session cookie and an allowed Origin. One admission per connection. Messages carry `eventId`, `type`, `admissionId`, timestamp, metric/value/unit where relevant, source type, quality and payload. Message types: `READING`, `WAVEFORM`, `ALERT`, `DEVICE_STATUS`, `SIMULATION`. Waveforms use compact chunks, at most 4096 samples per ingestion. No sample-by-sample 125 Hz broadcast occurs. Socket buffers are bounded at 64 KiB with a 3-second send limit; the server caps connections at 256. A slow client is disconnected and must refresh REST state after reconnecting. The frontend backs off to 30 seconds and coalesces refreshes. Login invalidation/expiry and ward revocation close subscriptions before more data is sent.

## Analytics and alerts

Mean, median, min/max and population standard deviation use usable recorded values (`GOOD`/`FAIR`). At coincident timestamps, the last stored observation controls the analysis and temporal coverage; every original row remains retrievable. Trend slope is least-squares per second. Rolling means use a real time window, not an assumed sample count. Missing/invalid/poor/unknown values are excluded from numerical statistics.

Coverage is the union of usable observation intervals extending forward by the **explicit expected cadence**, capped by the next observation and selected range. Gaps are returned explicitly, including leading/trailing gaps. Threshold deviation duration uses the same bounded hold policy. This is an availability estimate for observations, not a claim of continuous waveform coverage. Reports currently declare a 10-second cadence; analytics callers can configure it. Freshness expires after twice that cadence. Spot observations and ECG/PPG/respiration coverage must not be conflated.

Alert rules belong to admissions. Bounds, persistence, maximum evidence gap and severity are configured individually; seed thresholds are illustrative fixtures. Missing/poor-quality observations or excessive gaps interrupt persistence. A rule opens at most one active alert, enforced by a database uniqueness constraint. State changes require the current version; conflicting or terminal transitions return 409. Acknowledgment does not resolve an alert. Resolution is an explicit staff workflow action, not a treatment recommendation. Historic or out-of-order ingest may evaluate historic events at their recorded times.

The experimental `AnalysisModel` strategy implements versioned baseline-relative z-scores, with features, inference timestamp and provenance. Fewer than five baseline values and zero-variance baselines return explicit unavailable states. Every result is `RESEARCH_ONLY`. NEWS2 and trained ML models are intentionally absent: this project has no complete validated input set or training dataset.

## Simulation, fixtures and reports

The opt-in seed mirrors the handoff: twelve identities, ten online/two offline devices, two critical/three warning pre-authored **synthetic workflow events**, and detailed measurements only for Avery R. Other patients do not acquire fabricated measurement histories. Fixture events have `SEEDED_SYNTHETIC_EVENT` timeline entries and are not claimed to have been generated from the sparse cohort data. The waveform fixture is eight minutes with a 15-second omitted span; numerical observations have their own cadence mask. The recording also contains a terminal spot snapshot, so metadata includes one extra second to retain half-open API semantics. Historical data is visibly labeled historical/stale.

Replay reads stored source rows, writes a separate simulation source/recording that references its origin, and preserves observation times. Its transactional cursor advances only after successful scalar and waveform ingestion. A monotonic clock advances the selected source interval at 0.5, 1, 2, 5 or 10 times real time; each tick processes at most ten source seconds before catching up on later ticks. This is an educational scheduler, not an acquisition-grade real-time clock. Recorded devices must be assigned to the target admission. A failed tick retains the cursor and logs the error; pause/stop, repair the assignment or data problem, then resume. There is no synthetic fallback when input is absent.

To import an optional [BIDMC CSV recording](https://physionet.org/content/bidmc/1.0.0/), put the published files in a directory outside the repository and set `BIOPULSE_BIDMC_DATASET_ROOT` to its absolute path on the server. An ADMIN calls `POST /api/v1/datasets/bidmc/import` with a `bidmc_##` recording ID, ECG lead (`II`, `V` or `AVR`), active admission ID, and assigned device IDs keyed by the available measurement codes. The browser never supplies a file path. The import is transactional, preserves source-relative timing under a documented synthetic timestamp anchor, and returns 409 for a repeated recording ID. Historical import does not create live alerts; replay emits them through the normal ingestion path. The [operator console](web-simulator/README.md) exposes this workflow with per-channel device mapping and live waveform/vital inspection. See the [simulator contract](docs/SIMULATOR_CONTRACT.md) for channel and provenance limits.

Reports are stored immutable CSV snapshots for one admission or one authorized ward. Downloads recheck current authorization. Exports include the requested window, declared aggregation policy, numerical statistics, availability, explicit missing ranges and source IDs. Native PDF generation and clinical signing are not implemented.

## Project map

| Path | Responsibility |
| --- | --- |
| `src/main/java/org/biopulse/domain` | Immutable typed models and device capabilities |
| `dto`, `controller`, `service` | Validated contracts, HTTP routes, use-case transactions |
| `dao`, `jdbc` | DAO interfaces, PreparedStatement/ResultSet SQL implementations |
| `analytics`, `alerts` | Statistical strategies and deterministic persistence rules |
| `simulation`, `reporting` | Recorded sources/import interface, scheduler and CSV strategy |
| `security`, `websocket`, `config`, `exception` | Access scopes, transport, wiring and errors |
| `database/migrations` | Shared MySQL schema migrations, packaged into Flyway's classpath location |
| `frontend/src` | Responsive Framer-based application and REST/WebSocket client |
| `web-simulator` | Static ADMIN console for BIDMC import, scenario replay, server-backed session history and live waveform inspection |
| `design/framer` | Preserved source reference and original render gallery |

[Architecture and OOP explanation](docs/ARCHITECTURE.md). `AGENTS.md` carries the user's project-level instruction to verify, commit and push completed tasks. Secrets, local tools, databases, browser traces and generated build products are excluded from Git.
