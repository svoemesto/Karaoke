FROM openjdk:17
ARG VERSION
ARG APP_VERSION

COPY karaoke-app-${APP_VERSION}.jar /app.jar

ENV APP_VERSION ${VERSION}
ENV TZ="Europe/Moscow"

ENTRYPOINT ["java","-Dfile.encoding=UTF-8","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]