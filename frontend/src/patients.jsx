import React, { useState, useEffect } from "react";
import { Search, ArrowUpRight, RefreshCw, Plus } from "lucide-react";
import { api, post, names, stamp } from "./api";
import {
  useData,
  useAction,
  ErrorNote,
  Status,
  Panel,
  State,
  Pager,
  go,
} from "./components";
import { Signals } from "./signals";
import { useReplay, ReplaySelection } from "./replay";
export function AdmissionSelect({ admission, setAdmission }) {
  const s = useData(() =>
    Promise.all([api("/admissions?size=100"), api("/patients?size=100")]),
  );
  if (!s.data) return <State {...s} />;
  const [a, p] = s.data;
  return (
    <label className="inline">
      Admission
      <select
        aria-label="Admission"
        value={admission}
        onChange={(e) => setAdmission(Number(e.target.value))}
      >
        {a.items.map((a) => (
          <option key={a.id} value={a.id}>
            {p.items.find((p) => p.id === a.patientId)?.displayName ||
              "Patient " + a.patientId}{" "}
            / Admission {a.id}
          </option>
        ))}
      </select>
    </label>
  );
}
export function Directory({ admin, overview }) {
  const [page, setPage] = useState(0),
    [search, setSearch] = useState(""),
    [ward, setWard] = useState("");
  const s = useData(
    () =>
      Promise.all([
        api("/patients?size=100&page=" + page),
        api("/admissions?size=100"),
        api("/beds"),
        api("/wards"),
        api("/devices"),
        api("/alerts?size=100"),
      ]),
    [page],
  );
  useEffect(() => {
    const timer = setInterval(s.reload, 5000);
    return () => clearInterval(timer);
  }, []);
  if (!s.data) return <State {...s} />;
  const [patients, admissions, beds, wards, devices, alerts] = s.data;
  const open = alerts.items.filter((a) => a.state !== "RESOLVED");
  const filtered = patients.items.filter(
    (p) =>
      (p.displayName + " " + p.code)
        .toLowerCase()
        .includes(search.toLowerCase()) &&
      (!ward ||
        admissions.items.some(
          (a) =>
            a.patientId === p.id &&
            beds.find((b) => b.id === a.bedId)?.wardId === Number(ward),
        )),
  );
  return (
    <>
      {overview && (
        <>
          <div className="summary-grid">
            {[
              [
                admissions.items.filter((a) => a.status === "ACTIVE").length,
                "Active admissions",
                "In your authorized scope",
              ],
              [
                open.filter((a) => a.severity === "CRITICAL").length,
                "Critical events",
                "Open or acknowledged",
              ],
              [
                open.filter((a) => a.severity === "WARNING").length,
                "Warning events",
                "Recorded threshold events",
              ],
              [
                devices.filter((d) => d.online).length + "/" + devices.length,
                "Devices online",
                "Registered connection state",
              ],
            ].map(([v, k, n]) => (
              <Panel key={k}>
                <div className="eyebrow">{k}</div>
                <div className="hero-number">{v}</div>
                <p>{n}</p>
              </Panel>
            ))}
          </div>
          <div className="section-title">
            <h2>Patients at a glance</h2>
            <button onClick={() => go("patients")}>
              View directory
              <ArrowUpRight size={14} />
            </button>
          </div>
        </>
      )}
      <div className="toolbar">
        <label className="search">
          <Search size={16} />
          <input
            aria-label="Search patients on this page"
            placeholder="Search name or patient code"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </label>
        <label className="inline">
          Ward
          <select value={ward} onChange={(e) => setWard(e.target.value)}>
            <option value="">All authorized wards</option>
            {wards.map((w) => (
              <option key={w.id} value={w.id}>
                {w.name}
              </option>
            ))}
          </select>
        </label>
        <small>{filtered.length} on this page</small>
        <button onClick={s.reload} aria-label="Refresh directory">
          <RefreshCw size={16} />
        </button>
      </div>
      <Panel>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Patient</th>
                <th>Location</th>
                <th>Admission</th>
                <th>Workstation</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((p) => {
                const a = admissions.items.find(
                    (a) => a.patientId === p.id && a.status === "ACTIVE",
                  ),
                  b = beds.find((b) => b.id === a?.bedId),
                  w = wards.find((w) => w.id === b?.wardId);
                return (
                  <tr key={p.id}>
                    <td>
                      <strong>{p.displayName}</strong>
                      <small>{p.code}</small>
                    </td>
                    <td>
                      {w?.name || "Unassigned"}
                      <small>{b?.label || "No bed"}</small>
                    </td>
                    <td>
                      <Status state={a?.status || "UNASSIGNED"} />
                    </td>
                    <td>
                      <button
                        aria-label={"Open " + p.displayName}
                        disabled={!a}
                        onClick={() => go("patient", { admission: a.id })}
                      >
                        Open
                        <ArrowUpRight size={14} />
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        {!filtered.length && <State empty />}
      </Panel>
      <Pager data={patients} page={page} setPage={setPage} />
      {(admissions.hasNext || alerts.hasNext) && (
        <p className="notice">
          Counts summarize the first 100 admissions and events in your scope.
        </p>
      )}
      {admin && !overview && <RegistryForms beds={beds} reload={s.reload} />}
      {overview && (
        <Panel title="Priority events" tag="CURRENT WORKFLOW">
          {open.slice(0, 4).map((a) => (
            <button
              className="event-row"
              key={a.id}
              onClick={() => go("alert-detail", { id: a.id })}
            >
              <Status state={a.severity} />
              <strong>{names[a.metric]} threshold</strong>
              <span>Admission {a.admissionId}</span>
              <Status state={a.state} />
              <ArrowUpRight size={16} />
            </button>
          ))}
          {!open.length && <p>No open events.</p>}
        </Panel>
      )}
    </>
  );
}
function RegistryForms({ beds, reload }) {
  const a = useAction();
  return (
    <Panel title="Patient registry" tag="ADMIN">
      <form
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault();
          const f = new FormData(e.currentTarget);
          a.run(async () => {
            await post("/patients", {
              code: f.get("code"),
              displayName: f.get("name"),
              dateOfBirth: f.get("dob") || null,
            });
            reload();
          });
        }}
      >
        <label>
          Patient code
          <input name="code" required maxLength={40} />
        </label>
        <label>
          Display name
          <input name="name" required maxLength={100} />
        </label>
        <label>
          Date of birth (optional)
          <input type="date" name="dob" />
        </label>
        <button disabled={a.busy}>
          <Plus size={14} />
          Register patient
        </button>
      </form>
      <form
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault();
          const f = new FormData(e.currentTarget);
          a.run(async () => {
            await post("/admissions", {
              patientId: Number(f.get("patient")),
              bedId: Number(f.get("bed")),
            });
            reload();
          });
        }}
      >
        <label>
          Registered patient ID
          <input type="number" name="patient" min="1" required />
        </label>
        <label>
          Bed
          <select name="bed">
            {beds.map((b) => (
              <option key={b.id} value={b.id}>
                {b.label}
              </option>
            ))}
          </select>
        </label>
        <button disabled={a.busy}>Create admission</button>
      </form>
      <ErrorNote message={a.message} />
    </Panel>
  );
}
export function Patient({ admission, setAdmission }) {
  const replay = useReplay();
  const s = useData(
    async () => {
      const [a, latest] = await Promise.all([
        api("/admissions/" + admission),
        api(`/admissions/${admission}/measurements/latest?${replay.params}`),
      ]);
      return { a, latest, p: await api("/patients/" + a.patientId) };
    },
    [admission, replay.recordingId],
    [replay.params, replay.revision],
  );
  return (
    <>
      <div className="toolbar">
        <AdmissionSelect admission={admission} setAdmission={setAdmission} />

        <button onClick={s.reload}>
          <RefreshCw size={14} />
          Refresh
        </button>
      </div>
      <ReplaySelection />
      {!replay.ready || !s.data ? (
        <State {...s} loading={!replay.ready || s.loading} />
      ) : (
        <>
          <Panel
            title={s.data.p.code + " / " + s.data.p.displayName}
            tag={"ADMISSION " + admission}
          >
            <p>
              Bed ID {s.data.a.bedId} · {s.data.a.status}
            </p>
            <small>
              Recorded and received timestamps are preserved independently.
            </small>
          </Panel>
          <div className="vitals-grid">
            {["HR", "SPO2", "RR", "SBP", "DBP", "TEMP", "PULSE"].map(
              (metric) => {
                const m = s.data.latest.find((m) => m.metric === metric);
                return (
                  <Panel
                    key={metric}
                    className={"vital " + metric.toLowerCase()}
                  >
                    <div className="eyebrow">{names[metric]}</div>
                    <div className="vital-value">
                      {m?.value == null ? "—" : Number(m.value.toFixed(1))}
                      <span>{m?.unit || ""}</span>
                    </div>
                    <p>{m ? m.quality + " quality" : "No measurement"}</p>
                    <small>
                      {m ? stamp(m.observedAt) : "No recorded observation"}
                    </small>
                    {m && (
                      <small>
                        Source {m.sourceId} ·{" "}
                        {Date.now() - Date.parse(m.observedAt) >= 20000
                          ? "Historical / stale"
                          : "Recent observation"}
                      </small>
                    )}
                  </Panel>
                );
              },
            )}
          </div>
          <div className="toolbar">
            {["monitoring", "trends", "report"].map((p) => (
              <button key={p} onClick={() => go(p, { admission })}>
                {p}
                <ArrowUpRight size={14} />
              </button>
            ))}
          </div>
          <Signals
            admission={admission}
            range={replay.signalRange}
            recordingId={replay.recordingId}
            revision={replay.revision}
          />
        </>
      )}
    </>
  );
}
