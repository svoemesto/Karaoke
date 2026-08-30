# Feature Specification: TTL-кеш для PublicSettingsWebController.getProperty

**Feature Branch**: `249-public-settings-cache`
**Created**: 2026-08-26
**Status**: Draft
**Parent**: [`specs/241-db-storage-perf-audit/spec.md`](../241-db-storage-perf-audit/spec.md) — Tier-2 / FR-106
**Input**: User description (через parent спеку FR-106): "`PublicSettingsWebController.getProperty` MUST кешировать результат в `ConcurrentHashMap<String, String>` с TTL 60 сек. Сейчас на каждый запрос — `SELECT value FROM tbl_public_settings WHERE key=?`."

## Clarifications

### Session 2026-08-26

- **Q**: Где хранить cache invalidation flag — переиспользовать `StatBySong.markDirty()` или сделать отдельный?
  **A**: A — отдельный `AtomicBoolean dirty` в `PublicSettingsWebController.companion object`. Семантика разная: `StatBySong` — про free-флаги песен (главная страница), `PublicSettings` — про публичные настройки (kill-switches и пр.). Разделение ответственности и предсказуемая инвалидация.
- **Q**: Какой TTL выбрать — 30 сек (как у `StatsCacheScheduler.refreshIfDirty`) или 60 сек (как в спецификации FR-106)?
  **A**: B — **60 сек**, как указано в FR-006 parent спеки. Настройки меняются редко (kill-switch toggle), но админский UI может опрашивать чаще.
- **Q**: Что делать, если `loadFn()` (SQL SELECT) бросил исключение — должен ли cache вернуть устаревшее значение (fail-open) или пробросить исключение?
  **A**: A — **fail-open с возвратом пустой строки** (как в текущей реализации `getProperty`). Текущая семантика: при ошибке БД возвращается `""`. Кеш НЕ сохраняется, следующий вызов повторит попытку.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Быстрый ответ `/api/properties/getproperty` (Priority: P2)

Администратор открывает страницу SongsTable (или любой компонент, вызывающий `getPropertyValuePromise` через `vuex/store`). Endpoint отвечает из кеша за <10 мс (warm path, in-memory `ConcurrentHashMap.get`), холодный первый запрос — за <50 мс (1 SQL). Под нагрузкой (несколько RPS с одной админ-машины) прод не получает лишних SELECT к `tbl_public_settings`.

**Why this priority**: `tbl_public_settings` — маленькая таблица, но используется как kill-switch (`newsAutoPublishKillSwitch`, `editorAssignmentDefaultTarget`, и др.) и читается на каждом действии в админ-таблице. На странице SongsTable при загрузке идёт серия `getProperty` вызовов (для лимитов, default-значений и т.п.) — это десятки SQL на одну страницу. Кеш снижает нагрузку на БД и ускоряет отзывчивость UI.

**Independent Test**: открыть SongsTable в админке → замерить SQL в `pg_log` (или `EXPLAIN`-журнал) — должно быть 0 SELECT к `tbl_public_settings` после первого вызова. Повторить `curl /api/properties/getproperty?key=foo` 100 раз — в `pg_log` ровно 1 SELECT (cold start) + 0 hot SELECT.

**Acceptance Scenarios**:
1. **Given** холодный старт (cache пуст, TTL истёк, dirty не взведён), **When** endpoint вызывается, **Then** cache miss → loadFn() → cache put (если value != "" AND value != NOT_FOUND_SENTINEL) → возврат значения. SQL: 1.
2. **Given** cache populated (TTL не истёк), **When** endpoint вызывается повторно в течение 60 сек, **Then** cache hit → 0 SQL к `tbl_public_settings`. Возврат из `ConcurrentHashMap`.
3. **Given** cache populated, TTL истёк, **When** endpoint вызывается, **Then** cache expired → loadFn() → cache put. SQL: 1.
4. **Given** cache populated, **`markDirty()` был вызван** (через `setProperty`), **When** endpoint вызывается, **Then** `consumeDirty()` возвращает `true`, cache очищается, loadFn() пересчитывает данные, новый результат кладётся в cache. Следующие вызовы в течение 60 сек — cache hits.
5. **Given** cache disabled через `karaoke.public.public-settings-cache.enabled = false`, **When** endpoint вызывается, **Then** loadFn() выполняется на каждый запрос (cache bypass), SQL: 1 на запрос.
6. **Given** key не существует в `tbl_public_settings` (cold start), **When** endpoint вызывается, **Then** `loadFn()` возвращает `""`, sentinel `NOT_FOUND_SENTINEL` кладётся в cache (с TTL), следующий вызов в течение 60 сек возвращает `""` без SQL.
7. **Given** `loadFn()` бросил исключение (БД недоступна), **When** endpoint вызывается, **Then** возвращается `""` (fail-open, как в текущей реализации), cache НЕ сохраняется, следующий вызов повторит попытку.

