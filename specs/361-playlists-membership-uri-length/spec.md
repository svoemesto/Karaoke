# Feature Specification: 414 Request-URI Too Large на /playlists/membership у крупных авторов

**Feature Branch**: `361-playlists-membership-uri-length`

**Created**: 2026-09-10

**Status**: Draft

**Input**: User description: "Работа над задачей #77 в трекере OpenProject"

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

### Идентификация

- **Issue ID**: `#77`
- **Title**: «Ошибка в консоли при открытии песен автора, когда их много»
- **Created in OpenProject**: 2026-09-10

### Workflow (NON-NEGOTIABLE при наличии Issue ID)

| Шаг | Команда | Когда | Кто |
|---|---|---|---|
| 1. **Claim** | `source .env.local-tracker && bash tools/tracker.sh claim-issue 77` | ПЕРЕД первой строкой кода спеки. | Agent |
| 2. **Pre-flight Knowledge** | `spec.md § Knowledge References` | Согласно Constitution Principle IX (см. ниже). | Agent |
| 3. **Work** | код, tests, knowledge updates | `/speckit.implement` | Agent |
| 4. **Add comment с отчётом** | `bash tools/tracker.sh add-comment 77 --file specs/361-playlists-membership-uri-length/report.md` | После merge. Файл `report.md` — REQUIRED. | Agent |
| 5. **Mark review** | `bash tools/tracker.sh mark-review 77` | После публикации комментария. | Agent |
| 6. **Close** | `bash tools/tracker.sh close-issue 77` | После ревью владельцем. | Agent или Owner |

### Проверки (validation)

- `tools/check-spec-issue-link.py` проверяет наличие секции и обязательных полей.

### Прецедент

Issue #77 открыт владельцем 2026-09-10 (репорт о 414 в консоли при заходе на
«Машина Времени» — почти 2500 песен, URL GET-запроса
`/api/public/account/playlists/membership?ids=22982,22983,...` превышает
HTTP-лимит прокси (nginx по умолчанию = 8 КБ), браузер получает
`414 Request-URI Too Large`).


## Knowledge References *(MANDATORY — see Constitution Principle IX)*

> **Прецедент**: 2026-09-09, spec #339 — агент пропустил Knowledge-first
> pre-flight и изобрёл форму кеша вместо паттернов из
> `knowledge/domains/caching/components/caching-patterns.md`.
> Без заполненной секции спека **НЕ ДОЛЖНА** переходить в
> `/speckit.plan`. См. `AGENTS.md` MUST #0, Constitution Principle IX.

### Pre-flight log

- **Дата pre-flight**: 2026-09-10
- **Grep-запросы** (5 попыток):
  1. `playlists/membership` →
     `knowledge/system/frontend/composable-playlist-membership.md` (компонент
     `usePlaylistMembership` — есть упоминание `/api/public/playlist/membership`
     singular), `knowledge/domains/karaoke-web/components/public-controllers.md`
     (PublicPlaylistController с префиксом `/api/public/account`), и
     `knowledge/domains/karaoke-web/components/public-controllers-6.md`
     (упоминание `playlists/membership` в средних контроллерах).
  2. `account/playlists` → те же файлы +
     `knowledge/domains/storage/components/storage-flow.md`,
     `knowledge/domains/integration/components/dtos.md`,
     `knowledge/domains/identity/components/site-user-entity.md`,
     `knowledge/domains/karaoke-web/components/services-overview.md`,
     `knowledge/domains/karaoke-web/components/internal-controllers.md`,
     `knowledge/domains/karaoke-web/components/song-share-link-service.md`,
     `knowledge/domains/karaoke-web/components/main-controller.md`,
     `knowledge/domains/karaoke-web/components/config.md`,
     `knowledge/domains/karaoke-web/components/public-controllers-2.md…-6.md`.
  3. `414 / Request-URI / URI Too Long` → **no relevant docs** (нет ADR/guideline
     о лимитах HTTP URI на nginx и о паттерне «GET→POST для больших payload»).
  4. `authorSongs / author songs` →
     `knowledge/domains/catalog/domain.md`,
     `knowledge/domains/caching/components/caching-patterns.md`,
     `knowledge/domains/caching/components/author-cache.md`,
     `knowledge/domains/caching/domain.md`,
     `knowledge/public/glossary.md`,
     `knowledge/system/frontend/composables-remaining.md`.
  5. `public-controllers` → см. выше (п. 2).

