# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

# Copy Maven wrapper + pom first — layer-cached until pom changes
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q

# Copy source and build
COPY src ./src
RUN ./mvnw package -DskipTests -q

# ── Stage 2: Runtime (distroless for minimal attack surface) ──────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

# Non-root user — security best practice
RUN addgroup -S barley && adduser -S barley -G barley
USER barley

WORKDIR /app

# Copy only the fat JAR from the build stage
COPY --from=builder /app/target/barley-service-*.jar app.jar

# JVM tuning for containers:
# -XX:+UseContainerSupport   — respect cgroup CPU/memory limits (default in JDK 11+)
# -XX:MaxRAMPercentage=75    — use 75% of container RAM for the JVM heap
# -XX:+UseG1GC               — G1 GC balances throughput and pause times for services
# -Djava.security.egd        — faster random for Tomcat session IDs
ENV JAVA_OPTS="\
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UseG1GC \
  -XX:+UseStringDeduplication \
  -Djava.security.egd=file:/dev/./urandom \
  -Dspring.profiles.active=production"

EXPOSE 8080

# Exec form (no shell wrapper) — SIGTERM reaches the JVM directly for graceful shutdown
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/actuator/health || exit 1
