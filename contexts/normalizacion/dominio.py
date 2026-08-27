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


# P-08 (hallazgo V-01 de la revisión acumulada de Codex, 2026-08-27, ver
# REVIEW.md): toda transición de estado debe generar un evento de auditoría
# inmutable, atribuible y fechado, con estado anterior y posterior — mismo
# criterio que `EventoAuditoria`/`BitacoraAuditoria` en records-custodia
# (RF-RC-005) y `EventoSeguridad`/`BitacoraSeguridad` en seguridad-acceso.
# Cada función de transición de este módulo ahora devuelve la unidad
# actualizada JUNTO con el evento que la describe — dominio.py sigue siendo
# funciones puras sin estado ni dependencias (a diferencia de las clases
# Kotlin, que sostienen una bitácora inyectada); es la capa de persistencia
# (persistencia.py) quien debe anexar ambos en una sola transacción, para no
# recrear el riesgo de atomicidad que T-21/T-22 corrigieron en Kotlin.
@dataclass(frozen=True)
class EventoAuditoria:
    actor: str
    fecha: datetime
    tipo: str
    estado_anterior: str | None
    estado_posterior: str | None


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
    actor: str,
) -> tuple[UnidadDocumentalCandidata, EventoAuditoria]:
    estado = (
        EstadoUnidadDocumental.LIMITES_CONFIRMADOS
        if es_caso_trivial
        else EstadoUnidadDocumental.PENDIENTE_DE_LIMITES
    )
    unidad = UnidadDocumentalCandidata(
        id=id,
        lote_id=lote_id,
        item_ingesta_id=item_ingesta_id,
        procedencia=procedencia,
        huella_de_contenido=huella_de_contenido,
        estado=estado,
    )
    evento = EventoAuditoria(
        actor=actor, fecha=procedencia.fecha, tipo="UNIDAD_RECIBIDA", estado_anterior=None, estado_posterior=estado.value
    )
    return unidad, evento


# RF-NO-002: el actor del evento es `sugerencia.modelo_id` — mismo criterio que
# T-20 usó para `SUGERENCIA_RECIBIDA` en records-custodia ("modeloId como actor
# de sistema atribuible", dato ya existente en el contrato, sin inventar un
# campo nuevo). `estado_posterior` es un sentinel ("SUGERENCIA_DE_LIMITES_
# RECIBIDA"), no el estado real de la unidad: la sugerencia no cambia el
# estado por sí sola (P-01, invariante 2), así que el evento no debe sugerir
# que sí lo hizo.
def recibir_sugerencia_de_limites(
    unidad: UnidadDocumentalCandidata, sugerencia: SugerenciaDeLimites
) -> tuple[UnidadDocumentalCandidata, EventoAuditoria]:
    if unidad.estado != EstadoUnidadDocumental.PENDIENTE_DE_LIMITES:
        raise ErrorDeDominio(f"La unidad '{unidad.id}' no está pendiente de límites.")
    actualizada = replace(unidad, sugerencia_de_limites=sugerencia)
    evento = EventoAuditoria(
        actor=sugerencia.modelo_id,
        fecha=sugerencia.fecha,
        tipo="SUGERENCIA_DE_LIMITES_RECIBIDA",
        estado_anterior=None,
        estado_posterior="SUGERENCIA_DE_LIMITES_RECIBIDA",
    )
    return actualizada, evento


# RF-NO-004: una sugerencia nunca separa documentos por sí sola (P-01); esta es
# la única función que mueve una unidad a Límites confirmados cuando el caso
# no es trivial. Cierra el ciclo que RF-VH-005 dejó abierto
# (spec-infra-servicios.md §9): Validación Humana llama a esto por HTTP.
def confirmar_limites(
    unidad: UnidadDocumentalCandidata, actor: str, fecha: datetime
) -> tuple[UnidadDocumentalCandidata, EventoAuditoria]:
    if unidad.estado != EstadoUnidadDocumental.PENDIENTE_DE_LIMITES:
        raise ErrorDeDominio(f"La unidad '{unidad.id}' no está pendiente de límites.")
    estado_anterior = unidad.estado.value
    actualizada = replace(
        unidad,
        estado=EstadoUnidadDocumental.LIMITES_CONFIRMADOS,
        confirmacion_limites=ConfirmacionHumanaDeLimites(actor=actor, fecha=fecha),
    )
    evento = EventoAuditoria(
        actor=actor,
        fecha=fecha,
        tipo="LIMITES_CONFIRMADOS",
        estado_anterior=estado_anterior,
        estado_posterior=actualizada.estado.value,
    )
    return actualizada, evento


