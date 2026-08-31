from dataclasses import dataclass, field, replace
from datetime import datetime
from enum import Enum
from typing import Protocol, TypeVar


class ErrorDeDominio(Exception):
    pass


# `str, Enum` por el mismo motivo que en normalizacion/dominio.py (T-33): el valor
# JSON es el nombre del miembro, para que la futura capa HTTP (T-55) serialice
# igual que el resto de contextos. Sin estado de eliminación (spec §3: "la
# eliminación definitiva... queda fuera de alcance de la Etapa 0").
class EstadoEntradaDeIndice(str, Enum):
    PENDIENTE_DE_INDEXACION = "PENDIENTE_DE_INDEXACION"
    INDEXADA = "INDEXADA"


# P-08 desde el primer commit de este contexto (lección de T-37/V-01): cada
# transición de EntradaDeIndice devuelve también un EventoAuditoria. Mismo
# shape que en normalizacion/extraccion — actor, fecha, tipo y estado
# anterior/posterior, porque esta transición SÍ tiene una máquina de estados
# (a diferencia de clasificacion/enriquecimiento, que no persisten agregado).
@dataclass(frozen=True)
class EventoAuditoria:
    actor: str
    fecha: datetime
    tipo: str
    estado_anterior: str | None
    estado_posterior: str | None


# RF-IB-009: distinto de EventoAuditoria — el criterio Dado/Cuando/Entonces
# pide literalmente "actor, fecha y los documentos accedidos", no un estado
# anterior/posterior (una consulta no transiciona ningún estado de dominio).
# Forzar el mismo shape que EventoAuditoria habría exigido inventar un
# estado_anterior/posterior ficticio sin sentido para una consulta de solo
# lectura; en vez de eso, este es un segundo tipo de evento con la forma que
# el propio RF exige, igual de "evento de auditoría" a efectos de P-08.
@dataclass(frozen=True)
class EventoDeAcceso:
    actor: str
    fecha: datetime
    tipo: str
    # VETO real de Codex sobre T-54 (commit 22b6b09, ver STATE.md): `frozen=True`
    # impide reasignar el atributo, pero NO impide mutar un `list` referenciado
    # por él (`evento.documentos_accedidos.append(...)` habría funcionado sin
    # error) -- un evento de auditoría que puede mutarse en silencio no es
    # inmutable de verdad. `tuple` sí lo es.
    documentos_accedidos: tuple[str, ...]


# P-03 (corrige el VETO real de Codex sobre la siembra original de T-54,
# commit c8f47d7, ver REVIEW.md/STATE.md): la spec §1 nombra CUATRO
# capacidades externas para este contexto — índice léxico, índice vectorial,
# embeddings e inferencia LLM — y las cuatro se abstraen aquí, no solo las
# probabilísticas (P-03 abstrae la capacidad externa en sí, sea o no
# determinística; mismo criterio que AlmacenDeUnidades en normalizacion, que
# abstrae persistencia aunque persistir sea determinístico).
#
# Ninguna función pura de este módulo invoca estos puertos: reciben ya
# resueltos los candidatos/embeddings/respuestas que T-55 obtuvo a través de
# ellos (mismo criterio que EnviadorDeSugerencias en clasificacion/
# enriquecimiento, cuyas implementaciones tampoco viven en dominio.py). Se
# declaran aquí por completitud de P-03; T-55 aporta dos variantes de
# despliegue reales por cada uno (autoalojada y gestionada, RNF-IB-002), sin
# nombrar ningún motor ni modelo concreto (el `[CLARIFICAR]` de motor/modelo
# sigue abierto en la spec §8).
class IndiceLexico(Protocol):
    def indexar(self, entrada_id: str, contenido: str) -> None: ...
    # Devuelve entradas completas (no solo ids): `buscar()` en este módulo
    # necesita `estado`/`metadatos` para aplicar sus propios filtros, y pedir
    # una segunda vuelta de consulta solo para "completar" cada id sería un
    # segundo puerto de facto. Real (RF-IB-002/005) — nunca FICTICIO.
    def buscar(self, termino: str) -> list["EntradaDeIndice"]: ...


class IndiceVectorial(Protocol):
    def indexar(self, entrada_id: str, embedding: list[float]) -> None: ...
    # RF-IB-003 ("su contenido es recuperable por similitud semántica"):
    # recuperar exige una operación de lectura real, no solo de escritura.
    # VETO real de Codex sobre 5a9f822 (ver STATE.md): la primera versión de
    # `IndiceVectorialAutoalojado.indexar()` era un `pass` -- el embedding se
    # persistía por otra ruta (`AlmacenDeEntradas`), así que el puerto era una
    # fachada vacía que nunca hacía trabajo real ni era genuinamente
    # intercambiable. `obtener()` fuerza que la implementación real tenga
    # almacenamiento propio y consultable.
    def obtener(self, entrada_id: str) -> list[float] | None: ...


