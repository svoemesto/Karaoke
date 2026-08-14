# Feature Specification: Анализ и устранение источников аномальной нагрузки на сайт

**Feature Branch**: `187-site-traffic-anomaly-investigation`

**Created**: 2026-08-14

**Status**: Draft

**Input**: User description: "Сайт продолжает быть периодически недоступным. Предыдущий фикс (не передаем полную историю для анонимов и т.п.) проблему полностью не решила. Возможно где-то ещё с бэка идут потоки данных, которые делают сайт недоступным. Проведи анализ ситуации, где это может быть. Нужно постараться полностью избавиться от таких мест - любая нагрузка на сервер/базу не должна приводить к тому, что сайт 10 минут недоступен! Кандидат на трафик - это страница закромов с плашками автора, которая из хранилища передаёт много картинок авторов."

## Контекст и предыстория

На протяжении последних 1-2 месяцев фиксируются периодические инциденты недоступности сайта `sm-karaoke.ru` длительностью до 7-10 минут. Первый раунд исправлений (Pass 52, ветка `174-fix-news-since-anon`):

- `GET /api/public/news/since` для анонимов теперь возвращает пустой массив (было: 3.5+ MB JSON × 45 сек × N вкладок × N пользователей → exhaustion `pg max_connections = 100` → 7-10 мин каскадных зависаний).
- Дополнительно NewsBell.vue на фронте defense in depth: не поллим вообще для анонимов.

Это снизило фоновую нагрузку, но инциденты продолжаются. Пользователь явно указал кандидата: **страница «Закрома» с тайлами авторов** — на ней сразу после открытия страницы идёт каскадный фетч ~200 превью-картинок авторов через `/api/public/picture?file=...` (302-редирект в nginx/MinIO). Дополнительно нужно исследовать:

- Каждый REST-эндпоинт karaoke-web в `PublicApiController`/`MainController` синхронно пишет `INSERT INTO tbl_events` через `doRegisterEvent` — даже для анонимов, даже для одного и того же запроса в окне 1 сек.
- Множество polling-эндпоинтов (chat unread 20 сек, share heartbeat 25 сек, news 45 сек, auth/me 5 мин) — каждый делает INSERT в `tbl_events` и/или SELECT из БД.
- Все `doRegisterEvent` INSERT'ы в `tbl_events` идут через сырой `connection.prepareStatement(...)` без `use {}` — соединение НЕ закрывается явно (Tomcat pool держит его по `thread-local`, см. `KaraokeConnection.kt:30`).
- Браузер при F5/возврате на страницу Закромов полностью перезагружает ВСЕ тайлы (нет HTTP-кеширования для редиректа + nginx не отдаёт `Cache-Control`).

## Цель фичи

1. **Провести аудит всех источников нагрузки** на сайт, которые потенциально могут привести к исчерпанию ресурсов (БД, томкат, nginx, MinIO, CPU).
2. **Задокументировать результат аудита** в `research.md` с конкретными file:line ссылками и приоримизацией «P1 (критично) / P2 (средне) / P3 (low)».
3. **Реализовать фиксы для P1-источников** в одном или нескольких коммитах, чтобы инцидент с недоступностью сайта более не воспроизводился при нормальной работе.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Страница «Закрома» открывается за разумное время без нагрузки на бэкенд (Priority: P1)

Анонимный/залогиненный посетитель открывает страницу `/zakroma` в первый раз. До завершения загрузки всех тайлов авторов (≈ 200-300 штук, в зависимости от числа авторов в каталоге) бэкенд karaoke-web получает МИНИМАЛЬНУЮ нагрузку — только один REST-запрос `/api/public/authors-tiles` (список метаданных) и параллельные GET'ы превью-картинок, которые ОБРАБАТЫВАЮТСЯ NGINX/MINIO НАПРЯМУЮ без Spring-контроллера. Spring-контроллер `PublicApiController.picture()` не получает тысячи HTTP-запросов при каждом открытии страницы.

**Why this priority**: Пользователь явно указал эту страницу как кандидата на источник трафика. 200+ редиректов через Spring = 200+ Tomcat-треад + 200+ Java-куча memory churn + 200+ исходящих запросов в nginx. При N одновременных посетителях это масштабируется как N×200 и легко упирается в лимиты Tomcat/БД.

**Independent Test**: Открыть `/zakroma` анонимным браузером, в DevTools → Network замерить: (а) сколько HTTP-запросов уходит на `/api/public/picture?file=...` за 5 секунд; (б) сколько из них проксируются Spring-контроллером (vs nginx напрямую); (в) общее время до завершения рендера сетки тайлов. Целевые значения: (а) ≤ 200 запросов, (б) 0 через Spring, (в) ≤ 4 сек на медленном канале.

**Acceptance Scenarios**:

