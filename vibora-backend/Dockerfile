# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Etapa 1: compilar. Aqui NO se ejecutan los tests (-DskipTests): corren en CI
# (.github/workflows/ci.yml, job "backend") y necesitan Docker para Testcontainers,
# que no esta disponible durante un `docker build`.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

# Primero lo que cambia poco (wrapper + pom) para cachear las dependencias de Maven.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

# Jar en capas (dependencias / loader / snapshots / aplicacion): solo la ultima cambia a menudo.
COPY src src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -DskipTests package \
 && mv target/vibora-backend-*.jar target/application.jar \
 && java -Djarmode=tools -jar target/application.jar extract --layers --destination /build/extracted

# ---------------------------------------------------------------------------
# Etapa 2: ejecutar. JRE 25 sobre Alpine (trae wget de busybox para el HEALTHCHECK).
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine AS runtime

RUN addgroup -S vibora && adduser -S -G vibora -H -s /sbin/nologin vibora
WORKDIR /app

COPY --from=build --chown=vibora:vibora /build/extracted/dependencies/ ./
COPY --from=build --chown=vibora:vibora /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=vibora:vibora /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=vibora:vibora /build/extracted/application/ ./

USER vibora

# Sin secretos en la imagen: la configuracion llega por entorno (SPRING_DATASOURCE_*, VIBORA_JWT_SECRET,
# VIBORA_WEBSOCKET_ORIGENES_PERMITIDOS...). Opciones extra de la JVM: JAVA_OPTS.
ENV JAVA_OPTS=""
EXPOSE 8081

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -q -O - http://127.0.0.1:8081/actuator/health | grep -q '"status":"UP"' || exit 1

# Falla pronto y claro si no hay secreto JWT (la imagen no lleva ninguno por defecto). Con docker compose, esto es solo
# la segunda linea de defensa: el servicio comprobar-VIBORA_JWT_SECRET ya aborta el `up` antes de crear el backend.
ENTRYPOINT ["sh", "-c", "[ -n \"$VIBORA_JWT_SECRET\" ] || { echo 'ERROR: define VIBORA_JWT_SECRET (>= 32 bytes, aleatorio)' >&2; exit 1; }; exec java -XX:MaxRAMPercentage=75 $JAVA_OPTS -jar application.jar"]
