import { useState } from "react";
import type { FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { ErrorDeApi, post } from "../api/cliente";
import { guardarSesion } from "../sesion/sesion";
import type { Sesion } from "../sesion/sesion";

// RF-UI-001 (specs/008-ui-demo/spec.md §5): login real contra
// `POST /identidades/autenticacion` (Seguridad y Acceso) -- credenciales
// inválidas devuelven 401 con `{"error": "..."}"` (ManejoDeErrores.kt),
// nunca queda ninguna identidad guardada en ese caso.
export function Login() {
  const navegar = useNavigate();
  const [actor, setActor] = useState("");
  const [credencial, setCredencial] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function alEnviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    setError(null);
    setEnviando(true);
    try {
      const identidad = await post<Sesion>("seguridad-acceso", "/identidades/autenticacion", {
        actor,
        credencial,
        fecha: new Date().toISOString(),
      });
      guardarSesion({ id: identidad.id, actor: identidad.actor, roles: identidad.roles });
      navegar("/");
    } catch (motivo) {
      const mensaje =
        motivo instanceof ErrorDeApi && typeof motivo.cuerpo === "object" && motivo.cuerpo !== null && "error" in motivo.cuerpo
          ? String((motivo.cuerpo as { error: unknown }).error)
          : "No se pudo iniciar sesión.";
      setError(mensaje);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <main>
      <h1>Iniciar sesión</h1>
      <form onSubmit={alEnviar}>
        <label>
          Actor
          <input
            type="text"
            value={actor}
            onChange={(evento) => setActor(evento.target.value)}
            required
          />
        </label>
        <label>
          Credencial
          <input
            type="password"
            value={credencial}
            onChange={(evento) => setCredencial(evento.target.value)}
            required
          />
        </label>
        <button type="submit" disabled={enviando}>
          Entrar
        </button>
      </form>
      {error && <p role="alert">{error}</p>}
    </main>
  );
}
