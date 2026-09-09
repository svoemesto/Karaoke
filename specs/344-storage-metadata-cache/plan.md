# Implementation Plan: Storage metadata cache (OpenProject #69)

**Branch**: `[344-storage-metadata-cache]` | **Date**: 2026-09-09 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/344-storage-metadata-cache/spec.md`

## Summary

In-memory TTL-cache для результатов `KaraokeStorageService.fileExists` / `fileIsActual` / `getFileInfo` (local MinIO) и `StorageApiClient.fileExists` / `fileIsActual` / `getFileInfo` (remote MinIO через nginx path-proxy). Цель — устранить 72k+ HTTP round-trip на странице Songs в webvue3 → загрузка страницы с минут на **< 5 секунд** при повторных открытиях в течение TTL (5 минут).

**Технический подход**:
- Копия готового `PollingCache<V>` (80 строк) из `karaoke-web` → `karaoke-app`.
- Новый бин `StorageMetadataCache` (`@Component`) с двумя `PollingCache` (local + remote) + `LongAdder` счётчики для метрик + SLF4J `infra.cache.storage`.
- Существующие интерфейсы `KaraokeStorageService` / `StorageApiClient` НЕ меняются (FR-011) — caller'ы (`HealthReport`) autowire `StorageMetadataCache` и оборачивают вызовы через `getOrCompute`.
- Конечная точка `GET /api/health/cacheStats` для метрик (admin-only).
- Юнит-тесты: `PollingCacheTest` (5 тестов, по образцу `HealthReportRepairRaceTest.kt`).

**Прецедент #339**: спека провалилась из-за изобретения БД-таблицы `storage_file_cache` (overengineering). В данной спеке используется готовый паттерн `PollingCache` из Knowledge.

## Technical Context

**Language/Version**: Kotlin 2.x, JVM (JDK 17). Module: `karaoke-app` (admin-side).
**Primary Dependencies**: spring-context 6.x (Spring Boot 3.x), `org.slf4j.Logger`, `org.slf4j.LoggerFactory`, `org.slf4j.MDC`, `java.util.concurrent.ConcurrentHashMap`, `java.util.concurrent.atomic.{AtomicLong, LongAdder}`. **NO new external dependencies** (Caffeine/Guava explicitly avoided per `web-caches.md#почему-не-caffeine-guava`).
**Storage**: in-memory only (no persistence; MinIO metadata is volatile by design — TTL-based).
**Testing**: JUnit 5 + kotlin.test (existing in `karaoke-app/src/test/kotlin`, pattern from `HealthReportRepairRaceTest.kt`).
**Target Platform**: Linux admin-machine (karaoke-app runs on admin only, per Constitution Principle IV).
**Project Type**: Backend library / Spring service (in `karaoke-app/.../services/`).
**Performance Goals**: p99 latency <1ms для cache hit (in-memory lookup); 50-100ms для cache miss (MinIO round-trip). См. SC-001.
**Constraints**: In-memory, maxEntries=50_000 default, TTL=300s default, no AOP, no new dependencies.
**Scale/Scope**: 18k песен × 4 типов × 2 источника = 144k max theoretical, но уникальных ключей значительно меньше (≤50k realistic). HTTP calls: 72k/page ДО, ~144/страница ПОСЛЕ (по одному на уникальный ключ), F5 в течение TTL — почти 0 calls.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Проверка | Pass? |
|---|---|---|
| **I. Self-contained pipeline** | Фича — in-memory cache для существующего MinIO, не вводит внешних API | ✅ |
| **II. Raw JDBC + diff** | Фича НЕ трогает persistence (никаких миграций) | ✅ |
| **III. SyncRegistry** | Фича НЕ трогает LOCAL↔SERVER sync (metadata cache = singleton, не per-instance) | ✅ |
| **IV. Async Process Queue** | Фича НЕ использует ProcessBuilder; `fileExists` остаётся блокирующим (существующий контракт) | ✅ |
| **V. Двух-фронтенд** | Фича живёт только в `karaoke-app` (admin-side); не трогает `webvue3` / `karaoke-public` | ✅ |
| **VI. Code Standards (FR-006/FR-009)** | KDoc + @see на per-feature документ обязателен; **TODO**: создать `docs/features/storage-metadata-cache.md` в том же PR (FR-009 — см. plan.md Tasks) | ✅ (с TODO) |
| **VII. Cross-Machine Setup** | Нет локальных AI-конфигов, .gitattributes нормализация не нужна (только Kotlin-файлы + 1 MD-обновление log-categories.md) | ✅ |
| **VIII. Secrets** | Cache key = `bucket/fileName` (не секрет). Нет хардкоженных credentials в коде. Logging — без секретов. | ✅ |
| **IX. Knowledge-first** | Knowledge-first pre-flight выполнен (см. spec.md § Knowledge References). Используется готовый `PollingCache<V>` (НЕ изобретена новая структура). | ✅ |

**Все 9 принципов passed. Constitution violations: NONE.**

## Project Structure

### Documentation (this feature)

