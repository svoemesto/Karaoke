# Двух-БД синхронизация LOCAL↔SERVER

> **Status**: active
> **Feature Key**: dual-db-sync
> **Last Updated**: 2026-07-20

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

## Ссылки на ключевые классы/файлы

- [`sync/SyncTarget.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sync/SyncTarget.kt) — описание сущности
- [`sync/SyncRegistry.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sync/SyncRegistry.kt) — реестр
- [`sync/SyncOperation.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sync/SyncOperation.kt) — операция diff
- [`Utils.updateDatabases`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt) — общий sync runner
- [`SyncController.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SyncController.kt) — REST-контроллер
- [`deploy/recordhash_*.sql`](../../deploy/karaoke-db/) — триггеры для всех syncable-таблиц