# FICTICIO (constitución, disciplina de alcance) — ninguna implementación de
# este puerto calcula un embedding real; existe solo para que el seam de P-03
# esté completo. El llamador entrega el embedding YA CALCULADO a `indexar()`.
class GeneradorDeEmbeddings(Protocol):
    def generar(self, texto: str) -> list[float]: ...


# FICTICIO, mismo criterio que GeneradorDeEmbeddings — el llamador entrega la
# respuesta y las citas YA CALCULADAS a `responder_qa()`.
class ModeloDeLenguaje(Protocol):
    def responder(self, pregunta: str, contexto: list[str]) -> str: ...


# P-03 (corrige el VETO real de Codex sobre la primera implementación de
# T-54, commit 22b6b09, ver STATE.md): "documentos_permitidos" ya resuelto
# como `set` no exigía que ninguna función de dominio invocara el puerto —
# mismo defecto que Codex señaló ("ninguna ejercita esos puertos"). Corregido
# con el mismo patrón que `VerificadorDeAutorizacion` en extracción
# (T-41b, `confirmar_extraccion`, ya validado por Codex sin VETO): las
# funciones de consulta reciben el puerto y lo invocan ellas mismas, por
# candidato — P-03 real, no solo declarado. Seguridad y Acceso no expone un
# endpoint de "lista de documentos permitidos" (specs/spec-infra-servicios.md
# §5: `POST /autorizacion` es por recurso), así que "por candidato" es el
# único contrato que existe, no una elección de conveniencia.
class VerificadorDePermisos(Protocol):
    def tiene_permiso(self, actor: str, documento_id: str) -> bool: ...


# RF-IB-001/002/003/[CLARIFICAR] de correlación (spec §8): el llamador entrega
# el documento materializado y su texto extraído YA CORRELACIONADOS (mismo
# criterio que "el llamador declara la condición" en los demás contextos) —
# el dominio no decide cómo se obtuvo esa correlación. `metadatos` es un dict
# libre (serie, subserie, fecha, campos de Enriquecimiento...) — nunca se fija
# aquí un nombre de campo real, mismo criterio que Enriquecimiento (spec §8,
# esquema de metadatos [CLARIFICAR]).
@dataclass(frozen=True)
class DocumentoParaIndexar:
    documento_id: str
    texto_extraido: str
    metadatos: dict[str, str]


def recibir_documento_materializado(
    documento_id: str, texto_extraido: str, metadatos: dict[str, str]
) -> DocumentoParaIndexar:
    return DocumentoParaIndexar(documento_id=documento_id, texto_extraido=texto_extraido, metadatos=metadatos)


# Agregado raíz (spec §3). `texto_extraido`/`metadatos`/`embedding` quedan
# `None`/vacíos mientras la entrada está Pendiente de indexación —
# `indexar()` es la única función que los puebla, junto con el estado.
@dataclass(frozen=True)
class EntradaDeIndice:
    id: str
    documento_id: str
    estado: EstadoEntradaDeIndice
    texto_extraido: str | None = None
    metadatos: dict[str, str] = field(default_factory=dict)
    embedding: list[float] | None = None
    fecha_indexacion: datetime | None = None


# Construye la entrada en Pendiente de indexación (spec §3, estado inicial) —
# la "recepción" del documento materializado es su propio paso, previo a la
# indexación real (RF-IB-001/002/003, función `indexar` de abajo).
def crear_entrada_pendiente(
    entrada_id: str, documento: DocumentoParaIndexar, actor: str, fecha: datetime
) -> tuple[EntradaDeIndice, EventoAuditoria]:
    entrada = EntradaDeIndice(
        id=entrada_id, documento_id=documento.documento_id, estado=EstadoEntradaDeIndice.PENDIENTE_DE_INDEXACION
    )
    evento = EventoAuditoria(
        actor=actor,
        fecha=fecha,
        tipo="DOCUMENTO_MATERIALIZADO_RECIBIDO",
        estado_anterior=None,
        estado_posterior=entrada.estado.value,
    )
    return entrada, evento


