# Multi-stage build for AI Concierge Service
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S concierge && adduser -S concierge -G concierge

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

RUN chown -R concierge:concierge /app
USER concierge

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/health || exit 1

ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "-Xmx512m", "app.jar"]
