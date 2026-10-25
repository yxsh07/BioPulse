# Verification evidence

Executed on 25 October 2026.

| Check | Result |
| --- | --- |
| Java 21 compilation and executable JAR packaging | Passed |
| JUnit/Mockito unit tests | 25 passed; one published-dataset test skipped because BIDMC files were not supplied |
| JDBC + API integration tests against isolated MySQL 8.4 (Testcontainers) | 14 passed; zero skipped, all six shared Flyway migrations enabled |
| GitHub Actions MySQL 8.4 Testcontainers integration | Current push in progress; prior run passed |
| Local Docker Engine 29 + Testcontainers 1.21.4 MySQL 8.4 integration | 14 passed; zero skipped; 25 unit tests (one optional dataset test skipped) |
| Frontend production build | Passed |
| Desktop and phone Chromium workflows against the real API | 4 passed |
| OpenAPI 3.0 contract validation | Passed with Swagger Parser |
| Desktop/phone screenshots | Reviewed; waveform display window and mobile monitoring access corrected |
| npm dependency audit | Zero reported vulnerabilities at installation |

CI evidence for commit `2172e9b`: https://github.com/yxsh07/BioPulse/actions/runs/40204125487 — successful. The run includes the default Docker/Testcontainers path, frontend build and all four browser scenarios.

Local Docker Desktop 29.0.1 is reachable. Testcontainers 1.21.3 pinned Docker API 1.32, which Engine 29 rejects because its minimum supported API is 1.44; Testcontainers 1.21.4 negotiates 1.44 and falls back to 1.32 for older engines. The default Testcontainers MySQL 8.4 path now passes locally. The separate MySQL 8.0.46 process on port 3307 remains available for `TEST_DB_*` runs but is no longer required. The existing MySQL Windows service and its data were left alone.

The browser scenarios cover secure login/logout, patient selection, honest missing measurements, waveform rendering, historical analytics, WebSocket connection, replay create/start/pause/stop, alert timeline and acknowledgment, report generation and CSV download, directory search and local theme preferences. Unit and API tests additionally cover numerical/missing-data behavior, fractional threshold gaps, duplicate transaction rollback, version conflicts, ward and role restrictions, CSRF, source provenance and session-invalidated sockets.

The combined Java verification above used the committed shared DAO layer and six Flyway migrations. The ADMIN `/audit` route now reads the canonical `audit_logs` table through `AuditDao.page`, and an API regression asserts that new audit writes appear there with reconstructed `entityType:entityId` resources. Device listing now applies ward authorization in SQL through `DeviceDao.allAuthorized`. The BIDMC import regression tested a short generated CSV fixture through the actual MySQL DAOs; the published full dataset was unavailable locally, so its optional count check skipped. CI success cited above predates these latest shared-schema commits; the current push is being verified. Physical device transport, native Android/JavaFX clients, clinical validation, NEWS2, trained ML, PDF signing and hosted deployment are not claimed.
