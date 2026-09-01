import { expect, request as apiRequest, test } from "@playwright/test";

// RNF-UI-001 (specs/008-ui-demo/spec.md §6) + T-63 (VETO real de Codex sobre
// el primer intento del bloque de records-custodia en nginx.conf/vite.config.ts,
// ver STATE.md): un prefijo genérico `/api/records-custodia/...` reenviaría
// TODO el contrato del contexto, no solo los cinco endpoints que verifican
// `identidadId` -- en particular `POST .../decisiones`, que RF-UI-005 exige
// que la UI nunca llame directamente. Este e2e ejercita el proxy curado real
// (nginx en Docker, o el `bypass` de vite.config.ts en desarrollo local) sin
// ningún doble: confirma que las rutas fuera del alcance de T-63 responden
// 404 a través del ÚNICO camino que el navegador tiene hacia records-custodia,
// y que las permitidas sí llegan de verdad al backend real.
test.describe("proxy curado de records-custodia -- solo cinco rutas, el resto 404", () => {
  const sufijo = Date.now();
  const rol = `rol-proxy-rc-${sufijo}`;
  const identidadId = `id-proxy-rc-${sufijo}`;
  const actor = `actor-proxy-rc-${sufijo}`;
  const documentoId = `doc-proxy-rc-${sufijo}`;

  test.beforeAll(async () => {
    const seguridad = await apiRequest.newContext({ baseURL: "http://localhost:8083" });
    const respuestaRol = await seguridad.post("/roles", {
      data: {
        nombre: rol,
        permisos: [{ accion: "leer", tipoRecurso: "documento", nivelClasificacionMaximo: "PUBLICA" }],
      },
    });
    expect(respuestaRol.ok()).toBe(true);
    const respuestaIdentidad = await seguridad.post("/identidades", {
      data: { id: identidadId, actor, credencial: "clave-proxy-rc", roles: [rol] },
    });
    expect(respuestaIdentidad.ok()).toBe(true);

    const custodia = await apiRequest.newContext({ baseURL: "http://localhost:8082" });
    const respuestaCustodia = await custodia.post("/documentos", {
      data: {
        id: documentoId,
        bytesBase64: "cHJveHktcmM=",
        actor: "victor",
        fecha: new Date().toISOString(),
        procedencia: { fuente: "e2e-proxy-rc", fecha: new Date().toISOString(), loteOFlujoId: `lote-proxy-rc-${sufijo}` },
      },
    });
    expect(respuestaCustodia.ok()).toBe(true);
  });

  test("una ruta permitida (GET /documentos/{id}) llega de verdad a records-custodia a través del proxy", async ({ request }) => {
    const response = await request.get(`/api/records-custodia/documentos/${documentoId}?identidadId=${identidadId}`);
    expect(response.status()).toBe(200);
    const cuerpo = await response.json();
    expect(cuerpo.id).toBe(documentoId);
  });

  test("la misma ruta sin identidadId responde 400 real de records-custodia, no un 404 del proxy", async ({ request }) => {
    const response = await request.get(`/api/records-custodia/documentos/${documentoId}`);
    expect(response.status()).toBe(400);
  });

  // El id de documento en estas rutas es literal, NO `documentoId` (que
  // depende de `Date.now()`): el título de un `test()` debe ser idéntico
  // entre la fase de descubrimiento de Playwright y el proceso worker que
  // lo ejecuta, o falla con "Test not found in the worker process" (cada
  // uno importa este archivo por separado, ver STATE.md) -- y da igual qué
  // id se use, porque estas rutas deben responder 404 exista o no el
  // documento: el bloqueo es de forma de la ruta, no de su contenido.
  for (const [descripcion, metodo, ruta] of [
    ["custodiar un original", "post", "/api/records-custodia/documentos"],
    ["materializar una decisión", "post", "/api/records-custodia/documentos/doc-cualquiera/decisiones"],
    ["consultar la procedencia", "get", "/api/records-custodia/documentos/doc-cualquiera/procedencia"],
    // "correcciones" tiene la misma forma textual que un id de documento
    // (`/documentos/{id}`) -- regresión explícita del hallazgo de Codex.
    ["listar correcciones pendientes de re-revisión", "get", "/api/records-custodia/documentos/correcciones"],
    ["verificar integridad agregada", "post", "/api/records-custodia/verificacion-integridad"],
    ["listar sugerencias pendientes", "get", "/api/records-custodia/sugerencias/pendientes"],
  ] as const) {
    test(`${descripcion} (${metodo.toUpperCase()} ${ruta}) responde 404 -- fuera del alcance de T-63`, async ({ request }) => {
      const response = await request[metodo](ruta, { data: {} });
      expect(response.status()).toBe(404);
    });
  }
});
