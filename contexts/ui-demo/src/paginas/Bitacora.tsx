import { useEffect, useState } from "react";
import { get } from "../api/cliente";
import { obtenerSesion } from "../sesion/sesion";

// RF-UI-011 (specs/008-ui-demo/spec.md §5), alcance inicial de T-64: cierra
// el flujo pedido por Victor -- login → clasificación → decisión → bitácora
// -- mostrando la bitácora de auditoría real de Records/Custodia
// (`GET /eventos-auditoria`, desbloqueado en T-63). NO es todavía la
// bitácora consolidada completa que pide el RF (Normalización, Extracción e
// Indexación y Búsqueda también exponen `GET /eventos-auditoria`, y
// Seguridad y Acceso expone `GET /eventos-seguridad` en forma distinta) --
// eso queda para una ampliación futura de esta misma pantalla, sin
// prometer aquí lo que todavía no consulta.
//
// El backend no filtra eventos por documento (`EventoAuditoria` no porta
// `documentoId`, ver CustodiaOriginales.kt): esta pantalla muestra la
// bitácora completa que expone Records/Custodia, no una vista recortada a
// un documento -- mostrar solo un subconjunto inventado sería menos
// honesto que mostrar exactamente lo que el backend realmente expone.
interface EventoAuditoria {
  actor: string;
  fecha: string;
  tipo: string;
  estadoAnterior: string | null;
  estadoPosterior: string | null;
  esCorreccion: boolean;
}

export function Bitacora() {
  const sesion = obtenerSesion();
  const [eventos, setEventos] = useState<EventoAuditoria[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!sesion) return;
    get<EventoAuditoria[]>("records-custodia", `/eventos-auditoria?identidadId=${encodeURIComponent(sesion.id)}`)
      .then(setEventos)
      .catch(() => setError("No se pudo cargar la bitácora de auditoría."));
  }, [sesion]);

  if (!sesion) {
    return (
      <main>
        <p role="alert">Inicia sesión para ver la bitácora.</p>
      </main>
    );
  }

  return (
    <main>
      <h1>Bitácora de auditoría</h1>
      <p>Eventos de Records/Custodia, atribuibles y fechados (P-08).</p>
      {error && <p role="alert">{error}</p>}
      {eventos === null ? (
        <p>Cargando…</p>
      ) : eventos.length === 0 ? (
        <p>Sin eventos todavía.</p>
      ) : (
        <ul aria-label="Bitácora de auditoría">
          {eventos.map((evento, indice) => (
            <li key={indice}>
              <strong>{evento.tipo}</strong>
              <span> — actor: {evento.actor}</span>
              <span> — fecha: {evento.fecha}</span>
              {evento.esCorreccion && <span> — corrección</span>}
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