# RF-NO-005: el formato de preservación exacto queda [CLARIFICAR] (spec §8,
# ningún estándar decidido); esta función no inventa una conversión real, solo
# transiciona el estado y conserva una referencia honesta a la forma
# "normalizada" — nunca finge un formato que nadie decidió. `actor`/`fecha`
# identifican quién o qué disparó la normalización (puede ser un componente de
# sistema), mismo criterio que `verificarIntegridad(id, actor, fecha)` en
# records-custodia para operaciones sin decisión humana pero igual auditables.
def normalizar(
    unidad: UnidadDocumentalCandidata, formato_normalizado: str, actor: str, fecha: datetime
) -> tuple[UnidadDocumentalCandidata, EventoAuditoria]:
    if unidad.estado != EstadoUnidadDocumental.LIMITES_CONFIRMADOS:
        raise ErrorDeDominio(f"La unidad '{unidad.id}' no tiene límites confirmados.")
    estado_anterior = unidad.estado.value
    actualizada = replace(unidad, estado=EstadoUnidadDocumental.NORMALIZADA, formato_normalizado=formato_normalizado)
    evento = EventoAuditoria(
        actor=actor,
        fecha=fecha,
        tipo="UNIDAD_NORMALIZADA",
        estado_anterior=estado_anterior,
        estado_posterior=actualizada.estado.value,
    )
    return actualizada, evento


# RF-NO-009: mismo criterio que RF-CI-006 (T-02): recuperable dentro del
# sistema actual -> En cuarentena; solo recuperable con artefacto nuevo o
# cambio de sistema -> Rechazada.
def marcar_cuarentena_o_rechazo(
    unidad: UnidadDocumentalCandidata, condicion: CondicionDeNormalizacion, actor: str, fecha: datetime
) -> tuple[UnidadDocumentalCandidata, EventoAuditoria]:
    if condicion == CondicionDeNormalizacion.CORRUPTO:
        estado = EstadoUnidadDocumental.EN_CUARENTENA
        razon = "Artefacto corrupto: requiere reescaneo o confirmación manual."
    elif condicion == CondicionDeNormalizacion.ILEGIBLE:
        estado = EstadoUnidadDocumental.EN_CUARENTENA
        razon = "Artefacto ilegible: requiere juicio de calidad humano."
    else:
        estado = EstadoUnidadDocumental.RECHAZADA
        razon = "Formato no soportado: requiere un artefacto nuevo o soporte de formato añadido al sistema."
    estado_anterior = unidad.estado.value
    actualizada = replace(unidad, estado=estado, razon=razon)
    evento = EventoAuditoria(
        actor=actor, fecha=fecha, tipo="VALIDACION_APLICADA", estado_anterior=estado_anterior, estado_posterior=estado.value
    )
    return actualizada, evento


# RF-NO-006/010: entrega a Extracción; una unidad cuyo contenido ya entregó
# otra unidad queda vinculada al duplicado en vez de entregarse otra vez.
def entregar(
    unidad: UnidadDocumentalCandidata, huellas_ya_entregadas: set[str], actor: str, fecha: datetime
) -> tuple[UnidadDocumentalCandidata, EventoAuditoria]:
    if unidad.estado != EstadoUnidadDocumental.NORMALIZADA:
        raise ErrorDeDominio(f"La unidad '{unidad.id}' no está normalizada.")
    estado_anterior = unidad.estado.value
    if unidad.huella_de_contenido is not None and unidad.huella_de_contenido in huellas_ya_entregadas:
        actualizada = replace(unidad, estado=EstadoUnidadDocumental.VINCULADA_A_DUPLICADO)
    else:
        actualizada = replace(unidad, estado=EstadoUnidadDocumental.ENTREGADA_A_EXTRACCION)
    evento = EventoAuditoria(
        actor=actor,
        fecha=fecha,
        tipo="ENTREGA_PROCESADA",
        estado_anterior=estado_anterior,
        estado_posterior=actualizada.estado.value,
    )
    return actualizada, evento


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


# RF-VH-001 (specs/007-validacion-humana/spec.md, T-39): Validación Humana
# necesita agregar sugerencias de límites que todavía no tienen confirmación
# humana, a través de todas las unidades, no de una a la vez (mismo criterio
# que `documentosSinClasificar`/`sugerenciasPendientes` en records-custodia,
# T-28). Una unidad sin `sugerencia_de_limites` todavía no tiene nada que un
# humano pueda revisar, así que no cuenta como pendiente de revisión.
def pendientes_de_limites(unidades: list[UnidadDocumentalCandidata]) -> list[UnidadDocumentalCandidata]:
    return [
        unidad
        for unidad in unidades
        if unidad.estado == EstadoUnidadDocumental.PENDIENTE_DE_LIMITES and unidad.sugerencia_de_limites is not None
    ]
