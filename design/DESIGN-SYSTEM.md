# BioPulse design system

## Foundations

Token values below are the native implementation baseline. Sleek renders are a visual reference; normalize their styling to these tokens during implementation rather than importing browser CSS. Dark is the default monitoring workspace; light preserves the same hierarchy and semantics.

| Token | Dark | Light | Use |
|---|---|---|---|
| surface.canvas | #10171F | #F3F6F8 | App background |
| surface.panel | #18232D | #FFFFFF | Cards and panels |
| surface.raised | #21313E | #E9F0F4 | Selection and inputs |
| border.default | #627789 | #657887 | Input/panel boundaries |
| text.primary | #F3F7FA | #14212B | Values and headings |
| text.secondary | #B5C4D0 | #425564 | Metadata and units |
| action.primary | #62D7CB | #006B63 | Main action/focus accent |
| action.onPrimary | #092B28 | #FFFFFF | Filled action text |
| signal.ecg | #71E69A | #176B36 | ECG lead |
| signal.ppg | #66D9F2 | #006A82 | PPG lead |
| signal.resp | #C5ABFF | #6845A0 | Respiration lead |
| status.critical | #FF9B9B | #B52230 | Critical label/icon |
| status.warning | #FFD37A | #795000 | Warning label/icon |
| status.normal | #71E69A | #176B36 | Normal label/icon |
| status.technical | #9CCBFF | #225C93 | Technical event |
| focus.ring | #8DECE1 | #006B63 | 2dp/2px visible outline |

Do not use a signal color to imply alert severity. An ECG panel remains green when an alert label is amber. Missing segments use a hatch/label and a break in the trace; never interpolate them silently. Criticality uses both text and shape. Selected items use a border plus a selection mark.

Typography: Android Roboto/system sans; Windows Segoe UI. Tabular numerals where supported; fallback monospace only for dense measurement tables. Android sizes in sp: screen title 24/30 semibold, section 18/24 semibold, vital 28/34 semibold, body 16/24, label 14/20, secondary 12/18. Desktop: title 24px, section 18px, body 14px, metadata 12px, bedside vital 30–38px. Preserve units as adjacent text, not superscript decoration. Respect font scaling; use wrapping and vertical expansion rather than clipping.

Spacing scale 4, 8, 12, 16, 24, 32, 48; mobile screen gutter 16dp, desktop 24px; internal card padding 16. Card radius 12dp/px, control radius 8. Flat 1px boundaries, no glass, gradients or decorative shadows. Use familiar outlined icons with visible text labels, consistent stroke and optical size; do not use emoji for operational status.

## Reusable component inventory

| Component | Anatomy | Variants and behavior |
|---|---|---|
| App shell | title, scope, connection strip, navigation | Android top/bottom bars; desktop rail/toolbar |
| Patient header | code, pseudonym, ward/bed, admission, source | compact list / expanded detail; code persists on drilldown |
| Vital tile | name, value, unit, sample time, quality, state | current/replay/stale/missing; BP has paired values |
| Signal strip | lead label, unit/scale, trace, time axis, quality | ECG/PPG/respiration; gap/disconnected/paused |
| Source badge | icon, source text, optional recording ID | Synthetic identity / Research waveform / Replay / Missing |
| Coverage rail | selected interval, observed spans, gaps | exact duration, usable duration, percentage; explicit timezone |
| Event row | severity, type, patient, value, time, state | physiological / technical / experimental; open/acknowledged/resolved |
| Event inspector | rule, threshold, duration, quality, audit timeline | acknowledge only with permitted server transition |
| Trend panel | title, axes, coverage, trace, events, summary | one vital / aligned comparison; independent units |
| Statistic group | current, mean, min, max, direction | units, computation window, usable sample count |
| Anomaly annotation | score, model version, explanation | experimental; server-provided only; unavailable state |
| Filter controls | search, ward, severity, state, interval | active chips, reset, result count; retain on back |
| State panel | icon, headline, description, action | loading/empty/error/offline/stale/unauthorized |
| Report preview | patient, interval, stats, events, gaps, disclaimer | screen preview / printable light page, export progress |
| Preferences row | label, helper, control, save state | switch/selection; enabled/disabled/error |
| Audit row | actor, action, timestamp, result | read only; pagination and filters |

## Shared state model

Each measurement carries value, units, source, observedAt, receivedAt and quality. Freshness is independent from device connection: online transport can carry stale observations. The server supplies freshness classification and expected cadence; clients show elapsed age and use the supplied expiry policy if transport is lost. No fabricated fixed universal clinical freshness threshold.

- Initial loading: static skeletons, accessible loading announcement, no zero-valued vitals.
- Empty: explain scope/date filter and provide clear/reset action; distinguish no patients from no matching patients.
- API failure: show scope-specific error and Retry; keep previous values only with timestamp and stale label.
- Disconnected: persistent banner, reconnect progress, last received time; no advancing trace or silently current values.
- Stale: keep last known value muted with explicit age and label; missing is an em dash plus “No measurement”, never zero.
- Replay: persistent badge, recording time, playback position, pause state and speed; do not change observed timestamps to wall-clock time.
- Partial data: panel-specific missing status; unaffected signals continue. Quality “unknown” is distinct from “good”.
- Unauthorized: hide sensitive content, explain insufficient scope; expired session returns to login with safe route restoration after reauthentication.

Motion is restricted to purposeful trace updates and small state transitions. Reduced-motion mode disables interpolation and uses discrete frame updates. No pulsing alarm animations.
