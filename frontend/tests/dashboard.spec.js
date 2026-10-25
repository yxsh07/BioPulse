import { test, expect } from "@playwright/test";
test.beforeEach(async ({ page }) => {
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
});
test("patient workstation, honest missing values, trends, signals and responsive layout", async ({
  page,
}, testInfo) => {
  // Select a known recording explicitly: the workstation now follows real replay
  // history, so previously-created BIDMC or test sessions must not define this test.
  const recordings = await (
    await page.request.get("/api/v1/recordings")
  ).json();
  const fixture = recordings.find(
    (r) => r.name === "Eight-minute research fixture",
  );
  expect(fixture).toBeTruthy();
  const identity = await (await page.request.get("/api/v1/session")).json();
  const created = await page.request.post("/api/v1/simulation-sessions", {
    headers: { "X-XSRF-TOKEN": identity.csrfToken },
    data: { admissionId: 1, recordingId: fixture.id, speed: 1 },
  });
  expect(created.ok()).toBe(true);
  const fixtureSession = await created.json();
  await page.request.post(
    `/api/v1/simulation-sessions/${fixtureSession.id}/start`,
    { headers: { "X-XSRF-TOKEN": identity.csrfToken }, data: {} },
  );
  await expect
    .poll(async () => {
      const session = await (
        await page.request.get(
          `/api/v1/simulation-sessions/${fixtureSession.id}`,
        )
      ).json();
      return Date.parse(session.cursorAt) - Date.parse(fixture.startAt);
    })
    .toBeGreaterThan(1000);
  await page.request.post(
    `/api/v1/simulation-sessions/${fixtureSession.id}/stop`,
    { headers: { "X-XSRF-TOKEN": identity.csrfToken }, data: {} },
  );
  await expect(page.getByText("Avery R.", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Open Avery R." }).click();
  await expect(
    page.getByRole("heading", { name: "Patient workstation" }),
  ).toBeVisible();
  await expect(
    page.locator(".vital.pulse").getByText("No measurement", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("img", { name: "ECG recorded waveform" }),
  ).toBeVisible();
  await page.screenshot({
    path: testInfo.outputPath("patient.png"),
    fullPage: true,
  });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  await page.getByRole("button", { name: "trends", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Historical trends" }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Data availability" }),
  ).toBeVisible();
  await expect(
    page.getByText("baseline-zscore/1.0", { exact: false }),
  ).toBeVisible();
  await page.screenshot({
    path: testInfo.outputPath("trends.png"),
    fullPage: true,
  });
  await page.getByRole("button", { name: "Open monitoring" }).click();
  await expect(page.getByText("CONNECTED", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Create replay session" }).click();
  await expect(page.getByText("CREATED", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "start", exact: true }).click();
  await expect(page.getByText("RUNNING", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "pause", exact: true }).click();
  await expect(page.getByText("PAUSED", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "stop", exact: true }).click();
  await expect(page.getByText("STOPPED", { exact: true })).toBeVisible();
});
test("event timeline, report export, filters, preferences and logout", async ({
  page,
}, testInfo) => {
  await page.getByRole("link", { name: "Alerts", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Alert center" }),
  ).toBeVisible();
  await page.locator(".event-row").first().click();
  await expect(
    page.getByRole("heading", { name: "Event timeline" }),
  ).toBeVisible();
  const ack = page.getByRole("button", { name: "acknowledge", exact: true });
  if (await ack.isEnabled()) {
    await ack.click();
    await expect(
      page.locator(".audit-row").filter({ hasText: "ACKNOWLEDGED" }),
    ).toBeVisible();
  }
  await page.goto("/#/report?admission=1");
  await page
    .getByRole("button", { name: "Generate report", exact: true })
    .click();
  await expect(page.getByRole("link", { name: "CSV" }).first()).toBeVisible();
  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("link", { name: "CSV" }).first().click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toMatch(/biopulse-report-\d+\.csv/);
  await page.getByRole("link", { name: "Patients", exact: true }).click();
  await page
    .getByRole("textbox", { name: "Search patients on this page" })
    .fill("Avery");
  await expect(page.getByText("Avery R.", { exact: true })).toBeVisible();
  await expect(page.getByText("Jordan M.", { exact: true })).toHaveCount(0);
  await page.getByRole("link", { name: "Profile", exact: true }).click();
  await page.getByLabel("Appearance").selectOption("light");
  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");
  await page.screenshot({
    path: testInfo.outputPath("profile-light.png"),
    fullPage: true,
  });
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Welcome back." }),
  ).toBeVisible();
});
