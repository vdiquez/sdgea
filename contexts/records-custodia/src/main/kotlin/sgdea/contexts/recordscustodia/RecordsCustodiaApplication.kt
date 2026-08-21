package sgdea.contexts.recordscustodia

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

// specs/spec-infra-servicios.md §2: cada bounded context es su propio
// proceso/servicio HTTP (Spring Boot). Este es el punto de entrada de
// records-custodia; no contiene lógica de dominio.
@SpringBootApplication
class RecordsCustodiaApplication

fun main(args: Array<String>) {
    runApplication<RecordsCustodiaApplication>(*args)
}
