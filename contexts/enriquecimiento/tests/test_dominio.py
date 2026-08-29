from datetime import datetime

import dominio
import pytest
from dominio import (
    CampoNoEncontrado,
    ErrorDeDominio,
    MarcaNoEnriquecible,
    SugerenciaDeMetadatos,
    SugerenciaSaliente,
    TextoDisponible,
    ValorPropuesto,
    a_sugerencia_saliente,
    evaluar_texto,
    exigir_al_menos_un_valor,
    generar_sugerencia_de_metadatos,
    marcar_campo_no_encontrado,
    marcar_no_enriquecible,
    proponer_valor,
    recibir_texto_extraido,
)

FECHA = datetime.fromisoformat("2026-08-29T00:00:00+00:00")


def _texto_disponible(texto_extraido_id: str = "texto-1", documento_id: str = "documento-1") -> TextoDisponible:
    return recibir_texto_extraido(
        texto_extraido_id=texto_extraido_id, documento_id=documento_id, contenido="contenido del documento", estado="EXTRAIDO"
    )


# RF-EN-001 · Recepción de texto extraído
class TestRecepcionDeTextoExtraido:
    def test_dado_un_texto_extraido_en_extraido_cuando_se_recibe_entonces_queda_disponible(self):
        disponible = recibir_texto_extraido(
            texto_extraido_id="texto-1", documento_id="documento-1", contenido="contenido", estado="EXTRAIDO"
        )

        assert disponible == TextoDisponible(
            texto_extraido_id="texto-1", documento_id="documento-1", contenido="contenido"
        )

    def test_un_texto_que_no_esta_extraido_se_rechaza(self):
        with pytest.raises(ErrorDeDominio):
            recibir_texto_extraido(
                texto_extraido_id="texto-1", documento_id="documento-1", contenido="contenido", estado="PENDIENTE_DE_EXTRACCION"
            )


# RF-EN-002 · Extracción probabilística de valores por campo
class TestExtraccionProbabilisticaPorCampo:
    def test_dado_un_texto_y_un_campo_cuando_se_evalua_entonces_genera_valor_propuesto_con_confianza(self):
        valor = proponer_valor(
            campo="remitente",
            valor_original="Juan Pérez",
            valor_normalizado="JUAN PEREZ",
            confianza=0.82,
            evidencia=["Firma: Juan Pérez"],
        )

        assert valor.campo == "remitente"
        assert valor.confianza == 0.82

    def test_dado_un_campo_sin_evidencia_suficiente_cuando_se_evalua_entonces_queda_marcado_no_encontrado(self):
        marca = marcar_campo_no_encontrado(campo="asunto")

        assert marca == CampoNoEncontrado(campo="asunto")


# RF-EN-003 · Normalización del valor extraído
class TestNormalizacionDelValorExtraido:
    def test_dado_un_valor_propuesto_cuando_se_consulta_expone_su_forma_original_y_normalizada(self):
        valor = proponer_valor(
            campo="fecha",
            valor_original="29/08/2026",
            valor_normalizado="2026-08-29",
            confianza=0.9,
            evidencia=["Bogotá, 29 de agosto de 2026"],
        )

        assert valor.valor_original == "29/08/2026"
        assert valor.valor_normalizado == "2026-08-29"


# RF-EN-004 · Confianza y evidencia por valor propuesto
class TestConfianzaYEvidenciaPorValor:
    def test_dado_un_valor_propuesto_cuando_se_consulta_incluye_evidencia_y_confianza(self):
        valor = proponer_valor(
            campo="destinatario",
            valor_original="María Gómez",
            valor_normalizado="MARIA GOMEZ",
            confianza=0.75,
            evidencia=["Para: María Gómez"],
        )

        assert valor.evidencia == ["Para: María Gómez"]
        assert valor.confianza == 0.75


