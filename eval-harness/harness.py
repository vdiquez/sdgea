import json
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol


@dataclass(frozen=True)
class RegistroSetPatron:
    id: str
    entrada: str
    referencia: str


class Componente(Protocol):
    def predecir(self, entrada: str) -> str: ...


@dataclass(frozen=True)
class Boleta:
    total: int
    aciertos: int
    exactitud: float
    detalle: list[tuple[str, bool]]


def cargar_set_patron(ruta: Path) -> list[RegistroSetPatron]:
    datos = json.loads(ruta.read_text(encoding="utf-8"))
    return [RegistroSetPatron(**registro) for registro in datos]


def correr_arnes(set_patron: list[RegistroSetPatron], componente: Componente) -> Boleta:
    detalle: list[tuple[str, bool]] = []
    aciertos = 0
    for registro in set_patron:
        acierto = componente.predecir(registro.entrada) == registro.referencia
        aciertos += acierto
        detalle.append((registro.id, acierto))
    total = len(set_patron)
    exactitud = aciertos / total if total else 0.0
    return Boleta(total=total, aciertos=aciertos, exactitud=exactitud, detalle=detalle)
