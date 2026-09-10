# Feature Specification: Редактор видит все альбомы автора (а не только с готовыми песнями)

**Feature Branch**: `360-editor-sees-all-albums`

**Created**: 2026-09-10

**Status**: Draft

**Input**: User description: "Работа над задачей #76 в трекере OpenProject" (OpenProject issue #76 «Не все альбомы автора отображаются для редактора»).

## Clarifications

### Session 2026-09-10

Root cause найдена на стадии `/speckit.clarify` через grep + чтение кода (НЕ `codegraph_explore` — codegraph на этой машине возвращает символы чужого проекта, см. A-008). Никаких вопросов пользователю не понадобилось — спека self-contained.

**Найденная root cause (подтверждена в коде)**:

1. Бэкенд (`Album.loadAlbumTilesWithCounts`, `PublicApiController.authorAlbums`) уже корректно поддерживает `onlyPublished: Boolean` (KDoc явно ссылается на FR-011 спеки 356). Сигнатура:
   ```kotlin
   fun loadAlbumTilesWithCounts(authorId: Long, onlyPublished: Boolean, includeSkipped: Boolean = false, ...)
   ```
   `onlyPublished == false` для редактора снимает фильтр `ready_song_count > 0` (см. `Album.kt:533-561`).
2. `PublicApiController.onlyPublishedFor(request)` возвращает `!siteUserResolver.resolve(request)?.isEditor` (строка 283). То есть `onlyPublished = !isEditor` — корректно.
3. **`SiteUserResolver.resolve`** (строка 25) берёт токен **только** из `Authorization: Bearer <token>`:
   ```kotlin
   val header = request.getHeader("Authorization") ?: return null
   ```
   Cookie (`credentials: 'include'`) **не** учитывается.
4. **`ZakromaAlbumsView.vue:111`** — единственный во всём `karaoke-public/src/`, кто загружает `/api/public/authors/{authorId}/albums`. Используется **наивный `fetch`** без `Authorization`-заголовка:
   ```js
   const response = await fetch(`/api/public/authors/${this.authorId}/albums?scope=main`, {
     credentials: 'include',
   })
   ```
5. Все остальные fetch'и (например, `StemJobsView.vue:455`, `useZakromaStreamProgress.js:144-146`) передают `Authorization: Bearer ${localStorage.getItem('km_auth_token')}` явно или через `apiGet()` (см. `services/api.js:16-17`).

**Вывод**: bypass по `isEditor` работает на бэкенде, но фронт просто не передаёт токен. Спека #356 была реализована с расчётом, что контроллеры `karaoke-web` всегда получают `Authorization`-заголовок из браузера — а для `ZakromaAlbumsView.vue` это предположение не выполнено.

**Следствие**: фикс — **минимальная правка фронта** (1 метод, ~5 строк). Бэкенд не меняется. Никаких изменений в `Album.kt`, `PublicApiController.kt`, миграции 49, триггере `trg_tbl_songs_update_album_counts`.

### Уточнённые данные (нетронутый контракт API)

- **API** — не меняется. `GET /api/public/authors/{authorId}/albums?scope=main` уже правильно отдаёт `onlyPublished = !isEditor`.
- **`AlbumTilePublicDto`** — не меняется. Поля `totalSongCount` / `readySongCount` уже отдаются для каждого альбома, и фронт уже умеет их использовать (`ZakromaAlbumsView.vue:83-88` определяет режим подписи «N готовых» vs «N песен» по наличию `km_auth_token`).
- **Skip-фильтр** — см. `Album.kt:517-518`: «в `tbl_albums` сейчас нет колонки `skip` (Pass 357), фильтрация не выполняется». Это означает, что FR-008 спеки (skip-альбомы скрыты) и FR-016 спеки 356 не реализуются в текущей версии БД — но это **не относится к фиксу #76** (баг не про skip).
- **Минимальное изменение**: `ZakromaAlbumsView.vue` — добавить `Authorization: Bearer ${localStorage.getItem('km_auth_token')}` в `fetch` (если токен есть). Аналогично `useZakromaStreamProgress.js:144-146`.

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

### Идентификация

- **Issue ID**: `#76`
- **Title**: Не все альбомы автора отображаются для редактора
- **Created in OpenProject**: 2026-09-10

### Workflow (NON-NEGOTIABLE при наличии Issue ID)

| Шаг | Команда | Когда | Кто |
|---|---|---|---|
| 1. **Claim** | `source .env.local-tracker && bash tools/tracker.sh claim-issue 76` | ✅ ВЫПОЛНЕНО перед стартом спеки (status переведён в `In progress`). | Agent |
| 2. **Pre-flight Knowledge** | `spec.md § Knowledge References` | См. ниже — MUST #0, Constitution Principle IX. | Agent |
| 3. **Work** | код, tests, knowledge updates | `/speckit.implement` | Agent |
| 4. **Add comment с отчётом** | `bash tools/tracker.sh add-comment 76 --file specs/360-editor-sees-all-albums/report.md` | После merge. Файл `report.md` — REQUIRED. | Agent |
| 5. **Mark review** | `bash tools/tracker.sh mark-review 76` | После публикации комментария. | Agent |
| 6. **Close** | `bash tools/tracker.sh close-issue 76` | После ревью владельцем. | Agent или Owner |

### Проверки (validation)

- `tools/check-spec-issue-link.py` — секция `## OpenProject Tracking` присутствует, поля заполнены.
- Чек-лист `checklists/requirements.md` — MANDATORY (Pass 340 Knowledge Compliance).

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-10
- **Grep-запросы** (минимум 3, по релевантным ключевым словам задачи):
  1. `editor.*album|album.*editor|редактор.*альбом` →
     `specs/017-editor-status-bypass/spec.md`,
     `specs/017-editor-status-bypass/contracts/public-api-editor-visibility.md`,
     `specs/356-zakroma-albums-by-author/spec.md`,
     `docs/features/zakroma-albums-by-author.md`,
     `knowledge/domains/catalog/components/album-entity.md`,
     `knowledge/domains/identity/components/site-user-entity.md`,
     `knowledge/domains/karaoke-web/components/public-controllers-3.md`
  2. `ready_song_count|total_song_count|isEditor|albumsTilesCache` →
     `knowledge/domains/catalog/domain.md`,
     `knowledge/domains/catalog/components/album-entity.md`,
     `docs/features/zakroma-albums-by-author.md`,
     `specs/286-author-song-counts-cache/spec.md`
  3. `Album.loadAlbumTilesWithCounts|loadAlbumTiles|authorsTiles|public` →
     `knowledge/domains/karaoke-web/components/public-controllers-3.md`,
     `knowledge/domains/integration/components/dtos.md`,
     `knowledge/domains/integration/components/song-public-dto.md`

### Knowledge files consulted

- [`knowledge/domains/catalog/components/album-entity.md`](../../knowledge/domains/catalog/components/album-entity.md)
  — модель `Album`, поля `total_song_count` / `ready_song_count` (Pass 360 спекой #356 уже введены), связь с `Author` через `author_id`.
- [`knowledge/domains/identity/components/site-user-entity.md`](../../knowledge/domains/identity/components/site-user-entity.md)
  — `SiteUser.isEditor` — флаг «может работать как редактор разметки», единственный признак роли «редактор» в системе.
- [`knowledge/domains/identity/domain.md`](../../knowledge/domains/identity/domain.md)
  — `editor` как роль, `canSelfAssign=true`. Подтверждает, что для bypass фильтров на публичной части достаточно одного `SiteUser.isEditor == true`.
- [`knowledge/domains/karaoke-web/components/public-controllers-3.md`](../../knowledge/domains/karaoke-web/components/public-controllers-3.md)
  — `PublicAuthorController` / публичные endpoint'ы по автору. Контракт `/api/public/authors/{id}/albums`.
- [`specs/017-editor-status-bypass/spec.md`](../017-editor-status-bypass/spec.md)
  — **прецедент**: bypass фильтра `id_status >= 6` для песен/закромов/поиска по признаку `SiteUser.isEditor`. Этот спек описывает ТО ЖЕ поведение, что нужно для альбомов.
- [`specs/017-editor-status-bypass/contracts/public-api-editor-visibility.md`](../017-editor-status-bypass/contracts/public-api-editor-visibility.md)
  — паттерн: «Если запрос несёт `Authorization: Bearer <token>`, токен резолвится в валидного `SiteUser`, и `SiteUser.isEditor == true` — фильтр `id_status >= 3` (для песен) НЕ применяется». Этот же паттерн должен быть применён к фильтру `ready_song_count > 0` для альбомов.
- [`specs/356-zakroma-albums-by-author/spec.md`](../356-zakroma-albums-by-author/spec.md)
  — **исходная фича**, в которой US2 «Редактор видит все альбомы автора» уже описана (FR-010, FR-011, SC-002). Спека #356 уже реализована (Pass 360, см. `docs/features/zakroma-albums-by-author.md`), но по сообщению пользователя (issue #76) bypass для редактора не работает.
- [`docs/features/zakroma-albums-by-author.md`](../../docs/features/zakroma-albums-by-author.md)
  — per-feature документ (FR-009). На странице 32 явно зафиксировано:
    > «Гость: только альбомы с `ready_song_count > 0` (есть готовые песни), подпись «N готовых». Редактор: все альбомы автора, подпись «N песен» (`total_song_count`).»
  Это контракт, который должен соблюдаться — но сейчас нарушается (issue #76).
- [`knowledge/adr/0001-raw-jdbc.md`](../../knowledge/adr/0001-raw-jdbc.md)
  — сырой JDBC, без JPA/Hibernate (Constitution II).
- [`knowledge/adr/local-0003-shared-minio-image-cache.md`](../../knowledge/adr/local-0003-shared-minio-image-cache.md)
  — кеш обложек альбомов не относится к этому фиксу, но важен для контекста (обложка берётся из MinIO).
- [`specs/286-author-song-counts-cache/spec.md`](../286-author-song-counts-cache/spec.md)
  — прецедент денормализации счётчиков через DB-триггер. `Album.total_song_count` / `Album.ready_song_count` уже работают по тому же паттерну.

### Найденные паттерны для переиспользования (ЗАПРЕЩЕНО переизобретать)

1. **Bypass `id_status` для редактора в публичном API** (спек 017) — точная копия для фильтра `ready_song_count`. Если пользователь аутентифицирован и `SiteUser.isEditor == true`, фильтр `ready_song_count > 0` НЕ применяется. Используем тот же механизм: резолв токена → проверка `isEditor` → флаг `onlyPublished` (или эквивалентный) в вызов `Album.loadAlbumTilesWithCounts`.
2. **Денормализация счётчиков через DB-триггер** (спек 286 → 356) — никаких ручных `COUNT(*) GROUP BY song_album` (SC-006 спеки 356). Фильтрация по `ready_song_count > 0` vs `total_song_count > 0` уже держится в `tbl_albums`.
3. **`AlbumTilePublicDto`** (спек 356) — ответ содержит `totalSongCount` и `readySongCount`. Эти поля уже отдаются фронту для подписи плашки «N готовых» vs «N песен». Контракт ответа не меняется.

### Найденная root cause (подтверждена в коде на стадии clarify)

Per-feature документ спеки #356 фиксирует правильное поведение. Бэкенд (`Album.loadAlbumTilesWithCounts`, `PublicApiController.authorAlbums`, `onlyPublishedFor`) уже реализует bypass `isEditor` корректно — см. подробности в секции `## Clarifications` выше. **Проблема — исключительно на фронте**: `ZakromaAlbumsView.vue:111` запрашивает `/api/public/authors/{authorId}/albums` через наивный `fetch` без `Authorization`-заголовка, поэтому `SiteUserResolver.resolve` всегда возвращает `null` → `isEditor` всегда `false` → бэкенд отдаёт выборку гостя. Другие эндпоинты (`/api/public/zakroma`, `/api/public/authors-tiles`) работают через `apiGet()` (`services/api.js:16-17`), который автоматически добавляет `Authorization: Bearer <token>`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Редактор видит все альбомы автора на `/zakroma/{author_id}/albums` (Priority: P1)

Зарегистрированный пользователь с правами редактора (`SiteUser.isEditor == true`) открывает `/zakroma/{author_id}/albums`. В отличие от гостя, он видит **все** альбомы автора — включая те, у которых 0 готовых песен. Подпись плашки показывает общее количество песен (`total_song_count`), а не количество готовых.

**Why this priority**: Это и есть запрос пользователя в issue #76 («для пользователя-редактора должны отображаться все альбомы автора, а сейчас он видит только альбомы с готовыми песнями»). Без исправления — баг сохраняется.

**Independent Test**: Войти под учётной записью редактора, открыть `/zakroma/{author_id}/albums` для автора, у которого есть альбом без готовых песен. Этот альбом должен появиться в сетке с подписью «N песен», а не «N готовых». Гость, открывший ту же страницу, должен видеть альбом без готовых песен скрытым.

**Acceptance Scenarios**:

1. **Given** автор с 3 альбомами: A (5 готовых из 7 всего), B (2 готовых из 4), C (0 готовых из 1), **When** редактор открывает `/zakroma/{author_id}/albums`, **Then** отображаются 3 плашки; подписи: A — «7 песен», B — «4 песни», C — «1 песня».
2. **Given** тот же автор, **When** гость открывает `/zakroma/{author_id}/albums`, **Then** отображаются только 2 плашки (A и B); подпись «N готовых»; альбом C скрыт (как было до этого фикса).
3. **Given** альбом C (без готовых), **When** редактор кликает на его плашку, **Then** открывается `/zakroma/{author_id}?albumId={album_id_C}` со списком песен альбома (включая песни со статусом < 6) — это уже работает по спеке 017.

### User Story 2 — API `/api/public/authors/{authorId}/albums` отдаёт редактору все альбомы (Priority: P1)

Запрос `GET /api/public/authors/{authorId}/albums?scope=main` с валидным bearer-токеном пользователя-редактора возвращает массив `AlbumTilePublicDto`, в котором присутствуют **все** альбомы автора (включая без готовых песен, не-skip). Для анонимного запроса или запроса без `isEditor` — массив содержит только альбомы с `ready_song_count > 0` (как до фикса).

**Why this priority**: Это API-контракт, на котором строится US1. Если API неправильное — фронт показывает неправильное. Без API-фикса нет UI-фикса.

**Independent Test**:
- `curl -H "Authorization: Bearer <editor_token>" /api/public/authors/{authorId}/albums?scope=main` → массив содержит все альбомы (включая без готовых).
- `curl /api/public/authors/{authorId}/albums?scope=main` (без токена) → массив содержит только альбомы с готовыми.

**Acceptance Scenarios**:

1. **Given** автор с альбомами A (готовые есть), B (готовых нет, не-skip), **And** валидный bearer-токен редактора, **When** `GET /api/public/authors/{authorId}/albums?scope=main` с заголовком `Authorization`, **Then** ответ содержит оба альбома; `totalSongCount` и `readySongCount` присутствуют у каждого; у B `readySongCount == 0`, `totalSongCount > 0`.
2. **Given** тот же автор, **When** запрос без `Authorization`, **Then** ответ содержит только альбом A; альбом B отсутствует в массиве (как до фикса).
3. **Given** запрос с невалидным/просроченным токеном, **When** резолв токена возвращает `null` или пользователя с `isEditor == false`, **Then** поведение как у гостя (только `ready_song_count > 0`).

### User Story 3 — Подпись плашки отражает то, что видит редактор (Priority: P2)

Плашка альбома содержит подпись с количеством песен. Для гостя — «N готовых» (по `readySongCount`), для редактора — «N песен» (по `totalSongCount`). Это поведение уже закреплено в per-feature документе спеки #356, и если US1 + US2 работают, US3 должен работать автоматически (фронт берёт `totalSongCount` или `readySongCount` в зависимости от роли).

**Why this priority**: согласованность UI с уже работающим API. Не блокирует — если US1 и US2 сделаны правильно, US3 — это визуальная проверка.

**Independent Test**: открыть `/zakroma/{author_id}/albums` редактором, посмотреть на подпись плашки альбома с 3 готовыми из 5 — должно быть «5 песен». Гость на той же странице видит «3 готовых».

**Acceptance Scenarios**:

1. **Given** альбом X с `totalSongCount=5, readySongCount=3`, **When** редактор открывает страницу, **Then** подпись плашки X — «5 песен».
2. **Given** тот же альбом X, **When** гость открывает страницу, **Then** подпись — «3 готовых» (как до фикса).

### Edge Cases

- **Токен есть, но `SiteUser.isEditor == false`** (обычный зарегистрированный пользователь): поведение как у гостя. Только анонимный запрос тоже считается «как гость».
- **Запрос от анонимного пользователя** (без `Authorization`): без изменений — только `ready_song_count > 0`.
- **Запрос от редактора, но автор не имеет ни одного альбома**: пустой массив `[]` (как и для гостя).
- **Запрос от редактора на автора со skip = true**: skip-альбомы НЕ показываются даже редактору в публичных DTO (безопасность, см. FR-016 спеки #356).
- **Альбом с `total_song_count = 0`**: для гостя — не виден (как раньше, нет готовых); для редактора — виден с подписью «0 песен». Это семантически корректно — альбом существует в каталоге.
- **Параллельная смена статуса песни во время запроса**: счётчики обновляются атомарно через DB-триггер (спек 356), race condition не возникает (см. SC-003 спеки 356).
- **Старая версия фронта (`karaoke-public`) без поддержки нового поведения**: контракт ответа `AlbumTilePublicDto` не меняется (поля `totalSongCount`/`readySongCount` уже есть). Старый клиент, не учитывающий `isEditor`, продолжит показывать «N готовых» — это безопасно (откат к поведению гостя).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Бэкенд (`PublicApiController` или эквивалентный контроллер) MUST определять роль пользователя из `Authorization: Bearer <token>` так же, как это делается для эндпоинтов `/api/public/zakroma` и `/api/public/authors-tiles` в спеке 017. Результат — флаг `isEditor: Boolean` (true, если токен валиден и `SiteUser.isEditor == true`).
- **FR-002**: Эндпоинт `GET /api/public/authors/{authorId}/albums?scope=main` MUST учитывать `isEditor`:
  - `isEditor == true` → возвращать **все** не-skip альбомы автора с `total_song_count > 0` (включая без готовых).
  - `isEditor == false` (или токена нет) → возвращать только альбомы с `ready_song_count > 0` (как до фикса).
- **FR-003**: Поведение фильтрации по `ready_song_count` для гостя MUST остаться неизменным (спек 017 / FR-010 спеки 356). Никаких побочных эффектов для анонимных и обычных зарегистрированных пользователей.
- **FR-004**: Реализация MUST следовать паттерну спеки 017 для `/api/public/zakroma` и `/api/public/authors-tiles` — там, где уже сделан bypass по `isEditor`. ЗАПРЕЩЕНО изобретать новый механизм ролевой авторизации. Если фильтрация делается в `Album.loadAlbumTilesWithCounts`, он MUST принимать параметр `onlyPublished: Boolean` (или эквивалентный), где `onlyPublished = !isEditor`. Имя параметра и его интерпретация — на усмотрение плана, контракт FR-002 — обязателен.
- **FR-005**: Контракт ответа `AlbumTilePublicDto` НЕ изменяется — поля `id`, `name`, `year`, `pictureUrl`, `albumType`, `totalSongCount`, `readySongCount` остаются. Изменение поведения — **только в выборке**, не в форме DTO.
- **FR-006**: Подпись плашки альбома на фронте (`AlbumTiles.vue`) MUST учитывать роль пользователя (как зафиксировано в `docs/features/zakroma-albums-by-author.md` стр. 32): редактор — «N песен» (`totalSongCount`), гость — «N готовых» (`readySongCount`). Если это уже работает в текущей реализации после фикса бэкенда — фиксируется как «уже работает по спеке 356», без дополнительных правок фронта.
- **FR-007**: Сортировка альбомов остаётся `year ASC NULLS LAST, name ASC` (см. Clarification Q1 спеки 356). Сортировка не зависит от роли.
- **FR-008**: Skip-альбомы (`tbl_albums.skip = true`) MUST быть полностью скрыты и для редактора, и для гостя в публичных DTO (FR-016 спеки 356, безопасность).
- **FR-009**: Sync флаги для `tbl_albums` остаются включёнными — фикс не меняет миграцию 49 и триггер `trg_tbl_songs_update_album_counts`.

### Key Entities *(include if feature involves data)*

- **AlbumTilePublicDto** — DTO публичного API (спек 356). Поля: `id`, `name`, `year`, `pictureUrl`, `albumType`, `totalSongCount`, `readySongCount`. **Без изменений** (FR-005).
- **SiteUser.isEditor** — существующий флаг (Pass 426, `site-user-entity.md`). Источник истины для роли «редактор». Без изменений.
- **`onlyPublished` (или эквивалентный) параметр модели `Album`** — внутренний флаг (имя и реализация — на усмотрение плана). Определяет, фильтровать ли по `ready_song_count` или по `total_song_count`. Значение: `false` для редактора, `true` для остальных.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Авторизованный пользователь с `SiteUser.isEditor == true`, открывая `/zakroma/{author_id}/albums` для автора с альбомами разной степени готовности, видит **все** альбомы автора (включая без готовых песен). Анонимный пользователь на той же странице — только альбомы с готовыми песнями (как до фикса).
- **SC-002**: `curl -H "Authorization: Bearer <editor_token>" /api/public/authors/{authorId}/albums?scope=main` возвращает массив, содержащий все не-skip альбомы автора (включая без готовых). `curl /api/public/authors/{authorId}/albums?scope=main` (без токена) возвращает только альбомы с `ready_song_count > 0`.
- **SC-003**: Для пользователя без `isEditor` поведение не меняется: ровно те же альбомы, что и до фикса (regression test: `git diff` для неаутентифицированных запросов должен быть пустым по составу ответа).
- **SC-004**: Подпись плашки у редактора — «N песен» (`totalSongCount`), у гостя — «N готовых» (`readySongCount`). Соответствует `docs/features/zakroma-albums-by-author.md` стр. 32.
- **SC-005**: Никаких новых SQL-фраз `GROUP BY song_album` в postgres.log после фикса (SC-006 спеки 356 сохраняется). Фильтрация — на уровне `tbl_albums.ready_song_count > 0` vs `tbl_albums.total_song_count > 0`.
- **SC-006**: Время ответа эндпоинта для редактора — не хуже, чем для гостя (тот же индекс на `tbl_albums.author_id`, никаких дополнительных JOIN).

## Assumptions

- **A-001**: Механизм резолва токена в `PublicApiController` уже реализован для `/api/public/zakroma` и `/api/public/authors-tiles` (спек 017). Этот спек переиспользует тот же механизм, не вводя новый. Если по факту механизм не общий — это выяснится на стадии plan и будет исправлено локально.
- **A-002**: Параметр модели `Album` (например, `onlyPublished`) уже существует или легко добавляется без breaking change в API. Если в `loadAlbumTilesWithCounts` параметр отсутствует — это часть фикса (минимальная правка сигнатуры + 1-2 места вызова).
- **A-003**: Фронт (`AlbumTiles.vue`) уже умеет подставлять `totalSongCount` vs `readySongCount` в зависимости от роли (per-feature документ это описывает как реализованное поведение). Если по факту нет — будет исправлено в `plan.md`.
- **A-004**: Skip-фильтр (`tbl_albums.skip = true`) применяется **до** ролевого bypass в публичном DTO (безопасность, FR-016 спеки 356). Редактор видит skip-альбом только в админке `webvue3`, не на публичной странице.
- **A-005**: Никаких изменений в `tbl_albums`, в миграции 49 (`49_albums_song_counts.sql`), в триггере `trg_tbl_songs_update_album_counts` не требуется — счётчики уже корректные.
- **A-006**: Никаких новых записей в `Knowledge/` не требуется — спека #356 и её per-feature документ уже фиксируют правильное поведение. Этот спек — фикс реализации, а не нового дизайна.
- **A-007**: `Authorization`-заголовок для редактора передаётся стандартным механизмом аутентификации `karaoke-public` (тот же bearer-токен, что используется в спеке 017).