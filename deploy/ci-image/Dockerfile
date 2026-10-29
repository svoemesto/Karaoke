# CI-образ для раннера Forgejo: node 22 + JDK 17 + python3/PyYAML + jq + sudo +
# ffmpeg + docker CLI/buildx + iproute2 + Playwright chromium + Gradle 9.8.2.
#
# Зачем: каждый джоб `lint.yml` не должен ходить в интернет. Всё, что раньше
# скачивалось в каждом прогоне (JDK, Gradle, Playwright, ffmpeg, python-пакеты,
# docker CLI), лежит здесь и переиспользуется.
#
# Сборка и публикация — см. README.md рядом.
FROM node:22-bookworm

RUN apt-get update && apt-get install -y --no-install-recommends \
      openjdk-17-jdk-headless \
      python3 python3-pip python3-venv python3-yaml \
      jq sudo git curl ca-certificates unzip xz-utils iproute2 ffmpeg \
      libssl-dev \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH=${JAVA_HOME}/bin:${PATH}

# docker CLI (клиент, без демона): e2e-джоб вызывает docker build/run через
# смонтированный docker.sock. BuildKit обязателен — Dockerfile проекта
# использует RUN --mount=type=cache, а legacy-билдер на нём падает.
RUN curl -fsSL https://download.docker.com/linux/static/stable/x86_64/docker-27.5.1.tgz -o /tmp/docker.tgz \
    && tar -xzf /tmp/docker.tgz -C /tmp \
    && install -m 0755 /tmp/docker/docker /usr/local/bin/docker \
    && rm -rf /tmp/docker /tmp/docker.tgz
RUN mkdir -p /usr/local/lib/docker/cli-plugins \
    && curl -fsSL -o /usr/local/lib/docker/cli-plugins/docker-buildx \
         https://github.com/docker/buildx/releases/download/v0.19.3/buildx-v0.19.3.linux-amd64 \
    && chmod +x /usr/local/lib/docker/cli-plugins/docker-buildx \
    && docker buildx version
ENV DOCKER_BUILDKIT=1

# Temurin 17: и tarball для setup-java (jdkFile), и разложенный toolcache.
# setup-java смотрит в <RUNNER_TOOL_CACHE>/Java_<dist>_<pkg>/<version>/<arch>,
# версия — та, что указана в workflow (17.0.21+9 → каталог 17.0.21-9),
# маркер x64.complete говорит «дистрибутив готов, качать не надо».
RUN set -eux; \
    curl -fsSL -o /opt/temurin17.tar.gz \
      "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.21_9.tar.gz"; \
    base=/opt/hostedtoolcache/Java_Temurin-Hotspot_jdk/17.0.21-9; \
    mkdir -p "$base"; \
    tar -xzf /opt/temurin17.tar.gz -C "$base"; \
    mv "$base/jdk-17.0.21+9" "$base/x64"; \
    touch "$base/x64.complete"; \
    "$base/x64/bin/java" -version

# Gradle из wrapper — по пути, который проверяет gradlew
# (~/.gradle/wrapper/dists/<dist>/<hash>/). Хеш детерминирован для
# distributionUrl из gradle/wrapper/gradle-wrapper.properties; при смене
# версии Gradle в проекте хеш меняется — его надо пересчитать
# (`cd /tmp && ./gradlew --version` с этим distributionUrl и посмотреть имя
# каталога в ~/.gradle/wrapper/dists/gradle-<ver>-bin/).
RUN set -eux; \
    dir=/root/.gradle/wrapper/dists/gradle-9.8.2-bin/8wepis63pzuhrlfaiy04bagwu; \
    mkdir -p "$dir"; \
    curl -fsSL -o /tmp/gradle.zip "https://services.gradle.org/distributions/gradle-9.8.2-bin.zip"; \
    unzip -q /tmp/gradle.zip -d "$dir"; \
    touch "$dir/gradle-9.8.2-bin.zip.ok"; \
    rm -f /tmp/gradle.zip; \
    "$dir/gradle-9.8.2/bin/gradle" --version | head -5

# Playwright: браузеры и системные зависимости. Версия закреплена под
# karaoke-public (package-lock: @playwright/test 1.63.0).
ENV PLAYWRIGHT_BROWSERS_PATH=/opt/ms-playwright
RUN npx -y playwright@1.63.0 install --with-deps chromium \
    && rm -rf /var/lib/apt/lists/*

# Node для setup-node: та же логика, что с JDK — дистрибутив лежит в toolcache,
# поэтому action не ходит ни в API версий nodejs.org (rate limit), ни за tarball.
# Версия должна совпадать с базовым образом (node:22-bookworm = 22.23.4) и с
# node-version в workflow.
RUN set -eux; \
    v=22.23.4; \
    dir=/opt/hostedtoolcache/node/$v; \
    mkdir -p "$dir"; \
    curl -fsSL -o /tmp/node.tar.xz "https://nodejs.org/dist/v$v/node-v$v-linux-x64.tar.xz"; \
    tar -xJf /tmp/node.tar.xz -C "$dir"; \
    mv "$dir/node-v$v-linux-x64" "$dir/x64"; \
    touch "$dir/x64.complete"; \
    rm -f /tmp/node.tar.xz; \
    "$dir/x64/bin/node" --version

RUN java -version && node --version && python3 -c "import yaml; print('pyyaml', yaml.__version__)" \
    && jq --version && docker --version && ffmpeg -version | head -1
