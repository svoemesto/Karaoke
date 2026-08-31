# Feature Specification: Кэш счётчиков песен автора в `tbl_authors`

**Feature Branch**: `286-author-song-counts-cache`
**Created**: 2026-08-31
**Status**: Draft
**Input**: User description: "При заходе в закрома (/zakroma) пользователь видит плашки авторов и для каждого автора — количество готовых песен (а если пользователь редактор — то общее количество песен). Это требует множественных запросов на бэке к базе данных. Сейчас вроде все нужные поля проиндексированы, запросы выполняются быстро, но нагрузка на БД всё равно есть. Мысль такая. Количество готовых песен и общее количество песен изменяется не часто, поэтому имеет смысл эти два числа хранить в таблице авторов, и обновлять эти данные по необходимости — при добавлении новых песен и при изменении статуса песни в 'готово' — и синхронизировать эти данные с сервером. А в закромах брать эти цифры из таблицы авторов, а не из запросов SQL к базе."

> **Контекст обсуждения.** Пользователь задал вопрос «что думаешь, насколько такой подход облегчит нагрузку?». Спецификация фиксирует ответ: подход правильный по сути, но требует аккуратного исполнения (источник истины для счётчиков — БД-триггер, не код; backfill при миграции; sync через `recordhash`). Архитектурные вопросы вынесены в раздел Q&A; решения фиксируются в `## Clarifications`.

---

## Clarifications

### Session 2026-08-31

- Q: Механизм инвалидации L2-кеша `authorsTilesCache` на проде при изменении счётчиков на локальной БД → A: **B (`consumeDirty` через sync)** — при sync-пуше `tbl_authors` с LOCAL на SERVER на стороне `karaoke-web` срабатывает существующий `consumeDirty()`, который сбрасывает кэш; целевая задержка — ≤ одного sync-прохода.
- Q: Семантика «skip» для счётчиков `tbl_authors.ready_songs_count` / `total_songs_count` → A: **A (зеркалить текущее поведение)** — триггер обновляет счётчики для всех авторов, у которых `song_author` есть в `tbl_authors`, **независимо от `tbl_authors.skip`**. UI по-прежнему фильтрует skip-авторов (как сейчас делает `loadListAuthors`), но их счётчики в БД остаются актуальными (для отладки и будущих фич). Это зеркалит существующий код `loadAuthorSongCounts`, который также не фильтрует по `tbl_authors.skip`.
- Q: Что делать с песнями, у которых `song_author` не имеет соответствия в `tbl_authors` («висящие» песни)? → A: **A (тихий no-op)** — триггер выполняет `UPDATE tbl_authors SET ... WHERE author = NEW.song_author` (или `OLD.song_author` для DELETE/UPDATE). Если 0 строк затронуто — no-op, БД-консистентность для существующих авторов не нарушается; «висящие» песни игнорируются как в текущем коде `PublicApiController.authorsTiles` (там они логируются через `println` warning). Без `RAISE EXCEPTION` — чтобы не сломать существующие bulk-импорты.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Снижение нагрузки на БД при заходе в `/zakroma` (Priority: P1)

Посетитель заходит на публичную страницу «Закрома» (`/zakroma`). Сервер отдаёт сетку плашек авторов с подписью «N готовых песен». Запрос `/api/public/authors-tiles?scope=main` отрабатывает без `GROUP BY` по `tbl_songs`: счётчики читаются напрямую из `tbl_authors.ready_songs_count` (одна колонка — без агрегации, без `JOIN`, без `DISTINCT`).

**Why this priority**: Это основная мотивация фичи — убрать регулярный GROUP BY по `tbl_songs` с горячего пути `/zakroma`. Даже при наличии L2-кеша `authorsTilesCache` (TTL 30 мин, см. спеку 248) холодный старт и инвалидация теперь триггерят одну лёгкую выборку вместо агрегатного сканирования.

**Independent Test**: Запросить `/api/public/authors-tiles?scope=main` 100 раз подряд с интервалом 1 с — на каждом запросе лог `karaoke-web` НЕ должен содержать `select song_author, count(*) ... group by song_author`. Допустимо ровно ноль вхождений SQL-фразы `group by song_author` в `postgres.log` за период теста (или эквивалентный assert на кодовой стороне — тест, что в `Song.kt` метод `loadAuthorSongCounts` НЕ вызывается из `PublicApiController.authorsTiles`).

