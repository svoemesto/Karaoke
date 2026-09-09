# Research: Storage metadata cache (OpenProject #69)

**Phase**: 0 — Outline & Research
**Date**: 2026-09-09
**Owner**: agent (Karaoke)
**Spec**: [spec.md](./spec.md)

## Контекст

Задача OpenProject #69 «Кеширование информации из хранилища». Главный горячий путь: `HealthReport.getHealthReportList(song)` (в `karaoke-app/HealthReport.kt:1119`) для каждой песни делает ~4× `StorageApiClient.fileExists` HTTP round-trip через nginx path-proxy (50-100 ms каждый на проде). При 18k песен на странице Songs в webvue3 это 72k+ HTTP-запросов — загрузка занимает минуты.

Прецедент #339 (2026-09-09): агент пропустил Knowledge-first и изобрёл форму кеша (таблица `storage_file_cache` в Postgres) вместо использования готового `PollingCache<V>`. Спека провалена, ветка удалена, NNN 339 освобождён.

## Resolved questions (из спеки)

Все 8 пунктов Assumptions уже зафиксированы в spec.md и подкреплены Knowledge. Здесь повторяю ключевые решения:

### Decision: In-memory TTL cache на основе готового `PollingCache<V>`

**Rationale**:
- `knowledge/domains/caching/components/web-caches.md` явно говорит:
  > «**Кеш метаданных MinIO (задача #69)** — **`PollingCache<V>`** — лучший fit».
  И отдельно: «NB: предыдущая спека #339 предлагала БД-таблицу `storage_file_cache` — это **overengineering**».
- `PollingCache<V>` уже работает в `karaoke-web/.../services/` для 3 production endpoints (`PublicNewsController`, `PublicChatController`, `PublicShareController`). Контракт проверен, lazy cleanup реализован.
- Caffeine/Guava сознательно НЕ используются (см. KDoc `PollingCache.kt:46-49`): "намеренный минимум зависимостей, ConcurrentHashMap достаточен для ~30 req/min".

**Alternatives considered**:

| Option | Pros | Cons | Verdict |
|---|---|---|---|
| `PollingCache<V>` (готовый) | Проверен в прод, generic, lazy cleanup, zero deps | 80 строк (копия из web) | ✅ **Chosen** |
| Caffeine cache | Более мощный API, LRU eviction, статистика | Новая зависимость, overengineering для 30 req/min | ❌ |
| Postgres `storage_file_cache` table | Persistence across restart | Overengineering, добавляет таблицу и миграцию; cold-start burst всё равно возможен при массовых удалениях | ❌ (прецедент #339) |
| Redis | Distributed cache | Отдельная инфраструктура, проект не использует Redis (Constitution) | ❌ |
| TTL=0 (no cache) | Simpler | 72k round-trip, UX «висит» — задача не решена | ❌ |

**Notes**:
- Копирование `PollingCache.kt` из `karaoke-web` в `karaoke-app` — намеренный workaround. Универсальное решение (выделение в shared модуль) — overengineering для 80 строк, оставлено на будущее (Pass 343+ TODO в `web-caches.md#known-gaps`).
- KDoc-ссылка на источник обязательна (Principle VI FR-006: «публичные API MUST сопровождаться KDoc с @see на per-feature документ»).

### Decision: TTL = 300 секунд для обоих кешей (local + remote)

**Rationale**:
- HealthReport — UI-индикатор, не критичный для воспроизведения песен (player использует прямые `getFileUrl` через nginx-proxy GET, не зависит от кеша метаданных).
- Существующие polling-кеши (Pass 341): `PublicNewsController` — TTL=60s (новости), `PublicChatController` — TTL=10s (UX бейдж), `PublicShareController` — TTL=15s (heartbeat). HealthReport — менее критичный, чем player-UX → 5 минут разумно.
- Local MinIO round-trip 10-30ms, remote 50-100ms. Local менее нуждается в кеше, но единый TTL упрощает конфигурацию.
- Настраивается через `KaraokeProperties` или `application.yml` (`storage.metadata.cache.local.ttlSeconds: 300`).

**Alternatives considered**:

| Option | Pros | Cons | Verdict |
|---|---|---|---|
| `local=60s, remote=300s` (split) | Точнее под нагрузку | Больше параметров конфигурации, избыточно | ⚪ deferred |
| `local=remote=300s` (uniform) | Простая конфигурация, OK для UI | Чуть избыточно для local | ✅ **Chosen** |
| `local=60s, remote=60s` | Короткий TTL, свежее | UI-UX спорный: пользователь успеет F5-нуть между итерациями | ❌ |

### Decision: TTL-only invalidation (без active invalidation через Song.save())

**Rationale**:
- Active invalidation из `HealthReport.recomputeAndBroadcast` потребует touch KaraokeProcess + HealthReport + storage-cache.invalidate(...). Много кода, много поверхностей для багов.
- Stale на 5 минут допустим: admin работает с UI-индикатором, не с воспроизведением. Если файл удалён через `mc` мимо Karaoke — admin увидит "OK" ещё ≤5 мин; после следующего `recomputeAndBroadcast` (через repair или process finish) кеш ключ уже истёк.
- Точно соответствует `PollingCache` контракту (уже реализован и проверен).

**Alternatives considered**:

| Option | Pros | Cons | Verdict |
|---|---|---|---|
| TTL-only | Простой, проверенный | ≤5 мин stale | ✅ **Chosen** |
| TTL + active invalidation | Нулевой stale | Touch 4 модуля, race condition risk, сложный debug | ❌ |
| TTL + dirty-flag (как `StatBySong`) | Чёткая инвалидация, минимальный stale | Нужен подписчик через KaraokeProcess.onFinished (как Spec 286 AuthorsCache) | ⚪ future improvement |

### Decision: DI wiring (не AOP)

**Rationale**:
- ADR `local-0006-logging-and-error-handling-karaoke-web.md` явно отвергает AOP/aspect logging: «Aspect / AOP logging: rejected — скрытый flow, сложно debug».
- HealthReport — heavy existing module (2240 строк companion object). Изменения должны быть **минимальные и видимые**.
- DI autowire `StorageMetadataCache` + явный вызов `cache.getOrCompute(source, key, ttl, loader)` в `actionsRemoteStorage` / `actionsLocalStorage` — прозрачно для читателя.

**Alternatives considered**:

| Option | Pros | Cons | Verdict |
|---|---|---|---|
| Explicit DI autowire | Видимый flow, debug-friendly | HealthReport меняется (но это unavoidable) | ✅ **Chosen** |
| Spring AOP `@Cacheable` | Не трогаем HealthReport | Новая абстракция (CGLIB proxy), сложнее тестить, требует global cache manager | ❌ |
| Decorator pattern на `StorageApiClient` | Не трогаем HealthReport | Двойной indirection, нужен Spring proxy конфиг | ⚪ future |

### Decision: Copy `PollingCache.kt` в `karaoke-app`

**Rationale**:
- Текущее расположение: только `karaoke-web`. HealthReport — в `karaoke-app`.
- Shared Gradle модуль — overengineering для 80 строк.
- KDoc ссылается на источник (принцип DRY: явно указываем, что это копия).

**Alternatives considered**: см. decision #1.

### Decision: SLF4J-категория `infra.cache.storage`

**Rationale**:
- `knowledge/domains/monitoring/components/log-categories.md` — единый реестр категорий. Новая категория `infra.cache.storage` (subsystem `cache`, feature `storage`) — естественное продолжение `infra.cache.statbysong`.
- Уровень INFO по умолчанию: `cache:hit`, `cache:miss` — успешный flow; нет WARN/ERROR событий в норме (исключения НЕ кешируются, FR-006).
- `logback-spring.xml` НЕ существует в `karaoke-app/src/main/resources` → используется Spring Boot default. Уровни настраиваются через `application.yml` (`.logging.level.com.svoemesto.karaokeapp.services.StorageMetadataCache: INFO` — но мы используем строковый logger "infra.cache.storage", не class-based).

**Decision sub-tree**:
- `infra.cache.storage` уровень INFO.
- Sample messages:
  - `INFO cache:hit key="LOCAL:karaoke/song-123.mp4" source=LOCAL`
  - `INFO cache:miss key="REMOTE:karaoke/song-123.mp4" source=REMOTE durationMs=80`
  - `INFO cache:evicted key="..." reason=ttl count=12`
- Регистрация в `log-categories.md` (Knowledge MUST).

### Decision: Формат `/api/health/cacheStats` JSON

**Rationale** (см. spec FR-008):

```json
{
  "local": {"entries": 1234, "hits": 5678, "misses": 42, "hitRatio": 0.99, "ttlSeconds": 300, "evictions": 10},
  "remote": {"entries": 2345, "hits": 9876, "misses": 123, "hitRatio": 0.99, "ttlSeconds": 300, "evictions": 15}
}
```

- Local и remote раздельно — для диагностики (если local hit-rate < 50%, ищем баг в local MinIO).
- `hitRatio = hits / (hits + misses)` — float в [0, 1] для удобства графиков.
- `evictions` — счётчик `maybeCleanup` removals.
- Не включаем raw entries (privacy: имена файлов песен).
- Единый endpoint `/api/health/cacheStats` (admin-сторона, `karaoke-app`). Контракт в `contracts/cache-stats-api.md`.

### Resolved unknowns (ничего не осталось)

| Вопрос спеки | Решение | Обоснование |
|---|---|---|
| TTL стратегия | 300s uniform | UI-UX, не player |
| Invalidation strategy | TTL-only | Простой, проверен |
| DI strategy | Explicit autowire | Видимость |
| PollingCache placement | Copy в karaoke-app | Overengineering shared module |
| Persisted (P3) | Deferred | Не входит в scope #344 |
| Bulk `checkIfExists` (P3) | Deferred | Отдельная задача, вне scope |
| Логирование | `infra.cache.storage` INFO | Реестр categories |
| Cold-start | Lazy miss (без warmup) | Соответствует existing pattern |

Все 8 Assumptions из spec.md → конкретные code-уровневые решения в plan.md.

## Границы (out-of-scope)

Явно подтверждено НЕ в этой спеке:
- `fileExists` race fix (#65) — Pass 343 починил только repair-loop; HTTP-level race остаётся. Отдельная задача.
- `checkIfExists` bulk-расширение (storage-api-client.md#known-gaps).
- Persistence write-through (P3 user story) — отложено.
- HealthReport UI-представление (`HealthReportView.vue`) — отдельная задача в webvue3.
- Изменение контрактов `KaraokeStorageService` / `StorageApiClient` (FR-011 — без изменений).

## Готовность к Phase 1 (Design)

Все решения зафиксированы. Переходим к `data-model.md`, `contracts/cache-stats-api.md`, `quickstart.md`.
