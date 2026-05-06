FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

COPY pom.xml ./pom.xml
COPY common/pom.xml ./common/pom.xml
COPY common/src ./common/src
COPY ledger-service/pom.xml ./ledger-service/pom.xml
COPY ledger-service/src ./ledger-service/src

RUN mvn -pl common install -DskipTests -q
RUN mvn -pl ledger-service package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/ledger-service/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
