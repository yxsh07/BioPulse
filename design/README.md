# BioPulse design handoff

**Current delivery: Framer, following the user's change of plan and supplied UI reference.** All ten screen designs now exist with Phone and Desktop breakpoints. [Open Framer](https://framer.com/projects/azwYqp0KC0VqssYRvb7l), [read the current handoff](framer/HANDOFF.md), or [browse the current renders](framer/index.html). This is an unpublished research prototype; backend/authentication/native applications remain integration work.

Academic biomedical intelligence prototype. Synthetic patient identities and de-identified research waveforms; all alerts and experimental outputs are demonstrations, not clinical recommendations.

- [Editable Sleek project](https://sleek.design/project/3xjcpDcVXit)
- [Design system and shared components](DESIGN-SYSTEM.md)
- [Android screen and interaction specification](ANDROID-SCREENS.md)
- [JavaFX desktop specification](JAVAFX-DESKTOP.md)
- [API and streaming contract proposal](API-MAPPING.md)
- [Accessibility and responsive acceptance checklist](ACCESSIBILITY.md)
- [Actual completion status, credit blocker and review findings](REVIEW-STATUS.md)
- [Local screen gallery](mobile/index.html)
- `mobile/html/`: active screen HTML exported from Sleek, for visual reference only.
- `mobile/renders/`: individual viewport renders, full-content review renders, and grouped previews.
- `sleek/`: request, run result, component metadata and manifest for traceability.

**Historical Sleek delivery:** four of ten screens were generated before credits were exhausted. It has been superseded by the ten-screen Framer project above. REVIEW-STATUS.md documents only that earlier Sleek pass.

The API contract is a proposed frontend integration contract, not a claim about an existing backend. HTML is reference material; production frontends remain Java/XML on Android and JavaFX/FXML/CSS on Windows. This handoff does not implement authentication, streaming, clinical logic, or exports.

## Fixed demonstration scenario

12 patients; 2 critical and 3 warning open simulated physiological events; 10 devices online and 2 offline. Counts refer to the same snapshot and must reconcile with filtered event lists. Technical device events are counted separately.

PT-0247 / Avery R. / Ward B, Bed 12; research admission: post-procedure observation. Snapshot: **25 October 2026, 14:08:00 IST (UTC+05:30)**. HR 108 bpm; SpO₂ 94%; RR 22 breaths/min; BP 128/82 mmHg; temperature 37.1 °C. MON-012 online; ECG quality good, PPG fair, respiration good. These are labeled replay fixtures.

Recording: 14:00:00–14:08:00 IST; 480 seconds total, 465 usable seconds; missing 14:04:10–14:04:25. Coverage 96.875%, displayed 96.9%. Sample time, server receipt time, and client receipt time are distinct. Replay never implies live acquisition.

Historical resolved SpO₂ event: measured 89% at 14:03:12; rule <90% for 20 seconds; duration 26 seconds; signal quality fair. This resolved event does not increase open-event counts. Event and analytics values are synthetic illustrations, not medical guidance.
