# BioPulse Research Paper — IEEE Standard

This directory contains the complete IEEE-formatted research paper for **BioPulse: An Object-Oriented Biomedical Intelligence Platform for High-Cadence Physiological Signal Processing and Telemetric Clinical Monitoring**.

The paper is structured for a second-year undergraduate research level, maintaining academic rigor, mathematical formulations, software design patterns, and empirical benchmarks based on the BioPulse Java 21 / Spring Boot / JDBC / MySQL 8 system.

---

## Files in this Directory

| File | Description | Compilation Mode |
| --- | --- | --- |
| `main.tex` | Complete IEEEtran LaTeX paper using external BibTeX bibliography | `pdflatex` + `bibtex` + `pdflatex` $\times 2$ |
| `references.bib` | 20 peer-reviewed academic references in BibTeX format | Used by `main.tex` |
| `main_standalone.tex` | Self-contained IEEEtran LaTeX paper with embedded `\begin{thebibliography}` | Single-pass `pdflatex` (no BibTeX needed) |

---

## How to Compile

### Option 1: Overleaf (Recommended for easy editing and PDF export)
1. Compress this `paper` folder into a `.zip` archive (or drag and drop the files).
2. Go to [Overleaf](https://www.overleaf.com), click **New Project** > **Upload Project**.
3. Select `main.tex` (or `main_standalone.tex`) as the root document and click **Recompile**.
4. The IEEEtran template will compile automatically into a 2-column IEEE journal/conference PDF.

### Option 2: Local Command Line (TeX Live / MiKTeX / MacTeX)
Using `latexmk` (automates passes):
```bash
latexmk -pdf main.tex
```

Using standard `pdflatex` and `bibtex`:
```bash
pdflatex main.tex
bibtex main
pdflatex main.tex
pdflatex main.tex
```

Using the standalone single-pass file:
```bash
pdflatex main_standalone.tex
```

---

## Inserting Mobile App Screenshots Later

When your Android screenshots are ready:
1. Place your PNG/JPG image in this `paper/` directory (e.g., `paper/mobile_dashboard.png`).
2. In `main.tex` (or `main_standalone.tex`), locate Section VII-C (*Mobile Remote Companion Architecture*) and insert:

```latex
\begin{figure}[!t]
\centering
\includegraphics[width=0.85\columnwidth]{mobile_dashboard.png}
\caption{BioPulse native Android companion interface: (a) Ward triage overview, (b) Patient real-time telemetry card, and (c) Persistent alarm notification with one-tap acknowledgment.}
\label{fig:mobile_ui}
\end{figure}
```

---

## 20 Academic References Included

1. **Goldberger et al. (2000)** — *Circulation* (PhysioNet foundation)
2. **Pimentel et al. (2017)** — *IEEE TBME* (BIDMC PPG & Respiration Dataset)
3. **Yang et al. (2024)** — *JMIR Human Factors* (ICU Vital-Sign Viewer & situational awareness)
4. **Xu et al. (2025)** — *Critical Care Medicine* (ICU alarm fatigue systematic review)
5. **Royal College of Physicians (2017)** — *NEWS2 Report* (Early warning assessment)
6. **Yuan et al. (2025)** — *The Lancet Digital Health* (AI early warning systems review)
7. **HL7 International (2020)** — *FHIR R4* (Vital Signs Profile Specification)
8. **Walonoski et al. (2018)** — *JAMIA* (Synthea synthetic EHR generator)
9. **Lee et al. (2022)** — *Scientific Data* (VitalDB high-fidelity perioperative dataset)
10. **Reyna et al. (2020)** — *Crit Care Med* (PhysioNet/CinC Challenge 2019 Sepsis)
11. **Imhoff & Kuhls (2006)** — *Anesth Analg* (Alarm algorithms and false alarm reduction)
12. **Gamma et al. (1994)** — *GoF Design Patterns* (Strategy, Factory, Observer)
13. **Bloch (2018)** — *Effective Java* (Immutability, defensive copying, records)
14. **Walls (2022)** — *Spring in Action* (Spring Boot & enterprise Java)
15. **Stonebraker & Cetintemel (2005)** — *ICDE* (Relational vs Specialized Time-Series engines)
16. **Fowler (2002)** — *Patterns of Enterprise Application Architecture* (DAO & Impedance Mismatch)
17. **Fette & Melnikov (2011)** — *IETF RFC 6455* (WebSocket Protocol)
18. **Görges et al. (2009)** — *Anesth Analg* (Delay and deadband algorithms in ICU alarms)
19. **Bleyer et al. (2022)** — *IEEE TBCAS* (ICU Cockpit multimodal waveform acquisition)
20. **ISO/IEEE (2019)** — *ISO/IEEE 11073-10101* (Point-of-care medical device nomenclature)
