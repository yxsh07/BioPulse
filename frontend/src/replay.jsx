import React, {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import { api, stamp } from "./api";
import { useAdmissionStream } from "./live";
import { ErrorNote, Status } from "./components";
const ReplayContext = createContext(null);
export const useReplay = () => useContext(ReplayContext);
export function ReplayProvider({ admission, children }) {
  const emptyWindowEnd = useMemo(() => Date.now(), [admission]);
  const [snapshot, setSnapshot] = useState(null);
  const [error, setError] = useState(null);
  const [selection, setSelection] = useState({});
  const [revision, setRevision] = useState(0);
  const connection = useAdmissionStream(admission, () =>
    setRevision((n) => n + 1),
  );
  useEffect(() => {
    let active = true,
      running = false;
    const refresh = async () => {
      if (running) return;
      running = true;
      try {
        const [sessions, recordings] = await Promise.all([
          api(`/simulation-sessions?size=100&admissionId=${admission}`),
          api(`/admissions/${admission}/recordings`),
        ]);
        if (active) {
          setSnapshot({
            admission,
            sessions: sessions.items.filter((s) => s.admissionId === admission),
            recordings,
          });
          setError(null);
        }
      } catch (e) {
        if (active) setError(e);
      } finally {
        running = false;
      }
    };
    refresh();
    const timer = setInterval(refresh, 2000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [admission, revision]);
  const current = snapshot?.admission === admission ? snapshot : null;
  const mode = selection[admission] || "auto";
  const sessions = current?.sessions || [];
  const session =
    mode === "latest"
      ? null
      : mode === "auto"
        ? sessions[0]
        : sessions.find((s) => String(s.id) === mode);
  const recordings = current?.recordings || [];
  const recording = session
    ? recordings.find((r) => r.id === session.outputRecordingId)
    : [...recordings].sort(
        (a, b) => Date.parse(b.endAt) - Date.parse(a.endAt),
      )[0];
  const recordingId = session?.outputRecordingId;
  const end = session
    ? Math.min(
        Date.parse(session.cursorAt),
        Date.parse(recording?.endAt || session.cursorAt),
      )
    : recording
      ? Date.parse(recording.endAt)
      : emptyWindowEnd;
  const start = recording ? Date.parse(recording.startAt) : end - 60000;
  const windowEnd = Math.max(start + 1, end + (session ? 1 : 0));
  const range = {
    from: new Date(start).toISOString(),
    to: new Date(
      Math.max(start + 1, recording ? Date.parse(recording.endAt) : windowEnd),
    ).toISOString(),
  };
  const signalRange = {
    from: new Date(Math.max(start, windowEnd - 10000)).toISOString(),
    to: new Date(windowEnd).toISOString(),
  };
  const params = recordingId
    ? `recordingId=${recordingId}&at=${encodeURIComponent(session.cursorAt)}`
    : "";
  return (
    <ReplayContext.Provider
      value={{
        admission,
        ready: !!current,
        error,
        connection,
        sessions,
        recordings,
        session,
        recording,
        recordingId,
        range,
        signalRange,
        params,
        revision,
        mode,
        select: (mode) =>
          setSelection((s) => ({ ...s, [admission]: String(mode) })),
        refresh: () => setRevision((n) => n + 1),
      }}
    >
      {children}
    </ReplayContext.Provider>
  );
}
export function ReplaySelection() {
  const r = useReplay();
  const latest = r.sessions[0];
  return (
    <>
      <div className="toolbar">
        <label>
          Replay session
          <select
            aria-label="Replay session"
            value={r.mode}
            onChange={(e) => r.select(e.target.value)}
          >
            <option value="auto">
              Follow newest replay
              {latest ? ` · Session ${latest.id} · ${latest.state}` : " · none"}
            </option>
            <option value="latest">Latest observations (all sources)</option>
            {r.sessions.map((s) => (
              <option key={s.id} value={s.id}>
                Session {s.id} · {s.state} · {s.scenario.mode} ·{" "}
                {stamp(s.createdAt)}
              </option>
            ))}
          </select>
        </label>
        <Status state={r.connection} />
      </div>
      <ErrorNote message={r.error?.message} />
      <div className="notice">
        {r.session
          ? `Replay Session ${r.session.id} · ${r.session.state} · ${r.session.scenario.mode} · recorded cursor ${stamp(r.session.cursorAt)}. `
          : "Latest recorded observations across sources. "}
        {r.recording ? (
          <>
            <strong>
              {r.recording.name} · {r.recording.sourceType}
            </strong>
            <br />
            {r.recording.provenance}
          </>
        ) : (
          "No recording available for this admission."
        )}
        {r.session && (
          <p>
            Replay preserves original observation times. Missing channels remain
            unavailable; fixture values are excluded.
          </p>
        )}
      </div>
    </>
  );
}
