# ===== Этап: Подготовка Java-приложения =====
FROM openjdk:17-ea-jdk-slim AS java-app-builder

ARG VERSION
ARG APP_VERSION

COPY www.sm-karaoke.ru.crt /tmp/www.sm-karaoke.ru.crt

RUN cp /usr/local/openjdk-17/lib/security/cacerts /tmp/cacerts_working && \
    /usr/local/openjdk-17/bin/keytool \
        -import \
        -alias www.sm-karaoke.ru \
        -keystore /tmp/cacerts_working \
        -file /tmp/www.sm-karaoke.ru.crt \
        -storepass changeit \
        -noprompt && \
    mv /tmp/cacerts_working /usr/local/openjdk-17/lib/security/cacerts && \
    rm /tmp/www.sm-karaoke.ru.crt

COPY karaoke-app-${APP_VERSION}.jar /tmp/app.jar

# ===== Финальный Этап: Создание итогового образа =====
FROM ubuntu:22.04

# Блок 1: системные пакеты + зависимости Playwright + unzip
# BuildKit apt cache избавляет от повторного скачивания пакетов при пересборке
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update && apt-get install -y --no-install-recommends \
    dumb-init \
    git \
    xvfb \
    ffmpeg \
    cpulimit \
    python3 \
    python3-dev \
    python3-pip \
    libgl1-mesa-glx \
    libglib2.0-0 \
    libxrender1 \
    libxrandr2 \
    libasound2 \
    libxss1 \
    libgtk-3-0 \
    libgomp1 \
    wget \
    fontconfig \
    openjdk-17-jre-headless \
    smplayer \
    nodejs \
    npm \
    ca-certificates \
    curl \
    unzip \
    libgstreamer1.0-0 \
    libgstreamer-plugins-base1.0-0 \
    libgtk-4-1 \
    libgraphene-1.0-0 \
    libatomic1 \
    libxslt1.1 \
    libwoff1 \
    libevent-2.1-7 \
    libwebpdemux2 \
    libavif13 \
    libharfbuzz-icu0 \
    libenchant-2-2 \
    libsecret-1-0 \
    libhyphen0 \
    libmanette-0.2-0 \
    libgles2 \
    libgstreamer-gl1.0-0 \
    libgstreamer-plugins-bad1.0-0

# Блок 2: Docker CE (нужен для запуска docker run / docker compose внутри контейнера)
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    install -m 0755 -d /etc/apt/keyrings && \
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc && \
    chmod a+r /etc/apt/keyrings/docker.asc && \
    echo \
        "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
        $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}") stable" | \
        tee /etc/apt/sources.list.d/docker.list > /dev/null && \
    apt-get update && \
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin docker-compose

# Шрифты
COPY Roboto-Black.ttf /usr/share/fonts/truetype/roboto/
COPY FiraSansExtraCondensed-Medium.ttf /usr/share/fonts/truetype/FiraSansExtraCondensed/
RUN fc-cache -fv

# --- Копирование Java-приложения ---
COPY --from=java-app-builder /tmp/app.jar /app.jar
COPY --from=java-app-builder /usr/local/openjdk-17/lib/security/cacerts /usr/lib/jvm/java-17-openjdk-amd64/lib/security/cacerts

# Путь к браузерам Playwright в образе (используется и при установке, и в runtime)
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

# Извлечение зависимостей из bootJar (unzip уже установлен в блоке 1)
RUN mkdir -p /app_dependencies && \
    unzip -q /app.jar 'BOOT-INF/lib/*' -d /tmp/unpack_dir && \
    mv /tmp/unpack_dir/BOOT-INF/lib/* /app_dependencies/ && \
    rm -rf /tmp/unpack_dir

# Установка браузеров Playwright
# --mount=type=cache сохраняет скачанные браузеры между сборками на билд-машине.
# Playwright при повторной сборке видит уже скачанную версию и пропускает загрузку (~5 сек вместо 5 мин).
RUN --mount=type=cache,target=/root/.cache/ms-playwright \
    PLAYWRIGHT_BROWSERS_PATH=/root/.cache/ms-playwright \
    java -cp "/app.jar:/app_dependencies/*" com.microsoft.playwright.CLI install && \
    mkdir -p /ms-playwright && \
    cp -r /root/.cache/ms-playwright/. /ms-playwright/

# Установка переменных окружения для Java-приложения
ENV JAVA_HOME=/opt/java/openjdk
ENV APP_VERSION=${VERSION:-unknown}
ENV TZ="Europe/Moscow"
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Duser.language=ru -Duser.country=RU -Djava.security.egd=file:/dev/./urandom"

CMD ["echo 'Please specify a command to run (e.g., java -jar /app/app.jar, /lib/demucs/demucs2, melt ...)'"]
