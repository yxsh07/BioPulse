# Verification evidence

## Dashboard and simulator connection correction (26 October 2026)

The earlier simulator-only checks did not establish that the React workstation followed its output. Browser inspection reproduced fixture HR 108 and SpO2 94, mixed recording sources, and empty fixed-date waveform windows while BIDMC replay existed. This correction scopes replay reads to the output recording and recorded cursor, replaces fixed fixture ranges with recording intervals, and bundles the console on the dashboard origin.

| Current check | Result |
| --- | --- |
| Java 21 Maven verify, Docker Engine 29 / MySQL 8.4 Testcontainers | 28 unit tests and 16 integration tests passed; zero failures, errors or skips; published BIDMC CSV directory supplied |
| Frontend production build and OpenAPI validation | Passed; both OpenAPI copies identical |
| Combined real-backend Playwright suite | 12 passed, desktop and phone Chromium, one worker |
| Workstation and monitoring | Older replay replaces newer fixture cards; absent channels remain empty; waveform samples respect the selected interval; switching admission clears the previous scope |
| Simulator integration | Shared-origin authentication; create/start/pause/resume/stop; external session discovery; server-backed reload recovery |
| Trends and reports | Recording-derived intervals; filtered analytics and measurement history; generated and downloaded CSV identifies the selected output recording |
| Other application workflows | Patient search, ward filters, patient registration, duplicate registration errors, occupied-bed rejection, device registration/assignment, duplicate assignment rejection, invalid rule rejection, event timeline/acknowledgment, audit entries, theme preference and logout |
| Published BIDMC cross-application check | Original HR 94 / SpO2 97; abnormal output 104 / 87; source rows unchanged; normal replay restored; ECG/PPG/respiration visible; zero page errors |
| Packaging | Bundled dashboard index and simulator script byte-match their current build/source |

The published-data check left local session 67 paused against imported `bidmc_01`, ready to resume. These IDs describe the local verification database, not portable configuration. Screenshots are ignored local artifacts: `.tools/published-connected-dashboard.png` and `.tools/published-connected-simulator.png`. Automated regressions use labeled generated historical fixtures and stop their own sessions. The seeded patient directory remains a generated research registry; imported BIDMC subjects are not represented as real hospital admissions.

Intermediate checks exposed a Windows lock on a running build JAR, simultaneous browser runs sharing trace output, and an initial browser request made during backend startup. The runtime now runs from a separate ignored directory, browser verification runs serially, and browser health probes use bounded waits. The final checks above passed after those corrections. Testcontainers still emits the previously documented scheduler teardown connection messages after a test context closes; the final test results contain no failures.

Playwright was used with the user's approval because ARTEMIS was unavailable and `adb devices` listed no connected device. Phone results are Chromium viewport checks, not physical Android verification. `DELAYED_TELEMETRY` remains the documented metadata-only scenario.

## Earlier simulator verification

Executed on 26 October 2026.

| Check | Result |
| --- | --- |
| Java 21 compilation and executable JAR packaging | Passed |
| JUnit/Mockito unit tests | 33 passed, zero skipped, when `BIDMC_CSV_DIR` pointed at the complete published `bidmc_01` CSV set |
| JDBC + API integration tests against isolated MySQL 8.4 (Testcontainers) | 15 passed; zero skipped, all seven shared Flyway migrations enabled |
| Local Docker Engine 29 + Testcontainers 1.21.4 MySQL 8.4 integration | 15 passed; zero skipped |
| Full published BIDMC parser counts | Passed; 60,001 ECG samples, 481 × 4 numeric observations, 480.008 s duration |
| Full published BIDMC import/replay end-to-end | Passed; 1,443 waveform chunks, 1,924 numeric observations, 480.0 s, subject `s01182` |
| Scenario transformation and server-backed history | Passed; `ABNORMAL_DEMO` output changed values/quality, session survived reload |
| Frontend production build | Passed |
| OpenAPI 3.0 contract validation | Passed with Swagger Parser |
| JavaScript syntax (`node --check web-simulator/simulator.js`) | Passed |
| Simulator browser console errors and failed requests | None in the final scenario workflow |
| npm dependency audit | Zero reported vulnerabilities at installation |

