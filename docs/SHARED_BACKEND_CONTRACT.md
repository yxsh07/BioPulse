# Shared backend boundary — coordination handoff

Updated 2026-10-25 following the user's explicit ownership split.

The database agent owns `database/`, Flyway SQL, `dao/`, `jdbc/` and shared persistence/domain contracts. The backend agent owns controllers, services, analytics, alerts, security, WebSockets and reporting. Do not create a parallel schema or competing JDBC implementation. Existing baseline migrations/JDBC were created and pushed before this split was communicated; please evolve that baseline additively or identify an agreed replacement. Preserve the current REST shapes unless a contract change is coordinated.

## Current integration expectations

- Existing `RegistryDao`, `MeasurementDao`, `AlertDao`, `SimulationDao`, `ReportDao` are wired into working services. New `PatientDao`, `AdmissionDao`, `DeviceDao` have been discovered and will replace registry facade use for their respective operations.
- `MeasurementDao.insertBatch` returns persisted Measurement records with generated IDs; `insertWaveform` returns a persisted WaveformChunk. Scalar event uniqueness is `(source_id,event_id)`; waveform uniqueness is independently `(source_id,event_id)`.
- `MeasurementCode` is ECG, PPG, RESP, HR, PULSE, SPO2, RR, SBP, DBP, TEMP. Keep PULSE backward compatible if adding PR. Device adapter types are ECG, OXIMETER, RESPIRATORY, NIBP, TEMPERATURE, MULTI. New normalized vocabularies must accept these public DTO codes (including insertion into a fresh database before devices exist).
- Observations keep `Double value` nullable for MISSING/INVALID; canonical units and recorded/received instants remain independently available. Ingestion normalizes observed instants to microseconds before persistence/evaluation. JSON waveform samples must still round-trip through the DAO if storage becomes binary.
- Simulation sessions expose id, admissionId, recordingId, outputRecordingId, state, speed and cursorAt. Output source is SIMULATION and records original source/recording provenance. Tick reads are half-open and bounded. Missing data must never be generated.
- `RegistryDao.setOnline` and `assignedAdmissions` back `/devices/{id}/status`, with after-commit DEVICE_STATUS events. Please preserve these or provide equivalent DeviceDao methods.
- Alert sourceType comes from RegistryDao.source; state changes require expectedVersion and atomic optimistic update. Reports keep a persisted CSV snapshot plus source IDs/missing ranges and require current ward checks on download.
- HttpSession invalidation/expiry now revokes open WebSockets; admission scopes are checked on handshake and on every event.

## Requested DAO boundaries (coordinate implementation here)

Current baseline still has prepared SQL in RegistryController, AccessPolicy, AuthenticationService, ReportService and DemoSeeder. To complete the separation without duplicating your work, please provide/agree interfaces for:

1. Authorized patient/admission/device/ward/bed listing with SQL-level pagination; actor and ADMIN flag are explicit inputs. Filtering after pagination would leak counts or omit valid results.
2. Account lookup (username -> User/passwordHash/role/enabled), admission/patient/ward access predicates, and audit insertion/pagination. This moves security query SQL behind DAO interfaces.
3. All admissions in a ward for historical reports (not only active admissions).
4. Recorded source device IDs (scalar + waveform) to validate replay target assignments before starting a session.
5. Opt-in synthetic seed ownership. The existing DemoSeeder writes the baseline tables only and is disabled by default; normalized schemas should maintain that fixture path or supply a compatible replacement.

No additional JDBC or SQL migrations will be authored by the backend agent. Shared contract changes should be recorded here before service adaptation. Please keep the shared build compilable and run the combined Maven integration suite after enabling migrations.

## Review notes for persistence owner

- The pending `JdbcMeasurementDao.latest()` diff adds a GOOD/FAIR filter. The `/measurements/latest` UI contract needs the **latest recorded event, including MISSING/INVALID/POOR/UNKNOWN**, so an explicit missing event does not get replaced by an older apparently-current valid value. Please preserve that behavior in `latest()` and expose a separate `latestValid()` query if required for another consumer. I will add an API regression test for this distinction.
- The shared DAO additions are currently untracked. Backend service adaptations to PatientDao/AdmissionDao/DeviceDao depend on those files; please commit your shared contracts before the backend adaptation is pushed. I will stage only backend-owned files. Avoid staging another task's partial changes with `git add .`.
- The original CI baseline is green: https://github.com/yxsh07/BioPulse/actions/runs/40204125487. After your Maven resource wiring is enabled, both ApplicationIT and DatabaseIT should run against the combined schema; use a separate temporary test database if changing applied SQL checksums.
- Backend now consumes AccountDao, AccessDao, AuditDao.append, AdmissionDao.allInWard and SimulationDao.recordedDeviceIds. Please add `AuditDao.page(int offset,int limit)` for the ADMIN `/audit` endpoint so its reads use the same `audit_logs` target as writes. `DeviceDao.allAuthorized(String actor,boolean admin)` would avoid N+1 assignment-history checks in device listing. No other contract additions are needed for the existing routes.
- When migrating an existing seeded database from V1 through V6, MySQL warned that V3 truncated `value_numeric` and `original_value` while backfilling waveform-adjacent measurement rows. The original `measurements.value` DOUBLE column remains present, but `original_value DECIMAL(16,6)` no longer represents exact recorded precision. Please either retain exact original values in a suitable column or document that those DECIMAL fields are rounded projections and never use them in place of the original recorded DOUBLE.

## Verification baseline

Before the new migrations are enabled, Java 21 compilation, 15 unit tests, 5 real-MySQL integration tests and 4 desktop/mobile browser scenarios pass. Testcontainers is also running in GitHub CI. Local Docker's Linux engine is unavailable; a separate MySQL 8.0.46 instance on port 3307 is used for local integration tests. Local secrets and test data are under ignored `.tools/`; never commit those files.

## Independent build output

Concurrent Maven clean/compile operations collided in `target`. The backend task now verifies with `-Dbiopulse.build-directory=D:/BioPulse/.tools/backend-target`; the default remains `target` for CI, Docker and ordinary builds. Please keep independent build output directories while both agents are active.
