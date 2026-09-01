// specs/008-ui-demo/spec.md §2/§8: "Sesión de demo" -- Seguridad y Acceso
// todavía no emite un token real (`POST /identidades/autenticacion` solo
// devuelve la `Identidad`), así que esta capa conserva la identidad
// autenticada en el cliente y la reenvía como `actor` en las llamadas
// siguientes, mismo patrón que ya usa la colección Postman. Decisión de
// alcance consciente, no una referencia normativa ni un umbral inventado
// (§8 de la spec) -- si el proyecto necesita sesión con expiración/
// revocación real, es una ampliación de `specs/006-seguridad-acceso/spec.md`.
const CLAVE_DE_ALMACENAMIENTO = "sgdea-ui-demo:sesion";

export interface Rol {
  nombre: string;
}

// Deliberadamente NO incluye `credencialHash`: aunque la respuesta real de
// `POST /identidades/autenticacion` lo trae (contrato ya existente en
// Seguridad y Acceso, fuera del alcance de esta capa), la sesión de la UI
// nunca lo persiste ni lo reenvía -- no hace falta para actuar como `actor`.
export interface Sesion {
  id: string;
  actor: string;
  roles: Rol[];
}

export function guardarSesion(sesion: Sesion): void {
  localStorage.setItem(CLAVE_DE_ALMACENAMIENTO, JSON.stringify(sesion));
}

export function obtenerSesion(): Sesion | null {
  const crudo = localStorage.getItem(CLAVE_DE_ALMACENAMIENTO);
  return crudo ? (JSON.parse(crudo) as Sesion) : null;
}

export function cerrarSesion(): void {
  localStorage.removeItem(CLAVE_DE_ALMACENAMIENTO);
}
