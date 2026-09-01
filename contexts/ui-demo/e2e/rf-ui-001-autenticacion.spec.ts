import { expect, test } from "@playwright/test";

// RF-UI-001 (specs/008-ui-demo/spec.md §5): e2e contra el stack real --
// crea un rol + identidad reales en Seguridad y Acceso (mismo patrón que
// las carpetas 3/4 de postman/SGDEA-coleccion.postman_collection.json)
// antes de ejercitar el login desde el navegador. Nunca un doble que
// simule la respuesta de autenticación (RNF-UI-004).
test.describe("RF-UI-001 · Autenticación de la sesión de demo", () => {
  test("credenciales válidas guardan la identidad autenticada y navegan al inicio", async ({ page, request }) => {
    const sufijo = Date.now();
    const rol = `rol-ui-${sufijo}`;
    const actor = `actor-ui-${sufijo}`;
    const identidadId = `id-ui-${sufijo}`;
    const credencial = "clave-ui-demo";

    const respuestaRol = await request.post("/api/seguridad-acceso/roles", {
      data: { nombre: rol, permisos: [] },
    });
    expect(respuestaRol.ok()).toBe(true);

    const respuestaIdentidad = await request.post("/api/seguridad-acceso/identidades", {
      data: { id: identidadId, actor, credencial, roles: [rol] },
    });
    expect(respuestaIdentidad.ok()).toBe(true);

    await page.goto("/login");
    await page.getByLabel("Actor").fill(actor);
    await page.getByLabel("Credencial").fill(credencial);
    await page.getByRole("button", { name: "Entrar" }).click();

    await expect(page).toHaveURL("/");
    await expect(page.getByText(`Sesión activa: ${actor}`)).toBeVisible();
  });

  test("credenciales inválidas muestran el rechazo y no guardan ninguna sesión", async ({ page }) => {
    await page.goto("/login");
    await page.getByLabel("Actor").fill(`actor-inexistente-${Date.now()}`);
    await page.getByLabel("Credencial").fill("cualquier-cosa");
    await page.getByRole("button", { name: "Entrar" }).click();

    await expect(page.getByRole("alert")).toBeVisible();
    await expect(page).toHaveURL(/\/login$/);

    await page.goto("/");
    await expect(page.getByRole("link", { name: "Iniciar sesión" })).toBeVisible();
  });
});
