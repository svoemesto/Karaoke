# Feature Specification: Кеш для /api/public/authors-tiles

**Feature Branch**: `248-authors-tiles-cache`
**Created**: 2026-08-26
**Status**: Draft
**Parent**: [`specs/241-db-storage-perf-audit/spec.md`](../241-db-storage-perf-audit/spec.md) — Tier-2 / FR-105
**Input**: User description (через parent спеку FR-105): "Эндпоинт `/api/public/authors-tiles` MUST кешировать результат `Song.loadAuthorSongCounts + Song.loadListAuthors` в `KaraokeProperties`-совместимом кеше с TTL 30 сек (или по dirty-флагу, как `StatsCacheScheduler`). Сейчас на каждый запрос — 2 тяжёлых full-scan с DISTINCT/GROUP BY по `tbl_songs`."

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Быстрый ответ главной страницы «Закромов» (Priority: P1)

Посетитель публичного сайта (аноним или залогиненный) открывает главную страницу «Закромов» или любую страницу с навигацией по авторам, которая вызывает `/api/public/authors-tiles`. Endpoint отвечает из кеша за <50 мс (warm path), холодный первый запрос — за <500 мс вместо текущих full-scan с DISTINCT/GROUP BY. Под нагрузкой (10+ RPS) прод не проседает по SQL.

**Why this priority**: `/api/public/authors-tiles` — один из самых «горячих» endpoints на главной странице прода и в навигации по «Закромам». На каждый вызов — 2 full-scan запроса к `tbl_songs`: (1) `Song.loadAuthorSongCounts` с `GROUP BY song_author`, (2) `Song.loadListAuthors` с `DISTINCT song_author`. На таблице 18k+ записей это десятки-сотни мс на каждый запрос. На пиковой нагрузке (10 RPS) — до 20 full-scan/сек, что ощутимо нагружает БД.

**Independent Test**: открыть в браузере главную страницу публичного сайта, замерить время отклика через browser devtools (Network tab) — должно быть <50 мс warm path. Сделать 100 повторных запросов через curl, замерить SQL в `pg_log` — должно быть ≤2 SQL (cold start) + 0 hot SQL (cache hits).

**Acceptance Scenarios**:
1. **Given** холодный старт (TTL cache истёк, dirty-флаг не взведён), **When** endpoint вызывается, **Then** cache miss → loadFn() → cache put (если not empty) → возврат результата. SQL: 2 (counts + authors).
2. **Given** cache populated, **When** endpoint вызывается повторно в течение 30 минут, **Then** cache hit → 0 SQL к `tbl_songs`. Возврат — из `ConcurrentHashMap`.
3. **Given** `StatBySong.consumeDirty()` возвращает `true` (была сохранена/синхронизирована песня с изменённым free-статусом), **When** следующий вызов endpoint, **Then** cache очищается, выполняется loadFn() заново, результат кладётся в cache. Следующие вызовы в течение 30 минут — cache hits.
4. **Given** cache disabled через `karaoke.public.authors-tiles-cache.enabled = false`, **When** endpoint вызывается, **Then** loadFn() выполняется на каждый запрос (cache bypass), SQL: 2 на запрос.
5. **Given** cold start (loadFn() возвращает пустой список), **When** результат кладётся в cache, **Then** НЕ кешируется пустой список — cache остаётся пустым, следующий вызов повторит попытку (FR-007).

---

### User Story 2 — Инвалидация кеша при изменении данных (Priority: P2)

При сохранении/синхронизации песни (free-статус мог измениться) `StatBySong.markDirty()` взводит флаг. Следующий вызов `/api/public/authors-tiles` подхватывает изменение через `StatBySong.consumeDirty()` и сбрасывает cache → loadFn() пересчитывает результат с новыми данными. Без этой инвалидации — данные в cache могли бы быть устаревшими до 30 минут.

**Why this priority**: для пользователя «Закромов» — главное, чтобы счётчик песен на плашке автора соответствовал реальности. Без инвалидации после save — UI показывает старый счётчик 30 минут.

**Independent Test**: симулировать save песни (через admin-панель) → вызвать `/api/public/authors-tiles` — cache должен быть сброшен, ответ пересчитан. В docker logs — `markDirty` от `InternalStatsController`.

