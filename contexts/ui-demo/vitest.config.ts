import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

// RNF-UI-004 (specs/008-ui-demo/spec.md §6): componentes puros de
// presentación se prueban en aislamiento (Vitest + Testing Library) --
// la orquestación real contra los servicios se prueba con Playwright
// (playwright.config.ts), nunca con un doble que oculte el camino real.
export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    setupFiles: ["./src/pruebas/configuracion.ts"],
    include: ["src/**/*.prueba.{ts,tsx}"],
  },
});