### Knowledge files consulted

- [`knowledge/README.md`](../../knowledge/README.md)
  — общий обзор SSoT.
- [`knowledge/domains/README.md`](../../knowledge/domains/README.md)
  — реестр Bounded Contexts; определил `karaoke-web` как целевой домен.
- [`knowledge/domains/karaoke-web/components/public-controllers.md`](../../knowledge/domains/karaoke-web/components/public-controllers.md)
  — зачем прочитан: см. таблицу 19 Public-контроллеров; определил, что
  `PublicPlaylistController` — самый длинный (15 endpoints) и
  `PublicPlaylistController.kt` под `@RequestMapping("/api/public/account")`.
- [`knowledge/domains/karaoke-web/components/public-controllers-6.md`](../../knowledge/domains/karaoke-web/components/public-controllers-6.md)
  — зачем прочитан: детализация PublicPlaylistController (522 строки, 15
  endpoints), упомянут `/playlists/membership` (singular) с линком на
  `composable-playlist-membership.md`.
- [`knowledge/system/frontend/composable-playlist-membership.md`](../../knowledge/system/frontend/composable-playlist-membership.md)
  — зачем прочитан: фронтенд-composable `usePlaylistMembership` — показывает,
  как именно фронт использует `membership`-endpoint. Документ описывает
  endpoint **`/api/public/playlist/membership` (singular)** — это **другой**
  endpoint, не тот, что в задаче #77.
- [`knowledge/adr/local-0006-logging-and-error-handling-karaoke-web.md`](../../knowledge/adr/local-0006-logging-and-error-handling-karaoke-web.md)
  — зачем прочитан: контракт WARN/INFO для `infra.prod.*` — принципы
  логирования в karaoke-web (применимо к фиче).
- [`specs/239-zakroma-author-songs-batch-render/spec.md`](../239-zakroma-author-songs-batch-render/spec.md)
  — зачем прочитан: прецедент. В Pass 239 уже была сделана оптимизация —
  переход с per-row запросов на bulk-fetch `/playlists/membership?ids=...`
  (одним запросом с CSV из ~2500 id). На тот момент это работало (~7 КБ URL).
  Но в продакшене у «Машины Времени» оказалось > 2500 песен → URL превышает
  HTTP-лимит → **414**.

### Если ничего не нашлось (явный no-op)

> **Searched**: `414`, `URI Too Long`, `Request-URI`, `GET → POST`, `HTTP method
> vs payload size`, `URI length limit` в `knowledge/` → **no relevant docs**.
> Нет ADR/guideline о максимальной длине URL и о паттерне перехода GET→POST для
> крупных payloads. Решение будем принимать на этапе планирования, опираясь
> на общие веб-конвенции (REST: GET идемпотентный, URL ≤ 8 КБ; POST —
> неидемпотентный, body без лимита).


## User Scenarios & Testing *(mandatory)*

### User Story 1 — Зарегистрированный пользователь открывает «Машину Времени» (≈2500 песен) (Priority: P1)

Зарегистрированный пользователь (без премиума) открывает карточку автора
«Машина Времени» в «Закрома». Список всех ~2500 песен автора отрисовывается
**без ошибок в консоли браузера** и без поломанных иконок избранного/плейлистов
(красная ★, синяя ▶|). В DevTools Network — **ровно 1** запрос membership
(а не zero с ошибкой 414), все иконки получают финальное состояние.

