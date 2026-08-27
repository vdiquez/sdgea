from dataclasses import dataclass, replace
from datetime import datetime
from enum import Enum


# `str, Enum` (en vez de `auto()`) para que el valor JSON sea el nombre del
# miembro ("EN_CUARENTENA"), igual que Kotlin serializa sus enums con Jackson
# — necesario para que Pydantic/FastAPI (api.py) validen el mismo texto que ya
# usan captura-ingesta/records-custodia en sus propias peticiones HTTP.
class EstadoUnidadDocumental(str, Enum):
    PENDIENTE_DE_LIMITES = "PENDIENTE_DE_LIMITES"
    LIMITES_CONFIRMADOS = "LIMITES_CONFIRMADOS"
    NORMALIZADA = "NORMALIZADA"
    ENTREGADA_A_EXTRACCION = "ENTREGADA_A_EXTRACCION"
    RECHAZADA = "RECHAZADA"
    EN_CUARENTENA = "EN_CUARENTENA"
    VINCULADA_A_DUPLICADO = "VINCULADA_A_DUPLICADO"


# RF-NO-009: mismo criterio que CondicionValidacion en captura-ingesta (T-02):
# el llamador declara la condición, el dominio no la detecta.
class CondicionDeNormalizacion(str, Enum):
    CORRUPTO = "CORRUPTO"
    ILEGIBLE = "ILEGIBLE"
    FORMATO_NO_SOPORTADO = "FORMATO_NO_SOPORTADO"


class ErrorDeDominio(Exception):
    pass


@dataclass(frozen=True)
class ProcedenciaHeredada:
    fuente: str
    fecha: datetime
    disparador: str
    lote_o_flujo_id: str
    item_ingesta_id: str


# RF-NO-002: componente FICTICIO (constitución, disciplina de alcance) — esta
# clase solo transporta una sugerencia ya calculada, igual que Sugerencia en
# records-custodia (T-08); ningún código de este contexto calcula límites de
# verdad.
@dataclass(frozen=True)
class SugerenciaDeLimites:
    modelo_id: str
    evidencia: list[str]
    confianza: float
    fecha: datetime


@dataclass(frozen=True)
class ConfirmacionHumanaDeLimites:
    actor: str
    fecha: datetime


@dataclass(frozen=True)
class UnidadDocumentalCandidata:
    id: str
    lote_id: str
    item_ingesta_id: str
    procedencia: ProcedenciaHeredada
    estado: EstadoUnidadDocumental
    huella_de_contenido: str | None = None
    sugerencia_de_limites: SugerenciaDeLimites | None = None
    confirmacion_limites: ConfirmacionHumanaDeLimites | None = None
    razon: str | None = None
    formato_normalizado: str | None = None


# RF-NO-001/003: recibir un ítem validado desde Captura/Ingesta genera una
# unidad documental candidata. El caso trivial (un artefacto = un documento) lo
# declara explícitamente el llamador: el mecanismo automático para decidirlo
# queda [CLARIFICAR] en la spec (§8) — no se inventa una detección real donde
# la spec no la resuelve, mismo criterio que CondicionValidacion en
# captura-ingesta.
def recibir_item(
    id: str,
    lote_id: str,
    item_ingesta_id: str,
    procedencia: ProcedenciaHeredada,
    huella_de_contenido: str | None,
    es_caso_trivial: bool,
) -> UnidadDocumentalCandidata:
    estado = (
        EstadoUnidadDocumental.LIMITES_CONFIRMADOS
        if es_caso_trivial
        else EstadoUnidadDocumental.PENDIENTE_DE_LIMITES
    )
    return UnidadDocumentalCandidata(
        id=id,
        lote_id=lote_id,
        item_ingesta_id=item_ingesta_id,
        procedencia=procedencia,
        huella_de_contenido=huella_de_contenido,
        estado=estado,
    )


def recibir_sugerencia_de_limites(
    unidad: UnidadDocumentalCandidata, sugerencia: SugerenciaDeLimites
) -> UnidadDocumentalCandidata:
    if unidad.estado != EstadoUnidadDocumental.PENDIENTE_DE_LIMITES:
        raise ErrorDeDominio(f"La unidad '{unidad.id}' no está pendiente de límites.")
    return replace(unidad, sugerencia_de_limites=sugerencia)


