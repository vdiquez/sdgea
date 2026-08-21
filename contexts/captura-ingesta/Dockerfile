# Imagen de captura-ingesta (specs/spec-infra-servicios.md §3).
#
# El build es multi-módulo (captura-ingesta depende de platform-kotlin, ver
# settings.gradle.kts), así que este Dockerfile necesita el repositorio
# completo como contexto de build, no solo este directorio:
#   docker build -f contexts/captura-ingesta/Dockerfile .   (desde la raíz)
# deploy/docker-compose.{saas,onprem}.yml lo invocan así (`context: ..` +
# `dockerfile: contexts/captura-ingesta/Dockerfile`, relativo a `deploy/`).
#
# JDK 21 en ambas etapas porque contexts/captura-ingesta/build.gradle.kts fija
# `jvmToolchain(21)`. Tests omitidos en este build (`-x test`): ya los corre
# `./test.sh` en CI antes de que exista una imagen que construir; repetirlos
# aquí solo duplica tiempo de build, no cobertura.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :contexts:captura-ingesta:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/contexts/captura-ingesta/build/libs/captura-ingesta.jar app.jar

# Puerto por defecto de application.yml (SERVER_PORT); no se publica al host
# desde docker-compose todavía — specs/spec-infra-servicios.md §7: sin el
# contexto Seguridad y Acceso, estos servicios no deben exponerse fuera de la
# red interna de docker-compose.
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
