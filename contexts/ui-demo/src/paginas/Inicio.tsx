import { Link } from "react-router-dom";
import { obtenerSesion } from "../sesion/sesion";

export function Inicio() {
  const sesion = obtenerSesion();

  return (
    <main>
      <h1>SGDEA — demostración</h1>
      <p>Sistema de Gestión de Documentos Electrónicos de Archivo.</p>
      {sesion ? (
        <p>Sesión activa: {sesion.actor}</p>
      ) : (
        <p>
          <Link to="/login">Iniciar sesión</Link>
        </p>
      )}
    </main>
  );
}
