# Stage 1: Build JAR (Java 17 + Maven)
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

# 1. Build và install module shared vào local .m2
COPY shared ./shared
RUN mvn -f shared/pom.xml clean install -DskipTests

# 2. Tải dependencies của app chính (tận dụng Docker cache)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 3. Copy source và build JAR
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime image nhẹ
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

# Render/Railway cấp biến PORT; Spring đọc SERVER_PORT
ENV SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=75.0 -Dserver.port=${PORT:-${SERVER_PORT}} -jar app.jar"]