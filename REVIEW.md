# Revisión de `1233298e03b359aa36a254f6cf5bec8f45e253e1` — corrección T-45

## Resultado: OK

Se retira el VETO anterior. `EnviadorDeSugerencias` cumple P-03 en el alcance
que corresponde a este commit: existe un puerto propio y el endpoint depende de
él, mientras `EnviadorDeSugerenciasHttp` es el adaptador de producción hacia
Records/Custodia. No se exige una segunda implementación de despliegue para
esa llamada HTTP interna.

## Decisión sobre P-03

P-03 está acotado a las seis capacidades críticas externas que enumera
literalmente: almacenamiento de objetos, OCR, embeddings, inferencia LLM,
índice vectorial e índice léxico. Para cada una exige una interfaz propia y
dos implementaciones intercambiables (gestionada para SaaS y autoalojada para
on-premise). La enumeración delimita el alcance de la obligación de dos
implementaciones; no convierte toda comunicación HTTP entre contextos internos
en una de esas capacidades ni crea una variante de despliegue adicional.

Records/Custodia y Seguridad/Acceso son contextos internos del mismo código
base y se despliegan en ambos modos conforme a P-02. La llamada HTTP al servicio
interno es la misma en SaaS y on-premise, por lo que `POST /sugerencias` no es
una capacidad externa de la lista de P-03. El puerto sigue siendo un buen límite
de acoplamiento y permite sustituir el adaptador en pruebas, pero P-03 no exige
para este caso dos adaptadores de producción gestionado/autoalojado.

Esta conclusión es consistente con el precedente ratificado en `82f866b` /
`d1471fc`: `VerificadorDeAutorizacion` +
`VerificadorDeAutorizacionHttp` hacia Seguridad/Acceso se revisó como P-03
conforme con una única implementación HTTP de producción. Aplicar ahora la
exigencia de una segunda implementación a `EnviadorDeSugerencias` habría sido
una aplicación inconsistente de P-03. No corresponde abrir una tarea simétrica
para Extracción.

## Verificaciones

- **P-01:** Clasificación produce `SugerenciaSaliente` y la remite a
  `POST /sugerencias`; no materializa directamente estado de documento o
  expediente. La recepción permanece detrás de la capa anticorrupción de
  Records/Custodia y de una decisión humana posterior.
- **P-08:** Clasificación no mantiene estado propio. La transición de
  recepción ocurre en Records/Custodia, donde ya se registra
  `SUGERENCIA_RECIBIDA`.
- **RF-CL-010:** Una lista vacía de candidatas recibe 409 explícito. Esto no
  inventa el criterio pendiente de "no clasificable".
- **Pruebas:** Los tres tests añadidos cubren el rechazo de lista vacía y la
  ruta HTTP; `test_integracion.py` verifica método, URL y cuerpo. No se
  aprecia un doble que oculte el comportamiento del adaptador.
- **Specs, referencias y umbrales:** El commit no cambia `specs/`; no añade
  referencias normativas ni umbrales.

La tarea T-45b se retira de `TODO.md`: derivaba exclusivamente del VETO de P-03
que esta revisión deja sin efecto.