1. **Given** посетитель впервые открывает `/zakroma` и у него нет закэшированных изображений авторов, **When** страница рендерит сетку тайлов, **Then** Spring-контроллер `PublicApiController.picture()` получает 0 HTTP-запросов, **And** все превью-картинки авторов загружаются напрямую через nginx-прокси `/minio/karaoke/...`.
2. **Given** посетитель повторно открывает `/zakroma` (или возвращается по «Назад» из `/song/{id}`) в течение 24 часов, **When** страница рендерит сетку тайлов, **Then** картинки загружаются из браузерного кеша (HTTP 304/200 with `If-Modified-Since` или `max-age`).
3. **Given** посетитель с медленным 3G-каналом открывает `/zakroma` с 300+ авторами, **When** страница начинает рендерить, **Then** первые 30 тайлов видны за ≤ 2 сек, остальные догружаются фоном без блокировки UI.

---

### User Story 2 — Polling-эндпоинты не подвешивают сайт даже при большом числе открытых вкладок (Priority: P1)

Залогиненный премиум-пользователь держит открытыми 3-5 вкладок сайта одновременно (например, плеер в одной, чат в другой, новости в третьей, Закрома в четвёртой). На каждой вкладке активны 1-3 polling-таймера (`NewsBell`, `ChatUnreadBadge`, `KaraokePlayer._shareHeartbeatTimer`, `useAuth().fetchMe`). Суммарно — до 15 параллельных polling-запросов от одного пользователя.

При пиках (например, во время стрима на 100+ зрителей с share-ссылками) **суммарное число polling-запросов по всему сайту** может достигать сотен в минуту. Каждый polling-запрос синхронно INSERT'ит в `tbl_events` через `doRegisterEvent`. Никакого HTTP-кеширования или server-side throttling не настроено.

**Why this priority**: Polling × N пользователей × N вкладок = O(N²). Без какого-либо контроля даже 100 пользователей по 3 вкладки дают 300 polling-запросов каждые 20-45 секунд = 600-900 INSERT'ов в минуту только на фоне. С лимитом `pg max_connections = 100` это ставит под угрозу основную БД.

**Independent Test**: Поднять 5 вкладок `/zakroma` как залогиненный пользователь, через 5 минут замерить в логе karaoke-web: (а) сколько запросов ушло на `/api/public/news/since`, `/api/public/account/chat/unreadcount`, `/api/public/share/heartbeat`, `/api/public/auth/me`; (б) сколько INSERT'ов в `tbl_events` от `eventType=CALL_REST, restName=...`. Целевые значения: (а) ≤ 5 запросов каждого типа за 5 минут (с учётом backoff/dedup), (б) ≤ 5 INSERT'ов в минуту от polling-запросов.

**Acceptance Scenarios**:

1. **Given** залогиненный пользователь открыл `/zakroma` и держит вкладку открытой 10 минут, **When** истекают 45 секунд с момента загрузки, **Then** polling `NewsBell` либо (а) дедуплицируется с предыдущим тиком (no-op, если `unread=0` после первого ответа), либо (б) кэширует ответ на 30-60 сек in-memory, **And** не вызывает новый `INSERT` в `tbl_events` для повторных безрезультатных тиков.
2. **Given** залогиненный пользователь с активной подпиской держит 3 вкладки одновременно, **When** все 3 вкладки одновременно отправляют polling на `/api/public/account/chat/unreadcount`, **Then** бэкенд либо (а) обслуживает все 3 запроса из общего in-process кеша (≤ 30 сек TTL), либо (б) отдаёт `304 Not Modified` с пустым телом, **And** никакой из них не пишет `INSERT` в `tbl_events`.
3. **Given** share-link гость смотрит плеер 10 минут, **When** истекают 25 секунд с последнего heartbeat, **Then** плеер отправляет `POST /api/public/share/heartbeat`, **And** запрос НЕ пишет `INSERT` в `tbl_events` (heartbeat — это не аналитическое событие, а технический механизм lease).
4. **Given** анонимный/залогиненный пользователь открыл любую страницу сайта, **When** `useAuth` запускает `setInterval(fetchMe, 5*60*1000)`, **Then** каждые 5 минут уходит ровно 1 запрос `GET /api/public/auth/me`, **And** НЕ дублируется per-вкладка при открытии одной и той же SPA в нескольких табах (см. `useAuth.js:55` — `autoRefreshStarted` уже дедуплицирует).

---

### User Story 3 — Каждый REST-запрос не делает синхронный INSERT в tbl_events (Priority: P1)

Анонимный посетитель открывает сайт, открывает страницу песни, делает поиск, листает каталог — всё это создаёт десятки REST-запросов в karaoke-web. Каждый из них через `doRegisterEvent` (`MainController.kt:121`) синхронно выполняет `INSERT INTO tbl_events (...)`. Это:
- Создаёт O(N) INSERT-запросов к БД на каждый визит (вместо аналитического sampling).
- Держит открытое JDBC-соединение на время INSERT (thread-local кеш `KaraokeConnection.kt:30`).
- Увеличивает размер `tbl_events` неконтролируемо (нет автоматической очистки — см. `27_listening_history.sql:3-6` где упоминается что `tbl_events` регулярно опустошается через sync, но это про PROD-only).

