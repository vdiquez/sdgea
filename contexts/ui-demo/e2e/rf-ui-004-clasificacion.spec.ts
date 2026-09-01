import { expect, request as apiRequest, test } from "@playwright/test";

// RF-UI-004 (specs/008-ui-demo/spec.md §5): e2e contra Clasificación real
// (`POST /clasificaciones`) -- sin doble, sin mockear la respuesta. La
// sugerencia debe mostrarse con la marca de simulación (RNF-UI-002).
//
// `POST /clasificaciones` reenvía cada sugerencia a Records/Custodia
// servidor-a-servidor (RF-CL-004, `enviador.enviar()` en
// clasificacion/api.py) ANTES de responder -- `CapaAnticorrupcionSugerencias.
// recibir()` exige que el documento ya esté custodiado
// (`custodia.consultarDocumento`, 404 si no existe). Ese reenvío es interno
// a docker-compose (no pasa por el proxy curado ni por el navegador), pero
// el SETUP de esta prueba sí necesita un documento real -- se crea llamando
// directo al puerto local de records-custodia
// (`docker-compose.local-ports.yml`, 8082), nunca a través de la UI (el
// prerrequisito de §1 sigue bloqueando esa ruta para el navegador).
test("RF-UI-004 · la sugerencia de clasificación generada se muestra marcada como simulada", async ({ page }) => {
  const sufijo = Date.now();
  const documentoId = `doc-ui-${sufijo}`;

  const recordsCustodia = await apiRequest.newContext({ baseURL: "http://localhost:8082" });
  const respuestaCustodia = await recordsCustodia.post("/documentos", {
    data: {
      id: documentoId,
      bytesBase64: "SG9sYSBTR0RFQQ==",
      actor: "victor",
      fecha: new Date().toISOString(),
      procedencia: { fuente: "e2e-ui-demo", fecha: new Date().toISOString(), loteOFlujoId: `lote-ui-${sufijo}` },
    },
  });
  expect(respuestaCustodia.ok()).toBe(true);

  await page.goto("/clasificacion");
  await page.getByLabel("Documento").fill(documentoId);
  await page.getByLabel("Contenido extraído").fill("acta de reunión del comité directivo");
  await page.getByLabel("Serie", { exact: true }).fill("serie-1");
  await page.getByLabel("Subserie").fill("subserie-1");
  await page.getByLabel("Confianza").fill("0.73");
  await page.getByRole("button", { name: "Generar sugerencia" }).click();

  const lista = page.getByRole("list", { name: "Sugerencias de clasificación" });
  await expect(lista.getByText("serie-1/subserie-1")).toBeVisible();
  await expect(lista.getByText(/confianza 0\.73/)).toBeVisible();
  await expect(lista.getByRole("status")).toHaveText(/simulad/i);
});
