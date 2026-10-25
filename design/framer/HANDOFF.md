# BioPulse / Framer handoff

Active editable project: https://framer.com/projects/azwYqp0KC0VqssYRvb7l

The user's latest instruction replaces Sleek with Framer. The supplied fitness UI establishes the visual reference: near-black canvas, square thin-outline cards, restrained white typography, large numerical readings, spaced metadata and a four-item bottom navigation. The screenshot is a reference, not an embedded background or source of patient data.

## Delivered in Framer

Ten routed screens with Desktop (1200px) and Phone (390px) breakpoints: Overview `/`, Secure access `/login`, Active patients `/patients`, Patient workstation `/patient`, Signal monitoring `/monitoring`, Historical trends `/trends`, Alert center `/alerts`, Event information `/alert-detail`, Monitoring report `/report`, and Profile/preferences `/profile`.

Shared canvas-native layout template, navigation, source strips, identity headers, vital cards, typography styles and color tokens. A self-contained React code component supplies behaviors that need runtime state: search/filtering, deterministic synthetic chart samples, date/period/metric selection, accessible data table, replay position/speed/pause, disconnected preview, event filters and local acknowledgment, theme-panel preview, preference toggles, password reveal and report print window. `source/ResearchControls.tsx` is a copy of that Framer code component, not Android or JavaFX code.

The shell navigation is fixed at the viewport bottom and content has clearance below it. Full-content captures show the fixed navigation at its initial viewport position; the gallery uses viewport crops with links to the full captures.

## Data and interactions

All values are explicitly synthetic fixtures. No clinical dataset, credentials, REST service or WebSocket endpoint was supplied or connected. Login does not authenticate or submit credentials; Enter research demo opens Overview. Data continues to carry recording time, not current wall-clock time. Shapes are illustrative and labeled uncalibrated. Replay advances a demonstration position; it does not reproduce a time-calibrated acquisition sample stream.

Twelve patients are represented; ten devices online, two offline. PT-0247 has HR 108 bpm, SpO₂ 94%, RR 22 breaths/min, BP 128/82 mmHg and temperature 37.1 °C. Open physiological counts reconcile with two critical and three warning fixtures. Technical disconnection is separate. Historical SpO₂ 89% at 14:03:12 is resolved after 26 seconds and is excluded from open counts. Other cohort rows intentionally do not claim detailed measurement datasets.

Recording 25 October 2026, 14:00–14:08 IST; missing 14:04:10–14:04:25. Waveform-mask coverage 465/480 = 96.9%. HR and SpO₂ charts use fixed illustrative ten-second series with null points; no random values are generated. Their discrete sample count is separate from waveform coverage. A 24-hour range states that only eight minutes were recorded and 23h52m are unrecorded. Another date gives an empty recording state. Trend statistics describe fixtures only, not clinical scores; actual operational statistics must be supplied by the shared backend.

Patient row PT-0247 opens the detailed workstation. Other rows are cohort context without independent full patient records. Event links carry an event ID in the query string; invalid IDs fall back to the historical fixture. The inspector displays selected patient/type/rule/quality/state. Local acknowledgment lasts for that inspector instance only, writes no backend state, and never resolves an event. Preference saves are preview-session state; no OS notification delivery exists. Light theme is an interactive panel preview, not a site-wide theme switch. Report Print opens a separate white report document; browser Save as PDF remains a user action. Server report requests explain that the service is not connected.

## Updated reference design tokens

| Role | Value |
|---|---|
| Canvas | #111111 |
| Panel | #131313 |
| Boundary | #2E2E2E |
| Primary text/action | #F4F4F1 |
| Metadata | #AAAAAA |
| ECG | #8BD8A4 |
| PPG | #86CCE1 |
| Respiration | #C4B0E7 |
| Warning | #EFC679 |
| Critical | #FF9999 |
| Clinical card radius | 0px |
| Typography | Inter, 400/500/600 |
| Type sizes | 48 reading, 38 desktop title, 32 phone title, 21 section, 15 body, 12 metadata, 10 eyebrow/navigation |
| Spacing | 12/16/20/24/32px |

These tokens supersede the earlier Sleek visual baseline for the Framer design. Small reference-style navigation labels require native accessibility validation and may need enlargement in production. The earlier shared component, Android/JavaFX and API specifications remain the behavioral/native handoff; translate these updated visual tokens when implementing them.

## Verification and boundaries

- Rendered and visually inspected all ten Phone screens, plus representative desktop Overview, Workstation and Trends.
- Corrected mobile grid clipping and inconsistent event severity/state/counts during review.
- Final Framer code write returned no diagnostics; final tree mutation returned no errors or linter warnings.
- Source and screen renders exported locally. Framer source layers remain editable in the project.
- Full keyboard/screen-reader, OS font scaling, browser interaction, backend, authentication and print-dialog tests have not been executed. Native applications have not been built.
- No publication or deployment was requested or performed. Use Framer Preview in the project to try the interactions.

## Native implementation references

- ../ANDROID-SCREENS.md — ten Java/XML screen mappings and navigation
- ../JAVAFX-DESKTOP.md — eight desktop layouts, state handling and responsive rules
- ../API-MAPPING.md — proposed Spring Boot REST/WebSocket contracts
- ../ACCESSIBILITY.md — native acceptance checklist
- ../DESIGN-SYSTEM.md — shared state model/component inventory; apply this handoff's newer visual tokens

Earlier Sleek HTML/renders and REVIEW-STATUS.md are archived first-pass artifacts; their credit blocker does not apply to this Framer project. Framer exports here include native canvas designs and React component source, not newly generated Sleek HTML or a native Java application.
