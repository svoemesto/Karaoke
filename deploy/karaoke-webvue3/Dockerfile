FROM node:latest as build-stage

ARG VERSION
ARG APP_VERSION

ENV VERSION ${VERSION}
ENV APP_VERSION ${VERSION}

WORKDIR /app
COPY ./webvue3/package*.json ./
RUN npm install
COPY ./webvue3/ .
RUN npm run build

FROM nginx as production-stage
RUN mkdir /app
COPY --from=build-stage /app/dist /app
