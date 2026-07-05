# ── Stage 1: Build ─────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Copy Maven wrapper and pom first (caches dependencies layer)
COPY mvnw mvnw.cmd ./
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies (cached unless pom.xml changes)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source and build
COPY src src
RUN ./mvnw package -DskipTests -B

# ── Stage 2: Runtime ───────────────────────────────────────────────────
FROM eclipse-temurin:25-jre
WORKDIR /app

# Non-root user for security
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser
USER appuser

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# Cloud Run sets PORT env var — Spring Boot reads server.port
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]