**Why this priority**: Этот один источник INSERT'ов генерирует больше БД-нагрузки чем все остальные polling-эндпоинты вместе взятые. При 100 одновременных посетителях × 30 REST-запросов за визит = 3000 INSERT'ов в БД за 1 час сессии.

**Independent Test**: Запустить нагрузочный скрипт (curl / wrk) — 50 параллельных клиентов делают по 100 запросов на `/api/public/songs?songName=test` (без кеширования). Замерить: (а) время ответа p95; (б) число INSERT'ов в `tbl_events` за время теста. Целевые: (а) p95 ≤ 200 мс (сейчас зависит от БД), (б) 0 INSERT'ов в `tbl_events` от CALL_REST событий (или радикальное сокращение, например ≤ 5% от текущего).

**Acceptance Scenarios**:

1. **Given** анонимный/залогиненный посетитель делает 100 REST-запросов подряд за 1 минуту, **When** обрабатываются эти запросы, **Then** `tbl_events` получает НЕ БОЛЕЕ 5 INSERT-строк от `eventType=CALL_REST` за эту минуту (sampling rate 1 из 20, или иная стратегия).
2. **Given** посетитель делает тот же REST-запрос повторно (например `/api/public/songs?songName=test`) в течение 5 секунд, **When** обрабатывается повторный запрос, **Then** бэкенд отдаёт кешированный ответ без обращения к БД (in-memory TTL кеш, ≥ 10 сек).
3. **Given** `tbl_events` заполняется событиями любой природы (CALL_REST, PLAY, CLICK, UI, player), **When** размер таблицы превышает 100 000 строк, **Then** фоновый scheduled-task (Spring `@Scheduled`) автоматически удаляет самые старые строки старше 7 дней (или иной retention policy, см. FR-005 ниже).
4. **Given** INSERT в `tbl_events` для конкретного запроса важен (например, для отладки инцидента), **When** разработчик устанавливает debug-режим, **Then** каждый CALL_REST пишет INSERT (как сейчас), **And** debug-режим управляется через env-переменную `KARAOKE_WEB_DEBUG_EVENTS=true`.

---

### User Story 4 — Картинки Закромов имеют корректные HTTP-кеш-заголовки (Priority: P2)

Браузер посетителя запрашивает превью автора `/minio/karaoke/Author/Author.preview.author.png`. nginx проксирует его в MinIO и отдаёт без `Cache-Control` / `ETag` / `Last-Modified` заголовков (проверено: nginx `proxy_pass` по умолчанию НЕ добавляет кеш-заголовки если upstream их не вернул, а MinIO их не возвращает для статики).

**Why this priority**: Без кеш-заголовков браузер при F5 / возврате на страницу / переходе на другой автор полностью перезагружает ВСЕ тайлы (до 200+ HTTP-запросов зря). Это усиливает проблему US1.

**Independent Test**: Открыть `/zakroma`, дождаться загрузки всех тайлов, нажать F5, в DevTools → Network замерить размер ответов для тех же URL. Целевые: на F5 все 200+ запросов возвращают `200 (from disk cache)` или `304 Not Modified`, **And** `Transfer-Size = 0` для всех.

**Acceptance Scenarios**:

1. **Given** посетитель загрузил `/zakroma` с тайлом автора «Кино», **When** в течение следующих 24 часов посетитель переходит на любую страницу, где этот тайл снова виден, **Then** браузер НЕ отправляет HTTP-запрос за `/minio/karaoke/Кино/Кино.preview.author.png` (используется HTTP-кеш).
2. **Given** разработчик изменил картинку автора в MinIO, **When** происходит следующий запрос за `/minio/...`, **Then** nginx/MinIO отдаёт новую версию, **And** старые кешированные копии автоматически становятся невалидными (через ETag или версионирование URL).
3. **Given** nginx передаёт файл из MinIO, **When** возвращается ответ, **Then** HTTP-заголовки содержат `Cache-Control: public, max-age=86400` (24 часа) и `ETag: "<hash>"` или `Last-Modified: <timestamp>`.

---

### User Story 5 — Анализ других потенциальных источников нагрузки (Priority: P1)

Прежде чем закрывать любые другие источники, нужно провести **полный аудит** всех эндпоинтов и фоновых задач, которые потенциально могут давать нагрузку. Это будет оформлено в `research.md` как таблица «источник / текущая нагрузка / риск / приоритет фикса». Минимум должны быть проверены:

