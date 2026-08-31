# Imagen de indexacion-busqueda (specs/spec-infra-servicios.md §14).
#
# Quinto contenedor Python/FastAPI de este proyecto (mismo patrón que
# contexts/extraccion/Dockerfile, T-42). contexts/indexacion-busqueda es
# miembro del workspace `uv` de la raíz (uv.lock compartido con
# eval-harness/platform-python/los otros contextos Python), así que el build
# necesita el repo completo, igual que los Dockerfiles de Kotlin necesitan la
# raíz para resolver `:platform-kotlin`:
#   docker build -f contexts/indexacion-busqueda/Dockerfile .   (desde la raíz)
# deploy/docker-compose.{saas,onprem}.yml lo invocan así (`context: ..` +
# `dockerfile: contexts/indexacion-busqueda/Dockerfile`, relativo a `deploy/`).
#
# `--no-dev`: excluye pytest (solo hace falta para correr los tests, que
# ./test.sh ya corre en CI antes de construir la imagen — mismo criterio que
# `-x test` en los Dockerfiles de Kotlin).
FROM python:3.12-slim AS build
WORKDIR /workspace
COPY . .
RUN pip install --no-cache-dir uv
RUN uv sync --directory contexts/indexacion-busqueda --no-dev --frozen

FROM python:3.12-slim
WORKDIR /app
COPY --from=build /workspace/.venv /app/.venv
COPY --from=build /workspace/contexts/indexacion-busqueda/dominio.py /workspace/contexts/indexacion-busqueda/persistencia.py /workspace/contexts/indexacion-busqueda/integracion.py /workspace/contexts/indexacion-busqueda/api.py /workspace/contexts/indexacion-busqueda/main.py ./
ENV PATH="/app/.venv/bin:$PATH"

# Puerto por defecto de main.py (SERVER_PORT). Sin `ports:` en docker-compose,
# mismo criterio que los otros ocho servicios.
EXPOSE 8089

ENTRYPOINT ["python", "main.py"]
