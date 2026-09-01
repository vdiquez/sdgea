import "./MarcaDeSimulacion.css";

// RF-UI-012 (specs/008-ui-demo/spec.md §5): componente único, reutilizado en
// toda la UI, para marcar cualquier salida de un componente FICTICIO
// (clasificación, agrupamiento, sugerencia de OCR, embedding/orden de
// relevancia/respuesta de Indexación y Búsqueda, enriquecimiento). Nunca una
// implementación distinta por pantalla (RNF-UI-002) -- P-01 extendido a la
// capa visual: la IA propone, y aquí queda explícito que es una propuesta
// simulada, no un resultado real todavía.
export function MarcaDeSimulacion() {
  return (
    <span className="marca-de-simulacion" role="status">
      Sugerencia simulada — pendiente de calibrar con datos reales
    </span>
  );
}
