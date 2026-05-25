FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

ARG READABLE_PDF_SOURCE="https://github.com/coodersio/readable-pdf-api"
ARG READABLE_PDF_VERSION="0.1.0"
ARG READABLE_PDF_COMMIT="unknown"
ARG READABLE_PDF_TAG="docker"
ARG READABLE_PDF_IMAGE_DIGEST="unknown"

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0" \
    READABLE_PDF_SOURCE="${READABLE_PDF_SOURCE}" \
    READABLE_PDF_VERSION="${READABLE_PDF_VERSION}" \
    READABLE_PDF_COMMIT="${READABLE_PDF_COMMIT}" \
    READABLE_PDF_TAG="${READABLE_PDF_TAG}" \
    READABLE_PDF_IMAGE_DIGEST="${READABLE_PDF_IMAGE_DIGEST}" \
    READABLE_PDF_LICENSE="AGPL-3.0"

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system app && useradd --system --gid app app

COPY --from=build /workspace/target/*.jar /app/readable-pdf-api.jar
RUN chown -R app:app /app

USER app
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=45s --retries=3 \
    CMD curl -fsS http://localhost:8080/api/pdf/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/readable-pdf-api.jar"]
