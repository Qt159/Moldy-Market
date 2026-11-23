# Stage 1: Build JAR (Java 17 + Maven)
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

# Dùng Google Maven Central Mirror (Asia) để tránh lỗi 429 của Render
RUN mkdir -p /root/.m2 && printf '<settings><mirrors><mirror><id>gcs-asia</id><mirrorOf>central</mirrorOf><url>https://maven-central-asia.storage-download.googleapis.com/maven2</url></mirror></mirrors></settings>' > /root/.m2/settings.xml

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

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar app.jar"]