The earlier CI evidence for commit `61a6176`: https://github.com/yxsh07/BioPulse/actions/runs/40220130647 was successful. That run predates the scenario/session-history changes and is not evidence for the current working tree. Local verification above is the current evidence.

Local Docker Desktop 29.0.1 is reachable. Testcontainers 1.21.3 pinned Docker API 1.32, which Engine 29 rejects because its minimum supported API is 1.44; Testcontainers 1.21.4 negotiates 1.44 and falls back to 1.32 for older engines. The default Testcontainers MySQL 8.4 path passes locally. The separate MySQL 8.0.46 process on port 3307 was used for the simulator end-to-end run and is not required for standard verification. Testcontainers teardown still logs transient scheduler connection errors after a context closes; they occur after the test results and did not fail any test.

The four earlier browser workflows cover secure login/logout, patient selection, honest missing measurements, waveform rendering, historical analytics, WebSocket connection, replay create/start/pause/stop, alert timeline and acknowledgment, report generation and CSV download, directory search and local theme preferences. Unit and API tests additionally cover numerical/missing-data behavior, fractional threshold gaps, duplicate transaction rollback, version conflicts, ward and role restrictions, CSRF, source provenance, scenario validation, session listing and session-invalidated sockets.

## Simulator console end-to-end (26 October 2026)

A final headless-Chromium run used the complete published BIDMC `bidmc_01` CSV set under ignored `.tools/bidmc-test/`, a local MySQL 8.0 demo database, and the backend built from this working tree. The ADMIN import route mapped all seven canonical channels to an assigned `MULTI` device.

| Check | Result |
| --- | --- |
| Published file download and parser count | Passed; 2,678,552-byte Signals CSV, 60,001 samples per waveform channel |
| BIDMC import through `POST /api/v1/datasets/bidmc/import` | Passed; 1,443 waveform chunks and 1,924 numeric observations, recording selected automatically |
| Source provenance | Passed; source recording `bidmc_01` remains `ORIGINAL_RESEARCH`; subject pseudonym `s01182` |
| Scenario session creation | Passed; `ABNORMAL_DEMO`, range `[0,60)` source seconds, magnitude `10`, speed `1×` |
| Scenario replay output | Passed; source HR 94 became HR 104 with `POOR` quality; SPO2 97 became 87; source rows were unchanged |
| Synthetic provenance | Passed; output source type `SIMULATION`, class `SYNTHETIC_CLINICAL`, replay recording references the original |
| Server-backed session history | Passed; session appeared in `GET /api/v1/simulation-sessions`, survived a browser reload, and reopened with its scenario metadata |
| Browser console errors and failed requests | None |
| Screenshot | `.tools/simulator-scenario-e2e.png` (ignored, not committed) |

The published signal time column is printed to two decimal places after 100 s, so the importer validates each row against the declared 125 Hz cadence with a 0.006 s tolerance. The row count, not the rounded text, still determines sample count and duration. This change was required for the published recording; the generated synthetic fixture had exact three-decimal times and did not expose it.

An earlier generated 60-second BIDMC-format fixture also exercised pause/resume, completion and the full replay UI. That historical run is not a substitute for the published-data result above.

The combined Java verification used seven Flyway migrations. The BIDMC import regression now runs against the full published CSV set when `BIDMC_CSV_DIR` is supplied, and the optional test passes locally. `DELAYED_TELEMETRY` remains a labeled metadata-only scenario: it records the delay but does not alter source timestamps or schedule delayed transport. Physical device transport, native Android/JavaFX clients, clinical validation, NEWS2, trained ML, PDF signing and hosted deployment are not claimed.
