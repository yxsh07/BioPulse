# Accessibility and responsive acceptance checklist

This is an implementation acceptance checklist; native screen reader, contrast, touch and keyboard checks are not claimed as executed against a native app.

## Shared

- [ ] Verify text contrast ≥4.5:1 for normal text and ≥3:1 for large text in both themes; essential graphical boundaries/icons ≥3:1 against their actual adjacent surfaces. Test generated Sleek colors separately from the token baseline.
- [ ] Every severity, quality, source, connection and workflow state includes text/icon; color alone never conveys meaning.
- [ ] Label all six vital names and units explicitly. Screen readers announce value, unit, observed time, source and freshness as a meaningful group.
- [ ] Charts provide names, units, interval, coverage, gaps, event counts and an equivalent navigable table/summary. Do not announce every incoming sample.
- [ ] Missing values are “No measurement”; stale values announce age; replay values announce recording position rather than imply live data.
- [ ] Do not read a waveform as a decorative unlabeled image. Provide lead, scale, quality, source and selected interval descriptions.
- [ ] Error/status announcements use appropriate live-region equivalents without repeated interruption. Focus stays stable as samples and event lists update.
- [ ] Preserve user-selected range, search, scope, list scroll and selected record across navigation and reconnect.
- [ ] Use reduced motion and non-flashing status styles; avoid alarm blinking and autoplaying decorative motion.
- [ ] All experimental outputs, synthetic identities and replay recordings retain visible provenance in detail and export.

## Android

- [ ] Test 360, 393 and 600dp widths, portrait/landscape, gesture/button navigation insets and keyboard visible.
- [ ] Minimum interactive target 48×48dp; no tiny chart marker is the only route to event details.
- [ ] Test font scale 100%, 150%, 200%; two-column vitals collapse when needed, units and timestamps wrap without truncation.
- [ ] TalkBack order follows header → source/status → content → actions → navigation. Icon-only controls have labels and state descriptions.
- [ ] All controls work with hardware keyboard/switch access; focus outline remains visible on dark and light surfaces.
- [ ] Password toggle announces show/hide state; sign-in failure returns focus to the relevant error without exposing credentials.
- [ ] Filter chips and horizontal rows can scroll to every option; active filter is announced. Bottom navigation never covers final content.
- [ ] Notification controls expose OS permission state and backend policy restrictions, with a clear save/error state.

## JavaFX

- [ ] Validate 1920×1080, 1366×768 and 1024×720 plus Windows scaling 125–200%; no hidden essential columns/actions without an alternate detail route.
- [ ] Test keyboard traversal, Escape/Enter and menus; restoring a drawer returns focus to its trigger.
- [ ] Narrator can read patient identity, vital groups, TableView columns, connection state, errors and audit timeline.
- [ ] At reduced width collapse navigation, then move inspector/event sidebar to a drawer or tab; retain patient/source/age context.
- [ ] Command Center visibly states patient page/count; confirm no impression that only six of twelve are monitored.
- [ ] Print report is legible in grayscale and includes exact coverage/source, units, missing intervals and research disclaimer.

## Data integrity walkthrough

- [ ] An eight-minute recording selected within 24h displays only actual eight-minute observations, with remaining time explicitly unrecorded.
- [ ] Missing 14:04:10–14:04:25 is a break, never a line connecting neighboring samples; 465/480 produces 96.9% coverage.
- [ ] Pausing replay freezes position/time; network disconnect stops apparent progression and shows last receipt/freshness.
- [ ] Physiology, technical disconnect and experimental anomaly remain distinct in counts, filters and detail.
- [ ] Resolved historical event is not counted open; acknowledging does not imply resolution.
- [ ] Server rejection/timeout does not appear successful. Stale caches never appear current after reconnect without resynchronization.
