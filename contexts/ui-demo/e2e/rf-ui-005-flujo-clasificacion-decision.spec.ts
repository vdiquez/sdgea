import { expect, request as apiRequest, test } from "@playwright/test";

// RF-UI-005 (specs/008-ui-demo/spec.md §5) -- y el flujo funcional completo
// que pidió Victor: login → clasificación → decisión → (bitácora en T-64,
// una vez cerrado el prerrequisito de §1). Este e2e encadena las cuatro
// pantallas reales de punta a punta contra el stack de Docker, sin ningún
// doble -- exactamente el criterio de honestidad de RNF-UI-004.
//
// La cola de Validación Humana exige `leer`/`documento` (para verla) y
// `decidir`/`documento` (para decidir) -- ambos permisos reales, verificados
// contra Seguridad y Acceso (RF-VH-007), así que el rol de la identidad de
// prueba los declara los dos.
test("flujo completo: login → clasificación → cola de validación → decisión", async ({ page, request }) => {
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

  await request.post("/api/seguridad-acceso/roles", {
    data: {
      nombre: rol,
      permisos: [
        { accion: "leer", tipoRecurso: "documento", nivelClasificacionMaximo: "PUBLICA" },
        { accion: "decidir", tipoRecurso: "documento", nivelClasificacionMaximo: "PUBLICA" },
      ],
    },
  });
  await request.post("/api/seguridad-acceso/identidades", {
    data: { id: identidadId, actor, credencial, roles: [rol] },
  });

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

  // 4. Decisión (RF-UI-005) -- Validación Humana materializa en
  // Records/Custodia servidor-a-servidor; la UI solo confirma que la
  // sugerencia se retira de la cola.
  await fila.getByRole("button", { name: "Aceptar decisión" }).click();
  await expect(fila).toHaveCount(0);
});
