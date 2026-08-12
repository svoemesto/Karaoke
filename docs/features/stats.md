# Статистика (Stats)

> **Status**: active
> **Feature Key**: stats
> **Last Updated**: 2026-08-12 (specs/174-fix-stats-connection-leak: lazy load табов + 60s TTL кеш + `503 stats.unavailable` banner)

## Что делает

Считает и отображает статистику по коллекции песен, по событиям
(прослушивания, лайки, репосты, шеры), по доходам (Sponsr-подписки) и
по реферерам (гео, источники трафика).

Используется в:
- Главная страница (4 карточки: «В коллекции», «В открытом доступе»,
  «По подписке», «В работе»).
- `webvue3` админ-панель → раздел «Статистика» (KPI, графики, top-100).
- `karaoke-public` публичный сайт (карточки на главной).

## Зачем

Владельцу сайта нужно видеть:
- Сколько песен реально готово к показу (а не «всего в БД»).
- Сколько из них доступно бесплатно vs по подписке.
- Откуда идёт трафик (Telegram, прямые заходы, поисковики).
- Какие песни популярны (top-100 по прослушиваниям).
- Какие рефереры приносят больше всего конверсий (гео, каналы).

## Как работает (кратко)

### Счётчики главной страницы (`StatBySong`, модуль karaoke-web)

| Лейбл | Формула (SQL, без учёта SKIP) |
|-------|-------------------------------|
| **Песен в коллекции** | `count(*) WHERE id_status>=6 AND btrim(source_markers)!=''` |
| **В открытом доступе** | подмножество «коллекции» с истёкшим `publish_date`/`publish_time` |
| **По подписке** | «коллекция» − «в открытом доступе» |
| **В работе** | «всего в БД» − «в коллекции» |
| **Всего в БД** | `count(*)` без SKIP |

**SKIP-фильтр** (одинаков во всех формулах):
```sql
(tags IS NULL OR NOT ('SKIP' = ANY(string_to_array(upper(coalesce(tags,'')), ' '))))
```

SQL-аппроксимация готовности премиум-плеера (точная проверка живёт в
`PublicPlayerController.stemsReady()` и делает 2 HEAD-запроса в MinIO на
песню — слишком дорого для 18k+ записей на главной).

**Согласованность с листингами** (specs/013-song-status-filter): до фичи 013
счётчик «Песен в коллекции» использовал `id_status>=3`, но публичные
закрома и поиск (`PublicApiController`/`MainController` в `karaoke-web`)
показывали песни любого статуса — счётчик на главной мог показывать меньше,
чем реально было видно в закромах/поиске. `Zakroma.getZakroma`/
`getZakromaBySpecialOrder` (параметр `onlyPublished`) и прямые вызовы
`Song.loadListFromDb` в публичных read-путях используют тот же порог, что и
этот счётчик — см. [special-orders.md](./special-orders.md) для деталей
параметра.

**Расширенный жизненный цикл статуса** (specs/022-song-status-lifecycle,
2026-07-29): `id_status` теперь имеет 7 значений (0-6) вместо 4 (0-3) —
0 новая, 1 текст найден, 2 текст проверен (орфография/пунктуация), 3 текст
проверен (слова соответствуют песне), 4 маркеры расставлены, 5 маркеры
проверены, 6 песня готова. Порог «готова» перенесён с `id_status>=3` на
`id_status>=6` — везде, где раньше использовался старый порог (эта таблица,
`PublicPlayerController.stemsReady()`, `Zakroma`/`Song.loadListFromDb`
публичные read-пути). Значения 3-5 могут временно встречаться у песен
в процессе производства и не считаются «готовыми».

### Кеш в AtomicInteger

`StatBySong` (karaoke-web) хранит `cachedTotal/Collection/OnAir/Exclusive/InWork` в
`AtomicInteger` для мгновенного ответа без обращения к БД. Обновление:
- `StatsCacheScheduler.warmUp()` (`@PostConstruct`) — cold start.
- `@Scheduled cron "0 0 * * * *"` — каждый час.

Spring `@Cacheable` намеренно НЕ подключён (нет `@EnableCaching`).
Проще держать инвариант «endpoint отвечает без обращения к БД» явно
через `AtomicInteger + Scheduled`.

### Кеш агрегатов на dashboard (StatsCache, Pass 51)

Спека [174-fix-stats-connection-leak](../../specs/174-fix-stats-connection-leak/spec.md)
добавляет **in-process TTL-кеш на 60 секунд** для 6 чистых агрегатов
в `StatsController` (FR-004):

| Endpoint | Response body | TTL |
|----------|---------------|-----|
| `/api/stats/summary` | `{summary: {...}}` | 60s |
| `/api/stats/timeseries` | `{items: [...]}` (days=30, mode=all defaults) | 60s |
| `/api/stats/channels` | `{items: [...]}` | 60s |
| `/api/stats/countries` | `{items: [...]}` | 60s |
| `/api/stats/referrers` | `{items: [...]}` | 60s |
| `/api/stats/monetization` | `{summary: {...}}` | 60s |

