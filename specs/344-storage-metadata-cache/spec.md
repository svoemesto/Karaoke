# Feature Specification: Storage metadata cache

**Feature Branch**: `[344-storage-metadata-cache]`

**Created**: 2026-09-09

**Status**: Draft

**Input**: User description: "Работа над задачей #69 в трекере OpenProject (Кеширование информации из хранилища)"

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

> **Прецедент**: 2026-09-09, spec #339 — агент пропустил Knowledge-first
> pre-flight и изобрёл форму кеша (таблица `storage_file_cache` в БД) вместо
> паттернов из `knowledge/domains/caching/components/caching-patterns.md` и
> готового `PollingCache<V>` из `knowledge/domains/caching/components/web-caches.md`.
> Спека приведена в негодность, ветка удалена, NNN 339 освобождён.
>
> Без заполненной секции спека **НЕ ДОЛЖНА** переходить в
> `/speckit.plan`. См. `AGENTS.md` MUST #0, Constitution Principle IX.

### Pre-flight log

- **Дата pre-flight**: 2026-09-09
- **Author**: agent (Karaoke)
- **Grep-запросы** (минимум 3, по релевантным ключевым словам задачи):
  1. `grep -ri "caching" knowledge/` → `domains/caching/{domain.md, components/{caching-patterns,author-cache,web-caches}.md}`, `systems/frontend/...`, ADR не найдено (TODO)
  2. `grep -ri "fileExists\|fileIsActual\|getFileInfo" knowledge/` → `domains/storage/components/{karaoke-storage-service,storage-api-client}.md`, `domains/health/components/{health-report,race-fixed-65}.md`
  3. `grep -ri "health report\|HealthReport" knowledge/` → `domains/health/{domain.md, components/{health-report,race-fixed-65}.md}`, ADR не найдено
  4. `grep -ri "MinIO\|cache" knowledge/adr/` → `local-0003-shared-minio-image-cache.md` (готовый паттерн TTL+cleanup для image-cache)
  5. `grep -ri "infra\.cache\.\|infra\.cache" knowledge/` → `domains/caching/components/caching-patterns.md`, `domains/monitoring/components/log-categories.md` (готовая таблица SLF4J-категорий `infra.cache.*`)

### Knowledge files consulted

**Без знания следующих документов спека была бы невозможна**:

- [`knowledge/domains/caching/domain.md`](../../knowledge/domains/caching/domain.md)
  — зачем прочитан: определить Ubiquitous Language кеширования, инварианты (AtomicInteger, dirty-флаг, async cold-start, single-flight guard).

- [`knowledge/domains/caching/components/caching-patterns.md`](../../knowledge/domains/caching/components/caching-patterns.md)
  — зачем прочитан: каталог из **6 паттернов** (AtomicInteger / Cron / Dirty-flag / Денормализация / Async cold-start / Single-flight). Определяет, какие паттерны использовать и каких ловушек избегать.

- [`knowledge/domains/caching/components/web-caches.md`](../../knowledge/domains/caching/components/web-caches.md)
  — зачем прочитан: **готовые** потокобезопасные TTL-кеши `DedupCache` и `PollingCache<V>` в `karaoke-web/.../services/`. **Knowledge ЯВНО рекомендует `PollingCache<V>` для задачи #69** («Применимость к OpenProject #69»). Предыдущая попытка (#339) предлагала БД-таблицу `storage_file_cache` — Knowledge явно говорит, что это **overengineering**.

- [`knowledge/domains/health/domain.md`](../../knowledge/domains/health/domain.md)
  — зачем прочитан: Bounded Context Health (HealthReport), почему он делает «тяжёлые» запросы к MinIO, hot paths.

- [`knowledge/domains/health/components/health-report.md`](../../knowledge/domains/health/components/health-report.md)
  — зачем прочитан: понять `getHealthReportList` (точка входа), `actionsLocalStorage` / `actionsRemoteStorage` (где дёргается `fileExists`), `recomputeAndBroadcast` (когда инвалидация).

- [`knowledge/domains/health/components/race-fixed-65.md`](../../knowledge/domains/health/components/race-fixed-65.md)
  — зачем прочитан: уже сделанный Pass 343 fix для **repair-loop race** (perSong single-flight guard). 4/4 unit-тестов. Это смежная задача #65, фикс выполнен, но **`fileExists` race остаётся**.

