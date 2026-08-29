from dataclasses import dataclass
from datetime import datetime
from enum import Enum


class ErrorDeDominio(Exception):
    pass


# RF-EN-001: Enriquecimiento no posee la máquina de estados del texto extraído
# (esa es de Extracción, RF-EX-010) — solo verifica la precondición declarada
# por el llamador ("Extraído") antes de aceptar el texto, mismo criterio
# defensivo que `recibir_texto_extraido` en clasificacion (T-44).
class EstadoTextoExtraido(str, Enum):
    EXTRAIDO = "EXTRAIDO"


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


# RF-EN-002/003/004 · componente FICTICIO (constitución, disciplina de
# alcance): campo/valor_original/valor_normalizado/confianza/evidencia ya
# vienen calculados por el llamador, igual que SugerenciaDeClasificacion en
# clasificacion (T-44) — este contexto nunca extrae metadatos de verdad.
# Invariante 3 (forma original + forma normalizada) e invariante 2
# (evidencia+confianza obligatorios) son garantías estructurales del
# constructor, no validaciones en tiempo de ejecución, mismo criterio que
# `Sugerencia` en records-custodia (T-08).
@dataclass(frozen=True)
class ValorPropuesto:
    campo: str
    valor_original: str
    valor_normalizado: str
    confianza: float
    evidencia: list[str]


def proponer_valor(
    campo: str, valor_original: str, valor_normalizado: str, confianza: float, evidencia: list[str]
) -> ValorPropuesto:
    return ValorPropuesto(
        campo=campo,
        valor_original=valor_original,
        valor_normalizado=valor_normalizado,
        confianza=confianza,
        evidencia=evidencia,
    )


# RF-EN-005: marca explícita de "campo no encontrado" — un tipo distinto de
# ValorPropuesto, no un ValorPropuesto con campos vacíos, para que la ausencia
# de evidencia sea estructural y no se pueda confundir con un valor de baja
# confianza. Sin confianza ni evidencia: no hay evidencia suficiente que
# portar, eso ES el contenido de la marca (invariante 4).
@dataclass(frozen=True)
class CampoNoEncontrado:
    campo: str


def marcar_campo_no_encontrado(campo: str) -> CampoNoEncontrado:
    return CampoNoEncontrado(campo=campo)


# Sugerencia de metadatos (agregado raíz, spec §3): documento de origen, lista
# de valores propuestos (encontrados o marcados "no encontrado" — invariante
# 4), modelo, fecha. Igual que Clasificación, sin persistencia ni estado
# propio después de entregarse (spec §3).
@dataclass(frozen=True)
class SugerenciaDeMetadatos:
    documento_id: str
    valores: list[ValorPropuesto | CampoNoEncontrado]
    modelo_id: str
    fecha: datetime


def generar_sugerencia_de_metadatos(
    texto: TextoDisponible,
    valores: list[ValorPropuesto | CampoNoEncontrado],
    modelo_id: str,
    fecha: datetime,
) -> SugerenciaDeMetadatos:
    # RF-EN-009 (VETO real de Codex sobre el primer commit de T-49): sin este
    # llamado, valores=[] pasaba de largo y a_sugerencia_saliente() lo convertía
    # en [] -- ninguna sugerencia, ninguna marca de "no enriquecible". La
    # operación que produce la salida real es la que debe rechazar la pérdida
    # silenciosa, no un guardia aislado que nadie invoca.
    exigir_al_menos_un_valor(valores)
    return SugerenciaDeMetadatos(documento_id=texto.documento_id, valores=valores, modelo_id=modelo_id, fecha=fecha)


# RF-EN-009 (VETO real de Codex sobre commit 17642a7 de clasificacion,
# aplicado aquí desde el inicio, no como fix posterior — ver TODO.md): una
# lista de valores vacía no debe producir una sugerencia de metadatos sin
# ningún valor ni marca — el Dado/Cuando/Entonces exige que el texto termine
# en una sugerencia (con campos, aunque estén "no encontrado") o en una marca
# explícita de "no enriquecible" (`marcar_no_enriquecible`), nunca en un
# cuerpo vacío silencioso.
def exigir_al_menos_un_valor(
    valores: list[ValorPropuesto | CampoNoEncontrado],
) -> list[ValorPropuesto | CampoNoEncontrado]:
    if not valores:
        raise ErrorDeDominio(
            "No se recibió ningún valor propuesto ni campo marcado; use marcar_no_enriquecible "
            "para marcar el texto explícitamente."
        )
    return valores


