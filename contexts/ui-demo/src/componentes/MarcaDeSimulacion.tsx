import "./MarcaDeSimulacion.css";

// Pieza de infraestructura compartida (T-59, andamiaje) que las pantallas de
// RF-UI-004/008/010 (T-61+) usarán para cumplir RF-UI-012
// (specs/008-ui-demo/spec.md §5: un único componente reutilizado en toda la
// UI para marcar cualquier salida de un componente FICTICIO, nunca una
// implementación distinta por pantalla). VETO real de Codex sobre T-59 (ver
// STATE.md): este componente por sí solo NO satisface RF-UI-012 -- ese RF
// exige que TODA salida FICTICIA lo use, y todavía no existe ninguna
// pantalla que muestre una; queda pendiente de T-61+, con sus propias
// pruebas de aceptación en ese momento.
export function MarcaDeSimulacion() {
  return (
    <span className="marca-de-simulacion" role="status">
      Sugerencia simulada — pendiente de calibrar con datos reales
    </span>
  );
}
