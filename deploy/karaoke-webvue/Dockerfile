FROM node:latest as build-stage

ARG VERSION
ARG APP_VERSION

ENV VERSION ${VERSION}
ENV APP_VERSION ${VERSION}

WORKDIR /app
COPY ./web/package*.json ./
RUN npm install bootstrap jquery popper.js
COPY ./web/ .
RUN npm run build

FROM nginx as production-stage
RUN mkdir /app
COPY --from=build-stage /app/dist /app
COPY ./web/nginx.conf /etc/nginx/nginx.conf