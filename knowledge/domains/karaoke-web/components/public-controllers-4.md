# Public controllers: Player (8 endpoints)

> **Домен**: [karaoke-web](../domain.md)
> **Компонента**: `PublicPlayerController` — endpoints плеера.


## Ответственность | Responsibility


`PublicPlayerController` — endpoints плеера.

## Файл

`karaoke-web/.../controllers/PublicPlayerController.kt` (589 строк)

## Интерфейсы и Контракты | Interfaces and Contracts

Class-level `@RequestMapping("/api/public/player")`; пути в таблице — от
корня (полный вид, как их вызывают клиенты).

### Endpoints (8)

| # | URL | Метод | Назначение |
|---|---|---|---|
| 1 | `/api/public/player/{id}/access` | GET | Проверка доступа (premium) |
| 2 | `/api/public/player/readiness` | POST | Готовность плеера (массовая) |
| 3 | `/api/public/player/{id}/fileminus.mp3` | GET | Стрим минусовки |
| 4 | `/api/public/player/{id}/filevoice.mp3` | GET | Стрим вокала |
| 5 | `/api/public/player/{id}/filebass.mp3` | GET | Стрим баса |
| 6 | `/api/public/player/{id}/filedrums.mp3` | GET | Стрим ударных |
| 7 | `/api/public/player/{id}/playerdata` | GET | JSON playerdata |
| 8 | `/api/public/player/{id}/playerfile` | GET | Полный playerfile (playerdata + zip) |

Сверено с кодом: все 8 маппингов присутствуют (`access` — стр. 146,
`readiness` — 228, `fileAccompaniment` — 330, `fileVocals` — 341,
`fileBass` — 352, `fileDrums` — 363, `playerData` — 374, `playerFile` — 480).

## Логика и Алгоритмы | Logic and Algorithms

Потоки сверены с
`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicPlayerController.kt`.

**`GET /{id}/access`** — единственное место, где выдаётся токен плеера:

1. `loadSong(id)` → `404`, если песни нет.
2. `ready = stemsReady(song)` = `song.isContentReady`. Это персистентные
   флаги Song (см. `deploy/karaoke-db/26_player_readiness_flags.sql`),
   которые `karaoke-app` проставляет при успешной заливке стема/картинки и
   сверяет `HealthReport`. Обращений к MinIO здесь нет: раньше готовность
   проверялась двумя живыми HEAD-запросами на каждый вызов.
3. `premium = siteUserResolver.resolve(request)?.isEffectivePremium == true` —
   живая проверка на каждый запрос (намеренно не кешируется в токене
   плеера, чтобы бан или снятие премиума действовали немедленно, не дожидаясь
   30-минутного TTL токена).
4. `subscribed = !premium && Subscription.isSubscribedToSong(userId, id, ...)` —
   отдельная ветка: бессрочная подписка на одну песню (`scope=SONG`) не даёт
   `isEffectivePremium`.
5. `shareGuest = ready && session != null && shareLinkService.validateShareSession(session, id) != null`.
6. `canWatch = ready && (song.isFreelyAvailableNow || premium || subscribed || shareGuest)`;
   `canExport = canWatch && premium && !shareGuest` — гость по share-ссылке
   стемы не скачивает (Clarifications Q1).
7. `isDemo = ready && !canWatch` — вместо отказа не-премиум получает
   демо-токен на фрагмент («куплет минус отступ под фейд-ин»),
   `demoFadeInSeconds = song.demoFragmentFadeInSeconds`.
8. Токен: `canWatch` → `gestureUnlockService.issueDirectAccessToken(id)`;
   `isDemo` → `issueDemoAccessToken(id, song.demoFragmentStartSeconds, song.demoFragmentEndSeconds)`;
   иначе `null`.
9. Аналитика: при `canWatch || isDemo` пишется `EventType.PLAYER` через
   `mainController.doRegisterEvent`; `source=list` (клик по иконке в
   «Закромах»/«Поиске») даёт `PlayerAction.OPENED`, обычный заход — `SHOWN`.
10. Ответ: `{ready, isPremiumUser, canWatch, canExport, isDemo, demoFadeInSeconds, token}`.

**`POST /readiness`** — батч-проверка без сайд-эффектов (не логирует и не
выдаёт токенов):

