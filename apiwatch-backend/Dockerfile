FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 apiwatch
COPY --from=build /workspace/target/apiwatch-backend-*.jar app.jar
USER apiwatch
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
