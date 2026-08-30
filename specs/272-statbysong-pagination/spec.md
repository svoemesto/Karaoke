# Feature Specification: Ограничение лимита для Thymeleaf /statbysong (FR-107)

**Feature Branch**: `272-statbysong-pagination`
**Created**: 2026-08-26
**Status**: Draft
**Parent**: [`specs/241-db-storage-perf-audit/spec.md`](../241-db-storage-perf-audit/spec.md) — Tier-3 / FR-107
**Input**: User description (через parent спеку FR-107): "`MainController.doStatBySong` (Thymeleaf `/statbysong`) MUST ограничить `limit` разумным значением (≤ 1000) или выводить в CSV-формате постранично. Сейчас `limit = 100_000` + 17 условных count(*) filter — это минутный запрос на полную таблицу `tbl_events`."

## Clarifications

### Session 2026-08-26

- **Q**: Какой вариант выбрать — вариант A (ограничить limit ≤ 1000) или вариант B (CSV постранично)?
  **A**: A — **ограничить `limit` до 1000** в `MainController.doStatBySong`. UI без пагинации (top-1000 как самые значимые). CSV-формат — отдельная будущая фича, если понадобится.
- **Q**: Стоит ли добавить safety-guard в самом `StatsByEvents.getStatBySong` (`if (limit > MAX_LIMIT) limit = MAX_LIMIT`), чтобы защитить любой будущий код от случайного огромного лимита?
  **A**: A — **да, добавить safety-guard** `MAX_LIMIT = 1000`. Защищает от: (1) случайного copy-paste с другим hardcoded значением; (2) потенциальной DoS через `StatsController.statsBySong?pageSize=...` (там тоже можно передать любой pageSize).
- **Q**: Менять ли Thymeleaf шаблон `statbysong.html` (добавлять пагинацию или предупреждение «показано топ-1000»)?
  **A**: B — **добавить ненавязчивый баннер** «Показано топ-1000 из ~N доступных. Для полной выгрузки используйте `/api/stats/by-song` с пагинацией». Минимальная правка UI (5 строк), явно сообщает об ограничении.
- **Q**: Что делать с StatsController (`/api/stats/by-song`) — тоже защищать?
  **A**: B — **защита через safety-guard в `getStatBySong`** покрывает оба случая автоматически. StatsController не трогаем.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Быстрая загрузка /statbysong (Priority: P2)

Администратор открывает страницу `/statbysong` (Thymeleaf). Страница загружается за <2 секунды (раньше — десятки секунд / минуты). Показываются **топ-1000 песен** по числу событий в `tbl_events`. Вверху страницы — ненавязчивый баннер «Показано топ-1000 из ~N доступных. Для полной выгрузки используйте `/api/stats/by-song` с пагинацией».

**Why this priority**: Thymeleaf `/statbysong` — единственный «тяжёлый» endpoint в `karaoke-web`, который грузит полную таблицу `tbl_events` (десятки/сотни тысяч записей) на каждый запрос. С `limit=100_000` и 17 условными `count(*) filter (...)` это минутный запрос на полную БД — даже холодный рестарт контейнера может занять десятки секунд. На проде (где нет admin-UI) endpoint не критичен, но при локальной отладке / чтении логов — сильно мешает.

**Independent Test**: открыть `/statbysong` в браузере → замерить время загрузки через devtools (Network tab) — должно быть <2 сек. Сделать `EXPLAIN ANALYZE` SQL из `getStatBySong` с `limit=1000` — должен быть Index Scan на `tbl_events_song_id_index` + быстрый GROUP BY (PostgreSQL использует HashAggregate на маленьком подмножестве).

**Acceptance Scenarios**:
1. **Given** `MainController.doStatBySong` с изменённым `limit=1000`, **When** страница `/statbysong` открывается, **Then** SQL выполняется с `LIMIT 1000 OFFSET 0`. Полное время загрузки <2 сек.
2. **Given** страница загружена, **When** администратор смотрит результат, **Then** видны **ровно 1000 строк** (или меньше, если в БД меньше песен с событиями).
3. **Given** страница загружена, **When** администратор смотрит верх страницы, **Then** видит баннер «Показано топ-1000 из ~N доступных. Для полной выгрузки используйте `/api/stats/by-song` с пагинацией».
4. **Given** Thymeleaf шаблон с баннером, **When** страница загружается, **Then** баннер не блокирует основной контент (видимый сразу), стилизуется в `alert-info` Bootstrap.

