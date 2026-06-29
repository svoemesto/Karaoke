FROM eclipse-temurin:17-jre-jammy
ARG VERSION
ARG APP_VERSION

COPY karaoke-web-${APP_VERSION}.jar /app.jar
COPY Roboto-Black.ttf /tmp/Roboto-Black.ttf

RUN mkdir -p /usr/share/fonts/truetype/ && \
    install -m644 /tmp/Roboto-Black.ttf /usr/share/fonts/truetype/ && \
    rm /tmp/Roboto-Black.ttf

ENV APP_VERSION=${VERSION:-unknown}
ENV TZ="Europe/Moscow"

ENTRYPOINT ["java","-Dfile.encoding=UTF-8","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]
