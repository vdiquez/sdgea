from dataclasses import dataclass, replace
from datetime import datetime
from enum import Enum


# `str, Enum` por el mismo motivo que en normalizacion/dominio.py (T-33): el valor
# JSON es el nombre del miembro, para que la futura capa HTTP (T-41) serialice igual
# que el resto de contextos.
class EstadoTextoExtraido(str, Enum):
    PENDIENTE_DE_EXTRACCION = "PENDIENTE_DE_EXTRACCION"
    EXTRAIDO = "EXTRAIDO"
    RECHAZADO = "RECHAZADO"
    EN_CUARENTENA = "EN_CUARENTENA"


# RF-EX-002: born-digital extrae de forma determinística; escaneo invoca el
# componente probabilístico de OCR (invariante 2 de la spec §3).
class Soporte(str, Enum):
    BORN_DIGITAL = "BORN_DIGITAL"
    ESCANEO = "ESCANEO"


# RF-EX-009: mismo criterio que CondicionDeNormalizacion (normalizacion/dominio.py,
# T-33) y CondicionValidacion (captura-ingesta, T-02): el llamador declara la
# condición, el dominio no la detecta. El mapeo condición -> rama terminal es el
# mismo ya ratificado por Victor para RF-CI-006 (QUESTIONS.md, 2026-08-23) y
# reaplicado aquí porque tanto RF-EX-009 como TODO.md (T-40) lo piden
# explícitamente ("mismo criterio que RF-CI-006/RF-NO-009") — no es una taxonomía
# nueva inventada para este contexto.
class CondicionDeExtraccion(str, Enum):
    CORRUPTO = "CORRUPTO"
    ILEGIBLE = "ILEGIBLE"
    FORMATO_NO_SOPORTADO = "FORMATO_NO_SOPORTADO"


class ErrorDeDominio(Exception):
    pass


# RF-EX-007/invariante 1: procedencia heredada transitivamente desde Captura/Ingesta
# hasta la unidad documental candidata de Normalización; mismo shape que
# ProcedenciaHeredada en normalizacion/dominio.py, con el id de la unidad de origen
# añadido porque Extracción rastrea hasta ESA unidad, no hasta el ítem de ingesta
# directamente.
@dataclass(frozen=True)
class ProcedenciaHeredada:
    fuente: str
    fecha: datetime
    disparador: str
    lote_o_flujo_id: str
    item_ingesta_id: str
    unidad_documental_id: str


# RF-EX-004: componente FICTICIO (constitución, disciplina de alcance) — esta clase
# solo transporta una SUGERENCIA de OCR YA CALCULADA por el llamador; ningún código
# de este contexto ejecuta un motor de OCR real. VETO real de Codex sobre la
# segunda versión de este archivo (commit e623ad6, ver REVIEW.md/QUESTIONS.md):
# un `ResultadoOcr` sin `evidencia` no cruzaba la capa anticorrupción "como
# Sugerencia" (P-01) — aplazar la materialización con `confirmar_extraccion` no
# bastaba si lo que se adjuntaba al agregado seguía sin la forma de una
# Sugerencia. Renombrado y con `evidencia` para calzar exactamente el mismo
# shape que `SugerenciaDeLimites` en normalizacion y `Sugerencia` en
# records-custodia — la única diferencia real es que esta sugerencia también
# porta el `contenido` propuesto (el texto que el OCR leyó), inevitable porque
# eso es justamente lo que OCR sugiere.
@dataclass(frozen=True)
class SugerenciaOcr:
    modelo_id: str
    contenido: str
    calidad: float
    evidencia: list[str]
    fecha: datetime


@dataclass(frozen=True)
class TextoExtraido:
    id: str
    unidad_documental_candidata_id: str
    procedencia: ProcedenciaHeredada
    estado: EstadoTextoExtraido
    soporte: Soporte | None = None
    contenido: str | None = None
    calidad: float | None = None
    razon: str | None = None
    sugerencia_ocr: SugerenciaOcr | None = None


