import { BrowserRouter, Route, Routes } from "react-router-dom";
import { Bitacora } from "./paginas/Bitacora";
import { Clasificacion } from "./paginas/Clasificacion";
import { ColaDeValidacion } from "./paginas/ColaDeValidacion";
import { Inicio } from "./paginas/Inicio";
import { Login } from "./paginas/Login";

// specs/008-ui-demo/spec.md: cada RF-UI-NNN suma su propia ruta aquí a
// medida que se implementa. RF-UI-001 (T-60) trae /login; RF-UI-004
// (T-61) trae /clasificacion; RF-UI-005 (T-62) trae /cola-validacion;
// RF-UI-011, alcance inicial (T-64) trae /bitacora.
export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Inicio />} />
        <Route path="/login" element={<Login />} />
        <Route path="/clasificacion" element={<Clasificacion />} />
        <Route path="/cola-validacion" element={<ColaDeValidacion />} />
        <Route path="/bitacora" element={<Bitacora />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
