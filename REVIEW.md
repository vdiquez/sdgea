# Revisión de `b7cefc660e352929a401a332dc7409eaae92d0a9` — T-46

## Resultado: OK

El commit añade el empaquetado Docker y el wiring Compose de Clasificación, y
reemplaza el stub de su punto de entrada por el arranque real de Uvicorn. Es
consistente con `specs/003-clasificacion/spec.md` y con
`specs/spec-infra-servicios.md` §12.

## Principios constitucionales

- **P-01 — conforme.** El cambio no añade una operación que materialice
  clasificación ni expediente. El proceso expone la misma `api.app` cuyo flujo
  sólo genera `SugerenciaSaliente` y la entrega por
  `EnviadorDeSugerencias` a `POST /sugerencias` de Records/Custodia: la capa
  anticorrupción. La decisión humana sigue siendo la única materialización.
- **P-03 — conforme.** No se incorpora OCR, inferencia, embeddings, índices ni
  otra capacidad externa crítica. La comunicación con Records/Custodia conserva
  el puerto propio `EnviadorDeSugerencias`, con adaptador HTTP intercambiable;
  la orquestación no depende de una implementación concreta.
- **P-08 — conforme.** Clasificación no mantiene estado propio y T-46 no añade
  transiciones. La recepción de la sugerencia continúa en Records/Custodia,
  donde el contrato existente emite el evento de auditoría correspondiente.

## Specs, referencias y umbrales

Se modificó `specs/spec-infra-servicios.md`. No aparecen Acuerdo, Ley,
Decreto, ISO ni otra referencia normativa nueva. El único número introducido
es el puerto técnico `8087` (y su mapeo `8087:8087`), no un umbral normativo,
de negocio o evaluación. Sus referencias a T-46, T-31 y a la spec de
Clasificación ya existente son trazabilidad interna, no fuentes inventadas.

## Tests y honestidad

Las 27 pruebas de Clasificación pasan. Los dobles de `test_api.py` se usan
para probar la composición HTTP-dominio sin red y no amañan el adaptador:
`test_integracion.py` usa `httpx.MockTransport` y verifica el método, URL y
cuerpo exacto que `EnviadorDeSugerenciasHttp` manda a Records/Custodia.

T-46 no tiene un RF nuevo con Dado/Cuando/Entonces; es empaquetado. La suite
previa no cubría el entrypoint, pero el defecto del stub fue corregido y esta
revisión importó `main.app` con éxito. También validó ambas composiciones
`saas` y `onprem` combinadas con los puertos locales mediante `docker compose
config --quiet`. No se construyó ni ejecutó una imagen/contenedor real: T-47
mantiene pendiente esa comprobación end-to-end. Esa limitación queda
documentada y no se presenta como evidencia inexistente.

## Verificación realizada

- `git diff --check HEAD^ HEAD`: sin errores.
- `uv run --directory contexts/clasificacion pytest`: **27 passed**.
- Importación de `main.app`: correcta.
- `docker compose ... config --quiet` para SaaS y on-prem con
  `docker-compose.local-ports.yml`: correcto.