# RF-IB-001/002/003: indexación léxica y vectorial en una sola transición —
# la spec no distingue dos pasos separados de indexación, ambas quedan
# recuperables a la vez ("Cuando se indexa, Entonces su contenido es
# recuperable..."). `embedding` es FICTICIO (RF-IB-003, ya calculado por el
# llamador vía GeneradorDeEmbeddings — nunca invocado desde aquí), pero
# GUARDARLO es una operación REAL sobre `IndiceVectorial` (P-03, corrige el
# mismo VETO que `IndiceLexico` — indexar() ahora llama a los dos puertos de
# verdad, no solo construye el valor de retorno).
def indexar(
    entrada: EntradaDeIndice,
    texto_extraido: str,
    metadatos: dict[str, str],
    embedding: list[float],
    indice_lexico: IndiceLexico,
    indice_vectorial: IndiceVectorial,
    actor: str,
    fecha: datetime,
) -> tuple[EntradaDeIndice, EventoAuditoria]:
    if entrada.estado != EstadoEntradaDeIndice.PENDIENTE_DE_INDEXACION:
        raise ErrorDeDominio(f"La entrada de índice '{entrada.id}' no está pendiente de indexación.")
    estado_anterior = entrada.estado.value
    actualizada = replace(
        entrada,
        estado=EstadoEntradaDeIndice.INDEXADA,
        texto_extraido=texto_extraido,
        metadatos=metadatos,
        embedding=embedding,
        fecha_indexacion=fecha,
    )
    indice_lexico.indexar(actualizada.id, texto_extraido)
    indice_vectorial.indexar(actualizada.id, embedding)
    evento = EventoAuditoria(
        actor=actor, fecha=fecha, tipo="ENTRADA_INDEXADA", estado_anterior=estado_anterior, estado_posterior=actualizada.estado.value
    )
    return actualizada, evento


# RF-IB-004: solo una entrada ya Indexada puede actualizarse (una entrada
# Pendiente todavía no tiene contenido materializado que rectificar — para
# eso está `indexar`). Cada campo es opcional: `None` significa "sin cambio",
# mismo criterio que un PATCH parcial — el llamador declara solo lo que
# cambió en el documento materializado subyacente.
def actualizar_entrada(
    entrada: EntradaDeIndice,
    actor: str,
    fecha: datetime,
    texto_extraido: str | None = None,
    metadatos: dict[str, str] | None = None,
    embedding: list[float] | None = None,
) -> tuple[EntradaDeIndice, EventoAuditoria]:
    if entrada.estado != EstadoEntradaDeIndice.INDEXADA:
        raise ErrorDeDominio(f"La entrada de índice '{entrada.id}' no está indexada todavía.")
    actualizada = replace(
        entrada,
        texto_extraido=texto_extraido if texto_extraido is not None else entrada.texto_extraido,
        metadatos=metadatos if metadatos is not None else entrada.metadatos,
        embedding=embedding if embedding is not None else entrada.embedding,
        fecha_indexacion=fecha,
    )
    evento = EventoAuditoria(
        actor=actor,
        fecha=fecha,
        tipo="ENTRADA_ACTUALIZADA",
        estado_anterior=entrada.estado.value,
        estado_posterior=actualizada.estado.value,
    )
    return actualizada, evento


# P-03/RF-IB-008 (structural, compartido por buscar/recuperar_por_relevancia/
# responder_qa para que RNF-IB-003 -consistencia de permisos entre las tres
# rutas- sea estructural y no una coincidencia de tres implementaciones
# repetidas). `verificador` (corrige el VETO real de Codex sobre 22b6b09, ver
# STATE.md: un `set` ya resuelto no ejercía el puerto) — se consulta AQUÍ,
# una vez por candidato, mismo patrón que `VerificadorDeAutorizacion` en
# extracción/T-41b. Nunca ordena ni reordena: preserva el orden de
# `candidatos` tal como llega (relevante para RF-IB-006, que ya trae el
# ranking resuelto).
class _AccedeADocumento(Protocol):
    documento_id: str


_T = TypeVar("_T", bound=_AccedeADocumento)


def aplicar_permisos_y_construir_evento(
    candidatos: list[_T], verificador: VerificadorDePermisos, actor: str, fecha: datetime, tipo: str
) -> tuple[list[_T], EventoDeAcceso]:
    permitidos = [candidato for candidato in candidatos if verificador.tiene_permiso(actor, candidato.documento_id)]
    evento = EventoDeAcceso(
        actor=actor,
        fecha=fecha,
        tipo=tipo,
        documentos_accedidos=tuple(candidato.documento_id for candidato in permitidos),
    )
    return permitidos, evento


def _cumple_filtros(entrada: EntradaDeIndice, filtros: dict[str, str]) -> bool:
    return all(entrada.metadatos.get(clave) == valor for clave, valor in filtros.items())


