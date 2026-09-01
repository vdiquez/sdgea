import { BrowserRouter, Route, Routes } from "react-router-dom";
import { Clasificacion } from "./paginas/Clasificacion";
import { Inicio } from "./paginas/Inicio";
import { Login } from "./paginas/Login";

// specs/008-ui-demo/spec.md: cada RF-UI-NNN suma su propia ruta aquí a
// medida que se implementa. RF-UI-001 (T-60) trae /login; RF-UI-004
// (T-61) trae /clasificacion.
export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Inicio />} />
        <Route path="/login" element={<Login />} />
        <Route path="/clasificacion" element={<Clasificacion />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
