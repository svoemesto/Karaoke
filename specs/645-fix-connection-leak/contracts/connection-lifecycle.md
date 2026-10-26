# Contract: жизненный цикл JDBC-соединения потока

**Дата**: 2026-10-26 | **Спека**: [spec.md](../spec.md) | **План**: [plan.md](../plan.md)

Контракт описывает **внутренний API** (`karaoke-app`, модуль `karaoke-app`),
а не HTTP-контракт приложения: публичные HTTP-эндпоинты и схема БД не меняются
(FR-011).

## 1. Типы потоков и правило закрытия

| Тип потока | Примеры | `closeThreadConnection()` | Почему |
|---|---|---|---|
| **Одноразовый** | фоновые задачи API-контроллеров, поток очереди `KaraokeProcessWorker`, воркер `SyncAudioDescendants`, `KaraokeProcessThread`, потоки `ExportAlignmentDataset` / `StorageCacheWarm` / `CacheEtagSizeBackfill` / sync-related в `SongEditorController` | **ОБЯЗАТЕЛЬНО**, в `finally` | поток умирает и больше не позовёт `getConnection()` — соединение нечем переиспользовать, канал остаётся открытым до финализации сборщиком мусора |
| **Переиспользуемый** | пул потоков Tomcat (HTTP-запросы), `@Scheduled`, Spring-пулы | **ЗАПРЕЩЕНО** | закрытие разрушит thread-local кеш: следующий запрос откроет НОВОЕ физическое соединение (TCP + auth handshake) — churn и риск исчерпания `max_connections` |
| **Завершаемый пулом** | `Dispatchers.IO` / `Dispatchers.Default` (в прикладном коде их нет — см. research F-9), вытесненные потоки Tomcat | **не определено** — признанное ограничение | поток не знает, что он последний; закрыть изнутри нельзя, «снаружи» закрывать некому |

Компенсирующая мера ограничения — запас по лимиту соединений:
PostgreSQL поднимается с `max_connections=300` (`DB_MAX_CONNECTIONS` переопределяет).
Устранение ограничения — OpenProject #410 (выбор владельца: вариант A — точечный
фикс; пул соединений — отдельная задача).

## 2. `startOneShotThread` — канонический способ запуска

```kotlin
fun startOneShotThread(
    database: KaraokeConnection,
    name: String? = null,
    onFinally: () -> Unit = {},
    alsoClose: List<KaraokeConnection> = emptyList(),
    block: () -> Unit,
): Thread
```

| Параметр | Тип | Назначение |
|---|---|---|
| `database` | `KaraokeConnection` | соединение, thread-local кеш которого закрывается по завершении потока |
| `name` | `String?` | имя потока (thread-dump'ы, логи) |
| `onFinally` | `() -> Unit` | действие, выполняемое ВСЕГДА в `finally`, **до** закрытия соединения (снятие флагов «задача идёт») |
| `alsoClose` | `List<KaraokeConnection>` | **дополнительные** экземпляры БД, чьи thread-local соединения закрываются в том же `finally` — для потоков, которые ходят в две БД (LOCAL + SERVER) |
| `block` | `() -> Unit` | тело одноразовой задачи |

**Гарантии** (наследуются из реализации, контракт не меняется):

- тело выполняется **ровно один раз**;
- `onFinally()` вызывается **всегда** — и при успехе, и при исключении; ошибка
  `onFinally` не маскирует исходное исключение тела и не мешает закрытию
  соединения;
- `database.closeThreadConnection()` вызывается **всегда**, в том же `finally`,
  после `onFinally()`;
- `alsoClose[i].closeThreadConnection()` вызывается **всегда**, после
  `database.closeThreadConnection()`; ошибка закрытия одного не мешает закрытию
  остальных;
- поток — `daemon`, `start()` вызывается до возврата;
- возвращается запущенный `Thread` (для `join()` в тестах).

**Обратная совместимость**: параметр `alsoClose` — аддитивный, с значением по
умолчанию; все существующие вызовы (`startOneShotThread(db, "name") { … }` и
вызовы с именованными аргументами `database =`/`name =`/`onFinally =`)
компилируются без изменений. Публичные HTTP-контракты не затронуты (FR-011).

**Почему `alsoClose` нужен**: `KaraokeConnection` кэширует соединение в
**своём** `ThreadLocal`; каждый экземпляр (LOCAL / SERVER) имеет свой. Поток
sync-related в `SongEditorController` и поток очереди в `KaraokeProcessWorker`
ходят в обе БД — закрытия одного экземпляра недостаточно.

## 3. Запрещённые конструкции

В **прикладном коде** (`karaoke-app/src/main`, `karaoke-web/src/main`) запрещены
в файлах, обращающихся к БД:

| Запрещено | Пример |
|---|---|
| `kotlin.concurrent.thread` | `thread { … }`, `thread("name") { … }`, `kotlin.concurrent.thread { … }` |
| `java.lang.Thread { … }` (SAM-конструктор) | `Thread { dbWork() }` |

Исключения (не нарушение):

- `addShutdownHook(Thread { … })` — JVM shutdown hook, не фоновый рабочий поток;
- `Thread(…)` с non-SAM-аргументами — фабрика потока пула, долгоживущий
  consumer-поток;
- канонический `Thread(` внутри `services/OneShotThread.kt`;
- любой поток в файле, который **не обращается к БД**.

Автоматическая проверка: `karaoke-app/src/test/.../services/OneShotThreadWiringTest.kt`.

## 4. Диагностика исчерпания соединений

| Что | Как |
|---|---|
| Признак в логах | `FATAL: sorry, too many clients already` |
| Признак в приложении | очередь `KaraokeProcess` не отвечает (`GET /process/isworking` → `false`), сайт отдаёт 5xx |
| Проверка состояния БД | `SELECT count(*) FROM pg_stat_activity;` — сравнить с `max_connections` |
| Компенсирующая мера | `max_connections=300` (`DB_MAX_CONNECTIONS`) — страховка, не фикс |

Ответ «перезапустить БД» **не является** инструкцией: канал закрывается в
приложении (см. контракт выше), БД — только следствие.

## 5. Регламент запуска тестов

Тестовые потоки **тоже** оставляют соединения — ровно так же, как фоновые
операции. Поэтому перед длинным прогоном backend-тестов лимит соединений БД
должен иметь требуемый запас (`max_connections=300`, `DB_MAX_CONNECTIONS`):
иначе прогон падает с `sorry, too many clients already` — не по своей причине.
Проверка: `SELECT count(*) FROM pg_stat_activity;` — сравнить с `max_connections`.

Канонический текст — `knowledge/system/infra/ci-tools.md` (§ «Прогон
backend-тестов и лимит соединений БД»); ссылка оттуда — в
`knowledge/domains/persistence/domain.md`.

## 6. Ссылки

- ADR: `knowledge/adr/local-0009-thread-local-connection-lifecycle.md`
- SSoT домена: `knowledge/domains/persistence/domain.md`
- Эксплуатация: `knowledge/system/infra/deploy-overview.md`
- Регламент прогонов: `knowledge/system/infra/ci-tools.md`
- Per-feature: `docs/features/fix-connection-leak.md`
- Спека: `specs/645-fix-connection-leak/spec.md` (OpenProject #410)