### User Story 2 — Защита от случайного огромного limit (Priority: P2)

Любой будущий код (или запрос через `StatsController?pageSize=1000000`) с `limit > 1000` будет автоматически ограничен до 1000 через safety-guard в `StatsByEvents.getStatBySong`. Защита от DoS и случайных copy-paste ошибок.

**Why this priority**: минимальная защита, которая ловит самый частый класс ошибок — случайная передача огромного limit. StatsController уже имеет пагинацию через `pageSize`, но пользователь может прислать `?pageSize=100000` через REST API.

**Independent Test**: вызвать `/api/stats/by-song?pageSize=100000` → проверить SQL в `pg_log` — должен быть `LIMIT 1000` (а не 100000). Проверить response — `items.length <= 1000`.

**Acceptance Scenarios**:
1. **Given** `StatsByEvents.getStatBySong` с safety-guard `MAX_LIMIT = 1000`, **When** функция вызывается с `limit=100_000`, **Then** фактический SQL использует `LIMIT 1000` (лимит схлопывается в safety-guard).
2. **Given** функция вызвана с `limit=50` (нормальное значение), **When** SQL выполняется, **Then** используется `LIMIT 50` (без изменений).
3. **Given** функция вызвана с `limit=0` или отрицательным, **When** SQL выполняется, **Then** используется `LIMIT 50` (default, защита от мусорных значений).
4. **Given** `StatsController.statsBySong` с `?pageSize=100000`, **When** endpoint вызывается, **Then** SQL использует `LIMIT 1000` (через safety-guard в `getStatBySong`), response содержит ≤1000 items.

### User Story 3 — Полная выгрузка через API (Priority: P3)

Если администратору нужна полная статистика (>1000 песен), он использует `/api/stats/by-song?page=1&pageSize=50` с пагинацией. `/api/stats/by-song` уже имеет правильную пагинацию (через `page` и `pageSize` query params) и возвращает `items + totalCount` для UI-пагинации.

**Why this priority**: tier-3 P2 — не блокер, но даёт администратору путь для получения полных данных без модификации UI `/statbysong`.

**Independent Test**: вызвать `/api/stats/by-song?page=1&pageSize=50` → response `totalCount` показывает общее число песен с событиями. Последовательно `?page=2`, `?page=3` и т.д. — можно получить все данные.

**Acceptance Scenarios**:
1. **Given** endpoint `/api/stats/by-song?page=1&pageSize=50`, **When** он вызывается, **Then** response содержит `{items: [...50 items...], totalCount: N}`.
2. **Given** `pageSize > 1000` через `?pageSize=100000`, **When** endpoint вызывается, **Then** SQL ограничен через safety-guard до 1000 (см. US2).

## Edge Cases

- **Что если `limit = 0`** (забыли параметр)? Safety-guard должен использовать default `50` (как сейчас в дефолте `getStatBySong`). Реализация: `val effectiveLimit = limit.coerceIn(MIN_LIMIT, MAX_LIMIT)`.
- **Что если `offset` очень большой**? PostgreSQL просто вернёт пустой результат — это OK, никакой дополнительной защиты не нужно.
- **Что если `tbl_events` пуста** (свежая БД)? SQL вернёт 0 строк — баннер покажет «топ-0 из 0 доступных» (не критично).
- **Что если администратор хочет именно >1000 строк через Thymeleaf**? Этот use-case больше не поддерживается через `/statbysong`. Используйте `/api/stats/by-song` (полная пагинация) или CSV-выгрузку (отдельная будущая фича, не в скоупе FR-107).
- **Что если `getStatBySongCount` тоже медленный**? Он сейчас делает `SELECT count(DISTINCT song_id) FROM tbl_events WHERE song_id > 0` — это лёгкий запрос, должен быть Index Only Scan на `tbl_events_song_id_index`. Не требует оптимизации.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `MainController.doStatBySong` MUST передавать `limit = 1000` вместо `limit = 100_000` в `StatsByEvents.getStatBySong`.
- **FR-002**: `StatsByEvents.getStatBySong` MUST иметь **safety-guard** `MAX_LIMIT = 1000` и `MIN_LIMIT = 1`. Параметр `limit` нормализуется через `coerceIn(MIN_LIMIT, MAX_LIMIT)` перед подстановкой в SQL.
- **FR-003**: Thymeleaf шаблон `statbysong.html` MUST иметь ненавязчивый баннер «Показано топ-1000 из ~N доступных. Для полной выгрузки используйте `/api/stats/by-song` с пагинацией.» вверху страницы (под заголовком, над таблицей). Стилизация — `alert alert-info` Bootstrap 4 (как уже используется в шаблоне).
- **FR-004**: Баннер MUST показывать общее число песен с событиями (через `StatsByEvents.getStatBySongCount(database = WORKING_DATABASE)`).
- **FR-005**: KDoc MUST быть добавлен на изменённые методы (`doStatBySong`, `getStatBySong`) со ссылками на FR-107 parent спеки 241, баннер и safety-guard (Constitution § VI Code Standards, FR-006).

