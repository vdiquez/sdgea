# Imagen de validacion-humana (specs/spec-infra-servicios.md §6).
#
# Mismo tratamiento que contexts/records-custodia/Dockerfile: build
# multi-módulo (depende de platform-kotlin), contexto de build = raíz del
# repositorio:
#   docker build -f contexts/validacion-humana/Dockerfile .   (desde la raíz)
# deploy/docker-compose.{saas,onprem}.yml lo invocan así (`context: ..` +
# `dockerfile: contexts/validacion-humana/Dockerfile`, relativo a `deploy/`).
#
# A diferencia de los otros tres servicios, este no tiene base de datos propia
# (specs/007-validacion-humana/spec.md §3) — sus variables de entorno son las
# URLs de los otros dos servicios (RECORDS_CUSTODIA_BASE_URL,
# SEGURIDAD_ACCESO_BASE_URL), no credenciales de Postgres.
#
# JDK 21 en ambas etapas (jvmToolchain(21) en build.gradle.kts). Tests
# omitidos en este build (`-x test`) por la misma razón que los otros tres:
# ./test.sh ya los corre en CI antes de construir la imagen.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :contexts:validacion-humana:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/contexts/validacion-humana/build/libs/validacion-humana.jar app.jar

# Puerto por defecto de application.yml (SERVER_PORT). Sin `ports:` en
# docker-compose por ahora, mismo criterio que los otros tres — nadie fuera de
# la red interna necesita llegar directo a este servicio.
EXPOSE 8084

ENTRYPOINT ["java", "-jar", "app.jar"]
