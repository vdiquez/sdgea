from datetime import datetime

import dominio
import pytest
from dominio import (
    ErrorDeDominio,
    MarcaNoClasificable,
    SugerenciaDeAgrupamiento,
    SugerenciaDeClasificacion,
    SugerenciaSaliente,
    TextoDisponible,
    a_sugerencia_saliente_de_agrupamiento,
    a_sugerencia_saliente_de_clasificacion,
    agrupar,
    clasificar,
    marcar_no_clasificable,
    ordenar_por_confianza,
    recibir_texto_extraido,
)

FECHA = datetime.fromisoformat("2026-08-28T00:00:00+00:00")


def _texto_disponible(texto_extraido_id: str = "texto-1", documento_id: str = "documento-1") -> TextoDisponible:
    return recibir_texto_extraido(
        texto_extraido_id=texto_extraido_id, documento_id=documento_id, contenido="contenido del documento", estado="EXTRAIDO"
    )


# RF-CL-001 · Recepción de texto extraído
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


# RF-CL-002 · Clasificación contra la TRD vigente
class TestClasificacionContraTrdVigente:
    def test_dado_un_texto_extraido_y_una_trd_vigente_cuando_se_clasifica_entonces_genera_sugerencia_con_version(self):
        texto = _texto_disponible()

        sugerencia = clasificar(
            texto,
            trd_version=3,
            serie="Serie Gestión Documental",
            subserie="Subserie Correspondencia",
            confianza=0.87,
            evidencia=["fragmento relevante del texto"],
            modelo_id="clasificador-ficticio-v1",
            fecha=FECHA,
        )

        assert sugerencia.documento_id == "documento-1"
        assert sugerencia.trd_version == 3
        assert sugerencia.serie == "Serie Gestión Documental"
        assert sugerencia.subserie == "Subserie Correspondencia"
        assert sugerencia.confianza == 0.87
        assert sugerencia.modelo_id == "clasificador-ficticio-v1"
        assert sugerencia.evidencia == ["fragmento relevante del texto"]


# RF-CL-003 · Ranking de sugerencias por confianza
class TestRankingPorConfianza:
    def test_dadas_varias_candidatas_cuando_se_consultan_aparecen_de_mayor_a_menor_confianza(self):
        texto = _texto_disponible()
        baja = clasificar(texto, 1, "Serie A", "Subserie A1", 0.30, ["e1"], "modelo-1", FECHA)
        alta = clasificar(texto, 1, "Serie B", "Subserie B1", 0.91, ["e2"], "modelo-1", FECHA)
        media = clasificar(texto, 1, "Serie C", "Subserie C1", 0.55, ["e3"], "modelo-1", FECHA)

        ordenadas = ordenar_por_confianza([baja, alta, media])

        assert ordenadas == [alta, media, baja]


# RF-CL-004 / RF-CL-006 · Traducción a la forma que espera Records/Custodia
class TestTraduccionASugerenciaSaliente:
    def test_una_sugerencia_de_clasificacion_se_traduce_con_tipo_clasificacion(self):
        texto = _texto_disponible()
        sugerencia = clasificar(texto, 2, "Serie A", "Subserie A1", 0.75, ["e1"], "modelo-1", FECHA)

        saliente = a_sugerencia_saliente_de_clasificacion(sugerencia)

        assert saliente == SugerenciaSaliente(
            documento_id="documento-1",
            tipo="clasificacion",
            contenido_propuesto="Serie A/Subserie A1",
            modelo_id="modelo-1",
            evidencia=["e1"],
            confianza=0.75,
            fecha=FECHA,
        )

    def test_una_sugerencia_de_agrupamiento_a_expediente_existente_se_traduce_con_tipo_agrupamiento(self):
        texto = _texto_disponible()
        sugerencia = agrupar(texto, "expediente-42", 0.6, ["e1"], "modelo-1", FECHA)

        saliente = a_sugerencia_saliente_de_agrupamiento(sugerencia)

        assert saliente.tipo == "agrupamiento"
        assert saliente.contenido_propuesto == "expediente-42"
        assert saliente.documento_id == "documento-1"

    def test_una_sugerencia_de_agrupamiento_a_expediente_nuevo_se_traduce_con_marca_explicita(self):
        texto = _texto_disponible()
        sugerencia = agrupar(texto, None, 0.6, ["e1"], "modelo-1", FECHA)

        saliente = a_sugerencia_saliente_de_agrupamiento(sugerencia)

        assert saliente.contenido_propuesto == "EXPEDIENTE_NUEVO"


