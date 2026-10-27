FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /source
COPY pom.xml ./
COPY src ./src
RUN mvn -B verify

FROM eclipse-temurin:17-jre
RUN groupadd --gid 10001 healthee && useradd --uid 10001 --gid 10001 --no-create-home healthee
WORKDIR /app
COPY --from=build /source/target/healthee-backend-1.0.0.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
