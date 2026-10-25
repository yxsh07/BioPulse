# JavaFX desktop companion specification

## Shell and sizing

Target 1920×1080 logical workspace; validate Windows DPI scaling at 100%, 125%, 150% and 200%. Use a BorderPane root in `MainShell.fxml`: top toolbar (64px), left navigation (216px expanded / 64px compact), center routed content, bottom connection/source strip (28px). Content gutter 24px, panel gap 16px, minimum useful application size 1024×720. Avoid absolute positioning; FXML uses GridPane, VBox, HBox, SplitPane, StackPane and virtualized TableView/ListView. Size all panels from available scene dimensions and constraints.

Navigation destinations: Command Center, Patient Workstation, Trends, Events, Patients/Wards/Beds, Devices, Reports, Settings/Audit. Toolbar holds hospital/ward scope, patient search, research-mode source, session menu. Status strip shows transport, last receipt time, timezone and data source. Back/forward preserve selected patient, interval, filters and table scroll. Ctrl+F focuses context search; Esc dismisses transient overlays; all shortcuts appear in menus. Keyboard navigation never requires charts or hover.

Each route has an FXML controller for bindings/interaction, a presentation model exposing JavaFX Properties and ObservableLists, and repositories for REST/stream calls. Keep I/O and chart preprocessing off the FX application thread; marshal bounded UI updates via Platform.runLater or a pulse-aware scheduler. Maintain bounded waveform buffers, decimate for display without changing timestamps or creating interpolated observations across gaps. Server owns calculations, rules and workflow transitions.

Reusable FXML: PatientHeader, VitalTile, WaveformPanel, TrendPanel, CoverageRail, EventRow, ConnectionBanner, SourceBadge, StatePanel, ReportPreview. CSS maps the shared dark/light tokens to JavaFX looked-up colors, with `.stale`, `.missing`, `.replay`, `.critical`, `.warning`, `.technical` style classes. Signal identity and severity classes remain independent. CSS also defines a visible keyboard focus ring.

## 1. Multi-Patient Hospital Command Center

Hierarchy: scope toolbar → counts ribbon → central patient grid + prioritized events → connection strip. At 1920×1080 with expanded rail, center is about 1656px wide. Allocate approximately 1304px to a 3×2 patient grid and 336px to the event sidebar, with a 16px gap. Six visible cards per page; remaining six from the 12-patient cohort are explicitly paginated. Use rows with constrained heights and expandable detail rather than overflowing hidden controls. Cards contain code/pseudonym, ward/bed, severity+state, core vitals, a small ECG trace, device/quality, source and observation/receipt time; opening a card enters the workstation.

Counts ribbon shows 12 monitored, 2 open simulated critical and 3 warning physiological events, 10 online/2 offline devices. Each is a scoped filter shortcut. Event sidebar separates physiological and technical groups with visible type text. Single-click selects; Enter or “Open workstation” drills down. Sort/focus changes never silently reorder a card under a pointer during acknowledgment. Streaming refresh keeps stable patient IDs and positions until an explicit resort.

Below 1600px: collapse rail and use two patient columns. Below 1280px: move events into a labeled Events drawer and use two columns. At 1024px retain two compact cards per row or use one when text scaling requires it. Always show page range, source and age. Loading uses card skeletons; no patients shows ward scope and clear-filter action; partial device failure overlays only affected traces; global outage keeps labeled last-known data and a reconnect banner.

## 2. Individual Patient Monitoring Workstation

Hierarchy: identity/admission/source header → 12-column monitoring workspace → record/event footer. At desktop width allocate 8 columns to three stacked ECG/PPG/respiration panels; 4 columns to six-vital grid, device quality and event feed. Each trace owns its y units, scale, time axis, lead and quality label. Header includes selected recording coverage and replay status. Footer controls play/pause/speed/position only in replay mode; live mode displays actual acquisition and receipt information instead.

Open Trends/Events/Report carries patient and interval. Freeze is a display action with frozen timestamp; it does not acknowledge an event or stop the device. Missing segment breaks trace and names interval. Disconnected device shows no advancing waveform. At smaller sizes stack vitals above traces and move events to a tab; identity stays pinned. Loading individual leads shows lead-specific states; unavailable BP never removes other values. Device/source lookup errors have separate retry actions.

## 3. Historical Trends and Analytics

Hierarchy: patient/interval toolbar → exact coverage rail → charts (8 columns) + statistics/experimental inspector (4 columns) → event timeline/data table. Charts align x timestamps, not unlike y values. Show current/mean/min/max, sample count and direction for the selected vital; current means last usable sample in the selected interval. Two-vital comparison uses separate labeled y axes or vertically aligned panels, with no shared scale inference. Experimental anomalies include server score, explanation, model version and research label.