**Acceptance Scenarios**:
1. **Given** cache populated, `StatBySong.markDirty()` вызван, **When** endpoint вызывается, **Then** `consumeDirty()` возвращает `true`, cache очищается, loadFn() пересчитывает данные, новый результат кладётся в cache.
2. **Given** cache populated, `markDirty()` НЕ вызывался, **When** endpoint вызывается, **Then** `consumeDirty()` возвращает `false`, cache используется без изменений.

---

### Edge Cases

- **Что если `loadFn()` возвращает пустой список** (например, БД недоступна)? Пустой результат НЕ кешируется — следующий вызов повторит попытку (FR-007).
- **Что если `KaraokeProperties` недоступен** (ранняя инициализация, проблемы с файлом)? Helper `isCacheEnabled()` возвращает `true` через `try/catch` — безопасный дефолт = кеш работает.
- **Что если два запроса прилетают одновременно в момент cache miss**? `ConcurrentHashMap` не гарантирует single-execution, поэтому возможен двойной loadFn(). Это допустимо (не блокирует UI, второй запрос просто перезапишет cache).
- **Что если `scope` принимает неожиданное значение** (не "main"/"special"/"all")? Текущая логика приводит к `isSpecialOrderFilter = false`, scope в cache key = оригинальная строка (например, "foo"). Это нормально — cache key уникален.
- **Что если `StatBySong.consumeDirty()` бросает исключение**? Helper оборачивает в `try/catch` с fallback на cache (или loadFn() если cache пуст).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `PublicApiController.authorsTiles()` MUST обернуть существующую логику (`Song.loadAuthorSongCounts` + `Song.loadListAuthors` + маппинг в `AuthorTilePublicDto`) в helper `getCachedAuthorsTiles(scope, onlyPublished, loadFn)`, который проверяет cache перед вызовом `loadFn()`.
- **FR-002**: Helper `getCachedAuthorsTiles` MUST использовать `ConcurrentHashMap<String, CachedAuthorsTiles>` в companion-объекте `PublicApiController` для хранения кешированных результатов.
- **FR-003**: Helper MUST читать флаг `karaoke.public.authors-tiles-cache.enabled` через `KaraokeProperties.getBoolean(...)` с `try/catch` fallback на `true` (безопасный дефолт = кеш работает).
- **FR-004**: Cache MUST инвалидироваться через `StatBySong.consumeDirty()`: если возвращает `true`, cache очищается перед проверкой TTL (dirty-проверка имеет приоритет над TTL).
- **FR-005**: TTL кеша MUST быть **30 минут** (`CACHE_TTL_MS = 30 * 60 * 1000L`). После истечения TTL — cache miss → loadFn().
- **FR-006**: KDoc MUST быть добавлен на оба helper'а (`getCachedAuthorsTiles` и `isCacheEnabled`) со ссылками на FR-001, FR-105 parent спеки, и `StatBySong.consumeDirty()` (Constitution § VI Code Standards, FR-006).
- **FR-007**: Пустой результат `loadFn()` MUST НЕ кешироваться (FR-005 SC-007). Cache остаётся пустым — следующий вызов повторит попытку.
- **FR-008**: Cache key MUST быть `"$scope:$onlyPublished"` (например, `"main:true"`, `"special:false"`, `"all:false"`). Это обеспечивает корректное разделение кеша для разных комбинаций scope/onlyPublished.
- **FR-009**: Cache MUST НЕ сохранять пустые/ошибочные результаты (для `loadFn() throws` — cache остаётся неизменным, вызов пробрасывает исключение дальше).

### Key Entities

- **`CachedAuthorsTiles`** (data class в companion `PublicApiController`): пара `(value: List<AuthorTilePublicDto>, expiresAtMs: Long)`. Immutable, thread-safe через `ConcurrentHashMap`.
- **`authorsTilesCache`** (companion-объект `PublicApiController`): `ConcurrentHashMap<String, CachedAuthorsTiles>` — thread-safe хранилище кеша по ключу `"$scope:$onlyPublished"`.
- **`StatBySong.consumeDirty()`**: существующий API в `karaoke-web/.../StatBySong.kt` — атомарно читает и сбрасывает dirty-флаг. Используется для инвалидации кеша при изменении данных.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Warm path `/api/public/authors-tiles` отвечает за <50 мс (cache hit, 0 SQL). Текущее значение — десятки-сотни мс на full-scan.
- **SC-002**: Cold start `/api/public/authors-tiles` отвечает за <500 мс (2 SQL: counts + authors). Текущее значение — аналогично (без изменений в latency на cold start).
- **SC-003**: При 100 повторных запросах в течение 30 минут — `pg_log` показывает ≤2 SQL к `tbl_songs` (cold start). Текущее значение — 200 SQL.
- **SC-004**: `pg_log` за 24 часа после деплоя показывает снижение SQL от `/api/public/authors-tiles` на ≥80% при пиковой нагрузке (10 RPS).
- **SC-005**: При выставлении `karaoke.public.authors-tiles-cache.enabled = false` — endpoint работает без cache (loadFn на каждый запрос), без падений.
- **SC-006**: Code metrics: добавлено 2 helper'а (`getCachedAuthorsTiles`, `isCacheEnabled`) + 1 data class (`CachedAuthorsTiles`), общий объём нового кода — ≤50 строк (KDoc включительно). Цикломатическая сложность `authorsTiles()` остаётся ≤5.

