# Imagen de clasificacion (specs/spec-infra-servicios.md §12).
#
# Tercer contenedor Python/FastAPI de este proyecto (mismo patrón que
# contexts/normalizacion/Dockerfile y contexts/extraccion/Dockerfile). A
# diferencia de esos dos, este contexto no tiene persistencia propia
# (specs/003-clasificacion/spec.md §3: "no mantiene estado propio de sus
# sugerencias después de entregarlas") — sin persistencia.py, sin Postgres:
# es un orquestador HTTP puro sobre records-custodia, mismo criterio "sin
# base de datos propia" que contexts/validacion-humana/Dockerfile.
# contexts/clasificacion es miembro del workspace `uv` de la raíz (uv.lock
# compartido con eval-harness/platform-python/los otros contextos Python),
# así que el build necesita el repo completo, igual que los otros dos
# Dockerfiles Python:
#   docker build -f contexts/clasificacion/Dockerfile .   (desde la raíz)
# deploy/docker-compose.{saas,onprem}.yml lo invocan así (`context: ..` +
# `dockerfile: contexts/clasificacion/Dockerfile`, relativo a `deploy/`).
#
# `--no-dev`: excluye pytest/httpx de test (solo hacen falta para correr los
# tests, que ./test.sh ya corre en CI antes de construir la imagen — mismo
# criterio que `-x test` en los Dockerfiles de Kotlin).
FROM python:3.12-slim AS build
WORKDIR /workspace
COPY . .
RUN pip install --no-cache-dir uv
RUN uv sync --directory contexts/clasificacion --no-dev --frozen

FROM python:3.12-slim
WORKDIR /app
COPY --from=build /workspace/.venv /app/.venv
COPY --from=build /workspace/contexts/clasificacion/dominio.py /workspace/contexts/clasificacion/integracion.py /workspace/contexts/clasificacion/api.py /workspace/contexts/clasificacion/main.py ./
ENV PATH="/app/.venv/bin:$PATH"

# Puerto por defecto de main.py (SERVER_PORT). Sin `ports:` en docker-compose,
# mismo criterio que los otros seis servicios.
EXPOSE 8087

ENTRYPOINT ["python", "main.py"]