Range selection fetches coverage first or alongside data; a 24h selection with eight minutes observed leaves the rest blank and explicitly marked unrecorded. Hover and keyboard-selected points show exact values and timestamps; equivalent accessible table is always available. Clicking an event marker opens inspector without losing range. Narrow display stacks inspector below chart; stats wrap to two columns. No data offers choose recorded range; aggregate failure leaves raw trace visible with statistics unavailable; query pending shows progress and allows cancellation. Never connect gaps visually.

## 4. Alarm and Event Management

Hierarchy: filter bar + state counts → event TableView (roughly 65%) + detail/audit inspector (35%). Columns: severity, type, state, patient, ward/bed, value/units, rule summary, observed time, duration, quality, acknowledgment. Type differentiates physiology, technical and experimental. Inspector includes rule/threshold and version or technical cause, source, timeline and permitted actions. Selection persists when new events arrive; a “new events” indicator allows controlled re-sort.

Acknowledge requires an explicit selected event and permitted action; do not offer blind bulk acknowledgment. Server response updates state; conflict refreshes the inspector and identifies the changed state. Resolution is server-driven or an explicitly permitted workflow action, never inferred from acknowledgment. Search/filter empty and no-open-events have distinct messages. Authorization error explains restricted scope; outage disables mutations with reconnect status. At narrow widths use full-width table plus a detail drawer with keyboard return focus.

## 5. Patients, Wards and Beds

Hierarchy: left ward/bed tree (20%) → central patient table (55%) → selected assignment/detail panel (25%). Rows show pseudonymous identity, patient code, admission context, ward/bed, monitoring assignment, source and status. Filters support ward, bed availability, monitoring and device state. Tree selection scopes the table; search retains visible query context. Open patient goes to workstation.

Assignment edits, if backend permits them, use a reviewable form showing current and requested assignment; save checks server revision and permission. Conflict preserves proposed fields and asks to reload the changed assignment. No client database edits. Empty bed list explains whether all beds occupied or filter excludes them; unknown ward is handled as unavailable. At narrower sizes ward selection becomes a toolbar picker and details a drawer. Initial load shows tree/table skeletons; per-panel retries preserve selection.

## 6. Device Administration

Hierarchy: device availability summary → device table (70%) + selected device inspector (30%). Columns: ID, patient assignment, connection, last receipt, expected cadence, signal channels, quality, firmware metadata if supplied. Inspector shows transport history, acquisition source, missing intervals and last backend diagnostic. “Online” never guarantees fresh physiological samples. Filter disconnected/stale/unknown independently.

Inspect reconnect history and navigate to assigned patient; any configuration action is permission-gated and backend-validated with explicit changes before saving. No silent reconnection commands to real devices in this prototype. Loading, no devices configured, no matching filter, diagnostic unavailable and transport failure are different states. Narrow view uses table plus drawer. Historical stale values keep their timestamps; device disconnect creates technical events, not physiological critical events.

## 7. Reports and Exports

Hierarchy: report criteria sidebar (25%) → printable preview (55%) → generation history/status (20%), collapsing history to a tab at smaller widths. Criteria: patient/cohort scope, exact interval/timezone, included signals/statistics/events, permitted format. Preview is white paper with dark text in both themes; contains provenance, observed coverage, missing intervals, summary tables, experimental labels and generated timestamp. Default patient report uses PT-0247's eight-minute interval and 96.9% coverage.

Generate submits server job; queued/generating/ready/failed status is observable and retryable. Export uses the generated revision; save location comes from native FileChooser. Print preview and Print are explicit controls. Empty interval prevents generation with an explanation. Scope or record changes invalidate preview and identify the need to regenerate. Large reports use paginated preview and bounded memory; API failure never exports a stale preview as a new report.

## 8. Settings and Audit History

Hierarchy: section navigation → settings form or audit table → explanatory/save area. Settings: account/session, ward scope, dark/light/system, text density, motion, notifications by severity/type, timezone display and connection diagnostics. Server policy can lock required categories with explanation. Theme preview includes representative vital, warning and focus states. Failed save leaves a visible unsaved/error state; leaving a dirty form provides save/discard choices.

Audit tab: actor, action, resource, timestamp, result and revision; filters for actor/type/time with server pagination. It is read only and scope-limited. Acknowledge/export/assignment actions link to the corresponding event/report/patient when permitted. Empty audit scope differs from failed fetch. At narrow widths settings stack label over input; audit hides optional columns behind row detail, keeping timestamp/action/result. Session expiry clears protected views and routes to login.

## Desktop acceptance walkthrough

At 1920×1080 confirm six patient cards plus event sidebar, with legible vital values and no clipped controls. Select PT-0247 → workstation → eight-minute trends → resolved SpO₂ event → return to range → report. Disconnect stream: stop progression, expose stale/receipt information, reconnect and resume only after authoritative synchronization. Repeat at 1366×768 and 1024×720 with light theme, keyboard only, and scaled text. Native implementation validation remains future work; this document defines intended behavior.
