# Android implementation and interaction specification

## Native architecture

Single activity with Java Fragments and Android Navigation Component; native XML layouts and Material Components. Each screen has a Java ViewModel exposing immutable UiState through LiveData. Repositories call a shared Spring Boot REST service and WebSocket stream; no SQL, direct database access, synthetic random generators or local clinical engines. HTML from Sleek establishes visual composition only.

Use MaterialToolbar, BottomNavigationView (Overview / Patients / Alerts / Profile), RecyclerView with stable IDs and ListAdapter, TextInputLayout, MaterialButton, ChipGroup, MaterialSwitch and date-range controls. Draw waveform/trend graphics in native custom Views or an audited native chart library selected during implementation. Custom Views accept timestamped data and viewport state; graphics never generate measurements. Canvas is for drawing the provided data only.

Package proposal: ui/{auth,overview,patients,monitor,trends,alerts,reports,profile}, ui/components, presentation, data/{api,stream,repository}, model. Screen XML uses ConstraintLayout and vertical content with insets. Lists use RecyclerView; avoid unbounded nested scrolling. ViewModels survive rotation; SavedStateHandle stores patient ID, filter and interval. Cancel screen subscriptions on lifecycle stop and resynchronize on resume.

## Screen-by-screen specification

### 01 Secure Login

`LoginFragment.java` / `fragment_login.xml` / `LoginViewModel`. Top research brand and hospital scope, concise purpose, email/password fields, show-password action, primary sign-in, credential assistance and research disclaimer. Keyboard next/done order follows fields; password never appears in logs. Submit shows progress and prevents duplicates; field errors are specific, credentials error remains generic. API/network failure offers Retry without erasing email. Authentication token handling and session expiry are backend integration work; do not imply a working login in design HTML.

### 02 Overview / Monitoring Summary

`OverviewFragment` / `fragment_overview.xml` / `OverviewViewModel`. Toolbar ward scope and last refresh, global Replay/source strip; 2×2 metric cards for monitored patients, critical/warning events and device availability; prioritized event list; recent patient rows. Counts are 12 / 2 critical / 3 warning / 10 online / 2 offline. The critical/warning card can show both values rather than hiding one in a combined total. Tap counts opens prefiltered lists; tap patient opens detail; event opens inspector. Loading covers only pending sections; cached overview includes age, and failed subpanels retain independent retries.

### 03 Active Patient List

`PatientListFragment` / `fragment_patient_list.xml` / `PatientListViewModel`. Toolbar, search code/pseudonym, ward/status chips, count, patient rows with ward/bed, key vitals, source/age/quality and alert text. Search debounce is a UI behavior, not a risk calculation. Clear search and filter reset are accessible. Selecting a row carries patientId; back restores query/scroll. No results explains active filters. Pagination uses stable ordering and explicit loading/error footer; do not replace the whole list on every stream update.

### 04 Patient Detail and Latest Vitals

`PatientDetailFragment` / `fragment_patient_detail.xml` / `PatientDetailViewModel`. Persistent PT-0247 identity, Avery R., ward/bed and admission context; source/coverage; six vital tiles (HR, SpO₂, RR, paired BP, temperature); ECG/PPG/respiration previews; device status and receipt timestamp; trends, alerts and report actions; separately labeled experimental panel. Phone uses two vital columns, large text one. Each tile has units and observation time. Preserve selected patient on every action. No measurement replaces value with labeled missing state while retaining valid others.

### 05 Live ECG / PPG / Respiration Monitoring

`MonitorFragment` / `fragment_monitor.xml` / `MonitorViewModel`. Title “Monitoring”, persistent REPLAY badge and recording ID; patient mini-header; three stacked named signal strips; units, time scale and quality per strip; timeline and explicit gap; play/pause, position and speed. Online means the replay source is connected, not live acquisition. Pause freezes position and sample timestamp. Reconnect retains last frame with “Disconnected” overlay; no fabricated continuation. Scrub requests samples for that position. Landscape expands width and can hide expanded metadata, never source/age. Unavailable lead gets its own state panel.

### 06 Historical Trends and Date Range

`TrendsFragment` / `fragment_trends.xml` / `TrendsViewModel`. Patient header, date-range picker and timezone, coverage rail 14:00–14:08, metric selector; chart with 15-second explicit missing interval, event markers; current/mean/min/max with statistic window and sample count; direction and experimental annotation; aligned two-vital comparison. Only current fixture values are specified by the brief: additional design statistics are illustrative and must be replaced by server aggregates. A request for 24h shows eight minutes observed and the remaining period unrecorded. Pickers validate start≤end. Out-of-range dates yield honest empty coverage. Separate axes retain units; charts expose an accessible table.

