FROM openjdk:17.0.1-jdk-slim

# RUN apk add --update --no-cache python3 && ln -sf python3 /usr/bin/python &&\
#     python3 -m ensurepip &&\
#     pip3 install --no-cache --upgrade pip setuptools
#
# Run apt-get -y update \
#     && apt-get -y install curl
#
# RUN curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp
#
# RUN chmod a+rx /usr/local/bin/yt-dlp

RUN apt update \
    && apt install -y curl ffmpeg \
    && rm -rf /var/lib/apt/lists/*

# This is on a separate line because youtube-dl needs to be frequently updated
# RUN apt install --no-install-recommends yt-dlp \
#     && rm -rf /var/lib/apt/lists/*

EXPOSE 8080

ARG JAR_FILE=jar/*.jar
COPY ${JAR_FILE} app.jar
COPY yt-dlp_linux yt-dlp
ENTRYPOINT ["java","-jar","/app.jar"]