FROM eclipse-temurin:22-jre-jammy
ARG VERSION
ARG APP_VERSION

COPY karaoke-web-${APP_VERSION}.jar /app.jar

ENV APP_VERSION=${VERSION:-unknown}
ENV TZ="Europe/Moscow"

ENTRYPOINT ["java","-Dfile.encoding=UTF-8","-Djava.security.egd=file:/dev/./urandom","-Djava.awt.headless=true","-jar","/app.jar"]
