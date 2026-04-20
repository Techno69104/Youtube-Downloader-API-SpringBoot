FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine

# Install runtime dependencies (no build tools needed)
RUN apk add --no-cache \
    python3 \
    ffmpeg \
    curl

# Download yt-dlp binary directly (no compilation!)
RUN curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp \
    && chmod a+rx /usr/local/bin/yt-dlp

RUN mkdir -p /app/downloads && chmod 777 /app/downloads
WORKDIR /app
COPY --from=builder /app/target/app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
