FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

COPY pom.xml ./pom.xml
COPY common/pom.xml ./common/pom.xml
COPY common/src ./common/src
COPY account-service/pom.xml ./account-service/pom.xml
COPY account-service/src ./account-service/src

RUN mvn -pl common install -DskipTests -q
RUN mvn -pl account-service package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/account-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