Реализация — `services/StatsCache.kt` (singleton, `ConcurrentHashMap<StatsCacheKey, StatsCacheEntry>`,
SLF4J `log.debug` для cache hit/miss). Lazy expiration через проверку
`expiresAt > Instant.now()` при чтении. Thread-safety — atomic `ConcurrentHashMap`.

Параметризованные endpoint'ы (`/by-song`, `/top-users`, `/webevents`, `/by-detail`,
`/top-listened`, `/monetization/top-songs`, `/user-events`, `/song-events`) — НЕ
кешируются в этой спеке (cache key explosion не оправдан; см. FR-004).

HikariCP не подключается в этой спеке (FR-007) — вынесено в отдельную
задачу `XXX-fix-stats-connection-pool`, если метрики после фикса покажут
недостаточность lazy load + кеша.

### Lazy load табов в `StatsView.vue` (FR-001)

До фикса `StatsView.vue:mounted() → reloadAll()` запускал **10-12 параллельных
HTTP-запросов** к `/api/stats/*` при каждом открытии вкладки — каскадно
исчерпывая `pg max_connections = 100` за несколько загрузок с логом
`FATAL: sorry, too many clients already`.

После фикса `mounted()` вызывает `loadDataForActiveTab()` — загружает данные
только активной вкладки (дефолт `KPI` = 1-2 запроса вместо 10-12). Watch
на `activeTab` подгружает данные при переключении. Метод `reloadAll()` удалён
как footgun (10-12 параллельных HTTP).

Параметр «Обновить» в toolbar вызывает `loadDataForActiveTab()` для текущей
вкладки — тот же путь, что при первом открытии. Поддерживается 60s TTL
на фронте (Vuex `lastLoadedAt`) — в течение окна повторный открыватель таба
short-circuit'ит и не шлёт HTTP.

Кнопка «Обновить всё» (10-12 параллельных HTTP) убрана из toolbar — см.
`AGENTS.md` секцию «Известные ловушки».

### Обработка сбоя БД — `503 stats.unavailable` (US3)

При сбое `KaraokeConnection.getConnection()` (включая `too many clients already`)
бэкенд возвращает `503 Service Unavailable` с заголовком `Retry-After: 10`
и телом `{"errorCode":"stats.unavailable","retryAfterSeconds":10,"endpoint":"/api/stats/..."}` —
по образцу спеки 167 (`share.internal`).

Фронт (`webvue3`) показывает компонент `<DbOverloadBanner>` вместо пустых
графиков — текст «БД перегружена. Retry через N секунд» + кнопка
«Retry now» (FR-005). Кнопка disabled на `retryAfterSeconds` с обратным
отсчётом; один авто-retry через `retryAfterSeconds` (FR-011).

В кеше не сохраняется failed body — `StatsCache.put` вызывается только
после успешного `compute()`, чтобы не засорить кеш 503-ответами.

Debug-endpoint `POST /api/stats/debug` (FR-010) — для ручной диагностики:
возвращает `cacheSize + cacheKeys (с age/expired) + pgActiveConnections +
pgMaxConnections + timestamp`. `permitAll()` — admin-зона, доступ по
сети. Без auth, без секретов.

### Потребители

- `PublicApiController.kt` → `@GetMapping("/stats")` → JSON для Vuex-модуля
  `stats` (`karaoke-public/src/store/modules/stats.js`).
- `MainController.kt:main()` → атрибуты `onSponsr/onAir/exclusive/inWork/total`
  для Thymeleaf `main.html` (legacy).
- `StatsController.kt` (webvue3) — расширенная статистика для админа.

### События (`tbl_web_event`, `StatsByEvents`)

Фронт (`karaoke-public`) шлёт события на `/api/event`:
- `play` (старт воспроизведения).
- `pause`, `resume`, `stop`.
- `like`, `dislike`, `share`.
- `subscribe_click`, `pay_click`.

`Stat.processEvent()` валидирует и пишет в `tbl_web_event`. Агрегация:
- `StatsByEvents` (DTO) — по дням/типам.
- `TopListenedSongsTable.vue` — top-100 песен.
- `TopUsersTable.vue` — top-100 пользователей.
- `TimeSeriesChart.vue` — графики за период.
- `GeoReferrers.vue` — карта по странам.
- `TypeChannelBreakdown.vue` — разбивка по каналам/типам.

## Инварианты / правила

- **MUST**: все SQL-формулы учитывают SKIP-фильтр — иначе показываются
  «удалённые по требованию правообладателя» песни.
- **MUST**: `StatsCacheScheduler` обновляется каждый час — НЕ на каждый
  запрос. Иначе нагрузка на БД при 100 RPS = 100 SQL/сек.
- **MUST**: события из `tbl_web_event` агрегируются асинхронно
  (PostgreSQL materialized view или ETL-ночью) — на лету считать по
  18k записей × 100k событий = 30+ секунд.
- **SHOULD**: новые счётчики добавляются как поле в `StatBySong` (karaoke-web) с
  `AtomicInteger` + обновление в `StatBySong.refreshCache()`.

## Известные ловушки

