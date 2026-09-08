# Phase 1 Data Model: Настраиваемый таймаут между поисковыми запросами (iter #316)

**Branch**: `316-search-timeout-configurable`
**Date**: 2026-09-08 (rev 3: UI fix + авто-подбор)
**Spec**: [spec.md](./spec.md)

## Сущности

### 1. MassSearchTimeoutSetting (backend, KaraokeProperties, single-user)

**Хранение**: backend, через существующий класс `KaraokeProperties` (`karaoke-app/.../KaraokeProperties.kt:25`, base64-encoded файл `/sm-karaoke/system/Karaoke.properties` — `PATH_TO_KARAOKE_PROPERTIES_FILE` стр. 18). Никаких новых БД-таблиц или миграций.

**Атрибуты**:
- ключ: `lyricsSearchTimeoutSeconds` (Int, секунды);
- default: `10` (если ключ отсутствует или повреждён);
- область: **single-user, глобально** (KaraokeProperties — глобальные настройки, не per-user). Single-user обосновано: в Karaoke один admin-пользователь по факту.
- редактирование: через существующий Properties UI/API (ApiController.kt:5874 `getProperties()` + `webvue3/src/views/PropertiesView.vue`) или через endpoint `POST /api/lyrics-search-timeout` (FR-008). Прямые правки файла в обход UI — только с согласия владельца.

**Lifecycle**:
- Read в момент каждого запуска поиска (FR-008, НЕ кэшируется) — оба пути A и B.
- Write через API endpoint после подтверждения диалога (FR-004) или через Properties UI.
- Read через UI для default в поле таймаута (FR-002, FR-005).

**Validation rules** (FR-006):
- Должно быть положительным целым числом ≥ 1.
- Если value некорректно (повреждено, устарело) — fallback на 10 (Edge case spec).
- Backend дополнительно: `coerceAtLeast(1)` на query-param input (defensive).

### 2. MassSearchSummary (runtime, не сохраняется)

**Тип**: локальная структура в `ApiController.kt` для FR-009/FR-010 — НЕ сохраняется в БД, существует только в рамках одного цикла поиска.

**Атрибуты**:
- `path`: `"A"` | `"B"` (какой путь — searchsongtextall или createfromfolder);
- `count`: Int (общее число песен в цикле);
- `successfulCount`: Int (число успешных `getLyricsSearch` без exception);
- `minIntervalMs`: `Long?` (минимальное время между двумя подряд успешными запросами; `null` если `successfulCount < 2`);
- `totalDurationMs`: Long (от старта цикла до завершения).

**Lifecycle**:
- Создаётся в начале цикла (`val cycleStart = System.currentTimeMillis()`, counters = 0).
- Обновляется в цикле при каждом успешном `getLyricsSearch`.
- Используется после цикла: backend-лог + SSE notification (FR-010).
- Уничтожается после отправки нотификации.

## Отношения

Нет — single-value global setting + runtime структура.

## Race Protection (Lesson #5 iter #1, Конституция)

Изменения только в admin-SPA (`webvue3/`) + backend `karaoke-app` (Karaoke.kt, ApiController.kt, SseNotificationService.kt). Без изменений в backend БД-структуры. Race conditions между вкладками (Edge case spec): «последнее записанное выигрывает» (acceptable per spec, single-user).

---

## Изменяемые файлы (на фазе implement)

### Backend

- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Karaoke.kt` (MODIFY: добавить `lyricsSearchTimeoutSeconds` рядом с `checkSearchAsync`).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt` (MODIFY: регистрация `lyricsSearchTimeoutSeconds` в `listKaraokeProperties` — B1 fix).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` (MODIFY, 4 точки):
  - `getSearchSongTextAll` (стр. 4771): timeout param + `forEachIndexed` + `Thread.sleep` после `getLyricsSearch` + сбор `minIntervalMs` (FR-009).
  - `doCreateFromFolder` (стр. 5302): timeout param + `forEachIndexed` + `Thread.sleep` ПЕРЕД `submit` + сбор `minIntervalMs` (FR-009).
  - После обоих циклов: backend-лог `[lyrics-search-summary]` + SSE notification `massSearchSummary` (FR-010).
  - Новый endpoint `GET/POST /api/lyrics-search-timeout`.
- `karaoke-app/.../model/SseNotificationType.kt` (УЖЕ содержит `MASS_SEARCH_SUMMARY` enum — broadcast по умолчанию, НЕ требует modify SseNotificationService).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt` (MODIFY: тот же endpoint — deprecated, симметрично).

### Frontend

- `webvue3/src/components/Songs/store.js` (MODIFY: actions `getLyricsSearchTimeout`/`setLyricsSearchTimeout` через API; добавить `timeout` в `searchTextForAll` params).
- `webvue3/src/components/Songs/SongsTable.vue` (MODIFY: добавить поле `timeout` в `customConfirmParams.fields` в методе `searchTextForAll`, рядом с `engine`).
- `webvue3/src/views/HomeView.vue` (MODIFY: добавить поле `timeout` в `customConfirmParams.fields` в методе `addFilesFromFolder`, рядом с информацией о формате).
- `webvue3/src/App.vue` (MODIFY, опционально: добавить `case SseNotificationType.MASS_SEARCH_SUMMARY` в switch SSE — может быть просто лог).

### Документация

- `livedocs/features/316-search-timeout-configurable.md` (NEW: livedoc по конвенции).
- `livedocs/INDEX.md` (MODIFY: добавить строку-ссылку).

— Илья (boss)
