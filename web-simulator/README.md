# BioPulse device simulator console

This static HTML/CSS/JavaScript console controls the shared Spring Boot backend. It does not contain demo waveform generators or a separate database.

Run the backend on `http://localhost:8080`, then serve this directory on an allowed origin:

```powershell
cd web-simulator
python -m http.server 5173
```

Open `http://localhost:5173`, sign in as an administrator, select an imported BIDMC recording and an active admission, create a session, then start it. The backend owns ingestion, JDBC persistence, alerts, and broadcast. The console subscribes to `/ws/monitoring?admissionId=…` and refreshes authoritative REST state after reconnect. `?api=http://host:port` can select another backend origin if `ALLOWED_ORIGINS` includes the console origin.

Session history in this console is a local browser list because `/api/v1/simulation-sessions` currently has no list route. The server remains authoritative for each session's state. The device panel lists visible devices; assignment is checked by the backend at replay creation/ingestion. There is currently no backend route to select synthetic scenario mode, so the console does not falsely present a scenario control.

## Dataset source

Download BIDMC CSV files from [PhysioNet](https://physionet.org/content/bidmc/1.0.0/) into a server-side directory. A recording needs matching `bidmc_##_Signals.csv`, `bidmc_##_Numerics.csv`, and `bidmc_##_Fix.txt`; `bidmc_##_Breaths.csv` is optional. Do not place source datasets in this repository. `BidmcCsvImporter` validates headers and source timing; `BidmcImportService` stores the historical source through shared DAOs. The shared replay service sends its output through `MeasurementIngestionService`.

The import service is a Java service in the shared app, but it is not exposed to the browser until the backend owner implements the import route in [the contract](../docs/SIMULATOR_CONTRACT.md). Import using that agreed route once available. The browser currently discovers recordings already registered in the backend.

## Verification

Run `./mvnw.cmd -Dtest=BidmcCsvImporterTest test`. To validate full published `bidmc_01` sample counts, set `BIDMC_CSV_DIR` to the directory containing its four files before running the test. Run `node --check web-simulator/simulator.js` for JavaScript syntax.