# RF-EN-005 · Marca explícita de campo no encontrado
class TestMarcaExplicitaDeCampoNoEncontrado:
    def test_un_campo_no_encontrado_se_incluye_en_la_sugerencia_en_vez_de_omitirse(self):
        texto = _texto_disponible()
        encontrado = proponer_valor("remitente", "Juan Pérez", "JUAN PEREZ", 0.8, ["e1"])
        no_encontrado = marcar_campo_no_encontrado("tipo_documental")

        sugerencia = generar_sugerencia_de_metadatos(
            texto, valores=[encontrado, no_encontrado], modelo_id="enriquecedor-ficticio-v1", fecha=FECHA
        )

        assert no_encontrado in sugerencia.valores
        assert len(sugerencia.valores) == 2


# RF-EN-006 · Envío de sugerencias de metadatos a Records/Custodia
class TestEnvioDeSugerenciasAMetadatos:
    def test_una_sugerencia_con_valor_encontrado_se_traduce_con_tipo_metadato(self):
        texto = _texto_disponible()
        valor = proponer_valor("remitente", "Juan Pérez", "JUAN PEREZ", 0.8, ["e1"])
        sugerencia = generar_sugerencia_de_metadatos(texto, [valor], "enriquecedor-ficticio-v1", FECHA)

        salientes = a_sugerencia_saliente(sugerencia)

        assert salientes == [
            SugerenciaSaliente(
                documento_id="documento-1",
                tipo="metadato",
                contenido_propuesto="remitente=JUAN PEREZ",
                modelo_id="enriquecedor-ficticio-v1",
                evidencia=["e1"],
                confianza=0.8,
                fecha=FECHA,
            )
        ]

    def test_una_sugerencia_con_campo_no_encontrado_se_traduce_sin_alterar_el_documento(self):
        texto = _texto_disponible()
        no_encontrado = marcar_campo_no_encontrado("asunto")
        sugerencia = generar_sugerencia_de_metadatos(texto, [no_encontrado], "enriquecedor-ficticio-v1", FECHA)

        salientes = a_sugerencia_saliente(sugerencia)

        assert salientes == [
            SugerenciaSaliente(
                documento_id="documento-1",
                tipo="metadato",
                contenido_propuesto="asunto=NO_ENCONTRADO",
                modelo_id="enriquecedor-ficticio-v1",
                evidencia=[],
                confianza=0.0,
                fecha=FECHA,
            )
        ]


# RF-EN-007 · Nunca materializa directamente
class TestNuncaMaterializaDirectamente:
    def test_el_modulo_no_expone_ninguna_operacion_de_materializacion(self):
        prohibidos = {
            "materializar",
            "aprobar",
            "decidir",
            "cambiar_metadatos",
            "cambiar_documento",
            "confirmar",
        }
        expuestos = {nombre for nombre in vars(dominio) if not nombre.startswith("_")}

        assert prohibidos.isdisjoint(expuestos)


# RF-EN-008 · Granularidad por campo
class TestGranularidadPorCampo:
    def test_dada_una_sugerencia_con_varios_valores_cada_uno_se_distingue_por_su_campo(self):
        texto = _texto_disponible()
        remitente = proponer_valor("remitente", "Juan Pérez", "JUAN PEREZ", 0.8, ["e1"])
        fecha_valor = proponer_valor("fecha", "29/08/2026", "2026-08-29", 0.95, ["e2"])
        sugerencia = generar_sugerencia_de_metadatos(
            texto, [remitente, fecha_valor], "enriquecedor-ficticio-v1", FECHA
        )

        campos = {valor.campo for valor in sugerencia.valores}

        assert campos == {"remitente", "fecha"}
        assert len(sugerencia.valores) == len(campos)