### User Story 2 — Инвалидация кеша при setProperty (Priority: P1)

При вызове `POST /api/properties/setproperty` (например, при включении kill-switch) `markDirty()` взводит флаг. Следующий вызов `getProperty` подхватывает изменение через `consumeDirty()` и сбрасывает cache → `loadFn()` читает свежее значение → кладёт в cache. Без этой инвалидации — администратор видел бы старое значение до 60 сек после собственного действия.

**Why this priority**: UX-критично — админ включает kill-switch, перезагружает страницу и ожидает немедленного эффекта. Без инвалидации он потратит минуту на отладку «почему не работает», а потом на поиск того, как сбросить кеш.

**Independent Test**: вызвать `POST /api/properties/setproperty?key=foo&stringValue=bar` → сразу вызвать `GET /api/properties/getproperty?key=foo` — должно вернуть `"bar"` (а не старое значение). В `pg_log` — ровно 2 SQL (UPDATE + SELECT).

**Acceptance Scenarios**:
1. **Given** cache populated (value=`"old"`), `setProperty(key, "new")` вызван, **When** endpoint `getProperty(key)` вызывается сразу после, **Then** `consumeDirty()` возвращает `true`, cache очищается, `loadFn()` возвращает `"new"`, новое значение кладётся в cache.
2. **Given** cache populated, `setProperty` НЕ вызывался, **When** endpoint вызывается, **Then** `consumeDirty()` возвращает `false`, cache используется без изменений.

### User Story 3 — Защита от двойного запроса (Priority: P3)

Два одновременных запроса к `getProperty` в момент cache miss делают двойной SELECT. Это допустимо (не блокирует UI, второй запрос перезаписывает cache). Но для админского UI (где RPS низкий) — не критично.

**Why this priority**: Tier-3 оптимизация (single-execution guard) — отдельная фича, если будет нужна. Для текущей нагрузки (≤5 RPS с одной админ-машины) — избыточно.

**Acceptance Scenarios**:
1. **Given** cache пуст, два одновременных HTTP-запроса, **When** они оба приходят, **Then** оба делают SELECT (race condition), оба кладут результат в cache (одинаковое значение, последний writer выигрывает).

## Edge Cases

- **Что если `loadFn()` возвращает пустую строку** (key не найден в БД)? В отличие от FR-007 parent спеки 248 (authors-tiles), здесь пустой ответ — это валидный результат (настройка может отсутствовать). Кладём `NOT_FOUND_SENTINEL` (отдельный объект-маркер) в cache, чтобы не делать повторный SELECT каждые 60 сек для несуществующих ключей. Возвращаем `""` как текущее поведение.
- **Что если `loadFn()` бросил исключение** (БД недоступна)? Возвращаем `""` (fail-open). Cache НЕ обновляется — следующий вызов повторит попытку.
- **Что если `KaraokeProperties.getBoolean(...)` бросил исключение** (ранняя инициализация, файл недоступен)? Helper `isCacheEnabled()` возвращает `true` через `try/catch` — безопасный дефолт = кеш работает.
- **Что если `setProperty` упал с исключением**? `markDirty()` НЕ вызывается (текущая логика: try/catch возвращает false). Cache остаётся валидным.
- **Что если два `setProperty` подряд с одним ключом**? Оба вызова взводят `consumeDirty()` → cache сбрасывается один раз (между setProperty и следующим getProperty). ОК.
- **Что если `getProperty` вызывается из разных потоков одновременно**? `ConcurrentHashMap` thread-safe. Гонка при cache miss → двойной SELECT, последний writer выигрывает (допустимо).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `PublicSettingsWebController.getProperty` MUST обернуть существующую логику (`SELECT value FROM tbl_public_settings WHERE key = ?`) в helper `getCachedProperty(key, loadFn)`, который проверяет cache перед вызовом `loadFn()`.
- **FR-002**: Helper MUST использовать `ConcurrentHashMap<String, CachedProperty>` в `companion object` `PublicSettingsWebController` для хранения кешированных результатов.
- **FR-003**: Helper MUST читать флаг `karaoke.public.public-settings-cache.enabled` через `KaraokeProperties.getBoolean(...)` с `try/catch` fallback на `true` (безопасный дефолт = кеш работает).
- **FR-004**: Cache MUST инвалидироваться через **отдельный** `dirty: AtomicBoolean` в companion object (НЕ через `StatBySong.consumeDirty()` — другая семантика). Метод `markDirty()` вызывается из `setProperty` при успешном UPDATE/INSERT. Метод `consumeDirty()` вызывается в `getCachedProperty` перед проверкой TTL.
- **FR-005**: TTL кеша MUST быть **60 секунд** (`CACHE_TTL_MS = 60 * 1000L`). После истечения TTL — cache miss → loadFn().
- **FR-006**: KDoc MUST быть добавлен на helper `getCachedProperty`, методы `markDirty`/`consumeDirty`, и `isCacheEnabled` со ссылками на FR-001, FR-106 parent спеки 241, и `setProperty` (Constitution § VI Code Standards, FR-006).
- **FR-007**: Если `loadFn()` возвращает пустую строку (key не найден) — MUST кешироваться `NOT_FOUND_SENTINEL` (отдельный объект-маркер), чтобы не делать повторный SELECT. Возвращаемое значение — `""`.
- **FR-008**: Если `loadFn()` бросил исключение — cache MUST НЕ обновляться, исключение НЕ пробрасывается (fail-open: вернуть `""`).
- **FR-009**: `setProperty` MUST вызывать `markDirty()` при успешном UPDATE/INSERT (после `connection.close()`, до `return true`).

