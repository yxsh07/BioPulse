import { test, expect } from "@playwright/test";
import { randomUUID } from "node:crypto";

test("registry validation, device assignment and audit use the real API", async ({
  page,
}) => {
  const errors = [];
  page.on("pageerror", (e) => errors.push(e.message));
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
  const submit = async (button, path) => {
    const response = page.waitForResponse(
      (r) =>
        r.url().endsWith(`/api/v1/${path}`) && r.request().method() === "POST",
    );
    await page.getByRole("button", { name: button, exact: true }).click();
    return response;
  };
  await page.getByRole("link", { name: "Patients", exact: true }).click();
  const suffix = randomUUID().slice(0, 8),
    code = `QA-${suffix}`;
  await page.getByLabel("Patient code", { exact: true }).fill(code);
  await page
    .getByLabel("Display name", { exact: true })
    .fill(`Generated QA patient ${suffix}`);
  const created = await submit("Register patient", "patients");
  expect(created.ok()).toBe(true);
  const patient = await created.json();
  await page
    .getByRole("textbox", { name: "Search patients on this page" })
    .fill(code);
  await expect(
    page.getByRole("button", { name: `Open Generated QA patient ${suffix}` }),
  ).toBeDisabled();
  expect((await submit("Register patient", "patients")).status()).toBe(409);
  await expect(page.getByRole("alert")).toBeVisible();
  await page
    .getByLabel("Registered patient ID", { exact: true })
    .fill(String(patient.id));
  expect((await submit("Create admission", "admissions")).status()).toBe(409);

  await page.goto("/#/devices");
  const serial = `GENERATED-QA-${suffix}`;
  await page.getByLabel("Serial", { exact: true }).fill(serial);
  const registered = await submit("Register device", "devices");
  expect(registered.ok()).toBe(true);
  const device = await registered.json();
  await expect(page.getByRole("row").filter({ hasText: serial })).toContainText(
    "OFFLINE",
  );
  await page.getByLabel("Device ID", { exact: true }).fill(String(device.id));
  await page.getByLabel("Admission ID", { exact: true }).fill("1");
  expect((await submit("Assign device", "device-assignments")).ok()).toBe(true);
  expect((await submit("Assign device", "device-assignments")).status()).toBe(
    409,
  );
  await expect(page.getByRole("alert")).toBeVisible();

  await page.getByRole("link", { name: "Alerts", exact: true }).click();
  const rule = page
    .locator("form")
    .filter({ has: page.getByRole("button", { name: "Save admission rule" }) });
  await rule.getByLabel("Admission ID", { exact: true }).fill("1");
  await rule.getByLabel("Lower bound", { exact: true }).fill("120");
  await rule.getByLabel("Upper bound", { exact: true }).fill("80");
  expect((await submit("Save admission rule", "alert-rules")).status()).toBe(
    400,
  );
  await expect(page.getByRole("alert")).toBeVisible();
  await page.goto("/#/audit");
  await expect(
    page.getByRole("heading", { name: "Audit trail" }),
  ).toBeVisible();
  await expect(
    page.locator(".audit-row").filter({ hasText: "ASSIGN_DEVICE" }).first(),
  ).toBeVisible();
  expect(errors).toEqual([]);
});
