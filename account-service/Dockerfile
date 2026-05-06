FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

COPY . .

RUN mvn install -N -DskipTests -q
RUN mvn -pl common install -DskipTests -q
RUN mvn -pl account-service package -DskipTests -q

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /build/account-service/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"] 