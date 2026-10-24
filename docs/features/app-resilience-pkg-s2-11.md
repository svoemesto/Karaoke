# Устойчивость admin-приложения: 15 записей аудита (BR-110…BR-176)

> **Status**: active
> **Feature Key**: app-resilience-pkg-s2-11
> **Last Updated**: 2026-10-24
> **Spec**: [specs/374-pkg-s2-11/report.md](../../specs/374-pkg-s2-11/report.md)
> **Issue**: OpenProject #374 (пакет `PKG-S2-11`, волна D)
> **Branch**: `596-pkg-s2-11`
> Per-feature документ (Constitution § VI FR-009).

## Что делает

Устраняет 15 дефектов S2 в `karaoke-app`, найденных аудитом #336 (порции
P-08, P-16, P-19, P-21, P-22, P-23, P-24): медленные утечки памяти и потоков,
потерю данных при записи в БД, неработающую диагностику и потерю результата
платной функции при сбое хранилища. Поведение при ошибках становится
fail-closed: вызывающий получает исключение/явный статус, а не «успех».

## Зачем

Каждый дефект либо накапливался до OOM/исчерпания дескрипторов в долгоживущем
контейнере (BR-110, BR-111, BR-113, BR-132, BR-142), либо молча терял данные
(BR-128, BR-133, BR-139, BR-141, BR-176), либо не работал вовсе (BR-140,
BR-143), либо превращал необязательную операцию в отказ старта (BR-162).
Severity S2: отказ или потеря данных на штатном сценарии, обход — рестарт или
ручное вмешательство.

## Как работает

| BR | Файл | Что изменилось |
|---|---|---|
| BR-110 | `WaveformCompare.kt` | LRU-кэш огибающих на 64 записи; при смене mtime прежний ключ того же файла удаляется |
| BR-111 | `services/cleanup/ArtifactCleanupService.kt` | `plans`/`runs` — LRU (32/128) + TTL 30 мин; отчёт удаляется после терминального `apply` |
| BR-112 | `controllers/ListeningHistoryController.kt` | фильтр/сортировка/пагинация в SQL (`EXISTS`, `ORDER BY`, `LIMIT/OFFSET`, `COUNT(*)`); песни — только для страницы |
| BR-113 | `UtilsPlaywright.kt` | `AuthFlowGate`: локальный latch, `await(10 мин)`, закрытие контекста по таймауту |
| BR-128 | `model/SongAssignment.kt`, `PublicApiController.kt` | `loadBySongIds` → `Map<Long, List<SongAssignment>>`; детерминированный выбор (open, затем max id); публичная карточка ищет назначение текущего редактора |
| BR-132 | `services/HealthReportBatchPool.kt` | воркеры запускаются один раз; побудка — `Semaphore`, не `submit { workerLoop() }` |
| BR-133 | `propertiesfiledictionary/PropertiesFileDictionary.kt` | Base64-значения, атомарная запись под lock, устойчивый разбор (битая строка → WARN) |
| BR-139 | `services/TelegramAutoPublishService.kt` | `saveWithRetry` (3 попытки); потеря `message_id` → `SEND_FAILED` с явной ошибкой |
| BR-140 | `services/TelegramApiClient.kt` | каждая попытка `sendVideo` в `try/catch`; сеть/таймаут/не-JSON — retryable; исходная ошибка как `cause` |
| BR-141 | `services/StorageApiClient.kt` | `uploadFile(path)` бросает исключение; нет файла и сбой загрузки различимы |
| BR-142 | `services/TelegramApiClient.kt` | прокси-`HttpClient` кэшируется по `telegramProxyUrl` |
| BR-143 | `services/StatsDebugController.kt` | DTO отдаётся как `ResponseEntity<StatsDebugDto>`, без `as Map` |
| BR-148 | `VkAutoPublishService.kt`, `VkPreviewWarmupClient.kt`, `VkPhotoUploadClient.kt` | изменяемые настройки читаются на каждом вызове |
| BR-162 | `KaraokeStorageService.kt`, `KaraokeAppService.kt` | уборка только бакетов `karaoke`/`stemjobs`; сбой уборки при старте → WARN, не отказ контекста |
| BR-176 | `model/KaraokeDbTable.kt` | `null` биндится как типизированный SQL NULL; ошибка `executeUpdate()` пробрасывается |

Общие принципы:

1. **Fail-closed.** Ошибка хранилища/БД/сети не превращается в `null`, `false`
   без последствий или `200 OK`: вызывающий получает исключение либо явный
   статус `SEND_FAILED`/`ERROR`.
2. **Ограниченные коллекции.** Долгоживущие кэши и реестры имеют верхнюю границу
   (LRU, TTL, лимит по числу воркеров).
3. **Один владелец ресурса.** JDBC-соединение — в `ThreadLocal` потока,
   `HttpClient`/`BrowserContext` переиспользуются или закрываются в `finally`.

## Инварианты