- `GET /api/public/songs` — синхронный SELECT + INSERT в `tbl_events`
- `GET /api/public/song/{id}` — синхронный SELECT + INSERT в `tbl_events`
- `GET /api/public/zakroma` (legacy non-stream) — синхронный SELECT + INSERT
- `GET /api/public/zakroma/stream` — синхронный INSERT + heavy SELECT + streaming
- `POST /api/public/zakroma/stream/metrics` — синхронный INSERT (до N events за раз)
- `GET /api/public/stats` — синхронный INSERT + 5 SELECT
- `GET /api/public/picture?file=...` — 302 redirect через Spring (N×200+ для тайлов)
- `GET /api/public/song-picture/{id}` — генерация BufferedImage 800×194 без кеша (см. Pass 60 — боты)
- `GET /api/public/song-vk-image/{id}` — генерация BufferedImage 1200×630 + кеш в `/tmp` (см. Pass 60 — боты)
- `GET /api/public/auth/me` — polling useAuth() каждые 5 мин
- `GET /api/public/authors` — синхронный SELECT + INSERT
- `GET /api/public/authors-tiles` — синхронный SELECT (2 запроса) + INSERT
- `GET /api/public/news` — синхронный SELECT + INSERT
- `GET /api/public/news/since` — синхронный SELECT (ано → 0) — фикс Pass 52
- `GET /api/public/account/history` — синхронный SELECT без INSERT
- `GET /api/public/account/chat/unreadcount` — polling 20 сек для премиум + INSERT
- `POST /api/public/share/heartbeat` — UPDATE в БД без INSERT в tbl_events (но всё равно polling)
- Все scheduled-task'и: `ShareLinkSweeper`, `VkAutoPublishScheduler`, `TelegramAutoPublishScheduler`, `PremiumAutoPublishScheduler`, `SponsrSyncScheduler`, `StemJobPollScheduler` и др. — проверка что они не делают массовых INSERT'ов без backoff'а.

**Why this priority**: Без полного аудита невозможно гарантировать, что инцидент не повторится из-за другого эндпоинта, который мы не рассмотрели.

**Independent Test**: Прочитать `research.md`, проверить, что для каждого публичного REST-эндпоинта и для каждого scheduled-task есть: (а) file:line ссылка на код; (б) оценка нагрузки при N пользователях; (в) приоритет фикса P1/P2/P3.

**Acceptance Scenarios**:

1. **Given** аналитик/разработчик читает `research.md`, **When** он доходит до конца таблицы, **Then** ВСЕ публичные REST-эндпоинты `karaoke-web` и все `@Scheduled` задачи `karaoke-web` и `karaoke-app` (публичные части) перечислены с явным verdict «OK / requires fix / requires investigation».
2. **Given** в `research.md` есть источник с verdict «requires fix» (P1 или P2), **When** реализация фичи завершена, **Then** все P1-источники из таблицы имеют фикс в коде, **And** все P2-источники либо имеют фикс, либо явно перенесены в backlog с обоснованием.

---

### User Story 6 — Мониторинг позволяет заранее видеть приближение к исчерпанию ресурсов (Priority: P3)

Когда сайт подходит к исчерпанию `pg max_connections`, нет проактивного сигнала — инцидент наступает внезапно. Хотя бы один простой сигнал (например, debug-эндпоинт `/api/public/debug/db` который возвращает `pg_stat_activity` с числом активных коннектов) позволит post-hoc анализировать инциденты.

**Why this priority**: Не P1, потому что фикс источников важнее мониторинга. Но если P1-фиксы не помогут (или для их валидации) — мониторинг нужен. Также полезен для Pass 174 аналогии (там добавили `503 stats.unavailable` + debug-эндпоинт `/api/stats/debug`).

**Independent Test**: В браузере открыть `https://sm-karaoke.ru/api/public/debug/db` (после реализации), проверить, что ответ содержит `{active: N, idle: M, max: 100, ...}` с актуальными числами. На нагрузочном тесте (50 параллельных curl) — убедиться, что число `active` не упирается в `max`.

**Acceptance Scenarios**:

1. **Given** разработчик/админ открывает `GET /api/public/debug/db`, **When** обрабатывается запрос, **Then** ответ содержит JSON с `{pgActiveConnections, pgIdleConnections, pgMaxConnections, currentThreadCount, ...}`, **And** НЕ падает с ошибкой даже если `pg_max_connections` почти исчерпан (использует короткий `connectTimeout` + `socketTimeout`).
2. **Given** в продакшене случился инцидент «сайт недоступен», **When** разработчик post-hoc открывает debug-эндпоинт, **Then** он видит актуальное состояние пула коннектов + может коррелировать с логами.

---

### Edge Cases

