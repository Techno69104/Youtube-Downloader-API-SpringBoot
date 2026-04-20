# Build Stage
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
# Build the application, skipping tests
RUN mvn clean package -Dmaven.test.skip=true

# Runtime Stage
FROM eclipse-temurin:17-jre-alpine

# ==========================================
# CRITICAL FIX: Install Python and yt-dlp
# ==========================================
RUN apk add --no-cache \
    python3 \
    py3-pip \
    ffmpeg \
    && pip3 install --no-cache-dir yt-dlp \
    && ln -s /usr/bin/python3 /usr/bin/python

# Create downloads directory
RUN mkdir -p /app/downloads && chmod 777 /app/downloads

WORKDIR /app
COPY --from=builder /app/target/app.jar app.jar

EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/api/youtube/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
