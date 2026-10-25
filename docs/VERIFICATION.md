# Verification evidence

Executed on 25 October 2026.

| Check | Result |
| --- | --- |
| Java 21 compilation and executable JAR packaging | Passed |
| JUnit/Mockito unit tests | 22 passed; one published-dataset test skipped because BIDMC files were not supplied |
| JDBC + API integration tests against isolated MySQL 8.0.46 | 12 passed; zero skipped, all six shared Flyway migrations enabled |
| GitHub Actions MySQL 8.4 Testcontainers integration | Passed |
| Frontend production build | Passed |
| Desktop and phone Chromium workflows against the real API | 4 passed |
| OpenAPI 3.0 contract validation | Passed with Swagger Parser |
| Desktop/phone screenshots | Reviewed; waveform display window and mobile monitoring access corrected |
| npm dependency audit | Zero reported vulnerabilities at installation |

CI evidence for commit `2172e9b`: https://github.com/yxsh07/BioPulse/actions/runs/40204125487 — successful. The run includes the default Docker/Testcontainers path, frontend build and all four browser scenarios.

Local Docker's Linux engine could not be reached. Attempting to start the Windows Docker service was rejected by automatic approval review, with no reason beyond a policy block. Local testing therefore used a separate MySQL process and dedicated databases on port 3307. The existing MySQL Windows service and its data were left alone. Testcontainers was verified on GitHub's Linux runner.

The browser scenarios cover secure login/logout, patient selection, honest missing measurements, waveform rendering, historical analytics, WebSocket connection, replay create/start/pause/stop, alert timeline and acknowledgment, report generation and CSV download, directory search and local theme preferences. Unit and API tests additionally cover numerical/missing-data behavior, fractional threshold gaps, duplicate transaction rollback, version conflicts, ward and role restrictions, CSRF, source provenance and session-invalidated sockets.

The combined Java verification above used the committed shared DAO layer and six Flyway migrations. The BIDMC import regression tested a short generated CSV fixture through the actual MySQL DAOs; the published full dataset was unavailable locally, so its optional count check skipped. The ADMIN audit list still reads the legacy `audit_events` table while new audit writes go to `audit_logs`; the persistence owner has a request in [the shared contract](SHARED_BACKEND_CONTRACT.md) for a paginated `AuditDao.page` read. The local combined run used `-Dbiopulse.build-directory=D:/BioPulse/.tools/backend-target` to avoid overlapping agents' Maven output. CI success cited above predates these latest shared-schema commits; a new CI run will verify the current push. Physical device transport, native Android/JavaFX clients, clinical validation, NEWS2, trained ML, PDF signing and hosted deployment are not claimed.
