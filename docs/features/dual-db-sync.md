# Двух-БД синхронизация LOCAL↔SERVER

> **Status**: active
> **Feature Key**: dual-db-sync
> **Last Updated**: 2026-07-26

## Что делает

Универсальный движок синхронизации записей между двумя PostgreSQL-БД:
LOCAL (admin-машина) и SERVER (прод). Каждая сущность, участвующая в
sync, имеет свой `SyncTarget<T>` с 8 флагами (push/pull × insert/update/
delete/move). Сравнение — через `recordhash` (md5 от канонизированной
строки), O(n) через `associateBy`, не O(n²).

## Зачем

Karaoke — self-pipeline. Admin-машина разрабатывает новые фичи и редактирует
песни, прод-сервер публикует их пользователям. Нужно аккуратно перенести
изменения в одну сторону (push) или обратно (pull), не потеряв данные и
не поломав конкурентные правки.

## Как работает (кратко)

1. **Триггер `recordhash`** в каждой syncable-таблице поддерживает колонку
   `recordhash = md5(canonical_row)` на INSERT/UPDATE/DELETE.
2. **`SyncTarget<T>`** (см. `sync/SyncTarget.kt`) описывает одну сущность:
   - `key` (имя в `SyncRegistry.all`),
   - `loadFromDb()` — загрузить все записи с обеих сторон,
   - `diffByHash()` — построить `SyncOperation` (INSERT/UPDATE/DELETE/MOVE)
     через `associateBy { it.id }` (O(n), не O(n²)).
3. **`SyncRegistry.all`** — список всех `SyncTarget`. Каждый target обязан
   иметь 8 флагов в `KaraokeProperties.kt`:
   `sync_<key>_<push|pull>_<insert|update|delete|move>_allowed`.
4. **`updateDatabases()`** — общая функция для всех сущностей. Возвращает
   `SyncResult(created, updated, deleted, moved)`.
5. **REST-контракт**:
   - `GET /api/sync/entities` — список всех сущностей с их флагами.
   - `POST /api/sync/run` (`key`, `direction=PUSH|PULL`, опц. `id`) — запуск.
   - `POST /api/sync/oneclick` — синхронизация всех в одну сторону.
   - `POST /api/sync/setflag` — изменить флаг.

## Инварианты / правила

- **MUST**: новая syncable-сущность **обязана** быть добавлена в
  `SyncRegistry.all` (см.
  [CONTRIBUTING.md#kotlin-sync-registry](../../CONTRIBUTING.md)).
  Наличие `recordhash`-триггера в SQL **не** означает авто-участие.
- **MUST**: при изменении схемы syncable-таблицы **обязательно**
  пересоздаётся `recordhash`-триггер (см.
  [CONTRIBUTING.md#sql-recordhash-triggers](../../CONTRIBUTING.md)).
- **MUST**: сравнение `localMap vs remoteMap` через `associateBy { it.id }` —
  не вложенные `.any`/`.none` (O(n²) → 3+ минуты на 18k записей).
- **MUST**: загрузка записей для diff — пакетно `WHERE id IN (...)`,
  не по одной в цикле (N+1 запросов).
- **MUST**: для append-only сущностей (`chatmessages`) `pull_move` держится
  выключенным — MOVE удаляет строку из источника (PROD), что для чата
  стирало бы переписку с сервера.

## Известные ловушки

- **Identity-sequence дрейф**: sync вставляет строки с явным серверным `id`
  через `INSERT .. OVERRIDING SYSTEM VALUE` — это **не двигает** локальную
  `GENERATED ALWAYS AS IDENTITY`-последовательность. У таких таблиц
  `nextval()` рано или поздно попадает на занятый id.
  `KaraokeDbTable.createDbInstance()` сам обнаруживает дрейф и
  рестартует sequence — но через **два `executeQuery()` на одном
  `Statement`** нельзя (закрывает `ResultSet`).
- **Timestamp-биндинг**: `Timestamp.valueOf()` падает на `null` или
  некорректной строке. Используйте `try-catch` с default.
- **Nullable-колонки**: при изменении nullable-колонки `recordhash`
  меняется → ложное «обновление». Не забывайте пересоздать триггер.
- **`tbl_subscriptions` (2026-07-09)**: была заведена в БД, но не в
  `SyncRegistry` — дашборд статистики «Монетизация» врал на LOCAL. Все
  новые сущности — через `SyncRegistry`.
- **`last_update useInDiff=false`**: некоторые таблицы имеют
  `last_update` только для UI, не для диффа. Помечается в entity.
- **Historical rename (2026-07-26, specs/011-album-song-rename)**: главная syncable-сущность
  `Settings`/`tbl_settings` переименована в `Song`/`tbl_songs` (класс, DTO, `SyncTarget` —
  `SettingsSyncTarget`→`SongSyncTarget`; физическая таблица тоже переименована). Строковый
  `key = "settings"` в `SyncRegistry` **намеренно не менялся** — он зашит в несохранённый в git
  `Karaoke.properties` на машине администратора, и смена ключа обнулила бы уже настроенные флаги
  синхронизации без предупреждения (см. `specs/011-album-song-rename/research.md` §5). Одновременно
  добавлены две новые сущности по тому же паттерну `GenericKaraokeDbTableSyncTarget`: `Album`
  (`key = "albums"`, `tbl_albums`) и `SongCoAuthor` (`key = "songcoauthors"`, `tbl_song_authors`,
  многие-ко-многим `Song`↔`Author` для соавторов, отдельно от главного автора).
- **Новые колонки на уже syncable-таблицах (2026-07-27, specs/012-entity-description-fields)**:
  `tbl_authors`, `tbl_albums`, `tbl_songs` получили по 3 новых столбца
  (`description`, `short_description`, `warning`, миграция
  `deploy/karaoke-db/31_entity_description_fields.sql`) — новых `SyncTarget`/sync-флагов заводить
  не потребовалось (все три таблицы уже зарегистрированы), но все три `recordhash`-триггера были
  пересобраны с новыми колонками (иначе diff молча не увидел бы их как расхождение — см. MUST выше).

## Ссылки на ключевые классы/файлы

- [`sync/SyncTarget.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sync/SyncTarget.kt) — описание сущности, `object SyncRegistry` и `enum class SyncOperation` объявлены в этом же файле
- [`Utils.updateDatabases`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt) — общий sync runner
- [`ApiController.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt) — REST-контроллер (sync-эндпоинты в составе общего `/api`)
- [`deploy/recordhash_*.sql`](../../deploy/karaoke-db/) — триггеры для всех syncable-таблиц
