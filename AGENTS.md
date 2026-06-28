# AGENTS.md — инструкции для агентов

## О проекте

Karaoke (svoemesto) — self-pipeline для автоматического создания караоке-видео. Kotlin/Spring Boot бэкенд + Vue 3 фронтенд.

## Модули

- `karaoke-app` — ядро: Spring Boot (Kotlin), все доменные модели, MLT-генератор, очередь задач, LLM-поиск текстов
- `karaoke-web` — публичный API и веб-страницы (依赖 karaoke-app)
- `karaoke-db` — legacy, не используется в продакшене
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

## Тесты

Тестов в CI нет. Существующие тесты (`karaoke-app/src/test`) — интеграционные, требуют сеть/браузер/credentials,大部分 `@Disabled`. Не полагайся на них как на проверку.

## Архитектура (ключевые особенности)

- **БД без JPA/Hibernate.** Сырой JDBC + рефлексия для diff. `KaraokeDbTable` — интерфейс всех сущностей. Изменения публикуются через SSE.
- **Две БД (LOCAL ↔ SERVER).** Синхронизация через хэши записей. О(n) через `associateBy`, не O(n²).
- **MLT-генерация.** `mlt/mko/*` — объекты для каждого визуального слоя караоке. ~150 настраиваемых параметров в `KaraokeProperties.kt`.
- **Async-очередь.** `KaraokeProcess*` — задачи (ffmpeg, Demucs, Sheetsage) запускаются как subprocess, прогресс парсится из stdout.
- **Storage.** MinIO (MinIO-compatible). Картинки: альбом 400×400, автор 1000×400. Превью генерируются on-demand.

## Важные паттерны

- **`karaoke-public` dual design:** два дизайна (classic/modern), выбор в `localStorage`. Компоненты: `views/classic/` и `views/modern/`. CSS-переменные `--km-*`.
- **Таблицы `karaoke-public`:** `table-layout: fixed` требует явной `width: Npx`. Колонки платформ: 16×22px = 352px.
- **Bootstrap 5:** `<select>` → класс `form-select` (не `form-control`).
- **Картинки в БД:** поле `picture_full` всегда `""`. Картинки только в MinIO. `PicturesDTO` содержит `previewUrl`/`fullUrl`.
- **Тег SKIP:** если в `tags` есть `SKIP`, показывается заглушка "удалено по требованию правообладателя".

## Git

Не коммитить: `deploy/ollama_data/`, `dist/`, `node_modules/`, `deploy/.env`, `deploy/do.env`. Всегда проверять `git status` перед `git add`.

## Деплой

- `deploy/deploy_web.sh` — обновление karaoke-web на проде
- `deploy/deploy_public.sh` — обновление karaoke-public на проде
- Сервер: `79.174.95.69`, Docker-сеть `deploy_karaokenet`
- Не редактировать файлы на сервере напрямую — синхронизировать через rsync
- `do.env` на сервере содержит секреты — не перезаписывать через rsync
