# Build Stage
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -Dmaven.test.skip=true

# Runtime Stage
FROM eclipse-temurin:17-jre-alpine

# Install Python, ffmpeg, and yt-dlp
RUN apk add --no-cache \
    python3 \
    ffmpeg \
    curl \
    && curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp \
    && chmod a+rx /usr/local/bin/yt-dlp \
    && ln -sf /usr/bin/python3 /usr/bin/python

# Create downloads directory
RUN mkdir -p /app/downloads && chmod 777 /app/downloads

WORKDIR /app
COPY --from=builder /app/target/app.jar app.jar

# ⬅️ NEW: Copy the cookies file into the container
COPY cookies.txt /app/cookies.txt

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/api/youtube/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