```text
specs/344-storage-metadata-cache/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/
│   └── cache-stats-api.md    # Phase 1 output (/speckit.plan command)
├── checklists/
│   └── requirements.md  # /speckit.specify command output
├── spec.md              # /speckit.specify command output
└── tasks.md             # /speckit.tasks command output (NEXT STAGE)
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── services/
│   ├── PollingCache.kt                          # NEW — copy from karaoke-web (80 lines)
│   ├── StorageMetadataCache.kt                  # NEW — Spring @Component (150 lines)
│   ├── KaraokeStorageService.kt                 # UNCHANGED (FR-011: public API frozen)
│   ├── StorageApiClient.kt                      # UNCHANGED (FR-011)
│   └── HealthReport.kt                          # MODIFIED — autowire StorageMetadataCache, wrap calls in actionsLocalStorage/actionsRemoteStorage
└── controllers/
    └── CacheStatsController.kt                  # NEW — GET /api/health/cacheStats

karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/
└── PollingCacheTest.kt                          # NEW — 5 unit tests

karaoke-app/src/main/resources/
└── application.yml                              # EXTENDED — log level for infra.cache.storage

knowledge/domains/monitoring/components/
└── log-categories.md                            # EXTENDED — register infra.cache.storage

docs/features/
└── storage-metadata-cache.md                    # NEW — per-feature doc (FR-009, Constitution Principle VI)
```

**Structure Decision**: monorepo (мультимодуль). Все правки в `karaoke-app/` (admin-side). `karaoke-web/` НЕ трогаем. `webvue3/` и `karaoke-public/` НЕ трогаем. Knowledge (`knowledge/`) и per-feature docs (`docs/features/`) обновляются.

## Implementation Outline

> **ЭТО НЕ tasks.md** — это высокоуровневая разбивка для понимания, какие модули будут задеты. Конкретные шаги с file paths — в `tasks.md` (генерируется через `/speckit.tasks`).

### Шаг 1. PollingCache.kt copy

- **Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/PollingCache.kt` (80 строк).
- Содержимое: точно как `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/PollingCache.kt`, но пакет `com.svoemesto.karaokeapp.services` + KDoc со ссылкой на оригинал.

### Шаг 2. StorageMetadataCache.kt

- **Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageMetadataCache.kt` (~150 строк).
- `@Component`, конструктор с `@Value` для TTL.
- 3 публичных метода: `getFileExists`, `getFileIsActual`, `getFileInfo` — каждый принимает `source` + `bucket` + `fileName` + `loader`.
- `stats()` возвращает `CacheStatsDto`.
- `LongAdder` для thread-safe счётчиков.
- Logger — строковый `"infra.cache.storage"` (НЕ `LoggerFactory.getLogger(Class)`, см. ADR `local-0005`).

### Шаг 3. CacheStatsController.kt + DTO

- **Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/CacheStatsController.kt` (~30 строк).
- `CacheStatsDto` + `StatsBucket` — внутренние data classes в файле.
- `GET /api/health/cacheStats` → JSON.

### Шаг 4. HealthReport.kt integration

- **Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt` (2240 строк существующего кода).
- Autowire `StorageMetadataCache` в `companion object`.
- В `actionsLocalStorage` / `actionsRemoteStorage` обернуть прямые вызовы `fileExists` / `fileIsActual` / `getFileInfo` через кеш.
- **Минимальные изменения** (только 3-5 call-сайтов), сохраняя существующую логику.

### Шаг 5. PollingCacheTest.kt (юнит-тесты)

- **Файл**: `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/PollingCacheTest.kt` (~80 строк).
- 5 тестов: miss → hit → TTL expiry → concurrency → clear.
- **Не требует Spring-контекста** — простой `@Test fun`.

### Шаг 6. Knowledge update — регистрация SLF4J категории

- **Файл**: `knowledge/domains/monitoring/components/log-categories.md`.
- Добавить строку в таблицу Категории: `infra.cache.storage` → где `StorageMetadataCache.kt`, события `cache:hit/miss/evicted`, уровень INFO.

### Шаг 7. Per-feature документ (FR-009)

- **Файл**: `docs/features/storage-metadata-cache.md` (создать, ~80 строк).
- Содержимое: контекст, scope, API контракт (`StorageMetadataCache.getFileExists/isActual/fileInfo`), endpoint `/api/health/cacheStats`, метрики, edge cases, ловушки (5+).

### Шаг 8. application.yml — уровень логирования

- **Файл**: `karaoke-app/src/main/resources/application.yml`.
- Добавить: `logging.level.infra.cache.storage: INFO` (см. существующие logging:level комментарии в файле).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

**No violations.** Все 9 Constitution Principles passed, никаких обоснований не нужно.

## Rollback plan

Если на проде выявится критичный баг (например, race в cache key collision):

1. **Revert** merge commit.
2. Кеш не имеет миграций в БД — просто отключается.
3. `HealthReport.kt` возвращается к прямым вызовам без `getOrCompute`.
4. Проверить, что `logback-spring.xml` (не существует) или `application.yml` не содержат новых уровней для `infra.cache.storage`.

## Готовность к Tasks

✅ Plan заполнен.
✅ Phase 0 (research.md) — complete.
✅ Phase 1 (data-model.md, contracts/cache-stats-api.md, quickstart.md) — complete.
✅ Constitution Check — re-evaluated post-design, all PASS.

**Можно переходить к `/speckit.tasks`.**
