let csrf = "";
export async function api(path, options = {}) {
  const r = await fetch("/api/v1" + path, {
    credentials: "same-origin",
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(options.method ? { "X-XSRF-TOKEN": csrf } : {}),
      ...options.headers,
    },
  });
  if (!r.ok) {
    let p = {};
    try {
      p = await r.json();
    } catch {}
    const e = new Error(p.detail || p.title || `Request failed (${r.status})`);
    e.status = r.status;
    if (r.status === 401) window.dispatchEvent(new Event("session-expired"));
    throw e;
  }
  if (r.status === 204) return null;
  const data = await r.json();
  if (data.csrfToken) csrf = data.csrfToken;
  return data;
}
export const post = (path, body = {}) =>
  api(path, { method: "POST", body: JSON.stringify(body) });
export const stamp = (v) =>
  v
    ? new Date(v).toLocaleString("en-IN", {
        timeZone: "Asia/Kolkata",
        dateStyle: "medium",
        timeStyle: "medium",
      }) + " IST"
    : "Unavailable";
export const names = {
  HR: "Heart rate",
  PULSE: "Pulse rate",
  SPO2: "Oxygen saturation",
  RR: "Respiratory rate",
  SBP: "Systolic pressure",
  DBP: "Diastolic pressure",
  TEMP: "Temperature",
  ECG: "ECG · Lead II",
  PPG: "PPG · Finger",
  RESP: "Respiration",
};
export const initialRange = {
  from: "2026-10-25T08:30:00Z",
  to: "2026-10-25T08:38:01Z",
};
export const query = (r) => new URLSearchParams(r).toString();

export const signalRange = { ...initialRange, to: "2026-10-25T08:30:10Z" };