- **Мобильный пользователь с медленным 3G**: 200+ тайлов автора могут не загрузиться за разумное время. Нужно убедиться, что `loading="lazy"` уже работает (см. `AuthorTiles.vue:15`) и не блокирует UI. Если тайлов слишком много для сетки — нужна пагинация.
- **Бот-краулер (bingbot, YandexBot)**: каждый такой запрос сейчас делает INSERT в tbl_events. Если бот-трафик значителен — нужно рейт-лимитить или вообще не регистрировать события от ботов.
- **Пользователь оставил вкладку открытой на сутки**: polling продолжает работать. Через 24 часа `tbl_events` получит 1728 INSERT'ов от одного пользователя (например, 20-сек chat unread × 4320 = 4320, минус dedup). Нужен либо TTL, либо sampling.
- **Сетевая ошибка при загрузке тайла**: браузер должен повторить 1 раз через 30 сек, не бесконечно. Если MinIO файл удалён — отдать `404` без бесконечных ретраев.
- **CDN перед nginx**: если в будущем добавится CDN — он может не отдавать `Cache-Control` от MinIO; нужно учесть, что nginx должен добавлять `Cache-Control` сам (`expires 24h;` или `add_header Cache-Control ...`).
- **Картинка автора в MinIO не существует**: `AuthorTiles.vue:17` скрывает `<img>` через `@error`, но сам запрос всё равно уходит на nginx → MinIO → `404`. Нужно учесть что 200+ запросов вхолостую (404) — тоже нагрузка.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `/api/public/picture?file=...` MUST отдавать `302 Found` с заголовком `Location: /minio/karaoke/<encoded path>` БЕЗ обработки Spring-контроллером (т.е. фронт должен формировать URL напрямую `/minio/karaoke/...`). Для обратной совместимости legacy `/api/public/picture?file=...` сохраняется как 302 redirect (как сейчас), но НЕ используется в новом коде.
- **FR-002**: `AuthorTilePublicDto.authorPictureUrl` MUST возвращать прямой URL `/minio/karaoke/<encoded preview.path>` (без `/api/public/picture` обёртки). Картинка должна грузиться напрямую через nginx.
- **FR-003**: nginx `/minio/` location MUST добавлять `Cache-Control: public, max-age=86400` (24 часа) ко всем успешным ответам от MinIO (для превью-картинок и других статических файлов). Применяется через `expires 24h;` или `add_header Cache-Control "public, max-age=86400";` в nginx-конфиге (`deploy/80to8897`).
- **FR-004**: nginx MUST добавлять `ETag` или `Last-Modified` к ответам от MinIO (если MinIO их не возвращает — вычислять nginx по `mtime` файла, или использовать `If-Modified-Since` с принудительным `304`).
- **FR-005**: Если картинка автора не существует в MinIO (`404`), nginx MUST отдавать `Cache-Control: public, max-age=300` (5 минут) вместо 24 часов, чтобы не кешировать 404 надолго и не залипнуть с «битой» ссылкой.
- **FR-006**: В `MainController.doRegisterEvent` MUST быть реализован дифференцированный sampling для `eventType=CALL_REST` (clarified 2026-08-14): **rate=1/20 для анонимов** (5% INSERT'ов), **rate=1/5 для залогиненных** (20% INSERT'ов), **rate=1/1 для админов** (`isAdmin=true`, 100% INSERT'ов). Все три значения настраиваются через `KaraokeProperties.kt` (env `KARAOKE_WEB_EVENTS_SAMPLING_ANON`, `KARAOKE_WEB_EVENTS_SAMPLING_LOGGED`, `KARAOKE_WEB_EVENTS_SAMPLING_ADMIN`), дефолты соответственно 20, 5, 1. Sampling НЕ применяется к `eventType=PLAY`, `eventType=CLICK`, `eventType=PLAYER`, `eventType=UI` (последние 4 пишутся всегда). Реализация: в `doRegisterEvent` для `eventType=CALL_REST` после проверки прав — `if (Math.random() * samplingRate >= 1) return false` (early-exit ДО INSERT'а).
- **FR-007**: `MainController.doRegisterEvent` для `eventType=CALL_REST` MUST иметь in-memory дедупликацию (clarified 2026-08-14): ключ дедупа = `(restName, canonical(rest_parameters), anonId-or-userId)` (per-user scope — разные пользователи с одинаковыми действиями пишутся отдельно). Если тот же ключ приходит повторно в течение 30 секунд — INSERT НЕ выполняется. Реализация: `ConcurrentHashMap<DedupKey, Long>` (epoch ms timestamp), проверка `now() - lastSeen < 30_000L`. TTL ключей — 30 сек + lazy cleanup (удаление при lookup если expired). Per-user scope означает до 30k активных ключей при N=1000 одновременных пользователей × 30 эндпоинтов = ~7 MB heap (приемлемо). Дедупликация не заменяет sampling (FR-006), а дополняет его — dedup срабатывает ДО sampling.
- **FR-008** (clarified 2026-08-14): Polling-эндпоинты MUST иметь server-side in-memory кеш для одинаковых запросов. Если два запроса с одинаковыми параметрами приходят в течение TTL — оба получают один и тот же кешированный ответ БЕЗ обращения к БД. Per-endpoint TTL (решение D-7 в plan.md):
  - `/api/public/news/since` — TTL **60 сек** (новости меняются нечасто).
  - `/api/public/account/chat/unreadcount` — TTL **10 сек** (важно для UX бейджа, polling 20 сек).
  - `/api/public/share/heartbeat` — TTL **15 сек** (heartbeat каждые 25 сек, кэш 15 = каждый 2-й запрос no-op).
