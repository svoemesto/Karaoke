# Feature Specification: Починить flood JDBC-соединений при открытии вкладки «Статистика»

**Feature Branch**: `174-fix-stats-connection-leak`
**Created**: 2026-08-12
**Status**: Draft
**Input**: User description: "Работай в новой ветке. Компонент «Статистика» — через несколько секунд при переходе на этот компонент в лога karaoke-app — каскад сообщений «KaraokeConnection getConnection Exception: FATAL: sorry, too many clients already»."

## Контекст и текущее состояние

### Симптом (наблюдение пользователя, 2026-08-12)

При переходе администратора на пункт меню «Статистика» в `webvue3` (компонент
`webvue3/src/views/StatsView.vue`) — через несколько секунд в логе `karaoke-app`
появляется каскад сообщений вида:

```
KaraokeConnection getConnection Exception: FATAL: sorry, too many clients already
```

(метод `KaraokeConnection.getConnection()`, `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeConnection.kt:42-44`).

Каскад повторяется при каждом F5 / re-mount компонента, и через короткое время
PostgreSQL перестаёт отдавать соединения — дашборд начинает показывать пустые
вкладки вместо данных, а параллельные пользователи `karaoke-public` могут
получать `503`/`504` на других эндпоинтах.

### Корневая причина (многослойная)

1. **`StatsView.vue:mounted()` → `reloadAll()` (строки 543-583)** запускает
   **11 параллельных** dispatch'ей через Vuex:
   - `loadStatsSummary` → `GET /api/stats/summary`
   - `loadStatsTimeSeries` → `GET /api/stats/timeseries`
   - `loadStatsBreakdown` → `GET /api/stats/by-type` + `/api/stats/by-detail` + `/api/stats/channels`
   - `loadStatsGeo` → `GET /api/stats/countries` + `/api/stats/referrers`
   - `reloadTopUsers` → `GET /api/stats/top-users`
   - `reloadStatsBySong` → `GET /api/stats/by-song`
   - `reloadWebEvents` → `GET /api/webevents`
   - `reloadTopListened` → `GET /api/stats/top-listened`
   - `loadMonetizationSummary` → `GET /api/stats/monetization`
   - `loadMonetizationTopSongs` → `GET /api/stats/monetization/top-songs`
   ⇒ **минимум 10–12 параллельных HTTP-запросов одним кликом мыши**.

2. **`StatsController` (`karaoke-app/.../controllers/StatsController.kt`)** —
   каждый endpoint создаёт **новый** объект `Connection` через приватный
   `resolveDb(target)` (строка 25), а каждое обращение к
   `Connection.local()/remote()` возвращает свежий инстанс (см. KDoc
   `Connection.kt:60-98` — статические фабрики всегда возвращают `new`).

3. **Частичный фикс уже есть**: приватный `withDb { ... }` в `StatsController`
   (строки 34-47) оборачивает вызовы в `try { ... } finally { db.getConnection()?.close() }`,
   то есть физическое JDBC-соединение закрывается сразу после ответа. Но
   паттерн «новый `DriverManager.getConnection` → `close`» на каждый
   HTTP-запрос — это **10 параллельных TCP+TLS хендшейков к Postgres при
   каждом открытии вкладки**, а не «переиспользование соединения».

4. **PostgreSQL `max_connections`** по умолчанию = 100; в `deploy/karaoke-db/`
   явных override'ов на этот параметр нет. С учётом того, что `karaoke-web`
   держит свой пул HTTP-потоков (Tomcat, дефолт 200), `karaoke-app` — свой
   (тоже ~200), плюс `KaraokeProcessWorker` для очереди заданий, плюс
   sync LOCAL↔SERVER (`SyncTarget`) — в пике открытия вкладки «Статистика»
   количество одновременных соединений легко превышает 100.

5. **`KaraokeConnection.getConnection()` маскирует сбой**: при исключении
   (`FATAL: too many clients already`) метод печатает `println` и возвращает
   `null` (`KaraokeConnection.kt:36-47`). Большинство вызывающих методов
   (`StatBySong.kt`, `MonetizationStats.kt`) корректно обрабатывают `null` и
   возвращают пустой результат — **но пользователь видит «пустые графики»**,
   а не «БД перегружена». Это ухудшает диагностику и UX.

