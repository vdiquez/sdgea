import { expect, request as apiRequest, test } from "@playwright/test";

// RF-UI-005 + RF-UI-011 (alcance inicial, T-64) -- el flujo funcional
// completo que pidió Victor: login → clasificación → decisión → bitácora.
// Este e2e encadena las cinco pantallas reales de punta a punta contra el
// stack de Docker, sin ningún doble -- exactamente el criterio de
// honestidad de RNF-UI-004.
//
// La cola de Validación Humana exige `leer`/`documento` (para verla) y
// `decidir`/`documento` (para decidir); la bitácora (GET /eventos-auditoria
// de Records/Custodia, T-63) exige `leer`/`documento` también -- los tres
// permisos reales, verificados contra Seguridad y Acceso (RF-VH-007 /
// T-63), así que el rol de la identidad de prueba los declara todos.
test("flujo completo: login → clasificación → cola de validación → decisión → bitácora", async ({ page, request }) => {
  const sufijo = Date.now();
  const rol = `rol-ui-flujo-${sufijo}`;
  const actor = `actor-ui-flujo-${sufijo}`;
  const identidadId = `id-ui-flujo-${sufijo}`;
  const credencial = "clave-ui-demo";
  const documentoId = `doc-ui-flujo-${sufijo}`;
  // La cola de Validación Humana es estado persistente y compartido entre
  // corridas de la colección (mismo patrón ya encontrado en T-57/T-58 con
  // Postman): sin una serie/subserie única por corrida, esta prueba
  // encontraría también las sugerencias de corridas anteriores.
  const serie = `serie-ui-flujo-${sufijo}`;
  const subserie = `subserie-ui-flujo-${sufijo}`;
  const contenidoPropuesto = `${serie}/${subserie}`;

  // Observación de Codex sobre T-62 (ver REVIEW.md/STATE.md): comprobar
  // explícitamente el resultado de la preparación, para que un fallo de
  // setup no quede oculto detrás de un fallo posterior más confuso.
  const respuestaRol = await request.post("/api/seguridad-acceso/roles", {
    data: {
      nombre: rol,
      permisos: [
        { accion: "leer", tipoRecurso: "documento", nivelClasificacionMaximo: "PUBLICA" },
        { accion: "decidir", tipoRecurso: "documento", nivelClasificacionMaximo: "PUBLICA" },
      ],
    },
  });
  expect(respuestaRol.ok()).toBe(true);
  const respuestaIdentidad = await request.post("/api/seguridad-acceso/identidades", {
    data: { id: identidadId, actor, credencial, roles: [rol] },
  });
  expect(respuestaIdentidad.ok()).toBe(true);

  const recordsCustodia = await apiRequest.newContext({ baseURL: "http://localhost:8082" });
  const respuestaCustodia = await recordsCustodia.post("/documentos", {
    data: {
      id: documentoId,
      bytesBase64: "SG9sYSBTR0RFQQ==",
      actor: "victor",
      fecha: new Date().toISOString(),
      procedencia: { fuente: "e2e-ui-demo", fecha: new Date().toISOString(), loteOFlujoId: `lote-ui-flujo-${sufijo}` },
    },
  });
  expect(respuestaCustodia.ok()).toBe(true);

  // 1. Login (RF-UI-001)
  await page.goto("/login");
  await page.getByLabel("Actor").fill(actor);
  await page.getByLabel("Credencial").fill(credencial);
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page).toHaveURL("/");

  // 2. Sugerencia de clasificación (RF-UI-004)
  await page.goto("/clasificacion");
  await page.getByLabel("Documento").fill(documentoId);
  await page.getByLabel("Contenido extraído").fill("acta de reunión del comité directivo");
  await page.getByLabel("Serie", { exact: true }).fill(serie);
  await page.getByLabel("Subserie").fill(subserie);
  await page.getByLabel("Confianza").fill("0.42");
  await page.getByRole("button", { name: "Generar sugerencia" }).click();
  await expect(page.getByText(contenidoPropuesto)).toBeVisible();

  // 3. Cola de validación humana (RF-UI-005) -- la sugerencia debe aparecer
  await page.goto("/cola-validacion");
  const lista = page.getByRole("list", { name: "Cola de clasificación" });
  const fila = lista.getByRole("listitem").filter({ hasText: contenidoPropuesto });
  await expect(fila).toBeVisible();
  await expect(fila.getByRole("status")).toHaveText(/simulad/i);

  // VETO real de Codex sobre T-62 (ver REVIEW.md/STATE.md): "Aceptar
  // decisión" llegaba a Records/Custodia como CORRECCIÓN para toda
  // sugerencia con formato "serie/subserie" -- ValidacionHumana.kt
  // comparaba solo contra `serieId`, nunca contra "serie/subserie" (el
  // formato real que produce Clasificación, T-45). Corregido en
  // ValidacionHumana.kt (con sus propias pruebas unitarias); aquí se
  // verifica servidor-a-servidor, vía el puerto local de records-custodia,
  // que esta aceptación exacta NO incrementó la lista de correcciones
  // pendientes de re-revisión (RF-VH-009) -- si el defecto reapareciera,
  // esta aserción lo detectaría de nuevo.
  const correccionesAntes = await recordsCustodia.get("/documentos/correcciones");
  // Observación de Codex sobre este mismo refuerzo (ver REVIEW.md/STATE.md):
  // sin exigir 2xx aquí, dos respuestas de error sin `length` en su cuerpo
  // JSON compararían `undefined === undefined` y la aserción de abajo
  // pasaría igual aunque la consulta real hubiera fallado.
  expect(correccionesAntes.ok()).toBe(true);
  const volumenAntes = (await correccionesAntes.json()).length;

  // 4. Decisión (RF-UI-005) -- Validación Humana materializa en
  // Records/Custodia servidor-a-servidor; la UI solo confirma que la
  // sugerencia se retira de la cola.
  await fila.getByRole("button", { name: "Aceptar decisión" }).click();
  await expect(fila).toHaveCount(0);

  const correccionesDespues = await recordsCustodia.get("/documentos/correcciones");
  expect(correccionesDespues.ok()).toBe(true);
  const volumenDespues = (await correccionesDespues.json()).length;
  expect(volumenDespues).toBe(volumenAntes);

  // 5. Bitácora (RF-UI-011, alcance inicial de T-64) -- la decisión que
  // acaba de materializarse debe ser consultable, atribuible y fechada,
  // cerrando el flujo pedido por Victor.
  await page.goto("/bitacora");
  const bitacora = page.getByRole("list", { name: "Bitácora de auditoría" });
  const eventoDecision = bitacora.getByRole("listitem").filter({ hasText: "DECISION_HUMANA_MATERIALIZADA" }).filter({ hasText: actor });
  await expect(eventoDecision).toBeVisible();
  await expect(eventoDecision).toContainText(/fecha: \S/);
});
