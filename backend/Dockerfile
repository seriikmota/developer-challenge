# syntax=docker/dockerfile:1

# ---- build: compiles and packages the jar (tests run in CI, they need Docker for Testcontainers) ----
# Runs on the machine that builds ($BUILDPLATFORM): the jar is architecture independent, so an arm64 image
# built on an x86 runner compiles natively and only the runtime stage below is arm64.
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# dependencies first, so a source change does not download them again
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/purchase-requests-*.jar extract --layers --launcher --destination target/extracted

# ---- runtime: JRE only, non-root user, one layer per dependency group ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app

COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./

USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"

# start-period is only a grace window (healthy as soon as /health answers UP); a small VM on first boot runs Flyway and the seed
HEALTHCHECK --interval=10s --timeout=3s --start-period=180s --retries=5 \
    CMD wget -qO- http://localhost:${SERVER_PORT:-8080}/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
