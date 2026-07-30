# Двух-БД синхронизация LOCAL↔SERVER

> **Status**: active
> **Feature Key**: dual-db-sync
> **Last Updated**: 2026-07-29

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
- **Намеренное исключение строк из sync-scope (2026-07-29, specs/089-auto-news-song-release)**:
  автоматически создаваемые новости («песня вышла в эфир») физически существуют ТОЛЬКО на PROD —
  их создаёт сам `karaoke-web` (`SongReleaseAnnouncementService`) напрямую в `WORKING_DATABASE` в
  момент применения синхронизации (`MainController.doChangeRecords`), а не через обычный push с
  LOCAL. Если бы такая строка `tbl_news` участвовала в обычном hash-diff `NewsSyncTarget`
  (LOCAL_TO_SERVER), следующий admin-триггерный «1 клик» увидел бы её как «есть на SERVER, нет на
  LOCAL» и удалил бы как «удалённую в источнике» (mirror-delete) — если когда-либо включат
  `sync_news_push_delete_allowed` (флаг operator-toggleable, дефолт в коде не гарантирует реальное
  runtime-значение). Поэтому `News.listHashes(...)` (используется ТОЛЬКО `NewsSyncTarget`)
  принудительно добавляет `WHERE source = 'manual'` — строки с `source = 'auto'` (и новые колонки
  `song_id`/`source` в целом) структурно невидимы для sync-движка, что соответствует правилу выше
  («recordhash-триггер... не означает участие в sync») в обратную сторону: колонка/строка может
  сознательно НЕ участвовать в sync, если это явно задокументировано. `recordhash`-триггер
  `tbl_news` НЕ пересобирался — новые колонки в хэш не входят намеренно. См.
  `specs/089-auto-news-song-release/research.md` (п.2) и `data-model.md`.
- **Три независимых триггера авто-новостей вместо одного (2026-07-30, specs/092-fix-auto-news-triggers)**:
  `SongReleaseAnnouncementService.checkAndAnnounce` (см. выше) до этой фичи вызывался ровно из одной
  точки — `MainController.doChangeRecords`, то есть только в момент синхронизации таблиц. Это
  создавало два разрыва: новость об «эфире по расписанию» появлялась только при следующей ручной
  синхронизации (иногда с задержкой в часы/дни), а новость о песне, ставшей доступной через апрув
  задания редактора администратором, не появлялась вовсе без отдельной синхронизации. Добавлены ещё
  две вызывающие точки той же самой идемпотентной функции (логика детекции/идемпотентности —
  `Song.isPubliclyWatchable` + `tbl_song_news_announced` — не менялась, см.
  `specs/092-fix-auto-news-triggers/research.md`):
  1. `SongReleaseAnnouncementScheduler` (`karaoke-web/.../services/`) — новый `@Scheduled`-компонент,
     периодическая проверка раз в ~5 минут, независимая от факта синхронизации.
  2. `SongEditorController.approve()` (`karaoke-app`) — сразу после подтверждённого (`SyncResult`
     непуст) best-effort push песни на `Connection.remote()`, тем же прямым JDBC-паттерном, что уже
     используется в этом методе для `SongAssignment` при `target == "remote"` — то есть в обход
     generic sync-движка, аналогично исключению из предыдущего пункта.
  Одновременно `checkAndAnnounce` расширил формируемый текст новости альбомом/годом песни (`Song.album`/
  `Song.year`), когда они заполнены — см. `specs/092-fix-auto-news-triggers/contracts/news-triggers.md`.
- **`SongEditorController.approve()` мог упасть необработанным исключением ПОСЛЕ успешного апрува
  (2026-07-30, specs/094-fix-approve-news-failure)**: вызов из предыдущего пункта (`approve()` →
  push → `checkAndAnnounce`) каждый раз открывал ДВА независимых `Connection.remote()` (каждый —
  новое физическое JDBC-подключение к прод-серверу, т.к. `KaraokeConnection` кеширует соединение на
  экземпляр, не глобально), а финальная запись `aRead.save()` (пометка задания одобренным) шла БЕЗ
  try/catch — необработанный `SQLException` на протухшем/сброшенном соединении уходил как HTTP 500,
  хотя запись песни уже была успешно обновлена. Администратор видел «Ошибка запроса», задание
  оставалось неодобренным, новость — не создавалась. Исправлено: (1) push и `checkAndAnnounce`
  переиспользуют ОДНО `Connection.remote()`-подключение в рамках одного запроса
  (`updateRemoteSongFromLocalDatabase` получил необязательный параметр `toDatabase`); (2) запись
  статуса задания обёрнута в try/catch, конвертируется в типизированный `status: "error"` вместо
  необработанного исключения; (3) повторный клик по уже одобренному заданию — короткое замыкание
  (`status: "already_approved"`), без повторного применения разметки/push/анонса. См.
  `specs/094-fix-approve-news-failure/research.md` и `contracts/approve-endpoint.md`.
- **Тот же класс риска, соседний участок `approve()` (2026-07-30, issue #121,
  specs/095-fix-approve-song-save-exception)**: применение разметки к `Song` (цикл по голосам +
  `settings.saveToDb()`), идущее ПЕРЕД push/`checkAndAnnounce`/`aRead.save()` из предыдущего пункта,
  тоже было без try/catch — необработанное исключение (например, `SQLException`) там ушло бы тем же
  необработанным HTTP 500, что чинил `specs/094`, просто из другого места той же функции. Не было
  частью инцидента 094 (в его логе этот шаг отработал успешно), поэтому осталось не исправленным в
  том PR — найдено отдельно при живой проверке 094 (T006) и исправлено этим PR тем же паттерном:
  try/catch вокруг блока, `return` с `status: "error", error: "song_save_failed"` при сбое.
- **Симптом «Ошибка запроса» пережил и 094, и 095 (2026-07-30,
  specs/096-approve-news-timing-diagnostics)**: живая проверка на машине администратора после обоих
  фиксов показала ТОТ ЖЕ лог, обрывающийся на «Получено хешей: N» — то есть исключения не было (ни
  один из try/catch из 094/095 не сработал), запрос просто долго выполняется/висит после этой точки,
  пока клиент не сдаётся. Гипотезы про пустую `tbl_song_news_announced` (нужен backfill) и про размер
  `tbl_news` не подтвердились кодом/данными (в `tbl_song_news_announced` на проде уже 20000+ строк;
  `News.createAutoAnnouncement` делает один INSERT с FK-проверкой по индексу — не должен зависеть от
  размера таблицы). Добавлены временные тайминги (`println` с миллисекундами) вокруг каждого шага
  `checkAndAnnounce`/`forEachNewlyReadyCandidate`/`approve()` — включая ранее совсем немой catch
  вокруг push (`catch (_: Exception) {}` без единого сообщения в логе). Чисто наблюдаемость, поведение
  не меняет — ждём следующий лог с админ-машины, чтобы увидеть, на каком именно шаге уходит время.

## Ссылки на ключевые классы/файлы

- [`sync/SyncTarget.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sync/SyncTarget.kt) — описание сущности, `object SyncRegistry` и `enum class SyncOperation` объявлены в этом же файле
- [`Utils.updateDatabases`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt) — общий sync runner
- [`ApiController.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt) — REST-контроллер (sync-эндпоинты в составе общего `/api`)
- [`deploy/recordhash_*.sql`](../../deploy/karaoke-db/) — триггеры для всех syncable-таблиц
