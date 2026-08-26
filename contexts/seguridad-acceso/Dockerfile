# Imagen de seguridad-acceso (specs/spec-infra-servicios.md §5).
#
# Mismo tratamiento que contexts/records-custodia/Dockerfile: build
# multi-módulo (depende de platform-kotlin), contexto de build = raíz del
# repositorio:
#   docker build -f contexts/seguridad-acceso/Dockerfile .   (desde la raíz)
# deploy/docker-compose.{saas,onprem}.yml lo invocan así (`context: ..` +
# `dockerfile: contexts/seguridad-acceso/Dockerfile`, relativo a `deploy/`).
#
# JDK 21 en ambas etapas (jvmToolchain(21) en build.gradle.kts). Tests
# omitidos en este build (`-x test`) por la misma razón que los otros dos
# servicios: ./test.sh ya los corre en CI antes de construir la imagen.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :contexts:seguridad-acceso:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/contexts/seguridad-acceso/build/libs/seguridad-acceso.jar app.jar

# Puerto por defecto de application.yml (SERVER_PORT). A diferencia de
# captura-ingesta/records-custodia, este SÍ es el contexto que resuelve
# specs/spec-infra-servicios.md §8 — pero la integración real (que los otros
# dos servicios lo llamen) todavía no existe, así que por ahora tampoco se
# publica al host desde docker-compose.
EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]
