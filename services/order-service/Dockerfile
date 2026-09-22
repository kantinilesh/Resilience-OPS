# ==========================================
# Stage 1: Build & Dependency Resolution
# ==========================================
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Cache Maven dependencies layer
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Compile and package application
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==========================================
# Stage 2: Production Hardened Slim Runtime
# ==========================================
FROM eclipse-temurin:21-jre-alpine

# Set deployment labels
LABEL maintainer="ResilienceOps Engineering" \
      service="order-service" \
      version="1.0.0"

WORKDIR /app

# Security: Create non-root system user and group (UID/GID 10001)
RUN addgroup -S -g 10001 appgroup && \
    adduser -S -u 10001 -G appgroup -h /app -s /sbin/nologin appuser

# Copy executable jar from builder
COPY --from=builder --chown=appuser:appgroup /build/target/*.jar /app/app.jar

# Enforce non-root execution
USER 10001:10001

EXPOSE 8080

# Native Spring Boot Actuator Liveness Healthcheck
HEALTHCHECK --interval=20s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health/liveness || exit 1

# Production Container-Tuned JVM Options
ENTRYPOINT ["java", \
  "-XX:+UseG1GC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "/app/app.jar"]