**Acceptance Scenarios**:
1. **Given** в `tbl_authors` для автора X `ready_songs_count = 42`, **When** посетитель заходит на `/zakroma`, **Then** в подписи плашки автора X отображается `42`, и backend-лог НЕ содержит SQL c `group by song_author`.
2. **Given** редактор заходит на `/zakroma`, **When** сервер отдаёт тайлы, **Then** подпись плашки содержит `total_songs_count` (а не `ready_songs_count`) — для редактора фильтр по статусу снимается (см. спеку 017).
3. **Given** `tbl_authors.ready_songs_count` корректно для всех авторов, **When** счётчики читаются, **Then** HTTP-ответ `/api/public/authors-tiles` совпадает по числам с ответом до включения фичи (регрессия отсутствует — числа идентичны).

---

### User Story 2 — Автоматическая инвалидация счётчиков при изменении песен (Priority: P1)

Администратор импортирует пачку новых песен (`Import Folder`, см. спеку 282), либо редактор переводит песню в статус «готово» (`id_status >= 6`), либо удаляет песню. После каждой такой операции счётчики `ready_songs_count` / `total_songs_count` в `tbl_authors` обновляются **атомарно** — так, чтобы любая последующая загрузка `/zakroma` отдавала уже актуальные цифры, без ожидания истечения L2-кеша `authorsTilesCache`.

**Why this priority**: Без автоинвалидации фича бесполезна — числа в `tbl_authors` застынут и перестанут совпадать с реальностью. Это не просто «обновление по крону», а гарантия «счётчик = правда» в любой момент времени.

**Independent Test**:
- Вставить в `tbl_songs` строку со статусом `id_status = 6` для автора X (где сейчас `ready_songs_count = 3`) — `ready_songs_count` должен стать `4` без перезапуска контейнера.
- Вставить в `tbl_songs` строку со статусом `id_status = 1` — `total_songs_count` должен инкрементироваться, `ready_songs_count` остаться неизменным.
- Обновить `id_status` с `1` на `6` — `ready_songs_count` инкрементируется, `total_songs_count` неизменен.
- Обновить `id_status` с `6` на `5` — `ready_songs_count` декрементируется.
- Удалить песню — оба счётчика уменьшаются на 1 для соответствующего автора.

**Acceptance Scenarios**:
1. **Given** автор X с `ready_songs_count = 5`, **When** в `tbl_songs` вставляется строка для X с `id_status = 6`, **Then** `tbl_authors.ready_songs_count` для X = `6` (атомарно, в той же транзакции).
2. **Given** автор Y с `total_songs_count = 10, ready_songs_count = 7`, **When** одна песня Y переходит из `id_status = 6` в `id_status = 5`, **Then** `total_songs_count = 10` (не изменился), `ready_songs_count = 6` (декрементирован).
3. **Given** песня автора Z в `tbl_songs`, **When** она удаляется, **Then** оба счётчика Z уменьшаются на 1 (или до 0, если других песен нет).
4. **Given** skip-автор X (`tbl_authors.skip = true`, скрыт на UI) с `ready_songs_count = 5, total_songs_count = 5`, **When** в `tbl_songs` вставляется новая песня X с `id_status = 6`, **Then** `ready_songs_count = 6, total_songs_count = 6` (счётчики обновляются, несмотря на `skip = true` — UI скрывает X, но БД остаётся консистентной).
5. **Given** «висящая» песня: `tbl_songs.song_author = "Ghost"` (нет соответствующего `tbl_authors.author = "Ghost"`), **When** эта песня обновляется или удаляется, **Then** триггер срабатывает как no-op (0 строк обновлено в `tbl_authors`), без `RAISE EXCEPTION`, без создания мусорной записи в `tbl_authors`.
6. **Given** после операции, **When** посетитель заходит на `/zakroma`, **Then** счётчик на плашке автора отражает новое состояние сразу (без задержки на TTL кэша, либо с задержкой ≤ нескольких секунд — на инвалидацию L2-кеша).

---

### User Story 3 — Синхронизация счётчиков между LOCAL и SERVER (Priority: P1)

Администратор на своей машине добавляет новую песню (импорт папки) — счётчики автора в LOCAL-БД обновляются. При односторонней синхронизации LOCAL → SERVER эти счётчики тоже расходятся на SERVER-БД. То есть `recordhash` триггер для `tbl_authors` учитывает новые колонки.

**Why this priority**: Без пересинхронизации фича работает только на локальной БД — на проде будут старые/нулевые значения. После первой же синхронизации пользователь увидит на проде «0 готовых песен» у всех авторов, что сломает `/zakroma` на проде.

**Independent Test**:
- Внести изменение `tbl_authors.ready_songs_count` на LOCAL (через тестовый INSERT/UPDATE).
- Запустить синхронизацию LOCAL → SERVER.
- Проверить: на SERVER значение совпадает с LOCAL.
- Проверить: `recordhash` для `tbl_authors` row в обеих БД совпадает (нет «diff-шум» из-за неучтённых колонок).

