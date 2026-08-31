from datetime import datetime

import pytest
from dominio import (
    Cita,
    DocumentoParaIndexar,
    EntradaDeIndice,
    ErrorDeDominio,
    EstadoEntradaDeIndice,
    EventoDeAcceso,
    NegativaApropiada,
    RespuestaQA,
    actualizar_entrada,
    aplicar_permisos_y_construir_evento,
    buscar,
    crear_entrada_pendiente,
    indexar,
    recibir_documento_materializado,
    recuperar_por_relevancia,
    responder_qa,
)

FECHA = datetime.fromisoformat("2026-08-30T00:00:00+00:00")


# Dobles en memoria para los puertos P-03 declarados en dominio.py -- mismo
# criterio que _VerificadorDeAutorizacionFalso en extraccion/T-41b: pruebas
# de dominio contra un doble, la implementación real (Postgres/HTTP) la
# prueba integracion.py/test_persistencia.py (T-55).
class _IndiceLexicoFalso:
    def __init__(self, entradas: list[EntradaDeIndice] | None = None):
        self.entradas = list(entradas or [])
        self.llamadas_indexar: list[tuple[str, str]] = []

    def indexar(self, entrada_id: str, contenido: str) -> None:
        self.llamadas_indexar.append((entrada_id, contenido))

    def buscar(self, termino: str) -> list[EntradaDeIndice]:
        return self.entradas


class _IndiceVectorialFalso:
    def __init__(self) -> None:
        self.llamadas_indexar: list[tuple[str, list[float]]] = []
        self._guardado: dict[str, list[float]] = {}

    def indexar(self, entrada_id: str, embedding: list[float]) -> None:
        self.llamadas_indexar.append((entrada_id, embedding))
        self._guardado[entrada_id] = embedding

    def obtener(self, entrada_id: str) -> list[float] | None:
        return self._guardado.get(entrada_id)


class _VerificadorDePermisosFalso:
    def __init__(self, permitidos: set[str]):
        self._permitidos = permitidos
        self.llamadas: list[tuple[str, str]] = []

    def tiene_permiso(self, actor: str, documento_id: str) -> bool:
        self.llamadas.append((actor, documento_id))
        return documento_id in self._permitidos


def _permite(*documento_ids: str) -> _VerificadorDePermisosFalso:
    return _VerificadorDePermisosFalso(set(documento_ids))


def _entrada_indexada(
    entrada_id: str = "entrada-1",
    documento_id: str = "documento-1",
    texto_extraido: str = "el gato subió al tejado",
    metadatos: dict[str, str] | None = None,
    embedding: list[float] | None = None,
) -> EntradaDeIndice:
    documento = recibir_documento_materializado(
        documento_id=documento_id, texto_extraido=texto_extraido, metadatos=metadatos or {}
    )
    pendiente, _ = crear_entrada_pendiente(entrada_id, documento, actor="sistema", fecha=FECHA)
    indexada, _ = indexar(
        pendiente,
        texto_extraido=documento.texto_extraido,
        metadatos=documento.metadatos,
        embedding=embedding or [0.1, 0.2],
        indice_lexico=_IndiceLexicoFalso(),
        indice_vectorial=_IndiceVectorialFalso(),
        actor="sistema",
        fecha=FECHA,
    )
    return indexada


# RF-IB-001 · Indexación de documentos materializados
class TestIndexacionDeDocumentosMaterializados:
    def test_dado_un_documento_recien_materializado_cuando_se_recibe_e_indexa_entonces_queda_indexada(self):
        documento = recibir_documento_materializado(
            documento_id="documento-1", texto_extraido="contenido del documento", metadatos={"serie": "100"}
        )
        pendiente, evento_recepcion = crear_entrada_pendiente("entrada-1", documento, actor="sistema", fecha=FECHA)

        assert pendiente.estado == EstadoEntradaDeIndice.PENDIENTE_DE_INDEXACION
        assert pendiente.documento_id == "documento-1"
        assert evento_recepcion.estado_anterior is None
        assert evento_recepcion.estado_posterior == "PENDIENTE_DE_INDEXACION"

        indice_lexico = _IndiceLexicoFalso()
        indice_vectorial = _IndiceVectorialFalso()
        indexada, evento_indexacion = indexar(
            pendiente,
            texto_extraido=documento.texto_extraido,
            metadatos=documento.metadatos,
            embedding=[0.1, 0.2],
            indice_lexico=indice_lexico,
            indice_vectorial=indice_vectorial,
            actor="sistema",
            fecha=FECHA,
        )

        assert indexada.estado == EstadoEntradaDeIndice.INDEXADA
        assert indexada.documento_id == "documento-1"
        assert evento_indexacion.estado_anterior == "PENDIENTE_DE_INDEXACION"
        assert evento_indexacion.estado_posterior == "INDEXADA"
        # P-03 (VETO real de Codex sobre 22b6b09, ver STATE.md): indexar()
        # debe ejercer de verdad los dos puertos reales, no solo construir el
        # valor de retorno.
        assert indice_lexico.llamadas_indexar == [("entrada-1", "contenido del documento")]
        assert indice_vectorial.llamadas_indexar == [("entrada-1", [0.1, 0.2])]

    def test_indexar_rechaza_una_entrada_que_no_esta_pendiente(self):
        indexada = _entrada_indexada()

        with pytest.raises(ErrorDeDominio):
            indexar(
                indexada,
                texto_extraido="otro",
                metadatos={},
                embedding=[0.0],
                indice_lexico=_IndiceLexicoFalso(),
                indice_vectorial=_IndiceVectorialFalso(),
                actor="sistema",
                fecha=FECHA,
            )


