import { defineConfig } from "@playwright/test";

// RNF-UI-004 (specs/008-ui-demo/spec.md §6): cada RF-UI-NNN se prueba de
// punta a punta contra el stack real de docker-compose (proxy curado +
// nueve contextos), nunca contra un doble que pueda pasar aunque el camino
// real esté roto -- mismo criterio de honestidad que el resto del proyecto.
// `PLAYWRIGHT_BASE_URL` apunta a la UI servida por el proxy nginx real
// (docker-compose.demo.yml) cuando corre en CI/verificación; por defecto
// apunta al dev server de Vite para iterar localmente.
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  retries: 0,
  reporter: "list",
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:5173",
    trace: "retain-on-failure",
  },
});
