# Quickstart: Колонка audio_parent_id в таблице песен админки

**Feature**: specs/023-songs-audio-root-column
**Date**: 2026-07-30

## Prerequisites

- PostgreSQL локальная БД запущена.
- `karaoke-app` собран и запущен (`./gradlew karaoke-app:bootJar` + `java -jar ...` или Docker).
- `webvue3` dev-сервер запущен (`cd webvue3 && npm run dev`).
- В БД есть песни с разными `audio_parent_id` (можно проверить через `SELECT id, audio_parent_id FROM tbl_settings LIMIT 10;`).

## Backend Validation

1. Собрать `karaoke-app`:

   ```bash
   ./gradlew karaoke-app:bootJar
   ```

2. Проверить, что `SongDTOdigest` содержит `audioParentId`:

   ```bash
   curl -s http://localhost:8080/api/songsdigests -X POST -d '' | python3 -m json.tool | head -40
   ```

   В ответе каждый элемент должен содержать поле `audioParentId`.

3. Проверить новый endpoint:

   ```bash
   curl -s http://localhost:8080/api/song/123/shortinfo | python3 -m json.tool
   ```

   Должен вернуться JSON с полями `id`, `author`, `year`, `album`, `songName`.

4. Проверить фильтр по `audio_parent_id`:

   ```bash
   curl -s http://localhost:8080/api/songsdigests -X POST -d 'filterAudioParentId=123' | python3 -m json.tool | head -40
   ```

   В ответе должны остаться только песни с `audioParentId = 123`.

## Frontend Validation

1. Открыть админку: http://localhost:5173 (или порт `webvue3` dev-сервера).
2. Перейти в «Песни».
3. Убедиться, что:
   - Колонка «A-root» видна сразу после колонки «root».
   - В ячейках отображается значение `audio_parent_id` (или `—`, если 0).
   - При наведении на ячейку «root» или «A-root» появляется тултип с автором, годом, альбомом и названием песни.
   - При наведении на пустую или несуществующую ссылку тултип не появляется / показывает «Не найдено».
4. Открыть фильтр, ввести `audio_parent_id`, нажать «Применить фильтр».
   - Таблица должна отфильтроваться.
   - Очистка поля и повторное применение возвращает полный список.

## Lint / Compile Checks

```bash
./gradlew ktlintCheck
./gradlew :karaoke-app:compileKotlin
./gradlew :karaoke-web:compileKotlin
cd webvue3 && npm run lint:check
```

Все проверки должны пройти без новых нарушений.

## Per-feature Documentation

Обновить `docs/features/songs-table.md` (создать, если отсутствует) в соответствии с FR-009.
