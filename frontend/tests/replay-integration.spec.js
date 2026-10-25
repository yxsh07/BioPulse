import { test, expect } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";

async function login(page) {
  if (!process.env.DEMO_PASSWORD)
    throw new Error("Set DEMO_PASSWORD for the seeded research server");
  await expect
    .poll(
      async () => {
        try {
          return (
            await page.request.get("/api/v1/health", { timeout: 3000 })
          ).ok();
        } catch {
          return false;
        }
      },
      { timeout: 30000 },
    )
    .toBe(true);
  await page.goto("/");
  await page.getByLabel("Staff email").fill("admin@biopulse.local");
  await page
    .getByLabel("Password", { exact: true })
    .fill(process.env.DEMO_PASSWORD);
  await page.getByRole("button", { name: "Enter workspace" }).click();
  await expect(
    page.getByRole("heading", { name: "Command center" }),
  ).toBeVisible();
}

// A separate authenticated client represents the simulator. The observations are
// deliberately older than the seeded fixture; recorded timestamps must stay old.
async function historicalReplay(request) {
  const bootstrap = await (await request.get("/api/v1/session")).json();
  const auth = await request.post("/api/v1/session", {
    headers: { "X-XSRF-TOKEN": bootstrap.csrfToken },
    data: {
      username: "admin@biopulse.local",
      password: process.env.DEMO_PASSWORD,
    },
  });
  expect(auth.ok(), "Separate simulator client signs in").toBe(true);
  const identity = await (await request.get("/api/v1/session")).json();
  const post = async (path, data = {}) => {
    const response = await request.post("/api/v1" + path, {
      headers: { "X-XSRF-TOKEN": identity.csrfToken },
      data,
    });
    expect(response.ok(), `${path}: ${await response.text()}`).toBe(true);
    return response.json();
  };
  const suffix = randomUUID().slice(0, 8);
  const startAt = "2001-01-01T00:00:00Z";
  const source = await post("/sources", {
    name: `Generated replay regression ${suffix}`,
    type: "DATASET",
    provenance:
      "GENERATED TEST FIXTURE; deterministic historical observations and waveform; not patient data",
  });
  const recording = await post("/recordings", {
    sourceId: source.id,
    name: `Generated historical replay ${suffix}`,
    startAt,
    endAt: "2001-01-01T00:00:20Z",
  });
  const common = {
    admissionId: 1,
    deviceId: 1,
    sourceId: source.id,
    recordingId: recording.id,
    quality: "GOOD",
  };
  await post("/internal/simulator/measurements", [
    {
      ...common,
      eventId: `${suffix}:hr:0`,
      metric: "HR",
      unit: "bpm",
      value: 73,
      observedAt: startAt,
    },
    {
      ...common,
      eventId: `${suffix}:hr:8`,
      metric: "HR",
      unit: "bpm",
      value: 91,
      observedAt: "2001-01-01T00:00:08Z",
    },
  ]);
  await post("/internal/simulator/waveform-chunks", {
    ...common,
    eventId: `${suffix}:ecg:0`,
    metric: "ECG",
    unit: "mV",
    observedAt: startAt,
    sampleRateHz: 10,
    samples: [
      0, 0.1, 0.8, -0.2, 0, 0.1, 0, 0.2, 0, 0, 0, 0.1, 0.8, -0.2, 0, 0.1, 0,
      0.2, 0, 0,
    ],
  });
  return {
    post,
    recording,
    create: () =>
      post("/simulation-sessions", {
        admissionId: 1,
        recordingId: recording.id,
        speed: 1,
      }),
  };
}

