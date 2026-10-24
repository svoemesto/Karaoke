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
| `POST /changerecords` | **Внутренний (server-to-server) эндпоинт**: требует заголовок `X-Internal-Secret` (общий секрет `sync.internal-secret` / env `SYNC_INTERNAL_SECRET`; на стороне `karaoke-app` — `Karaoke.syncInternalSecret`). Без корректного ключа — `403` и ни одной выполненной SQL-команды. Тело: `word` (зашифрованное кодовое слово) + `dataCreate`/`dataUpdate`/`dataDelete` (списки SQL-действий). Возвращает `ResponseEntity<String>`: `200` — текущий ответ сервиса (`"OK"`-строка прежнего формата), `403` — отказ по ключу, `5xx` — внутренняя ошибка **без** текста ошибки БД. |
| `GET /api/internal/stemjobs/{id}/raw` | Реальный путь; прежний `/api/internal/stem-jobs/{id}/download-original` не существует (Pass 477). См. [internal-stem-job-controller.md](internal-stem-job-controller.md) |

## Логика и Алгоритмы | Logic and Algorithms

**`POST /registerevent`** (web-аналитика):

1. Без `eventType` в теле — сразу `false`.
2. IP клиента — `ClientIpResolver.resolve(request)` (единое правило: `X-Real-IP` →
   последний элемент `X-Forwarded-For` → TCP-peer, см. [domain.md](../domain.md)
   «Решение 5»), плюс `User-Agent`
   и `anonId` из тела.
3. Событие формируется в `EventsBuffer.EventRecord` и уходит батчевым
   INSERT; kill-switch — `karaoke.web.events.batch-enabled` (дефолт
   `false` = синхронный INSERT, как раньше). SQL-формирование
   инкапсулировано в `EventsBuffer.buildInsertSql`.
4. Каждое событие проходит через [`SamplingFilter`](../../../system/frontend/composable-engagement-tracking.md) —
   1/N sampling для web-аналитики (см.
   [monitoring/log-categories.md](../../monitoring/components/log-categories.md)).

**`POST /changerecords`** (two-DB sync):

1. **Проверка внутреннего секрета** (первый рубеж; второй — сетевой фильтр nginx
   по адресу админской машины, `deploy/prod-single-host/80to8897`,
   `location /changerecords`): заголовок `X-Internal-Secret` должен совпасть с
   `sync.internal-secret` (env `SYNC_INTERNAL_SECRET`). Сравнение выполняется
   **только при непустом настроенном секрете** — иначе при пустом секрете и пустом
   заголовке сравнение прошло бы и доступ открылся (ловушка образца
   `InternalStemJobController.authorized()`, спека 554). При отказе — `403`,
   ни одной SQL-команды не выполняется, запись в журнал категории
   `infra.sync.security` (`sync:security:denied`: время, адрес источника, причина;
   без тела запроса).
2. `Crypto.decrypt(word)` должен совпасть с `Crypto.WORDS_TO_CHECK` —
   иначе возвращается «Не удалось расшифровать кодовое слово».
3. Применяет три набора действий к `WORKING_DATABASE` **одной транзакцией**
   (аудит BR-012, #378): `autoCommit=false`, `commit()` после успешного
   применения всего батча, `rollback()` при любой ошибке, autocommit
   возвращается в исходное состояние в `finally` (соединение thread-local из
   `KaraokeConnection`, его же используют следующие запросы на этом потоке).
   Раньше statement'ы шли по autocommit-соединению: исключение в середине
   оставляло БД частично изменённой. Для строк `tbl_songs` по-прежнему
   запоминается флаг «доступна для новости» **до** изменения
   (`songAvailabilityBefore`, spec `101-song-news-flag`), переход
   детектируется после коммита батча (новость создаётся по применённому
   состоянию БД).
4. **Ссылка новости на главной** (`GET /`, аудит BR-504, #378): `News.loadPublished`
   проходит через `SafeLink.isSafe(link)` — в модель попадают только ссылки со
   схемой `http(s)`; иначе `link` обнуляется, журнал пишет предупреждение, а
   шаблон рендерит заголовок текстом (без `href` и без `window.open`).
   Раньше `tbl_news.link` (пишется неаутентифицированным `/api/news/*` в
   karaoke-app) подставлялся в `href` «как есть» — `javascript:` исполнялся по
   клику. Валидация схемы на стороне записи (karaoke-app `NewsController`) —
   вне пакета, за владельцем.
5. `Crypto.encrypt`/`decrypt` берут ключ из env, см.
   [system/utilities.md](../../../system/utilities.md).
6. **Текст ошибки БД наружу не возвращается**: при внутренней ошибке клиент
   получает `5xx` с коротким телом, подробности — в журнал (спека 554, FR-006).
   Транзакционность применения батча закрыта аудитом BR-012 (см. п. 3).

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
- [x] **`/changerecords` security** — закрыт (спека 554, OpenProject #357): заголовок
      `X-Internal-Secret` в приложении + `allow`/`deny` по адресу админской машины
      в nginx (`deploy/prod-single-host/80to8897`).

## Changelog

- **BR-012/BR-504** (2026-10-24, OpenProject #378, пакет PKG-S2-15): `POST /changerecords`
  применяет батч одной транзакцией (`autoCommit=false`, `commit`/`rollback`, восстановление
  autocommit); ссылка новости на главной проходит проверку схемы (`SafeLink`) до рендера.
  Автор: agent (Karaoke).
- **2026-10-24** (OpenProject #384, аудит BR-003 «xff-first-element»): адрес источника
  в `doRegisterEvent` определяется по общему правилу `util/ClientIpResolver`
  (`X-Real-IP` → последний элемент `X-Forwarded-For` → TCP-peer); правило журнала
  безопасности `syncSourceAddress` не менялось (по-прежнему только `X-Real-IP`, BR-011).

- **Спека 554** (2026-10-24, OpenProject #357, аудит BR-011): контракт `POST /changerecords`
  дополнен требованием заголовка `X-Internal-Secret` (fail-safe), отказом `403` без деталей,
  нейтральным `5xx` вместо текста ошибки БД и сетевым рубежом nginx (allow/deny по адресу
  админской машины). Автор: agent (Karaoke).

- **Pass 481** (2026-09-27, spec `481-knowledge-domain-karaoke-web`): секции
  «Endpoints …» сведены в «Интерфейсы и Контракты» (два контракта: Thymeleaf
  и internal JSON), «Логика и Алгоритмы» дополнена фактическим потоком
  `/registerevent` (EventsBuffer, kill-switch) и `/changerecords`
  (decrypt → три набора действий → детекция флага новости). Автор: agent (Karaoke).
- **Pass 436-438** (2026-09-09): Initial. Автор: agent (Karaoke).