- [`knowledge/domains/storage/domain.md`](../../knowledge/domains/storage/domain.md)
  — зачем прочитан: Bounded Context Storage, **Hot paths таблица** (4 строки с указанием, где MinIO-вызов болит и на каком OpenProject — #65 или #69).

- [`knowledge/domains/storage/components/karaoke-storage-service.md`](../../knowledge/domains/storage/components/karaoke-storage-service.md)
  — зачем прочитан: интерфейс `KaraokeStorageService` (admin-сторона), методы `fileExists`, `fileIsActual`, `getFileInfo`. `fileIsActual` сравнивает **только size**, не etag. OkHttpClient: `connectionPool(0, 1, NANOSECONDS)` — **намеренно no-keepalive** (защита от stale keepalive на nginx-прокси).

- [`knowledge/domains/storage/components/storage-api-client.md`](../../knowledge/domains/storage/components/storage-api-client.md)
  — зачем прочитан: интерфейс `StorageApiClient` (для remote MinIO через nginx-прокси). `fileExists` — **блокирующий `Boolean`**, ~50-100ms на проде. **`checkIfExists`** — bulk-метод, но реализация принимает один (bucket, name) (TODO: расширить для батча).

- [`knowledge/adr/local-0003-shared-minio-image-cache.md`](../../knowledge/adr/local-0003-shared-minio-image-cache.md)
  — зачем прочитан: **готовый ADR с TTL/scheduled-cleanup паттерном** для MinIO-кэша (image-cache: 7 дней). Применимо как background знание для проектирования storage-metadata cache, но сам кеш будет **in-memory** (не MinIO), потому что metadata — мелочь (etag+size), а не гигабайты.

- [`knowledge/adr/local-0005-structured-logging-karaoke-app.md`](../../knowledge/adr/local-0005-structured-logging-karaoke-app.md)
  — зачем прочитан: конвенция структурированного логирования в `karaoke-app` (MDC, structured key=value, уровни INFO/WARN/ERROR).

- [`knowledge/adr/local-0006-logging-and-error-handling-karaoke-web.md`](../../knowledge/adr/local-0006-logging-and-error-handling-karaoke-web.md)
  — зачем прочитан: конвенция для `karaoke-web` (HTTP-request-id через MDC, JSON error response). Не критично для этой спеки (кеш живёт в `karaoke-app`), но для согласованности — знать формат.

- [`knowledge/domains/monitoring/components/log-categories.md`](../../knowledge/domains/monitoring/components/log-categories.md)
  — зачем прочитан: реестр SLF4J-категорий `infra.*`. Нужно **зарегистрировать новую категорию `infra.cache.storage`** в таблице, иначе grep-логи будут невидимы для runbook.

### Если ничего не нашлось (явный no-op)

Не применимо — Knowledge полностью покрывает задачу #69, наоборот: **известно слишком много готовых паттернов, и явная ошибка — пытаться сделать иначе**.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Ускорение загрузки HealthReport и страницы Songs (Priority: P1)

**Описание**: Админ открывает страницу Songs в `webvue3` (18 000+ песен). webvue3 через `/api/health/getHealthReportList` дёргает `HealthReport.getHealthReportList(song)` для каждой песни. Каждый вызов делает ~4× `StorageApiClient.fileExists` HTTP round-trip (~50-100 ms каждый на проде через nginx-прокси). Итого 72 000+ HTTP-запросов на одной странице, загрузка занимает **минуты**.

После реализации спеки: первый запрос делает реальные HTTP round-trip, **последующие в течение TTL (5 минут) возвращают кешированный результат**.

**Why this priority**: Основной болевой путь, который блокирует продакшн-сценарии. Без этого webvue3 страница Songs «висит» и админ не может работать.

**Independent Test**: Создать искусственно 18k тестовых записей, замерить время загрузки Songs page ДО/ПОСЛЕ реализации. Ожидаемое ускорение ≥10× на повторных открытиях в течение TTL.

**Acceptance Scenarios**:

1. **Given** 18k песен с разнообразными MinIO-объектами, **When** админ открывает Songs page → ждёт полной загрузки → обновляет страницу (F5) в течение 5 минут, **Then** первый запрос делает реальные MinIO round-trip; второй **отдаёт кеш с p99 latency <10 ms на запрос** без HTTP round-trip.

2. **Given** кеш содержит результат `(bucket="karaoke", fileName="song-123.mp4") → exists=true`, **When** кто-то другой удалил этот объект на MinIO **за пределами Karaoke** (например, через `mc`), **Then** через ≤TTL (5 минут) UI увидит `exists=false` (после следующего cache miss, вызванного TTL-инвалидацией).

3. **Given** `karaoke-app` стартует с холодным кешем, **When** приходит первый запрос к `HealthReport.getHealthReportList`, **Then** запрос НЕ блокируется на cold-start refresh — данные получаются по требованию (per-key lazy load), первый запрос идёт к MinIO, последующие — из кеша.

---

### User Story 2 — Ускорение bulk-операций в HealthReport repair-loop (Priority: P1)

**Описание**: Repair-loop (`HealthReport.startRepairAll`) обрабатывает тысячи песен. Каждая итерация — `executeResolvable`, который делает несколько `fileExists` / `fileIsActual` вызовов на одну песню. До кеша — каждый вызов идёт через HTTP. С кешем — повторные `(bucket, fileName)` возвращаются за <1 ms.

**Why this priority**: Repair-loop уже был частично починен от race (Pass 343, perSong single-flight guard), но не от медленных MinIO round-trip. Repair-loop — основной потребитель `actionsRemoteStorage` / `actionsLocalStorage`.

**Independent Test**: Запустить `startRepairAll` на 1k песен, замерить общее время. С кешем для уже проверенных `(bucket, fileName)` — ожидается существенное ускорение на повторных проходах (например, при retry).

**Acceptance Scenarios**:

1. **Given** Repair-loop обрабатывает песню #1, потом встречает ту же `(bucket="karaoke", fileName="...")` для другой песни, **When** обращение к кешу, **Then** ответ возвращается из in-memory без HTTP round-trip, время <1 ms (только ConcurrentHashMap lookup).

2. **Given** Repair-loop активен, **When** параллельно приходит запрос от webvue3 (UI), **Then** оба потока читают/пишут кеш **потокобезопасно** (без `ConcurrentModificationException`).

---

### User Story 3 — Метрики кеша для мониторинга (Priority: P2)

**Описание**: Админ хочет видеть через `/api/health/cacheStats` или runbook-grep размер кеша, hit-rate, miss-rate. Это позволяет принимать решения о тюнинге TTL.

**Why this priority**: Без метрик невозможно понять, работает ли фича. SC-002 (см. ниже) — измеримый критерий успеха.

**Independent Test**: Загрузить Songs page, подождать reload, посмотреть `/api/health/cacheStats` — `hit_count > 0`, `miss_count = n_unique_keys`.

**Acceptance Scenarios**:

1. **Given** кеш работает, **When** админ открывает `/api/health/cacheStats`, **Then** JSON содержит: `entries: <count>`, `hits: <count>`, `misses: <count>`, `hitRatio: <float>`, `configuredTtlSeconds: 300`.

2. **Given** в `logback-spring.xml` категория `infra.cache.storage` установлена на INFO, **When** происходит cache miss на cold-start, **Then** в `/var/log/karaoke-app.log` появляется строка с маркером `infra.cache.storage` (например, `cache:miss key=... bucket=...`) для грепа.

---

### User Story 4 — Persisted (write-through) вариант для долгоживущего кеша (Priority: P3)

**Описание**: Если нагрузка покажет, что 5 минут TTL мало (cold-start после рестарта `karaoke-app` создаёт burst MinIO round-trip), кеш можно расширить write-through в БД (Postgres `storage_metadata_cache` таблицу). Это полностью опциональное расширение.

**Why this priority**: P3, потому что in-memory TTL покрывает основную боль (P1). Persisted — бонус для cold-start. В рамках спеки #344 НЕ реализуется, но спека фиксирует интерфейс, чтобы добавить позже без breaking change.

**Independent Test**: Не входит в acceptance спеки #344; фиксируется только в «Assumptions».

**Acceptance Scenarios**: Не применимо для первого раунда.

---

### Edge Cases

- **TTL = 0**: TTL должен быть >0, иначе каждый запрос = miss → деградация.
- **TTL очень большой** (1 сутки): stale data при ручном удалении файла мимо Karaoke. Допустимо, но противоречит «single source of truth» — UI может показывать OK для удалённого файла. Выбран разумный дефолт (5 минут).
- **MinIO недоступен при cache miss**: loader падает → ошибка пробрасывается caller'у (НЕ маскируется в кеше). `fileExists → null` или `Exception` — НЕ кешируется (только успешные `Boolean` / `StorageFileInfo`).
- **Имя файла содержит URL-encoded символы** (`%`, `%20`): key — после `decodeFileNameIfEncoded` (для единообразия), иначе `песня%20с%20ёжиком.flac` и `песня с ёжиком.flac` дадут **разные ключи** → бессмысленный кеш.
- **Cache key collision между локальным и удалённым MinIO**: `("karaoke", "song-1.mp4")` локально, `("karaoke", "song-1.mp4")` удалённо — разные значения существования. Решение: ключ включает **источник**: `"LOCAL:karaoke/song-1.mp4"` vs `"REMOTE:karaoke/song-1.mp4"`.
- **`fileExists` race (#65)**: cache не устраняет root race condition, но добавляет single-flight (один loader = один MinIO-запрос; параллельные вызовы ждут ответ в рамках одного `PollingCache.getOrCompute`). Реальный race fix — отдельная задача (#65).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST кешировать in-memory результаты `KaraokeStorageService.fileExists(bucket, name)`, `KaraokeStorageService.fileIsActual(bucket, name, …)`, `KaraokeStorageService.getFileInfo(bucket, name)` в `karaoke-app`. Кеш живёт в новом бине `StorageMetadataCache`.

- **FR-002**: System MUST кешировать in-memory результаты `StorageApiClient.fileExists(bucket, name)`, `StorageApiClient.fileIsActual(bucket, name, …)`, `StorageApiClient.getFileInfo(bucket, name)` в `karaoke-app`. Локальный и удалённый MinIO кешируются **отдельными сущностями** (`storageMetadataCache.local` vs `storageMetadataCache.remote`) с разными конфигурациями TTL.

- **FR-003**: TTL по умолчанию для in-memory кеша MUST быть **300 секунд (5 минут)** для обоих кешей (local + remote). Конфигурируется через `KaraokeProperties`:
  - `storageMetadataCache.local.ttlSeconds: Long = 300`
  - `storageMetadataCache.remote.ttlSeconds: Long = 300`

- **FR-004**: Cache key MUST формироваться как `"$source:$bucket/$fileName"` или `"$source:$bucket/$fileName:$operation"` (где `source ∈ {LOCAL, REMOTE}`, `operation ∈ {fileExists, fileIsActual, getFileInfo}`). Это исключает коллизии между local/remote и разными операциями.

- **FR-005**: При cache miss (отсутствует ключ или истёк TTL) MUST вызываться loader (`() -> T`), результат сохраняется с TTL, возвращается caller'у. Loader выполняется **в caller thread** (без single-flight guard) — параллельные одинаковые miss допустимы для metadata (одна MinIO round-trip ≈ 50-100ms, race редок).

- **FR-006**: System MUST НЕ кешировать **исключения** / **ошибки** loader'а. Только успешные `Boolean` / `StorageFileInfo` / `Boolean` (`fileIsActual`). Исключение пробрасывается caller'у без изменения кеша.

- **FR-007**: System MUST логировать cache miss, cache hit, eviction через категорию `infra.cache.storage` (новая SLF4J-категория, регистрируется в `knowledge/domains/monitoring/components/log-categories.md`):
  - `INFO cache:hit key="..." bucket="..." fileName="..." source=LOCAL`
  - `INFO cache:miss key="..." bucket="..." fileName="..." source=REMOTE durationMs=80`
  - `INFO cache:evicted key="..." bucket="..." fileName="..." reason=ttl`
  - Уровень по умолчанию в `logback-spring.xml`: `INFO`.

- **FR-008**: System MUST экспонировать метрики кеша через admin endpoint `/api/health/cacheStats` (GET, JSON):
  ```json
  {
    "local": {"entries": 1234, "hits": 5678, "misses": 42, "hitRatio": 0.99, "ttlSeconds": 300, "evictions": 10},
    "remote": {"entries": 2345, "hits": 9876, "misses": 123, "hitRatio": 0.99, "ttlSeconds": 300, "evictions": 15}
  }
  ```
  Этот endpoint добавляется в существующий admin API `karaoke-app` рядом с `/api/health/check`.

- **FR-009**: System MUST использовать готовый паттерн `PollingCache<V>` из `karaoke-web/.../services/PollingCache.kt` как **шаблон**. Поскольку `PollingCache` живёт в `karaoke-web`, а кеш нужен в `karaoke-app`, файл **копируется** в `karaoke-app/.../services/PollingCache.kt` (с KDoc-ссылкой на оригинал и указанием, что это локальная копия с тем же контрактом). **НЕ создаётся новая структура или БД-таблица** (прецедент #339).

- **FR-010**: System MUST зарегистрировать новую SLF4J-категорию `infra.cache.storage` в `knowledge/domains/monitoring/components/log-categories.md` (строка таблицы).

- **FR-011**: System MUST НЕ изменять сигнатуру публичных методов `KaraokeStorageService` и `StorageApiClient`. Кеш живёт **прозрачно** — caller'ы вызывают те же методы, кеш подключается через Spring-бин wrapping или AOP (детали в plan.md, не в этой спеке).

- **FR-012**: System MUST обрабатывать URL-encoded имена файлов через `decodeFileNameIfEncoded` ДО формирования cache key. Это гарантирует единый ключ для `песня%20с%20ёжиком.flac` и `песня с ёжиком.flac`.

- **FR-013**: System MUST кешировать ТОЛЬКО результаты **blocking-методов** (`Boolean` и `StorageFileInfo` напрямую). Никогда не использовать `reactor.core.publisher.Mono.block()` / `Mono.toFuture().get()` внутри cache loader — это блокирует event-loop и усугубляет race-condition #65. Кеш подключается **caller-side** через явный Spring-autowire `StorageMetadataCache` в `HealthReport.actionsLocalStorage` / `actionsRemoteStorage` и оборачивает blocking-вызовы. Никаких AOP/Spring-proxy — Assumption 7 в `spec.md § Assumptions`.

- **FR-014**: System MUST уметь жить **cold-start без блокировки первого HTTP-запроса**. Кеш ленив (lazy load), без прогрева. Первый miss → MinIO round-trip, caller ждёт ответа (как раньше, без кеша). Это соответствует архитектурному решению из `knowledge/domains/health/domain.md` (Repository pattern: MinIO-операции НЕ выполняются в HTTP-handler-треде для пользовательских запросов через KaraokeProcess, для metadata — допустимо, потому что fileExists BLOCKING — это существующий паттерн #69).

### Non-Functional Requirements

- **NFR-001**: Производительность — `fileExists` через кеш MUST возвращать ответ **< 1 ms p99** для in-memory ConcurrentHashMap lookup (без MinIO round-trip).
- **NFR-002**: Memory footprint — кеш MUST ограничиваться разумным размером по умолчанию (`maxEntries = 50_000`). При превышении — eviction по LRU или FIFO (деталь в plan.md, не в этой спеке). На 18k песен × 4 типов × 2 источника = 144k потенциальных entries, но реально из них уникальных ключей значительно меньше (≤50k разумная оценка).
- **NFR-003**: Thread safety — все операции с кешем MUST быть потокобезопасны (ConcurrentHashMap, атомарные счётчики hit/miss через `LongAdder`).

### Key Entities *(include if feature involves data)*

- **`StorageMetadataCache`** — новый бин в `karaoke-app/.../services/StorageMetadataCache.kt`. Содержит два `PollingCache`-подобных instance: `local: PollingCache<CacheResult>` и `remote: PollingCache<CacheResult>`. Метрики: `LocalStats` / `RemoteStats` (entries, hits, misses, evictions, configuredTtlSeconds).

- **`CacheResult`** — sealed class для операций `Boolean` (для `fileExists` / `fileIsActual`) и `StorageFileInfo` (для `getFileInfo`). Структура фиксируется в `data-model.md § 2` и `tasks.md (T005)`. Альтернатива (`data class OperationResult<T>` с nullable-полями) — **отвергнута** как overengineering: nullable-поля не передают семантику операции и плохо сериализуются.

  ```kotlin
  sealed class CacheResult {
      data class BooleanResult(val value: Boolean) : CacheResult()        // fileExists / fileIsActual
      data class FileInfoResult(val value: StorageFileInfo) : CacheResult()  // getFileInfo
  }
  ```

- **`StorageMetadataCacheProperties`** — подсекция `KaraokeProperties` (см. FR-003): `local.ttlSeconds: Long`, `remote.ttlSeconds: Long`, `maxEntries: Int`. (Отметка: `KaraokeProperties` НЕ расширяется — см. Tasks T005: используем `@Value` напрямую, чтобы не трогать 2240-строчный файл.)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Загрузка страницы Songs в webvue3 (18k песен) на второй повтор в течение 5 минут занимает **≤ 5 секунд** (среднее время < 100 ms на запрос к `/api/health/getHealthReportList`). ДО реализации — та же страница занимала **минуты** (>300 секунд). **Улучшение ≥ 60×**.

- **SC-002**: На повторных запросах к `HealthReport.getHealthReportList` в течение TTL hit-rate по `/api/health/cacheStats` ≥ **90%** для уже посещённых ключей. Достигается за счёт детерминированных ключей на основе `(source, bucket, fileName)`.

- **SC-003**: Cold start `karaoke-app` НЕ вызывает burst MinIO round-trip — кеш ленив. Первый запрос — miss, последующие — hit. Метрика: после первого запроса hit/miss ratio растёт монотонно в течение первой минуты.

- **SC-004**: Все 72k MinIO round-trip на странице Songs заменяются на in-memory lookup через 5-минутный TTL. Логи `infra.cache.storage` НЕ показывают ошибок (`grep "ERROR"` пусто) в течение 24 часов на тестовом прогоне.

- **SC-005**: Метрика операционная: после релиза `grep "infra.cache.storage" /var/log/karaoke-app.log` возвращает записи о `cache:hit`, `cache:miss`, `cache:evicted` для diagnostics.

## Assumptions

1. **TTL = 300 секунд** разумный дефолт на основе существующих polling-кешей `PublicNewsController` (TTL=60s) и `PublicChatController` (TTL=10s). HealthReport — менее критичный к свежести (визуальный UI), чем player'ы; 5 минут — компромисс.
2. **Local cache меньше нагружен, чем remote** (local MinIO — 10-30 ms round-trip, remote через nginx — 50-100 ms). Можно в plan.md рассмотреть TTL_local = 60s, TTL_remote = 300s, но в этой спеке — единый дефолт для простоты.
3. **Cold-start допустим**. Метрики по cold-start доступны через отдельный endpoint (см. Tasks в `/speckit.tasks`).
4. **`fileExists` race condition (#65) НЕ чинится этой спекой**. Кеш не устраняет root cause (race в HTTP-запросе), но смягчает последствия (повторные miss пойдут в короткое окно после cold-start). Полный fix #65 — отдельная задача.
5. **`checkIfExists` (bulk) НЕ расширяется в этой спеке**. Это опциональная оптимизация для будущего. Текущая реализация `Mono<Map<String, Boolean>>` принимает одну пару `(bucket, name)`; превращение в реальный bulk — отдельная задача (см. `storage-api-client.md#known-gaps`).
6. **`PollingCache` в `karaoke-app`** — **копия** из `karaoke-web`. Не выделяем общий модуль (overengineering для 80 строк). KDoc ссылается на источник.
7. **`StorageMetadataCache` подключается в `HealthReport.actionsLocalStorage` / `actionsRemoteStorage`** через DI Spring (autowire `StorageMetadataCache`, вызов перед прямого вызова `fileExists` / `getFileInfo`). **НЕ через AOP** — AOP скрывает flow и затрудняет debug (см. ADR `local-0006` § Alternatives: «Aspect / AOP logging: rejected»). Решение переиспользовать те же принципы — без AOP.
8. **Write-through persistence (P3 user story)** — НЕ в этой спеке, но интерфейс `StorageMetadataCache` проектируется так, чтобы потом можно было добавить `BatchingStorageMetadataCache` без breaking change (открытый `interface` + Spring DI swap).
