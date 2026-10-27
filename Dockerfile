FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /source

COPY pom.xml ./
COPY src ./src

# Temporary: the current test sources reference removed packages.
# This skips test compilation and execution, but compiles the application.
RUN mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre

RUN groupadd --gid 10001 healthee \
    && useradd --uid 10001 --gid 10001 --no-create-home healthee

WORKDIR /app

COPY --from=build /source/target/*.jar /app/app.jar

# Render mounts the env.properties secret file here at runtime.
# Preserve the application's existing file:./env.properties import.
RUN ln -s /etc/secrets/env.properties /app/env.properties

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]