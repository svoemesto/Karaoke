# Устранение утечки соединений с БД (BR-627)

> **Status**: active
> **Feature Key**: fix-connection-leak
> **Last Updated**: 2026-10-26
> **Spec**: [specs/645-fix-connection-leak/spec.md](../../specs/645-fix-connection-leak/spec.md)
> **Issue**: OpenProject #410 (аудит #336, запись BR-627)
> **Branch**: `648-fix-connection-leak-impl`
> Per-feature документ (Constitution § VI FR-009).

## Что делает

Закрывает утечку физических JDBC-соединений: прикладной код открывает одно
физическое соединение на поток (`KaraokeConnection` кэширует его в `ThreadLocal`)
и не закрывает его, когда поток умирает. Канал к PostgreSQL остаётся жить до
недетерминированной финализации сборщиком мусора; при `max_connections` это
отказ БД для всего приложения (`sorry, too many clients already` → очередь
`KaraokeProcess` встаёт → сайт отдаёт 5xx).

Три слоя:

1. **Код** — четыре оставшихся фоновых потока, ходящих в БД, переведены на
   канонический `startOneShotThread` (`karaoke-app/.../services/OneShotThread.kt`),
   который закрывает thread-local соединение потока в `finally` при любом
   исходе (успех/исключение).
2. **Проверка** — `OneShotThreadWiringTest` больше не работает по фиксированному
   перечню файлов: она обходит **все** файлы прикладного кода, обращающиеся к
   БД, и требует в них канонического способа запуска. Проверка снабжена
   негативной фикстурой «до/после».
3. **Документация** — контракт соединения (правило закрытия по типам потока,
   признанное ограничение, компенсирующая мера, признак исчерпания и способ
   проверки числа соединений) собран в одном месте; регламент запуска тестов
   называет требуемый запас по лимиту соединений.

## Зачем

- **Отказ всей системы из-за одного класса дефекта.** Утечка соединений —
  единственная известная причина отказа **всей** системы, наблюдавшаяся в живом
  окружении: `too many clients already` → очередь заданий не отвечает →
  планировщики не читают БД → сайт отдаёт 5xx. Остальные находки аудита
  такого класса не давали.
- **Проверка не покрывала реальность.** Существующая регрессионная проверка
  смотрела на фиксированный перечень из двух файлов; три других файла с
  обращением к БД не проверялись никем — поэтому утечка и проскочила. Фикс
  без расширения проверки был бы точечной заплаткой: та же утечка вернулась бы
  через месяц в новом файле.
- **Ограничение по лимиту — страховка, а не решение.** `max_connections=300`
  отодвигает отказ БД, но не устраняет утечку каналов.

## Как работает

**Канонический способ.** `startOneShotThread(database, name, onFinally, alsoClose) { … }`:

- тело выполняется ровно один раз;
- `onFinally()` вызывается **всегда** (в `finally`, до закрытия соединения) —
  им снимаются флаги «задача идёт»;
- `database.closeThreadConnection()` вызывается **всегда** — и при успехе, и при
  исключении; при отсутствии соединения закрывать нечего, и метод выходит
  молча (edge case спеки: «падает до получения соединения»);
- `alsoClose[i].closeThreadConnection()` вызывается **всегда**, после основного
  закрытия. Параметр нужен, потому что у каждого экземпляра `KaraokeConnection`
  свой `ThreadLocal`-кеш, а поток может ходить в две БД (LOCAL + SERVER).

**Переведённые места** (все — файлы, обращающиеся к БД):

| Файл | Было | Стало |
|---|---|---|
| `karaoke-app/.../ExportAlignmentDataset.kt` | сырой `thread {` — соединение не закрывалось вовсе | `startOneShotThread(WORKING_DATABASE, "export-alignment-dataset")` |
| `karaoke-app/.../StorageCacheWarm.kt` | сырой `thread {` — соединение закрывалось вручную внутри тела | `startOneShotThread(database, "cache-warm")` |
| `karaoke-app/.../CacheEtagSizeBackfill.kt` | сырой `thread {` — то же | `startOneShotThread(Connection.local(), "cache-etag-size-backfill")` |
| `karaoke-app/.../controllers/SongEditorController.kt` | сырой `thread {` — соединение не закрывалось | `startOneShotThread(WORKING_DATABASE, "approve-sync-related", alsoClose = listOf(Connection.remote()))` |
| `karaoke-app/.../KaraokeProcessWorker.kt` | канонический способ, но соединение к SERVER не закрывалось | добавлен `alsoClose = listOf(Connection.remote())` |

**Проверка** (`karaoke-app/src/test/.../services/OneShotThreadWiringTest.kt` +
`RawThreadDbGuard.kt`):

1. обходятся все `.kt` в `karaoke-app/src/main/kotlin` и
   `karaoke-web/src/main/kotlin` (~420 файлов);
