# Базовый образ — вендорен в mirror/** (пин в deploy/prod-single-host/registry/images.lock).
# Причина: pull-through-зона library/** нерабочая — Zot синхронизирует образ из
# Docker Hub 15–17 минут, а его storage-GC вычищает синхронизированное содержимое
# (деплой 2026-11-14, см. RUNBOOK § 2б). Подробности — knowledge/system/infra/deploy-overview.md.
FROM sm-karaoke.ru:5443/mirror/library/eclipse-temurin:22-jre-jammy
ARG VERSION
ARG APP_VERSION

COPY karaoke-web-${APP_VERSION}.jar /app.jar

ENV APP_VERSION=${VERSION:-unknown}
ENV TZ="Europe/Moscow"

ENTRYPOINT ["java","-Dfile.encoding=UTF-8","-Djava.security.egd=file:/dev/./urandom","-Djava.awt.headless=true","-jar","/app.jar"]
