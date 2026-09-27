# Component: MainController (karaoke-web Thymeleaf)

> **Домен**: [karaoke-web](../domain.md)
> **Компонента**: `MainController` — Thymeleaf-страницы + internal
> endpoints.


## Ответственность | Responsibility


`MainController` — Thymeleaf-страницы + internal endpoints.

## Файл

`karaoke-web/.../controllers/MainController.kt` (~500+ строк)

## Назначение

**Thymeleaf HTML-страницы** (статические HTML) + **internal JSON
endpoints** (для server-to-server между karaoke-app и karaoke-web).

## Интерфейсы и Контракты | Interfaces and Contracts

Контроллер отдаёт два разных контракта из одного класса: HTML-страницы
(Thymeleaf) и JSON/`@ResponseBody`-endpoints для server-to-server.

### Thymeleaf HTML-страницы

| URL | Что |
|---|---|
| `GET /` | Главная |
| `GET /zakroma` | Закрома автора |
| `GET /testpage/{id}` | Тестовая страница (Pass 343+) |
| `GET /filter` | Фильтр |
| `GET /song` | Песня (HTML view) |
| `GET /statbysong` | Счётчики (StatBySong) |
| `GET /webevents` | События |

### JSON, internal (server-to-server)

| URL | Контракт |
|---|---|
| `POST /registerevent` | Тело-`Map` с обязательным `eventType` (+ `anonId`); `siteUserId` — query-параметр. Возвращает `Boolean`. |
| `POST /changerecords` | Тело: `word` (зашифрованное кодовое слово) + `dataCreate`/`dataUpdate`/`dataDelete` (списки SQL-действий). Возвращает `String` (`"OK"` или текст ошибки). |
| `GET /api/internal/stemjobs/{id}/raw` | Реальный путь; прежний `/api/internal/stem-jobs/{id}/download-original` не существует (Pass 477). См. [internal-stem-job-controller.md](internal-stem-job-controller.md) |

## Логика и Алгоритмы | Logic and Algorithms

**`POST /registerevent`** (web-аналитика):

1. Без `eventType` в теле — сразу `false`.
2. IP клиента — `ClientIpResolver.resolve(request)`, плюс `User-Agent`
   и `anonId` из тела.
3. Событие формируется в `EventsBuffer.EventRecord` и уходит батчевым
   INSERT; kill-switch — `karaoke.web.events.batch-enabled` (дефолт
   `false` = синхронный INSERT, как раньше). SQL-формирование
   инкапсулировано в `EventsBuffer.buildInsertSql`.
4. Каждое событие проходит через [`SamplingFilter`](../../../system/frontend/composable-engagement-tracking.md) —
   1/N sampling для web-аналитики (см.
   [monitoring/log-categories.md](../../monitoring/components/log-categories.md)).

**`POST /changerecords`** (two-DB sync):

1. `Crypto.decrypt(word)` должен совпасть с `Crypto.WORDS_TO_CHECK` —
   иначе возвращается «Не удалось расшифровать кодовое слово».
2. Применяет три набора действий к `WORKING_DATABASE`; для строк
   `tbl_songs` запоминает флаг «доступна для новости» **до** изменения
   (`songAvailabilityBefore`, spec `101-song-news-flag`) и детектирует
   переход после применения всего батча.
3. `Crypto.encrypt`/`decrypt` берут ключ из env, см.
   [system/utilities.md](../../../system/utilities.md).

**Реализация**: импорты — `KaraokeStorageService`, `StorageApiClient`,
`SongReleaseAnnouncementService`, `SamplingFilter`, `SiteUserResolver`,
`ClientIpResolver`; enums — `EventType`, `LinkType`, `PlayerAction`,
`RestName`.

## Hot paths

- **Каждое открытие** страницы сайта = `/` или `/zakroma` =
  Thymeleaf-рендеринг.
- **Каждое событие** = `POST /registerevent` (через `SamplingFilter`).
- **Two-DB sync** = `POST /changerecords` (зашифрованный SQL).

## Архитектурные решения

### Решение 1: Thymeleaf + REST в одном контроллере

`MainController` — **гибрид** (Thymeleaf HTML для страниц + JSON
endpoints для internal). Разделение — по `produces` и типу
возврата.

### Решение 2: Sampling защита

Каждое событие проходит через `SamplingFilter` (см.
[composable-engagement-tracking.md](../../../system/frontend/composable-engagement-tracking.md)) —
1/N sampling для web-аналитики (см.
[monitoring/log-categories.md](../../monitoring/components/log-categories.md)).

### Решение 3: WebSocket + SSE

`SimpMessagingTemplate` — для SSE-push (см.
[sse domain](../../sse/domain.md)). В Thymeleaf-страницах
используется для real-time обновлений (например, новые сообщения в
чате).

## Зависимости | Dependencies

- [internal-controllers.md](internal-controllers.md) — общий обзор.
- [services-overview.md](services-overview.md) — сервисы.
- [public-controllers.md](public-controllers.md) — публичные API.

## Известные TODO

- [ ] **Каждый endpoint** — детальный contract (Pass 343+).
- [ ] **Thymeleaf-шаблоны** — где определены (Pass 343+).
- [ ] **`/registerevent` schema** — какие поля, валидация.
- [ ] **`/changerecords` security** — только internal IP, защита
      nginx'ом.

## Changelog

- **Pass 481** (2026-09-27, spec `481-knowledge-domain-karaoke-web`): секции
  «Endpoints …» сведены в «Интерфейсы и Контракты» (два контракта: Thymeleaf
  и internal JSON), «Логика и Алгоритмы» дополнена фактическим потоком
  `/registerevent` (EventsBuffer, kill-switch) и `/changerecords`
  (decrypt → три набора действий → детекция флага новости). Автор: agent (Karaoke).
- **Pass 436-438** (2026-09-09): Initial. Автор: agent (Karaoke).