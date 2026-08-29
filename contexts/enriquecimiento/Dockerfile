# Imagen de enriquecimiento (specs/spec-infra-servicios.md §13).
#
# Cuarto contenedor Python/FastAPI de este proyecto (mismo patrón que
# contexts/normalizacion/Dockerfile, contexts/extraccion/Dockerfile y
# contexts/clasificacion/Dockerfile). Igual que clasificacion, este contexto
# no tiene persistencia propia (specs/004-enriquecimiento/spec.md §3: "igual
# que Clasificación, este contexto no mantiene estado propio de sus
# sugerencias después de entregarlas") — sin persistencia.py, sin Postgres:
# es un orquestador HTTP puro sobre records-custodia, mismo criterio "sin
# base de datos propia" que contexts/validacion-humana/Dockerfile y
# contexts/clasificacion/Dockerfile.
# contexts/enriquecimiento es miembro del workspace `uv` de la raíz (uv.lock
# compartido con eval-harness/platform-python/los otros contextos Python),
# así que el build necesita el repo completo, igual que los otros Dockerfiles
# Python:
#   docker build -f contexts/enriquecimiento/Dockerfile .   (desde la raíz)
# deploy/docker-compose.{saas,onprem}.yml lo invocan así (`context: ..` +
# `dockerfile: contexts/enriquecimiento/Dockerfile`, relativo a `deploy/`).
#
# `--no-dev`: excluye pytest/httpx-mock/etc. de test (solo hacen falta para
# correr los tests, que ./test.sh ya corre en CI antes de construir la
# imagen — mismo criterio que `-x test` en los Dockerfiles de Kotlin).
FROM python:3.12-slim AS build
WORKDIR /workspace
COPY . .
RUN pip install --no-cache-dir uv
RUN uv sync --directory contexts/enriquecimiento --no-dev --frozen

FROM python:3.12-slim
WORKDIR /app
COPY --from=build /workspace/.venv /app/.venv
COPY --from=build /workspace/contexts/enriquecimiento/dominio.py /workspace/contexts/enriquecimiento/integracion.py /workspace/contexts/enriquecimiento/api.py /workspace/contexts/enriquecimiento/main.py ./
ENV PATH="/app/.venv/bin:$PATH"

# Puerto por defecto de main.py (SERVER_PORT). Sin `ports:` en docker-compose,
# mismo criterio que los otros siete servicios.
EXPOSE 8088

ENTRYPOINT ["python", "main.py"]
