import { BrowserRouter, Route, Routes } from "react-router-dom";
import { Inicio } from "./paginas/Inicio";

// specs/008-ui-demo/spec.md: cada RF-UI-NNN suma su propia ruta aquí a
// medida que se implementa (T-60+). T-59 (este commit) solo trae el
// andamiaje -- sin ningún RF todavía.
export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Inicio />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
