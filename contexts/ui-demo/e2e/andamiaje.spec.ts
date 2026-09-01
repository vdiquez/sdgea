import { expect, test } from "@playwright/test";

// T-59: smoke test del andamiaje -- confirma que la UI carga de verdad
// contra el servidor real (dev server o el proxy nginx de la demo, según
// PLAYWRIGHT_BASE_URL). Sin ningún RF-UI todavía; las pruebas e2e por RF
// llegan en T-60+.
test("la aplicación carga y muestra la página de inicio", async ({ page }) => {
  await page.goto("/");

  await expect(page.getByRole("heading", { name: /SGDEA/i })).toBeVisible();
});
