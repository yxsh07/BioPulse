# BioPulse interface system

The current source is `design/framer/HANDOFF.md`. Its tokens supersede the earlier Sleek design.

- Canvas #111111, panel #131313, raised #1b1b1b, boundary #2e2e2e. Foreground #f4f4f1, metadata #aaaaaa.
- Signals: ECG #8bd8a4, PPG #86cce1, respiration #c4b0e7. Warning #efc679, critical #ff9999. Signal colors do not classify patient risk.
- Square borders; no glass, decorative gradients or shadows. 4px spacing base; primary gutters 64px desktop, 20px phone. Panel padding 24px desktop / 20px phone. Gaps 12/16/24px.
- Inter with Segoe UI fallback. Title 38px desktop / 32px phone, reading 48px / 36px, section 21px / 18px, metadata 10–12px. Tabular numerals.
- Button/input minimum height 44px. Focus outline 2px ECG color. Controls use a darker inset surface.
- Panel header has title and right-aligned provenance/state label. Vital tiles show canonical unit, timestamp, quality, source and freshness. Missing data is an em dash with explanation.
- Bottom navigation is 80px desktop / 76px phone; content reserves 120–140px clearance. The monitoring shortcut remains available on phones.
- Waveforms initially show a 10-second range so individual morphology stays readable. Historical trends show the full fixture window. Missing spans break paths.
- Forms, charts and tables have loading/empty/error states. Chart details expose accessible tables. No advancing synthetic trace substitutes for a disconnected stream.
