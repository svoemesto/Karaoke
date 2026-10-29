# CI-образ раннера (`ci-node22-jdk17`)

Образ, на котором работают джобы `.forgejo/workflows/lint.yml`:

```
registry.sm-karaoke.ru/svoemesto/ci-node22-jdk17:1
```

База — `debian:bookworm-slim`: правило R-05 (`check-docker-image-tags.sh`)
разрешает для node только `22-alpine`, а alpine не годится — JDK и Chromium
требуют glibc. Поэтому Node 22 ставится из официального tarball, а не из
`node:`-образа.

Содержит всё, что раньше скачивалось на каждом прогоне: node 22, JDK 17
(Temurin + toolcache для `setup-java`), python3 + PyYAML, jq, sudo, ffmpeg,
docker CLI + buildx, iproute2, Playwright chromium 1.63.0, дистрибутив
Gradle 9.8.2.

## Сборка и публикация

```bash
DOCKER_CONFIG="$PWD/.docker" docker login registry.sm-karaoke.ru -u admin
DOCKER_CONFIG="$PWD/.docker" docker build -t registry.sm-karaoke.ru/svoemesto/ci-node22-jdk17:1 deploy/ci-image
DOCKER_CONFIG="$PWD/.docker" docker push registry.sm-karaoke.ru/svoemesto/ci-node22-jdk17:1
```

Скачивания при сборке (JDK, Gradle, Playwright, docker CLI) происходят **один
раз на сборку образа**, а не на каждый прогон CI.

## Как раннер начинает его использовать

Метка в `.scratch/karaoke/runner/config.yml` и `.runner`:

```yaml
labels: ["ubuntu-latest:docker://registry.sm-karaoke.ru/svoemesto/ci-node22-jdk17:1"]
```

После правки — перезапуск демона (`start.sh`).

## Когда пересобирать

- обновился Gradle в `gradle/wrapper/gradle-wrapper.properties`
  (нужен новый хеш-каталог — см. комментарий в Dockerfile);
- обновился `@playwright/test` в `karaoke-public/package-lock.json`;
- нужен новый инструмент в джобах.

## Проверка после сборки

```bash
docker run --rm registry.sm-karaoke.ru/svoemesto/ci-node22-jdk17:1 sh -c \
  'java -version && node --version && jq --version && ffmpeg -version | head -1'
```