### Что УЖЕ исправлено (Pass 56, до этой спеки)

- `StatsController.withDb { ... }` — приватный helper, который гарантирует
  `close()` физического соединения после каждого запроса (строки 34-47 +
  комментарий 27-33). **Без него дашборд с ~11 эндпоинтами исчерпывал пул
  Postgres за несколько загрузок** — это уже сделано.
- `KaraokeConnection.closeThreadConnection()` — корректное закрытие
  ThreadLocal-кэша для короткоживущих потоков
  (`KaraokeConnection.kt:64-73`, спека `091-fix-connection-leak`).
- `KaraokeConnection` теперь использует `ThreadLocal` (а не общий `@Volatile`
  connection), спека `087-fix-shared-db-connection` — это лечит
  `SocketTimeoutException` от конкурентного использования одного канала.

### Что НЕ покрыто этой частичной правкой

1. **10–12 параллельных запросов при `mounted()`** — `withDb` лишь закрывает
   соединения, но не уменьшает их число. Каждый реквест — это всё ещё
   отдельный TCP-хендшейк к Postgres (без пула).
2. **Нет connection pool** — `DriverManager.getConnection()` напрямую, никакого
   HikariCP. Каждый запрос = новый TCP+TLS+startup_message+ready_for_query.
   При 10 параллельных запросах от одного пользователя это 10 handshake'ов
   за ~50–200 мс каждый — Postgres видит пиковую нагрузку.
3. **Нет кеширования агрегатов** — `/api/stats/summary`,
   `/api/stats/timeseries`, `/api/stats/channels` возвращают
   **одинаковые данные** в течение минут. Каждое открытие вкладки =
   полный re-compute COUNT(*) на `tbl_events` (таблица растёт: ~18k+ строк
   за счёт `tbl_events` и десятков тысяч событий).
4. **Lazy load для неактивных табов не реализован** — все 7 вкладок
   (`KPI`, `Монетизация`, `Динамика`, `Разбивки`, `География`, `Пользователи`,
   `Слушают`) грузят данные одновременно при `mounted()`. Активна только одна.
5. **Ошибка «too many clients» неотличима от «БД лежит»** — `getConnection()`
   возвращает `null` одинаково в обоих случаях; фронт показывает пустые
   графики без подсказки «retry позже / БД перегружена».

### Соседние спеки (контекст)

- `specs/087-fix-shared-db-connection/` — общий connection → ThreadLocal.
- `specs/091-fix-connection-leak/` — `closeThreadConnection()` для
  одноразовых потоков.
- `specs/167-fix-share-claim-500/` — пример фикса маскировки исключений
  под «user-friendly» 404 (применимо как паттерн для `/api/stats/*`).
- `AGENTS.md` секция «Счётчики главной страницы (StatBySong)» — описывает
  только публичные карточки (`HomeView.vue`+`main.html`), не дашборд
  `StatsView.vue`.

## Clarifications

### Session 2026-08-12

Все 3 ключевых вопроса закрыты решениями пользователя (см. чеклист
`checklists/requirements.md`):

- **Q1 — основной механизм снижения нагрузки**: **только frontend
  (lazy load табов + кеш агрегатов)**. HikariCP **не включается** в эту
  спеку — выносится в отдельную задачу, если метрики после фикса
  покажут недостаточность lazy load + кеша.

- **Q2 — формат ответа при сбое БД**: **`503 Service Unavailable` + заголовок
  `Retry-After: 10` + тело `{"errorCode":"stats.unavailable","retryAfterSeconds":10}`**
  (по аналогии с `167-fix-share-claim-500` и `share.internal`). Фронт
  показывает `<DbOverloadBanner>` вместо пустых графиков.

- **Q3 — TTL и инвалидация кеша**: **TTL = 60 секунд, без SSE-инвалидации**.
  In-process `ConcurrentHashMap<K, Pair<Any, Instant>>` (или эквивалент).
  Агрессивный TTL не нужен — данные статистики исторические по природе.
  SSE-инвалидация не подключается (SSE-канал используется для sync LOCAL↔SERVER,
  не для дашборда).

