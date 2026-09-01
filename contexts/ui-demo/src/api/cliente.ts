// specs/008-ui-demo/spec.md §4: toda llamada sale hacia el proxy curado
// (`/api/<contexto>/...`), nunca directo a un puerto interno de
// docker-compose (RNF-UI-001). `contexto` es el nombre de servicio tal como
// aparece en docker-compose.saas.yml -- Captura/Ingesta y Records/Custodia
// deliberadamente no tienen ruta hasta cerrar el prerrequisito de
// autorización de §1 (T-63+).
export type Contexto =
  | "seguridad-acceso"
  | "clasificacion"
  | "validacion-humana"
  | "normalizacion"
  | "extraccion"
  | "enriquecimiento"
  | "indexacion-busqueda";

export class ErrorDeApi extends Error {
  readonly status: number;
  readonly cuerpo: unknown;

  constructor(status: number, cuerpo: unknown) {
    super(`Error de API (${status})`);
    this.status = status;
    this.cuerpo = cuerpo;
  }
}

function urlDeApi(contexto: Contexto, ruta: string): string {
  return `/api/${contexto}${ruta}`;
}

async function peticion<T>(contexto: Contexto, ruta: string, init?: RequestInit): Promise<T> {
  const respuesta = await fetch(urlDeApi(contexto, ruta), {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...init?.headers,
    },
  });
  const texto = await respuesta.text();
  const cuerpo = texto ? JSON.parse(texto) : null;
  if (!respuesta.ok) {
    throw new ErrorDeApi(respuesta.status, cuerpo);
  }
  return cuerpo as T;
}

export function get<T>(contexto: Contexto, ruta: string): Promise<T> {
  return peticion<T>(contexto, ruta, { method: "GET" });
}

export function post<T>(contexto: Contexto, ruta: string, cuerpo: unknown): Promise<T> {
  return peticion<T>(contexto, ruta, { method: "POST", body: JSON.stringify(cuerpo) });
}