**Acceptance Scenarios**:
1. **Given** LOCAL: `tbl_authors.ready_songs_count = 42` для автора X, SERVER: `ready_songs_count = 41`, **When** запускается синхронизация LOCAL → SERVER, **Then** на SERVER значение становится `42` и `recordhash` строки совпадает с LOCAL.
2. **Given** в `tbl_authors` добавлены колонки `ready_songs_count`, `total_songs_count`, **When** администратор смотрит `Karaoke.properties`, **Then** флаги `sync_authors_push_update_allowed` и `sync_authors_pull_update_allowed` остаются `true` (синхронизация колонок включена).

---

### User Story 4 — Backfill существующих авторов при миграции (Priority: P2)

Миграция `add_author_song_counts_columns.sql` добавляет колонки и **заполняет их актуальными значениями** для всех существующих строк `tbl_authors` за один проход (один UPDATE с подзапросом COUNT по `tbl_songs`). После миграции `/zakroma` сразу отдаёт корректные цифры — без периода «временных нулей».

**Why this priority**: Без backfill после миграции `/zakroma` на короткое время (от запуска миграции до первого триггера) покажет нули. Это регрессия UX, пусть и короткая. Backfill делает миграцию атомарной с точки зрения пользователя.

**Independent Test**:
- Применить миграцию на тестовой БД с уже существующими авторами и песнями.
- Проверить: для каждого `tbl_authors` row `ready_songs_count = COUNT(*) FROM tbl_songs WHERE song_author = author.id AND id_status >= 6` (для не-skip авторов).
- Проверить: `total_songs_count = COUNT(*) FROM tbl_songs WHERE song_author = author.id` (или с учётом `skip`-фильтра — см. Q1 ниже).
- Проверить: число не-skip авторов без песен = 0, счётчики для них = 0.

**Acceptance Scenarios**:
1. **Given** до миграции в `tbl_authors` есть 100 авторов, в `tbl_songs` — 5 000 песен, **When** применяется миграция, **Then** после миграции все 100 строк `tbl_authors` имеют непустые `ready_songs_count` и `total_songs_count` (включая `0` для авторов без песен).
2. **Given** миграция применена, **When** `SELECT SUM(ready_songs_count)` по `tbl_authors`, **Then** это значение равно `SELECT COUNT(*) FROM tbl_songs WHERE id_status >= 6` (с допуском ±1 на race-condition в момент миграции).

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: В таблице `tbl_authors` MUST быть добавлены колонки `ready_songs_count BIGINT NOT NULL DEFAULT 0` и `total_songs_count BIGINT NOT NULL DEFAULT 0`.
- **FR-002**: Колонки MUST заполняться автоматически при любых изменениях в `tbl_songs` (INSERT, UPDATE поля `id_status` или `song_author`, DELETE) — посредством DB-триггера `trg_tbl_songs_update_author_counts` на таблице `tbl_songs`, атомарно в той же транзакции, что и само изменение песни.
- **FR-003**: «Готовая песня» определяется как `id_status >= 6` (то же определение, что и в `Song.loadAuthorSongCounts` сейчас и в `Zakroma.getZakroma`).
- **FR-004**: «Общее количество» = `COUNT(*) FROM tbl_songs WHERE song_author = tbl_authors.author` для всех авторов, у которых `song_author` есть в `tbl_authors` — **включая skip-авторов** (зеркалит текущее поведение `loadAuthorSongCounts`, который не фильтрует по `tbl_authors.skip`). «Готовое количество» = `COUNT(*) WHERE song_author = tbl_authors.author AND id_status >= 6`. UI-фильтр по `tbl_authors.skip = false` (см. `loadListAuthors`) применяется отдельно на уровне `PublicApiController.authorsTiles`, после чтения счётчиков из БД — skip-авторы просто не попадают в ответ, но счётчики в БД у них поддерживаются актуальными.
- **FR-005**: Триггер MUST корректно обрабатывать UPDATE поля `song_author` (перенос песни от одного автора к другому — оба счётчика сдвигаются), UPDATE `id_status` (включая все возможные переходы в/из «готово»), и DELETE. Для UPDATE/DELETE, где `OLD.song_author` (или `NEW.song_author`) не имеет соответствия в `tbl_authors` («висящие» песни — `tbl_songs.song_author` это строка, не FK), триггер MUST вести себя как no-op (`UPDATE ... WHERE author = ...` для несуществующего автора = 0 затронутых строк), без `RAISE EXCEPTION` — чтобы не сломать существующие bulk-импорты и не плодить мусорные записи в `tbl_authors`. Это зеркалит поведение `PublicApiController.authorsTiles`, который сейчас такие песни тихо пропускает.
- **FR-006**: Эндпоинт `/api/public/authors-tiles` MUST читать счётчики из `tbl_authors` (новые колонки) вместо вызова `Song.loadAuthorSongCounts`. Существующий кэш `authorsTilesCache` (TTL 30 мин, спека 248) MUST остаться как L2.
- **FR-007**: При изменении счётчиков на LOCAL-БД через `karaoke-app` (который обслуживает админский UI) MUST автоматически инвалидироваться L2-кеш `authorsTilesCache` на SERVER через механизм `consumeDirty()`, триггерящийся из sync-пуша `tbl_authors` (sync пушит изменения строк `tbl_authors` на SERVER; на стороне `PublicApiController` уже реализован сброс `authorsTilesCache` при `consumeDirty()`, см. спеку 248 — этот же путь переиспользуется для новых колонок). Целевая задержка от изменения на LOCAL до видимой инвалидации на SERVER — ≤ времени одного sync-прохода (десятки секунд), без ожидания TTL=30 мин.
- **FR-008**: Миграция MUST включать backfill: один UPDATE, рассчитывающий `ready_songs_count` и `total_songs_count` для всех существующих строк `tbl_authors` на момент применения миграции (атомарно, в одной транзакции миграции).
- **FR-009**: `recordhash`-триггер для `tbl_authors` MUST быть пересоздан с учётом новых колонок (`ready_songs_count`, `total_songs_count` включены в канонизированную строку), иначе md5 разойдётся и sync сломается (Constitution Principle III).
- **FR-010**: Sync-флаги `sync_authors_push_update_allowed` и `sync_authors_pull_update_allowed` MUST оставаться `true` (по умолчанию уже), иначе счётчики не будут расходиться на SERVER.
- **FR-011**: Фича MUST быть совместима с существующим `getCachedAuthorsTiles` (спека 248) — L2-кеш продолжает работать, новая фича заменяет только источник данных для холодного заполнения кэша.