test("external simulator replay replaces fixture cards and follows its source-time waveforms", async ({
  page,
  request,
}) => {
  await login(page);
  const simulator = await historicalReplay(request);
  await page.goto("/#/patient?admission=1");
  await expect(
    page.getByRole("heading", { name: "Patient workstation" }),
  ).toBeVisible();

  let session;
  try {
    session = await simulator.create();
    await simulator.post(`/simulation-sessions/${session.id}/start`);
    await expect
      .poll(async () => {
        const current = await (
          await request.get(`/api/v1/simulation-sessions/${session.id}`)
        ).json();
        return (
          Date.parse(current.cursorAt) - Date.parse(simulator.recording.startAt)
        );
      })
      .toBeGreaterThan(1000);
    session = await simulator.post(`/simulation-sessions/${session.id}/pause`);

    await expect(
      page
        .getByLabel("Replay session", { exact: true })
        .locator("option:checked"),
    ).toHaveText(new RegExp(`Session ${session.id}\\b`, "i"), {
      timeout: 15000,
    });
    await expect(page.locator(".vital.hr .vital-value")).toHaveText("73bpm");
    await expect(page.locator(".vital.sbp")).toContainText("No measurement");
    await expect(
      page.getByRole("img", { name: "ECG recorded waveform" }),
    ).toBeVisible();

    // Reload recovery must come from durable server state, not a WebSocket-only cache.
    await page.reload();
    await expect(
      page
        .getByLabel("Replay session", { exact: true })
        .locator("option:checked"),
    ).toHaveText(new RegExp(`Session ${session.id}\\b`, "i"));
    await expect(page.locator(".vital.hr .vital-value")).toHaveText("73bpm");

    await page.getByRole("button", { name: "monitoring", exact: true }).click();
    await expect(
      page
        .getByLabel("Replay session", { exact: true })
        .locator("option:checked"),
    ).toHaveText(new RegExp(`Session ${session.id}\\b`, "i"));
    await expect(
      page.getByRole("img", { name: "ECG recorded waveform" }),
    ).toBeVisible();
    await expect(
      page
        .getByLabel("Source recording", { exact: true })
        .locator("option", { hasText: simulator.recording.name }),
    ).toHaveCount(1);

    // A stored two-second chunk must not expose samples beyond the chosen
    // display interval. At 10 Hz the final included sample is source second 0.9.
    await page.getByLabel("Follow replay cursor", { exact: true }).uncheck();
    const intervalEnd = page.getByLabel("To (UTC, exclusive)", { exact: true });
    // Change through another second because the frozen cursor may already
    // display 00:00:01 after subsecond precision is hidden by datetime-local.
    await intervalEnd.fill("2001-01-01T00:00:03");
    await intervalEnd.fill("2001-01-01T00:00:01");
    const finalSampleTime =
      new Date("2001-01-01T00:00:00.900Z").toLocaleString("en-IN", {
        timeZone: "Asia/Kolkata",
        dateStyle: "medium",
        timeStyle: "medium",
      }) + " IST";
    await expect(
      page
        .locator("figure")
        .filter({
          has: page.getByRole("img", { name: "ECG recorded waveform" }),
        })
        .locator("figcaption span")
        .last(),
    ).toHaveText(finalSampleTime);
    await page.getByLabel("Follow replay cursor", { exact: true }).check();
    await page.getByLabel("Admission", { exact: true }).selectOption("2");
    await expect(
      page
        .getByLabel("Replay session", { exact: true })
        .locator("option:checked"),
    ).not.toHaveText(new RegExp(`Session ${session.id}\\b`, "i"));
  } finally {
    if (session)
      await simulator.post(`/simulation-sessions/${session.id}/stop`);
  }
});

test("recording-filtered reads never substitute a newer fixture or another replay", async ({
  page,
  request,
}) => {
  await login(page);
  const simulator = await historicalReplay(request);
  let session;
  try {
    session = await simulator.create();
    await simulator.post(`/simulation-sessions/${session.id}/start`);
    await expect
      .poll(async () => {
        const current = await (
          await request.get(`/api/v1/simulation-sessions/${session.id}`)
        ).json();
        return (
          Date.parse(current.cursorAt) - Date.parse(simulator.recording.startAt)
        );
      })
      .toBeGreaterThan(1000);
    session = await simulator.post(`/simulation-sessions/${session.id}/pause`);
    const filter = new URLSearchParams({
      recordingId: session.outputRecordingId,
      at: session.cursorAt,
    });
    const latestResponse = await request.get(
      `/api/v1/admissions/1/measurements/latest?${filter}`,
    );
    expect(latestResponse.ok()).toBe(true);
    const latest = await latestResponse.json();
    expect(latest).toHaveLength(1);
    expect(latest[0]).toMatchObject({
      metric: "HR",
      value: 73,
      recordingId: session.outputRecordingId,
    });

    const range = new URLSearchParams({
      recordingId: session.outputRecordingId,
      from: simulator.recording.startAt,
      to: session.cursorAt,
      metric: "HR",
    });
    const history = await (
      await request.get(`/api/v1/admissions/1/measurements?${range}`)
    ).json();
    expect(history.items).toHaveLength(1);
    expect(history.items[0].recordingId).toBe(session.outputRecordingId);
    range.set("metric", "ECG");
    const waveforms = await (
      await request.get(`/api/v1/admissions/1/waveforms?${range}`)
    ).json();
    expect(waveforms.items).toHaveLength(1);
    expect(waveforms.items[0].chunk.recordingId).toBe(
      session.outputRecordingId,
    );
  } finally {
    if (session)
      await simulator.post(`/simulation-sessions/${session.id}/stop`);
  }
});