> Все `[NEEDS CLARIFICATION]` маркеры в `### Requirements` ниже заменены
> на конкретные формулировки.

### Session 2026-08-12 (clarify)

Дополнительные уточнения по результатам `/speckit.clarify`:

- Q: Какой scope кеша агрегатов (FR-004)? Перечисленные 5 endpoint'ов —
  это весь список или нужно расширить? → A: **Только чистые агрегаты**
  (без query-параметров): `/summary`, `/timeseries`, `/channels`,
  `/countries`, `/referrers`, **плюс `/monetization`**. Параметризованные
  endpoint'ы (`/by-song`, `/top-users`, `/webevents` с фильтрами,
  `/by-detail`, `/top-listened`, `/monetization/top-songs`, drill-down
  `/user-events`/`/song-events`) НЕ кешируются в этой спеке — они
  грузятся по lazy load (1 запрос за раз, а не 10 параллельных), и
  cache key explosion не оправдан.
- Q: Какой уровень observability нужен для нового поведения (логирование
  cache hit/miss, метрики для SC-001..SC-005)? → A: **Только структурные
  логи SLF4J** (`log.warn` для `stats.unavailable`, `log.debug` для cache
  hit/miss) **+ debug endpoint `/api/stats/debug`** для ручной диагностики
  (по образцу `share/debug` из спеки 167). Без Prometheus/Micrometer и
  других новых зависимостей — существующей инфры логирования достаточно
  для post-hoc анализа SC-001..SC-005. Если позже понадобятся количественные
  метрики (latency histogram, cache hit rate) — отдельная задача.
- Q: Нужен ли rate limit на retry из DbOverloadBanner (защита от случайного
  «долбёжки» кнопкой)? → A: **Только клиентский троттлинг**: после показа
  баннера кнопка «Retry now» disabled на `retryAfterSeconds` секунд с
  обратным отсчётом + один автоматический retry через `retryAfterSeconds`
  после открытия баннера. Никаких server-side rate limits — лишний код
  без явной угрозы (admin-зона, один пользователь). При F5 страницы
  countdown сбрасывается и цикл повторяется.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Администратор открывает «Статистику» без перегрузки БД (Priority: P1)

Администратор сайта в `webvue3` нажимает на пункт меню «Статистика» и
получает активную вкладку (по умолчанию `KPI`) с данными не позднее **2 секунд**
после клика. В логах `karaoke-app` за время загрузки **не появляется** сообщений
`FATAL: sorry, too many clients already`. Параллельные пользователи публичного
сайта не получают `5xx` из-за открытого дашборда.

**Why this priority**: Это прямой симптом, который привёл к открытию задачи.
Без фикса дашборд становится неработоспособным при >5 одновременных
администраторах, а публичный сайт теряет доступность для всех.

**Independent Test**: Открыть вкладку «Статистика» в `webvue3`, проверить
логи `karaoke-app` за последнюю минуту (`docker logs karaoke-app --since 1m |
grep "too many clients"`) — должно быть 0 совпадений. Повторить 10 раз подряд
(F5) — должно остаться 0.

**Acceptance Scenarios**:

1. **Given** Postgres `max_connections = 100`, **When** один администратор
   открывает вкладку «Статистика» (любую из 7 подвкладок), **Then**
   `karaoke-app` открывает **не более 3** физических JDBC-соединений
   одновременно на этот дашборд.
2. **Given** 5 администраторов одновременно открыли вкладку «Статистика»,
   **When** любой из них делает F5, **Then** в логах `karaoke-app` нет
   `FATAL: sorry, too many clients already` за последние 5 минут.
3. **Given** `pg_stat_activity` показывает 80+ активных соединений
   (например, идёт sync LOCAL↔SERVER), **When** администратор открывает
   вкладку «Статистика», **Then** дашборд либо загружается за ≤5 секунд,
   либо показывает явное «БД перегружена, retry через 10 секунд» (а не
   молча пустые графики).

