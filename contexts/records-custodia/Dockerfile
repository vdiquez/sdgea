# Imagen de records-custodia (specs/spec-infra-servicios.md §4).
#
# Mismo tratamiento que contexts/captura-ingesta/Dockerfile: build
# multi-módulo (depende de platform-kotlin), contexto de build = raíz del
# repositorio:
#   docker build -f contexts/records-custodia/Dockerfile .   (desde la raíz)
# deploy/docker-compose.{saas,onprem}.yml lo invocan así (`context: ..` +
# `dockerfile: contexts/records-custodia/Dockerfile`, relativo a `deploy/`).
#
# JDK 21 en ambas etapas (jvmToolchain(21) en build.gradle.kts). Tests
# omitidos en este build (`-x test`) por la misma razón que captura-ingesta:
# ./test.sh ya los corre en CI antes de construir la imagen.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :contexts:records-custodia:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/contexts/records-custodia/build/libs/records-custodia.jar app.jar

# Puerto por defecto de application.yml (SERVER_PORT); no se publica al host
# desde docker-compose todavía — specs/spec-infra-servicios.md §7: sin el
# contexto Seguridad y Acceso, estos servicios no deben exponerse fuera de la
# red interna de docker-compose.
EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
