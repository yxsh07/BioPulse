import React, { useState, useEffect } from "react";
import { createRoot } from "react-dom/client";
import {
  Activity,
  ArrowUpRight,
  Users,
  Bell,
  UserRound,
  LogOut,
} from "lucide-react";
import { api, post } from "./api";
import { useAction, ErrorNote, Panel, State, go } from "./components";
import { Directory, Patient } from "./patients";
import { Monitoring, Trends } from "./signals";
import { Alerts, AlertDetail, Reports, Devices, Audit } from "./workflows";
import "./styles.css";
const titles = {
  overview: "Command center",
  patients: "Active patients",
  patient: "Patient workstation",
  monitoring: "Signal monitoring",
  trends: "Historical trends",
  alerts: "Alert center",
  "alert-detail": "Event information",
  report: "Monitoring reports",
  profile: "Profile & preferences",
  devices: "Device administration",
  audit: "Audit trail",
};
function useRoute() {
  const read = () => {
    const [p, q = ""] = location.hash.slice(2).split("?");
    return { page: p || "overview", params: new URLSearchParams(q) };
  };
  const [r, set] = useState(read);
  useEffect(() => {
    const fn = () => set(read());
    addEventListener("hashchange", fn);
    return () => removeEventListener("hashchange", fn);
  }, []);
  return r;
}
function Login({ onLogin }) {
  const [username, setUser] = useState(""),
    [password, setPassword] = useState(""),
    [visible, setVisible] = useState(false),
    a = useAction();
  return (
    <div className="login">
      <div className="login-art">
        <Activity size={48} strokeWidth={1} />
        <div>
          <div className="eyebrow">BIOMEDICAL INTELLIGENCE</div>
          <h1>
            Clarity in
            <br />
            every signal.
          </h1>
          <p>
            A shared view of observations, signals and research.
            <br />
            Built around the patient. Grounded in the data.
          </p>
        </div>
        <span className="eyebrow">ACADEMIC WORKSPACE / RESEARCH ONLY</span>
      </div>
      <main className="login-form">
        <div className="brand">
          <Activity size={22} />
          BIOPULSE
        </div>
        <div className="eyebrow">SECURE ACCESS</div>
        <h1>Welcome back.</h1>
        <p>Sign in to your monitoring workspace.</p>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            a.run(async () => {
              await post("/session", { username, password });
              setPassword("");
              onLogin(await api("/session"));
            });
          }}
        >
          <label>
            Staff email
            <input
              type="email"
              autoComplete="username"
              required
              value={username}
              onChange={(e) => setUser(e.target.value)}
              placeholder="doctor@biopulse.local"
            />
          </label>
          <label>
            Password
            <input
              autoComplete="current-password"
              required
              type={visible ? "text" : "password"}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </label>
          <label className="check">
            <input
              type="checkbox"
              checked={visible}
              onChange={(e) => setVisible(e.target.checked)}
            />
            Show password
          </label>
          <button className="primary" disabled={a.busy}>
            {a.busy ? "Signing in…" : "Enter workspace"}
            <ArrowUpRight size={16} />
          </button>
          <ErrorNote message={a.message} />
        </form>
        <div className="notice">
          Authorized academic access. Demo accounts exist only when the server’s
          opt-in seed is enabled. Use the password configured by your
          administrator.
        </div>
      </main>
    </div>
  );
}
function Profile({ session, onLogout, theme, setTheme }) {
  return (
    <>
      <Panel title={session.username} tag="AUTHENTICATED STAFF">
        <p>{session.roles.join(" / ").replaceAll("ROLE_", "")}</p>
        <p>
          Ward and admission access is enforced by the server for REST requests
          and streaming subscriptions.
        </p>
        <button onClick={onLogout}>
          <LogOut size={14} />
          Sign out
        </button>
      </Panel>
      <Panel title="Workspace preferences" tag="THIS BROWSER">
        <label className="preference">
          <div>
            <strong>Appearance</strong>
            <small>Stored locally on this device</small>
          </div>
          <select value={theme} onChange={(e) => setTheme(e.target.value)}>
            <option value="dark">Dark</option>
            <option value="light">Light</option>
          </select>
        </label>
        <p>
          Sessions expire after 30 minutes of inactivity. Research alerts are
          workflow notifications without clinical validation.
        </p>
      </Panel>
      <Panel title="Connected workspaces">
        <div className="toolbar">
          <button onClick={() => go("devices")}>
            Devices
            <ArrowUpRight size={14} />
          </button>
          {session.roles.includes("ROLE_ADMIN") && (
            <button onClick={() => go("audit")}>
              Audit trail
              <ArrowUpRight size={14} />
            </button>
          )}
        </div>
      </Panel>
    </>
  );
}
function App() {
  const route = useRoute(),
    [session, setSession] = useState(null),
    [ready, setReady] = useState(false),
    [bootError, setBootError] = useState(null),
    [admission, setAdmissionState] = useState(
      Number(route.params.get("admission")) || 1,
    ),
    [theme, setTheme] = useState(
      localStorage.getItem("biopulse-theme") || "dark",
    );
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem("biopulse-theme", theme);
  }, [theme]);
  const boot = () => {
    setBootError(null);
    api("/session")
      .then(setSession)
      .catch(setBootError)
      .finally(() => setReady(true));
  };
  useEffect(() => {
    boot();
  }, []);
  useEffect(() => {
    const fn = () => {
      setSession(null);
    };
    addEventListener("session-expired", fn);
    return () => removeEventListener("session-expired", fn);
  }, []);
  useEffect(() => {
    const a = Number(route.params.get("admission"));
    if (a) setAdmissionState(a);
    window.scrollTo(0, 0);
  }, [route.page, route.params.toString()]);
  const setAdmission = (a) => {
    setAdmissionState(a);
    go(route.page, { admission: a });
  };
  if (!ready) return <State loading />;
  if (bootError) return <State error={bootError} reload={boot} />;
  if (!session?.authenticated) return <Login onLogin={setSession} />;
  const admin = session.roles.includes("ROLE_ADMIN"),
    doctor = session.roles.includes("ROLE_DOCTOR"),
    page = titles[route.page] ? route.page : "overview",
    common = { admission, setAdmission, admin };
  return (
    <>
      <header>
        <a className="brand" href="#/overview">
          <Activity size={22} />
          BIOPULSE
        </a>
        <div className="header-meta">
          <span>RESEARCH WORKSPACE / AUTHENTICATED</span>
          <button aria-label="Open profile" onClick={() => go("profile")}>
            <UserRound size={17} />
            <span>{session.username.split("@")[0]}</span>
          </button>
        </div>
      </header>
      <main className="workspace">
        <div className="page-heading">
          <div>
            <div className="eyebrow">
              BIOMEDICAL INTELLIGENCE /{" "}
              {new Date().toLocaleDateString("en-IN", {
                day: "2-digit",
                month: "short",
                year: "numeric",
              })}
            </div>
            <h1>{titles[page]}</h1>
          </div>
          <button
            className="icon-button"
            aria-label="Open monitoring"
            onClick={() => go("monitoring", { admission })}
          >
            <Activity size={22} />
          </button>
        </div>
        <div className="source-strip">
          <span className="source-dot" />
          ACADEMIC PLATFORM / PROVENANCE PRESERVED / RESEARCH ONLY
        </div>
        {page === "overview" && <Directory overview admin={admin} />}{" "}
        {page === "patients" && <Directory admin={admin} />}{" "}
        {page === "patient" && <Patient {...common} />}{" "}
        {page === "monitoring" && <Monitoring {...common} />}{" "}
        {page === "trends" && <Trends {...common} />}{" "}
        {page === "alerts" && <Alerts admin={admin} doctor={doctor} />}{" "}
        {page === "alert-detail" && (
          <AlertDetail id={Number(route.params.get("id")) || 1} />
        )}{" "}
        {page === "report" && <Reports {...common} />}{" "}
        {page === "devices" && <Devices admin={admin} />}{" "}
        {page === "audit" &&
          (admin ? (
            <Audit />
          ) : (
            <State error={new Error("Administrator access required")} />
          ))}{" "}
        {page === "profile" && (
          <Profile
            session={session}
            theme={theme}
            setTheme={setTheme}
            onLogout={async () => {
              try {
                await post("/logout");
                setSession(null);
                await api("/session");
              } catch (e) {
                setBootError(e);
              }
            }}
          />
        )}
        <footer>
          Academic prototype. Source records distinguish devices, datasets and
          simulation. Experimental outputs are RESEARCH_ONLY. No treatment
          instructions.
        </footer>
      </main>
      <nav aria-label="Main navigation">
        {[
          ["overview", Activity, "Overview"],
          ["patients", Users, "Patients"],
          ["alerts", Bell, "Alerts"],
          ["profile", UserRound, "Profile"],
        ].map(([p, Icon, label]) => (
          <a
            key={p}
            href={"#/" + p}
            aria-current={page === p ? "page" : undefined}
          >
            <Icon size={18} />
            <span>{label}</span>
          </a>
        ))}
      </nav>
    </>
  );
}
createRoot(document.getElementById("root")).render(<App />);
