from pathlib import Path

from componente_ficticio import ComponenteFicticio
from harness import RegistroSetPatron, cargar_set_patron, correr_arnes


def test_correr_arnes_calcula_exactitud_sobre_predicciones_mixtas():
    set_patron = [
        RegistroSetPatron(id="a", entrada="x", referencia="x"),
        RegistroSetPatron(id="b", entrada="y", referencia="z"),
    ]

    class ComponenteFijo:
        def predecir(self, entrada: str) -> str:
            return entrada

    boleta = correr_arnes(set_patron, ComponenteFijo())

    assert boleta.total == 2
    assert boleta.aciertos == 1
    assert boleta.exactitud == 0.5
    assert boleta.detalle == [("a", True), ("b", False)]


def test_cargar_set_patron_lee_fixture_json(tmp_path: Path):
    ruta = tmp_path / "set.json"
    ruta.write_text('[{"id": "a", "entrada": "x", "referencia": "x"}]', encoding="utf-8")

    set_patron = cargar_set_patron(ruta)

    assert set_patron == [RegistroSetPatron(id="a", entrada="x", referencia="x")]


def test_componente_ficticio_corre_de_punta_a_punta_contra_la_fixture():
    fixture = Path(__file__).parent.parent / "fixtures" / "set_patron_ficticio.json"
    set_patron = cargar_set_patron(fixture)

    boleta = correr_arnes(set_patron, ComponenteFicticio())

    assert boleta.total == len(set_patron)
    assert 0.0 < boleta.exactitud < 1.0
