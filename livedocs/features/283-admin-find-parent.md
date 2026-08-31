---
status: Active
slug: 283-admin-find-parent
related:
  - ./279-fix-parent-search-folder-add.md
  - ./281-find-lyrics-overwrites-key-bpm.md
  - ../domain/processing.md
  - ../architecture/L3-components.md
  - ../../specs/283-admin-find-parent/spec.md
  - ../../specs/283-admin-find-parent/research.md
  - ../../specs/283-admin-find-parent/contracts/http-endpoint.md
---

# 283 — Админ-кнопки «Поиск родителя» и «Найти аудио-родителя» (LiveDoc)

> Drill-down — [specs/283-admin-find-parent/spec.md](../../specs/283-admin-find-parent/spec.md).

## Что делает

Две новые кнопки на главной странице админки `webvue3` (`HomeView.vue`), в блоке с полем «Автор», **над** кнопкой «Автопривязать оригинал по аудио (статус 1 → 2)»:

1. **«Поиск родителя»** (US1 P1) — фоновый поиск **текстового** родителя (`root_id`) для песен автора с `root_id = 0`. По умолчанию ищет только среди того же автора, опционально (`crossAuthor = true`) — среди всех авторов.
2. **«Найти аудио-родителя»** (US3 P1) — фоновый поиск **аудио**-родителя (`audio_parent_id`) среди песен в семье для песен автора с `root_id <> 0` И `audio_parent_id = 0`. Только в семье (`findFamilySongIds` транзитивно по `root_id`), только пишет `audio_parent_id` (без копирования маркеров/текста/статуса).

Обе кнопки `disabled` при пустом авторе, обе открывают стандартную модалку `CustomConfirm`, обе запускают фоновую задачу с SSE-уведомлением по завершении и `ALREADY_RUNNING`-защитой через `@Volatile`-флаги в JVM.

## Зачем

- **US1**: куратор может прогнать точечный поиск родителя для одного автора, не запуская глобальную `Custom Function` (которая обходит весь каталог).
- **US3**: куратор может прогнать аудио-сверку в уже сформированной семье (после US1 или вручную), не перезаписывая ручной текст/маркеры (как делает `autoAssignOriginalAll`).

## User Stories (краткий список)

- **US1** (P1): поиск текстового родителя для песен автора с `root_id = 0`.
- **US2** (P2): повторный запуск безопасен и идемпотентен (`ALREADY_RUNNING` + SQL-фильтр).
- **US3** (P1): поиск аудио-родителя среди претендентов в семье (`root_id <> 0` И `audio_parent_id = 0`).

## Functional Requirements (указатель)

- **FR-001..FR-013** (US1, US2): см. спек 283 — кнопка, модалка, эндпоинт `POST /api/utils/findparentforauthor`, расширение `findParentCandidateId(crossAuthor)`.
- **FR-014..FR-022** (US3): кнопка, модалка, эндпоинт `POST /api/utils/findaudioparentforauthor`, расширение `findAudioParentByWaveform(searchOtherAuthors)`, защита от гонок отдельным флагом `isFindAudioParentInProgress`.

## Ключевые изменения в общих функциях

- **`Utils.findParentCandidateId`**: убран фильтр `withText` (раньше приоритизировал кандидатов с `source_text`). Теперь ищет среди **всех** песен автора с тем же нормализованным названием — включая «сирот» без текста (по замечанию пользователя 2026-08-31). **Затрагивает и `customFunction`** (общая функция).
- **`Utils.findAudioParentByWaveform`**: добавлен параметр `searchOtherAuthors: Boolean = true` (default сохраняет обратную совместимость с `customFunction` и существующим endpoint `/song/findaudioparent`). При `searchOtherAuthors = false` исключает `searchSongsByNormalizedName` из набора кандидатов.

## Acceptance Criteria (US1, US3)

- [ ] **AC1** (US1, Сценарий 1 quickstart): при `crossAuthor=false` родитель подбирается только среди того же автора; SSE «Обработано N, родитель назначен M (найдено, но пропущено из-за текста: K)».
- [ ] **AC2** (US1, Сценарий 2): при `crossAuthor=true` допускаются кандидаты других авторов.
- [ ] **AC3** (US1, Сценарий 9): «сирота» без `source_text` того же автора получает родителя (раньше отсеивалась фильтром `withText`).
- [ ] **AC4** (US2, Сценарий 3): повторный запуск во время работы → `ALREADY_RUNNING` → warning-тост.
- [ ] **AC5** (US3, Сценарий A1): аудио-поиск идёт только в семье, без `searchSongsByNormalizedName`.
- [ ] **AC6** (US3, Сценарий A2): `source_text`/`source_markers`/`id_status`/`root_id` НЕ изменяются (только `audio_parent_id`/`audio_similarity_percent`/`audio_delta_ms`/`audio_compare_history`).
- [ ] **AC7** (US3, Сценарий A3): песни с `audio_parent_id <> 0` повторно не обрабатываются (SQL-фильтр `audio_parent_id = 0`).
- [ ] **AC8** (US3, Сценарий A5): аудио- и текстовый поиск могут идти параллельно (отдельные `@Volatile`-флаги).

## Связанные LiveDocs

- [279-fix-parent-search-folder-add.md](./279-fix-parent-search-folder-add.md) — багфикс регрессии поиска родителя (Pass 278); наша фича переиспользует исправленную `applyDuplicateOriginal`/`applyAudioParentMarkers`.
- [281-find-lyrics-overwrites-key-bpm.md](./281-find-lyrics-overwrites-key-bpm.md) — основа `reload-from-db-before-save`; в новых функциях применяется явно (`findParentForAuthor` использует reload перед `saveToDb`).
- Domain: [processing.md](../domain/processing.md) — async-паттерн (фоновый поток + SSE-тост).
- Architecture: [L3-components.md](../architecture/L3-components.md) — UI-компонент `CustomConfirm` с булевым полем через `fldIsBoolean`.

## Код

- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt`:
  - `findParentCandidateId(song, db, crossAuthor = true)` — расширен параметром; убран фильтр `withText`/`pool`; удалено поле `hasText` из `data class ParentCandidate`.
  - `findParentForAuthor(author, crossAuthor, storageService, storageApiClient)` — фоновая функция по образцу `customFunction` (только фаза 1); `@Volatile isFindParentInProgress`.
  - `findAudioParentByWaveform(song, db, storageService, storageApiClient, searchOtherAuthors = true)` — расширен параметром.
  - `findAudioParentForAuthor(author, storageService, storageApiClient)` — фоновая функция для аудио-кнопки; `@Volatile isFindAudioParentInProgress` (отдельный).
- Backend controller: `karaoke-app/.../controllers/ApiController.kt` — `doFindParentForAuthor` (`/utils/findparentforauthor`), `doFindAudioParentForAuthor` (`/utils/findaudioparentforauthor`).
- Frontend store: `webvue3/src/components/Songs/store.js` — `findParentForAuthorPromise`, `findAudioParentForAuthorPromise`.
- Frontend UI: `webvue3/src/views/HomeView.vue` — кнопки «Поиск родителя» и «Найти аудио-родителя» + методы модалки.

## История

- Создан: 2026-08-31 (Pass 283).
- Замечание пользователя 2026-08-31 в US1: фильтр «только с текстом» в `findParentCandidateId` признан ошибочным — родитель должен искаться среди всех песен автора.
- Замечание пользователя 2026-08-31 в US3: песни с уже найденным `audio_parent_id` исключаются из повторной обработки (SQL-фильтр `audio_parent_id = 0`).
