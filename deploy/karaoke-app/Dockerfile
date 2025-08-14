# ===== Этап: Подготовка Java-приложения =====
FROM openjdk:17-jdk-slim AS java-app-builder

# Аргументы сборки
ARG VERSION
ARG APP_VERSION

# --- Копирование SSL сертификата, находится в папке files  ---
COPY www.sm-karaoke.ru.crt /tmp/www.sm-karaoke.ru.crt

# --- Импорт сертификата в Java keystore, используя ЖЕСТКО ЗАДАННЫЕ абсолютные пути ---
# Пути для openjdk:17-jdk-slim
RUN cp /usr/local/openjdk-17/lib/security/cacerts /tmp/cacerts_working
RUN /usr/local/openjdk-17/bin/keytool \
    -import \
    -alias www.sm-karaoke.ru \
    -keystore /tmp/cacerts_working \
    -file /tmp/www.sm-karaoke.ru.crt \
    -storepass changeit \
    -noprompt
RUN mv /tmp/cacerts_working /usr/local/openjdk-17/lib/security/cacerts

# --- Очистка временных файлов ---
RUN rm /tmp/www.sm-karaoke.ru.crt

# --- Копирование JAR файла ---
COPY karaoke-app-${APP_VERSION}.jar /tmp/app.jar

# ===== Финальный Этап: Создание итогового образа =====
FROM ubuntu:22.04

# Установка зависимостей, необходимых для работы всех частей
# Включаем dumb-init, xvfb, ffmpeg, python3 (для запуска скриптов, если нужно),
# и библиотеки, которые могут понадобиться Java.

RUN apt-get update && apt-get install -y --no-install-recommends \
    dumb-init \
    git \
    xvfb \
    ffmpeg \
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
    && rm -rf /var/lib/apt/lists/*

RUN apt-get update -y
RUN apt-get install -y ca-certificates curl
RUN install -m 0755 -d /etc/apt/keyrings
RUN curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
RUN chmod a+r /etc/apt/keyrings/docker.asc
RUN echo \
    "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
    $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}") stable" | \
    tee /etc/apt/sources.list.d/docker.list > /dev/null
RUN apt-get update -y
RUN apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
RUN apt install -y docker-compose

# --- Копирование Java-приложения ---
# Копирование JAR-файла
COPY --from=java-app-builder /tmp/app.jar /app.jar
# Копирование ОБНОВЛЕННОГО сертификата cacerts из java-app-builder
# Используем правильный путь из этапа java-app-builder
COPY --from=java-app-builder /usr/local/openjdk-17/lib/security/cacerts /usr/lib/jvm/java-17-openjdk-amd64/lib/security/cacerts

# Установка переменных окружения для Java-приложения
# JAVA_HOME для финального образа (ubuntu + openjdk-17-jre-headless)
ENV JAVA_HOME=/opt/java/openjdk
ENV APP_VERSION=${VERSION:-unknown}
ENV TZ="Europe/Moscow"

# --- Установка параметров JVM по умолчанию ---
# Используем JAVA_TOOL_OPTIONS, чтобы опции применялись автоматически
# LANG и LC_ALL устанавливаются в docker-compose.yml или docker run, так как они влияют на ОС
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Duser.language=ru -Duser.country=RU -Djava.security.egd=file:/dev/./urandom"

# --- Определение точки входа ---
# Финальный образ не будет иметь единой команды ENTRYPOINT по умолчанию,
# так как он содержит несколько утилит. Предполагается, что команда будет
# передаваться при запуске контейнера.
# Однако, можно установить точку входа по умолчанию, например, для запуска Java-приложения:
#ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app.jar"]

# Или использовать оболочку для более гибкого запуска:
#ENTRYPOINT ["/bin/bash", "-l", "-c"]

# CMD может быть пустым или содержать команду по умолчанию
CMD ["echo 'Please specify a command to run (e.g., java -jar /app/app.jar, /lib/demucs/demucs2, melt ...)'"]
