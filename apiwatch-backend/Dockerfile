# syntax=docker/dockerfile:1.7

FROM maven:3.9.15-eclipse-temurin-26@sha256:029a8e2838ae68238ffb8be407cddbb3f07d4d839c60c6f26c619a69fd184531 AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests dependency:resolve
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre@sha256:273396ed5998598ed1091e8d72711c2d36980a0e65103859c55a4e977a41ffd3
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