---

### User Story 2 — Активная вкладка «KPI» готова ≤2с, остальные — по запросу (Priority: P1)

Администратор открывает «Статистику» — **активная подвкладка** (`KPI` по
умолчанию) показывает данные за ≤2 секунды. Остальные 6 подвкладок
(`Монетизация`, `Динамика`, `Разбивки`, `География`, `Пользователи`,
`Слушают`) **не запускают запросы к БД** до момента переключения на них
(или до явной кнопки «Обновить»).

**Why this priority**: Это самый дешёвый способ убрать 90% параллельных
запросов (10 → 1) — без новой инфраструктуры (HikariCP) и без риска
поломки контракта с уже работающими вкладками.

**Independent Test**: В DevTools → Network посмотреть, сколько запросов к
`/api/stats/*` уходит при первом `mounted()`. Должно быть ≤2
(`/summary` + `/monetization`, как самые «лёгкие» для дефолтной KPI-вкладки).
Остальные вкладки — при первом переключении на них.

**Acceptance Scenarios**:

1. **Given** ни одна вкладка не была активна, **When** администратор открывает
   «Статистику», **Then** на `/api/stats/*` уходит **≤2 запроса** (для
   дефолтной активной вкладки KPI) — не 10–12.
2. **Given** вкладка «KPI» уже загружена, **When** администратор кликает
   «Монетизация», **Then** уходит запрос на `/api/stats/monetization*` (≤2
   endpoint'а). Если эта вкладка была активна в течение последних 60 секунд
   — данные берутся из кеша без HTTP-запроса.
3. **Given** вкладка загружена, **When** администратор нажимает кнопку
   «Обновить» (refresh внутри таба) — текущая вкладка re-fetch'ится,
   остальные не трогаются.

---

### User Story 3 — Понятная обратная связь при сбое БД (Priority: P2)

При недоступности БД (например, временно превышен `max_connections`) фронт
**не показывает «пустые графики как будто данных нет»** — он показывает
понятное сообщение «БД перегружена, retry через N секунд» с HTTP-статусом
`503` и `errorCode: "stats.unavailable"`.

**Why this priority**: Это улучшает UX и диагностику, но не убирает саму
проблему перегрузки. Можно отложить в backlog, если effort на основной
фикс (US1+US2) превышает разумный.

**Independent Test**: Временно снизить `max_connections` в Postgres до 5
через `ALTER SYSTEM SET max_connections = 5; SELECT pg_reload_conf();` (или
просто занять 99 соединений через `psql`). Открыть «Статистику» — должно
появиться сообщение «БД перегружена» вместо пустого графика.

**Acceptance Scenarios**:

1. **Given** Postgres отказывает в новом соединении (`too many clients`),
   **When** фронт делает запрос к `/api/stats/summary`, **Then** бэк
   возвращает `503 Service Unavailable` с телом
   `{"errorCode":"stats.unavailable","retryAfterSeconds":10}` и заголовком
   `Retry-After: 10`.
2. **Given** бэк вернул `503 stats.unavailable`, **When** `StatsView.vue`
   получает ответ, **Then** вместо пустого графика показывается компонент
   `<DbOverloadBanner>` с текстом «БД перегружена, retry через 10 секунд»
   и кнопкой «Retry now».

---

### Edge Cases

- Что если Postgres `max_connections` уже снижен (например, до 20 для
  тестирования) — должен ли дашборд работать хотя бы для одного пользователя?
  **Yes** — это базовая гарантия для администратора.
- Что если SSE-канал для дашборда (если будет добавлен) отвалился — кеш не
  инвалидируется, но TTL сам истечёт через 60 секунд.
- Что если параллельно с дашбордом идёт тяжёлая `sync LOCAL↔SERVER` —
  дашборд не должен её прерывать (lazy load табов не блокирует sync-цикл).
- Что если `StatsView.vue` монтируется до того, как Vuex-стор полностью
  инициализирован — dispatch'и уйдут с `undefined`-параметрами, бэк
  отдаст дефолты. Это уже работает, ломать не нужно.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Система MUST отображать активную подвкладку «KPI» (или другую,
  выбранную пользователем) без загрузки данных для неактивных подвкладок.
  *(lazy load табов; убирает 80% параллельных запросов)*.

- **FR-002**: Система MUST использовать **composite endpoint или единый
  loader** для каждой подвкладки: при первом показе таба — один HTTP-запрос
  на таб (а не на каждый график/таблицу внутри таба). Например, для таба
  «Монетизация» — `/api/stats/monetization` + `/api/stats/monetization/top-songs`
  остаются, но «Динамика» должна грузиться одним запросом
  (`/api/stats/timeseries` уже один; для «Разбивки» —
  `/api/stats/by-detail?include=type,channel` или один общий endpoint).

- **FR-003**: Бэкенд MUST отдавать `503 Service Unavailable` с заголовком
  `Retry-After: 10` и телом `{"errorCode":"stats.unavailable","retryAfterSeconds":10}`
  при сбое `KaraokeConnection.getConnection()` (включая `too many clients`).
  Паттерн заимствован из `167-fix-share-claim-500` (`share.internal`).

- **FR-004**: Бэкенд MUST кешировать **только чистые агрегаты** (без
  query-параметров) — `/summary`, `/timeseries`, `/channels`,
  `/countries`, `/referrers`, `/monetization` — с **TTL = 60 секунд**,
  без SSE-инвалидации (in-process `ConcurrentHashMap<K, Pair<Any, Instant>>`
  или эквивалент — Spring `@Cacheable` + Caffeine). Параметризованные
  endpoint'ы (`/by-song`, `/top-users`, `/webevents`, `/by-detail`,
  `/top-listened`, `/monetization/top-songs`, `/user-events`,
  `/song-events`) НЕ кешируются в этой спеке — они загружаются по
  lazy load, и cache key explosion не оправдан.