2. файл считается «ходящим в БД», если его **код** (без комментариев и
   строковых литералов) содержит маркер обращения к БД: `getConnection()`,
   прямой SQL (`prepareStatement`/`executeQuery`/`executeUpdate`/…),
   `Connection.local()/remote()/virtual()`, `WORKING_DATABASE`,
   `KaraokeConnection`, `KaraokeDbTable`, передача экземпляра БД (`database =`);
3. в таких файлах запрещены `kotlin.concurrent.thread` (в любой форме) и
   `java.lang.Thread { … }` (кроме `addShutdownHook(Thread { … })`);
4. нарушение показывается с указанием файла и номера строки.

Негативная фикстура «до/после»: копия **реального** файла с обращением к БД, в
которую внедрён сырой `thread {`, обязана быть найдена детектором (и указать
файл и строку); тот же файл без внедрения — нарушений не даёт. Сырой поток в
файле **без** обращений к БД нарушением не считается.

## Инварианты

1. Одноразовый фоновый поток приложения, которому нужен доступ к БД, запускается
   **только** через `startOneShotThread`.
2. Соединение одноразового потока закрывается детерминированно — при успехе и
   при исключении; при отсутствии соединения закрытие не логирует ошибку.
3. Поток, ходящий в две БД, закрывает соединения **обоих** экземпляров.
4. Соединение переиспользуемого потока (пул Tomcat, `@Scheduled`) остаётся в
   thread-local кеше — закрывать его нельзя (ADR `local-0009`).
5. В файлах прикладного кода, обращающихся к БД, нет сырых фоновых потоков;
   проверка охватывает такие файлы динамически, а не по фиксированному перечню.
6. Публичные HTTP-контракты и схема БД не меняются; миграция данных не нужна.
7. Работа с БД не переводится на ограниченный пул соединений (FR-010 спеки).

## Известные ловушки

- **`Thread { … }` — не то же, что `thread { … }`.** Kotlin регистрозависим:
  нижний `thread` — функция `kotlin.concurrent.thread` (запрещена в файлах с
  обращением к БД), верхний `Thread { … }` — SAM-конструктор
  `java.lang.Thread`. Проверка ловит оба, кроме JVM shutdown hook.
- **Закрывать соединение вручную внутри тела потока — не то же, что гарантированное
  закрытие.** В `StorageCacheWarm` и `CacheEtagSizeBackfill` соединение закрывалось
  через `use { }` в середине тела: при исключении до этой строки канал оставался
  висеть. Гарантия даёт только `finally` канонического хелпера.
- **Фоновый поток, ходящий в две БД, закрывает не то соединение.** Закрытие
  переданного экземпляра `KaraokeConnection` не закрывает thread-local кеш
  другого экземпляра (LOCAL ≠ SERVER) — отсюда параметр `alsoClose`.
- **Тестовые потоки тоже оставляют соединения.** Длинный прогон backend-тестов
  исчерпывает лимит локальной БД, и тест падает с `sorry, too many clients
  already` — не по своей причине. Перед прогоном — проверить
  `SELECT count(*) FROM pg_stat_activity;` и убедиться, что запас по лимиту
  (`max_connections=300`) есть.
- **Комментарии не считаются кодом.** В прикладном коде есть KDoc-упоминания
  `thread { … }` (например, в `KaraokeConnection.kt`) — проверка снимает
  комментарии перед разбором, иначе она давала бы ложные срабатывания.
- **«Проверка зелёная» ≠ «утечки нет».** Проверка статическая: она ловит
  запрещённую конструкцию, но не измеряет число занятых соединений на сервере
  БД. Измерение — отдельная процедура (см. `quickstart.md` спеки).

## Ссылки

- Спека: [specs/645-fix-connection-leak/spec.md](../../specs/645-fix-connection-leak/spec.md)
- План: [specs/645-fix-connection-leak/plan.md](../../specs/645-fix-connection-leak/plan.md)
- Отчёт: [specs/645-fix-connection-leak/report.md](../../specs/645-fix-connection-leak/report.md)
- Контракт жизненного цикла соединения:
  [specs/645-fix-connection-leak/contracts/connection-lifecycle.md](../../specs/645-fix-connection-leak/contracts/connection-lifecycle.md)
- SSoT домена: [knowledge/domains/persistence/domain.md](../../knowledge/domains/persistence/domain.md)
- ADR: [knowledge/adr/local-0009-thread-local-connection-lifecycle.md](../../knowledge/adr/local-0009-thread-local-connection-lifecycle.md)
- Регламент прогонов: [knowledge/system/infra/ci-tools.md](../../knowledge/system/infra/ci-tools.md)
- Эксплуатация: [knowledge/system/infra/deploy-overview.md](../../knowledge/system/infra/deploy-overview.md)
- Канонический хелпер: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/OneShotThread.kt`
- Проверка: `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/OneShotThreadWiringTest.kt`
- Детектор: `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/RawThreadDbGuard.kt`
