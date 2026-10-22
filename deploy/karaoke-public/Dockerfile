# Базовые образы параметризованы (OpenProject #327): при недоступном registry
# локальная сборка может не делать `docker tag`, а передать
# NODE_IMAGE=node:22-alpine NGINX_IMAGE=nginx:stable (см. do.sh).
ARG NODE_IMAGE=sm-karaoke.ru:5443/library/node:22-alpine
ARG NGINX_IMAGE=sm-karaoke.ru:5443/library/nginx:stable

FROM ${NODE_IMAGE} AS build-stage

ARG VERSION
ARG APP_VERSION

ENV VERSION=${VERSION}
ENV APP_VERSION=${VERSION}

WORKDIR /app
COPY ./karaoke-public/package*.json ./
# npm ci + cache mount + retries: холодный кеш не должен ронять сборку
# пятиминутным ETIMEDOUT (инцидент 2026-10-22, OpenProject #327).
RUN --mount=type=cache,target=/root/.npm \
    npm ci --prefer-offline --no-audit --no-fund \
        --fetch-retries=5 \
        --fetch-retry-mintimeout=20000 \
        --fetch-retry-maxtimeout=120000 \
        --fetch-timeout=600000
COPY ./karaoke-public/ .
RUN npm run build

FROM ${NGINX_IMAGE} AS production-stage
RUN mkdir /app
COPY --from=build-stage /app/dist /app
