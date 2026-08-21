import json
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol


@dataclass(frozen=True)
class RegistroSetPatron:
    id: str
    entrada: str
    referencia: str


@dataclass(frozen=True)
class SetPatron:
    version: str
    registros: list[RegistroSetPatron]


class Componente(Protocol):
    def predecir(self, entrada: str) -> str: ...


@dataclass(frozen=True)
class Boleta:
    version: str
    total: int
    aciertos: int
    exactitud: float
    detalle: list[tuple[str, bool]]


def cargar_set_patron(ruta: Path) -> SetPatron:
    datos = json.loads(ruta.read_text(encoding="utf-8"))
    registros = [RegistroSetPatron(**registro) for registro in datos["registros"]]
    return SetPatron(version=datos["version"], registros=registros)


def correr_arnes(set_patron: SetPatron, componente: Componente) -> Boleta:
    detalle: list[tuple[str, bool]] = []
    aciertos = 0
    for registro in set_patron.registros:
        acierto = componente.predecir(registro.entrada) == registro.referencia
        aciertos += acierto
        detalle.append((registro.id, acierto))
    total = len(set_patron.registros)
    exactitud = aciertos / total if total else 0.0
    return Boleta(
        version=set_patron.version,
        total=total,
        aciertos=aciertos,
        exactitud=exactitud,
        detalle=detalle,
    )