### 07 Alert Center

`AlertsFragment` / `fragment_alerts.xml` / `AlertsViewModel`. Counts/tabs, filters for type/severity/state/ward, chronological or severity sort, event rows. Physiological events use severity; sensor disconnect/missing-data use technical labels and separate counts; experimental outputs never masquerade as validated alerts. Show measured value, unit, patient, observed time and workflow state. Acknowledgment is separate from resolution. Filter state persists through inspector navigation. Empty “No open events in this scope” differs from disconnected “Unable to refresh”.

### 08 Detailed Alert Information

`AlertDetailFragment` / `fragment_alert_detail.xml` / `AlertDetailViewModel`. Severity/type/state, linked patient, measured 89% at 14:03:12 IST, SpO₂ <90% for 20s, event duration 26s, PPG fair; rule version, provenance, observed/received timestamps and actor timeline. Resolved example renders prior acknowledgment and resolution, with action disabled/labeled resolved; an open-state variant exposes Acknowledge. Server validates permission and state; show conflict and refresh when another staff member acts. Quality limitations are visible. Experimental detail includes model version, server explanation and research-only label. Sensor detail replaces physiological threshold with connectivity/missing interval facts.

### 09 Patient Monitoring Report

`ReportFragment` / `fragment_report.xml` / `ReportViewModel`. Interval selector, report status, paginated/light printable preview: patient code/pseudonym, recording/source, 480s interval/465s usable/96.9% coverage, six-vital table, gaps, events and experimental disclaimer. Label report generated time separately from observed interval. Export requests server PDF and shows queued/generating/ready/failed states; preview stats and export use the same report ID/revision. Save/share are explicit user actions through native system flows; never auto-send. Empty interval disables export with explanation.

### 10 Profile and Notification Preferences

`ProfileFragment` / `fragment_profile.xml` / `ProfileViewModel`. Pseudonymous research staff role and ward scope; notification controls by severity and technical/physiological type, delivery description, Android permission state, dark/light/system selection with light preview, session information and sign-out. Explain when OS notifications are disabled and link to app settings. Preference save reports success/failure and reverts failed optimistic changes. Server enforces any required categories; disabled controls explain why. Sign-out clears session/subscriptions and protected cached data according to backend policy.

## Navigation and interaction map

Login → Overview after authenticated session. Bottom navigation selects Overview, Patients, Alerts or Profile with independent saved stacks. Overview patient/recent row → Patient Detail; overview critical/warning count → Alert Center with corresponding filters; device count → scoped availability list (patient/device filter sheet on mobile). Patients → Patient Detail → Monitoring, Trends, Patient Alerts or Report. Alert Center → Alert Detail → related Patient Detail. Trends event marker → Alert Detail; Trends report action → Report with the same interval. Profile sign-out → Login; expired session follows the same path with an explanation.

Back returns to the prior list/filter/scroll position; deep links carry opaque patient/event IDs, enforce authentication and scope, and safely handle deleted/unavailable records. An alert opened from a patient retains that patient context. Global tabs are hidden for focused login, monitor and report preview when space demands; toolbar Up remains available. Chart ranges and replay position are explicitly persisted; navigation never silently changes interval to 24h.

## Mapping shared components to XML/Java

| Design component | Android mapping | Presentation input |
|---|---|---|
| Vital tile | `view_vital_tile.xml`, `VitalTileView.java` | MeasurementUiModel |
| Patient row | `item_patient.xml`, PatientListAdapter | PatientRowUiModel |
| Source/quality badge | TextView + icon with content description | ProvenanceUiModel |
| Waveform | `WaveformView.java` in `view_signal_panel.xml` | Timestamped sample buffer + gaps |
| Trend/statistics | `TrendChartView.java`, `view_stat_group.xml` | Server aggregate + observed intervals |
| Event row/inspector | `item_event.xml`, typed detail sections | EventUiModel + allowed actions |
| Coverage | `CoverageRailView.java` + textual interval | CoverageUiModel |
| State banner | `view_data_state.xml` | ConnectionState + freshness |
| Report preview | server preview pages/native structured layout | ReportUiModel with revision |
| Theme | `values/colors.xml`, `values-night/colors.xml`, themes | saved user theme preference |

Resource identifiers, class names and endpoint names here are proposals. No runnable native application is included in the design deliverable.