1. `ids` — CSV, парсится в `List<Long>` с `distinct()`.
2. `premium` резолвится один раз на весь запрос; для не-премиума подписки
   на песню подтягиваются одним запросом `Subscription.subscribedSongIds(...)`
   (для премиума — пустой набор, ему доступно всё).
3. На каждую песню: `contentReady = song != null && stemsReady(song)`;
   `watchable = contentReady && (song.isFreelyAvailableNow || premium || id in subscribedIds)`.
4. Ответ — `{items: {<id>: {ready, watchable, contentReady}}}`. Фронту нужны
   оба поля, чтобы отличить «золотую» монетку (контент готов) от
   «серебряной» (ещё нет); MinIO не опрашивается.

**Стримы стемов** (`fileminus.mp3`, `filevoice.mp3`, `filebass.mp3`,
`filedrums.mp3`):

1. `authorized(id, token, session)` — двойная авторизация: сначала gesture
   token (`gestureUnlockService.validateToken(token, id)`), затем share
   session (`shareLinkService.validateShareSession(session, id)`). Приоритет
   у gesture token: если он валиден, share-session игнорируется. Отказ —
   `404`.
2. `stemResponse` строит ключ
   `stemStorageKey(song, fileType) = "${song.storageFileName}${fileType.suffix}.${fileType.extention}"`
   (`suffix` уже содержит ведущую точку), забирает байты через nginx-прокси
   `GET $minioProxyUrl/minio/karaoke/<encoded path>` и отдаёт
   `audio/mpeg`.
3. Байты проксируются напрямую этим же токен-защищённым эндпоинтом, а не
   `302`-редиректом на статический `/minio/`-путь: постоянный публичный URL
   обходил бы токен и его TTL (закладка, шаринг ссылки, кэш
   download-менеджера), теперь токен проверяется на каждом запросе.
4. Демо-токен: `gestureUnlockService.demoRangeForToken(token, id)` даёт
   диапазон, и `Mp3Trimmer.trimToRange(bytes, startSeconds, endSeconds)`
   обрезает байты на границе mp3-фрейма — полный файл физически не покидает
   сервер. Обычный токен получает байты как есть.

**`GET /{id}/playerdata` и `GET /{id}/playerfile`**: собирают JSON
playerdata (`playerData`) и полный `playerfile` (playerdata + zip);
zip-сборка добавляет стемы через `addStemIfPresent` (стр. 511) и
`addStored` (стр. 572).

## Hot paths

- **`/{id}/fileminus.mp3`** — каждое воспроизведение песни.
- **`/{id}/access`** — каждое открытие страницы песни (проверка premium).
- **`/readiness`** — каждые 5 минут из Закромов.

## Архитектурные решения

### Решение 1: stream через nginx-proxy

Файлы (`.mp3`) стримятся через nginx path-proxy (см.
[storage-flow.md](../../storage/components/storage-flow.md))
— **НЕ** через `KaraokeStorageService` или `StorageApiClientWeb`.

### Решение 2: `/access` для premium проверки

Возвращает JSON `{canWatch, canExport, isPremiumUser, isDemo}` — для
UI «золотой/серебряной монетки» (см.
[composable-use-player-access.md](../../../system/frontend/composable-use-player-access.md)).

### Решение 3: `/readiness` для батч-проверки

`/readiness` принимает **массив id** песен, возвращает их
готовность. Используется `usePlayerReadiness` (Pass 372) для
chunked-проверки (chunk=20, MAX_CONCURRENT=3).

## Зависимости | Dependencies

- [public-controllers.md](public-controllers.md) — обзор.
- [storage-flow.md](../../storage/components/storage-flow.md) — stream via nginx.

## Известные TODO

- [ ] **Каждый endpoint** — детальный contract (Pass 343+).

## Changelog

- **Pass 481** (2026-09-27, spec `481-knowledge-domain-karaoke-web`): секция
  «Endpoints (8)» перенесена под «Интерфейсы и Контракты»; добавлена
  «Логика и Алгоритмы» с проверенными по коду потоками `/access`
  (ready/premium/subscribed/shareGuest/demo/token), `/readiness`
  (батч без MinIO), стримов стемов (`authorized`, `stemResponse`,
  `Mp3Trimmer`) и `playerdata`/`playerfile`. Автор: agent (Karaoke).
- **Pass 476** (2026-09-09): Initial. Автор: agent (Karaoke).