# P-08 desde el primer commit de este contexto (lección de T-37/V-01: en
# Normalización se agregó como corrección posterior; aquí no se repite ese error).
# Mismo shape que EventoAuditoria en normalizacion/dominio.py.
@dataclass(frozen=True)
class EventoAuditoria:
    actor: str
    fecha: datetime
    tipo: str
    estado_anterior: str | None
    estado_posterior: str | None


# RF-EX-001/invariante 1: toda unidad documental candidata que Normalización entrega
# genera un texto extraído en Pendiente de extracción, conservando su procedencia.
def recibir_unidad(
    id: str,
    unidad_documental_candidata_id: str,
    procedencia: ProcedenciaHeredada,
    actor: str,
    fecha: datetime,
) -> tuple[TextoExtraido, EventoAuditoria]:
    texto = TextoExtraido(
        id=id,
        unidad_documental_candidata_id=unidad_documental_candidata_id,
        procedencia=procedencia,
        estado=EstadoTextoExtraido.PENDIENTE_DE_EXTRACCION,
    )
    evento = EventoAuditoria(
        actor=actor,
        fecha=fecha,
        tipo="TEXTO_EXTRAIDO_RECIBIDO",
        estado_anterior=None,
        estado_posterior=texto.estado.value,
    )
    return texto, evento


# RF-EX-002/invariante 2: determina el mecanismo de extracción antes de aplicarlo.
# No cambia el estado del texto (sigue Pendiente de extracción); solo lo marca.
# P-08 (hallazgo real de Codex sobre commit e623ad6): estado_anterior/posterior
# deben representar honestamente el estado del agregado, nunca un sentinel que
# no es un valor real de `EstadoTextoExtraido` — aquí ambos son el mismo valor
# porque esta transición no cambia el estado.
def determinar_soporte(
    texto: TextoExtraido, soporte: Soporte, actor: str, fecha: datetime
) -> tuple[TextoExtraido, EventoAuditoria]:
    if texto.estado != EstadoTextoExtraido.PENDIENTE_DE_EXTRACCION:
        raise ErrorDeDominio(f"El texto extraído '{texto.id}' no está pendiente de extracción.")
    actualizado = replace(texto, soporte=soporte)
    evento = EventoAuditoria(
        actor=actor,
        fecha=fecha,
        tipo="SOPORTE_DETERMINADO",
        estado_anterior=texto.estado.value,
        estado_posterior=actualizado.estado.value,
    )
    return actualizado, evento


# RF-EX-003: para born-digital el texto ya embebido se extrae de forma
# determinística, sin invocar OCR. Calidad máxima (1.0) porque el propio criterio
# Dado/Cuando/Entonces del RF lo exige literalmente ("Extraído con calidad máxima");
# no es un umbral de negocio inventado, es el extremo superior de la misma escala
# 0..1 que ya usa SugerenciaDeLimites.confianza en normalizacion.
def extraer_texto_born_digital(
    texto: TextoExtraido, contenido: str, actor: str, fecha: datetime
) -> tuple[TextoExtraido, EventoAuditoria]:
    if texto.soporte != Soporte.BORN_DIGITAL:
        raise ErrorDeDominio(f"El texto extraído '{texto.id}' no está marcado como born-digital.")
    if texto.estado != EstadoTextoExtraido.PENDIENTE_DE_EXTRACCION:
        raise ErrorDeDominio(f"El texto extraído '{texto.id}' no está pendiente de extracción.")
    estado_anterior = texto.estado.value
    actualizado = replace(texto, estado=EstadoTextoExtraido.EXTRAIDO, contenido=contenido, calidad=1.0)
    evento = EventoAuditoria(
        actor=actor,
        fecha=fecha,
        tipo="EXTRACCION_DETERMINISTICA_APLICADA",
        estado_anterior=estado_anterior,
        estado_posterior=actualizado.estado.value,
    )
    return actualizado, evento


