from dataclasses import dataclass
from datetime import datetime
from enum import Enum


class ErrorDeDominio(Exception):
    pass


# RF-CL-001: Clasificación no posee la máquina de estados del texto extraído
# (esa es de Extracción, RF-EX-010) — solo verifica la precondición declarada
# por el llamador ("Extraído") antes de aceptar el texto, mismo criterio
# defensivo que las demás transiciones del proyecto. No se importa el enum
# completo de Extracción (acoplaría los dos contextos); solo el único valor
# que este contrato necesita comparar.
class EstadoTextoExtraido(str, Enum):
    EXTRAIDO = "EXTRAIDO"


# RF-CL-001: "queda disponible para producir sugerencias" — el valor que
# devuelve `recibir_texto_extraido` es la única entrada que aceptan
# `clasificar`/`agrupar`/`marcar_no_clasificable`, así que ningún texto llega
# a esas funciones sin pasar primero por esta verificación.
@dataclass(frozen=True)
class TextoDisponible:
    texto_extraido_id: str
    documento_id: str
    contenido: str


def recibir_texto_extraido(
    texto_extraido_id: str, documento_id: str, contenido: str, estado: str
) -> TextoDisponible:
    if estado != EstadoTextoExtraido.EXTRAIDO.value:
        raise ErrorDeDominio(f"El texto extraído '{texto_extraido_id}' no está en estado Extraído.")
    return TextoDisponible(texto_extraido_id=texto_extraido_id, documento_id=documento_id, contenido=contenido)


# RF-CL-002/invariante 2 y 3: componente FICTICIO (constitución, disciplina de
# alcance) — serie/subserie/confianza/evidencia/modelo_id ya vienen calculados
# por el llamador, igual que SugerenciaDeLimites en normalizacion (T-33) y
# SugerenciaOcr en extraccion (T-40); este contexto nunca clasifica de verdad.
# Los tres campos modelo_id/evidencia/confianza son obligatorios en el
# constructor (invariante 3: "nunca se emite sin los tres") — la garantía es
# estructural, no una validación en tiempo de ejecución, mismo criterio que
# `Sugerencia` en records-custodia (T-08).
@dataclass(frozen=True)
class SugerenciaDeClasificacion:
    documento_id: str
    trd_version: int
    serie: str
    subserie: str
    confianza: float
    evidencia: list[str]
    modelo_id: str
    fecha: datetime


def clasificar(
    texto: TextoDisponible,
    trd_version: int,
    serie: str,
    subserie: str,
    confianza: float,
    evidencia: list[str],
    modelo_id: str,
    fecha: datetime,
) -> SugerenciaDeClasificacion:
    return SugerenciaDeClasificacion(
        documento_id=texto.documento_id,
        trd_version=trd_version,
        serie=serie,
        subserie=subserie,
        confianza=confianza,
        evidencia=evidencia,
        modelo_id=modelo_id,
        fecha=fecha,
    )


# RF-CL-003: descendente (mayor confianza primero) — a diferencia de
# ColaDeRevision/ColaDeLimites en Validación Humana (ascendente, más incierto
# primero para revisión). No es un error si sale al revés de ese patrón: es
# el orden que pide literalmente el Dado/Cuando/Entonces de este RF.
def ordenar_por_confianza(sugerencias: list[SugerenciaDeClasificacion]) -> list[SugerenciaDeClasificacion]:
    return sorted(sugerencias, key=lambda sugerencia: sugerencia.confianza, reverse=True)


# RF-CL-005/invariante 3: mismo criterio FICTICIO que clasificar().
# `expediente_propuesto = None` es la marca de "expediente nuevo" (spec §2).
@dataclass(frozen=True)
class SugerenciaDeAgrupamiento:
    documento_id: str
    expediente_propuesto: str | None
    confianza: float
    evidencia: list[str]
    modelo_id: str
    fecha: datetime


def agrupar(
    texto: TextoDisponible,
    expediente_propuesto: str | None,
    confianza: float,
    evidencia: list[str],
    modelo_id: str,
    fecha: datetime,
) -> SugerenciaDeAgrupamiento:
    return SugerenciaDeAgrupamiento(
        documento_id=texto.documento_id,
        expediente_propuesto=expediente_propuesto,
        confianza=confianza,
        evidencia=evidencia,
        modelo_id=modelo_id,
        fecha=fecha,
    )


# RF-CL-010: el llamador declara la condición de "no clasificable" (mismo
# criterio que CondicionDeExtraccion/CondicionDeNormalizacion/
# CondicionValidacion en los otros tres contextos) — el dominio no la
# detecta, solo registra la razón para que no haya pérdida silenciosa.
@dataclass(frozen=True)
class MarcaNoClasificable:
    documento_id: str
    razon: str
    actor: str
    fecha: datetime


def marcar_no_clasificable(texto: TextoDisponible, razon: str, actor: str, fecha: datetime) -> MarcaNoClasificable:
    return MarcaNoClasificable(documento_id=texto.documento_id, razon=razon, actor=actor, fecha=fecha)


# RF-CL-004/RF-CL-006: forma genérica que ya acepta `POST /sugerencias` de
# records-custodia (SugerenciaEntrante — documentoId, tipo, contenidoPropuesto,
# modeloId, evidencia, confianza; T-08). Esta traducción es pura: no hace
# ninguna llamada HTTP (eso es integracion.py, T-45) y no toca ningún estado
# de documento — es la propia estructura del módulo la que demuestra el
# "sin alterar el estado del documento" que exige el Dado/Cuando/Entonces de
# ambos RF, porque este contexto no tiene ningún agregado de documento que
# pudiera alterar.
@dataclass(frozen=True)
class SugerenciaSaliente:
    documento_id: str
    tipo: str
    contenido_propuesto: str
    modelo_id: str
    evidencia: list[str]
    confianza: float
    fecha: datetime


def a_sugerencia_saliente_de_clasificacion(sugerencia: SugerenciaDeClasificacion) -> SugerenciaSaliente:
    return SugerenciaSaliente(
        documento_id=sugerencia.documento_id,
        tipo="clasificacion",
        contenido_propuesto=f"{sugerencia.serie}/{sugerencia.subserie}",
        modelo_id=sugerencia.modelo_id,
        evidencia=sugerencia.evidencia,
        confianza=sugerencia.confianza,
        fecha=sugerencia.fecha,
    )


def a_sugerencia_saliente_de_agrupamiento(sugerencia: SugerenciaDeAgrupamiento) -> SugerenciaSaliente:
    contenido_propuesto = (
        sugerencia.expediente_propuesto if sugerencia.expediente_propuesto is not None else "EXPEDIENTE_NUEVO"
    )
    return SugerenciaSaliente(
        documento_id=sugerencia.documento_id,
        tipo="agrupamiento",
        contenido_propuesto=contenido_propuesto,
        modelo_id=sugerencia.modelo_id,
        evidencia=sugerencia.evidencia,
        confianza=sugerencia.confianza,
        fecha=sugerencia.fecha,
    )
