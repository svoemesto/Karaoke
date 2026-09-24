# Feature Specification: Корректный прогресс загрузки песен при открытии альбома автора

**Feature Branch**: `444-fix-album-progress`

**Created**: 2026-09-24

**Status**: Draft

**Input**: OpenProject issue #179 «Прогресс загрузки песен в альбоме автора». Прод `karaoke-web` + `karaoke-public`. Автор «Машина Времени» — 2485 песен, много альбомов. При заходе в «Все песни автора с группировкой по альбомам» прогресс показывает «Загружаем 0 из 2485 песен автора Машина Времени…» (корректно). При заходе в плашку любого альбома (например, 10 песен) прогресс показывает то же «0 из 2485», хотя должен показывать «0 из 10».

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

### Идентификация

- **Issue ID**: `#179`
- **Title**: Прогресс загрузки песен в альбоме автора
- **Created in OpenProject**: 2026-09-24

### Workflow (NON-NEGOTIABLE при наличии Issue ID)

| Шаг | Команда | Когда | Кто |
|---|---|---|---|
| 1. **Claim** | `source .env.local-tracker && bash tools/tracker.sh claim-issue 179` | ПЕРЕД первой строкой кода. Выполнено 2026-09-24 (статус `In progress`; PATCH вручную — `claim-issue` падает на смене assignee для Task в проекте Karaoke, прецеденты #152/#161). | Agent |
| 2. **Pre-flight Knowledge** | `spec.md § Knowledge References` | Constitution Principle IX, MUST #0. | Agent |
| 3. **Work** | код, tests, knowledge updates | `/speckit.implement` | Agent |
| 4. **Add comment с отчётом** | `bash tools/tracker.sh add-comment 179 --file specs/444-fix-album-progress/report.md` | После merge. `report.md` — REQUIRED. | Agent |
| 5. **Mark review** | `bash tools/tracker.sh mark-review 179` | После публикации комментария. | Agent |
| 6. **Close** | `bash tools/tracker.sh close-issue 179` | После ревью владельцем. | Agent или Owner |

### Проверки (validation)

- `tools/check-spec-issue-link.py` — секция `## OpenProject Tracking` + поля + команды workflow.
- `checklists/requirements.md` — Knowledge Compliance MANDATORY (Pass 340).

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-24
- **Grep-запросы** (минимум 3):
  1. `grep -rln "expectedCount|streamProgress|zakroma.stream" knowledge/` → `domains/karaoke-web/components/public-controllers-3.md`, `system/frontend/composable-zakroma-stream.md`, `adr/local-0007-zakroma-album-id-in-stream-dto.md`, `domains/integration/components/dtos.md`.
  2. `grep -rln "total_song_count|ready_song_count|songCount|loadAlbumTilesWithCounts" knowledge/` → `domains/catalog/domain.md`, `domains/catalog/components/album-entity.md`.
  3. `grep -rln "Zakroma|zakroma" knowledge/` → `domains/catalog/components/remaining-models.md`, `domains/karaoke-web/components/public-controllers-3.md`, `system/frontend/composable-zakroma-stream.md`, `adr/local-0007-...`, `adr/local-0008-effective-hidden-album-types.md`.
  4. `grep -rln "album_id|progress" knowledge/` → `domains/catalog/domain.md`, `domains/karaoke-web/components/public-controllers-3.md`.

### Knowledge files consulted

- [`knowledge/system/frontend/composable-zakroma-stream.md`](../../knowledge/system/frontend/composable-zakroma-stream.md) — контракт `expectedCount` / `meta`-сообщения NDJSON-стрима.
- [`knowledge/adr/local-0007-zakroma-album-id-in-stream-dto.md`](../../knowledge/adr/local-0007-zakroma-album-id-in-stream-dto.md) — предыдущий фикс стрима под `?albumId=`; прямое продолжение этой задачи.
- [`knowledge/domains/karaoke-web/components/public-controllers-3.md`](../../knowledge/domains/karaoke-web/components/public-controllers-3.md) — endpoint `/api/public/zakroma/stream`.
- [`knowledge/domains/catalog/components/album-entity.md`](../../knowledge/domains/catalog/components/album-entity.md) — денормализованные `total_song_count` / `ready_song_count` (триггер миграции 49).
- [`knowledge/domains/catalog/components/dictionaries.md`](../../knowledge/domains/catalog/components/dictionaries.md) — семантика `id_status >= 6` (публикация) и SKIP-тега.
- [`archive/docs/features/zakroma-stream-progress.md`](../../archive/docs/features/zakroma-stream-progress.md) — инвариант «`expectedCount` — та же формула, что на тайле».

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Открытие альбома показывает прогресс по песням альбома (Priority: P1)

Посетитель на странице `/zakroma/{authorId}/albums` кликает плашку альбома с 10 песнями. Открывается `/zakroma/{authorId}?albumId=N`, идёт NDJSON-стрим, показывается прогресс «Загружаем 0 из 10 песен автора «Машина Времени»…» с полосой, доезжающей до 100%.

**Why this priority**: Это исходный баг issue #179 — при открытии альбома знаменатель прогресса берётся от всего автора (2485), а не от альбома (10). Полоса прогресса и текст вводят посетителя в заблуждение.

**Independent Test**: Открыть `/zakroma/{authorId}?albumId={id небольшого альбома}` и убедиться, что в тексте прогресса знаменатель равен числу песен альбома (совпадает со счётчиком на плашке), а не общему числу песен автора.

**Acceptance Scenarios**:

1. **Given** у автора 2485 песен, выбран альбом с 10 песнями (`?albumId=N`), **When** страница загружается, **Then** прогресс показывает «Загружаем X из 10 песен автора {имя}…».
2. **Given** выбран альбом с 10 песнями, **When** стрим завершается, **Then** полоса прогресса доходит до 100% и `done.actualCount` совпадает с ожидаемым (нет «залипания» полосы на малом проценте).

---

### User Story 2 — Список «Все песни автора» сохраняет авторский прогресс (Priority: P2)

Посетитель открывает псевдо-плашку «Все песни автора с группировкой по альбомам» (`/zakroma/{authorId}` без `?albumId=`). Прогресс по-прежнему показывает общее число песен автора (2485).

**Why this priority**: Существующее корректное поведение (спека 181) нельзя сломать.

**Independent Test**: Открыть `/zakroma/{authorId}` без `?albumId=` — знаменатель равен общему числу песен автора.

**Acceptance Scenarios**:

1. **Given** открыт `/zakroma/{authorId}` без `?albumId=`, **When** идёт стрим, **Then** прогресс показывает «X из {общее число песен автора}».
2. **Given** открыт `?albumId=N`, **When** пользователь возвращается на `/zakroma/{authorId}` без фильтра, **Then** прогресс снова считает по автору (2485), а не по последнему альбому.

---

### User Story 3 — Deep-link и редактор (Priority: P3)

Прямой заход по URL `/zakroma/{authorId}?albumId=N` (без предварительной загрузки тайлов) и заход редактора (`Authorization: Bearer`) дают тот же корректный знаменатель по альбому; для редактора учитываются не-опубликованные песни (счётчик — `total_song_count`, а не `ready_song_count`).

**Why this priority**: Deep-link — известный хрупкий путь (Pass 359), а видимость редактора отличается (FR из спеки 356/360).

**Independent Test**: Открыть URL напрямую в новой вкладке; повторить с валидным токеном редактора.

**Acceptance Scenarios**:

1. **Given** прямой заход на `/zakroma/{authorId}?albumId=N` в новой вкладке, **When** страница загружается, **Then** знаменатель прогресса — по альбому.
2. **Given** редактор открывает `?albumId=N`, где часть песен не опубликована, **When** идёт стрим, **Then** знаменатель равен общему числу песен альбома (совпадает с плашкой «N песен» для редактора).

---

### Edge Cases

- **Альбом не найден** (удалён между загрузкой плашки и кликом): стрим вернёт 0 песен → знаменатель 0 (согласовано с фактическим содержимым), а не число песен автора.
- **SKIP-песни в альбоме**: счётчик-денормализация не учитывает SKIP-фильтр стрима; при расхождении >5% фронт корректирует знаменатель по `done.actualCount` (существующий drift-detection, FR спеки 251).
- **Legacy-альбом без FK (`album_id IS NULL`)**: `?albumId=` для таких альбомов не применяется (фильтр по id не сработает) — это вне scope, поведение не меняется.
- **Гонка: быстрый переход между альбомами**: ключ dedup стора уже включает `author:albumId` (Pass 359) — знаменатель не «протекает» от предыдущего альбома.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: При наличии `albumId` в запросе `/api/public/zakroma/stream` сервер MUST вычислять `meta.expectedCount` как число песен **этого альбома**, а не автора.
- **FR-002**: Для гостя (`onlyPublished=true`) счётчик альбома MUST равняться `ready_song_count`; для редактора (`onlyPublished=false`) — `total_song_count` (совпадение с подписью плашки альбома).
- **FR-003**: Сервер MUST NOT доверять присланному фронтом `expectedCount`, когда задан `albumId` (album-scoped значение авторитетно на сервере).
- **FR-004**: При отсутствии `albumId` поведение MUST оставаться прежним: `expectedCount` берётся из присланного фронтом значения (`tile.songCount`), иначе — fallback на `Song.loadAuthorSongCounts()` по автору (спека 181).
- **FR-005**: Если альбом с заданным `albumId` не найден, сервер MUST вернуть `expectedCount = 0` (а не число песен автора).
- **FR-006**: Фронт MUST NOT отправлять авторский `expectedCount`, когда активен `?albumId=` (передаёт `undefined`), чтобы источником истины был сервер.
- **FR-007**: Текст прогресса и разметка прогрессометра MUST NOT меняться (issue #179 требует лишь верный знаменатель).
- **FR-008**: `done.actualCount` MUST оставаться фактическим числом отправленных песен (существующий контракт, sanity-check/drift).

### Key Entities

- **`ZakromaStreamMessageDto.meta`**: первое NDJSON-сообщение `{type:"meta", author, expectedCount}` — знаменатель прогрессометра.
- **`Album` (`tbl_albums`)**: денормализованные `total_song_count` / `ready_song_count`, поддерживаются триггером `trg_tbl_songs_update_album_counts` (миграция `49_albums_song_counts.sql`). Источник истины счётчика альбома.
- **`selectedAlbumId`**: фронтовый computed из query `?albumId=` (`ZakromaView.vue`), уходит в `loadZakromaStream({ albumId })`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: При открытии альбома прогресс показывает «X из N», где N — число песен альбома, в 100% альбомов с `albumId > 0` (совпадает со счётчиком на плашке).
- **SC-002**: Полоса прогресса доезжает до 100% к завершению стрима альбома (drift скорректирован `done.actualCount`, если он есть).
- **SC-003**: Для списка «Все песни автора» (без `albumId`) знаменатель остаётся равен общему числу песен автора — нет регресса спеки 181.
- **SC-004**: `:karaoke-web:test` — новый unit-тест `ZakromaStreamProgressTest` PASS; `:karaoke-web:compileKotlin` / `:karaoke-app:compileKotlin` — OK.
- **SC-005**: `cd karaoke-public && npm run lint:check` + `format:check` — PASS.

## Assumptions

- Счётчики `tbl_albums.total_song_count` / `ready_song_count` актуальны (миграция 49 применена на проде вместе со спекой 356) — это тот же источник, что у плашек альбомов.
- `albumId` на публичном стриме — всегда `tbl_albums.id > 0` (реальный альбом); legacy-виртуальные альбомы `?albumId=` не используют.
- Разница из-за SKIP-фильтра стрима против денормализации допустима и корректируется существующим drift-detection (`done.actualCount`).
- Рестарт/сборка контейнеров — на стороне владельца (AGENTS.md, machine-specific: `nsa-i9`).
- Scope — правки `karaoke-web` (сервер, авторитетный знаменатель) + `karaoke-public` (не шлёт авторский знаменатель для альбома) + тест + knowledge/per-feature docs.

## Out of Scope

- Изменение текста/разметки прогрессометра («песен автора {имя}» остаётся).
- Пересчёт `total_song_count`/`ready_song_count` или SQL-миграции (счётчики уже есть).
- SKIP-фильтрация в счётчике-денормализации (отдельная задача; сейчас — drift-detection).
- Изменение поведения legacy-виртуальных альбомов без FK.