- `WaveformCompare.envelopeCache.size <= 64`; ключ включает mtime, старый ключ
  того же пути удаляется.
- `ArtifactCleanupService`: одновременно доступно не более 32 отчётов; после
  `DONE|PARTIAL|FAILED` отчёт удалён; `apply` работает только с утверждённым
  отпечатком.
- `ListeningHistoryController.digest`: число прочитанных строк истории не
  зависит от размера таблицы (LIMIT/OFFSET в SQL), `totalCount` считается
  отдельным `COUNT(*)`.
- `AuthFlowGate`: в один момент времени не более одного auth-flow; ожидание
  завершается не позже 10 минут.
- `SongAssignment.loadBySongIds`: порядок каждого списка детерминирован;
  `composeStatusesForSongIds` возвращает одну строку на песню.
- `HealthReportBatchPool`: после `start()` новые `workerLoop` не submit'ятся;
  число разрешений семафора не больше `POOL_SIZE`/`POOL_SIZE_WAITING`.
- `PropertiesFileDictionary.set`: значение с `\n`/`\r`/`{SEP}` не ломает файл;
  чтение переживает одну битую строку.
- `TelegramAutoPublishService`: успешная отправка без сохранённого
  `message_id` не возвращается как `PUBLISHED`.
- `TelegramApiClient.sendVideo`: сетевые исключения ретраятся по
  `backoffScheduleMs`; на исчерпании — `SEND_FAILED`.
- `StorageApiClient.uploadFile(path)`: `null` не возвращается.
- `StatsDebugController.debug()`: не бросает `ClassCastException`.
- `KaraokeStorageService.deleteAllEmptyBuckets`: не удаляет бакеты вне
  `KARAOKE_STORAGE_BUCKETS`.
- `KaraokeAppService.init`: сбой уборки бакетов не мешает старту.
- `KaraokeDbTable.save()`: nullable-поле пишется как SQL NULL; ошибка UPDATE
  не маскируется.

## Известные ловушки

- **`git stash` в этом репозитории общий для всех worktree.** Параллельный
  агент может сдвинуть стек stash между `push` и `pop` — так можно потерять
  чужие/свои правки. Для «до/после» используйте отдельный worktree, а не stash.
- **`-Djava.awt.headless=true` не пробрасывается в test-JVM.** На машине с
  невалидным `DISPLAY=:0` тесты `VkPreviewWarmupClientTest` падают с
  `AWTError`; запускайте с пустым `DISPLAY` (или под xvfb). Это окружение,
  а не дефект кода.
- **`KaraokeDbTable.loadList(limit = 0)` = без LIMIT.** Пагинация в Kotlin
  после полной загрузки не ограничивает ни память, ни время.
- **`saveToDbLocked()` возвращает `false` штатно** при `lock_timeout` (FR-060).
  Игнорировать `Boolean` нельзя: это предусмотренная ветка, а не «редкий сбой».
- **`workerLoop()` в `HealthReportBatchPool` — бесконечный цикл.** Любой
  `submit { workerLoop() }` на событие занимает поток навсегда и растит
  неограниченную очередь executor'а.
- **`proxyClient()` создаёт новый `HttpClient` с selector-потоком.** Без
  кэширования в proxy-режиме потоки/сокеты копятся каждые ~25-60 с.
- **`null` в `RecordDiff` нельзя биндить через `setString`.** Для
  `timestamp`/`boolean` PostgreSQL вернёт ошибку, и `save()` потеряет весь
  пакет правок, а не одно поле.
- **`/api/setwebvueprop` доступен без аутентификации** (admin-периметр по
  AGENTS.md). Значение с переводом строки — легальный ввод; кодирование
  обязательно.
- **Несколько назначений на песню — норма.** UNIQUE только по
  `(song_id, assignee_id)`; `associateBy { songId }` выбирает произвольную
  строку.
- **Уборка пустых бакетов при старте — необязательная операция.** Сетевой сбой
  MinIO не должен валить Spring-контекст; `MinioException` ≠ `IOException`.

## Ссылки

- [specs/374-pkg-s2-11/report.md](../../specs/374-pkg-s2-11/report.md) — отчёт
  пакета, таблица «до/после», команды проверок.
- [specs/336-code-audit/backlog.md](../../specs/336-code-audit/backlog.md) —
  блок `PKG-S2-11`.
- [knowledge/domains/persistence/domain.md](../../knowledge/domains/persistence/domain.md),
  [knowledge/domains/storage/domain.md](../../knowledge/domains/storage/domain.md),
  [knowledge/domains/publishing/components/publishing-services.md](../../knowledge/domains/publishing/components/publishing-services.md).
- [ADR local-0002](../../knowledge/adr/local-0002-save-exception-handling.md) —
  ошибки БД не подменяются пустым результатом.
- [ADR 0001](../../knowledge/adr/0001-raw-jdbc.md) — сырой JDBC, соединение в
  `ThreadLocal`.