# RF-EX-004: componente FICTICIO — recibe una SUGERENCIA de OCR YA CALCULADA por
# el llamador. Dos VETOs reales de Codex sobre versiones anteriores de este
# archivo (ver REVIEW.md/QUESTIONS.md): (1, commit dd97fb4) un resultado
# probabilístico NO puede materializar el estado por sí solo (P-01) — a
# diferencia de extraer_texto_born_digital (determinístico, P-01 no aplica
# ahí); (2, commit e623ad6) aplazar la materialización no basta si lo que se
# adjunta al agregado no tiene forma de Sugerencia (sin evidencia, sin cruzar
# una capa anticorrupción real). Esta función solo ADJUNTA la sugerencia al
# agregado — no cambia su estado — hasta que `confirmar_extraccion` la
# confirma explícitamente: mismo patrón de dos pasos que
# recibir_sugerencia_de_limites/confirmar_limites en normalizacion
# (RF-NO-002/004). Decisión de Victor, 2026-08-27 (QUESTIONS.md): esta
# confirmación humana es obligatoria para TODA sugerencia de OCR, no solo las
# de baja confianza — el enrutamiento por calidad
# (RF-EX-006/candidatas_a_revision_por_baja_confianza) es un control adicional
# posterior a la confirmación, no un sustituto de ella.
def recibir_sugerencia_ocr(
    texto: TextoExtraido, sugerencia: SugerenciaOcr
) -> tuple[TextoExtraido, EventoAuditoria]:
    if texto.soporte != Soporte.ESCANEO:
        raise ErrorDeDominio(f"El texto extraído '{texto.id}' no está marcado como escaneo.")
    if texto.estado != EstadoTextoExtraido.PENDIENTE_DE_EXTRACCION:
        raise ErrorDeDominio(f"El texto extraído '{texto.id}' no está pendiente de extracción.")
    actualizado = replace(texto, sugerencia_ocr=sugerencia)
    evento = EventoAuditoria(
        actor=sugerencia.modelo_id,
        fecha=sugerencia.fecha,
        tipo="SUGERENCIA_OCR_RECIBIDA",
        estado_anterior=texto.estado.value,
        estado_posterior=actualizado.estado.value,
    )
    return actualizado, evento


# RF-EX-011 (decisión de Victor, 2026-08-27, ver QUESTIONS.md — corrige el VETO
# de Codex sobre commit dd97fb4, ampliado en e623ad6): única función que puede
# materializar un texto extraído a partir de una sugerencia de OCR — mismo
# criterio que confirmar_limites en normalizacion (RF-NO-004) y materializar
# en records-custodia (RF-RC-004): nada probabilístico escribe estado por sí
# solo (P-01). No admite corregir el contenido aquí (solo confirmar la
# sugerencia adjunta tal cual): si un texto de baja confianza necesita
# corrección humana del contenido, ese mecanismo sigue [CLARIFICAR] en
# specs/002-extraccion/spec.md §8, no se inventa uno aquí.
def confirmar_extraccion(
    texto: TextoExtraido, actor: str, fecha: datetime
) -> tuple[TextoExtraido, EventoAuditoria]:
    if texto.sugerencia_ocr is None:
        raise ErrorDeDominio(f"El texto extraído '{texto.id}' no tiene una sugerencia de OCR pendiente de confirmar.")
    if texto.estado != EstadoTextoExtraido.PENDIENTE_DE_EXTRACCION:
        raise ErrorDeDominio(f"El texto extraído '{texto.id}' no está pendiente de extracción.")
    estado_anterior = texto.estado.value
    actualizado = replace(
        texto,
        estado=EstadoTextoExtraido.EXTRAIDO,
        contenido=texto.sugerencia_ocr.contenido,
        calidad=texto.sugerencia_ocr.calidad,
    )
    evento = EventoAuditoria(
        actor=actor,
        fecha=fecha,
        tipo="EXTRACCION_CONFIRMADA",
        estado_anterior=estado_anterior,
        estado_posterior=actualizado.estado.value,
    )
    return actualizado, evento


