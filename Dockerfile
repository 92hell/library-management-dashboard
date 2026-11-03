# Multi-stage build: React app -> Spring Boot jar -> slim runtime image.
FROM node:22-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /app
COPY pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY src ./src
COPY --from=frontend /app/frontend/dist ./src/main/resources/static
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:17-jre
RUN useradd --system --create-home library
WORKDIR /app
COPY --from=backend /app/target/library-dashboard-*.jar app.jar
RUN mkdir -p /app/logs && chown library /app/logs
USER library
EXPOSE 8080
ENV LOG_PATH=/app/logs
ENTRYPOINT ["java", "-jar", "app.jar"]
