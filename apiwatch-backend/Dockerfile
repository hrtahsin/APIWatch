# syntax=docker/dockerfile:1.7

FROM maven:3.9.9-eclipse-temurin-21@sha256:3a4ab3276a087bf276f79cae96b1af04f53731bec53fb2e651aca79e4b10211e AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests dependency:resolve
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre@sha256:8cef5fc7bebe421363ab543a2f4db5caf7d119d8db67d56b0f56c485d2de4d55
ARG VERSION=dev
ARG REVISION=unknown
LABEL org.opencontainers.image.title="APIWatch Backend" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}" \
      org.opencontainers.image.source="https://github.com/hrtahsin/APIWatch"
WORKDIR /app
RUN groupadd --system --gid 10001 apiwatch \
    && useradd --system --uid 10001 --gid apiwatch --shell /usr/sbin/nologin apiwatch
COPY --from=build --chown=10001:10001 /workspace/target/apiwatch-backend-*.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