- **FR-009**: `POST /api/public/share/heartbeat` MUST НЕ писать `INSERT` в `tbl_events` (heartbeat — это технический механизм lease, не аналитическое событие). Текущее поведение проверить — возможно уже не пишет (нужно сверить с `MainController.doRegisterEvent` вызовами).
- **FR-010**: `GET /api/public/song-picture/{id}` и `GET /api/public/song-vk-image/{id}` MUST быть rate-limit'нуты по IP (clarified 2026-08-14): **не более 60 запросов/мин с одного IP** (защита от bot-storm, Pass 60 зафиксировал эту проблему). Реализация: in-memory `ConcurrentHashMap<String, RateLimitBucket>` с TTL окна 60 сек; при превышении — 429 Too Many Requests с `Retry-After: 60` заголовком. Боты после редиректа `80to8897` получают SEO-HTML вместо BufferedImage, но эта защита нужна для прямого вызова endpoint'ов (например, если бот научится обходить User-Agent-sniffing). Легитимные share-link сценарии (1-2 preview в минуту) проходят свободно.
- **FR-011**: В karaoke-web добавляется фоновый `@Scheduled` task (раз в сутки, cron `0 0 3 * * *` — 3 AM), который удаляет строки из `tbl_events` старше 7 дней (или иной retention period, настраивается через env). Без этой очистки `tbl_events` будет расти неограниченно (в текущей версии синхронизация `tbl_events` НЕ настроена — см. `27_listening_history.sql:3-6`).
- **FR-012**: `MainController.doRegisterEvent` MUST логировать предупреждение (`SLF4J log.warn`) при `SQLException` (например, `pg connection pool exhausted`), а не молча падать. Это нужно для post-hoc анализа инцидентов.
- **FR-013** (clarified 2026-08-14): В `karaoke-web` добавляется debug-эндпоинт `GET /api/public/debug/db`, который возвращает JSON: `{pgActiveConnections, pgIdleConnections, pgMaxConnections, currentThreadCount, currentTomcatMaxThreads, sampledAt}`. Доступ — **только через IP allowlist** (решение D-5 в plan.md), настраивается через env `KARAOKE_WEB_DEBUG_DB_ALLOWED_IPS` (comma-separated). Если переменная пуста — endpoint отключён (404 Not Found). С IP вне allowlist — 403 Forbidden. В продакшене доступ через VPN (IP админа включается в allowlist).
- **FR-014**: В `karaoke-public/src/store/modules/zakroma.js` добавить Vuex-state `lastLoadedTilesAt: 0` и в `loadAuthorTiles` — дедупликация: если `< 30 сек` с последнего успешного `setAuthorTiles(tiles)`, **And** массив не пустой — no-op (без HTTP). По аналогии с `lastLoadedTimestampByAuthor` для zakroma stream (FR-FE-009 из спеки 181).
- **FR-015**: Должна быть проведена инвентаризация ВСЕХ `@Scheduled` задач в karaoke-web + karaoke-app (публичная часть) с указанием: (а) имя класса + метода; (б) cron/interval; (в) какие SQL делает (SELECT/INSERT/UPDATE); (г) сколько строк может задеть в худшем случае; (д) verdict «OK / requires fix / requires investigation». Результат — таблица в `research.md`.
- **FR-016**: Должна быть проведена инвентаризация ВСЕХ публичных REST-эндпоинтов karaoke-web с указанием: (а) HTTP-метод + URL; (б) какие SQL делает (SELECT/INSERT/UPDATE); (в) пишет ли `INSERT` в `tbl_events`; (г) verdict «OK / requires fix / requires investigation». Результат — таблица в `research.md`.
- **FR-017**: Все P1-источники нагрузки (определённые в `research.md`) MUST иметь фикс в коде в рамках этой же фичи или явно перенесены в backlog с обоснованием в `plan.md`. P2-источники — на усмотрение.
- **FR-018**: Фиксы НЕ ДОЛЖНЫ нарушать Constitution: (а) никакого JPA/Hibernate (только сырой JDBC); (б) `recordhash` триггеры не должны измениться; (в) sync-флаги остаются как есть; (г) сервисы по-прежнему могут переиспользоваться между LOCAL/SERVER; (д) ad-blocking и прочий transparent для пользователя функционал остаётся.
- **FR-019**: Фиксы НЕ ДОЛЖНЫ ломать обратной совместимости: (а) старый `/api/public/picture?file=...` продолжает работать как 302-redirect; (б) `AuthorTilePublicDto.authorPictureUrl` может либо вернуть прямой URL, либо старый — формат JSON тот же (`String`); (в) дедупликация/sampling/proba НЕ применяются к `eventType=PLAY` (важно для корректной статистики просмотров).
- **FR-020**: README/per-feature документация MUST быть обновлена после реализации (clarified 2026-08-14): создать отдельный документ `docs/features/site-traffic-resilience.md` и добавить запись в секцию «Cross-cutting» `docs/features/README.md` (по аналогии с `ci-lint-enforcement.md`). Документ содержит секции: «Что / Зачем / Как / Инварианты / Ловушки / Ссылки» — что и куда вынести. На этот документ ссылаются через `@see docs/features/site-traffic-resilience.md` все новые публичные классы фичи (`MainController.doRegisterEvent`, новый `SamplingFilter`, `DedupCache`, `RateLimitInterceptor`, `EventsRetentionScheduler`, debug-endpoint контроллер). KDoc обязателен по Constitution FR-006.