test("shared simulator controls update dashboard, trends and recording-scoped CSV", async ({
  page,
  context,
}, testInfo) => {
  await login(page);
  await page.goto("/#/patient?admission=1");
  const simulator = await context.newPage();
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  simulator.on("pageerror", (error) => errors.push(error.message));
  let session;
  try {
    await simulator.goto("/simulator/index.html");
    // Same-origin simulator must reuse the dashboard's authenticated session.
    await expect(simulator.locator("#workspace")).toBeVisible();
    await expect(simulator.locator("#login-panel")).toBeHidden();
    await simulator.locator("#admission").selectOption("1");
    await simulator.locator("#recording").selectOption("1");
    await simulator.locator("#scenario-mode").selectOption("NORMAL");
    await simulator.locator("#speed").selectOption("0.5");
    const createResponse = simulator.waitForResponse(
      (response) =>
        response.url().endsWith("/api/v1/simulation-sessions") &&
        response.request().method() === "POST",
    );
    await simulator.locator("#create").click();
    session = await (await createResponse).json();
    expect(session.id).toBeGreaterThan(0);
    await expect(simulator.locator("#state")).toHaveText("CREATED");
    await simulator.locator("#start").click();
    await expect(simulator.locator("#state")).toHaveText("RUNNING");
    await expect(simulator.locator("#vital-HR strong")).toHaveText("100");
    await simulator.locator("#pause").click();
    await expect(simulator.locator("#state")).toHaveText("PAUSED");
    await expect(
      page
        .getByLabel("Replay session", { exact: true })
        .locator("option:checked"),
    ).toHaveText(new RegExp(`Session ${session.id}\\b.*PAUSED`, "i"));
    await expect(page.locator(".vital.hr .vital-value")).toHaveText("100bpm");
    await expect(
      page.getByRole("img", { name: "ECG recorded waveform" }),
    ).toBeVisible();
    await page.screenshot({
      path: testInfo.outputPath("shared-replay-patient.png"),
      fullPage: true,
    });
    await simulator.screenshot({
      path: testInfo.outputPath("shared-simulator.png"),
      fullPage: true,
    });

    const recordings = await (
      await page.request.get("/api/v1/admissions/1/recordings")
    ).json();
    const output = recordings.find(
      (recording) => recording.id === session.outputRecordingId,
    );
    expect(output).toBeDefined();
    const analyticsResponse = page.waitForResponse((response) => {
      const url = new URL(response.url());
      return (
        url.pathname.endsWith("/admissions/1/analytics") &&
        url.searchParams.get("recordingId") ===
          String(session.outputRecordingId)
      );
    });
    await page.getByRole("button", { name: "trends", exact: true }).click();
    const analytics = await analyticsResponse;
    expect(analytics.ok()).toBe(true);
    expect((await analytics.json()).summary.count).toBeGreaterThan(0);
    await expect(
      page.getByRole("img", { name: "Heart rate history" }),
    ).toBeVisible();
    expect(
      new Date(
        (await page.getByLabel("From (UTC)", { exact: true }).inputValue()) +
          "Z",
      ).toISOString(),
    ).toBe(new Date(output.startAt).toISOString());
    expect(
      new Date(
        (await page
          .getByLabel("To (UTC, exclusive)", { exact: true })
          .inputValue()) + "Z",
      ).toISOString(),
    ).toBe(new Date(output.endAt).toISOString());

    await page.goto("/#/report?admission=1");
    const reportResponse = page.waitForResponse(
      (response) =>
        response.url().endsWith("/api/v1/reports") &&
        response.request().method() === "POST",
    );
    await page
      .getByRole("button", { name: "Generate report", exact: true })
      .click();
    const generated = await reportResponse;
    expect(generated.ok()).toBe(true);
    expect(generated.request().postDataJSON().recordingId).toBe(
      session.outputRecordingId,
    );
    const report = await generated.json();
    const downloadPromise = page.waitForEvent("download");
    await page
      .locator(`a[href="/api/v1/reports/${report.id}/download"]`)
      .click();
    const download = await downloadPromise;
    const csv = await readFile(await download.path(), "utf8");
    expect(csv).toContain(`\nRecording,${session.outputRecordingId}\n`);
    expect(csv).toContain("RESEARCH_ONLY");
    expect(csv).toMatch(/\n1,HR,bpm,1,100/);

    await simulator.locator("#resume").click();
    await expect(simulator.locator("#state")).toHaveText("RUNNING");
    await simulator.locator("#stop").click();
    await expect(simulator.locator("#state")).toHaveText("STOPPED");
    await expect(
      page
        .getByLabel("Replay session", { exact: true })
        .locator("option:checked"),
    ).toHaveText(new RegExp(`Session ${session.id}\\b.*STOPPED`, "i"));
    expect(errors).toEqual([]);
  } finally {
    if (session) {
      const current = await (
        await page.request.get(`/api/v1/simulation-sessions/${session.id}`)
      ).json();
      if (["CREATED", "RUNNING", "PAUSED"].includes(current.state)) {
        const identity = await (
          await page.request.get("/api/v1/session")
        ).json();
        await page.request.post(
          `/api/v1/simulation-sessions/${session.id}/stop`,
          {
            headers: { "X-XSRF-TOKEN": identity.csrfToken },
            data: {},
          },
        );
      }
    }
    await simulator.close();
  }
});
