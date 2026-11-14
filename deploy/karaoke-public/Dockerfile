# Базовые образы параметризованы (OpenProject #327): при недоступном registry
# локальная сборка может не делать `docker tag`, а передать
# NODE_IMAGE=node:22-alpine NGINX_IMAGE=nginx:stable (см. do.sh).
# Базовые образы — вендорены в mirror/** (пин в deploy/prod-single-host/registry/images.lock).
# Причина: pull-through-зона library/** нерабочая — Zot синхронизирует образ из
# Docker Hub 15–17 минут, а его storage-GC вычищает синхронизированное содержимое
# (деплой 2026-11-14, см. RUNBOOK § 2б). Подробности — knowledge/system/infra/deploy-overview.md.
ARG NODE_IMAGE=sm-karaoke.ru:5443/mirror/library/node:22-alpine
ARG NGINX_IMAGE=sm-karaoke.ru:5443/mirror/library/nginx:stable

FROM ${NODE_IMAGE} AS build-stage

ARG VERSION
ARG APP_VERSION

ENV VERSION=${VERSION}
ENV APP_VERSION=${VERSION}

WORKDIR /app
COPY ./karaoke-public/package*.json ./
# npm ci + cache mount + retries: холодный кеш не должен ронять сборку
# пятиминутным ETIMEDOUT (инцидент 2026-10-22, OpenProject #327).
# NODE_OPTIONS: registry резолвится в IPv6, который из docker-сети не тянет
# большие ответы; maxsockets=2 — обрывы соединений не убивают установку.
ENV NODE_OPTIONS=--dns-result-order=ipv4first
RUN --mount=type=cache,target=/root/.npm \
    npm ci --prefer-offline --no-audit --no-fund \
        --fetch-retries=5 \
        --fetch-retry-mintimeout=20000 \
        --fetch-retry-maxtimeout=120000 \
        --fetch-timeout=600000 \
        --maxsockets=2
COPY ./karaoke-public/ .
RUN npm run build

FROM ${NGINX_IMAGE} AS production-stage
RUN mkdir /app
COPY --from=build-stage /app/dist /app
