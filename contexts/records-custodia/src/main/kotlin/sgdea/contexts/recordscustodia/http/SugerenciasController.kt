package sgdea.contexts.recordscustodia.http

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import sgdea.contexts.recordscustodia.CapaAnticorrupcionSugerencias
import sgdea.contexts.recordscustodia.Sugerencia
import sgdea.contexts.recordscustodia.SugerenciaEntrante
import sgdea.contexts.recordscustodia.configuracion.RecepcionDeSugerenciasTransaccional

// specs/spec-infra-servicios.md §4: POST /sugerencias -> RF-RC-003
// (CapaAnticorrupcionSugerencias.recibir). El "EMISOR FICTICIO" sigue siendo
// quien construye el cuerpo de esta petición (T-08); este controlador no
// simula ningún clasificador, solo traduce la entrada HTTP a
// SugerenciaEntrante.
// T-21: usa el wrapper transaccional, no CapaAnticorrupcionSugerencias
// directo, para que guardar la sugerencia y anexar su evento de auditoría
// sean atómicos (P-08) — ver RecepcionDeSugerenciasTransaccional.
@RestController
@RequestMapping("/sugerencias")
class SugerenciasController(
    private val capa: RecepcionDeSugerenciasTransaccional,
    private val capaDeLectura: CapaAnticorrupcionSugerencias,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun recibir(@RequestBody request: RecibirSugerenciaRequest): Sugerencia =
        capa.recibir(
            SugerenciaEntrante(
                documentoId = request.documentoId,
                tipo = request.tipo,
                contenidoPropuesto = request.contenidoPropuesto,
                modeloId = request.modeloId,
                evidencia = request.evidencia,
                confianza = request.confianza,
                formaOriginal = request.formaOriginal,
            ),
            fecha = request.fecha,
        )

    // RF-VH-001 (specs/007-validacion-humana/spec.md): la cola de revisión de
    // Validación Humana lee de aquí. Endpoint de solo lectura — sin wrapper
    // transaccional, no escribe nada.
    @GetMapping("/pendientes")
    fun pendientes(): List<Sugerencia> = capaDeLectura.sugerenciasPendientes()
}