- **FR-005**: Фронт MUST показывать явный `<DbOverloadBanner>` при получении
  `503 stats.unavailable` от любого `/api/stats/*` endpoint'а, вместо
  отображения пустых графиков «как будто данных нет».

- **FR-006**: Система MUST сохранить существующее поведение приватного
  `StatsController.withDb { ... }` (try-finally с `db.getConnection()?.close()`).
  Рефакторинг lazy load / кеша не должен его сломать.

- **FR-007**: **HikariCP connection pool НЕ включается** в эту спеку
  (вынесено в отдельную задачу по решению Q1). Если после FR-001..FR-006
  метрики покажут, что `pg_stat_activity` всё ещё близок к `max_connections`,
  открыть задачу `XXX-fix-stats-connection-pool` с HikariCP.

- **FR-008**: Существующие 174 вызова `KaraokeConnection.getConnection()`
  по всему `karaoke-app` MUST продолжать работать без изменений (рефакторинг
  stats-эндпоинтов не должен ломать другие модули — `ApiController`,
  `SongEditorController`, `SponsrSyncController` и т.п.).

- **FR-009**: Документация MUST обновляться в `docs/features/stats.md`:
  - добавить секцию «Lazy load табов и composite endpoints»;
  - добавить секцию «Кеш агрегатов»;
  - обновить секцию «Известные ловушки» с новой ловушкой
    «10+ параллельных HTTP при mounted()».

- **FR-010**: Бэкенд MUST логировать `stats.unavailable` через
  `log.warn` (с полями `endpoint`, `cause`), cache hit/miss через
  `log.debug` (с полями `endpoint`, `params`, `hit=true|false`) — через
  существующий SLF4J, без новых зависимостей. Endpoint
  `POST /api/stats/debug` MUST возвращать JSON с состоянием кеша
  (размер, ключи, возраст записей) и `pg_stat_activity`-счётчиком для
  ручной диагностики при инцидентах (по образцу `share/debug` из
  спеки 167, без auth — endpoint в admin-зоне `permitAll`).

- **FR-011**: Фронт MUST троттлить кнопку «Retry now» в `<DbOverloadBanner>`:
  после показа баннера кнопка disabled на `retryAfterSeconds` секунд
  с обратным отсчётом; один автоматический retry через `retryAfterSeconds`
  после открытия баннера; при F5 страницы countdown сбрасывается.
  Это защищает БД от случайного «долбёжки» без введения server-side
  rate limiter'а.

