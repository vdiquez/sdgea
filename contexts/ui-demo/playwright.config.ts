import { defineConfig } from "@playwright/test";

// RNF-UI-004 (specs/008-ui-demo/spec.md §6): cada RF-UI-NNN se prueba de
// punta a punta contra el stack real de docker-compose (proxy curado +
// nueve contextos), nunca contra un doble que pueda pasar aunque el camino
// real esté roto -- mismo criterio de honestidad que el resto del proyecto.
// `PLAYWRIGHT_BASE_URL` apunta a la UI servida por el proxy nginx real
// (docker-compose.demo.yml) cuando corre en CI/verificación; por defecto
// apunta al dev server de Vite para iterar localmente.
//
// Hallazgo real de T-61: algunas pruebas necesitan sembrar datos en
// contextos que el proxy curado no expone al navegador (p. ej.
// Records/Custodia, bloqueado por el prerrequisito de §1) -- para eso
// llaman directo al puerto local del contexto (`docker-compose.
// local-ports.yml`, nunca a través de la UI). Levantar el stack completo
// para correr `npm run test:e2e` requiere las tres capas juntas:
//   docker compose -f deploy/docker-compose.saas.yml
//                  -f deploy/docker-compose.demo.yml
//                  -f deploy/docker-compose.local-ports.yml up -d --build
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