# RF-IB-002 · Indexación léxica
class TestIndexacionLexica:
    def test_dado_un_documento_indexado_su_contenido_es_recuperable_por_palabra_clave(self):
        entrada = _entrada_indexada(texto_extraido="el gato subió al tejado")
        indice = _IndiceLexicoFalso([entrada])

        resultados, _ = buscar(indice=indice, termino="tejado", filtros={}, verificador=_permite("documento-1"), actor="ana", fecha=FECHA)

        assert resultados == [entrada]

    def test_un_termino_que_no_aparece_no_devuelve_la_entrada(self):
        entrada = _entrada_indexada(texto_extraido="el gato subió al tejado")
        indice = _IndiceLexicoFalso([entrada])

        resultados, _ = buscar(indice=indice, termino="perro", filtros={}, verificador=_permite("documento-1"), actor="ana", fecha=FECHA)

        assert resultados == []


# RF-IB-003 · Indexación vectorial
class TestIndexacionVectorial:
    def test_dado_un_documento_indexado_su_embedding_ficticio_queda_almacenado(self):
        indexada = _entrada_indexada(embedding=[0.5, 0.25, 0.1])

        assert indexada.embedding == [0.5, 0.25, 0.1]


# RF-IB-004 · Actualización del índice ante cambios materializados
class TestActualizacionDelIndice:
    def test_dado_un_documento_ya_indexado_cuyo_estado_cambia_su_entrada_se_actualiza(self):
        indexada = _entrada_indexada(texto_extraido="texto original", metadatos={"serie": "100"})

        actualizada, evento = actualizar_entrada(
            indexada, actor="ana", fecha=FECHA, texto_extraido="texto rectificado", metadatos={"serie": "200"}
        )

        assert actualizada.texto_extraido == "texto rectificado"
        assert actualizada.metadatos == {"serie": "200"}
        assert actualizada.estado == EstadoEntradaDeIndice.INDEXADA
        assert evento.tipo == "ENTRADA_ACTUALIZADA"

    def test_actualizar_una_entrada_pendiente_se_rechaza(self):
        documento = recibir_documento_materializado(documento_id="documento-1", texto_extraido="x", metadatos={})
        pendiente, _ = crear_entrada_pendiente("entrada-1", documento, actor="sistema", fecha=FECHA)

        with pytest.raises(ErrorDeDominio):
            actualizar_entrada(pendiente, actor="ana", fecha=FECHA, texto_extraido="y")

    def test_campos_no_declarados_conservan_su_valor_anterior(self):
        indexada = _entrada_indexada(texto_extraido="texto original", metadatos={"serie": "100"})

        actualizada, _ = actualizar_entrada(indexada, actor="ana", fecha=FECHA, metadatos={"serie": "200"})

        assert actualizada.texto_extraido == "texto original"
        assert actualizada.metadatos == {"serie": "200"}


