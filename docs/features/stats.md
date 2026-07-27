# Статистика (Stats)

> **Status**: active
> **Feature Key**: stats
> **Last Updated**: 2026-07-27

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
| **Песен в коллекции** | `count(*) WHERE id_status>=3 AND btrim(source_markers)!=''` |
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

**Согласованность с листингами** (specs/013-song-status-filter): до этой
фичи счётчик «Песен в коллекции» использовал `id_status>=3`, но публичные
закрома и поиск (`PublicApiController`/`MainController` в `karaoke-web`)
показывали песни любого статуса — счётчик на главной мог показывать меньше,
чем реально было видно в закромах/поиске. Теперь `Zakroma.getZakroma`/
`getZakromaBySpecialOrder` (параметр `onlyPublished`) и прямые вызовы
`Song.loadListFromDb` в публичных read-путях используют тот же порог
`id_status>=3`, что и этот счётчик — см. [special-orders.md](./special-orders.md)
для деталей параметра.

### Кеш в AtomicInteger

`StatBySong` (karaoke-web) хранит `cachedTotal/Collection/OnAir/Exclusive/InWork` в
`AtomicInteger` для мгновенного ответа без обращения к БД. Обновление:
- `StatsCacheScheduler.warmUp()` (`@PostConstruct`) — cold start.
- `@Scheduled cron "0 0 * * * *"` — каждый час.

Spring `@Cacheable` намеренно НЕ подключён (нет `@EnableCaching`).
Проще держать инвариант «endpoint отвечает без обращения к БД» явно
через `AtomicInteger + Scheduled`.

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

- **`StatsCacheScheduler` падает с ошибкой → счётчики застывают**.
  Мониторинг: `MonitorCheck.cachedStatsCheck` (если есть) или вручную
  через `/api/stats?debug=1` смотреть timestamp последнего обновления.
- **Событий очень много** (10k/день для активного сайта) — индексы на
  `tbl_web_event` обязательны: `(user_id, event_type, created_at)`,
  `(song_id, created_at)`.
- **GDPR**: события содержат IP и user-agent. Для соответствия GDPR
  нужна политика retention (например, удалять старше 90 дней) +
  анонимизация IP после 30 дней.

## Ссылки

### Ключевые классы и файлы

- [`StatBySong.kt`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/StatBySong.kt) — кеш счётчиков главной страницы (`AtomicInteger`), см. «Как работает»
- [`StatsCacheScheduler.kt`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/StatsCacheScheduler.kt) — `@PostConstruct`/`@Scheduled` обновление кеша
- [`StatsByEvents`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/StatBySong.kt) — статистика по событиям (`tbl_web_event`), объявлен в `model/StatBySong.kt` (karaoke-app; не путать с `karaoke-web`'s `StatBySong.kt` выше)
- [`StatsController.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/StatsController.kt) — REST-эндпоинты для админа (`webvue3`)
- [`PublicApiController.kt`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt) — JSON для главной `karaoke-public`
- Vue: [`webvue3/src/components/Stats/`](../../webvue3/src/components/Stats/), [`karaoke-public/src/views/HomeView.vue`](../../karaoke-public/src/views/HomeView.vue)

### Связанные документы

- [dual-db-sync.md](./dual-db-sync.md) — синхронизация `tbl_web_event` LOCAL↔SERVER
- [ci-lint-enforcement.md](./ci-lint-enforcement.md) — почему нет `@Cacheable`
- [CONTRIBUTING.md](../../CONTRIBUTING.md) — правила оформления кода
- [special-orders.md](./special-orders.md) — `Zakroma.getZakroma`/`getZakromaBySpecialOrder` теперь тоже фильтруют по `id_status>=3`
- [specs/013-song-status-filter/spec.md](../../specs/013-song-status-filter/spec.md) — согласование счётчика «в коллекции» с листингами