# RF-EN-009 · Cero pérdida silenciosa
class TestCeroPerdidaSilenciosa:
    def test_dado_un_texto_sin_senal_suficiente_cuando_se_evalua_queda_marcado_no_enriquecible(self):
        texto = _texto_disponible()

        marca = marcar_no_enriquecible(
            texto, razon="Texto extraído vacío.", actor="enriquecedor-ficticio-v1", fecha=FECHA
        )

        assert marca == MarcaNoEnriquecible(
            documento_id="documento-1", razon="Texto extraído vacío.", actor="enriquecedor-ficticio-v1", fecha=FECHA
        )

    def test_exigir_al_menos_un_valor_rechaza_lista_vacia(self):
        with pytest.raises(ErrorDeDominio):
            exigir_al_menos_un_valor([])

    def test_exigir_al_menos_un_valor_devuelve_la_lista_intacta_si_no_esta_vacia(self):
        no_encontrado = marcar_campo_no_encontrado("asunto")

        assert exigir_al_menos_un_valor([no_encontrado]) == [no_encontrado]

    def test_generar_sugerencia_de_metadatos_rechaza_lista_vacia_en_vez_de_perderla_en_silencio(self):
        # VETO real de Codex sobre el commit T-49 (ver REVIEW.md): la
        # protección de exigir_al_menos_un_valor existía pero no estaba
        # conectada a la operación real que produce la salida -- un texto
        # con valores=[] pasaba de largo y a_sugerencia_saliente() lo
        # convertía en [] (ninguna sugerencia, ninguna marca). Este test
        # ejerce la operación real, no el guardia aislado.
        texto = _texto_disponible()

        with pytest.raises(ErrorDeDominio):
            generar_sugerencia_de_metadatos(texto, [], "enriquecedor-ficticio-v1", FECHA)

    def test_evaluar_texto_sin_senal_produce_marca_no_enriquecible_con_razon(self):
        # Segundo VETO real de Codex sobre T-49 (ver REVIEW.md/STATE.md):
        # rechazar la lista vacía con un error no basta -- el
        # Dado/Cuando/Entonces de RF-EN-009 exige que evaluar el texto
        # TERMINE en una MarcaNoEnriquecible con razón, para el mismo
        # TextoDisponible, sin invocar el constructor de la marca aparte.
        texto = _texto_disponible()

        resultado = evaluar_texto(
            texto,
            [],
            "enriquecedor-ficticio-v1",
            actor="enriquecedor-ficticio-v1",
            fecha=FECHA,
            razon_no_enriquecible="Texto extraído vacío.",
        )

        assert resultado == MarcaNoEnriquecible(
            documento_id="documento-1", razon="Texto extraído vacío.", actor="enriquecedor-ficticio-v1", fecha=FECHA
        )

    def test_evaluar_texto_con_valores_produce_sugerencia_de_metadatos(self):
        texto = _texto_disponible()
        valor = proponer_valor("asunto", "Factura No. 123", "factura-123", 0.9, ["página 1"])

        resultado = evaluar_texto(texto, [valor], "enriquecedor-ficticio-v1", actor="actor-1", fecha=FECHA)

        assert isinstance(resultado, SugerenciaDeMetadatos)
        assert resultado.valores == [valor]

    def test_evaluar_texto_sin_valores_ni_razon_se_rechaza(self):
        texto = _texto_disponible()

        with pytest.raises(ErrorDeDominio):
            evaluar_texto(texto, [], "enriquecedor-ficticio-v1", actor="actor-1", fecha=FECHA)


# RF-EN-010 · Consulta de sugerencias de metadatos por documento
class TestConsultaDeSugerenciasPorDocumento:
    def test_dado_un_documento_con_sugerencia_de_metadatos_se_listan_campo_valor_normalizado_confianza_y_evidencia(self):
        texto = _texto_disponible()
        valor = proponer_valor("remitente", "Juan Pérez", "JUAN PEREZ", 0.8, ["e1"])
        sugerencia = generar_sugerencia_de_metadatos(texto, [valor], "enriquecedor-ficticio-v1", FECHA)

        (saliente,) = a_sugerencia_saliente(sugerencia)

        assert "remitente" in saliente.contenido_propuesto
        assert "JUAN PEREZ" in saliente.contenido_propuesto
        assert saliente.confianza == 0.8
        assert saliente.evidencia == ["e1"]
