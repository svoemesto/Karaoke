FROM openjdk:22
ARG VERSION
ARG APP_VERSION

COPY karaoke-web-${APP_VERSION}.jar /app.jar

#COPY ../../../deploy/karaoke-web/Roboto-Black.ttf /Roboto-Black.ttf
#RUN mkdir -p /usr/share/fonts/truetype/
#RUN install -m644 Roboto-Black.ttf /usr/share/fonts/truetype/
#RUN rm ./Roboto-Black.ttf

ENV APP_VERSION ${VERSION}
ENV TZ="Europe/Moscow"

ENTRYPOINT ["java","-Dfile.encoding=UTF-8","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]