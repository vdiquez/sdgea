package sgdea.contexts.validacionhumana

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

// specs/spec-infra-servicios.md §2: cada bounded context es su propio
// proceso/servicio HTTP (Spring Boot). Este es el punto de entrada de
// validacion-humana; no contiene lógica de dominio.
@SpringBootApplication
class ValidacionHumanaApplication

fun main(args: Array<String>) {
    runApplication<ValidacionHumanaApplication>(*args)
}
