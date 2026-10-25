# BioPulse device simulator console

This static HTML/CSS/JavaScript console controls the shared Spring Boot backend. It does not contain demo waveform generators or a separate database.

Run the backend on `http://localhost:8080`, then serve this directory on an allowed origin:

```powershell
cd web-simulator
python -m http.server 5173
```

Open `http://localhost:5173`, sign in as an administrator, import or select a BIDMC recording, select an active admission, create a session, then start it. The backend owns ingestion, JDBC persistence, alerts, and broadcast. The console subscribes to `/ws/monitoring?admissionId=…` and refreshes authoritative REST state after reconnect. `?api=http://host:port` can select another backend origin if `ALLOWED_ORIGINS` includes the console origin.

## Import a recording

The **Import BIDMC recording** panel calls the ADMIN-only `POST /api/v1/datasets/bidmc/import` route. Enter a `bidmc_##` recording ID, choose the ECG lead (`II`, `V` or `AVR`), select an active admission, and map each required channel (`ECG`, `PPG`, `RESP`, `HR`, `PULSE`, `RR`, `SPO2`) to a capable device. The console preselects the first capable visible device for each channel; a `MULTI` device can serve every channel. Every mapped device must be assigned to the admission. The backend validates capabilities and assignment, resolves files beneath `BIOPULSE_BIDMC_DATASET_ROOT` on the server, and rejects a repeated recording ID.

The shared replay service then reads stored source rows, writes a separate simulation source/recording that references the original, and preserves source-relative observation times. Historical import does not emit live alerts; replay goes through the normal ingestion path and does emit them.

## Dataset source

Download BIDMC CSV files from [PhysioNet](https://physionet.org/content/bidmc/1.0.0/) into a server-side directory. A recording needs matching `bidmc_##_Signals.csv`, `bidmc_##_Numerics.csv`, and `bidmc_##_Fix.txt`; `bidmc_##_Breaths.csv` is optional. Do not place source datasets in this repository. Set `BIOPULSE_BIDMC_DATASET_ROOT` to the absolute parent directory on the server; the browser never supplies a filesystem path.

## Scope and limitations

Session history in this console is a local browser list because `/api/v1/simulation-sessions` currently has no list route. The server remains authoritative for each session's state. While a session is selected, vital cards follow replay WebSocket events instead of the global latest-measurement endpoint, because a historical recording's timestamps are older than current fixture data. The device panel lists visible devices, but assignment is validated server-side at import and replay time.

There is currently no backend route to select a synthetic scenario mode, so the console does not falsely present a scenario control. `ScenarioTransformer` remains an unexposed Java-library capability pending a durable session metadata contract.

## Verification

Run `./mvnw.cmd -Dtest=BidmcCsvImporterTest test`. To validate full published `bidmc_01` sample counts, set `BIDMC_CSV_DIR` to the directory containing its four files before running the test. Run `node --check web-simulator/simulator.js` for JavaScript syntax. End-to-end import and replay require a running backend plus a complete CSV set under its configured dataset root.