**Why this priority**: Это **исходный репорт бага** (Issue #77) — без
исправления публичный сайт «лежит» (главная страница не отвечает) при
открытии любого крупного автора зарегистрированным пользователем. Анонимы
не страдают (membership для них skip'нут по FR-009 в спеке #239), но
зарегистрированные и премиум-пользователи — да. Премиум-юзеров тоже нет —
для них фильтр не-избранных плейлистов не пустой (есть премиум-плейлисты).

**Independent Test**: Залогиниться, открыть `/zakroma?author=Машина
Времени`. В DevTools Network: 0 запросов с ответом 414 (раньше —
`GET /api/public/account/playlists/membership?ids=22982,...` → 414).
У всех 2500 строк иконки избранного/плейлистов сразу показывают финальное
состояние (красная ★ если в избранном, синяя ▶| если в не-избранных
плейлистах, outline — если нигде).

**Acceptance Scenarios**:

1. **Given** зарегистрированный пользователь открыл «Машину Времени»
   (≈2500 песен), **When** фронт делает bulk-fetch membership,
   **Then** DevTools Network показывает **1 успешный (HTTP 200)** запрос
   membership, **0 запросов с 4xx/5xx**, иконки избранного/плейлистов у всех
   строк отрисовываются с финальным состоянием.
2. **Given** зарегистрированный пользователь открыл автора с **≤ 500 песен**,
   **When** фронт делает bulk-fetch membership,
   **Then** поведение **не отличается** от текущего (URL укладывается в
   8 КБ, GET работает корректно) — никаких regression.
3. **Given** премиум-пользователь открыл «Машину Времени» (есть
   не-избранные премиум-плейлисты), **When** фронт делает bulk-fetch
   membership, **Then** у всех песен из премиум-плейлистов синяя иконка
   `▶|` горит; у не-премиум песен — outline. Без ошибок 414.
4. **Given** открыт крупный автор, **When** пользователь переключается на
   другой крупный автор (например, ещё одну плашку в «Закрома»),
   **Then** membership-store обновляется на нового автора (как раньше),
   иконки обновляются, без утечек от старого автора (паттерн
   `requestId/latest` уже есть в `usePlaylistMembership.js`).

---

### User Story 2 — Аноним открывает «Машину Времени» (Priority: P2)

Анонимный посетитель (без токена) открывает «Машину Времени». Никаких
запросов membership вообще нет (FR-009 спеки #239), иконки
избранного/плейлистов — «гостевые» (серая ★, серая ▶|), редирект на
`/login` по клику. Никаких регрессий: проблема **не существует** для
анонимов.

**Why this priority**: Этот сценарий не имеет бага (аноним не делает
membership-fetch по дизайну спеки #239), но мы обязаны проверить
регрессию: после смены URL/body формата запроса анонимный flow не должен
сломаться. Это **negative-test** — фикс должен оставаться совместимым с
анонимами.

**Independent Test**: В инкогнито открыть `/zakroma?author=Машина
Времени`. В DevTools Network: 0 запросов `/api/public/account/*`
(membership-fetch skip'нут). Иконки — «гостевые».

**Acceptance Scenarios**:

1. **Given** анонимный пользователь, **When** открыт крупный автор,
   **Then** в DevTools Network **нет** запросов `/api/public/account/playlists/membership*`
   и `/api/public/account/favorites/ids`. Membership-fetch skip'нут
   (`if (!token.value) return` в `usePlaylistMembership.js`).
2. **Given** аноним, **When** пользователь кликает по «гостевой» ★ или
   «гостевой» ▶|, **Then** редирект на `/login?redirect=...` (как сейчас).

---

### User Story 3 — Backward-compat: старый GET-эндпоинт остаётся работоспособным (Priority: P2)

Существующие клиенты (старые билды `karaoke-public` в кеше браузера,
мобильные/embedded клиенты, тесты, dev-tools) ещё какое-то время могут
слать запросы по **старому GET URL**. Эти запросы MUST продолжать
работать — без поломок авторизации, без удаления endpoint'а. В идеале
новый POST-эндпоинт становится предпочтительным, а старый GET
постепенно устаревает (но не отключается).

**Why this priority**: Это **вопрос надёжности релиза**. Спека #239 уже
ввела этот endpoint как часть оптимизации. Если мы без переходного
периода заменим GET на POST — все пользователи с открытыми вкладками
в старом билде получат 404 или 405. Это хуже, чем текущая 414 (которая
хотя бы явно говорит «URI too large»).

**Independent Test**: curl с явным URL GET
`/api/public/account/playlists/membership?ids=22982` → должен вернуть
HTTP 200 + `{"items":{"22982":{...}}}`. (Не 404, не 405.)

**Acceptance Scenarios**:

1. **Given** старый GET-эндпоинт с маленьким `ids` (≤ 500), **When**
   авторизованный пользователь делает запрос, **Then** сервер отвечает
   HTTP 200 + корректный JSON.
2. **Given** старый GET-эндпоинт с огромным `ids` (≥ 2500), **When**
   авторизрованный пользователь делает запрос, **Then** nginx/proxy
   отвечает HTTP 414 (текущее поведение, не наша забота). Клиент должен
   использовать новый POST-эндпоинт.

---

### Edge Cases

- **Концерт граничного случая**: автор ровно с **N песен, при котором URL
  пересекает 8 КБ**. Расчёт: средний id = 5 цифр + `,` = 6 байт.
  `8 КБ / 6 ≈ 1333 id`. Но реальный лимит зависит от nginx
  `large_client_header_buffers` (по умолчанию `4 8k` = **8192 байт** на
  request-line + headers). Плюс `?ids=` (5 байт), URL prefix `/api/public/account/playlists/membership`
  (~50 байт). Итого доступно ~8100 байт на CSV → ~1350 id. **Edge case**:
  автор с 1300 песен — старый GET работает, новый POST работает;
  автор с 1400 — старый GET **уже ломается** (414), новый POST работает.
- **Backend за nginx с другим лимитом**: в `deploy/nginx.conf` /
  конфигурации прокси может быть переопределён `large_client_header_buffers`
  или `client_header_buffer_size`. На проде проверить реальный лимит.
- **Membership-fetch упал с 5xx** (как в FR Edge Cases спеки #239): иконки
  остаются в «off», без retry, без re-fetch. Логика не меняется.
- **Пользователь с 0 песен в плейлистах**: фронт skip'ает membership-fetch
  (оптимизация FR-013 спеки #239). Новый POST-эндпоинт не должен ломать
  эту оптимизацию.
- **POST без body / с пустым `ids: []`**: сервер должен корректно вернуть
  пустой `{"items":{}}`, без 400/500. (Защита от кривых клиентов.)
- **POST с `Content-Type` отличным от `application/json`**: сервер может
  отклонить (415 Unsupported Media Type) — это нормальное REST-поведение.
- **CSRF-защита**: POST-запросы чувствительны к CSRF. Проверить, что
  `SiteAuthInterceptor` уже отрабатывает CSRF (или что защита не нужна —
  например, если используется JWT в `Authorization` header, а не cookie-based
  сессия).

## Requirements *(mandatory)*

### Functional Requirements

#### A. HTTP-метод и формат запроса (NON-NEGOTIABLE — решение бага #77)

- **FR-001**: Backend MUST принимать membership-запрос **по новому URL
  `/api/public/account/playlists/membership` методом `POST`** (вместо
  GET с CSV в query-string). Тело запроса — JSON
  `{"ids": [22982, 22983, ...]}`.
- **FR-002**: Старый GET-эндпоинт **MUST оставаться работоспособным** для
  backward-compat (см. US3). Не должен выдавать 404/405 на маленькие
  `ids`. (Реализация: контроллер содержит оба метода с одинаковой
  бизнес-логикой, см. Architecture в Plan.)
- **FR-003**: Новый POST-эндпоинт MUST принимать JSON `{"ids": number[]}`
  и возвращать **тот же** формат ответа, что и старый GET:
  `{"items": {"<songId>": {"favorited": bool, "playlistIds": number[]}, ...}}`.
- **FR-004**: Новый POST-эндпоинт MUST пройти через `SiteAuthInterceptor`
  (как и старый GET) — защищённые endpoint'ы (см. Decision 1 в
  `public-controllers.md`).
- **FR-005**: Размер тела POST-запроса MUST быть достаточен для 2500+ id
  (≥ 30 КБ; nginx по умолчанию `client_max_body_size = 1m`, что
  достаточно). Проверить `client_max_body_size` в конфигурации
  `nginx` на проде и поднять при необходимости.

#### B. Совместимость и поведение

- **FR-006**: Если клиент шлёт POST-запрос с маленьким `ids` (1-100),
  сервер MUST работать идентично GET-варианту (никакой разницы в ответе).
- **FR-007**: Если клиент шлёт POST-запрос с дубликатами в `ids`, сервер
  MUST дедуплицировать (как и старый GET делает через
  `ids.split(",").mapNotNull { it.trim().toLongOrNull() }.distinct()`).
- **FR-008**: Если клиент шлёт POST-запрос с пустым `ids: []`, сервер
  MUST вернуть `{"items": {}}` с HTTP 200.
- **FR-009**: Если клиент шлёт POST-запрос с некорректным JSON (синтаксис
  или `ids` не массив), сервер MUST вернуть HTTP 400 с человекочитаемым
  сообщением.
- **FR-010**: Если клиент **не** авторизован (нет JWT-токена) — поведение
  MUST совпадать со старым GET: вернуть пустой `{"items": {}}` (или
  текущая семантика для анонима; см. текущую логику `currentUser(request)`
  в `PublicPlaylistController.kt`).

#### C. Фронтенд

- **FR-011**: Фронт (`karaoke-public/src/services/playlistApi.js`) MUST
  переключиться с `authGet` на `authPost` для `fetchMembership()`. Тело —
  `{"ids": ids}`, **не** query-string.
- **FR-012**: `authPost` MUST поддерживать JSON-body (если ещё не
  поддерживает — добавить в `authApi.js`). Проверить, что
  `Content-Type: application/json` ставится автоматически.
- **FR-013**: Поведение композабла `usePlaylistMembership.js` MUST
  остаться **идентичным**: те же `favoriteIds`/`membership`/`playlists`,
  те же `favStateFor`/`plStateFor`, тот же `BroadcastChannel`. Меняется
  только сетевой вызов (POST вместо GET).
- **FR-014**: Перед отправкой POST фронт MAY дополнительно проверить
  размер payload (например, `JSON.stringify({ids}).length > 8192` →
  chunked POST) **или** положиться на сервер, что при `client_max_body_size`
  ≥ 1m POST пройдёт целиком. **Рекомендация**: оставить одним POST (до
  ~2500 id = ~15-20 КБ JSON, укладывается).

#### D. Безопасность

- **FR-015**: POST-эндпоинт **не требует CSRF-защиты** —
  аутентификация идёт через JWT в `Authorization: Bearer` header
  (подтверждено: `SiteAuthInterceptor.kt:25`). Cross-origin attacker
  не имеет доступа к JWT → CSRF-атака невозможна by design. Если в
  будущем проект перейдёт на cookie-based сессии — этот FR должен
  быть пересмотрен (добавить CSRF-token middleware).
- **FR-016**: Никаких новых секретов в коде (Constitution Principle VIII).
  POST-эндпоинт использует тот же JWT-токен, что и старый GET.

#### E. Наблюдаемость и observability

- **FR-017**: Backend MUST логировать метрику (или хотя бы WARN-сообщение
  при HTTP-методе) `membership_post_calls_total` (counter) — счётчик
  POST-вызовов `/api/public/account/playlists/membership`. После
  выкатки фикса MUST быть **1-2 вызова** на пользователя за сессию.
- **FR-018**: Backend MUST логировать `membership_get_calls_total`
  (counter) для обратной совместимости — после фикса MUST стремиться
  к 0 (только legacy-клиенты).

### Key Entities

- **HTTP Request**: расширен с `GET /api/public/account/playlists/membership?ids=<csv>`
  до `POST /api/public/account/playlists/membership` с JSON-телом `{"ids": number[]}`.
  Бизнес-логика (фильтр `visibleNonFav`, построение `songToPlaylists`,
  формирование `items`) — без изменений.
- **Request Body DTO** (новый): `MembershipRequest { ids: List<Long> }` —
  record/data class с JSON-десериализацией.
- **Response DTO** (без изменений): `{"items": {"<songId>": {"favorited":
  bool, "playlistIds": number[]}, ...}}` — фронт контракт неизменен.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001** (главный критерий бага #77): При открытии `/zakroma?author=Машина
  Времени` (≈2500 песен) **залогиненным** пользователем — в DevTools Network
  **0 запросов** с HTTP-статусом **4xx/5xx** для membership, иконки
  избранного/плейлистов отрисовываются с финальным состоянием у всех строк.
  До фикса — **1 запрос** с HTTP 414.
- **SC-002**: Время до отрисовки первой страницы песен для зарегистрированного
  пользователя на крупном авторе **не ухудшается** (≤ 5 сек на 2500 песен,
  см. SC-004 спеки #239). POST-запрос не должен занимать больше времени,
  чем GET (бенчмарк показывает, что POST с JSON-body обычно быстрее GET
  с большим query-string, т.к. nginx парсит body быстрее, чем ищет
  конец request-line).
- **SC-003**: Backward-compat: старый GET-эндпоинт продолжает работать
  для маленьких `ids` (≤ 500). Regression-тест: `curl -H "Cookie: ..." 
  http://localhost:7907/api/public/account/playlists/membership?ids=1,2,3` →
  HTTP 200 + корректный JSON. (Это — поведение «до», которое мы
  сохраняем.)
- **SC-004**: Все 2500 строк списка песен зарегистрированного
  пользователя отрисовываются **без «вечных спиннеров»** у иконок
  избранного/плейлистов (см. SC-003 спеки #239). Проверяется визуально +
  Network tab.
- **SC-005**: Backend-счётчик `membership_post_calls_total` после
  выкатки фикса — **≥ 95% от общего** `membership_*_calls_total`.
  `membership_get_calls_total` → стремится к 0 (только legacy).
- **SC-006**: Membership-логика для премиум (FR-013 спеки #239: «видит
  не-избранные плейлисты») сохранена — регрессий по доступу нет.
  Проверяется вручную на 2 типах юзеров (зарегистрированный / премиум)
  на крупном авторе.

## Clarifications

### Session 2026-09-10

- Q: Какой механизм аутентификации используется для `/api/public/account/*` —
  cookie-based (требует CSRF) или JWT в `Authorization: Bearer` (CSRF
  невозможен)? → A: **JWT в `Authorization: Bearer`** (подтверждено в
  `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/config/SiteAuthInterceptor.kt`,
  строка 25: `request.getHeader("Authorization")?.removePrefix("Bearer ")?.trim()`).
  Cross-origin attacker не имеет доступа к JWT → CSRF-атака невозможна.
  Никакой CSRF-middleware не требуется.

## Assumptions

- **GET → POST — общепринятый паттерн для больших payloads.** REST-конвенция:
  GET с URL ≤ 8 КБ, POST для всего, что не влезает. Подтверждается
  многими web-API (YooKassa, VK API, Telegram Bot API).
- **Анонимы не делают membership-fetch** — оптимизация FR-009 спеки #239
  остаётся в силе. Новый POST-эндпоинт для анонимов не вызывается.
- **JWT-токен в `Authorization` header, не cookie-based** —
  **подтверждено** в `SiteAuthInterceptor.kt:25`. См. FR-015 +
  Clarifications § Session 2026-09-10. CSRF невозможен by design.
- **nginx `client_max_body_size` ≥ 1m** — для POST с JSON-body на
  2500 id (~20 КБ) — текущая конфигурация уже должна подходить. Если
  нет — поднять в `deploy/nginx.conf`.
- **Membership-payload на 2500 id ≈ 15-20 КБ JSON** — это меньше
  `client_max_body_size = 1m`, nginx справится без правок.
- **CSRF**: не требуется (JWT в `Authorization` header, не cookie).
  См. FR-015 + Clarifications § Session 2026-09-10.
- **Кириллица в `ids`** невозможна — это числа. JSON-парсер обработает
  безопасно.
- **LiveDocs**: фича влияет на архитектуру (новый HTTP-метод, новый DTO) →
  в этом же PR обновить:
  - `knowledge/domains/karaoke-web/components/public-controllers.md`
    (упоминание POST-метода)
  - `knowledge/domains/karaoke-web/components/public-controllers-6.md`
    (детализация PublicPlaylistController)
  - `knowledge/system/frontend/composable-playlist-membership.md`
    (упоминание POST в `fetchMembership`)
  - `knowledge/adr/` — создать новый ADR `0009-get-vs-post-large-payload.md`
    с обоснованием выбора POST.
  - `docs/features/playlist-membership.md` (если существует per-feature
    документ — обновить FR/Acceptance). **Проверить наличие файла на
    этапе планирования.**
  - `docs/architecture-notes.md` (запись о PR).
- **Constitution Check** (см. Constitution § Governance п.4):
  - **Principle II (Сырой JDBC + дифф по хэшам)**: новый POST-эндпоинт
    использует **тот же** SQL `SitePlaylistItem.songIdsInPlaylists(...)`
    что и старый GET. Никакого нового запроса в БД — нулевое изменение
    в SQL. Никакого JPA/Hibernate.
  - **Principle I (Self-contained)**: фича чисто серверная + фронтовая,
    без внешних SaaS.
  - **Principle V (Двух-фронтенд)**: изменения касаются **только**
    `karaoke-public` (фронт) и `karaoke-web` (бэкенд), не `webvue3`
    (admin).
  - **Principle VIII (Секреты)**: никаких новых секретов, токен — тот же.
  - **Principle IX (Knowledge-first)**: ✅ pre-flight выполнен в этой
    спеке (см. § Knowledge References).