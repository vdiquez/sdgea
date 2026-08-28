# Imagen de extraccion (specs/spec-infra-servicios.md §11).
#
# Segundo contenedor Python/FastAPI de este proyecto (mismo patrón que
# contexts/normalizacion/Dockerfile, T-35). contexts/extraccion es miembro
# del workspace `uv` de la raíz (uv.lock compartido con eval-harness/
# platform-python/los otros contextos Python), así que el build necesita el
# repo completo, igual que los Dockerfiles de Kotlin necesitan la raíz para
# resolver `:platform-kotlin`:
#   docker build -f contexts/extraccion/Dockerfile .   (desde la raíz)
# deploy/docker-compose.{saas,onprem}.yml lo invocan así (`context: ..` +
# `dockerfile: contexts/extraccion/Dockerfile`, relativo a `deploy/`).
#
# `--no-dev`: excluye pytest (solo hace falta para correr los tests, que
# ./test.sh ya corre en CI antes de construir la imagen — mismo criterio que
# `-x test` en los Dockerfiles de Kotlin).
FROM python:3.12-slim AS build
WORKDIR /workspace
COPY . .
RUN pip install --no-cache-dir uv
RUN uv sync --directory contexts/extraccion --no-dev --frozen

FROM python:3.12-slim
WORKDIR /app
COPY --from=build /workspace/.venv /app/.venv
COPY --from=build /workspace/contexts/extraccion/dominio.py /workspace/contexts/extraccion/persistencia.py /workspace/contexts/extraccion/integracion.py /workspace/contexts/extraccion/api.py /workspace/contexts/extraccion/main.py ./
ENV PATH="/app/.venv/bin:$PATH"

# Puerto por defecto de main.py (SERVER_PORT). Sin `ports:` en docker-compose,
# mismo criterio que los otros cinco servicios.
EXPOSE 8086

ENTRYPOINT ["python", "main.py"]
