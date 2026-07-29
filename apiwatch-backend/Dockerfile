FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system --gid 10001 apiwatch \
    && useradd --system --uid 10001 --gid apiwatch --shell /usr/sbin/nologin apiwatch
COPY --from=build --chown=10001:10001 /workspace/target/apiwatch-backend-*.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
