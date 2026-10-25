import React, { useState, useEffect } from "react";
import {
  ArrowUpRight,
  ArrowLeft,
  Check,
  RefreshCw,
  Download,
  Plus,
  Shield,
} from "lucide-react";
import { api, post, names, stamp } from "./api";
import {
  useData,
  useAction,
  ErrorNote,
  Status,
  Panel,
  State,
  Pager,
  Range,
  go,
} from "./components";
import { AdmissionSelect } from "./patients";
import { useReplay, ReplaySelection } from "./replay";
function AlertRows({ alerts }) {
  return (
    <div className="event-list">
      {alerts.map((a) => (
        <button
          className="event-row"
          key={a.id}
          onClick={() => go("alert-detail", { id: a.id })}
        >
          <div className={"severity-marker " + a.severity.toLowerCase()} />
          <div>
            <Status state={a.severity} />
            <h3>{names[a.metric]} threshold event</h3>
            <p>
              Admission {a.admissionId} · {a.value ?? "Missing"} {a.unit}
            </p>
          </div>
          <div className="event-meta">
            <Status state={a.state} />
            <small>{stamp(a.triggeredAt)}</small>
          </div>
          <ArrowUpRight size={16} />
        </button>
      ))}
    </div>
  );
}
export function Alerts({ admin, doctor }) {
  const [page, setPage] = useState(0),
    [severity, setSeverity] = useState(""),
    [workflow, setWorkflow] = useState("");
  const s = useData(() => api("/alerts?size=100&page=" + page), [page]);
  useEffect(() => {
    const timer = setInterval(s.reload, 5000);
    return () => clearInterval(timer);
  }, []);
  return (
    <>
      <div className="toolbar">
        <label>
          Severity
          <select
            value={severity}
            onChange={(e) => setSeverity(e.target.value)}
          >
            <option value="">All severities</option>
            {["CRITICAL", "WARNING", "INFO"].map((x) => (
              <option key={x}>{x}</option>
            ))}
          </select>
        </label>
        <label>
          State
          <select
            value={workflow}
            onChange={(e) => setWorkflow(e.target.value)}
          >
            <option value="">All states</option>
            {["OPEN", "ACKNOWLEDGED", "RESOLVED"].map((x) => (
              <option key={x}>{x}</option>
            ))}
          </select>
        </label>
        <button onClick={s.reload}>
          <RefreshCw size={14} />
          Refresh
        </button>
        <small>Filters apply to this page</small>
      </div>
      {!s.data ? (
        <State {...s} />
      ) : (
        <Panel title="Monitoring events" tag="DETERMINISTIC RULES">
          <AlertRows
            alerts={s.data.items.filter(
              (a) =>
                (!severity || a.severity === severity) &&
                (!workflow || a.state === workflow),
            )}
          />
          {!s.data.items.length && <State empty />}
          <Pager data={s.data} page={page} setPage={setPage} />
        </Panel>
      )}
      {(admin || doctor) && <RuleForm />}
    </>
  );
}
function RuleForm() {
  const a = useAction(),
    [saved, setSaved] = useState(false);
  return (
    <Panel title="Configure an admission rule" tag="DOCTOR / ADMIN">
      <form
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault();
          const f = new FormData(e.currentTarget);
          a.run(async () => {
            await post("/alert-rules", {
              admissionId: Number(f.get("admission")),
              metric: f.get("metric"),
              lowerBound: f.get("low") === "" ? null : Number(f.get("low")),
              upperBound: f.get("high") === "" ? null : Number(f.get("high")),
              persistenceSeconds: Number(f.get("duration")),
              maxGapSeconds: Number(f.get("gap")),
              severity: f.get("severity"),
            });
            setSaved(true);
          });
        }}
      >
        <label>
          Admission ID
          <input name="admission" type="number" min="1" required />
        </label>
        <label>
          Metric
          <select name="metric">
            {["HR", "SPO2", "RR", "SBP", "DBP", "TEMP", "PULSE"].map((m) => (
              <option key={m}>{m}</option>
            ))}
          </select>
        </label>
        <label>
          Lower bound
          <input name="low" type="number" step="any" />
        </label>
        <label>
          Upper bound
          <input name="high" type="number" step="any" />
        </label>
        <label>
          Persistence (seconds)
          <input
            name="duration"
            type="number"
            min="0"
            max="86400"
            defaultValue="20"
            required
          />
        </label>
        <label>
          Maximum evidence gap (seconds)
          <input
            name="gap"
            type="number"
            min="1"
            max="3600"
            defaultValue="15"
            required
          />
        </label>
        <label>
          Severity
          <select name="severity">
            <option>WARNING</option>
            <option>CRITICAL</option>
            <option>INFO</option>
          </select>
        </label>
        <button disabled={a.busy}>Save admission rule</button>
      </form>
      <ErrorNote message={a.message} />
      {saved && (
        <p role="status">Rule saved. It applies to subsequent ingestion.</p>
      )}
    </Panel>
  );
}
export function AlertDetail({ id }) {
  const s = useData(() => api("/alerts/" + id), [id]),
    action = useAction();
  if (!s.data) return <State {...s} />;
  const { alert: a, events, rules } = s.data;
  return (
    <>
      <button onClick={() => go("alerts")}>
        <ArrowLeft size={14} />
        All events
      </button>
      <Panel
        title={names[a.metric] + " event / " + a.id}
        tag={"VERSION " + a.version}
      >
        <div className="toolbar">
          <Status state={a.severity} />
          <Status state={a.state} />
        </div>
        <div className="hero-number">
          {a.value ?? "—"}
          <small>{a.unit}</small>
        </div>
        <p>
          Admission {a.admissionId} · {stamp(a.triggeredAt)} · {a.quality}{" "}
          quality · source {a.sourceId}
        </p>
        {rules.map((r) => (
          <p key={r.id}>
            Rule {r.id}: {r.lowerBound != null ? `below ${r.lowerBound}` : ""}{" "}
            {r.upperBound != null ? `above ${r.upperBound}` : ""} {a.unit} for{" "}
            {r.persistenceSeconds}s, evidence gaps ≤ {r.maxGapSeconds}s.
          </p>
        ))}
        <div className="toolbar">
          {["acknowledge", "resolve"].map((x) => (
            <button
              key={x}
              disabled={
                action.busy ||
                a.state === "RESOLVED" ||
                (x === "acknowledge" && a.state === "ACKNOWLEDGED")
              }
              onClick={() =>
                action.run(async () => {
                  await post(`/alerts/${id}/${x}`, {
                    expectedVersion: a.version,
                  });
                  s.reload();
                })
              }
            >
              <Check size={14} />
              {x}
            </button>
          ))}
          <button onClick={() => go("patient", { admission: a.admissionId })}>
            Open workstation
            <ArrowUpRight size={14} />
          </button>
        </div>
        <ErrorNote message={action.message} />
      </Panel>
      <Panel title="Event timeline">
        {events.map((e) => (
          <div className="audit-row" key={e.id}>
            <Check size={16} />
            <div>
              <strong>{e.action}</strong>
              <small>
                {e.actor} · {stamp(e.occurredAt)}
              </small>
            </div>
          </div>
        ))}
      </Panel>
    </>
  );
}
export function Reports({ admission, setAdmission }) {
  const replay = useReplay();
  const [customRange, setRange] = useState(null),
    [scope, setScope] = useState("admission"),
    [ward, setWard] = useState(1),
    [page, setPage] = useState(0);
  useEffect(() => setRange(null), [admission, replay.recordingId]);
  const range = customRange || replay.range;
  const s = useData(() => api("/reports?page=" + page), [page]),
    a = useAction();
  return (
    <>
      <ReplaySelection />
      <Panel title="Generate a report" tag="STORED CSV SNAPSHOT">
        <div className="toolbar">
          <label>
            Scope
            <select value={scope} onChange={(e) => setScope(e.target.value)}>
              <option value="admission">Admission</option>
              <option value="ward">Ward</option>
            </select>
          </label>
          {scope === "admission" ? (
            <AdmissionSelect
              admission={admission}
              setAdmission={setAdmission}
            />
          ) : (
            <label>
              Ward ID
              <input
                type="number"
                min="1"
                value={ward}
                onChange={(e) => setWard(Number(e.target.value))}
              />
            </label>
          )}
        </div>
        <Range range={range} setRange={setRange} />
        <button
          className="primary"
          disabled={a.busy}
          onClick={() =>
            a.run(async () => {
              await post("/reports", {
                admissionId: scope === "admission" ? admission : null,
                wardId: scope === "ward" ? ward : null,
                ...range,
                recordingId: scope === "admission" ? replay.recordingId : null,
              });
              s.reload();
            })
          }
        >
          <Plus size={14} />
          Generate report
        </button>
        <ErrorNote message={a.message} />
        <p>
          Numerical statistics, coverage gaps and source provenance. Educational
          exports without clinical validation.
        </p>
      </Panel>
      {!s.data ? (
        <State {...s} />
      ) : (
        <Panel title="Report archive">
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Report</th>
                  <th>Scope / window</th>
                  <th>Created</th>
                  <th>Export</th>
                </tr>
              </thead>
              <tbody>
                {s.data.items.map((r) => (
                  <tr key={r.id}>
                    <td>BP-{String(r.id).padStart(4, "0")}</td>
                    <td>
                      {r.admissionId
                        ? "Admission " + r.admissionId
                        : "Ward " + r.wardId}
                      <small>
                        {stamp(r.from)} → {stamp(r.to)}
                      </small>
                    </td>
                    <td>
                      {stamp(r.createdAt)}
                      <small>{r.createdBy}</small>
                    </td>
                    <td>
                      <a
                        className="button"
                        href={"/api/v1/reports/" + r.id + "/download"}
                      >
                        <Download size={14} />
                        CSV
                      </a>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {!s.data.items.length && <State empty />}
          <Pager data={s.data} page={page} setPage={setPage} />
        </Panel>
      )}
    </>
  );
}
export function Devices({ admin }) {
  const s = useData(() => api("/devices")),
    a = useAction();
  return (
    <>
      {!s.data ? (
        <State {...s} />
      ) : (
        <Panel title="Monitoring devices">
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Device</th>
                  <th>Capabilities</th>
                  <th>Connection</th>
                </tr>
              </thead>
              <tbody>
                {s.data.map((d) => (
                  <tr key={d.id}>
                    <td>
                      {d.serial}
                      <small>ID {d.id}</small>
                    </td>
                    <td>{d.adapterType}</td>
                    <td>
                      <Status state={d.online ? "ONLINE" : "OFFLINE"} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Panel>
      )}
      {admin && (
        <Panel title="Register and assign" tag="ADMIN">
          <form
            className="form-grid"
            onSubmit={(e) => {
              e.preventDefault();
              const f = new FormData(e.currentTarget);
              a.run(async () => {
                await post("/devices", {
                  serial: f.get("serial"),
                  adapterType: f.get("type"),
                });
                s.reload();
              });
            }}
          >
            <label>
              Serial
              <input name="serial" required />
            </label>
            <label>
              Adapter
              <select name="type">
                {[
                  "MULTI",
                  "ECG",
                  "OXIMETER",
                  "RESPIRATORY",
                  "NIBP",
                  "TEMPERATURE",
                ].map((x) => (
                  <option key={x}>{x}</option>
                ))}
              </select>
            </label>
            <button disabled={a.busy}>Register device</button>
          </form>
          <form
            className="form-grid"
            onSubmit={(e) => {
              e.preventDefault();
              const f = new FormData(e.currentTarget);
              a.run(async () => {
                await post("/device-assignments", {
                  deviceId: Number(f.get("device")),
                  admissionId: Number(f.get("admission")),
                });
                s.reload();
              });
            }}
          >
            <label>
              Device ID
              <input name="device" type="number" min="1" required />
            </label>
            <label>
              Admission ID
              <input name="admission" type="number" min="1" required />
            </label>
            <button disabled={a.busy}>Assign device</button>
          </form>
          <ErrorNote message={a.message} />
        </Panel>
      )}
    </>
  );
}
export function Audit() {
  const [page, setPage] = useState(0),
    s = useData(() => api("/audit?page=" + page), [page]);
  return !s.data ? (
    <State {...s} />
  ) : (
    <Panel title="Accountability log">
      {s.data.items.map((e) => (
        <div className="audit-row" key={e.id}>
          <Shield size={16} />
          <div>
            <strong>{e.action}</strong>
            <small>
              {e.actor} · {e.resource} · {stamp(e.occurredAt)}
            </small>
          </div>
        </div>
      ))}
      <Pager data={s.data} page={page} setPage={setPage} />
    </Panel>
  );
}
