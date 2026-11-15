# Native Android delivery plan

Implement the approved `design/ANDROID-SCREENS.md` with the newer visual tokens and phone compositions in `design/framer/HANDOFF.md` and `design/framer/renders/`. The current OpenAPI contract and backend are authoritative where the design describes proposed features.

## Contract

- Java application code and native XML views; one activity, Navigation Component, ten fragments, per-screen ViewModels, LiveData and SavedStateHandle. No WebView, Compose, direct database access, locally generated measurements or clinical algorithms.
- Java 21 build toolchain; Android-compatible Java 17 bytecode, Android API 26 minimum, compile/target API 36. Pin AGP 8.13.2 and Gradle 8.13 per the official compatibility table.
- Framer near-black canvas, square outlined panels, Inter typography, large values, ECG/PPG/respiration colors and four bottom tabs. Native touch targets and scalable text take precedence over tiny reference labels.
- Configurable backend origin. Default emulator endpoint is `http://10.0.2.2:8080`; a physical phone can use `adb reverse tcp:8080 tcp:8080` and `http://127.0.0.1:8080`, or the backend's reachable LAN address on port 8080. Debug HTTP is restricted by client validation to local/private addresses. Release requires HTTPS. Do not bundle credentials.
- In-memory session cookies and CSRF token; bootstrap/login/resynchronize, authenticated WebSocket, scoped REST snapshots, explicit session expiry and logout. Do not persist passwords, cookies or protected observations.
- Derive counts and patient context from the API. Replay reads use output recording and recorded cursor; preserve original timestamps, units, missing values and provenance. Pause and reconnect never fabricate samples.
- The report API produces CSV, not PDF; provide native CSV preview, Save and Share through explicit system actions. No unsupported PDF job or background push delivery is claimed. Notification preferences are device-local and accurately describe available delivery.

## Build order and acceptance

1. Scaffold, themes and authentication: installable native shell, configurable endpoint, real login/logout and rotation-safe state.
2. Overview, patient list and workstation: authorized API counts, search/pagination, six vital cards with missing/source/time labels.
3. Monitoring and trends: authenticated reconnecting stream, server replay controls, recording/cursor isolation, bounded waveform rendering and accessible data, source-time ranges and server aggregates.
4. Alerts, detail, reports and profile: filters, versioned acknowledgment/resolution, CSV Save/Share, theme and session preferences.
5. Explore the installed app on an authorized Android device/emulator before authoring device tests. Run unit/API tests, lint, native UI/instrumentation workflows, rotation, missing/error states and visual comparisons. Produce a debug APK with checksum and honest verification report.
6. Inspect the diff for secrets/unintended changes; commit and push completed work. Keep SDK, Gradle caches, emulator images, local credentials and build output out of Git. Deliver the APK as a local artifact.

## Testing prerequisite

The user selected a physical phone and ARTEMIS. Project-local Android SDK tools and Gradle are installed outside Git. The authorized Xiaomi 2201116TI runs Android 13. ARTEMIS is installed at `C:/Users/ASUS/artemis`; its MCP server can be invoked through a local stdio client even while its tools are absent from this session's tool catalog. The device probe passed on 2026-10-26, with a degraded verdict for the optional accessibility helper. Device input was rejected with `INJECT_EVENTS`; the user has been asked to enable Xiaomi's USB debugging security option. Do not author tests until ARTEMIS has explored the installed BioPulse app. Build, lint and device verification results will be recorded in the delivery report.

## Primary references

- https://developer.android.com/build/releases/agp-8-13-0-release-notes
- https://developer.android.com/build/jdks
- https://developer.android.com/guide/navigation/design
- https://developer.android.com/topic/libraries/architecture/saving-states
- https://developer.android.com/training/data-storage/shared/documents-files
- https://developer.android.com/training/secure-file-sharing
