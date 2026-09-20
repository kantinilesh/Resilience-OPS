# Multi-stage Dockerfile for remediation-service
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /workspace
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
COPY --from=builder /workspace/target/*.jar app.jar
EXPOSE 8082
HEALTHCHECK --interval=15s --timeout=3s --retries=3 \
  CMD wget -qO- http://localhost:8082/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-XX:+UseG1GC", "-jar", "app.jar"]