### Key Entities *(include if feature involves data)*

- **`DbOverloadBanner`**: новый Vue-компонент в `webvue3/src/components/Stats/`,
  показывает текст «БД перегружена, retry через N секунд» + кнопку retry.
  Props: `retryAfterSeconds: number`. Emits: `retry` (click).
  Кнопка disabled на `retryAfterSeconds` секунд после показа с обратным
  отсчётом; один автоматический retry через `retryAfterSeconds` после
  открытия (FR-011).

- **`StatsCache`**: новый in-process кеш в `karaoke-app`, класс-обёртка над
  `ConcurrentHashMap<CacheKey, CacheEntry>` (или Spring `@Cacheable` с
  `Caffeine`). TTL per-key. Метод `invalidateAll()` для сброса (не
  вызывается из кода на этом этапе, зарезервировано под SSE-инвалидацию
  в будущем).

- **`CacheKey`**: data-class, equals+hashCode по `(endpointName, params)`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: При открытии вкладки «Статистика» одним администратором
  **количество HTTP-запросов к `/api/stats/*` в первые 2 секунды —
  ≤3** (сейчас 10–12). Измеримо: DevTools → Network panel или
  `pg_stat_activity` фильтр по `application_name='karaoke-app'`.

- **SC-002**: При 10 F5 подряд в течение 30 секунд **0 сообщений**
  `KaraokeConnection getConnection Exception` в `docker logs karaoke-app`.
  Проверка: `docker logs karaoke-app --since 30s | grep -c "too many clients"`
  → `0`.

- **SC-003**: При активной нагрузке (5 администраторов × дашборд +
  публичный сайт 100 RPS) `pg_stat_activity` показывает **≤70**
  одновременных соединений к Postgres (вместо текущих пиков 100+).
  Проверка: `SELECT count(*) FROM pg_stat_activity;` при симуляции
  нагрузки через `siege` / `wrk`.

- **SC-004**: **p95 времени ответа** `/api/stats/summary` — ≤500 мс
  (сейчас — без замера, но с кешем TTL=60s должен быть <10 мс на cache hit).
  Проверка: добавить метрику в `/debug` или внешний `wrk`.

- **SC-005**: При искусственной перегрузке БД (`pg_terminate_backend` до
  `max_connections`) **100% вкладок** показывают `<DbOverloadBanner>` вместо
  пустых графиков в течение ≤5 секунд после первого отказа.

- **SC-006**: Существующие функциональные тесты (если есть) и ручной
  сценарий «открыть → F5 → переключить таб → обновить → закрыть» —
  проходят без регрессий. Проверка: ручной чек-лист в `quickstart.md`
  спеки (этап `/speckit.plan`).

## Assumptions

- Пользователь работает на `dev-pc` под OS-пользователем `dev` — это даёт
  право агенту пересобирать/перезапускать локальные контейнеры
  `karaoke-app`, `karaoke-web`, `karaoke-db` без отдельного согласия
  (см. Constitution, Principle VIII / AGENTS.md «Ограничения агента →
  Разрешено», п. 6). На любой другой машине пересборка — только по прямому
  согласию пользователя.
- Текущий Postgres `max_connections = 100` (дефолт) не меняется в рамках
  этой спеки. Если окажется, что после FR-001..FR-006 всё ещё тесно —
  вынести в отдельную задачу с обсуждением (повышение `max_connections`
  ≠ решение, это маскировка).
- SSE-канал для дашборда НЕ добавляется в этой спеке. TTL-кеш без явной
  инвалидации достаточен для 60-секундного окна.
- Lazy load табов НЕ меняет существующий контракт URL — endpoint'ы
  остаются те же, меняется только то, КОГДА они вызываются с фронта.
- HikariCP / пул соединений — **отдельная задача** (см. FR-007), если
  потребуется. В этой спеке основной фокус — frontend lazy load + backend
  TTL-кеш + обработка `503 stats.unavailable`.