# RF-IB-005 · Búsqueda léxica y por metadatos
class TestBusquedaLexicaYPorMetadatos:
    def test_una_consulta_con_filtros_devuelve_solo_lo_que_cumple_termino_y_filtros(self):
        entrada_a = _entrada_indexada(
            entrada_id="entrada-a", documento_id="documento-a", texto_extraido="resolución de archivo", metadatos={"serie": "100"}
        )
        entrada_b = _entrada_indexada(
            entrada_id="entrada-b", documento_id="documento-b", texto_extraido="resolución de archivo", metadatos={"serie": "200"}
        )

        resultados, _ = buscar(
            indice=_IndiceLexicoFalso([entrada_a, entrada_b]),
            termino="resolución",
            filtros={"serie": "100"},
            verificador=_permite("documento-a", "documento-b"),
            actor="ana",
            fecha=FECHA,
        )

        assert resultados == [entrada_a]

    def test_una_entrada_pendiente_nunca_aparece_en_resultados(self):
        documento = recibir_documento_materializado(documento_id="documento-1", texto_extraido="contenido buscable", metadatos={})
        pendiente, _ = crear_entrada_pendiente("entrada-1", documento, actor="sistema", fecha=FECHA)

        resultados, _ = buscar(
            indice=_IndiceLexicoFalso([pendiente]),
            termino="buscable",
            filtros={},
            verificador=_permite("documento-1"),
            actor="ana",
            fecha=FECHA,
        )

        assert resultados == []


# RF-IB-006 · Recuperación por relevancia semántica (componente FICTICIO)
class TestRecuperacionPorRelevanciaSemantica:
    def test_dada_una_consulta_los_resultados_se_devuelven_en_el_orden_de_relevancia_ya_calculado(self):
        mas_relevante = _entrada_indexada(entrada_id="entrada-1", documento_id="documento-1")
        menos_relevante = _entrada_indexada(entrada_id="entrada-2", documento_id="documento-2")

        resultados, _ = recuperar_por_relevancia(
            candidatos_ordenados=[mas_relevante, menos_relevante],
            verificador=_permite("documento-1", "documento-2"),
            actor="ana",
            fecha=FECHA,
        )

        assert resultados == [mas_relevante, menos_relevante]

    def test_nunca_reordena_el_orden_de_relevancia_ya_calculado(self):
        primero = _entrada_indexada(entrada_id="entrada-1", documento_id="documento-1")
        segundo = _entrada_indexada(entrada_id="entrada-2", documento_id="documento-2")
        tercero = _entrada_indexada(entrada_id="entrada-3", documento_id="documento-3")

        resultados, _ = recuperar_por_relevancia(
            candidatos_ordenados=[tercero, primero, segundo],
            verificador=_permite("documento-1", "documento-2", "documento-3"),
            actor="ana",
            fecha=FECHA,
        )

        assert resultados == [tercero, primero, segundo]


# RF-IB-007 · Respuesta conversacional (Q&A) con citas
class TestRespuestaConversacionalConCitas:
    def test_dada_evidencia_suficiente_la_respuesta_incluye_al_menos_una_cita_verificable(self):
        cita = Cita(documento_id="documento-1", fragmento="el gato subió al tejado")

        resultado, _ = responder_qa(
            pregunta="¿dónde subió el gato?",
            respuesta="El gato subió al tejado.",
            citas=[cita],
            verificador=_permite("documento-1"),
            modelo_id="qa-ficticio-v1",
            actor="ana",
            fecha=FECHA,
        )

        assert isinstance(resultado, RespuestaQA)
        assert resultado.citas == [cita]
        assert len(resultado.citas) >= 1


# RF-IB-008 · Cero exposición sin permiso (RNF-IB-003: misma garantía en las tres rutas)
class TestCeroExposicionSinPermiso:
    def test_buscar_no_devuelve_un_documento_sin_permiso(self):
        permitido = _entrada_indexada(entrada_id="entrada-1", documento_id="documento-1", texto_extraido="acta de reunión")
        sin_permiso = _entrada_indexada(entrada_id="entrada-2", documento_id="documento-2", texto_extraido="acta de reunión")

        resultados, _ = buscar(
            indice=_IndiceLexicoFalso([permitido, sin_permiso]),
            termino="acta",
            filtros={},
            verificador=_permite("documento-1"),
            actor="ana",
            fecha=FECHA,
        )

        assert resultados == [permitido]
        assert sin_permiso not in resultados

    def test_recuperar_por_relevancia_no_devuelve_un_documento_sin_permiso(self):
        permitido = _entrada_indexada(entrada_id="entrada-1", documento_id="documento-1")
        sin_permiso = _entrada_indexada(entrada_id="entrada-2", documento_id="documento-2")

        resultados, _ = recuperar_por_relevancia(
            candidatos_ordenados=[sin_permiso, permitido], verificador=_permite("documento-1"), actor="ana", fecha=FECHA
        )

        assert resultados == [permitido]

    def test_responder_qa_no_cita_un_documento_sin_permiso_ni_como_referencia(self):
        cita_sin_permiso = Cita(documento_id="documento-2", fragmento="información reservada")

        resultado, _ = responder_qa(
            pregunta="¿qué dice el documento reservado?",
            respuesta="El documento dice X.",
            citas=[cita_sin_permiso],
            verificador=_permite("documento-1"),
            modelo_id="qa-ficticio-v1",
            actor="ana",
            fecha=FECHA,
            razon_negativa="Sin evidencia con permiso suficiente para responder.",
        )

        assert isinstance(resultado, NegativaApropiada)


