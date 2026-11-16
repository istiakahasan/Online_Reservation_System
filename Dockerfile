# ==============================================================================
# Stage 1: Build stage (JDK 21)
# ==============================================================================
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Install bash and utility tools
RUN apk add --no-cache bash

# Copy Maven wrapper configuration and descriptor for dependency caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Pre-fetch project dependencies in a separate layer to leverage Docker layer cache
RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -B || true

# Copy project source code
COPY src/ ./src/

# Package production application artifact
RUN ./mvnw clean package -DskipTests -B

# Extract layered jar layers for fine-grained container image caching
RUN mkdir -p /build/extracted && \
    (java -Djarmode=tools -jar target/*.jar extract --layers --destination /build/extracted 2>/dev/null || \
     (cd /build/extracted && java -Djarmode=layertools -jar /build/target/*.jar extract))

# ==============================================================================
# Stage 2: Runtime stage (JRE 21 Minimal, Non-Root User)
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine AS runner

# Create non-root user and group with explicit UID/GID for least-privilege security
RUN addgroup -S -g 10001 appgroup && \
    adduser -S -u 10001 -G appgroup -s /sbin/nologin appuser

WORKDIR /app

# Copy extracted layers in order of change frequency:
# 1. External dependencies (rarely change)
# 2. Spring Boot loader infrastructure (rarely changes)
# 3. Snapshot dependencies (changes occasionally)
# 4. Application code and resources (changes frequently)
COPY --from=builder --chown=appuser:appgroup /build/extracted/dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=appuser:appgroup /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /build/extracted/application/ ./

# Switch execution context to the unprivileged non-root user
USER 10001:10001

# Expose HTTP traffic port
EXPOSE 8080

# Production JVM flags with container awareness, memory limits, and Virtual Threads optimization
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:+ExitOnOutOfMemoryError -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

# Container healthcheck targeting the Actuator liveness endpoint
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# Launch the application using Spring Boot's Layered JarLauncher
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
