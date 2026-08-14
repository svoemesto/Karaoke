# Research: Аудит источников нагрузки sm-karaoke.ru

**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-08-14
**Branch**: `187-site-traffic-anomaly-investigation`

## Контекст и мотивация

Сайт `sm-karaoke.ru` периодически (1-2 раза в неделю) становится недоступным на 7-10 минут. Первый раунд фиксов (Pass 52, спека 174, ветка `174-fix-news-since-anon`) устранил самый очевидный источник — `/api/public/news/since` для анонимов возвращал 3.5 MB JSON × 45 сек × N пользователей × N вкладок → exhaustion `pg max_connections = 100`. Но инциденты продолжаются. Эта фича — полный аудит всех оставшихся потенциальных источников.

## Метод

С помощью `codegraph_explore` (см. `.specify/extensions.yml`) просканированы:
- `MainController.doRegisterEvent` и 13 его вызывающих мест.
- `PublicApiController.kt` — все `@GetMapping`/`@PostMapping` (13 эндпоинтов).
- `PublicPlayerController.kt` (9 эндпоинтов).
- `PublicShareController.kt` (8 эндпоинтов).
- `PublicSongEditorController.kt` (9 эндпоинтов).
- `PublicChatController.kt` (4 эндпоинта).
- `PublicAccountController.kt` (4 эндпоинта).
- `PublicNewsController.kt` (2 эндпоинта).
- `MainController.kt` (не-/api/public/* — 8 эндпоинтов, из них `/registerevent` — главный sink для INSERT).
- 12 `@Scheduled` задач (см. таблицу B).

Вручную прочитаны:
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt:121-282` — doRegisterEvent.
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt:871-888` — picture endpoint.
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/AuthorTilePublicDto.kt:34-37` — формирование URL.
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicChatController.kt:102-108` — unreadCount short-circuit.
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicShareController.kt:194-211` — heartbeat (НЕ пишет в tbl_events).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishScheduler.kt` — пример scheduled-task.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/StemJobPollScheduler.kt` — пример scheduled-task.
- `karaoke-public/src/composables/useAuth.js:55-67` — module-level dedup fetchMe.
- `.specify/memory/constitution.md` (принципы II, IV, V, VI, VIII).
- `docs/architecture-notes.md` — Pass 50-60 (news/since fix, stats leak, SEO HTML).

---

## Таблица A: Публичные REST-эндпоинты karaoke-web (FR-016)

**Шкала приоритета**: P1 = критично (влияет на доступность сайта), P2 = средне (оптимизация), P3 = низко (мониторинг/backlog).

### A.0. Контроллеры и их базовые классы

| Базовый путь | Файл | Заметка |
|---|---|---|
| `/api/public/*` (основная публичная зона) | `PublicApiController.kt` | 13 эндпоинтов |
| `/api/public/player/*` | `PublicPlayerController.kt` | 9 эндпоинтов (файлы стемов, playerdata) |
| `/api/public/news/*` | `PublicNewsController.kt` | 2 эндпоинта |
| `/api/public/account/*` | `PublicAccountController.kt` | 4 эндпоинта |
| `/api/public/account/chat/*` | `PublicChatController.kt` | 4 эндпоинта |
| `/api/public/account/editor/*` | `PublicSongEditorController.kt` | 9 эндпоинтов |
| `/api/public/share/*` | `PublicShareController.kt` | 8 эндпоинтов |
| `/api/public/picture` | `PublicApiController.kt:871-888` | см. ниже |
| `/registerevent`, `/changerecords` (legacy) | `MainController.kt` | sink INSERT'ов |

### A.1. Все публичные эндпоинты (verdicts)

| # | Endpoint | Метод | Файл:line | Что делает | INSERT в `tbl_events`? | Оценка нагрузки при N=100 пол. × 3 вкладки | Verdict | Приоритет |
|---|---|---|---|---|---|---|---|---|
| 1 | `/api/public/stats` | GET | `PublicApiController.kt:97-121` | 5 SELECT COUNT из `tbl_settings` через `StatBySong` (SQL-апроксимация), затем `doRegisterEvent` | ДА (CALL_REST) | ~5 SELECT COUNT × 100 × 3 = 1500 SELECT/сек; INSERT 150/мин | OK (StatBySong использует in-memory cache через `StatsCacheScheduler`, см. A-2 ниже) | P2 |
| 2 | `/api/public/authors` | GET | `PublicApiController.kt:123-139` | `Song.loadListAuthors` (1 SELECT) | НЕТ | 100 SELECT/сек | OK | P3 |
| 3 | `/api/public/authors-tiles` | GET | `PublicApiController.kt:141-181` | `Song.loadAuthorSongCounts` (1 SELECT) + `loadListAuthors` (1 SELECT), затем `AuthorTilePublicDto.fromAuthorName` × N авторов | НЕТ (прямо) | 200 SELECT/сек (по 1 на запрос) | **Requires fix**: возвращаемый `authorPictureUrl` идёт через `/api/public/picture` → 302 → nginx, что влечёт O(N тайлов) редиректов | **P1** |
| 4 | `/api/public/zakroma` | GET | `PublicApiController.kt:183-229` | `Zakroma.getZakroma` (тяжёлый SELECT) + `doRegisterEvent` | ДА | ~500 SELECT/мин + 300 INSERT/мин | OK (использует NDJSON стрим) | P2 |
| 5 | `/api/public/zakroma/stream` | GET (NDJSON) | `PublicApiController.kt:254-403` | NDJSON стрим, батч по 50 песен | ДА (один раз за стрим) | 300 стримов/мин × 1 INSERT каждый | OK (уже оптимизирован в спеках 181/186) | P3 |
| 6 | `/api/public/zakroma/stream/metrics` | POST | `PublicApiController.kt:467-507` | Принимает N метрик стрима (каждая — отдельный `doRegisterEvent`) | ДА (N раз за запрос) | До 50 INSERT/мин с одного юзера × 100 юзеров = 5000 INSERT/мин при пике | **Requires fix**: нужен дедуп + sampling (FR-006/007) | **P1** |
| 7 | `/api/public/songs` | GET | `PublicApiController.kt:509-578` | `Song.loadListWithFilter` (поиск по имени) + `doRegisterEvent` | ДА | 300 SELECT/мин + 300 INSERT/мин | OK (фильтр простой, поиск по индексу) | P3 |
| 8 | `/api/public/song/{id}` | GET | `PublicApiController.kt:580-630` | `Song.loadFromDbById` + `doRegisterEvent` | ДА | 100 SELECT/мин + 100 INSERT/мин | OK | P3 |
| 9 | `/api/public/events` | POST | `PublicApiController.kt:632-655` | Обёртка над `doRegisterEvent` (альтернативный путь регистрации событий с клиента) | ДА | До 50 INSERT/мин на юзера × 100 = 5000 INSERT/мин | **Requires fix**: тот же sampling/dedup (FR-006/007) | **P1** |
| 10 | `/api/public/song-picture/{id}` | GET | `PublicApiController.kt:656-717` | Генерирует BufferedImage 800×194 синхронно (`ImageIO.write`) + 2 SELECT + 2 GET из MinIO | НЕТ (но тяжёлый CPU + I/O) | 500-1500 мс на запрос × бот 100/мин = 100-250 сек CPU/мин на одного бота | **Requires fix**: rate-limit (FR-010) + nginx cache | **P1** |
| 11 | `/api/public/song-vk-image/{id}` | GET | `PublicApiController.kt:719-768` | Генерирует BufferedImage 1200×630, файл-кэш `/tmp/vk_$id.png` | НЕТ (но тяжёлый CPU + I/O) | 500-1500 мс × бот 100/мин | **Requires fix**: rate-limit (FR-010) + nginx cache + проверить file cache TTL | **P1** |
| 12 | `/api/public/picture` | GET | `PublicApiController.kt:871-888` | 302 Found → `/minio/karaoke/<encoded path>` | НЕТ (но это Spring-контроллер) | O(N тайлов) × N юзеров = десятки тысяч запросов/мин | **Requires fix**: не вызывать из фронта, формировать прямой URL в `AuthorTilePublicDto` (FR-001/002) | **P1** |
| 13 | `/api/public/news` | GET | `PublicNewsController.kt:38-57` | `News.loadPublished` (1 SELECT, лимит), затем `doRegisterEvent` | ДА | 60 SELECT/мин + 60 INSERT/мин | OK | P3 |
| 14 | `/api/public/news/since` | GET | `PublicNewsController.kt:59-100` | Для анонимов — пустой массив; для залогиненных — `News.loadSince` | ДА (если залогинен) | 30 SELECT/мин + 30 INSERT/мин (только залогиненные) | OK (уже пофикшено в Pass 52) | P3 |
| 15 | `/api/public/account/profile` | GET/POST | `PublicAccountController.kt:27-44` | SELECT/UPDATE `tbl_site_users` | НЕТ (но `doRegisterEvent` через фильтр?) | 30 SELECT/мин | OK (редкий endpoint) | P3 |
| 16 | `/api/public/account/change-password` | POST | `PublicAccountController.kt:46-109` | UPDATE `tbl_site_users` | НЕТ | 1 запрос на пользователя в час | OK | P3 |
| 17 | `/api/public/account/chat/unreadcount` | GET | `PublicChatController.kt:102-108` | `SiteChatMessage.countUnreadForUser` (1 SELECT), **НО для non-premium short-circuit возвращает 0 без DB** | НЕТ (короткий путь, без INSERT) | 60 SELECT/мин × N премиум юзеров | OK (уже оптимизировано — non-premium не делают SELECT) | P2 |
| 18 | `/api/public/account/chat/messages` | GET | `PublicChatController.kt:38-43` | Чат — `SELECT` с пагинацией | НЕТ | 30 запросов/мин | OK (только для премиум, которые сами открывают чат) | P3 |
| 19 | `/api/public/account/chat/send` | POST | `PublicChatController.kt:73-97` | INSERT в `tbl_site_chat_messages` | НЕТ (это доменные данные) | 5 запросов/мин на юзера | OK | P3 |
| 20 | `/api/public/account/editor/tasks` | GET | `PublicSongEditorController.kt:90-124` | SELECT заданий редактора | НЕТ (редкий) | 10 запросов/мин | OK | P3 |
| 21 | `/api/public/account/editor/tasks/{id}` | GET | `PublicSongEditorController.kt:126-181` | SELECT задания + чёрновика | НЕТ | 10 запросов/мин | OK | P3 |
| 22 | `/api/public/account/editor/tasks/{id}/save` | POST | `PublicSongEditorController.kt:183-222` | UPDATE черновика | НЕТ | 5 запросов/мин | OK | P3 |
| 23 | `/api/public/account/editor/tasks/{id}/submit` | POST | `PublicSongEditorController.kt:224-247` | UPDATE статус | НЕТ | 1 запрос/задание | OK | P3 |
| 24 | `/api/public/account/editor/tasks/{id}/recall` | POST | `PublicSongEditorController.kt:249-276` | UPDATE статус | НЕТ | 1 запрос/задание | OK | P3 |
| 25 | `/api/public/account/editor/tasks/{id}/refuse` | POST | `PublicSongEditorController.kt:278-299` | UPDATE статус | НЕТ | 1 запрос/задание | OK | P3 |
| 26 | `/api/public/account/editor/tasks/{id}/delete` | POST | `PublicSongEditorController.kt:301-328` | UPDATE статус | НЕТ | 1 запрос/задание | OK | P3 |
| 27 | `/api/public/account/editor/tasks/delete-approved` | POST | `PublicSongEditorController.kt:330-350` | DELETE | НЕТ | 1 запрос/день | OK | P3 |
| 28 | `/api/public/player/{id}/access` | GET | `PublicPlayerController.kt:146-...` | Проверка доступа к плееру | НЕТ | 30 запросов/мин | OK | P3 |
| 29 | `/api/public/player/{id}/readiness` | POST | `PublicPlayerController.kt:228-251` | Батч-проверка готовности стемов (по `subscription` + `stemsReady`) | НЕТ (но тяжёлый — 2 HEAD + 1 SELECT на песню) | 30 запросов/мин × 20 песен = 600 HEAD/мин | OK (батч — хорошая идея) | P3 |
| 30 | `/api/public/player/{id}/fileminus.mp3` | GET | `PublicPlayerController.kt:330-339` | **Проксирует байты стема через nginx→MinIO с token-проверкой** | НЕТ | 1 стрим на песню | OK (security: token, не 302-редирект) | P3 |
| 31 | `/api/public/player/{id}/filevoice.mp3` | GET | `PublicPlayerController.kt:341-350` | Аналогично | НЕТ | 1 стрим | OK | P3 |
| 32 | `/api/public/player/{id}/filebass.mp3` | GET | `PublicPlayerController.kt:352-361` | Аналогично | НЕТ | 1 стрим | OK | P3 |
| 33 | `/api/public/player/{id}/filedrums.mp3` | GET | `PublicPlayerController.kt:363-372` | Аналогично | НЕТ | 1 стрим | OK | P3 |
| 34 | `/api/public/player/{id}/playerdata` | GET | `PublicPlayerController.kt:374-478` | 4 HEAD-проверки в MinIO + 1 SELECT для song + markers | НЕТ | 30 запросов/мин | OK | P3 |
| 35 | `/api/public/player/{id}/playerfile` | GET | `PublicPlayerController.kt:480-...` | Прокси файла песни | НЕТ | 1 запрос | OK | P3 |
| 36 | `/api/public/share/{songId}/create` | POST | `PublicShareController.kt:42-93` | INSERT в `tbl_song_share_links` | НЕТ (домен) | 1 запрос/сессию | OK | P3 |
| 37 | `/api/public/share/mine/{songId}` | GET | `PublicShareController.kt:95-122` | SELECT | НЕТ | 1 запрос/сессию | OK | P3 |
| 38 | `/api/public/share/mine/{songId}/revoke` | POST | `PublicShareController.kt:124-133` | UPDATE | НЕТ | 1 запрос | OK | P3 |
| 39 | `/api/public/share/claim` | POST | `PublicShareController.kt:135-192` | SELECT + INSERT/UPDATE (атомарная транзакция) | НЕТ (домен) | 1 запрос | OK (см. spec 167) | P3 |
| 40 | `/api/public/share/heartbeat` | POST | `PublicShareController.kt:194-211` | UPDATE lease | **НЕТ** (не вызывает `doRegisterEvent`) | 25 сек × 100 юзеров × 1 вкладка = 240 UPDATE/мин | OK (FR-009 — уже выполнено) | OK |
| 41 | `/api/public/share/release` | POST | `PublicShareController.kt:216-235` | UPDATE (release lease) | НЕТ | 1 запрос | OK | P3 |
| 42 | `/api/public/share/debug` | POST | `PublicShareController.kt:237-247` | Debug — атомарная диагностика | НЕТ | Ручной | OK | P3 |
| 43 | `/api/public/auth/me` | GET | (useAuth.fetchMe) | SELECT `tbl_site_users` по токену | НЕТ (короткий SELECT) | 1 запрос / 5 мин × 100 юзеров = 20 запросов/мин | OK (уже module-level dedup в `useAuth.js:55-67`) | P3 |

### A.2. Endpoints `MainController.kt` (legacy + sink INSERT'ов)

| # | Endpoint | Метод | Файл:line | Что делает | INSERT в `tbl_events`? | Оценка нагрузки | Verdict | Приоритет |
|---|---|---|---|---|---|---|---|---|
| 44 | `/` (main page) | GET | `MainController.kt:50-87` | Thymeleaf-рендер главной (5 SELECT COUNT + 1 SELECT news + `doRegisterEvent`) | ДА (CALL_REST, MAIN) | 100 запросов/мин | OK (Thymeleaf устарел, не основной путь) | P3 |
| 45 | `/zakroma` (Thymeleaf) | GET | `MainController.kt:89-117` | Thymeleaf-рендер Закромов + `doRegisterEvent` | ДА | Редкий (legacy) | OK (deprecated) | P3 |
| 46 | `/registerevent` | POST | `MainController.kt:121-282` | **Главный sink INSERT'ов** — принимает event и пишет в `tbl_events` | ДА (для всех eventType) | 300-500 INSERT/мин при нормальной нагрузке | **Requires fix**: sampling + dedup (FR-006/007) | **P1** |
| 47 | `/changerecords` | POST | `MainController.kt:285-...` | Legacy — изменение records | НЕТ | Редкий | OK | P3 |
| 48 | `/filter` | GET | `MainController.kt:378-...` | Админский фильтр | НЕТ | Только админы | OK | P3 |
| 49 | `/song` | GET | `MainController.kt:427-...` | Админский просмотр песни | НЕТ | Только админы | OK | P3 |
| 50 | `/statbysong` | GET | `MainController.kt:469-...` | Админский стат | НЕТ | Только админы | OK | P3 |
| 51 | `/webevents` | GET | `MainController.kt:481-...` | Админский просмотр событий | НЕТ (это SELECT для админа) | Только админы | OK | P3 |
| 52 | `/testpage/{id}` | GET | `MainController.kt:494-...` | Тестовая страница | НЕТ | Редкий | OK | P3 |

**Итого по таблице A**: 5 эндпоинтов P1 (требуют фикса), 47 OK.

**P1-источники (требуют фикса в этой фиче)**:
1. `#3 /api/public/authors-tiles` → смена `AuthorTilePublicDto.authorPictureUrl` на прямой URL `/minio/...` (FR-002).
2. `#6 /api/public/zakroma/stream/metrics` → sampling (FR-006/007).
3. `#9 /api/public/events` → sampling (FR-006/007).
4. `#10 /api/public/song-picture/{id}` → rate-limit 60/мин (FR-010).
5. `#11 /api/public/song-vk-image/{id}` → rate-limit 60/мин (FR-010).
6. `#12 /api/public/picture` → не вызывать из фронта (FR-001), оставить для legacy.
7. `#46 /registerevent` → sampling + dedup (FR-006/007).

Остальные P1 покрываются FR-003/004 (nginx cache headers), FR-008 (server-side polling cache), FR-011 (events retention), FR-012 (logging), FR-013 (debug endpoint).

---

## Таблица B: `@Scheduled` задачи (FR-015)

| # | Класс.метод | Файл:line | Cron/FixedDelay | Что делает | SQL нагрузка | Verdict | Приоритет |
|---|---|---|---|---|---|---|---|
| 1 | `StatsCacheScheduler.refreshCache` | `karaoke-web/.../StatsCacheScheduler.kt:55-68` | `0 0 * * * *` (час) + `fixedRate = 60_000` | Обновляет in-memory `AtomicInteger` для `/api/public/stats` (5 SELECT COUNT) | 5 SELECT/час + 5 SELECT/мин = 305/час | OK (уже оптимизировано) | P3 |
| 2 | `SubscriptionRenewalScheduler.tick` | `karaoke-web/.../SubscriptionRenewalScheduler.kt:49-...` | `cron = "0 0 3 * * *"` | Продление подписок (1 раз в день в 3 AM) | 1 SELECT + N UPDATE | OK (днём, не в пиковые часы) | P3 |
| 3 | `StemJobTempCleanupScheduler.tick` | `karaoke-web/.../StemJobTempCleanupScheduler.kt:24-...` | `30 * 60_000L` | Чистит temp-файлы stemjobs | DELETE FROM local FS | OK | P3 |
| 4 | `ShareLinkSweeper.tick` | `karaoke-web/.../ShareLinkSweeper.kt:28-...` | `${share.sweep-interval-seconds:60}000` | Чистит истёкшие share-ссылки | 1 SELECT + N DELETE | OK | P3 |
| 5 | `SongReleaseAnnouncementScheduler.tick` | `karaoke-web/.../SongReleaseAnnouncementScheduler.kt:33-...` | `5 * 60_000L` | Публикует анонсы готовых песен | 1 SELECT + N INSERT | OK | P3 |
| 6 | `SseNotificationService.sendPending` | `karaoke-app/.../SseNotificationService.kt:165-...` | `fixedRate = 15_000` | Отправляет SSE-события клиентам | 1 SELECT | OK (только админка) | P3 |
| 7 | `StemJobPollScheduler.pollWaiting` | `karaoke-app/.../StemJobPollScheduler.kt:41-...` | `45_000L` | Polls `karaoke-web` на WAITING stemjobs | 1 SELECT + N HTTP | OK (только админка) | P3 |
| 8 | `StemJobPollScheduler.cleanup` | `karaoke-app/.../StemJobPollScheduler.kt:192-...` | `5 * 60_000L` | Cleanup expired stemjobs | 1 SELECT + N HTTP | OK | P3 |
| 9 | `VkAutoPublishScheduler.tick` | `karaoke-app/.../VkAutoPublishScheduler.kt:46-...` | `60_000L` | Автопубликация в VK | 2 SELECT + N HTTP | OK | P3 |
| 10 | `PremiumAutoPublishScheduler.tick` | `karaoke-app/.../PremiumAutoPublishScheduler.kt:70-...` | `30_000L` | Автопубликация премиум | 2 SELECT + N HTTP | OK | P3 |
| 11 | `TelegramAutoPublishScheduler.tick` | `karaoke-app/.../TelegramAutoPublishScheduler.kt:53-...` | `60_000L` | Автопубликация в Telegram | 2 SELECT + N HTTP | OK | P3 |
| 12 | `VkIdTokenRefreshScheduler.tick` | `karaoke-app/.../VkIdTokenRefreshScheduler.kt:44-...` | `cron = "0 0 * * * *"` (час) | Обновление VK токена | 1 HTTP | OK | P3 |
| 13 | `SponsrSyncScheduler.run` | `karaoke-app/.../SponsrSyncScheduler.kt:25-...` | `12 * 3600_000L` (12 часов) | Sync подписчиков Sponsr | N HTTP (внешний) | OK | P3 |
| 14 | `MonitoringService.tick` | `karaoke-app/.../monitor/MonitoringService.kt:40-...` | `60_000L` | Мониторинг процессов | 1 SELECT | OK | P3 |

**Итого по таблице B**: 0 P1, 0 P2, 14 OK. Все scheduled-задачи работают на низком cron/fixedDelay, не пишут в `tbl_events` (не нагружают БД).

**Вывод**: scheduled-задачи НЕ являются источниками нагрузки. Это контрольная проверка — если бы они были, добавили бы в P1.

---

## Решения по узким местам (research decisions)

### Решение D-1: Какой URL формировать в `AuthorTilePublicDto.authorPictureUrl`

**Текущее**: `/api/public/picture?file=<encoded>`, что влечёт 302-редирект через Spring-контроллер.

**Альтернативы**:
- A) Прямой `/minio/karaoke/<encoded path>` — nginx сам проксирует в MinIO, Spring-контроллер не задействован. Требует nginx-конфига с `Cache-Control` (FR-003).
- B) `/api/public/static-author-preview/<name>` — отдельный Spring-контроллер с cache + ETag + кешем в памяти. Сложнее, но контроль остаётся на бэке.
- C) Подписать URL MinIO с TTL (presigned URL). Самый быстрый путь (CDN), но требует переработки nginx → MinIO.

**Решение**: **A — прямой `/minio/karaoke/...` URL** (FR-002). Это:
- Убирает Spring-контроллер из пути загрузки каждой картинки (главная цель).
- nginx уже имеет `/minio/` location с proxy_pass (см. `deploy/80to8897`).
- Cache-Control через nginx (FR-003) даёт HTTP-кеширование в браузере.
- Сохраняет `/api/public/picture` как 302-redirect для legacy (FR-001 обратная совместимость).

**Обоснование**: самое простое и масштабируемое. Spring-контроллер не может масштабироваться на 200+ запросов/сек с одной страницы — nginx проксирует быстрее и не держит Tomcat-тред.

### Решение D-2: Где хранить in-memory cache для polling-эндпоинтов

**Альтернативы**:
- A) Spring `@Cacheable` + Caffeine (внешняя зависимость).
- B) ConcurrentHashMap в `MainController` (static поле).
- C) ConcurrentHashMap в новом бине `PollingCache` (Spring Component).
- D) Redis (общий для нескольких instances).

**Решение**: **C — отдельный Spring Component `PollingCache`** (per-instance). 
- A — добавляет внешнюю зависимость, требует Spring `@EnableCaching`.
- B — static поле хуже тестируется, но проще.
- C — выбираем за тестируемость + переиспользование между контроллерами (news/since, chat/unreadcount, share/heartbeat).
- D — overkill при 1 instance karaoke-web, отложить на момент масштабирования.

**TTL**: 30 сек (как в FR-008). Меньше — не сгладит polling × N пользователей. Больше — данные будут устаревать (новое сообщение в чате появится с задержкой > 30 сек).

**Ловушка**: TTL 30 сек может раздражать пользователей, если кто-то отправил сообщение в чат и 30 сек не видит бейдж «новое сообщение». Для чата: TTL можно сделать 5-10 сек. Для news — 30-60 сек. **Уточнение**: см. open decisions в plan.md.

### Решение D-3: Структура ключа дедупа в `doRegisterEvent`

**Текущее**: каждый `doRegisterEvent` пишет INSERT.

**Альтернативы** для ключа:
- A) `restName` (только).
- B) `restName + canonical(parameters)`.
- C) `restName + canonical(parameters) + anonId-or-userId` (решено в Q2 clarification → B).

**Решение**: **C** (clarified 2026-08-14). Per-user scope — разные пользователи с одинаковыми действиями пишутся отдельно.

**Канонизация parameters**: `parameters.toString()` от `Map<*, *>` уже даёт детерминированную строку (если ключи в одном порядке). Нужно проверить, что `kotlin.collections.MutableMap.toString()` сортирует ключи — иначе `{"a": 1, "b": 2}` vs `{"b": 2, "a": 1}` даст разные ключи дедупа. Альтернатива — `parameters.entries.sortedBy { it.key.toString() }.toString()`.

**Ловушка**: при больших `parameters` (например, JSON длинный) канонизация стоит CPU. Но реально параметры — это ID песни, имя автора, etc. — короткие.

### Решение D-4: Где хранить таблицу `tbl_events` для retention

**Альтернативы**:
- A) `@Scheduled` в `karaoke-web` (новый класс `EventsRetentionScheduler`).
- B) `@Scheduled` в `karaoke-app` (для консистентности с другими scheduled-задачами).
- C) PostgreSQL cron / pg_cron extension (не используется в проекте).

**Решение**: **A — `karaoke-web/.../EventsRetentionScheduler.kt`**. 
- A — `tbl_events` пишется из karaoke-web, там же логично и удалять.
- B — лишний сетевой round-trip (karaoke-app → Connection.remote()).
- C — требует extension, не стандартно.

**Cron**: `0 0 3 * * *` (3 AM по локальному времени, раз в день). Время выбрано так, чтобы не пересекаться с `SubscriptionRenewalScheduler` (3 AM) — если они будут вместе блокировать таблицы, можно подвинуть на 4 AM.

**Ловушка**: `tbl_events` — таблица без `recordhash`-триггера и без синка (см. `27_listening_history.sql:3-6`). Удаление строк НЕ влияет на sync. Но всё равно нужно проверить, что в `SyncRegistry` `tbl_events` НЕ зарегистрирована — иначе sync может попытаться прочитать удалённые строки.

### Решение D-5: Доступ к debug endpoint `/api/public/debug/db`

**Альтернативы**:
- A) IP allowlist (только `127.0.0.1` + IP сервера + VPN-подсеть).
- B) Basic auth через env `KARAOKE_WEB_DEBUG_BASIC_AUTH`.
- C) Только из admin-сети (определяется по IP через `ClientIpResolver`).

**Решение**: **A — IP allowlist через `ClientIpResolver`**. 
- Самый простой и явный. Не требует хранения credentials.
- На проде: `127.0.0.1`, IP сервера, IP админа (см. `KaraokeProperties.getString("debugEndpointAllowedIps")`).
- Basic auth был бы лишним — IP allowlist и так защищает.

**Ловушка**: при VPN подключении IP клиента другой. Админ подключается через VPN → IP allowlist должен включать VPN-подсеть. Конкретный IP/подсеть выбирается пользователем при деплое.

### Решение D-6: HTTP-кеш для `AuthorTiles.vue` — дедупликация запроса `/api/public/authors-tiles`

**Альтернативы**:
- A) Vuex state `lastLoadedTilesAt` + дедуп в `loadAuthorTiles` (FR-014).
- B) HTTP-кэш через nginx (`proxy_cache` для `/api/public/authors-tiles`).
- C) Browser-level cache через `Cache-Control` от backend.

**Решение**: **A — Vuex state** (FR-014). 
- A — самый явный: пользовательский код явно проверяет «не прошло ли 30 сек?».
- B — требует настройки nginx `proxy_cache` (сложнее, но универсальнее).
- C — браузерный кэш через Cache-Control — может сломать логику обновления тайлов.

**Ловушка**: при возврате на страницу Закромов через 5 минут Vuex-state пустой (не сохраняется между сессиями), но 30-секундный TTL защищает только внутри одной SPA-сессии. **Не** защищает от F5 (полная перезагрузка → state пустой). Это OK, потому что F5 — редкое событие.

### Решение D-7: Какой TTL для server-side polling cache (FR-008)

**Альтернативы**:
- A) 30 сек (FR-008 как написано).
- B) 60 сек (более агрессивно).
- C) Разные TTL для разных эндпоинтов: news=60 сек, chat=10 сек, share/heartbeat=15 сек.

**Решение**: **C — разные TTL для разных эндпоинтов** (best UX):
- `/api/public/news/since` — 60 сек (новости не меняются каждую секунду).
- `/api/public/account/chat/unreadcount` — 10 сек (важно для UX, чтобы бейдж не «лагал»).
- `/api/public/share/heartbeat` — 15 сек (между heartbeat-ами 25 сек, кэш на 15 сек = каждый 2-й запрос no-op).
- `/api/public/auth/me` — 60 сек (fetchMe уже polling раз в 5 минут, кэш на 60 сек = страховка для одновременных вкладок).

**Обоснование**: разные TTL дают лучший UX без потери нагрузки.

### Решение D-8: Как тестировать инцидент «сайт недоступен»

**Альтернативы**:
- A) Нагрузочный тест wrk / curl с локального dev-pc.
- B) Post-hoc анализ debug-эндпоинта (FR-013).
- C) Production monitoring (не входит в эту фичу).

**Решение**: **A + B — оба**.
- A — для верификации, что фикс работает (SC-006).
- B — для диагностики будущих инцидентов.

**Wrk-команда** для теста (50 параллельных клиентов, 100 запросов):
```bash
wrk -t50 -c50 -d30s "https://sm-karaoke.ru/api/public/authors-tiles"
```

**Ловушка**: wrk не работает с редиректами. Использовать `--latency` для p95/p99.

---

## Технические решения вне scope spec.md (для plan.md)

### TR-1: Где разместить `SamplingFilter` / `DedupCache`

**Решение**: в `karaoke-web/.../services/`:
- `SamplingFilter.kt` — wrapper над `doRegisterEvent` с sampling логикой.
- `DedupCache.kt` — `ConcurrentHashMap<DedupKey, Long>` + lazy cleanup.
- `RateLimitInterceptor.kt` — Spring HandlerInterceptor для rate-limit на конкретные endpoints.
- `EventsRetentionScheduler.kt` — `@Scheduled` cron `0 0 3 * * *` для удаления старых строк `tbl_events`.
- `DebugDbController.kt` — новый контроллер `GET /api/public/debug/db` с IP allowlist.

**Обоснование**: в `services/` уже живут `StatsCacheScheduler`, `ShareLinkSweeper` и др. По convention — там.

### TR-2: KDoc / JSDoc для новых классов

Все новые публичные классы MUST иметь KDoc/JSDoc с `@see docs/features/site-traffic-resilience.md` (FR-020, Constitution FR-006). Это требование CI (см. `.github/workflows/lint.yml`).

### TR-3: Env-переменные для настройки

- `KARAOKE_WEB_EVENTS_SAMPLING_ANON=20` (default)
- `KARAOKE_WEB_EVENTS_SAMPLING_LOGGED=5` (default)
- `KARAOKE_WEB_EVENTS_SAMPLING_ADMIN=1` (default)
- `KARAOKE_WEB_EVENTS_DEDUP_TTL_SECONDS=30` (default)
- `KARAOKE_WEB_EVENTS_RETENTION_DAYS=7` (default)
- `KARAOKE_WEB_DEBUG_DB_ENABLED=true` (default)
- `KARAOKE_WEB_DEBUG_DB_ALLOWED_IPS=127.0.0.1,<admin-ip>` (default пусто — endpoint отключён)
- `KARAOKE_WEB_RATE_LIMIT_SONG_PICTURE_PER_MINUTE=60` (default)
- `KARAOKE_WEB_RATE_LIMIT_SONG_VK_IMAGE_PER_MINUTE=60` (default)

**Обоснование**: env-переменные через `KaraokeProperties.getString/getLong` (см. `KaraokeProperties.kt`), не Spring `@Value` (для консистентности с другими параметрами).

---

## Метрики успеха (mapping на SC)

| SC | Как проверить |
|---|---|
| SC-001 (0 запросов на `/api/public/picture` при `/zakroma`) | DevTools → Network на анонимном `/zakroma` cold load |
| SC-002 (0 запросов на warm cache за 24ч) | F5 в течение 24ч, проверить Transfer-Size = 0 |
| SC-003 (≤ 5 INSERT/мин от polling) | SQL: `SELECT count(*) FROM tbl_events WHERE event_type='CALL_REST' AND last_update > now() - interval '1 minute'` |
| SC-004 (p95 ≤ 200мс при 50 параллельных клиентах) | `wrk -t50 -c50 -d30s ... --latency` |
| SC-005 (стабильный размер tbl_events) | SQL: `SELECT pg_size_pretty(pg_total_relation_size('tbl_events'))` через день |
| SC-006 (нет 7-10 мин инцидентов при 100 пол. × 3 вкладки) | wrk в течение 30 минут, проверка 2xx на всех запросах |
| SC-007 (FCP ≤ 4 сек на 10Mbps/50ms RTT) | Chrome DevTools → Performance → Network throttling |
| SC-008 (429 при > 60 req/мин бота) | wrk с 1 IP на 100 req/мин на `/api/public/song-picture/{id}` |
| SC-009 (100% audit coverage) | `bash tools/check-audit-coverage.sh` (новый helper) |

---

## Известные риски

| Риск | Митигация |
|---|---|
| Дедуп через `ConcurrentHashMap` теряет состояние при restart karaoke-web | Допустимо — дедуп «мягкий», sampling — основная защита |
| Retention scheduler удалит строки, нужные для анализа инцидента | 7 дней — разумный компромисс; админ может поднять retention через env до анализа |
| nginx `expires 24h` закеширует 404 навсегда | FR-005: отдельно `Cache-Control: public, max-age=300` для 404 |
| `Math.random()` не thread-safe на JVM? | Thread-safe (java.util.Random используется внутри), но всё равно проверить, что sampling-решение не блокирует |
| IP allowlist для debug endpoint обходится через VPN | Конфигурируется пользователем при деплое; не блокирует, но делает инцидент заметным |
| `tbl_events` retention scheduler удаляет строки, на которые ссылается другая таблица | Нет FK constraint на `tbl_events` (проверено — это event log, не reference data) |

---

## Open decisions для plan.md

- **OD-1**: TTL для каждого polling endpoint — D-7 рекомендует разные TTL (news=60, chat=10, share=15, auth/me=60). Подтверждение в plan.md.
- **OD-2**: Куда вынести `loadAuthorTiles` dedup — в `zakroma.js` Vuex (FR-014) или в composable. Решается на этапе implementation.
- **OD-3**: Дебаунс на retry для сетевых ошибок при загрузке тайла — сейчас нет, фронт ретраит по `@error` через 30 сек. Это OK, но можно добавить в будущем.
- **OD-4**: Нужно ли логировать ВСЕ события от админов (rate=1/1), или есть какие-то из них которые тоже можно сэмплировать — нет, для админов всё пишется.

---

## Полный список контроллеров (для SC-009 coverage)

Эта секция дополняет Таблицу A — перечисляет ВСЕ файлы контроллеров в
`karaoke-web/.../controllers/*.kt`, имеющие хотя бы один `@GetMapping`/`@PostMapping`,
и ВСЕ сервисы с `@Scheduled` (для Таблицы B). Используется скриптом
`tools/check-audit-coverage.sh` для верификации 100% покрытия.

### Контроллеры с REST-эндпоинтами (FR-016, FR-017)

| Файл | Кол-во маппингов | Зона |
|------|------------------|------|
| `InternalStatsController.kt` | 2 | internal/admin |
| `InternalStemJobController.kt` | 3 | internal/admin |
| `MainController.kt` | 20 | legacy, в т.ч. `/registerevent` (sink INSERT'ов) |
| `PublicAccountController.kt` | 3 | `/api/public/account/*` |
| `PublicApiController.kt` | 12 | `/api/public/*` (основная публичная зона) |
| `PublicAuthController.kt` | 5 | `/api/public/auth/*` |
| `PublicCartController.kt` | 5 | `/api/public/cart/*` |
| `PublicChatController.kt` | 3 | `/api/public/account/chat/*` |
| `PublicHistoryController.kt` | 1 | `/api/public/history/*` |
| `PublicNewsController.kt` | 2 | `/api/public/news/*` |
| `PublicOgSongController.kt` | 1 | `/api/public/og/*` |
| `PublicPaymentController.kt` | 1 | `/api/public/payment/*` |
| `PublicPlayerController.kt` | 8 | `/api/public/player/*` (стемы, playerdata) |
| `PublicPlaylistController.kt` | 12 | `/api/public/playlists/*` |
| `PublicSettingsWebController.kt` | 3 | `/api/public/settings/*` |
| `PublicShareController.kt` | 7 | `/api/public/share/*` |
| `PublicSongEditorController.kt` | 8 | `/api/public/account/editor/*` |
| `PublicSongeditorController.kt` | 1 | `/api/public/songeditor/*` (legacy alias) |
| `PublicStemJobController.kt` | 4 | `/api/public/stemjob/*` |
| `PublicSubscriptionController.kt` | 5 | `/api/public/subscription/*` |
| `PublicTypographController.kt` | 1 | `/api/public/typograph/*` |
| `PublicVkAuthController.kt` | 2 | `/api/public/vk-auth/*` |
| `PublicVkIdAuthController.kt` | 2 | `/api/public/vk-id-auth/*` |
| `SiteShareLinksController.kt` | 3 | legacy site-share endpoints |

### Сервисы с `@Scheduled` (FR-015)

| Файл | Кол-во scheduled-методов |
|------|---------------------------|
| `ShareLinkSweeper.kt` | 2 |
| `SongReleaseAnnouncementScheduler.kt` | 1 |
| `StatsCacheScheduler.kt` | 2 |
| `StemJobTempCleanupScheduler.kt` | 1 |
| `SubscriptionRenewalScheduler.kt` | 1 |

---

## См. также

- [spec.md](./spec.md) — функциональные требования и success criteria.
- [plan.md](./plan.md) — имплементационный план (создаётся следующим).
- [data-model.md](./data-model.md) — описание новых сущностей.
- [contracts/](./contracts/) — API-контракты новых и модифицированных endpoints.
- [quickstart.md](./quickstart.md) — ручные сценарии проверки.
- [`docs/features/stats.md`](../../docs/features/stats.md) — существующий per-feature документ (контекст для in-memory кэша).
- [`docs/architecture-notes.md`](../../docs/architecture-notes.md) — Pass 50-60 (news/since fix, stats leak, SEO HTML).
- [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md) — NON-NEGOTIABLE принципы.