# RF-IB-009 · Auditoría de acceso por consulta
class TestAuditoriaDeAccesoPorConsulta:
    def test_buscar_devuelve_su_propio_evento_de_acceso_con_actor_fecha_y_documentos_accedidos(self):
        entrada = _entrada_indexada(documento_id="documento-1", texto_extraido="contenido consultable")

        _, evento = buscar(
            indice=_IndiceLexicoFalso([entrada]),
            termino="consultable",
            filtros={},
            verificador=_permite("documento-1"),
            actor="ana",
            fecha=FECHA,
        )

        assert isinstance(evento, EventoDeAcceso)
        assert evento.actor == "ana"
        assert evento.fecha == FECHA
        assert evento.documentos_accedidos == ("documento-1",)

    def test_recuperar_por_relevancia_devuelve_su_propio_evento_de_acceso(self):
        entrada = _entrada_indexada(documento_id="documento-1")

        _, evento = recuperar_por_relevancia(
            candidatos_ordenados=[entrada], verificador=_permite("documento-1"), actor="ana", fecha=FECHA
        )

        assert evento.documentos_accedidos == ("documento-1",)

    def test_responder_qa_devuelve_su_propio_evento_de_acceso_con_los_documentos_citados(self):
        cita = Cita(documento_id="documento-1", fragmento="fragmento citado")

        _, evento = responder_qa(
            pregunta="¿qué dice?",
            respuesta="Dice X.",
            citas=[cita],
            verificador=_permite("documento-1"),
            modelo_id="qa-ficticio-v1",
            actor="ana",
            fecha=FECHA,
        )

        assert evento.documentos_accedidos == ("documento-1",)

    def test_aplicar_permisos_y_construir_evento_es_la_unica_fuente_del_evento_en_las_tres_rutas(self):
        entrada = _entrada_indexada(documento_id="documento-1")

        permitidos, evento = aplicar_permisos_y_construir_evento(
            [entrada], verificador=_permite("documento-1"), actor="ana", fecha=FECHA, tipo="BUSQUEDA_LEXICA"
        )

        assert permitidos == [entrada]
        assert evento == EventoDeAcceso(actor="ana", fecha=FECHA, tipo="BUSQUEDA_LEXICA", documentos_accedidos=("documento-1",))


# RF-IB-010 · Negativa apropiada
class TestNegativaApropiada:
    def test_sin_evidencia_suficiente_declara_que_no_puede_responder_en_vez_de_inventar(self):
        resultado, _ = responder_qa(
            pregunta="¿algo sin evidencia?",
            respuesta=None,
            citas=[],
            verificador=_permite(),
            modelo_id="qa-ficticio-v1",
            actor="ana",
            fecha=FECHA,
            razon_negativa="No hay evidencia suficiente en el acervo para sustentar una respuesta.",
        )

        assert isinstance(resultado, NegativaApropiada)
        assert resultado.razon == "No hay evidencia suficiente en el acervo para sustentar una respuesta."

    def test_sin_evidencia_y_sin_razon_declarada_se_rechaza_en_vez_de_responder_sin_cita(self):
        with pytest.raises(ErrorDeDominio):
            responder_qa(
                pregunta="¿algo sin evidencia?",
                respuesta=None,
                citas=[],
                verificador=_permite(),
                modelo_id="qa-ficticio-v1",
                actor="ana",
                fecha=FECHA,
            )

    def test_una_respuesta_propuesta_sin_ninguna_cita_permitida_nunca_pasa_como_valida(self):
        # Aunque el modelo ficticio proponga una `respuesta`, si el filtrado de
        # permisos deja `citas` vacía la única salida honesta es una negativa
        # apropiada — nunca una RespuestaQA sin cita real que la sustente
        # (invariante 3/RF-IB-007), exactamente la alucinación que el RF prohíbe.
        cita_sin_permiso = Cita(documento_id="documento-2", fragmento="dato reservado")

        with pytest.raises(ErrorDeDominio):
            responder_qa(
                pregunta="¿qué dice el reservado?",
                respuesta="Una respuesta inventada sobre el reservado.",
                citas=[cita_sin_permiso],
                verificador=_permite("documento-1"),
                modelo_id="qa-ficticio-v1",
                actor="ana",
                fecha=FECHA,
            )