# RF-EN-006 / RF-EN-008 / RF-EN-010: forma genérica que ya acepta
# `POST /sugerencias` de records-custodia (SugerenciaEntrante — documentoId,
# tipo, contenidoPropuesto, modeloId, evidencia, confianza, fecha; T-08). Un
# ValorPropuesto/CampoNoEncontrado por elemento de salida (no un único bloque
# por documento) para que cada campo sea revisable y aprobable de forma
# independiente (RF-EN-008) y consultable por separado (RF-EN-010). Esta
# traducción es pura: no hace ninguna llamada HTTP (eso es integracion.py,
# T-50) y no toca ningún estado de documento — la propia estructura del
# módulo demuestra "sin alterar los metadatos del documento" (RF-EN-006)
# porque este contexto no tiene ningún agregado de documento que pudiera
# alterar.
@dataclass(frozen=True)
class SugerenciaSaliente:
    documento_id: str
    tipo: str
    contenido_propuesto: str
    modelo_id: str
    evidencia: list[str]
    confianza: float
    fecha: datetime


def a_sugerencia_saliente(sugerencia: SugerenciaDeMetadatos) -> list[SugerenciaSaliente]:
    salientes = []
    for valor in sugerencia.valores:
        if isinstance(valor, ValorPropuesto):
            salientes.append(
                SugerenciaSaliente(
                    documento_id=sugerencia.documento_id,
                    tipo="metadato",
                    contenido_propuesto=f"{valor.campo}={valor.valor_normalizado}",
                    modelo_id=sugerencia.modelo_id,
                    evidencia=valor.evidencia,
                    confianza=valor.confianza,
                    fecha=sugerencia.fecha,
                )
            )
        else:
            salientes.append(
                SugerenciaSaliente(
                    documento_id=sugerencia.documento_id,
                    tipo="metadato",
                    contenido_propuesto=f"{valor.campo}=NO_ENCONTRADO",
                    modelo_id=sugerencia.modelo_id,
                    evidencia=[],
                    confianza=0.0,
                    fecha=sugerencia.fecha,
                )
            )
    return salientes


# RF-EN-009: el llamador declara la condición de "no enriquecible" (mismo
# criterio que MarcaNoClasificable en clasificacion, T-44) — el dominio no la
# detecta, solo registra la razón para que no haya pérdida silenciosa.
@dataclass(frozen=True)
class MarcaNoEnriquecible:
    documento_id: str
    razon: str
    actor: str
    fecha: datetime


def marcar_no_enriquecible(texto: TextoDisponible, razon: str, actor: str, fecha: datetime) -> MarcaNoEnriquecible:
    return MarcaNoEnriquecible(documento_id=texto.documento_id, razon=razon, actor=actor, fecha=fecha)


# RF-EN-009 (segundo VETO real de Codex sobre T-49, ver STATE.md): rechazar
# valores=[] con un error no basta -- el Dado/Cuando/Entonces exige que
# evaluar un texto sin señal TERMINE en una MarcaNoEnriquecible con razón
# registrada, no que el llamador deba saber atrapar un error y decidir por su
# cuenta invocar marcar_no_enriquecible por separado. evaluar_texto() es la
# única operación de evaluación: bifurca hacia SugerenciaDeMetadatos (si hay
# al menos un valor/campo) o hacia MarcaNoEnriquecible (si no hay ninguno,
# con la razón que declara el llamador -- el dominio no la infiere, mismo
# criterio que el resto de "condiciones declaradas" del proyecto). Solo
# rechaza con ErrorDeDominio la llamada genuinamente malformada: sin valores
# Y sin razón declarada.
def evaluar_texto(
    texto: TextoDisponible,
    valores: list[ValorPropuesto | CampoNoEncontrado],
    modelo_id: str,
    actor: str,
    fecha: datetime,
    razon_no_enriquecible: str | None = None,
) -> SugerenciaDeMetadatos | MarcaNoEnriquecible:
    if valores:
        return generar_sugerencia_de_metadatos(texto, valores, modelo_id, fecha)
    if razon_no_enriquecible is None:
        raise ErrorDeDominio(
            "No se recibió ningún valor propuesto ni campo marcado, y no se declaró una razón "
            "de 'no enriquecible'; declare al menos uno de los dos."
        )
    return marcar_no_enriquecible(texto, razon_no_enriquecible, actor, fecha)