- **10+ параллельных HTTP при `mounted()` (StatsView)** — корневая причина
  `FATAL: too many clients already` в `karaoke-app`. Исправлено в спеке 174
  через lazy load табов + 60s TTL кеш на фронте + 6 кешируемых endpoint'ов
  на бэке. **Не использовать `reloadAll()` / параллельные dispatch —
  только `loadDataForActiveTab()`** (см. FR-001).
- **Открытое окно `getConnection()` всё ещё даёт 1 соединение на запрос** —
  на пике 70+ одновременных соединений в `pg_stat_activity` (SC-003). После
  этой спеки HikariCP не включается (FR-007). Если метрики покажут
  недостаточность — открыть задачу `XXX-fix-stats-connection-pool`.
- **`StatsCacheScheduler` падает с ошибкой → счётчики застывают**.
  Мониторинг: `MonitorCheck.cachedStatsCheck` (если есть) или вручную
  через `/api/stats?debug=1` смотреть timestamp последнего обновления.
- **Событий очень много** (10k/день для активного сайта) — индексы на
  `tbl_web_event` обязательны: `(user_id, event_type, created_at)`,
  `(song_id, created_at)`.
- **GDPR**: события содержат IP и user-agent. Для соответствия GDPR
  нужна политика retention (например, удалять старше 90 дней) +
  анонимизация IP после 30 дней.
- **Failed-response не кладётся в StatsCache** — `put()` вызывается только
  на success. Если бы клали при 503, при откате БД пользователь продолжал
  бы видеть баннер ещё 60 секунд (бэк отдаёт cached 503 на cache hit).
  Это нарушает инвариант «503 = реальный сбой, retry решит».

## Ссылки

### Ключевые классы и файлы

- [`StatBySong.kt`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/StatBySong.kt) — кеш счётчиков главной страницы (`AtomicInteger`), см. «Как работает»
- [`StatsCacheScheduler.kt`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/StatsCacheScheduler.kt) — `@PostConstruct`/`@Scheduled` обновление кеша
- [`StatsByEvents`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/StatBySong.kt) — статистика по событиям (`tbl_web_event`), объявлен в `model/StatBySong.kt` (karaoke-app; не путать с `karaoke-web`'s `StatBySong.kt` выше)
- [`StatsController.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/StatsController.kt) — REST-эндпоинты для админа (`webvue3`); 6 кешируемых через `respondCached()` + обработка `503 stats.unavailable`
- [`StatsCache.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StatsCache.kt) — in-process TTL-кеш для 6 чистых агрегатов (60s, `ConcurrentHashMap`)
- [`StatsCacheKey.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/StatsCacheKey.kt) — data classes `StatsCacheKey` + `StatsCacheEntry`
- [`StatsDebugDto.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/StatsDebugDto.kt) — DTO для `POST /api/stats/debug` (FR-010)
- [`StatsDebugController.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StatsDebugController.kt) — debug endpoint с `pg_stat_activity` счётчиком
- [`StatsResponseUtils.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/StatsResponseUtils.kt) — top-level `statsUnavailableResponse()` для `503 stats.unavailable`
- [`PublicApiController.kt`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt) — JSON для главной `karaoke-public`
- Vue: [`webvue3/src/components/Stats/`](../../webvue3/src/components/Stats/), [`DbOverloadBanner.vue`](../../webvue3/src/components/Stats/DbOverloadBanner.vue), [`karaoke-public/src/views/HomeView.vue`](../../karaoke-public/src/views/HomeView.vue)

### Связанные документы

- [dual-db-sync.md](./dual-db-sync.md) — синхронизация `tbl_web_event` LOCAL↔SERVER
- [ci-lint-enforcement.md](./ci-lint-enforcement.md) — почему нет `@Cacheable`
- [CONTRIBUTING.md](../../CONTRIBUTING.md) — правила оформления кода
- [special-orders.md](./special-orders.md) — `Zakroma.getZakroma`/`getZakromaBySpecialOrder` теперь тоже фильтруют по `id_status>=6`
- [specs/013-song-status-filter/spec.md](../../specs/013-song-status-filter/spec.md) — согласование счётчика «в коллекции» с листингами
- [specs/022-song-status-lifecycle/spec.md](../../specs/022-song-status-lifecycle/spec.md) — расширение жизненного цикла статуса до 7 значений, перенос порога готовности на `>=6`
- [specs/174-fix-stats-connection-leak/spec.md](../../specs/174-fix-stats-connection-leak/spec.md) — lazy load табов + 60s TTL + 503 stats.unavailable + `<DbOverloadBanner>`. SC-001..SC-005. Соседняя задача для контекста: спеки [087-fix-shared-db-connection](../../specs/087-fix-shared-db-connection/spec.md), [091-fix-connection-leak](../../specs/091-fix-connection-leak/spec.md), [167-fix-share-claim-500](../../specs/167-fix-share-claim-500/spec.md) (паттерн `share.internal`).
- [specs/174-fix-stats-connection-leak/quickstart.md](../../specs/174-fix-stats-connection-leak/quickstart.md) — 6 ручных сценариев валидации (SC-001..SC-005)
