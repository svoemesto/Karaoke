# Implementation Plan: Редактор видит все альбомы автора

**Branch**: `360-editor-sees-all-albums` | **Date**: 2026-09-10 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/360-editor-sees-all-albums/spec.md`

## Summary

Минимальная правка фронта (`karaoke-public/src/views/ZakromaAlbumsView.vue`) — добавить передачу `Authorization: Bearer <token>` из `localStorage.getItem('km_auth_token')` в `fetch('/api/public/authors/{authorId}/albums')`. Бэкенд уже корректно реализует bypass `isEditor` через `Album.loadAlbumTilesWithCounts(authorId, onlyPublished=false, ...)` + `PublicApiController.onlyPublishedFor(request) = !isEditor` — никаких изменений на бэкенде не требуется. Контракт API (`AlbumTilePublicDto`) не меняется. Миграция 49 и триггер `trg_tbl_songs_update_album_counts` уже работают по спеке 356.

**Технический подход** (см. подробности в [research.md](./research.md)):
- Модель `Album.loadAlbumTilesWithCounts(authorId, onlyPublished, includeSkipped, database)` уже поддерживает `onlyPublished: Boolean` (`Album.kt:533-561`).
- `PublicApiController.onlyPublishedFor(request)` (`PublicApiController.kt:283`) возвращает `!siteUserResolver.resolve(request)?.isEditor`.
- `SiteUserResolver.resolve` (`SiteUserResolver.kt:25`) берёт токен **только** из `Authorization: Bearer <token>` header.
- `ZakromaAlbumsView.vue:111` — единственное место в `karaoke-public/src/`, где fetch идёт **без** этого заголовка.
- Паттерн исправления: добавить 1 helper или inline-header к fetch (по образцу `useZakromaStreamProgress.js:144-146` или `services/api.js:16-17`).

## Technical Context

**Language/Version**: Vue 3 (Composition API + Options API mix в существующем коде), JavaScript (ES2020). Бэкенд не меняется.

**Primary Dependencies**:
- `localStorage.getItem('km_auth_token')` — клиентское хранилище токена (см. `karaoke-public/src/composables/useAuth.js:7-27`).
- `fetch` API (нативный браузерный, без axios) — текущий подход в `ZakromaAlbumsView.vue:111`.
- Существующий хелпер `apiGet` в `karaoke-public/src/services/api.js` уже инкапсулирует добавление Authorization-заголовка — вариант переиспользовать вместо `fetch`.

**Storage**: N/A (фикс не трогает БД, миграции, кеши).

**Testing**:
- Ручная проверка через UI: войти под редактором → открыть `/zakroma/{id}/albums` для автора с альбомом без готовых песен → альбом должен появиться с подписью «N песен».
- `curl -H "Authorization: Bearer <editor_token>" /api/public/authors/{authorId}/albums?scope=main` — массив содержит все альбомы.
- Regression: `curl /api/public/authors/{authorId}/albums?scope=main` (без токена) — массив без альбомов с 0 готовых (как до фикса).
- Линтеры: `cd karaoke-public && npm run lint:check && npx prettier --check "src/views/ZakromaAlbumsView.vue"` (после правки).

**Target Platform**: Браузер (Vue 3 SPA, `karaoke-public`).

**Project Type**: Frontend-only фикс (1 файл, 1 метод). Никаких бэкенд-правок.

**Performance Goals**: N/A (фикс не меняет нагрузку). Никаких дополнительных SQL-запросов, никаких новых JOIN. Используется тот же эндпоинт, та же логика на бэке.

**Constraints**:
- Никаких изменений в контракте API (FR-005 спеки).
- Никаких изменений в DTO (`AlbumTilePublicDto` остаётся как есть).
- Никаких изменений в миграции 49 / триггере `trg_tbl_songs_update_album_counts`.
- Никаких изменений в `Album.kt`, `PublicApiController.kt`.
- Минимальное изменение: 1 файл, ~5 строк (по аналогии с `useZakromaStreamProgress.js:144-146`).
- Backward compatible: для неавторизованного пользователя поведение не меняется (токена нет — header не добавляется).

**Scale/Scope**: 1 файл фронта (`ZakromaAlbumsView.vue`), 1 endpoint, 1 issue. Объём минимальный.

## Constitution Check

*Gate: must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Compliance | Notes |
|---|---|---|
| **I. Self-contained автопайплайн** | ✅ N/A | Фикс не затрагивает пайплайн медиа-обработки |
| **II. Сырой JDBC + recordhash** | ✅ N/A | Бэкенд не меняется |
| **III. Двух-БД синхронизация** | ✅ N/A | Миграция 49 не меняется |
| **IV. Async-очередь** | ✅ N/A | Не затрагивается |
| **V. Двух-фронтенд (admin vs public)** | ✅ Compliant | Меняется только `karaoke-public`, не `webvue3` |
| **VI. Code Standards** | ✅ Compliant | KDoc/JSDoc на обновлённый код, eslint пройдёт, prettier пройдёт (пост-правка) |
| **VII. Cross-Machine Setup** | ✅ N/A | Локальные правила |
| **VIII. Секреты и git-гигиена** | ✅ Compliant | Токен `km_auth_token` НЕ коммитится, берётся из `localStorage`. Никаких секретов в коде. |
| **IX. Knowledge-first** | ✅ Compliant | Pre-flight выполнен в Stage 1 (3+ grep-запроса, 8+ файлов прочитано), подтверждено в Stage 2 |

**Constitution Check post-design**: ✅ Без изменений (фикс настолько мал, что design не меняет compliance).

## Project Structure

### Documentation (this feature)

```text
specs/360-editor-sees-all-albums/
├── plan.md              # This file (Stage 3)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output (минимальный, см. ниже)
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (контракт API)
│   └── albums-tiles-authorization.md
├── spec.md              # Stage 1
├── checklists/
│   └── requirements.md  # Stage 1
├── tasks.md             # Stage 4 (created by /speckit.tasks, not /speckit.plan)
└── report.md            # Stage 6 (after implement, before mark-review)
```

### Source Code (repository root)

**Структура изменения**:

```text
karaoke-public/src/views/ZakromaAlbumsView.vue   # 1 файл, ~5 строк (метод loadAlbums)
```

**Никакие другие файлы не меняются**:
- `karaoke-app/.../model/Album.kt` — НЕ меняется (уже поддерживает `onlyPublished: Boolean`).
- `karaoke-web/.../controllers/PublicApiController.kt` — НЕ меняется (уже вызывает `onlyPublishedFor`).
- `karaoke-web/.../services/SiteUserResolver.kt` — НЕ меняется (контракт не трогаем).
- `karaoke-web/.../dto/AlbumTilePublicDto.kt` — НЕ меняется (поля `totalSongCount`/`readySongCount` уже есть).
- `deploy/karaoke-db/49_albums_song_counts.sql` — НЕ меняется (миграция уже применена).

**Structure Decision**: Option 2 (Web application — backend + frontend). Backend без изменений. Frontend — точечная правка 1 файла.

## Implementation Strategy

### Подход

1. **Перед правкой**: прочитать `karaoke-public/src/services/api.js` (helper `apiGet`) и `karaoke-public/src/composables/useZakromaStreamProgress.js:130-150` (паттерн для fetch с заголовком).
2. **Вариант A (предпочтительный)**: заменить наивный `fetch` на `apiGet('/api/public/authors/...', {...})`. Унифицирует подход, убирает дублирование, уменьшает риск regression (например, если когда-нибудь добавят CSRF-токен или cookie-based session).
3. **Вариант B (минимальный)**: добавить inline-заголовок:
   ```js
   const headers = {}
   const token = localStorage.getItem('km_auth_token')
   if (token) headers.Authorization = `Bearer ${token}`
   const response = await fetch(url, { credentials: 'include', headers })
   ```
4. **Решение** — на стадии `/speckit.implement` после прочтения `services/api.js`. Если `apiGet` подходит по типу возврата (Array<Dto>), использовать вариант A. Иначе — B.

### Что НЕ делаем (явные no-go)

- НЕ вводим новый helper / composable для авторизации (уже есть `useAuth.js`, `api.js`).
- НЕ меняем `services/api.js`.
- НЕ рефакторим `ZakromaAlbumsView.vue` сверх необходимого (минимальная правка).
- НЕ трогаем skip-фильтр (FR-008 спеки) — он не относится к issue #76 и не реализован в БД (`Album.kt:517-518` явно фиксирует «skip-колонки пока нет»).
- НЕ добавляем кеширование на фронте — кеш `albumsTilesCache` на бэке уже есть (TTL ≤60с).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| (нет нарушений) | — | — |

Нет нарушений Constitution. Все принципы соблюдены или N/A.