# RF-IB-005: búsqueda léxica y por metadatos — REAL y determinística (spec
# §1: "la construcción y el mantenimiento del índice en sí... es una
# operación determinística"). Corrige el VETO real de Codex sobre 22b6b09
# (ver STATE.md): ahora llama a `indice.buscar(termino)` de verdad — el
# puerto hace la búsqueda por término (real, Postgres en T-55), y este
# módulo aplica encima el filtro de metadatos y el de permiso, que sí son
# lógica de dominio pura. La recontención de subcadena que sigue abajo es
# defensa en profundidad (no depende de que la implementación del puerto
# filtre exactamente igual), no una segunda fuente de verdad.
def buscar(
    indice: IndiceLexico, termino: str, filtros: dict[str, str], verificador: VerificadorDePermisos, actor: str, fecha: datetime
) -> tuple[list[EntradaDeIndice], EventoDeAcceso]:
    coincidencias = [
        entrada
        for entrada in indice.buscar(termino)
        if entrada.estado == EstadoEntradaDeIndice.INDEXADA
        and entrada.texto_extraido is not None
        and termino.lower() in entrada.texto_extraido.lower()
        and _cumple_filtros(entrada, filtros)
    ]
    return aplicar_permisos_y_construir_evento(coincidencias, verificador, actor, fecha, tipo="BUSQUEDA_LEXICA")


# RF-IB-006: componente FICTICIO real — `candidatos_ordenados` llega YA
# ORDENADO por relevancia estimada (el llamador la calculó); esta función
# nunca recalcula ni reordena, solo filtra por permiso preservando el orden
# recibido (mismo criterio que `ordenar_por_confianza` en clasificacion, que
# tampoco filtra por permiso — aquí es al revés, se filtra sin reordenar).
def recuperar_por_relevancia(
    candidatos_ordenados: list[EntradaDeIndice], verificador: VerificadorDePermisos, actor: str, fecha: datetime
) -> tuple[list[EntradaDeIndice], EventoDeAcceso]:
    return aplicar_permisos_y_construir_evento(
        candidatos_ordenados, verificador, actor, fecha, tipo="RECUPERACION_POR_RELEVANCIA"
    )


# RF-IB-007/RF-IB-010: cita a un documento y fragmento concretos — nunca solo
# un documento_id sin fragmento, porque una cita debe ser "verificable"
# (RNF-IB-004) contra un pasaje real, no solo contra el documento entero.
@dataclass(frozen=True)
class Cita:
    documento_id: str
    fragmento: str


@dataclass(frozen=True)
class RespuestaQA:
    pregunta: str
    respuesta: str
    citas: list[Cita]
    modelo_id: str
    fecha: datetime


# RF-IB-010: el llamador declara la razón (mismo criterio que
# MarcaNoClasificable/MarcaNoEnriquecible) — el dominio no infiere cuánta
# evidencia "basta", ese umbral sigue [CLARIFICAR] en la spec §8.
@dataclass(frozen=True)
class NegativaApropiada:
    pregunta: str
    razon: str
    modelo_id: str
    fecha: datetime


# RF-IB-007/008/009/010, componente FICTICIO real (Q&A): única operación de
# evaluación que bifurca entre RespuestaQA y NegativaApropiada — mismo
# criterio que `evaluar_texto` en enriquecimiento (T-49, dos VETOs reales de
# Codex, ver STATE.md 2026-08-29): nunca una función que acepte una
# `respuesta` sin ninguna cita permitida y la deje pasar como válida, eso es
# exactamente la alucinación que invariante 3/RF-IB-007 prohíbe.
#
# `respuesta`/`citas` llegan YA CALCULADAS por el llamador (ModeloDeLenguaje,
# nunca invocado aquí). El filtrado de permisos sobre las citas ocurre ANTES
# de decidir la rama (RF-IB-008: ninguna cita sin permiso puede sustentar una
# respuesta, ni siquiera como referencia) — si el filtrado deja la lista de
# citas vacía, la única salida honesta es una negativa apropiada, aunque el
# modelo ficticio hubiera propuesto una respuesta con evidencia no permitida.
# El evento de acceso se construye siempre, en la MISMA llamada, con los
# documentos de las citas efectivamente permitidas (RF-IB-009) — nunca una
# función aparte que un test pudiera invocar aislada de la respuesta real.
def responder_qa(
    pregunta: str,
    respuesta: str | None,
    citas: list[Cita],
    verificador: VerificadorDePermisos,
    modelo_id: str,
    actor: str,
    fecha: datetime,
    razon_negativa: str | None = None,
) -> tuple[RespuestaQA | NegativaApropiada, EventoDeAcceso]:
    citas_permitidas, evento = aplicar_permisos_y_construir_evento(
        citas, verificador, actor, fecha, tipo="PREGUNTA_RESPONDIDA"
    )
    if respuesta is not None and citas_permitidas:
        return RespuestaQA(pregunta=pregunta, respuesta=respuesta, citas=citas_permitidas, modelo_id=modelo_id, fecha=fecha), evento
    if razon_negativa is None:
        raise ErrorDeDominio(
            "Sin ninguna cita permitida que sustente una respuesta, y sin razón de negativa apropiada "
            "declarada; declare una razón."
        )
    return NegativaApropiada(pregunta=pregunta, razon=razon_negativa, modelo_id=modelo_id, fecha=fecha), evento
