# Imagen de normalizacion (specs/spec-infra-servicios.md §7).
#
# Primer contenedor Python/FastAPI de este proyecto — los cuatro anteriores
# son Kotlin/Spring. contexts/normalizacion es miembro del workspace `uv` de
# la raíz (uv.lock compartido con eval-harness/platform-python/los otros
# contextos Python), así que el build necesita el repo completo, igual que
# los Dockerfiles de Kotlin necesitan la raíz para resolver `:platform-kotlin`:
#   docker build -f contexts/normalizacion/Dockerfile .   (desde la raíz)
# deploy/docker-compose.{saas,onprem}.yml lo invocan así (`context: ..` +
# `dockerfile: contexts/normalizacion/Dockerfile`, relativo a `deploy/`).
#
# `--no-dev`: excluye pytest/httpx (solo hacen falta para correr los tests,
# que ./test.sh ya corre en CI antes de construir la imagen — mismo criterio
# que `-x test` en los Dockerfiles de Kotlin).
FROM python:3.12-slim AS build
WORKDIR /workspace
COPY . .
RUN pip install --no-cache-dir uv
RUN uv sync --directory contexts/normalizacion --no-dev --frozen

FROM python:3.12-slim
WORKDIR /app
COPY --from=build /workspace/.venv /app/.venv
COPY --from=build /workspace/contexts/normalizacion/dominio.py /workspace/contexts/normalizacion/persistencia.py /workspace/contexts/normalizacion/api.py /workspace/contexts/normalizacion/main.py ./
ENV PATH="/app/.venv/bin:$PATH"

# Puerto por defecto de main.py (SERVER_PORT). Sin `ports:` en docker-compose,
# mismo criterio que los otros cuatro servicios.
EXPOSE 8085

ENTRYPOINT ["python", "main.py"]
