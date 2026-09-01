import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// specs/008-ui-demo/spec.md §4: `npm run dev` refleja el mismo proxy curado
// que nginx sirve en producción/demo (Dockerfile + docker-compose.demo.yml)
// -- mismos prefijos `/api/<contexto>`, apuntando aquí a los puertos locales
// que expone docker-compose.local-ports.yml en vez de los nombres de
// servicio internos de docker-compose. Captura/Ingesta y Records/Custodia
// deliberadamente sin entrada (ver prerrequisito de arquitectura, §1).
const PUERTOS_LOCALES: Record<string, number> = {
  "seguridad-acceso": 8083,
  "validacion-humana": 8084,
  normalizacion: 8085,
  extraccion: 8086,
  clasificacion: 8087,
  enriquecimiento: 8088,
  "indexacion-busqueda": 8089,
};

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: Object.fromEntries(
      Object.entries(PUERTOS_LOCALES).map(([contexto, puerto]) => [
        `/api/${contexto}`,
        {
          target: `http://localhost:${puerto}`,
          changeOrigin: true,
          rewrite: (ruta: string) => ruta.replace(new RegExp(`^/api/${contexto}`), ""),
        },
      ]),
    ),
  },
});
