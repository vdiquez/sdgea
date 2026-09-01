import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { MarcaDeSimulacion } from "./MarcaDeSimulacion";

// RNF-UI-002/RF-UI-012: la marca debe ser visible sin interacción adicional
// -- `role="status"` + texto legible, no un tooltip ni un ícono sin texto.
describe("MarcaDeSimulacion", () => {
  it("muestra un texto visible que identifica la salida como simulada", () => {
    render(<MarcaDeSimulacion />);

    const marca = screen.getByRole("status");

    expect(marca).toBeVisible();
    expect(marca).toHaveTextContent(/simulad/i);
  });
});