### Key Entities *(include if feature involves data)*

- **AuditEntry** (строка таблицы в `research.md`): источник нагрузки (имя эндпоинта или scheduled-task), тип нагрузки (DB SELECT/INSERT/UPDATE, HTTP redirect, image fetch), file:line, оценка количества запросов при N пользователей, verdict (OK/fix/investigate), приоритет фикса.
- **SamplingRate**: число от 0 до 1, определяющее долю `CALL_REST` событий, которые реально пишутся в `tbl_events`. Персистится в KaraokeProperties (настраивается через `Karaoke.properties` без перекомпиляции).
- **DedupKey**: ключ для in-memory дедупликации повторных запросов (формат `<restName>:<canonical(parameters)>:<anonId-or-userId>`), TTL 30 сек, in-memory `ConcurrentHashMap` с автоматической очисткой по `expiresAt`.
- **TilesCacheState** (Vuex): `lastLoadedTilesAt` (epoch ms) для дедупликации `loadAuthorTiles` на фронте.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: При открытии `/zakroma` анонимным пользователем (cold cache) Spring-контроллер `PublicApiController.picture()` получает **0 HTTP-запросов** (все картинки идут напрямую через nginx → MinIO). Замер: DevTools → Network, фильтр по `/api/public/picture?file=`.
- **SC-002**: При повторном открытии `/zakrama` в течение 24 часов (warm cache) браузер отправляет **0 HTTP-запросов** для загрузки превью-картинок авторов (все из HTTP-кеша `200 from disk cache` или `304 Not Modified`). Замер: тот же фильтр + проверка `Transfer-Size`.
- **SC-003**: `tbl_events` получает **не более 5 INSERT-строк в минуту** от `eventType=CALL_REST` polling-запросов (`/news/since`, `/chat/unreadcount`, `/auth/me`) при 10 одновременных залогиненных пользователях с 3 вкладками каждый (30 вкладок суммарно). Замер: `SELECT count(*) FROM tbl_events WHERE event_type='CALL_REST' AND last_update > now() - interval '1 minute'` каждые 60 сек в течение 5 минут. С дифференцированным sampling (FR-006): при 30 вкладках × 1 polling/45s = 40 запросов/мин × rate=1/5 для залогиненных = **8 INSERT/мин** (что в пределах SC). Анонимные вкладки при rate=1/20 дают ещё меньше.
- **SC-004**: При 50 параллельных клиентах (нагрузочный тест wrk/curl) на любой из публичных REST-эндпоинтов karaoke-web — p95 latency ≤ 200 мс, p99 latency ≤ 500 мс. До фикса эти цифры могут быть 1000+ мс под нагрузкой из-за исчерпания `pg_max_connections`.
- **SC-005**: Размер `tbl_events` остаётся **стабильным во времени** (не растёт неограниченно): retention policy удаляет строки старше 7 дней, поэтому средний размер таблицы колеблется около равновесного значения (≈ (rate INSERT) × 7 дней). До фикса `tbl_events` растёт неконтролируемо (см. `27_listening_history.sql:3-6` — sync не настроен).
- **SC-006**: Инцидент «сайт недоступен 7-10 минут» НЕ воспроизводится при типичной нагрузке (≤ 100 одновременных посетителей, ≤ 5 вкладок у каждого). Тест: непрерывный wrk/curl в течение 30 минут — все запросы возвращают 2xx в течение разумного времени.
- **SC-007**: Загрузка страницы `/zakroma` (cold cache, 200+ тайлов) завершается **≤ 4 сек до видимого контента** (first contentful paint) на типичном канале (10 Mbps, 50 мс RTT). До фикса — 5-7 сек.
- **SC-008**: Bot-краулеры (bingbot, YandexBot) получают rate-limit 429 при > **60 запросов/мин** (clarified 2026-08-14) на `/api/public/song-picture/{id}` или `/api/public/song-vk-image/{id}` с одного IP. До фикса бот может генерировать сотни запросов в минуту, каждый — синхронный `BufferedImage` 500-1500 мс.
- **SC-009**: Каждый публичный REST-эндпоинт karaoke-web и каждая `@Scheduled` задача имеют audit-запись в `research.md` с verdict. Полнота: 100% покрытие. Тест: `bash tools/check-audit-coverage.sh` (новый helper) проверяет, что для каждого `@GetMapping`/`@PostMapping` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/*.kt` есть запись в `research.md`.

## Assumptions

- **A-001**: 100 одновременных посетителей и 5 вкладок у каждого — это «нормальная» нагрузка для сайта sm-karaoke.ru (на основе метрик за последний месяц). Увеличение в 10× — это уже аномалия, которую можно лечить отдельно.
- **A-002**: Sampling `CALL_REST` событий 1 из 20 для анонимов не приведёт к потере важной аналитики — точные данные для аналитики берутся из агрегатов (Google Analytics, Яндекс.Метрика), а `tbl_events` нужна для дебага конкретных действий.
- **A-003**: nginx добавляет `Cache-Control: public, max-age=86400` для всех ответов `/minio/` — это безопасно, потому что превью-картинки меняются крайне редко (только при смене аватарки автора).
- **A-004**: `tbl_events` retention period 7 дней — разумный баланс между «хватит для дебага инцидента» и «не растёт бесконечно». Настраивается через env `EVENTS_RETENTION_DAYS`.
- **A-005**: `pg_max_connections = 100` — текущий лимит. Не увеличиваем (маскировка проблемы). Реальное решение — снижать нагрузку на БД.
- **A-006**: In-memory дедупликация в `MainController.doRegisterEvent` работает per-instance karaoke-web. При нескольких instances (сейчас 1) нужен Redis. Деградирует gracefully: при большом числе instances дедуп не идеален, но sampling всё равно работает.
- **A-007**: Pass 60 (SEO-HTML для ботов) уже решил проблему ` /api/public/song-vk-image/{id}` для ботов через nginx-redirect по User-Agent. Этот фикс — дополнение для прямого вызова эндпоинта (например, если бот научится обходить User-Agent-sniffing).
- **A-008**: `useAuth().fetchMe` уже имеет module-level dedup (`autoRefreshStarted` в `useAuth.js:55`), но polling каждые 5 минут × N вкладок всё равно создаёт N запросов каждые 5 минут. Нужно либо per-tab dedup в localStorage, либо глобальный in-process кеш на бэке.

## Out of Scope

- Увеличение `pg_max_connections` (маскировка, не решение).
- Переписывание архитектуры БД (шардинг, репликация, выделение аналитической БД). Это отдельная фича, если потребуется.
- Миграция с `KaraokeConnection` (thread-local cache) на HikariCP/DBCP. Это отдельная фича, требует `KaraokeConnection.kt` рефакторинга и прогона через Constitution Check.
- Замена polling на SSE для всех эндпоинтов (это большая фича, спецификация отдельно).
- Замена nginx на CDN (Cloudflare, etc.) — это инфраструктурное решение, не код-фича.
- Изменение retention policy для других таблиц (`tbl_news`, `tbl_song_share_sessions` и т.п.) — только `tbl_events`.

## Open Questions (для `/speckit.clarify`)

Все запланированные вопросы (`Q1`–`Q4`) резолвнуты через `/speckit.clarify`. См. секцию `## Clarifications` ниже. Дополнительных критичных неоднозначностей для этой сессии не выявлено — остальные архитектурные детали (TTL in-memory кеша для polling, способ доступа к debug endpoint, и т.п.) вынесены в `plan.md` как технические решения.

## Clarifications

### Session 2026-08-14

- **Q1**: Какой sampling rate использовать для `eventType=CALL_REST` от анонимов? → A: **B** — дифференцированный sampling: 1/20 для анонимов, 1/5 для залогиненных, 1/1 для админов (см. FR-006).
- **Q2**: Дедупликация повторных запросов в `doRegisterEvent` — по `anonId` или глобальная? → A: **B** — per-`anonId`/`userId`, ключ `(restName, parameters, anonId-or-userId)` (см. FR-007).
- **Q3**: Rate-limit для `/api/public/song-picture/{id}` — какой лимит? → A: **A** — 60 запросов/мин на IP (см. FR-010).
- **Q4**: Per-feature документ — отдельный или дополнить существующий? → A: **A** — отдельный `docs/features/site-traffic-resilience.md` в секции «Cross-cutting» (см. FR-020).
- **Q5**: Какой TTL для server-side polling cache — единый или per-endpoint? → A: **per-endpoint** (news=60s, chat=10s, share=15s), решение D-7 в plan.md (см. FR-008).
- **Q6**: Доступ к `/api/public/debug/db` — IP allowlist или basic auth? → A: **только IP allowlist** через env `KARAOKE_WEB_DEBUG_DB_ALLOWED_IPS` (см. FR-013).