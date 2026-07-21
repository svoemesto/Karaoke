# Ключевые инварианты (правила, нарушение которых уже ломало прод)

> **Status**: active
> **Last Updated**: 2026-07-21 (PR #24, 012-development-md-rewrite)

> Датированная история инцидентов — в
> [docs/architecture-notes-archive.md](./architecture-notes-archive.md).

## Ловушки `karaoke-web` (Settings trap)

В `karaoke-web` **нельзя** трогать `Settings.rootFolder` / любые `*NameFlac`-геттеры — они тянут
инициализацию `ConstantsKt`/`Connection` из `karaoke-app`, которой в karaoke-web нет →
`IllegalStateException`/`NoClassDefFoundError` роняет процесс на любой следующий запрос.
Читать стемы/картинки из karaoke-web — только через MinIO (`fetchFromMinIO`/`existsInMinIO`),
никогда через локальные пути.

Ловушка шире, чем только `rootFolder`: любое обращение к top-level `WORKING_DATABASE`/`ConstantsKt`
из нового karaoke-app кода рискованно в karaoke-web. Защита для словарей —
`TextFileDictionary.dict` обёрнут в `try { .. } catch (e: Throwable) { emptyList() }`
(важно `Throwable`, не `Exception` — `NoClassDefFoundError` это `Error`).

**Второй вариант — коллизия имён.** В `karaoke-web` есть СВОЙ
`com.svoemesto.karaokeweb.WORKING_DATABASE` (свой `Connection`, env-флаг
`WEB_WORK_ON_SERVER`) — использовать всегда именно его, никогда
`com.svoemesto.karaokeapp.WORKING_DATABASE` (тот на проде резолвится в LOCAL, т.к. флаги
karaoke-app там не выставлены). Аналогично `KSS_APP`/`SAC_APP` (lateinit из `karaoke-app.services`)
в karaoke-web ВСЕГДА не инициализированы — использовать конструкторные Spring-бины
`KaraokeStorageService`/`StorageApiClient` (как в `PublicPlaylistController`), не глобалы.

## MTU black-hole на проде

Любой исходящий HTTPS с прод-контейнера `karaoke-web` на внешний хост (MinIO `<MINIO_IP>`,
Yandex SmartCaptcha и т.п.) виснет из-за MTU-дропа больших TLS-пакетов. Обход: HTTP через
host-nginx под алиасом `minio-proxy` (env-переменная с дефолтом на прямой адрес + `location` в
`80to8897`). **Для любого нового внешнего API из karaoke-web — сразу закладывать этот паттерн.**
(Admin-машина не затронута.)

## URL-кодирование `?file=`

Любой `?file=<storageFileName>` **обязан** оборачивать значение в
`URLEncoder.encode(value, UTF_8)` — иначе `&`/`#` в имени автора/альбома («Чиж & Co») обрывают
query → 404, картинка молча не показывается. Серверные посегментные фетчи MinIO — не трогать
(кодируют посегментно).

## Jackson `is*`-поля

Kotlin `val isX: Boolean` Jackson сериализует в JSON-ключ **без** `is` (`"x"`). Фронт читает
`user.premium`, `dto.original` — не `isPremium`/`isOriginal` (иначе тихо `undefined`). Новый
`data class ..Dto` с булевым полем сразу называть без `is`-префикса.

## `apiGet`/`apiPost` vs `authGet`/`authPost`

**`apiGet`/`apiPost`** (karaoke-public) возвращают тело напрямую, НЕ `{status, body}`.
В отличие от `authGet`/`authPost` (`services/authApi.js`, для приватных
`/api/public/account/**`), публичный клиент `services/api.js` резолвит уже распарсенным JSON
без обёртки. Код по образцу `chatApi.js` (`const { status, body } = await fetchXxx()`), написанный
поверх `apiGet`, молча получает `status=undefined` и всегда уходит в ветку «ошибка/пусто» — без
ошибок в консоли, просто пустая лента.

## nullable-колонки в БД → nullable-поля в Kotlin

`KaraokeDbTable.loadList` бросает NPE на `SQL NULL`, если Kotlin-поле объявлено non-null.
**Nullable-колонки → nullable-поля в Kotlin.** Невозможно исправить на стороне загрузчика —
нельзя отличить "поле с null" от "поле не SELECT-нуто".

## KaraokeDbTable.save() и UNIQUE-конфликты

`KaraokeDbTable.save()` молча проглатывает UNIQUE-конфликты и другие SQLException
в `try/catch` вокруг `executeUpdate()`. Для сущностей с UNIQUE-индексом (`tbl_dictionaries`,
`tbl_site_users`) проверяйте конфликт ДО `save()` в контроллере.

## KaraokeDbTable.save() и большие таблицы

На больших таблицах (18k+ записей) `save()` с diff может быть медленным из-за reflection
(для каждого изменённого поля — отдельный `UPDATE` + recordhash-пересчёт + SSE-рассылка). Для
batch-операций используйте прямые SQL через `JdbcTemplate` или raw `Connection`, а не `save()`.

## recordhash-триггер (для sync)

`tbl_settings`/`tbl_dictionaries`/etc. — поле `recordhash VARCHAR(32)` + триггер на INSERT/UPDATE/
DELETE. **`UPDATE` без явного `recordhash = md5(...)` НЕ обновит hash** → `SyncTarget.listHashes()`
не увидит diff → sync молча игнорирует. Любой прямой SQL (`UPDATE` через `JdbcTemplate`,
миграция, ручной fix) должен вручную пересчитать `recordhash`. См. [docs/database.md](./database.md).

## Переиспользование `Statement` (Connection leak)

**Никогда не переиспользовать один `Statement` на несколько запросов** — `Statement` держит
курсор на ResultSet, и при попытке использовать тот же Statement на другом ResultSet возникает
`"Forward only ResultSet has been closed"` или silent data loss. Паттерн — один `Connection` +
один `Statement` + один `ResultSet` на запрос, закрывать в `finally`.

## См. также

- [docs/architecture-notes-archive.md](./architecture-notes-archive.md) — dated-история
- [CONTRIBUTING.md](../CONTRIBUTING.md) — правила оформления кода
- [docs/features/dual-db-sync.md](./features/dual-db-sync.md) — sync-механизм
