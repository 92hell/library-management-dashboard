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
# logs/ and data/ (the H2 database of the "h2" profile) must be writable by the app user.
RUN mkdir -p /app/logs /app/data && chown library /app/logs /app/data
USER library
EXPOSE 8080
ENV LOG_PATH=/app/logs
# Fit small containers (e.g. Render's free 512 MB instance): heap sized from the container limit,
# a lean GC, and faster start-up on a fraction of a CPU.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"
ENTRYPOINT ["java", "-jar", "app.jar"]