# RF-EX-009: mismo criterio que marcar_cuarentena_o_rechazo en normalizacion
# (RF-NO-009) y validar en captura-ingesta (RF-CI-006): recuperable dentro del
# sistema actual (reescaneo) -> En cuarentena; solo recuperable con artefacto nuevo
# -> Rechazado. Precondición de estado (hallazgo real de Codex sobre commit
# dd97fb4): las tres ramas terminales (Extraído/Rechazado/En cuarentena) no
# admiten otra transición — spec §3, "Ramas terminales alternativas". Sin este
# chequeo, un texto ya Extraído podría "revalidarse" a Rechazado, violando la
# invariante de estado terminal.
def marcar_cuarentena_o_rechazo(
    texto: TextoExtraido, condicion: CondicionDeExtraccion, actor: str, fecha: datetime
) -> tuple[TextoExtraido, EventoAuditoria]:
    if texto.estado != EstadoTextoExtraido.PENDIENTE_DE_EXTRACCION:
        raise ErrorDeDominio(f"El texto extraído '{texto.id}' no está pendiente de extracción.")
    if condicion == CondicionDeExtraccion.CORRUPTO:
        estado = EstadoTextoExtraido.EN_CUARENTENA
        razon = "Artefacto corrupto: requiere reescaneo."
    elif condicion == CondicionDeExtraccion.ILEGIBLE:
        estado = EstadoTextoExtraido.EN_CUARENTENA
        razon = "Artefacto ilegible: requiere reescaneo o revisión humana."
    else:
        estado = EstadoTextoExtraido.RECHAZADO
        razon = "Formato no soportado: requiere un artefacto nuevo o soporte de formato añadido al sistema."
    estado_anterior = texto.estado.value
    actualizado = replace(texto, estado=estado, razon=razon)
    evento = EventoAuditoria(
        actor=actor, fecha=fecha, tipo="VALIDACION_APLICADA", estado_anterior=estado_anterior, estado_posterior=estado.value
    )
    return actualizado, evento


# RF-EX-006: umbral RECIBIDO COMO PARÁMETRO, nunca inventado (spec §8 [CLARIFICAR]:
# "umbral de calidad... se calibra con el arnés"). Mismo criterio que
# candidatasAAprobacionMasiva en Validación Humana.
def candidatas_a_revision_por_baja_confianza(textos: list[TextoExtraido], umbral: float) -> list[TextoExtraido]:
    return [
        texto
        for texto in textos
        if texto.estado == EstadoTextoExtraido.EXTRAIDO and texto.calidad is not None and texto.calidad < umbral
    ]


# RF-EX-010: el mismo texto extraído se entrega a Clasificación, Enriquecimiento e
# Indexación y Búsqueda sin diferenciar por consumidor (no hay estado distinto por
# destino, mismo criterio que records-custodia entrega el documento materializado a
# varios destinos sin duplicar su modelo de estados). Conserva procedencia y calidad
# porque `replace()` nunca las toca en ninguna transición previa.
def entregar(texto: TextoExtraido) -> TextoExtraido:
    if texto.estado != EstadoTextoExtraido.EXTRAIDO:
        raise ErrorDeDominio(f"El texto extraído '{texto.id}' no está Extraído.")
    return texto


_ESTADOS_TERMINALES = {
    EstadoTextoExtraido.EXTRAIDO,
    EstadoTextoExtraido.RECHAZADO,
    EstadoTextoExtraido.EN_CUARENTENA,
}


# RF-EX-008: cero pérdida silenciosa — mismo patrón que ConteoPorEstado en
# captura-ingesta (T-05) y normalizacion (T-33).
@dataclass(frozen=True)
class ConteoPorEstado:
    por_estado: dict[EstadoTextoExtraido, int]
    total: int

    @property
    def terminales(self) -> int:
        return sum(self.por_estado.get(estado, 0) for estado in _ESTADOS_TERMINALES)

    @property
    def sin_perdida_silenciosa(self) -> bool:
        return self.terminales == self.total


def contar_por_estado(textos: list[TextoExtraido]) -> ConteoPorEstado:
    por_estado: dict[EstadoTextoExtraido, int] = {}
    for texto in textos:
        por_estado[texto.estado] = por_estado.get(texto.estado, 0) + 1
    return ConteoPorEstado(por_estado=por_estado, total=len(textos))