# RF-CL-005 · Agrupamiento probabilístico en expedientes
class TestAgrupamientoProbabilistico:
    def test_dado_un_texto_extraido_cuando_se_evalua_su_agrupamiento_entonces_genera_sugerencia_con_confianza(self):
        texto = _texto_disponible()

        sugerencia = agrupar(texto, "expediente-7", 0.66, ["fragmento"], "agrupador-ficticio-v1", FECHA)

        assert sugerencia == SugerenciaDeAgrupamiento(
            documento_id="documento-1",
            expediente_propuesto="expediente-7",
            confianza=0.66,
            evidencia=["fragmento"],
            modelo_id="agrupador-ficticio-v1",
            fecha=FECHA,
        )

    def test_dado_un_texto_extraido_sin_expediente_existente_razonable_entonces_marca_expediente_nuevo(self):
        texto = _texto_disponible()

        sugerencia = agrupar(texto, None, 0.4, ["fragmento"], "agrupador-ficticio-v1", FECHA)

        assert sugerencia.expediente_propuesto is None


# RF-CL-007 · Nunca materializa directamente
class TestNuncaMaterializaDirectamente:
    def test_el_modulo_no_expone_ninguna_operacion_de_materializacion(self):
        prohibidos = {
            "materializar",
            "aprobar",
            "decidir",
            "cambiar_clasificacion",
            "cambiar_expediente",
            "confirmar",
        }
        expuestos = {nombre for nombre in vars(dominio) if not nombre.startswith("_")}

        assert prohibidos.isdisjoint(expuestos)


# RF-CL-008 · Evidencia trazable por sugerencia
class TestEvidenciaTrazable:
    def test_una_sugerencia_de_clasificacion_expone_la_evidencia_que_la_sustenta(self):
        texto = _texto_disponible()

        sugerencia = clasificar(texto, 1, "Serie A", "Subserie A1", 0.8, ["frase 1", "frase 2"], "modelo-1", FECHA)

        assert sugerencia.evidencia == ["frase 1", "frase 2"]

    def test_una_sugerencia_de_agrupamiento_expone_la_evidencia_que_la_sustenta(self):
        texto = _texto_disponible()

        sugerencia = agrupar(texto, "expediente-1", 0.8, ["frase 1"], "modelo-1", FECHA)

        assert sugerencia.evidencia == ["frase 1"]


# RF-CL-009 · Uso de la TRD vigente para clasificaciones nuevas
class TestUsoDeTrdVigente:
    def test_una_publicacion_posterior_de_trd_no_cambia_la_version_de_sugerencias_ya_generadas(self):
        texto = _texto_disponible()
        sugerencia_con_trd_v1 = clasificar(texto, 1, "Serie A", "Subserie A1", 0.7, ["e1"], "modelo-1", FECHA)

        sugerencia_con_trd_v2 = clasificar(texto, 2, "Serie A", "Subserie A2", 0.7, ["e1"], "modelo-1", FECHA)

        assert sugerencia_con_trd_v1.trd_version == 1
        assert sugerencia_con_trd_v2.trd_version == 2


# RF-CL-010 · Cero pérdida silenciosa
class TestCeroPerdidaSilenciosa:
    def test_dado_un_texto_sin_senal_suficiente_cuando_se_evalua_entonces_queda_marcado_no_clasificable(self):
        texto = _texto_disponible()

        marca = marcar_no_clasificable(texto, razon="Texto extraído vacío.", actor="clasificador-ficticio-v1", fecha=FECHA)

        assert marca == MarcaNoClasificable(
            documento_id="documento-1", razon="Texto extraído vacío.", actor="clasificador-ficticio-v1", fecha=FECHA
        )