# RF-NO-004: una sugerencia nunca separa documentos por sí sola (P-01); esta es
# la única función que mueve una unidad a Límites confirmados cuando el caso
# no es trivial. Cierra el ciclo que RF-VH-005 dejó abierto
# (spec-infra-servicios.md §9): Validación Humana llama a esto por HTTP.
def confirmar_limites(
    unidad: UnidadDocumentalCandidata, actor: str, fecha: datetime
) -> UnidadDocumentalCandidata:
    if unidad.estado != EstadoUnidadDocumental.PENDIENTE_DE_LIMITES:
        raise ErrorDeDominio(f"La unidad '{unidad.id}' no está pendiente de límites.")
    return replace(
        unidad,
        estado=EstadoUnidadDocumental.LIMITES_CONFIRMADOS,
        confirmacion_limites=ConfirmacionHumanaDeLimites(actor=actor, fecha=fecha),
    )


# RF-NO-005: el formato de preservación exacto queda [CLARIFICAR] (spec §8,
# ningún estándar decidido); esta función no inventa una conversión real, solo
# transiciona el estado y conserva una referencia honesta a la forma
# "normalizada" — nunca finge un formato que nadie decidió.
def normalizar(unidad: UnidadDocumentalCandidata, formato_normalizado: str) -> UnidadDocumentalCandidata:
    if unidad.estado != EstadoUnidadDocumental.LIMITES_CONFIRMADOS:
        raise ErrorDeDominio(f"La unidad '{unidad.id}' no tiene límites confirmados.")
    return replace(unidad, estado=EstadoUnidadDocumental.NORMALIZADA, formato_normalizado=formato_normalizado)


# RF-NO-009: mismo criterio que RF-CI-006 (T-02): recuperable dentro del
# sistema actual -> En cuarentena; solo recuperable con artefacto nuevo o
# cambio de sistema -> Rechazada.
def marcar_cuarentena_o_rechazo(
    unidad: UnidadDocumentalCandidata, condicion: CondicionDeNormalizacion
) -> UnidadDocumentalCandidata:
    if condicion == CondicionDeNormalizacion.CORRUPTO:
        estado = EstadoUnidadDocumental.EN_CUARENTENA
        razon = "Artefacto corrupto: requiere reescaneo o confirmación manual."
    elif condicion == CondicionDeNormalizacion.ILEGIBLE:
        estado = EstadoUnidadDocumental.EN_CUARENTENA
        razon = "Artefacto ilegible: requiere juicio de calidad humano."
    else:
        estado = EstadoUnidadDocumental.RECHAZADA
        razon = "Formato no soportado: requiere un artefacto nuevo o soporte de formato añadido al sistema."
    return replace(unidad, estado=estado, razon=razon)


# RF-NO-006/010: entrega a Extracción; una unidad cuyo contenido ya entregó
# otra unidad queda vinculada al duplicado en vez de entregarse otra vez.
def entregar(
    unidad: UnidadDocumentalCandidata, huellas_ya_entregadas: set[str]
) -> UnidadDocumentalCandidata:
    if unidad.estado != EstadoUnidadDocumental.NORMALIZADA:
        raise ErrorDeDominio(f"La unidad '{unidad.id}' no está normalizada.")
    if unidad.huella_de_contenido is not None and unidad.huella_de_contenido in huellas_ya_entregadas:
        return replace(unidad, estado=EstadoUnidadDocumental.VINCULADA_A_DUPLICADO)
    return replace(unidad, estado=EstadoUnidadDocumental.ENTREGADA_A_EXTRACCION)


_ESTADOS_TERMINALES = {
    EstadoUnidadDocumental.ENTREGADA_A_EXTRACCION,
    EstadoUnidadDocumental.RECHAZADA,
    EstadoUnidadDocumental.EN_CUARENTENA,
    EstadoUnidadDocumental.VINCULADA_A_DUPLICADO,
}


# RF-NO-008: cero pérdida silenciosa — mismo patrón que ConteoPorEstado en
# captura-ingesta (T-05).
@dataclass(frozen=True)
class ConteoPorEstado:
    por_estado: dict[EstadoUnidadDocumental, int]
    total: int

    @property
    def terminales(self) -> int:
        return sum(self.por_estado.get(estado, 0) for estado in _ESTADOS_TERMINALES)

    @property
    def sin_perdida_silenciosa(self) -> bool:
        return self.terminales == self.total


def contar_por_estado(unidades: list[UnidadDocumentalCandidata]) -> ConteoPorEstado:
    por_estado: dict[EstadoUnidadDocumental, int] = {}
    for unidad in unidades:
        por_estado[unidad.estado] = por_estado.get(unidad.estado, 0) + 1
    return ConteoPorEstado(por_estado=por_estado, total=len(unidades))