### Key Entities

- **CachedProperty** (data class): пара `(value: String, expiresAtMs: Long)`. Immutable.
- **NOT_FOUND_SENTINEL**: object-маркер для обозначения «key отсутствует в БД». Используется как `value` в `CachedProperty`, чтобы отличать от валидного `value = ""`.
- **dirty: AtomicBoolean**: флаг инвалидации (отдельный от `StatBySong.dirty`).
- **cache: ConcurrentHashMap<String, CachedProperty>**: key = property key, value = `CachedProperty`.

### Cache Key

- **key** = property key (например, `"newsAutoPublishKillSwitch"`).
- **value** = `CachedProperty(value, expiresAtMs)`, где `value` может быть `""` или `NOT_FOUND_SENTINEL`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `getProperty` при warm cache возвращает значение за **<10 мс** (in-memory `ConcurrentHashMap.get`, без SQL).
- **SC-002**: На странице SongsTable после первой загрузки — **0 SELECT к `tbl_public_settings`** в течение 60 сек (вместо текущих N вызовов).
- **SC-003**: После `setProperty` — следующий `getProperty` возвращает свежее значение (НЕ ждёт 60 сек до истечения TTL). Проверка: `setProperty` → `getProperty` = свежее значение, без 60-секундной задержки.
- **SC-004**: KDoc coverage 100% (Constitution § VI FR-006, проверяется `tools/check-kdoc-coverage.sh`).
- **SC-005**: ktlint PASS, все 7 CI gates PASS (`lint.yml`).

## Assumptions

- **Частота вызовов `getProperty`**: оценивается как 1–5 RPS с одной админ-машины (админов немного, в основном SPA-фронт). На проде (где нет admin-UI) — 0 RPS, кеш неактуален. Эффект — **только на админ-машине**, не на проде (но оптимизация всё равно полезна для отзывчивости UI).
- **Текущая семантика `getProperty`**: возвращает `""` при ошибке БД и при отсутствии ключа. Кеш сохраняет это поведение (FR-007, FR-008).
- **`StatBySong.consumeDirty()` НЕ переиспользуется** (см. Clarifications Session 2026-08-26): разные домены. Свой `AtomicBoolean dirty` в companion object.
- **`setProperty` уже защищён try/catch и возвращает `false` при ошибке** — `markDirty()` вызывается ТОЛЬКО при успешном UPDATE/INSERT (FR-009).
- **`KaraokeProperties.getBoolean` доступен** через зависимость `karaoke-web → karaoke-app` (см. `karaoke-web/build.gradle.kts`). Используется тот же паттерн, что в `PublicApiController.isCacheEnabled()` (specs/248).
- **`ConcurrentHashMap` thread-safe**: гонка при cache miss допустима (UI не блокируется, последний writer выигрывает — не критично при 1–5 RPS).
- **Pre-commit/CI-gate не ломается**: правки в одном файле + добавление LiveDoc. Все проходят через обычный CI/lint/compile pipeline.

## Out of Scope

- Изменение `setProperty` (только `markDirty()`-инвалидация при успехе).
- Изменение `digest` (возвращает все настройки разом — другой use-case, не кешируется).
- Single-execution guard для предотвращения двойного SELECT (US3, Tier-3 — избыточно при текущей нагрузке).
- Distributed cache (Redis) — не нужен при 1–5 RPS и одном инстансе `karaoke-web`.

## Reference

- Parent спека: [`specs/241-db-storage-perf-audit/spec.md`](../241-db-storage-perf-audit/spec.md), FR-106.
- Sister spec (та же тема Tier-2): [`specs/248-authors-tiles-cache/spec.md`](../248-authors-tiles-cache/spec.md) — проверенный паттерн TTL-cache + `consumeDirty()` + `KaraokeProperties.getBoolean`.
- Current implementation: [`PublicSettingsWebController.kt`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicSettingsWebController.kt:98-117).
- Existing dirty flag (НЕ переиспользуем): [`StatBySong.dirty`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/StatBySong.kt:52).