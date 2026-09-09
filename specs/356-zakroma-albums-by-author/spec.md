# Feature Specification: Закрома — Альбомы авторов

**Feature Branch**: `356-zakroma-albums-by-author`

**Created**: 2026-09-09

**Status**: Draft

**Input**: User description: "Работа над задачей #70 в трекере OpenProject" (OpenProject issue #70 «Закрома и альбомы авторов»).

## Clarifications

### Session 2026-09-09

- **Q1 (порядок сортировки альбомов)**: Какой порядок сортировки альбомов в сетке `/zakroma/{author_id}/albums`? → A: **Год выпуска ASC + название ASC** (хронологический порядок — от старых к новым; внутри одного года — алфавитный тай-брейкер). Альбомы без `year` (NULL) уходят в самый конец списка (NULLS LAST в Postgres).
- **Q2 (визуальный стиль псевдо-плашки)**: Какой визуальный стиль для первого элемента в сетке альбомов? → A: **Псевдо-плашка в том же стиле** — обложка-иконка + подпись «Все песни автора с группировкой по альбомам», реализуется через `<slot name="leading" />` в `AuthorTiles.vue` (паттерн спеки 307 для спец-плашки «Отдельные песни разных авторов» на `/zakroma`).

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

### Идентификация

- **Issue ID**: `#70`
- **Title**: Закрома и альбомы авторов
- **Created in OpenProject**: 2026-09-09

### Workflow (NON-NEGOTIABLE при наличии Issue ID)

| Шаг | Команда | Когда | Кто |
|---|---|---|---|
| 1. **Claim** | `source .env.local-tracker && bash tools/tracker.sh claim-issue 70` | ✅ ВЫПОЛНЕНО перед стартом спеки (2026-09-09, status переведён в `In progress`). | Agent |
| 2. **Pre-flight Knowledge** | `spec.md § Knowledge References` | См. ниже — MUST #0, Constitution Principle IX. | Agent |
| 3. **Work** | код, tests, knowledge updates | `/speckit.implement` | Agent |
| 4. **Add comment с отчётом** | `bash tools/tracker.sh add-comment 70 --file specs/356-zakroma-albums-by-author/report.md` | После merge. Файл `report.md` — REQUIRED. | Agent |
| 5. **Mark review** | `bash tools/tracker.sh mark-review 70` | После публикации комментария. | Agent |
| 6. **Close** | `bash tools/tracker.sh close-issue 70` | После ревью владельцем. | Agent или Owner |

### Проверки (validation)

- `tools/check-spec-issue-link.py` — секция `## OpenProject Tracking` присутствует, поля заполнены.
- Чек-лист `checklists/requirements.md` — MANDATORY (Pass 340 Knowledge Compliance).

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-09
- **Grep-запросы** (минимум 3, по релевантным ключевым словам задачи):
  1. `zakroma` → `docs/features/zakroma-tiles-sort-order.md`, `specs/307-special-authors-zakroma-order/spec.md`, `specs/286-author-song-counts-cache/spec.md`, `karaoke-public/src/views/ZakromaView.vue`
  2. `album author` → `knowledge/domains/catalog/domain.md`, `knowledge/domains/catalog/components/dictionaries.md`, `karaoke-app/.../model/Album.kt`
  3. `total_song_count ready_song_count trigger` → `knowledge/domains/processing/components/two-db-sync.md`, `deploy/karaoke-db/46_author_sort_order.sql`, `specs/286-author-song-counts-cache/contracts/...`
  4. `frontend slider persistence` → `knowledge/adr/local-0004-lazy-eager-load-webvue3-pagination.md`, `docs/features/pagination-filter-admin-tables.md`

### Knowledge files consulted

- [`knowledge/README.md`](../../knowledge/README.md) — SSoT-структура, статус наполнения.
- [`knowledge/domains/README.md`](../../knowledge/domains/README.md) — реестр доменов, компонентов.
- [`knowledge/domains/publishing/domain.md`](../../knowledge/domains/publishing/domain.md) — публичный сайт, `tbl_settings.publish_date`, формула `isContentReady` (idStatus=6 + publishDate в прошлом).
- [`knowledge/domains/catalog/domain.md`](../../knowledge/domains/catalog/domain.md) — AR `Album` (Identity=id), `Song`, `Author`. Поле `total_song_count`/`ready_song_count` для альбомов ещё НЕ существует (для авторов есть с спеки 286).
- [`knowledge/domains/catalog/components/dictionaries.md`](../../knowledge/domains/catalog/components/dictionaries.md) — `IdStatus` 1..6 (6 = APPROVED, готова), `SongType`, тег `SKIP`. Готовность песни определяется по `idStatus >= 6` (см. также spec 286).
- [`knowledge/domains/persistence/domain.md`](../../knowledge/domains/persistence/domain.md) — reflection-based mapping, `recordhash`, sync-готовность через SQL-триггер.
- [`knowledge/domains/processing/components/two-db-sync.md`](../../knowledge/domains/processing/components/two-db-sync.md) — SyncRegistry, `Album` уже зарегистрирован, добавление колонок потребует пересоздания `recordhash`-триггера (см. Constitution III).
- [`knowledge/adr/local-0003-shared-minio-image-cache.md`](../../knowledge/adr/local-0003-shared-minio-image-cache.md) — обложки альбомов 200×200, MinIO-кеш, TTL 7 дней, dedup через ETag.
- [`knowledge/adr/local-0004-lazy-eager-load-webvue3-pagination.md`](../../knowledge/adr/local-0004-lazy-eager-load-webvue3-pagination.md) — Vuex-паттерн для админки. Для публичного сайта (`karaoke-public`) — localStorage / Vue 3 composables, паттерн другой (но идейно близок).
- [`docs/features/zakroma-tiles-sort-order.md`](../../docs/features/zakroma-tiles-sort-order.md) — прецедент: спец-плашка, sort_order, `AuthorTiles.vue` со слотом `<slot name="leading" />`. Эта фича — её «next step».
- [`specs/286-author-song-counts-cache/spec.md`](../286-author-song-counts-cache/spec.md) — **точный прецедент** для денормализации счётчиков + DB-триггер + sync через recordhash. Этот спек прямо упоминается в issue #70 («как в своё время эти поля были добавлены в таблицу авторов»).
- [`specs/307-special-authors-zakroma-order/spec.md`](../307-special-authors-zakroma-order/spec.md) — `/zakroma` route, `AuthorTiles.vue`, `ZakromaView.vue`, `AuthorTilePublicDto`.
- [`knowledge/adr/0001-raw-jdbc.md`](../../knowledge/adr/0001-raw-jdbc.md) — сырой JDBC, без JPA/Hibernate.

### Найденные паттерны для переиспользования (ЗАПРЕЩЕНО переизобретать)

1. **Денормализация `total_song_count`/`ready_song_count` в `tbl_authors` через DB-триггер** (spec 286) — копируем паттерн 1:1 для `tbl_albums`. Источник истины — БД-триггер, backfill в миграции, sync через recordhash.
2. **`AuthorTiles.vue` со слотом `<slot name="leading" />`** (spec 307) — для плашек альбомов используем тот же компонент (`AlbumTiles.vue` или переиспользуем `AuthorTiles.vue` с другим DTO). Псевдо-плашка «Все песни автора с группировкой по альбомам» встаёт в `leading`-слот.
3. **Vuex-паттерн + SSE для админки** (ADR local-0004) — НЕ переносим на публичный сайт; для `karaoke-public` используем локальный state + localStorage (паттерн публичного сайта, см. `karaoke-public/src/composables/`).
4. **MinIO-кеш обложек 200×200** (ADR local-0003) — обложка альбома = то же хранилище, тот же MinIO bucket `karaoke-cache`, TTL 7 дней. Размер тайла можно сделать настраиваемым (200..400px), но дефолт 200×200 как в спеке 286 для авторов.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Посетитель переходит от автора к его альбомам (Priority: P1)

Гость сайта, не авторизованный и без premium-подписки, заходит на страницу `/zakroma/{author_id}` (страница песен автора с группировкой по альбомам). Над списком песен появляется новая секция «Альбомы автора» — набор плашек (визуальный стиль как у плашек авторов на `/zakroma`). Каждая плашка содержит обложку альбома 200×200 (дефолт), год выпуска, название и количество **готовых** песен в альбоме. Альбомы, в которых нет ни одной готовой песни (idStatus >= 6 и publishDate в прошлом), для гостя НЕ отображаются. Клик по плашке открывает страницу песен автора, отфильтрованную по этому альбому (`/zakroma/{author_id}?album={album_id}`).

**Why this priority**: Основная ценность фичи — дать посетителю путь «автор → альбом → песни» вместо «автор → плоский список песен». Это и есть мотивация, описанная пользователем в issue #70.

**Independent Test**: Открыть `/zakroma/{author_id}` — в верхней части страницы появилась новая секция «Альбомы автора» с плашками. Альбомы без готовых песен НЕ отображаются. Клик по плашке открывает `/zakroma/{author_id}?album={album_id_A}` со списком песен, где присутствуют только песни выбранного альбома. Левая «хлебная крошка» в шапке страницы песен ведёт на `/zakroma/{author_id}/albums` (а не на `/zakroma`, как сейчас).

**Acceptance Scenarios**:
1. **Given** автор с 3 альбомами: A (5 готовых песен), B (2 готовых), C (0 готовых, только idStatus=1..5), **When** гость открывает `/zakroma/{author_id}`, **Then** в секции «Альбомы автора» отображаются 2 плашки (A и B); подпись у A — «5 готовых», у B — «2 готовых».
2. **Given** гость находится на `/zakroma/{author_id}` с видимыми плашками альбомов, **When** он кликает на плашку альбома A, **Then** открывается `/zakroma/{author_id}?album={album_id_A}` со списком песен альбома A; URL содержит query-параметр `album`.
3. **Given** гость находится на странице песен альбома `/zakroma/{author_id}?album={album_id}`, **When** он смотрит на шапку страницы, **Then** левая хлебная крошка ведёт на `/zakroma/{author_id}/albums` (список альбомов автора), а не на `/zakroma` (как сейчас).
4. **Given** автор с 3 альбомами разных годов: «Zebra» (2020), «Alpha» (1995), «Beta» (2018) — все имеют `ready_song_count > 0`, **When** гость открывает `/zakroma/{author_id}/albums`, **Then** плашки в сетке идут в порядке: Alpha (1995) → Beta (2018) → Zebra (2020) — хронологический по году ASC, тай-брейкер по названию ASC (см. Clarification Q1).
5. **Given** автор с альбомами, у одного из которых `year = NULL`, **When** гость открывает `/zakroma/{author_id}/albums`, **Then** альбом без года отображается **последним** в сетке (`NULLS LAST`).

---

### User Story 2 — Редактор видит все альбомы автора, включая пустые (Priority: P1)

Зарегистрированный пользователь с правами редактора заходит на `/zakroma/{author_id}`. В отличие от гостя, он видит **все** альбомы автора, включая те, у которых 0 готовых песен. Подпись плашки показывает **общее** количество песен (а не только готовых). Для редактора плашки альбомов выглядят так же, как для гостя, но фильтр «только с готовыми» снят.

**Why this priority**: Редактор должен видеть альбомы в работе (черновики, рендер в процессе) — иначе он не сможет планировать работу над ними. Это отражает существующую логику показа плашек авторов (см. спеку 017 / 286): для редактора фильтр `idStatus >= 6` снимается.

**Independent Test**: Зарегистрированный редактор открывает `/zakroma/{author_id}` — все альбомы видны (включая без готовых песен). Подпись плашки показывает общее количество (`total_song_count`). Подсчёт совпадает с `SELECT COUNT(*) FROM tbl_songs WHERE song_album = album.id` (без фильтра по статусу).

**Acceptance Scenarios**:
1. **Given** автор с 3 альбомами: A (5 готовых из 7 всего), B (2 готовых из 4), C (0 готовых из 1), **When** редактор открывает `/zakroma/{author_id}`, **Then** отображаются 3 плашки; подписи: A — «7 песен», B — «4 песни», C — «1 песня».
2. **Given** редактор находится на `/zakroma/{author_id}`, **When** он кликает на плашку альбома C (0 готовых), **Then** открывается `/zakroma/{author_id}?album={album_id_C}` со списком из 1 песни в статусе, отличном от 6 (например, idStatus=3).
3. **Given** гость и редактор оба открывают `/zakroma/{author_id}`, **When** сравнивается состав плашек, **Then** у редактора больше плашек (он видит альбомы без готовых песен).

---

### User Story 3 — Счётчики песен в альбоме обновляются автоматически (Priority: P1)

Администратор импортирует новую песню (Import Folder, спека 282) или переводит песню в статус «готово» (idStatus=6). Счётчики `total_song_count` и `ready_song_count` в таблице `tbl_albums` обновляются атомарно через SQL-триггер. При синхронизации LOCAL → SERVER значения счётчиков попадают на прод через `recordhash` (как у авторов). На странице `/zakroma/{author_id}` плашка альбома сразу отражает новые числа (после инвалидации L2-кеша ≤60с).

**Why this priority**: Без автоматического поддержания счётчиков фича бесполезна — числа в `tbl_albums` застынут. Это та же гарантия «счётчик = правда», что и в спеке 286 для авторов.

**Independent Test**:
- Вставить в `tbl_songs` строку с `song_album = album.id, id_status = 6` → `tbl_albums.ready_song_count` инкрементируется.
- Перевести песню из id_status=6 в id_status=5 → `ready_song_count` декрементируется, `total_song_count` не меняется.
- Удалить песню → оба счётчика уменьшаются на 1.
- Запустить sync LOCAL → SERVER → `recordhash` для `tbl_albums` совпадает на LOCAL и SERVER (нет «diff-шум»).

**Acceptance Scenarios**:
1. **Given** альбом X с `ready_song_count = 3, total_song_count = 5`, **When** в `tbl_songs` вставляется строка для X с id_status=6, **Then** `ready_song_count = 4, total_song_count = 6` атомарно.
2. **Given** песня альбома Y переходит из id_status=6 в id_status=5, **When** транзакция коммитится, **Then** `ready_song_count` декрементируется на 1, `total_song_count` неизменен.
3. **Given** LOCAL: `ready_song_count = 42` для альбома X, SERVER: `ready_song_count = 41`, **When** запускается sync, **Then** на SERVER значение = 42, `recordhash` совпадает.
4. **Given** skip-альбом (отредактирован и помечен как скрытый), **When** обновляются его счётчики, **Then** значения обновляются (зеркалим логику для авторов из спеки 286 — БД всегда консистентна, UI скрывает).

---

### User Story 4 — Слайдер масштаба плашек (Priority: P2)

Гость / редактор открывает `/zakroma` (плашки авторов) или `/zakroma/{author_id}/albums` (плашки альбомов). В верхней части страницы появляется слайдер размера (аналогично слайдеру размера шрифта в онлайн-редакторе). Значение слайдера сохраняется в `localStorage` для конкретного пользователя (per-browser). При следующем заходе размер плашек восстанавливается.

**Why this priority**: UX-улучшение, не блокирующее основной сценарий. Дефолтный размер (200×200) работает из коробки; слайдер — это «настройка под себя».

**Independent Test**: Открыть `/zakroma` — слайдер виден. Сдвинуть ползунок вправо (например, на 300px) — плашки становятся крупнее. Перезагрузить страницу (F5) — размер сохранился. Открыть в другом браузере — слайдер в дефолтном положении (200×200).

**Acceptance Scenarios**:
1. **Given** пользователь первый раз на `/zakroma`, **When** страница загружается, **Then** слайдер в дефолтной позиции (200×200), плашки отображаются 200×200.
2. **Given** пользователь сдвинул слайдер на 300px, **When** страница перезагружается, **Then** слайдер в позиции 300px, плашки 300×300 (или пропорционально).
3. **Given** пользователь на `/zakroma/{author_id}/albums`, **When** он двигает слайдер, **Then** размер меняется и для плашек альбомов (общий слайдер для обоих разделов, если в localStorage одно и то же значение).

---

### User Story 5 — Переключатель «Плашки / Таблица» (Priority: P2)

Гость / редактор на `/zakroma` или `/zakroma/{author_id}/albums` видит переключатель вида отображения: «Плашки» (дефолт) или «Таблица». При выборе «Таблица» плашки скрываются, появляется таблица со столбцами: название, год, кол-во песен. Выбор сохраняется в `localStorage` и применяется на обоих разделах (закрома авторов + альбомы авторов).

**Why this priority**: UX-альтернатива для пользователей, предпочитающих компактный табличный вид. Не блокирует основной сценарий.

**Independent Test**: Открыть `/zakroma`, переключиться на «Таблица» — плашки скрылись, появилась таблица. Перезагрузить — таблица. Перейти на `/zakroma/{author_id}/albums` — там тоже таблица (тот же режим).

**Acceptance Scenarios**:
1. **Given** пользователь на `/zakroma`, **When** он переключает на «Таблица», **Then** плашки исчезают, появляется таблица с колонками: автор, кол-во готовых песен, sort_order.
2. **Given** режим «Таблица» сохранён в localStorage, **When** пользователь заходит на `/zakroma/{author_id}/albums`, **Then** альбомы тоже отображаются таблицей (тот же режим).
3. **Given** режим «Таблица» активен, **When** пользователь переключает на «Плашки», **Then** вид возвращается к плашкам, выбор сохраняется.

---

### Edge Cases

- **Альбом без картинки**: если в `tbl_albums` пусто `picture_full` — плашка отображается с placeholder-обложкой (иконка диска или серый фон); это уже реализовано в `AuthorTiles.vue` для авторов без фото, паттерн переиспользуется.
- **Автор без альбомов**: на `/zakroma/{author_id}/albums` отображается пустое состояние «У этого автора пока нет альбомов». Плашки авторов (на `/zakroma`) не скрывают таких авторов — это орфанный кейс (UI показывает плашку, но переход ведёт на пустую страницу альбомов).
- **Гость на `/zakroma/{author_id}/albums` напрямую** (без перехода с `/zakroma`): страница доступна; хлебные крошки ведут на `/zakroma` → Главная. Этот кейс не отличается от обычного захода.
- **Альбомы с одинаковым `id` но разным `author`**: теоретически `id` глобально уникален в `tbl_albums`; если бы один и тот же `id` был у двух авторов, фильтр `WHERE album.author = ?` исключит ложные совпадения. Дополнительной защиты не требуется.
- **Песня с `song_album = 0` или `NULL`**: песня не принадлежит ни одному альбому; триггер должен корректно игнорировать такие строки (no-op, как в спеке 286 для «висящих» песен).
- **Длинные названия альбомов**: длинное название обрезается в подписи плашки многоточием (`...`); полное название — в tooltip.
- **Год альбома отсутствует**: в подписи плашки поле «год» скрывается; остаётся только название + кол-во песен.
- **Размер тайла меняет сетку**: при увеличении размера плашек число колонок сетки пересчитывается (CSS-grid `auto-fit minmax(...)`); при уменьшении — колонок становится больше. Поведение как у авторов.
- **Одновременное обновление `song_author` и `song_album`**: спека 286 покрывает инвалидацию author-кэша через consumeDirty при sync; для альбомов тот же механизм работает (sync push → consumeDirty → сброс кеша).
- **`tbl_albums.skip = true`**: альбом скрыт с публичного сайта; счётчики в БД всё равно поддерживаются (UI-фильтр, как в спеке 286).

## Requirements *(mandatory)*

### Functional Requirements

#### Каталог и страницы

- **FR-001**: Публичный сайт MUST отображать новую секцию «Альбомы автора» на странице `/zakroma/{author_id}`, визуально совпадающую по стилю с плашками авторов на `/zakroma`. (См. `AuthorTiles.vue` со слотом `<slot name="leading" />` из спеки 307.)
- **FR-002**: Публичный сайт MUST отображать страницу `/zakroma/{author_id}/albums` со списком плашек альбомов конкретного автора. Сортировка плашек — **год выпуска ASC + название ASC** (см. Clarification Q1: хронологический порядок от старых к новым, в одной группе года — алфавитный тай-брейкер; альбомы с `year = NULL` идут последними — `NULLS LAST`). Над сеткой плашек — первая «псевдо-плашка» в том же стиле с иконкой-обложкой и подписью «Все песни автора с группировкой по альбомам» (ведёт на `/zakroma/{author_id}` без фильтра); реализуется через `<slot name="leading" />` в `AuthorTiles.vue` (см. Clarification Q2).
- **FR-003**: Публичный сайт MUST открывать страницу песен автора с фильтром по альбому при клике на плашку альбома: URL = `/zakroma/{author_id}?album={album_id}` (query-параметр, см. **A-004**). Это та же существующая страница песен, но с предустановленным фильтром по альбому. Альтернативный формат `/zakroma/{author_id}/{album_id}` НЕ используется.
- **FR-004**: На странице песен с фильтром по альбому левая хлебная крошка MUST вести на `/zakroma/{author_id}/albums` (список альбомов), а не на `/zakroma` (как сейчас).
- **FR-005**: На странице `/zakroma/{author_id}/albums` левая хлебная крошка MUST вести на `/zakroma` (список авторов).

#### Денормализация и БД

- **FR-006**: В таблицу `tbl_albums` MUST быть добавлены колонки `total_song_count INTEGER NOT NULL DEFAULT 0` и `ready_song_count INTEGER NOT NULL DEFAULT 0`. Колонки входят в `recordhash` (обновлённый триггер `update_tbl_albums_recordhash`). Миграция идемпотентна (`ADD COLUMN IF NOT EXISTS`).
- **FR-007**: SQL-триггер MUST поддерживать `total_song_count` и `ready_song_count` в `tbl_albums` атомарно на INSERT/UPDATE/DELETE в `tbl_songs` (по образцу спеки 286 для `tbl_authors`). Песни с `song_album = 0`/`NULL` и песни без соответствующего альбома — no-op (без `RAISE EXCEPTION`).
- **FR-008**: Миграция MUST выполнить backfill существующих альбомов за один проход: `UPDATE tbl_albums SET total_song_count = (SELECT COUNT(*) FROM tbl_songs WHERE song_album = tbl_albums.id), ready_song_count = (SELECT COUNT(*) FROM tbl_songs WHERE song_album = tbl_albums.id AND id_status >= 6)`.
- **FR-009**: Sync флаги для `tbl_albums` MUST оставаться включёнными для обеих колонок (sync_albums_push_update_allowed = true, sync_albums_pull_update_allowed = true). Синхронизация работает через `recordhash` автоматически.

#### Видимость и фильтрация

- **FR-010**: Для **гостя** (не авторизован, не premium-подписчик) MUST отображаться только альбомы с `ready_song_count > 0`. Подпись плашки — «N готовых» (`ready_song_count`). Скрытые (`skip = true`) альбомы НЕ показываются.
- **FR-011**: Для **редактора** MUST отображаться все альбомы автора (включая без готовых песен), независимо от `skip = true` (skip-фильтр всё равно применяется — это политика безопасности, см. спеку 017). Подпись плашки — «N песен» (`total_song_count`). Логика зеркалит существующее поведение для плашек авторов в спеках 017 и 286.

#### UI-настройки

- **FR-012**: На страницах `/zakroma` и `/zakroma/{author_id}/albums` MUST быть слайдер размера плашек (диапазон 200..400px, шаг 50, дефолт 200). Значение сохраняется в `localStorage` (`key = "zakroma_tile_size"`) и применяется на обоих разделах.
- **FR-013**: На тех же страницах MUST быть переключатель «Плашки / Таблица». Значение сохраняется в `localStorage` (`key = "zakroma_view_mode"`, значения `"tiles"` или `"table"`). При выборе «Таблица» отображается таблица с колонками: название, год, кол-во песен (для гостя — `ready_song_count`, для редактора — `total_song_count`; для авторов: автор, кол-во готовых, sort_order).

#### API

- **FR-014**: Публичный API MUST предоставлять endpoint `GET /api/public/authors/{author_id}/albums?scope=main` (или эквивалентный), возвращающий список `AlbumTilePublicDto` с полями: `id`, `name`, `year`, `pictureUrl`, `totalSongCount`, `readySongCount`. Endpoint учитывает роль пользователя (как `/api/public/authors-tiles?scope=main`). Сортировка результата — `year ASC NULLS LAST, name ASC` (см. Clarification Q1).

#### Производительность

- **FR-015**: Загрузка `/zakroma/{author_id}/albums` MUST отрабатывать без агрегатных запросов к `tbl_songs` (используем `tbl_albums.total_song_count` / `ready_song_count` напрямую). Допустимо ровно ноль SQL-фраз `GROUP BY song_album` в postgres.log за период теста.

#### Безопасность

- **FR-016**: Альбомы с `tbl_albums.skip = true` MUST быть полностью скрыты для гостей и обычных пользователей (без утечки данных о существовании). Редактор видит их в UI редактора (отдельный путь), но НЕ в публичных `AuthorTilePublicDto`/`AlbumTilePublicDto`.

### Key Entities *(include if feature involves data)*

- **Album (Альбом)** — AR каталога. Identity = `id`. Расширен полями `total_song_count: Int` (общее кол-во песен в альбоме), `ready_song_count: Int` (кол-во песен с idStatus >= 6). Связь с `Author` через `tbl_albums.author_id` (или `song_author` — проверить по коду в плане).
- **AlbumTilePublicDto** — DTO публичного API. Поля: `id`, `name`, `year`, `pictureUrl` (MinIO URL обложки 200×200), `totalSongCount`, `readySongCount`, `sortOrder` (на будущее).
- **UIState (localStorage)** — клиентский объект настроек отображения. Поля: `zakroma_tile_size: Int` (200..400), `zakroma_view_mode: "tiles" | "table"`.
- **AlbumCached** — L2-кеш в `karaoke-web` (как `authorsTilesCache` в спеке 286). TTL ≤60с. Invalidation через `consumeDirty()` при sync.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Гость, открывая `/zakroma/{author_id}`, видит новую секцию «Альбомы автора» с плашками; альбомы без готовых песен скрыты. Переход по плашке открывает страницу песен с фильтром — URL содержит `?album=...`. Хлебная крошка возвращает на список альбомов автора.
- **SC-002**: Редактор видит все альбомы автора (включая без готовых песен); подпись плашки показывает общее количество песен.
- **SC-003**: Счётчики `total_song_count` и `ready_song_count` в `tbl_albums` обновляются атомарно при INSERT/UPDATE/DELETE в `tbl_songs` без перезапуска контейнера `karaoke-app`. Проверяется unit-тестом триггера + ручной проверкой через `psql`.
- **SC-004**: После sync LOCAL → SERVER значение `total_song_count`/`ready_song_count` совпадает на обеих БД; `recordhash` строки в `tbl_albums` совпадает (отсутствует «diff-шум»).
- **SC-005**: Слайдер размера плашек и переключатель «Плашки/Таблица» сохраняются в localStorage; после F5 настройки восстанавливаются. Размер применяется одновременно на `/zakroma` и `/zakroma/{author_id}/albums`.
- **SC-006**: Запрос `/api/public/authors/{author_id}/albums` отрабатывает без `GROUP BY song_album` в postgres.log. Проверяется командой `grep -c "GROUP BY song_album" /var/log/postgres/postgresql.log` после серии из 100+ запросов — должно вернуть 0. Также `EXPLAIN ANALYZE` не должен показывать `HashAggregate` или `GroupAggregate` по `song_album`.
- **SC-007**: На странице `/zakroma` плашка «Отдельные песни разных авторов» остаётся на месте (PASS 307 не сломан). Новая фича совместима с существующей (плашки авторов + новая секция альбомов — на разных страницах).
- **SC-008**: Применение миграции идемпотентно (повторный запуск SQL не ломает состояние БД). Backfill выполняется один раз; при повторном запуске миграции `total_song_count`/`ready_song_count` остаются корректными.
- **SC-009** (из Clarification Q1, 2026-09-09): Плашки альбомов на `/zakroma/{author_id}/albums` отсортированы как `year ASC NULLS LAST, name ASC` (от старых к новым; внутри одного года — алфавитный тай-брейкер; альбомы без года — в конце). Проверяется на тестовой выборке 3+ альбомов с разными годами.
- **SC-010** (из Clarification Q2, 2026-09-09): Первый элемент сетки альбомов — псевдо-плашка в том же стиле, что обычные тайлы (обложка-иконка + подпись «Все песни автора с группировкой по альбомам»), реализуется через `<slot name="leading" />`. Ссылка ведёт на `/zakroma/{author_id}` без query-параметра `album`.

## Assumptions

- **A-001**: Под «готовой» песней понимается `idStatus >= 6` (APPROVED). Это совпадает с логикой в спеке 286 для авторов и общим контрактом `isContentReady()` в `knowledge/domains/publishing/domain.md`.
- **A-002**: Обложка альбома (200×200 дефолт) берётся из MinIO-кеша `karaoke-cache` по ключу `album-{id}-200x200` (паттерн ADR local-0003). Если обложки нет — placeholder. Это поведение идентично плашкам авторов.
- **A-003**: Значение слайдера и переключателя хранится в `localStorage` (НЕ в БД), так как это чисто клиентская UI-настройка без серверной персонализации. Пользователь не авторизован на `/zakroma`/публичной странице в большинстве случаев.
- **A-004**: URL-схема для страницы песен альбома — query-параметр (`/zakroma/{author_id}?album={album_id}`), а не путь (`/zakroma/{author_id}/{album_id}`). Причина: backward-compatible с текущей страницей `/zakroma/{author_id}`, проще фильтрация на клиенте.
- **A-005**: Шапка страницы песен с фильтром по альбому показывает плашку текущего альбома (или подпись с названием альбома) — это дополнительная UX-плюшка, не критичная для сценария. Если её сложно сделать в рамках спеки — отложить.
- **A-006**: Sync двух-БД через `recordhash` будет работать автоматически после добавления колонок в `tbl_albums` и обновления триггера. Никаких ручных миграций на SERVER не требуется. Это подтверждено в спеке 286 для `tbl_authors`.
- **A-007**: Дефолтный размер плашки — 200×200 (как у авторов). Диапазон слайдера 200..400px с шагом 50 — UX-defaults; конкретные значения могут быть уточнены на стадии plan/design.
- **A-008**: Никаких новых прав `webvue3` (админки) не требуется — фича полностью на публичном сайте. Если в будущем потребуется редактирование `total_song_count` через UI (вручную) — отдельная спека.
- **A-009** (из Clarification Q1, 2026-09-09): Порядок плашек альбомов в сетке — `year ASC NULLS LAST, name ASC`. Альбомы без года (NULL) уходят в конец. Это «библиотечный» порядок, типичный для каталогов классической музыки и альбомов-сборников.
- **A-010** (из Clarification Q2, 2026-09-09): Первый элемент сетки альбомов — псевдо-плашка в стиле обычных тайлов (обложка-иконка + подпись), реализуется через `<slot name="leading" />` в `AuthorTiles.vue`. Подпись — «Все песни автора с группировкой по альбомам»; ссылка — `/zakroma/{author_id}` без query-параметра `album`.