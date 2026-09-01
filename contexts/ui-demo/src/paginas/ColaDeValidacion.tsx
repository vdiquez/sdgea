import { useEffect, useState } from "react";
import { get, post } from "../api/cliente";
import { MarcaDeSimulacion } from "../componentes/MarcaDeSimulacion";
import { obtenerSesion } from "../sesion/sesion";

// RF-UI-005 (specs/008-ui-demo/spec.md §5): cola de Validación Humana,
// ordenada por confianza (RF-VH-001/002), y decisión individual
// (RF-VH-003). La UI llama solo a Validación Humana -- es ella quien
// internamente materializa en Records/Custodia (RF-RC-004),
// servidor-a-servidor, la misma orquestación que ya existe hoy (no una
// exposición nueva del navegador hacia ese contexto, que sigue bloqueado
// por el prerrequisito de §1).
interface SugerenciaPendiente {
  documentoId: string;
  tipo: string;
  contenidoPropuesto: string;
  modeloId: string;
  evidencia: string[];
  confianza: number;
  fecha: string;
}

export function ColaDeValidacion() {
  const sesion = obtenerSesion();
  const [cola, setCola] = useState<SugerenciaPendiente[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [decidiendo, setDecidiendo] = useState<string | null>(null);

  useEffect(() => {
    if (!sesion) return;
    get<SugerenciaPendiente[]>("validacion-humana", `/colas/clasificacion?identidadId=${encodeURIComponent(sesion.id)}`)
      .then(setCola)
      .catch(() => setError("No se pudo cargar la cola de validación."));
  }, [sesion]);

  async function decidir(sugerencia: SugerenciaPendiente) {
    if (!sesion) return;
    setError(null);
    setDecidiendo(sugerencia.documentoId);
    try {
      const [serieId, subserieId] = sugerencia.contenidoPropuesto.split("/");
      await post("validacion-humana", "/decisiones", {
        identidadId: sesion.id,
        sugerencia,
        clasificacionResultante: { trdVersion: 1, serieId, subserieId: subserieId || null },
        actor: sesion.actor,
        fecha: new Date().toISOString(),
      });
      setCola((actual) => (actual ?? []).filter((s) => s.documentoId !== sugerencia.documentoId));
    } catch {
      setError("No se pudo registrar la decisión.");
    } finally {
      setDecidiendo(null);
    }
  }

  if (!sesion) {
    return (
      <main>
        <p role="alert">Inicia sesión para ver la cola de validación.</p>
      </main>
    );
  }

  return (
    <main>
      <h1>Cola de validación humana</h1>
      {error && <p role="alert">{error}</p>}
      {cola === null ? (
        <p>Cargando…</p>
      ) : cola.length === 0 ? (
        <p>Sin sugerencias pendientes.</p>
      ) : (
        <ul aria-label="Cola de clasificación">
          {cola.map((sugerencia) => (
            <li key={sugerencia.documentoId}>
              <MarcaDeSimulacion />
              <span>{sugerencia.contenidoPropuesto}</span>
              <span> — confianza {sugerencia.confianza}</span>
              <button
                type="button"
                onClick={() => decidir(sugerencia)}
                disabled={decidiendo === sugerencia.documentoId}
              >
                Aceptar decisión
              </button>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
