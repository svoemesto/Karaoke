# Public controllers: PublicApiController (13 endpoints)

> **Домен**: [karaoke-web](../domain.md)
> **Компонента**: `PublicApiController` — главный public API контроллер.


## Ответственность | Responsibility


`PublicApiController` — главный public API контроллер.

## Файл

`karaoke-web/.../controllers/PublicApiController.kt` (**1297 строк**,
самый длинный public-контроллер)

## Интерфейсы и Контракты | Interfaces and Contracts

Class-level `@RequestMapping("/api/public")`; ниже — пути
относительно него. Все ответы — JSON, кроме `/zakroma/stream`
(NDJSON) и `/picture` (302-redirect).

### Endpoints (13)

| # | URL | Метод | Назначение |
|---|---|---|---|
| 1 | `/api/public/stats` | GET | Сводная статистика (см. [store-stats.md](../../../system/frontend/store-stats.md)) |
| 2 | `/api/public/authors` | GET | Список авторов (дайджест) |
| 3 | `/api/public/authors-tiles` | GET | Тайлы авторов для главной |
| 4 | `/api/public/zakroma` | GET | Закрома автора (страница) |
| 5 | `/api/public/zakroma/stream` | GET (NDJSON) | **Streaming** Закромов (см. [composable-zakroma-stream.md](../../../system/frontend/composable-zakroma-stream.md)) |
| 6 | `/api/public/zakroma/stream/metrics` | POST | Метрики стрима (FR-FE-010) |
| 7 | `/api/public/songs` | GET | Список песен (с пагинацией) |
| 8 | `/api/public/song/{id}` | GET | Одна песня (см. [song-public-dto.md](../../integration/components/song-public-dto.md)) |
| 9 | `/api/public/events` | POST | Регистрация события (см. [composable-engagement-tracking.md](../../../system/frontend/composable-engagement-tracking.md)) |
| 10 | `/api/public/song-picture/{id}` | GET | URL обложки песни |
| 11 | `/api/public/song-vk-image/{id}` | GET | URL превью VK |
| 12 | `/api/public/picture` | GET | URL обложки автора/альбома |
| 13 | `/api/public/authors/{authorId}/albums` | GET | Тайлы альбомов автора (кеш 60с, spec 356) |

## Логика и Алгоритмы | Logic and Algorithms

Потоки сверены с `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt`.

**Общий фильтр видимости**: `onlyPublishedFor(request)` =
`siteUserResolver.resolve(request)?.isEditor != true`. Аноним и обычный
пользователь видят только готовые песни (`id_status >= 6`); редактор —
без фильтра статуса. Проверка именно `!= true`, поэтому отсутствующий или
невалидный токен попадает в ветку «фильтр действует».

**`GET /stats`**: сначала пишет аналитическое событие
`EventType.CALL_REST` / `RestName.MAIN` через `mainController.doRegisterEvent`
(с `anonId` и `referrer`), затем возвращает согласованный снимок `StatBySong`
(`getCounters`): `onSponsr` (`collection`), `freeNow`, `subscriptionOnly`,
`inWork`, `total` и `state` (`ready` / `warming_up` / `error`, BR-016).
Формат ответа — `Map<String, Any>` (было `Map<String, Int>`); пять прежних
ключей не менялись, `state` добавлен, чтобы потребитель отличал «нет данных» и
«ошибку БД» от настоящего нуля. HTTP-статус остаётся `200` — публичная страница
не падает из-за статистики.

**`GET /authors`**: параметр `scope` переводится в
`isSpecialOrderFilter` (`special` → `true`, `main` → `false`); далее
выборка авторов идёт с учётом `onlyPublishedFor`.

**Кеши** (обе ветки — `ConcurrentHashMap`, запись иммутабельная
`(value, expiresAtMs)`):

- `/authors-tiles` — `authorsTilesCache`, `CACHE_TTL_MS = 30 * 60 * 1000`
  (30 мин), kill-switch — свойство
  `karaoke.public.authors-tiles-cache.enabled`. Инвалидация — через
  `StatBySong.consumeDirty` после `StatBySong.markDirty` (save/sync песни).
  Пустые результаты не кешируются, поэтому cache miss повторит попытку.
