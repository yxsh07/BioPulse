# Proposed API-to-screen data mapping

This is an integration proposal for the shared Spring Boot service; no existing endpoints have been verified. The same DTOs feed Android Java and JavaFX presentation models. Endpoint examples below require authentication and server-side ward/patient scope checks.

## Route contract

| Screen | Proposed read routes | Core data | Explicit writes |
|---|---|---|---|
| Login | `GET /api/v1/session` | staff, role, scopes, expiresAt | `POST /api/v1/session`, `DELETE /api/v1/session` |
| Overview/command center | `GET /api/v1/overview?wardId=` | snapshotId, asOf, monitoredCount, eventCounts, deviceCounts, recentPatients, prioritizedEvents | none |
| Patients/wards/beds | `GET /api/v1/patients`, `/wards`, `/beds` | paged identities, assignments, monitoring states | permitted assignment update with revision |
| Patient detail | `GET /api/v1/patients/{id}`, `/{id}/vitals/latest` | pseudonym, code, context, six measurements, device and source | none |
| Monitoring | `GET /api/v1/recordings/{id}`, `/{id}/samples?start=&end=&signals=` | exact interval, channel units/rate/scale, samples, gaps | replay position stays local UI state |
| Trends | `GET /api/v1/patients/{id}/trends?start=&end=&metrics=`, `/coverage` | points, stats, direction, sample count, observed intervals, gaps | none |
| Analytics | `GET /api/v1/patients/{id}/analytics?start=&end=` | experimental score, modelVersion, explanation, limitations, computedAt | none; no client risk engine |
| Alert Center/events | `GET /api/v1/events?wardId=&patientId=&type=&severity=&state=&cursor=` | typed event summary, counts, cursor | none |
| Alert Detail | `GET /api/v1/events/{id}` | rule, threshold, value/unit, duration, quality, timeline, allowedActions, version | `POST /{id}/acknowledgments` with expectedVersion/idempotency key |
| Device Administration | `GET /api/v1/devices`, `/{id}/history` | online state, last receipt, channels, quality, source | optional authorized config route only when backend supports it |
| Reports | `GET /api/v1/reports/{id}`, `/{id}/preview`, `/{id}/download` | generation state, revision, coverage, artifact | `POST /api/v1/reports` with patient, interval, format |
| Profile/settings | `GET /api/v1/me`, `/me/preferences` | role/scopes, theme, notification categories/policy | `PATCH /api/v1/me/preferences` with expectedVersion |
| Audit | `GET /api/v1/audit?start=&end=&cursor=` | actor, action, resource, observed time/result/revision | none |

Time filters are ISO 8601 UTC instants with inclusive start and exclusive end; the UI labels IST explicitly. Store timestamps as Java Instant; convert using a selected ZoneId at formatting. Pagination has stable cursors. Overview counts and list filters share snapshotId/asOf so apparent disagreements during updates can be explained and refreshed coherently.

## DTO essentials

`Measurement`: metric, nullable value (or systolic/diastolic pair), unit, observedAt, receivedAt, sourceType, recordingId, quality {state, reason}, freshness {state, expiresAt}, serverEventIds. A null is missing, not zero. Non-observed analytics are not measurements. UI derives display formatting and elapsed age; clinical classification comes from server events, never hard-coded client thresholds.

`Coverage`: requestedStart/end, recordingStart/end, observedIntervals[], missingIntervals[{start,end,reason}], usableDurationMs, selectedDurationMs, coverageFraction, aggregationMethod. Signal-specific coverage may differ; a combined report must define its denominator. Fixture 465/480 refers to the illustrative shared recording mask; do not reuse it for unrelated channels.

`Event`: id, type (PHYSIOLOGICAL / TECHNICAL / EXPERIMENTAL), severity, workflowState, patientId, deviceId, measuredValue/unit, observedAt, receivedAt, startedAt, endedAt, durationMs, rule{id,version,expression,threshold,duration}, qualityAtTrigger, source, explanation, modelVersion when applicable, acknowledgment/resolution timeline, allowedActions, version. A technical event may have no measuredValue or physiological threshold. A resolved historical event is excluded from open counts.

`TrendSummary`: metric, unit, computationStart/end, lastUsableValue, mean, minimum, maximum, direction, sampleCount, usableDuration, method and missing policy. Backend defines aggregation (sample vs time weighting), BP statistics separately for systolic/diastolic, and direction window. UI must show unavailable statistics honestly; it cannot infer clinical meaning from direction.

## WebSocket updates

Proposed endpoint `/ws/monitoring`, authenticated handshake, subscribe by authorized ward/patient/recording. Envelope: eventId, sequence, type, patientId/deviceId, observedAt, serverSentAt, schemaVersion, source and payload. Types: vital.updated, waveform.chunk, event.created/updated, device.status, coverage.updated, report.status. Waveform chunk includes signal, start, sample period or explicit timestamps, values and quality/gap mask. Server defines units and rate; clients do not assume rates from screenshots.

Deduplicate eventId; process sequence within documented subscription scope. On sequence gap or reconnect, mark stale and retrieve an authoritative snapshot plus resume cursor before showing current state. Apply revision checks to event transitions; do not let a delayed update undo a newer acknowledgment. Distinguish connection heartbeat from physiological sample freshness. Backoff reconnections with jitter and a bounded ceiling; pause subscriptions on inaccessible screens and resubscribe on return. Rendering can drop display frames under load but may not invent samples, bridge missing intervals or discard audit events.

## Failure and mutation behavior

401: session expired; clear protected UI and authenticate. 403: insufficient scope. 404: unavailable/deleted patient/event with return navigation. 409: revision conflict, refresh detail. 422: invalid interval or invalid transition, show backend validation. 429: respect Retry-After. 5xx/network: show retry and last receipt with stale labeling. Acknowledgment/export request uses an idempotency key; a timeout triggers status reconciliation before another mutation. Never treat a network timeout as confirmed success.
