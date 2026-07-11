# AGENTS.md — инструкции для агентов

При старте каждой сессии обязательно читать также `DEVELOPMENT.md` — он содержит дополнительные указания и архитектурные детали. Оба файла являются руководством к действию.

## О проекте

Karaoke (svoemesto) — self-pipeline для автоматического создания караоке-видео. Kotlin/Spring Boot бэкенд + Vue 3 фронтенд.

## Модули

- `karaoke-app` — ядро: Spring Boot (Kotlin), все доменные модели, MLT-генератор, очередь задач, LLM-поиск текстов
- `karaoke-web` — публичный API и веб-страницы (depends on karaoke-app)
- `karaoke-db` — legacy, не используется в продакшене
- `karaoke-vue` — legacy, заброшен (только `src/assets`, нет реального кода). Не участвует в сборке.
- `karaoke-public` — публичный SPA (Vue 3 + Vite + Bootstrap 5)
- `webvue3` — admin SPA (Vue 3 + Vite + Bootstrap-vue-next + Vuex)
- `deploy/` — docker-compose, `do.sh`, серверные конфиги

## Сборка и запуск

```bash
# Backend (Kotlin, JDK 17)
./gradlew karaoke-app:bootJar
./gradlew karaoke-web:bootJar
./gradlew clean karaoke-app:bootJar karaoke-web:bootJar --parallel

# Frontend (webvue3)
cd webvue3 && npm install && npm run dev    # dev
cd webvue3 && npm run build                # production

# Frontend (karaoke-public)
cd karaoke-public && npm run dev
cd deploy && bash do.sh build_start_public  # Docker build + restart

# Deploy (всегда запускать из deploy/)
cd deploy
bash do.sh build              # gradle + docker images
bash do.sh build_app          # только karaoke-app
bash do.sh build_start_web    # rebuild karaoke-web + restart
bash do.sh start / stop       # контейнеры
bash do.sh push / pull        # Docker Hub
```

Root `pom.xml` — leftover от Maven, не использовать. Проект собирается через Gradle.

## Тесты

Тестов в CI нет. Существующие тесты (`karaoke-app/src/test`) — интеграционные, требуют сеть/браузер/credentials, большинство `@Disabled`. Не полагайся на них как на проверку.

## Архитектура (ключевые особенности)

- **БД без JPA/Hibernate.** Сырой JDBC + рефлексия для diff. `KaraokeDbTable` — интерфейс всех сущностей. Изменения публикуются через SSE.
- **Две БД (LOCAL ↔ SERVER).** Синхронизация через хэши записей. О(n) через `associateBy`, не O(n²).
- **MLT-генерация.** `mlt/mko/*` — объекты для каждого визуального слоя караоке. ~150 настраиваемых параметров в `KaraokeProperties.kt`.
- **Async-очередь.** `KaraokeProcess*` — задачи (ffmpeg, Demucs, Sheetsage) запускаются как subprocess, прогресс парсится из stdout.
- **Storage.** MinIO (MinIO-compatible). Картинки: альбом 400×400, автор 1000×400. Превью генерируются on-demand.

**Синхронизация LOCAL↔SERVER — критичные паттерны производительности:**
- Сравнение хэшей: `associateBy { it.id }` + Map lookup — O(n), **не** вложенные `.any`/`.none` — O(n²) (при 18858 записях = 3+ минуты).
- Загрузка записей для diff: пакетно через `WHERE id IN (...)`, **не** по одной в цикле (N+1 запросов).

## Важные паттерны

- **`karaoke-public` dual design:** два дизайна (classic/modern), выбор в `localStorage`. Компоненты: `views/classic/` и `views/modern/`. CSS-переменные `--km-*`.
- **Таблицы `karaoke-public`:** `table-layout: fixed` требует явной `width: Npx`. Колонки платформ: 16×22px = 352px. `display: flex` на `<td>` ломает высоту строки — использовать `text-align: center; vertical-align: middle`.
- **Bootstrap 5:** `<select>` → класс `form-select` (не `form-control`).
- **Картинки в БД:** поле `picture_full` всегда `""`. Картинки только в MinIO. `PicturesDTO` содержит `previewUrl`/`fullUrl`. При загрузке всегда использовать `ignoreUseInList = false`.
- **Тег SKIP:** если в `tags` есть `SKIP`, показывается заглушка "удалено по требованию правообладателя".
- **Табулатура (ASCII-only):** `-` вместо `⎼` (U+23BC), `||` вместо `‖` (U+2016). Unicode ломает выравнивание через font fallback.
- **HealthReport:** видеофайлы проверяются **только при `idStatus >= 6`**. Не трогать логику для видео при статусе < 6.

## Dockerfile-ловушки

- **`nginx:alpine` нельзя** — docker-compose использует `/bin/bash -c`, в alpine нет bash → контейнер падёт. Использовать `nginx:stable` (Debian).
- **`node:latest` нельзя** — недетерминированный. Использовать `node:22-alpine` (LTS).
- **karaoke-app: `eclipse-temurin:22-jre-jammy`** (JRE, не JDK — Spring Boot fat jar не требует компилятора).
- **Docker CE внутри karaoke-app** намеренно — приложение запускает `docker run`/`docker compose` через `ProcessBuilder`.
- **IP-сервисы:** `ip-api.com`, `ipapi.co`, `ipapi.is` из Docker возвращают 403/502. Использовать `api.country.is` для проверки VPN.

## Ограничения агента

### Запрещено

- Пересобирать/перезапускать контейнер `karaoke-app` локально — делает только пользователь
- Деплоить на сервер (`deploy_web.sh`, `deploy_public.sh`, rsync на 79.174.95.69) — делает только пользователь
- Редактировать файлы на сервере напрямую
- Перезаписывать `do.env` на сервере (содержит секреты)
- Коммитить `deploy/ollama_data/`, `dist/`, `node_modules/`, `deploy/.env`, `deploy/do.env`

### Разрешено

- Редактировать код во всех модулях
- Собирать gradle-джары (`./gradlew karaoke-app:bootJar`, `./gradlew karaoke-web:bootJar`)
- Запускать `npm run dev` / `npm run build` для `webvue3` и `karaoke-public`
- Пересобирать/перезапускать контейнеры `karaoke-web`, `webvue3`, `karaoke-public` локально через `deploy/do.sh`

## Git

Не коммитить: `deploy/ollama_data/`, `dist/`, `node_modules/`, `deploy/.env`, `deploy/do.env`. Всегда проверять `git status` перед `git add`.

## Деплой

- `deploy/deploy_web.sh` — обновление karaoke-web на проде
- `deploy/deploy_public.sh` — обновление karaoke-public на проде
- Сервер: `79.174.95.69`, Docker-сеть `deploy_karaokenet`
- Не редактировать файлы на сервере напрямую — синхронизировать через rsync
- `do.env` на сервере содержит секреты — не перезаписывать через rsync

**Проверка после деплоя `deploy_web.sh`:**
1. В логах **нет** `EOF` / `400 Bad request` при push — иначе пуш не удался.
2. На сервере: `Status: Downloaded newer image` (не `Image is up to date`).
3. Если push через VPN падает — попросить пользователя запустить вручную без VPN.

**Nginx 80to8897:** это отдельный файл (не симлинк). При rsync обновляется в `/root/Karaoke/deploy/`, но nginx читает из `/etc/nginx/sites-enabled/`. Нужно копировать вручную:
```bash
ssh root@79.174.95.69 "cp /root/Karaoke/deploy/80to8897 /etc/nginx/sites-enabled/80to8897 && nginx -t && systemctl reload nginx"
```
