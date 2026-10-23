# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY domain/ domain/
COPY application/ application/
COPY adapter/ adapter/
COPY bootstrap/ bootstrap/
COPY docs/openapi.yaml docs/openapi.yaml
# Verification runs separately with Docker available for Testcontainers.
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests package

FROM eclipse-temurin:25-jre AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/bootstrap/target/bootstrap-1.0-SNAPSHOT.jar app.jar
USER app:app
ENV PORT=8080
EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=3s --start-period=30s --retries=10 \
    CMD curl --fail --silent --output /dev/null "http://localhost:${PORT}/time-deposits?size=1" || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
