import react from "@vitejs/plugin-react";
import { defineConfig, type ProxyOptions } from "vite";

// specs/008-ui-demo/spec.md §4: `npm run dev` refleja el mismo proxy curado
// que nginx sirve en producción/demo (Dockerfile + docker-compose.demo.yml)
// -- mismos prefijos `/api/<contexto>`, apuntando aquí a los puertos locales
// que expone docker-compose.local-ports.yml en vez de los nombres de
// servicio internos de docker-compose. Captura/Ingesta deliberadamente sin
// entrada (sigue bloqueada, ver prerrequisito de arquitectura, §1).
const PUERTOS_LOCALES: Record<string, number> = {
  "seguridad-acceso": 8083,
  "validacion-humana": 8084,
  normalizacion: 8085,
  extraccion: 8086,
  clasificacion: 8087,
  enriquecimiento: 8088,
  "indexacion-busqueda": 8089,
};

const PUERTO_RECORDS_CUSTODIA = 8082;

// T-63 (VETO real de Codex sobre el primer intento de este proxy, ver
// STATE.md): un prefijo genérico `/api/records-custodia` -- como el que usan
// los demás contextos, arriba -- reenviaría TODO el contrato de
// records-custodia, no solo los cinco endpoints que verifican `identidadId`.
// Mismas cinco rutas+métodos que nginx.conf permite en producción/demo;
// cualquier otra petición bajo este prefijo se rechaza aquí con 404 (`bypass`
// devuelve `false`), antes de llegar a records-custodia.
const RUTAS_RECORDS_CUSTODIA_PERMITIDAS: Array<{ metodo: string; patron: RegExp }> = [
  { metodo: "GET", patron: /^\/documentos\/[^/]+$/ },
  { metodo: "GET", patron: /^\/documentos\/[^/]+\/original$/ },
  { metodo: "GET", patron: /^\/documentos\/[^/]+\/sugerencias$/ },
  { metodo: "GET", patron: /^\/eventos-auditoria$/ },
  { metodo: "POST", patron: /^\/documentos\/[^/]+\/verificacion-integridad$/ },
];

// "correcciones" encajaría en el patrón `/documentos/[^/]+$` de arriba (mismo
// shape textual que un id de documento) y reenviaría, sin querer, al
// endpoint NO protegido `GET /documentos/correcciones` (RF-VH-009) -- mismo
// riesgo, y misma exclusión explícita, que nginx.conf.
const RUTA_CORRECCIONES = /^\/documentos\/correcciones$/;

const proxyRecordsCustodia: ProxyOptions = {
  target: `http://localhost:${PUERTO_RECORDS_CUSTODIA}`,
  changeOrigin: true,
  rewrite: (ruta: string) => ruta.replace(/^\/api\/records-custodia/, ""),
  bypass: (req) => {
    const ruta = (req.url ?? "").replace(/^\/api\/records-custodia/, "").split("?")[0];
    if (RUTA_CORRECCIONES.test(ruta)) return false;
    const permitido = RUTAS_RECORDS_CUSTODIA_PERMITIDAS.some(
      ({ metodo, patron }) => req.method === metodo && patron.test(ruta),
    );
    return permitido ? undefined : false;
  },
};

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      ...Object.fromEntries(
        Object.entries(PUERTOS_LOCALES).map(([contexto, puerto]) => [
          `/api/${contexto}`,
          {
            target: `http://localhost:${puerto}`,
            changeOrigin: true,
            rewrite: (ruta: string) => ruta.replace(new RegExp(`^/api/${contexto}`), ""),
          },
        ]),
      ),
      "/api/records-custodia": proxyRecordsCustodia,
    },
  },
});