- `/authors/{authorId}/albums` — отдельный `albumsTilesCache`,
  `ALBUMS_CACHE_TTL_MS = 60 * 1000` (60 с), ключ
  `scope:authorId:onlyPublished:includeSkipped`.

**`GET /zakroma/stream`** — `produces = ["application/x-ndjson"]`,
`ResponseEntity<StreamingResponseBody>`:

1. Регистрирует `CALL_REST` / `RestName.ZAKROMA` с
   `parameters = {author, stream: true}` (+ `anonId`, `referrer`).
2. `onlyPublished = onlyPublishedFor(request)`, а
   `canSeeSkipped = siteUserResolver.resolve(request)?.canWorkWithSkipped ?: false`
   (для анонима — `false`, spec 293).
3. Первым фреймом пишется `meta` (до загрузки данных), чтобы фронт сразу
   знал `expectedCount`. При `albumId` знаменатель — число песен альбома,
   и он авторитетен на сервере: присланный фронтом `expectedCount` не
   используется; для гостя берётся `ready_song_count`, для редактора —
   `total_song_count` (spec 444 / issue #179). Без `albumId` — прежняя
   стратегия: `expectedCount > 0` от фронта принимается как есть, иначе
   считается на сервере (`ready_song_count` / `total_song_count` в
   зависимости от роли).
4. Дальше построчно (NDJSON) отдаются песни; nginx-фрагмент
   `deploy/80to8897.stream-addition.frag` отключает буферизацию chunked,
   иначе «real-time» не будет.

**`GET /songs`**:

1. Фильтры складываются в `attr`: `song_name`, `text`, `song_album`.
   Поиск по `author` сначала резолвится через `Author.resolveByTerm`
   (имя или алиас — солист/участник группы) в `attr["author_in"]`
   (склейка `Song.AUTHOR_IN_DELIMITER`); если совпадений в `tbl_authors`
   нет — фолбэк на строгое `attr["author"]`.
2. `onlyPublishedFor(request)` добавляет `attr["id_status"] = ">=6"`.
3. Пагинация (spec 262): `page < 1` → `1`; `pageSize` — whitelist
   `[10, 25, 35, 50, 100]`, иначе `35`. Обёртка `PagedSongsDto`
   (`totalCount`, `hasMore`) включается, только если клиент передал
   `page` или `pageSize`; иначе сохраняется старый формат
   `List<SongPublicDto>`.
4. `count(*)` считается по фильтру без `limit/offset` (иначе вернулся бы
   размер страницы), а `items` — с `limit/offset`.
5. Если суммарная длина поисковых строк `< 3` — возвращается пустой
   список без запроса в БД.

**`POST /events`**: делегирует в `mainController.doRegisterEvent(data,
request, userId)` и возвращает `{ok, meta}`.

**Значения доступа не возвращает** (спека 557, OpenProject #358, аудит BR-018):
`meta` всегда `null`. Раньше здесь минтился «скрытый жест» —
`gestureUnlockService.registerClick(clientId, songId, field, shiftKey)`
при `eventType == "clickToLink"` и `linkType == "songMeta"`, и результат
кладётся в `meta`, а клиент по нему открывал плеер платной песни без оплаты.
Механика жеста удалена из `PlayerGestureUnlockService`, а право на полный
доступ проверяется в момент отдачи байт
(см. [public-controllers-4.md](public-controllers-4.md)). Обычный клик-трекинг
по метаданным песни продолжает работать без изменений — состав события
(`eventType`, `linkType`, `linkName`, `songId`, `shiftKey`, `clientId`) тот же.

**Точечная загрузка и картинки — та же видимость (аудит BR-013, #378).**
`GET /song/{id}`, `/song-picture/{id}` и `/song-vk-image/{id}` раньше отдавали
ЛЮБУЮ песню по id: без `id_status >= 6` и без проверки тега `SKIP`, тогда как
выборки `/songs`, `/zakroma`, `/authors-tiles` фильтруют. Анонимный посетитель
получал полный текст неопубликованной песни. Теперь для не-редактора
(`onlyPublishedFor(request)`) неопубликованная/SKIP-песня — `404` (как
несуществующая, факт существования черновика не раскрывается); редактор с
site-user токеном видит её по-прежнему. Правило вынесено в
`isPubliclyVisible(song)` (`idStatus >= 6 && !tags.contains("SKIP")`).

**Ограничение батча метрик (аудит BR-015, #378).**
`POST /api/public/zakroma/stream/metrics` принимал массив любого размера: один
запрос раздувал heap десериализацией и `tbl_events` INSERT'ами. Теперь батч
длиннее `MAX_STREAM_METRICS_BATCH = 500` отклоняется с `413` и в БД не пишется.

**Картинки**: `/song-picture/{id}` и `/song-vk-image/{id}` отдают байты
(BufferedImage из MinIO / загрузка из хранилища); на них действует
`RateLimitInterceptor` — 60 запросов/мин на IP (регистрируется в
`WebMvcConfig`). `/picture?file=...` не отдаёт контент сам: он делает
`302 FOUND` на nginx-прокси `/minio/karaoke/<path>`, кодируя каждый
сегмент пути по отдельности (`+` → `%20`); это нужно из-за MTU 1450 на
хосте — Java в Docker с MTU 1500 при прямом обращении к MinIO терял бы
крупные TCP-пакеты.

## Hot paths

- **`/api/public/songs`** — каждое открытие главной.
- **`/api/public/song/{id}`** — каждое открытие страницы песни.
- **`/api/public/zakroma/stream`** — каждое открытие Закромов (chunked).
- **`/api/public/events`** — каждое событие (с Sampling).

## Архитектурные решения

### Решение 1: NDJSON streaming для zakroma

`/api/public/zakroma/stream` использует `produces = "application/x-ndjson"`
(см. [composable-zakroma-stream.md](../../../system/frontend/composable-zakroma-stream.md)) —
real-time progress (FR-FE-001..011).

### Решение 2: rate limiting

`/api/public/song-picture/{id}` и `/api/public/song-vk-image/{id}` —
rate limit 60/min per IP (через `RateLimitInterceptor`, см.
[config.md](config.md)).

### Решение 3: events через Sampling

`/api/public/events` — все события проходят через
`SamplingFilter` (см. [composable-engagement-tracking.md](../../../system/frontend/composable-engagement-tracking.md))
— 1/N sampling.

## Зависимости | Dependencies

- [public-controllers.md](public-controllers.md) — обзор всех 19.
- [storage domain](../../storage/domain.md) — картинки.
- [sse domain](../../sse/domain.md) — не подключен напрямую
  (SSE через karaoke-app).

## Известные TODO

- [ ] **Каждый endpoint** — детальный contract (Pass 343+).
- [ ] **Кеширование** — `Cache-Control` headers (Pass 343+).

## Changelog

- **BR-013/BR-015** (2026-10-24, OpenProject #378, пакет PKG-S2-15): точечная загрузка песни и
  обе картинки применяют то же правило видимости, что и `/songs` (`idStatus >= 6`, без `SKIP`) —
  не-редактору 404; батч `/zakroma/stream/metrics` ограничен 500 событиями (413). Автор: agent (Karaoke).
- **2026-10-24** (спека 557, OpenProject #358, аудит BR-018): `POST /events` больше не
  возвращает значение доступа (`meta` всегда `null`) — «скрытый жест» удалён, обычный
  клик-трекинг по метаданным песни работает без изменений.
- **Pass 481** (2026-09-27, spec `481-knowledge-domain-karaoke-web`): секция
  «Endpoints (12)» перенесена под «Интерфейсы и Контракты»; добавлена
  «Логика и Алгоритмы» с проверенными по коду потоками (`stats`, кеши
  `authors-tiles`/`albums`, NDJSON-стрим, пагинация `/songs`, redirect
  `/picture`); [WARN] про 13-й маппинг
  `/authors/{authorId}/albums` и фактический размер файла (1297 строк).
  Автор: agent (Karaoke).
- **Pass 475** (2026-09-09): Initial. Автор: agent (Karaoke).