### Key Entities

- **Safety guard `MAX_LIMIT = 1000`**: константа в `StatsByEvents`, защищает от огромных `limit`.
- **Safety guard `MIN_LIMIT = 1`**: константа, защищает от мусорных значений (0, отрицательные).
- **Баннер**: HTML-блок в `statbysong.html` с текстом и числом.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Полное время загрузки `/statbysong` <2 сек (vs десятки секунд/минуты baseline).
- **SC-002**: SQL содержит `LIMIT 1000` (видно в `pg_log` при загрузке страницы).
- **SC-003**: На странице отображается ровно ≤1000 строк.
- **SC-004**: Баннер виден вверху страницы с правильным текстом и числом.
- **SC-005**: `StatsController?pageSize=100000` возвращает ≤1000 items (safety-guard работает).
- **SC-006**: KDoc coverage 100% (Constitution § VI FR-006).
- **SC-007**: ktlint PASS, все 7 CI gates PASS (`lint.yml`).

## Assumptions

- **`StatsByEvents.getStatBySong` уже принимает `limit` и `offset`** (см. `StatBySong.kt:449-452`). Дефолт — `limit=50, offset=0`. Мы только нормализуем `limit` через safety-guard.
- **`StatsByEvents.getStatBySongCount` — лёгкий запрос** (`SELECT count(DISTINCT song_id)`), использует `tbl_events_song_id_index` (см. FR-110 миграцию 41). На маленьких объёмах (<200k events) выполняется за миллисекунды.
- **`getStatBySong` уже оптимизирован** (см. KDoc на строке 455: «Одна группировка по song_id + условные count(*) filter вместо 8 LEFT JOIN-подзапросов»). С `LIMIT 1000` PostgreSQL использует HashAggregate на маленьком подмножестве — миллисекунды.
- **Thymeleaf шаблон `statbysong.html` использует Bootstrap 4** (см. `<link rel="stylesheet" href="...bootstrap@4.6.0...">` в строке 4). Стилизация `alert alert-info` совместима.
- **Текущая семантика `doStatBySong`**: возвращает Thymeleaf-шаблон `statbysong.html` с атрибутом `stats: List<StatBySongDto>`. Никаких изменений в response формате — только `limit` и шаблон.
- **`MainController.doStatBySong` вызывается только с одной страницы** (`/statbysong`), нет клиентов, зависящих от конкретного `limit`. Изменение `100_000 → 1000` ничего не ломает.
- **`StatsController.statsBySong?pageSize=100000`** — потенциальный DoS-вектор, который мы ЗАКРЫВАЕМ safety-guard'ом. Без этого фикса пользователь мог бы получить огромный ответ и нагрузить сервер.

## Out of Scope

- Изменение `StatsController.statsBySong` (safety-guard в `getStatBySong` достаточно).
- CSV-выгрузка (отдельная будущая фича).
- Пагинация в Thymeleaf UI `/statbysong` (не нужна при `LIMIT 1000`).
- Оптимизация `getStatBySongCount` (уже Index Only Scan на `tbl_events_song_id_index`).
- Изменение SQL в `getStatBySong` (17 условных `count(*) filter` уже оптимизированы, см. KDoc).

## Reference

- Parent спека: [`specs/241-db-storage-perf-audit/spec.md`](../241-db-storage-perf-audit/spec.md), FR-107, H-10.
- Current implementation: [`MainController.kt:486-496`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt).
- SQL function: [`StatBySong.kt:449-541`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/StatBySong.kt).
- Sister stats endpoint: [`StatsController.kt:93-104`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/StatsController.kt).
- Thymeleaf template: [`statbysong.html`](../../karaoke-web/src/main/resources/templates/statbysong.html).
- Sister-spec (pass 241, Tier-2): [`specs/248-authors-tiles-cache/spec.md`](../248-authors-tiles-cache/spec.md).