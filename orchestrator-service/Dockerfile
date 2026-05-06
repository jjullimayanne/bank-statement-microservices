FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

COPY pom.xml ./pom.xml
COPY common/pom.xml ./common/pom.xml
COPY common/src ./common/src
COPY orchestrator-service/pom.xml ./orchestrator-service/pom.xml
COPY orchestrator-service/src ./orchestrator-service/src

RUN mvn -pl common install -DskipTests -q
RUN mvn -pl orchestrator-service package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/orchestrator-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
