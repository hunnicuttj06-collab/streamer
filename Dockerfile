FROM --platform=linux/amd64 maven:3.9-eclipse-temurin-17
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package
ENV APP_DATA_DIR=/data PLAYER_BIND_ADDRESS=0.0.0.0 PLAYER_PORT=8080
EXPOSE 8080
CMD ["mvn", "-B", "exec:java"]
