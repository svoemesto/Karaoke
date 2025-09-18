FROM openjdk:22
ARG VERSION
ARG APP_VERSION

COPY karaoke-web-${APP_VERSION}.jar /app.jar

ENV APP_VERSION ${VERSION}
ENV TZ="Europe/Moscow"

ENTRYPOINT ["java","-Dfile.encoding=UTF-8","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]