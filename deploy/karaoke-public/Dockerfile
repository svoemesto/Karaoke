FROM sm-karaoke.ru:5443/library/node:22-alpine AS build-stage

ARG VERSION
ARG APP_VERSION

ENV VERSION=${VERSION}
ENV APP_VERSION=${VERSION}

WORKDIR /app
COPY ./karaoke-public/package*.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm install
COPY ./karaoke-public/ .
RUN npm run build

FROM sm-karaoke.ru:5443/library/nginx:stable AS production-stage
RUN mkdir /app
COPY --from=build-stage /app/dist /app