## Assumptions

- **`StatBySong.consumeDirty()`** существует в `karaoke-web/.../StatBySong.kt:60` (см. parent спека, A.4 — `dirty.getAndSet(false)`). Используется как сигнал «данные изменились — кеш устарел». Взводится `markDirty()` через `InternalStatsController` при save/sync песни.
- **`KaraokeProperties.getBoolean(key)`** существует в `karaoke-app/.../KaraokeProperties.kt:96` и возвращает `Boolean` с дефолтом `false`. Для получения дефолта `true` будет зарегистрировано новое свойство `karaoke.public.authors-tiles-cache.enabled` в `listKaraokeProperties` (рядом с `karaoke.db.schema_cache.enabled`, см. parent спека, A.4 — паттерн «кеш с TTL и properties-управлением»).
- **Cross-module import**: `karaoke-web` зависит от `karaoke-app` через `implementation(project(":karaoke-app"))` (см. `karaoke-web/build.gradle.kts:24`), поэтому импорт `com.svoemesto.karaokeapp.KaraokeProperties` в `PublicApiController` не нарушает существующих ограничений (PublicApiController уже импортирует `com.svoemesto.karaokeapp.model.Song` и др.).
- **Существующая логика** `Song.loadAuthorSongCounts` и `Song.loadListAuthors` (см. `Song.kt:7171` и `Song.kt:7115`) НЕ изменяется. Только оборачивается в cache.
- **Поведение для редактора vs анонима**: `onlyPublished` зависит от `siteUserResolver.resolve(request)?.isEditor != true` (см. `onlyPublishedFor` в `PublicApiController.kt:95`). Разные пользователи → разные cache keys → кеш работает корректно.
- **TTL 30 минут** — компромисс между свежестью данных и нагрузкой. Альтернативы (TTL 1 час / 6 часов) ухудшают SC-004 (свежесть после save). Для пользователя «Закромов» 30 минут + dirty-инвалидация — оптимальный баланс.
- **Тестирование**: автоматических тестов нет (см. Constitution § Тесты — `@Disabled`). Проверка — пользователем через deploy + `pg_log`-замеры.
- **Pre-commit/CI-gate** не ломается: все правки в рамках этой фичи проходят через обычный CI/lint/compile pipeline (см. AGENTS.md, секция «Обязательная проверка после ЛЮБОГО изменения кода»).
- **Parent спека** `241-db-storage-perf-audit/spec.md` FR-105 — Tier-2 / P1. Эта фича — реализация FR-105.

## Out of Scope (явно НЕ делается в этой фиче)

- Индексы `idx_songs_song_author` (FR-110 / H-5) — отдельная фича, SQL-миграция.
- Кеш для `/api/public/zakroma` (FR-106) — отдельная фича.
- Tier-1 (FR-101, FR-102, FR-103, FR-104) и Tier-3 (FR-107, FR-108, FR-109) — отдельные фичи.
- Изменение SQL в `Song.loadListAuthors`/`Song.loadAuthorSongCounts` — этот PR только добавляет cache-слой.
- Изменение `StatBySong.refreshCache` (ежечасный пересчёт счётчиков главной) — НЕ затрагивается.
- Изменение публичного API или DTO-контрактов (`AuthorTilePublicDto`, `ZakromaPublicDto`) — cache прозрачен для клиента.
- Изменение стека доступа к БД (Constitution § II «Сырой JDBC»).
- Включение `pg_stat_statements` — перенесено в backlog (см. parent спека, Clarifications).