### Key Entities *(include if feature involves data)*

- **`tbl_authors`** — расширена двумя новыми колонками: `ready_songs_count` (готово: `id_status >= 6`), `total_songs_count` (все песни). Колонки синхронизируются с SERVER-БД через существующий sync-механизм.
- **`tbl_songs`** — таблица песен. Источник событий для триггера. Никакие её колонки не меняются (только добавляется триггер `trg_tbl_songs_update_author_counts`).

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: При загрузке `/api/public/authors-tiles?scope=main` SQL `select song_author, count(*) as cnt from tbl_songs ... group by song_author` MUST больше НЕ выполняться (0 выполнений за 100 запросов подряд). Это проверяется по логу SQL на стороне `karaoke-web` / тестом на отсутствие вызова `Song.loadAuthorSongCounts` в стеке.
- **SC-002**: Числа на плашках авторов в `/zakroma` после внедрения фичи MUST совпадать с числами до внедрения (для всех авторов, для всех scope'ов `main`/`special`/`all`) — регрессия отсутствует.
- **SC-003**: Латентность cold-cache для `/api/public/authors-tiles` MUST уменьшиться. Точный порог — см. Q1 ниже, ожидаемый диапазон: с X мс (GROUP BY по `tbl_songs`) до Y мс (одна точечная выборка из `tbl_authors`). Достаточно, чтобы укладываться в бюджет < 100 мс для 100 авторов.
- **SC-004**: После INSERT/UPDATE/DELETE в `tbl_songs` (любой одиночной операции) счётчики в `tbl_authors` MUST обновиться атомарно в той же транзакции (тест: после `COMMIT` счётчики корректны; в середине транзакции — обновлены, но видны только её участникам).
- **SC-005**: После `bash do.sh sync_local_to_server` (или эквивалентного sync-команд) значения `ready_songs_count` и `total_songs_count` на SERVER-БД MUST совпадать с LOCAL-БД для всех авторов.
- **SC-006**: Миграция на тестовой БД с 5 000 песен и 100 авторами MUST выполниться за < 30 секунд (backfill — один UPDATE).

---

## Assumptions

- **(A1)** Источник истины для счётчиков — DB-триггер, а не код `Song.save()`. Рациональ: единственный путь гарантировать атомарность и не пропустить операции (bulk-импорт, миграции, прямые SQL). Альтернатива «обновлять в `Song.save()`» рискованна — любое место, которое меняет `tbl_songs` в обход `Song.save`, сломает счётчики. Триггер ловит всё.
- **(A2)** `id_status >= 6` — это и есть «готово» (зеркалит текущий код `Song.loadAuthorSongCounts` и `Zakroma.getZakroma`).
- **(A3)** Счётчики хранятся в `tbl_authors` (а не в отдельной `tbl_author_stats`) — потому что их всего две, отдельная таблица не оправдана. Это соответствует предложению пользователя.
- **(A4)** В `karaoke-app` (admin-машина) и `karaoke-web` (прод) — один процесс JVM, гонок между параллельными `Song.save` нет. Но триггер пишется так, чтобы быть корректным в любом случае.
- **(A5)** Существующий L2-кеш `authorsTilesCache` (спека 248, TTL 30 мин, key = `scope:onlyPublished`) остаётся без изменений — он продолжает работать поверх нового источника данных. Это снимает 99 % запросов к БД, новая фича закрывает оставшийся 1 % холодных заполнений кэша.
- **(A6)** Sync-механизм для `tbl_authors` уже включён (8 флагов в `KaraokeProperties.kt`), фича не требует его включения — только пересоздания `recordhash`-триггера (FR-009).
- **(A7)** Бэкенд: Kotlin 1.x + Spring Boot + сырой JDBC. Никакого JPA/Hibernate для этих колонок (Constitution Principle II).
- **(A8)** Колонки добавляются на LOCAL и на SERVER-БД одной и той же миграцией (с учётом что миграции запускаются на обеих БД; либо двумя отдельными миграциями с одинаковым backfill).

## Open Questions (Q&A)

### Q1: ~~Подход к инвалидации L2-кеша `authorsTilesCache` на проде при изменении счётчиков на локальной БД~~ ✅ **Resolved 2026-08-31 → B (`consumeDirty` через sync)**

**Контекст и решение** (FR-007): при sync-пуше `tbl_authors` с SERVER-БД на стороне `karaoke-web` срабатывает `consumeDirty()`, который сбрасывает `authorsTilesCache`. Целевая задержка — ≤ одного sync-прохода (десятки секунд). Реализация переиспользует существующий путь инвалидации из спеки 248.

### Q2: ~~Семантика «skip» для `total_songs_count`~~ ✅ **Resolved 2026-08-31 → A (зеркалить текущее поведение)**

**Контекст и решение** (FR-004): триггер обновляет счётчики для всех авторов, у которых `song_author` присутствует в `tbl_authors`, без фильтра по `tbl_authors.skip`. UI по-прежнему скрывает skip-авторов (через `loadListAuthors`), но их счётчики в БД остаются актуальными — это зеркалит существующее поведение `loadAuthorSongCounts`.

### Q3: ~~Что делать с «висящими» песнями (`song_author` без соответствия в `tbl_authors`)?~~ ✅ **Resolved 2026-08-31 → A (тихий no-op)**

**Контекст и решение** (FR-005): `tbl_songs.song_author` — строка, не FK, поэтому возможны «висящие» песни. Триггер выполняет `UPDATE tbl_authors SET ... WHERE author = NEW/OLD.song_author`; если 0 строк затронуто — no-op, без `RAISE EXCEPTION`. Зеркалит поведение `PublicApiController.authorsTiles`, который сейчас такие песни тихо пропускает с warning.

---

## Done When

- [ ] Миграция `add_author_song_counts_columns.sql` принята на LOCAL и SERVER БД, выполнен backfill.
- [ ] DB-триггер `trg_tbl_songs_update_author_counts` создан на LOCAL и SERVER, корректно обрабатывает INSERT/UPDATE/DELETE.
- [ ] `recordhash`-триггер для `tbl_authors` пересоздан с учётом новых колонок (LOCAL и SERVER).
- [ ] `PublicApiController.authorsTiles` читает счётчики из `tbl_authors`, а не вызывает `Song.loadAuthorSongCounts` (холодное заполнение L2-кеша).
- [ ] Все 5 acceptance-сценариев US2 пройдены (insert, update, status-change, delete, transfer song_author).
- [ ] Все 2 acceptance-сценария US3 пройдены (sync LOCAL → SERVER, `recordhash` совпадает).
- [ ] LiveDocs обновлены: добавлен per-feature документ `docs/features/author-song-counts-cache.md` (Constitution Principle VI FR-009).
- [ ] Проверена AGENTS.md чек-лист обязательной сборки после изменения кода (compileKotlin + ktlintCheck + bootJar обоих модулей + Vite build обоих фронтов + Docker build webvue3/public).
- [ ] Проверена синхронизация счётчиков на проде после деплоя.