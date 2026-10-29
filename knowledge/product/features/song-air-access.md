# Song air access (эфир и окно бесплатного доступа)

> **Status**: active
> **Feature Key**: song-air-access
> **Last Updated**: 2026-10-24
> **Spec**: [specs/369-free-after-onair-flag/spec.md](../../../specs/369-free-after-onair-flag/spec.md) (issue #81)
> Per-feature документ по [Constitution VI FR-009](../../../.specify/memory/constitution.md).

## Что делает

Управление публичным доступом к песне: всегда бесплатно (`free`), окно
бесплатного доступа после эфира, флаг «не снимать с эфира»
(`free_after_on_air`). После того как песня выходит в эфир (`publish_date` +
`publish_time` наступили), она становится публично доступной на определённый
срок (стандартное окно бесплатного доступа — 1 календарный месяц от даты эфира;
см. [specs/143-song-free-access-window](../../../specs/143-song-free-access-window/spec.md)).
После окончания окна песня уходит в premium-only (по таймеру, если только
она не была помечена как `free=true` — «всегда бесплатно»).

## Зачем

В 2026 году появилась потребность (issue #81): пометить конкретную песню
так, чтобы **она оставалась публично доступной после окончания стандартного
окна**, но при этом не была «всегда бесплатной» (т.к. до эфира она всё
ещё premium-only). Это — флаг `free_after_on_air`.

## Как работает

### Флаги и их семантика

| Флаг | Имя в БД | До эфира | После эфира, в окне | После эфира, окно истекло |
|---|---|---|---|---|
| `free` | `free` | open | open | open |
| `free_after_on_air` | `free_after_on_air` | premium-only (как обычно) | open | **open** ← поведение флаг |
| `exclusive` | `exclusive` | premium-only | premium-only | premium-only |
| (без флагов) | — | premium-only | open | premium-only |

Приоритеты в логике доступа (сверху вниз):

0. **Тег `SKIP`** (аудит BR-022, OpenProject #378) → контент снят по требованию
   правообладателя: публичного доступа нет ни у анонима, ни у премиума/подписчика,
   ни по гостевой share-ссылке; демо-фрагмент тоже не выдаётся. Исключения —
   `canWorkWithSkipped` (автор/владелец, specs/293) и предпросмотр по СВОЕМУ
   заданию редактора. Проверяется и в `/access`, и в момент отдачи байт
   (`PlayerAccessService`), и в точечной загрузке/картинках публичного API.
1. `idStatus < 6` → IN_WORK (нет публичного доступа вообще).
2. `exclusive=true` → premium-only (бизнес-решение).
3. `free=true` → open (всегда бесплатно).
4. `free_after_on_air=true` И эфир наступил (`dateTimePublish ≤ now`) → open.
5. Стандартное окно (`freeAccessWindowMonths=1`) → open.
6. Иначе → premium-only.

### Техническая реализация

- **БД**: колонка `free_after_on_air BOOLEAN NOT NULL DEFAULT false` в
  `tbl_songs` (см. `deploy/karaoke-db/50_tbl_songs_free_after_on_air.sql`).
- **Kotlin entity**: `Song.freeAfterOnAir: Boolean` (`Song.kt`,
  рядом с `free`).
- **Логика доступа**:
  - `Song.isFreelyAvailableNow` — учитывает `freeAfterOnAir && onAir` (см.
    `Song.kt` ~стр. 664).
  - `SongStateResolver.resolve` — учитывает `freeAfterOnAir` после `free`
    (см. `SongStateResolver.kt`).
- **UI в админке `SongsTable.vue`**: рядом со столбцом `FR` (`flagFree` —
  флаг «всегда бесплатно») добавлен столбец `IA` (`flagFreeAfterOnAir` —
  флаг «не снимать с эфира»). Формат ячейки идентичен FR: `"-"` если флаг не
  установлен, галочка (U+2713) — если установлен. CSS: `.fld-flag-free-after-on-air`
  (аналог `.fld-flag-free`).
- **UI**: пара кнопок ДА/НЕТ «Не снимать с эфира (после окна доступа)» в
  `SongEdit.vue`, рядом с блоком «Всегда бесплатно».
- **API**:
  - `POST /api/song` (`id`) — поле `freeAfterOnAir: boolean` в `SongDTO`.
  - `POST /api/songs?filter_free_after_on_air=true|false` — фильтр списка.
  - `GET /api/public/player/{id}/access` — публичный доступ (через
    `PublicPlayerController.canWatch` → `isFreelyAvailableNow`).
- **recordhash** — в той же миграции
  (`deploy/karaoke-db/50_tbl_songs_free_after_on_air.sql`) пересозданы оба
  триггера для `tbl_songs` И `tbl_songs_sync` (Constitution III).
  В `update_tbl_songs_sync_recordhash()` все 31 «хвостовых» колонки были
  скопированы из `UPDATE`-выражения без префикса `NEW.` — триггер падал на
  первой же записи (`ERROR: column "rate" does not exist`), то есть ни одна
  песня не уезжала в sync. Исправлено (аудит #336, BR-553, OpenProject #363);
  та же проверка (`DO $$ … $$` со счётом `COALESCE(NEW.`) повторена в
  `deploy/karaoke-db/52_tbl_songs_sync_description_fields.sql`, а статически
  контракт проверяет `tools/check-recordhash-consistency.py`.
- **Схема sync-таблицы** — `tbl_songs_sync` обязана содержать все колонки,
  которые пишет `Song.getSqlToInsert(sync = true)`; недостающие пять
  (`status_process_demo`, `id_tariff`, `description`, `short_description`,
  `warning`) добавлены миграцией
  `deploy/karaoke-db/52_tbl_songs_sync_description_fields.sql` (аудит #336,
  BR-533) — до неё `INSERT INTO tbl_songs_sync` падал на первой песне батча.

### Правило доступа к плееру (с 2026-10-24)

Публичность песни — **одно из пяти оснований** полного доступа к онлайн-плееру. Полный
набор оснований: публичность песни (этот документ) / действующий премиум / бессрочная
подписка на эту песню / гостевая сессия по публичной share-ссылке / предпросмотр
неодобренной разметки по своему заданию редактора.

Основание проверяется **в момент отдачи байт**, а не по факту владения ранее выданным
токеном плеера; «скрытый жест» в перечень оснований не входит и доступа не даёт.
Подробности — [close-gesture-token-paywall.md](close-gesture-token-paywall.md).

## Инварианты

- **MUST**: порядок приоритетов логики доступа неизменен: `idStatus < 6` → IN_WORK, затем `exclusive` → premium-only, затем `free` → open, затем `freeAfterOnAir && onAir` → open, затем стандартное окно, иначе premium-only (FR-005, FR-006, FR-007).
- **MUST**: `freeAfterOnAir=true` действует **только после** наступления эфира (`dateTimePublish ≤ now`); до эфира песня остаётся premium-only (clarification 2026-09-11).
- **MUST**: `free` и `freeAfterOnAir` — независимые флаги; `freeAfterOnAir` не затирает `free` и наоборот (FR-009).
- **MUST**: `exclusive=true` имеет приоритет над `freeAfterOnAir` (FR-005).
- **MUST**: колонка `free_after_on_air` присутствует в **обеих** таблицах — `tbl_songs` и `tbl_songs_sync`; recordhash-триггеры обеих таблиц включают поле (Constitution III, LOCAL↔SERVER sync).
- **MUST**: `tbl_songs_sync` содержит все колонки, которые пишет `Song.getSqlToInsert(sync = true)`, а в теле обоих recordhash-триггеров нет «голых» имён колонок (только `NEW.`). Оба инварианта проверяются статически, без БД: `python3 tools/check-recordhash-consistency.py` (CI + pre-commit).
- **MUST**: авто-новости (`SongReleaseAnnouncementService`) не ретриггерятся из-за флага — публикация остаётся привязана к первому переходу `isPubliclyWatchable` (FR-008).
- **MUST**: флаг не упоминается в публичных маркетинговых материалах — только внутренний UI редактора и движок доступа (FR-010).
- **MUST**: публичность песни — одно из оснований доступа к плееру; основание проверяется в момент отдачи байт, а «скрытый жест» основанием доступа не является (спека 557, OpenProject #358).

## Известные ловушки

- **Флаг без наступившего эфира ничего не даёт**: `freeAfterOnAir=true` при `dateTimePublish` в будущем сохраняется, но публичный доступ не меняет — редактор может ожидать «включил и сразу открылось» (FR-004, FR-005).
- **Забыли пересобрать recordhash-триггер `tbl_songs_sync`**: запись в sync-таблицу упадёт с `column free_after_on_air does not exist`, либо её recordhash не будет включать значение → LOCAL↔SERVER молча разойдутся (Constitution III). Прецедент — BR-553: потерянный префикс `NEW.` в 31 колонке (функция создаётся успешно, падает только триггер).
- **Переприменение 50 после 52**: 50 пересобирает ту же функцию по состоянию на момент 50 (95 колонок) и потеряет четыре новых. Порядок «50 → 52»; повторно 50 после 52 не применять.
- **Не пересчитали recordhash существующих строк**: md5-цепочка изменилась из-за нового поля, без backfill обе таблицы расходятся до первого UPDATE каждой строки.
- **Мёртвое имя `tbl_settings`**: таблица переименована в `tbl_songs` (`28_rename_settings_to_songs.sql`); старые `deploy/recordhash_settings*.sql` удалены (Pass 369), использовать их нельзя — проверяется `tools/check-no-legacy-tbl-settings.sh`.
- **`free=true` + `freeAfterOnAir=true`**: оба флага хранятся раздельно, эффект даёт `free`; при последующем выключении `free` флаг «не снимать с эфира» продолжает действовать как запасной (spec 369 Clarifications).
- **Ретриггер авто-новости**: изменение флага не должно повторно публиковать новость «вышла в эфир» (FR-008).
- **Публичность ≠ единственное условие**: «в эфире» само по себе не открывает полный доступ премиум-пользователю после окончания окна — действуют ещё премиум/подписка/share-сессия/задание редактора; проверка живёт в `PlayerAccessService` (см. [close-gesture-token-paywall.md](close-gesture-token-paywall.md)).

## Ссылки

- [specs/369-free-after-onair-flag/spec.md](../../../specs/369-free-after-onair-flag/spec.md) — основная спека (issue #81)
- [specs/143-song-free-access-window/spec.md](../../../specs/143-song-free-access-window/spec.md) — стандартное окно бесплатного доступа (1 месяц)
- [Song.kt](../../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt) — `freeAfterOnAir`, `isFreelyAvailableNow`, `flagFreeAfterOnAir`
- [SongStateResolver.kt](../../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongStateResolver.kt) — резолвер состояния песни (IN_WORK / ON_AIR / premium-only)
- [SongEdit.vue](../../../webvue3/src/components/Songs/edit/SongEdit.vue) — пара кнопок «Не снимать с эфира»
- [SongsTable.vue](../../../webvue3/src/components/Songs/SongsTable.vue) — столбец `IA` (`flagFreeAfterOnAir`)
- [PublicPlayerController.kt](../../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicPlayerController.kt) — `canWatch` → `isFreelyAvailableNow`
- [50_tbl_songs_free_after_on_air.sql](../../../deploy/karaoke-db/50_tbl_songs_free_after_on_air.sql) — миграция колонки + оба recordhash-триггера
- [52_tbl_songs_sync_description_fields.sql](../../../deploy/karaoke-db/52_tbl_songs_sync_description_fields.sql) — недостающие колонки `tbl_songs_sync` + recordhash (BR-533)
- [check-recordhash-consistency.py](../../../tools/check-recordhash-consistency.py) — guard recordhash-контракта (CI + pre-commit)
- [song-entity.md](../../domains/catalog/components/song-entity.md) — описание полей `Song`
- [song-lifecycle.md](../../domains/catalog/components/song-lifecycle.md) — жизненный цикл
- [dictionaries.md](../../domains/publishing/components/dictionaries.md) — словарь магических кодов
- [growth.md (архив)](../../../archive/docs/strategy/growth.md) — модель монетизации (Модель D, гибрид)
- [constitution.md](../../../.specify/memory/constitution.md) — Constitution III (sync-таблицы), VI FR-009

## Changelog

- **2026-10-24** (BR-022, OpenProject #378, пакет PKG-S2-15): тег `SKIP` добавлен высшим
  приоритетом правила доступа — плеер и публичные выборки не отдают снятый контент.
  Карточка P-36-05 имела статус NEEDS-VERDICT; подтверждение «снятие контента всегда
  сопровождается снятием публикации/стемов» — за владельцем.
- **2026-10-24** (спека 557, OpenProject #358): добавлен раздел «Правило доступа к плееру»:
  публичность песни — одно из пяти оснований, проверяется в момент отдачи байт; «скрытый
  жест» основанием доступа не является.
- **Pass 369** (2026-09-11): Initial. Добавлен флаг `free_after_on_air`,
  OpenProject #81 «Флаг не снимать с эфира».
