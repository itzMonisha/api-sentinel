# ==============================================================================
# API Sentinel — Production Multi-Stage Backend Dockerfile
# Stage 1: Build JAR using Eclipse Temurin 17 & Maven Wrapper
# Stage 2: Lightweight Alpine JRE Minimal Runtime with non-root security
# ==============================================================================

FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /build

# Copy Maven wrapper and POM first to leverage Docker layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy application source and build production artifact
COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# ==============================================================================
# Production Runtime Stage
# ==============================================================================
FROM eclipse-temurin:17-jre-alpine AS runner

# Install wget for container health check and dumb-init for process signal forwarding
RUN apk add --no-cache dumb-init wget

# Create unprivileged system user and group
RUN addgroup -S sentinel -g 10001 && \
    adduser -S sentinel -u 10001 -G sentinel

WORKDIR /app

# Copy the built executable fat JAR from the builder stage
COPY --from=builder /build/target/api-sentinel-backend-0.0.1-SNAPSHOT.jar /app/app.jar

# Change ownership to unprivileged sentinel user
RUN chown -R sentinel:sentinel /app

USER sentinel:sentinel

# Standard container-aware JVM memory flags
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8080

EXPOSE 8080

# Health check verifies application responsiveness
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["/usr/bin/dumb-init", "--"]
CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
