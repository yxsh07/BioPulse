FROM node:24-alpine AS frontend
WORKDIR /ui
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.11-eclipse-temurin-21 AS backend
WORKDIR /build
COPY pom.xml ./
RUN mvn -q dependency:go-offline
COPY src ./src
COPY database ./database
COPY web-simulator ./web-simulator
COPY --from=frontend /ui/dist/ ./src/main/resources/static/
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S biopulse && adduser -S biopulse -G biopulse
USER biopulse
WORKDIR /app
COPY --from=backend /build/target/biopulse-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
