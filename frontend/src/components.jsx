import React, { useEffect, useState } from "react";
import { Activity, RefreshCw, WifiOff } from "lucide-react";
import { stamp } from "./api";
export const go = (page, params = {}) => {
  location.hash = "/" + page + "?" + new URLSearchParams(params);
};
export function useData(loader, deps = [], refreshDeps = []) {
  const scope = JSON.stringify(deps);
  const [state, set] = useState({
      data: null,
      loading: true,
      error: null,
      scope,
    }),
    [rev, update] = useState(0);
  useEffect(() => {
    let active = true;
    set((s) => ({ ...s, loading: true, error: null }));
    loader()
      .then(
        (data) => active && set({ data, loading: false, error: null, scope }),
      )
      .catch(
        (error) => active && set({ data: null, loading: false, error, scope }),
      );
    return () => {
      active = false;
    };
  }, [...deps, ...refreshDeps, rev]);
  return {
    ...(state.scope === scope
      ? state
      : { data: null, loading: true, error: null }),
    reload: () => update((n) => n + 1),
  };
}
export function useAction() {
  const [busy, setBusy] = useState(false),
    [message, setMessage] = useState("");
  return {
    busy,
    message,
    run: async (fn) => {
      setBusy(true);
      setMessage("");
      try {
        await fn();
      } catch (e) {
        setMessage(e.message);
      } finally {
        setBusy(false);
      }
    },
  };
}
export function ErrorNote({ message }) {
  return message ? (
    <p role="alert" className="error">
      {message}
    </p>
  ) : null;
}
export function Status({ state }) {
  return (
    <span className={"badge " + String(state).toLowerCase()}>{state}</span>
  );
}
export function Panel({ title, tag, children, className = "" }) {
  return (
    <section className={"panel " + className}>
      {title && (
        <div className="panel-head">
          <h2>{title}</h2>
          <span className="eyebrow">{tag}</span>
        </div>
      )}
      {children}
    </section>
  );
}
export function State({ loading, error, reload, empty }) {
  if (loading)
    return (
      <div className="state" role="status">
        <Activity />
        <h2>Loading observations</h2>
        <p>Retrieving your authorized workspace.</p>
      </div>
    );
  if (error)
    return (
      <div className="state" role="alert">
        <WifiOff />
        <h2>Unable to load this workspace</h2>
        <p>{error.message}</p>
        {reload && (
          <button onClick={reload}>
            <RefreshCw size={14} />
            Retry
          </button>
        )}
      </div>
    );
  if (empty)
    return (
      <div className="state">
        <Activity />
        <h2>No observations in this scope</h2>
        <p>
          Choose another admission or interval. Missing data is never replaced.
        </p>
      </div>
    );
  return null;
}
export function Pager({ data, page, setPage }) {
  return (
    <div className="pager">
      <button disabled={!page} onClick={() => setPage(page - 1)}>
        Previous
      </button>
      <small>Page {page + 1}</small>
      <button disabled={!data?.hasNext} onClick={() => setPage(page + 1)}>
        Next
      </button>
    </div>
  );
}
export function Range({ range, setRange }) {
  return (
    <div className="range">
      {["from", "to"].map((k) => (
        <label key={k}>
          {k === "from" ? "From (UTC)" : "To (UTC, exclusive)"}
          <input
            type="datetime-local"
            step="1"
            value={new Date(range[k]).toISOString().slice(0, 19)}
            onChange={(e) =>
              e.target.value &&
              setRange({ ...range, [k]: e.target.value + "Z" })
            }
          />
        </label>
      ))}
    </div>
  );
}
export function Chart({ points, label, color = "var(--ecg)", gap = 15 }) {
  const valid = points.filter(
    (p) => p.value != null && Number.isFinite(p.value),
  );
  if (!valid.length) return <State empty />;
  const lo = Math.min(...valid.map((p) => p.value)),
    hi = Math.max(...valid.map((p) => p.value)),
    start = points[0].time,
    end = points.at(-1).time;
  let prev = null;
  const d = points
    .map((p) => {
      if (p.value == null) {
        prev = null;
        return "";
      }
      const op = prev && p.time - prev.time <= gap * 1000 ? "L" : "M";
      prev = p;
      return `${op}${(16 + ((p.time - start) / (end - start || 1)) * 968).toFixed(2)},${(130 - ((p.value - lo) / (hi - lo || 1)) * 105).toFixed(2)}`;
    })
    .join(" ");
  return (
    <figure className="chart">
      <svg
        role="img"
        aria-label={label}
        viewBox="0 0 1000 150"
        preserveAspectRatio="none"
      >
        <defs>
          <pattern
            id="grid"
            width="40"
            height="30"
            patternUnits="userSpaceOnUse"
          >
            <path d="M40 0H0V30" fill="none" stroke="var(--grid)" />
          </pattern>
        </defs>
        <rect width="1000" height="150" fill="url(#grid)" />
        <path
          d={d}
          fill="none"
          stroke={color}
          strokeWidth="2"
          vectorEffect="non-scaling-stroke"
        />
      </svg>
      <figcaption>
        <span>{stamp(start)}</span>
        <span>
          {lo.toFixed(1)} – {hi.toFixed(1)} · gaps remain visible
        </span>
        <span>{stamp(end)}</span>
      </figcaption>
    </figure>
  );
}
