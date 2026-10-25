import React, { useEffect, useState } from "react";
import { Play, Pause, Square } from "lucide-react";
import {
  api,
  post,
  names,
  stamp,
  signalRange,
  initialRange,
  query,
} from "./api";
import {
  useData,
  useAction,
  ErrorNote,
  Panel,
  State,
  Status,
  Range,
  Chart,
} from "./components";
import { AdmissionSelect } from "./patients";
export function Signals({ admission, range, revision = 0 }) {
  const s = useData(
    () =>
      Promise.all(
        ["ECG", "PPG", "RESP"].map((metric) =>
          api(
            `/admissions/${admission}/waveforms?metric=${metric}&${query(range)}&size=100`,
          ),
        ),
      ),
    [admission, range.from, range.to, revision],
  );
  if (!s.data) return <State {...s} />;
  return s.data.map((data, i) => {
    const metric = ["ECG", "PPG", "RESP"][i],
      points = [];
    for (const { chunk: c } of data.items) {
      if (points.length)
        points.push({ time: Date.parse(c.observedAt) - 1, value: null });
      c.samples.forEach((value, j) =>
        points.push({
          time: Date.parse(c.observedAt) + (j / c.sampleRateHz) * 1000,
          value,
        }),
      );
    }
    return (
      <Panel
        key={metric}
        title={names[metric]}
        tag={data.items[0]?.chunk.quality || "MISSING"}
      >
        <Chart
          points={points}
          gap={1}
          label={metric + " recorded waveform"}
          color={["var(--ecg)", "var(--ppg)", "var(--resp)"][i]}
        />
        <small>
          Recorded chunks · {data.items[0]?.chunk.unit || "unit unavailable"} ·{" "}
          {data.items.length} chunks
          {data.hasNext
            ? " · first page; narrow the range for full display"
            : ""}
        </small>
        <details>
          <summary>Accessible waveform metadata</summary>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Observed</th>
                  <th>Samples</th>
                  <th>Rate</th>
                  <th>Quality</th>
                  <th>Source</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map(({ chunk: c }) => (
                  <tr key={c.id}>
                    <td>{stamp(c.observedAt)}</td>
                    <td>{c.samples.length}</td>
                    <td>{c.sampleRateHz} Hz</td>
                    <td>{c.quality}</td>
                    <td>{c.sourceId}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </details>
      </Panel>
    );
  });
}
export function Monitoring({ admission, setAdmission, admin }) {
  const [range, setRange] = useState(signalRange),
    [session, setSession] = useState(null),
    [recording, setRecording] = useState(1),
    [speed, setSpeed] = useState(1),
    [connection, setConnection] = useState("CONNECTING"),
    [revision, setRevision] = useState(0);
  const a = useAction();
  useEffect(() => {
    let stopped = false,
      socket,
      timer,
      delay = 1000,
      pending = false;
    const connect = () => {
      setConnection("CONNECTING");
      socket = new WebSocket(
        `${location.protocol === "https:" ? "wss:" : "ws:"}//${location.host}/ws/monitoring?admissionId=${admission}`,
      );
      socket.onopen = () => {
        delay = 1000;
        setConnection("CONNECTED");
        setRevision((n) => n + 1);
      };
      socket.onmessage = (e) => {
        const m = JSON.parse(e.data);
        if (m.type === "SIMULATION") setSession(m.payload);
        if (["READING", "WAVEFORM"].includes(m.type) && !pending) {
          pending = true;
          timer = setTimeout(() => {
            pending = false;
            setRevision((n) => n + 1);
          }, 1000);
        }
      };
      socket.onclose = () => {
        if (!stopped) {
          setConnection("DISCONNECTED");
          timer = setTimeout(connect, delay);
          delay = Math.min(delay * 2, 30000);
        }
      };
      socket.onerror = () => socket.close();
    };
    connect();
    return () => {
      stopped = true;
      clearTimeout(timer);
      socket?.close();
    };
  }, [admission]);
  return (
    <>
      <div className="toolbar">
        <AdmissionSelect admission={admission} setAdmission={setAdmission} />
        <Status state={connection} />
      </div>
      <div className="notice">
        Authenticated stream · recorded observation time is preserved. A
        reconnect refreshes the REST snapshot.
      </div>
      <Panel title="Recorded playback" tag="RESEARCH ONLY">
        <Range range={range} setRange={setRange} />
        {admin && (
          <>
            <div className="toolbar">
              <label>
                Recording ID
                <input
                  type="number"
                  min="1"
                  value={recording}
                  onChange={(e) => setRecording(Number(e.target.value))}
                />
              </label>
              <label>
                Speed
                <select
                  value={speed}
                  onChange={(e) => setSpeed(Number(e.target.value))}
                >
                  {[0.5, 1, 2, 4].map((x) => (
                    <option key={x} value={x}>
                      {x}×
                    </option>
                  ))}
                </select>
              </label>
              <button
                disabled={a.busy}
                onClick={() =>
                  a.run(async () =>
                    setSession(
                      await post("/simulation-sessions", {
                        admissionId: admission,
                        recordingId: recording,
                        speed,
                      }),
                    ),
                  )
                }
              >
                Create replay session
              </button>
            </div>
            {session && (
              <div className="toolbar">
                <Status state={session.state} />
                <span>
                  Session {session.id} · {stamp(session.cursorAt)}
                </span>
                {[
                  ["start", Play],
                  ["pause", Pause],
                  ["resume", Play],
                  ["stop", Square],
                ].map(([action, Icon]) => (
                  <button
                    key={action}
                    disabled={
                      a.busy ||
                      !{
                        start: ["CREATED"],
                        pause: ["RUNNING"],
                        resume: ["PAUSED"],
                        stop: ["CREATED", "RUNNING", "PAUSED"],
                      }[action].includes(session.state)
                    }
                    onClick={() =>
                      a.run(async () =>
                        setSession(
                          await post(
                            `/simulation-sessions/${session.id}/${action}`,
                          ),
                        ),
                      )
                    }
                  >
                    <Icon size={14} />
                    {action}
                  </button>
                ))}
              </div>
            )}
            <ErrorNote message={a.message} />
          </>
        )}
      </Panel>
      <Signals admission={admission} range={range} revision={revision} />
    </>
  );
}
export function Trends({ admission, setAdmission }) {
  const [range, setRange] = useState(initialRange),
    [metric, setMetric] = useState("HR"),
    [cadence, setCadence] = useState(10),
    [page, setPage] = useState(0);
  const s = useData(
    () =>
      Promise.all([
        api(
          `/admissions/${admission}/analytics?metric=${metric}&cadenceSeconds=${cadence}&${query(range)}`,
        ),
        api(
          `/admissions/${admission}/measurements?metric=${metric}&${query(range)}&size=1000&page=${page}`,
        ),
      ]),
    [admission, metric, range.from, range.to, cadence, page],
  );
  const stats = s.data?.[0],
    history = s.data?.[1];
  return (
    <>
      <div className="toolbar">
        <AdmissionSelect admission={admission} setAdmission={setAdmission} />
        <label>
          Metric
          <select
            value={metric}
            onChange={(e) => {
              setMetric(e.target.value);
              setPage(0);
            }}
          >
            {["HR", "SPO2", "RR", "SBP", "DBP", "TEMP", "PULSE"].map((m) => (
              <option key={m} value={m}>
                {names[m]}
              </option>
            ))}
          </select>
        </label>
      </div>
      <Panel title="Analysis window">
        <Range
          range={range}
          setRange={(r) => {
            setRange(r);
            setPage(0);
          }}
        />
        <label className="inline">
          Expected cadence (seconds)
          <input
            type="number"
            min="1"
            max="3600"
            value={cadence}
            onChange={(e) => setCadence(Number(e.target.value))}
          />
        </label>
      </Panel>
      {!s.data ? (
        <State {...s} />
      ) : (
        <>
          <Panel title={names[metric]} tag={metric}>
            <Chart
              label={names[metric] + " history"}
              points={history.items.map((m) => ({
                time: Date.parse(m.observedAt),
                value: ["GOOD", "FAIR"].includes(m.quality) ? m.value : null,
              }))}
              gap={cadence * 1.5}
            />
            <div className="pager">
              <button disabled={!page} onClick={() => setPage(page - 1)}>
                Previous
              </button>
              <small>Page {page + 1}</small>
              <button
                disabled={!history.hasNext}
                onClick={() => setPage(page + 1)}
              >
                Next
              </button>
            </div>
            <details>
              <summary>Recorded observations · accessible data table</summary>
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Observed (IST)</th>
                      <th>Value</th>
                      <th>Unit</th>
                      <th>Quality</th>
                      <th>Source</th>
                    </tr>
                  </thead>
                  <tbody>
                    {history.items.map((m) => (
                      <tr key={m.id}>
                        <td>{stamp(m.observedAt)}</td>
                        <td>{m.value ?? "Missing"}</td>
                        <td>{m.unit}</td>
                        <td>{m.quality}</td>
                        <td>{m.sourceId}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </details>
          </Panel>
          <div className="stats-grid">
            {[
              ["Mean", stats.summary.mean],
              ["Median", stats.summary.median],
              ["Minimum", stats.summary.minimum],
              ["Maximum", stats.summary.maximum],
              ["Standard deviation", stats.summary.standardDeviation],
              [
                "Available observations",
                stats.summary.availabilityPercent,
                "%",
              ],
            ].map(([n, v, u]) => (
              <Panel key={n}>
                <div className="eyebrow">{n}</div>
                <div className="stat-number">
                  {v == null ? "—" : v.toFixed(2)}
                  <small>{u || ""}</small>
                </div>
              </Panel>
            ))}
          </div>
          <Panel
            title="Data availability"
            tag={stats.summary.count + " USABLE OBSERVATIONS"}
          >
            <p>{stats.aggregationMethod}</p>
            <p>
              Trend slope:{" "}
              {stats.summary.slopePerSecond?.toFixed(4) ?? "Unavailable"}{" "}
              {history.items[0]?.unit || ""}/second ·{" "}
              {stats.summary.fresh ? "Fresh" : "Historical / stale"}
            </p>
            {stats.summary.gaps.map((g, i) => (
              <div className="gap" key={i}>
                <span>Missing</span>
                {stamp(g.from)} → {stamp(g.to)}{" "}
                <strong>{g.seconds.toFixed(1)}s</strong>
              </div>
            ))}
          </Panel>
          <Panel title="Baseline anomaly analysis" tag="RESEARCH_ONLY">
            <div className="stat-number">
              {stats.anomaly.score?.toFixed(2) ?? "Unavailable"}
            </div>
            <p>
              {stats.anomaly.status} · {stats.anomaly.modelVersion}
            </p>
            <small>
              {stats.anomaly.provenance} · {stamp(stats.anomaly.inferredAt)}
            </small>
            <p>
              Statistical deviation from this interval’s preceding baseline. No
              clinical validation or treatment interpretation.
            </p>
          </Panel>
        </>
      )}
    </>
  );
}
