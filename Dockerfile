FROM openjdk:17.0.1-jdk-slim

RUN apt update \
    && apt install -y curl ffmpeg \
    && rm -rf /var/lib/apt/lists/*

# This is on a separate line because youtube-dl needs to be frequently updated
RUN apt update \
    && apt install -y youtube-dl \
    && rm -rf /var/lib/apt/lists/*

ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java","-jar","/app.jar"]