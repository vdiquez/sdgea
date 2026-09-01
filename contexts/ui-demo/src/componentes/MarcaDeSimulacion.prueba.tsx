import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { MarcaDeSimulacion } from "./MarcaDeSimulacion";

// Prueba unitaria del componente en sí (T-59, andamiaje) -- confirma que
// renderiza un texto visible sin interacción adicional (`role="status"`, no
// un tooltip ni un ícono sin texto), la forma que RF-UI-012/RNF-UI-002
// exigirán una vez haya pantallas reales que lo usen (T-61+). No es, por sí
// misma, la prueba de aceptación de ese RF.
describe("MarcaDeSimulacion", () => {
  it("muestra un texto visible que identifica la salida como simulada", () => {
    render(<MarcaDeSimulacion />);

    const marca = screen.getByRole("status");

    expect(marca).toBeVisible();
    expect(marca).toHaveTextContent(/simulad/i);
  });
});
