import { useState } from "react";
import type { FormEvent } from "react";
import { post } from "../api/cliente";
import { MarcaDeSimulacion } from "../componentes/MarcaDeSimulacion";

// RF-UI-004 (specs/008-ui-demo/spec.md §5): sugerencia de clasificación
// FICTICIA. `POST /clasificaciones` (Clasificación, RF-CL-001..004) recibe
// una candidata ya calculada por el llamador -- en esta demo, el operador
// la declara a mano (mismo criterio que el resto del proyecto: nunca se
// implementa un clasificador real). La respuesta ya cruzó la capa
// anticorrupción como `Sugerencia` en Records/Custodia (RF-RC-003, servidor
// a servidor, `enviador.enviar()` en clasificacion/api.py) antes de llegar
// aquí -- este RF NO llama a Records/Custodia directamente.
interface SugerenciaSaliente {
  documento_id: string;
  tipo: string;
  contenido_propuesto: string;
  modelo_id: string;
  evidencia: string[];
  confianza: number;
  fecha: string;
}

export function Clasificacion() {
  const [documentoId, setDocumentoId] = useState("");
  const [contenido, setContenido] = useState("");
  const [serie, setSerie] = useState("");
  const [subserie, setSubserie] = useState("");
  const [confianza, setConfianza] = useState("0.5");
  const [sugerencias, setSugerencias] = useState<SugerenciaSaliente[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function alEnviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    setError(null);
    setEnviando(true);
    try {
      const fecha = new Date().toISOString();
      const respuesta = await post<SugerenciaSaliente[]>("clasificacion", "/clasificaciones", {
        texto: {
          texto_extraido_id: `texto-demo-${Date.now()}`,
          documento_id: documentoId,
          contenido,
          estado: "EXTRAIDO",
        },
        candidatas: [
          {
            trd_version: 1,
            serie,
            subserie,
            confianza: Number(confianza),
            evidencia: ["heurística de demostración"],
            modelo_id: "clasificador-ficticio-v1",
            fecha,
          },
        ],
      });
      setSugerencias(respuesta);
    } catch {
      setError("No se pudo generar la sugerencia de clasificación.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <main>
      <h1>Sugerencia de clasificación</h1>
      <form onSubmit={alEnviar}>
        <label>
          Documento
          <input type="text" value={documentoId} onChange={(e) => setDocumentoId(e.target.value)} required />
        </label>
        <label>
          Contenido extraído
          <textarea value={contenido} onChange={(e) => setContenido(e.target.value)} required />
        </label>
        <label>
          Serie
          <input type="text" value={serie} onChange={(e) => setSerie(e.target.value)} required />
        </label>
        <label>
          Subserie
          <input type="text" value={subserie} onChange={(e) => setSubserie(e.target.value)} />
        </label>
        <label>
          Confianza
          <input
            type="number"
            min="0"
            max="1"
            step="0.01"
            value={confianza}
            onChange={(e) => setConfianza(e.target.value)}
            required
          />
        </label>
        <button type="submit" disabled={enviando}>
          Generar sugerencia
        </button>
      </form>
      {error && <p role="alert">{error}</p>}
      {sugerencias && (
        <ul aria-label="Sugerencias de clasificación">
          {sugerencias.map((sugerencia) => (
            <li key={sugerencia.contenido_propuesto}>
              <MarcaDeSimulacion />
              <span>{sugerencia.contenido_propuesto}</span>
              <span> — confianza {sugerencia.confianza}</span>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
