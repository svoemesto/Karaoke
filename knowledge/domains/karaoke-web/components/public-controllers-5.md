# Public controllers: Og/History/News/Chat/Typograph (мелкие)

> **Домен**: [karaoke-web](../domain.md)
> **Компонента**: обзор 5 мелких Public-контроллеров.


## Ответственность | Responsibility


обзор 5 мелких Public-контроллеров.

## Файлы

| Store | Файл | Строк | Endpoints |
|---|---|---|---|
| `PublicOgSongController` | `webvue3/.../PublicOgSongController.kt` | 539 | 1 |
| `PublicHistoryController` | `webvue3/.../PublicHistoryController.kt` | 35 | 1 |
| `PublicNewsController` | `webvue3/.../PublicNewsController.kt` | 93 | 2 |
| `PublicChatController` | `webvue3/.../PublicChatController.kt` | 119 | 3 |
| `PublicTypographController` | `webvue3/.../PublicTypographController.kt` | 52 | 1 |

[WARN] Путь `webvue3/...` не подтверждён: все пять файлов лежат в
`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/`
(в репозитории по одному экземпляру каждого). Размеры строк совпадают
(`wc -l`: 539 / 35 / 93 / 119 / 52).

## Интерфейсы и Контракты | Interfaces and Contracts

Пять независимых контроллеров; полные URL — с учётом class-level
`@RequestMapping`, где он есть.

### `PublicOgSongController` (1 endpoint, 539 строк — **огромный!**)

`/api/public/og/song` (GET) — **Open Graph** теги для шеринга
песни в соцсетях. Генерирует OG-meta (title, image, description)
динамически.

### `PublicHistoryController` (1 endpoint, 35 строк)

`/api/public/history` — POST для регистрации прослушивания (см.
[ListeningHistory](../../catalog/components/entities-catalog.md#listeninghistory)).

[WARN] Сверка с кодом: `@RequestMapping("/api/public/account")` +
`@GetMapping("/history")` (стр. 29) → фактический URL
`GET /api/public/account/history`; POST-маппинга в файле нет.

### `PublicNewsController` (2 endpoints, 93 строки)

`/api/public/news/*`:
- `GET /since` — **polling** непрочитанных (TTL=60s, см.
  [composable-news-unread.md](../../../system/frontend/composable-news-unread.md)).
- `GET /{id}` — одна новость.

[WARN] `GET /{id}` в `PublicNewsController.kt` не найден: маппинги —
`@GetMapping("")` (стр. 45, лента) и `@GetMapping("/since")` (стр. 66).

### `PublicChatController` (3 endpoints, 119 строк)

`/api/public/account/chat/*` — **требует** auth (через
`SiteAuthInterceptor`):
- `GET /threads` — список тредов.
- `GET /messages/{threadId}` — сообщения.
- `POST /send` — отправить.

См. [unreadchatmessagescheck](../../monitoring/components/monitor-checks-detailed.md).

[WARN] Сверка с кодом: `@GetMapping("/messages")` (стр. 53),
`@PostMapping("/send")` (83), `@GetMapping("/unreadcount")` (109);
`/threads` и `/messages/{threadId}` в файле отсутствуют.

### `PublicTypographController` (1 endpoint, 52 строки)

`POST /api/replacesymbolsinsong` — утилита для типографики (см.
[TypographUtils.kt](../../../system/utilities.md)). Используется
`PublicTypographController` для нормализации текста на сервере.

Сверено с кодом: `@PostMapping("/api/replacesymbolsinsong")` (стр. 47) с
`@ResponseBody`, параметр `txt` (required); class-level `@RequestMapping`
нет.

## Логика и Алгоритмы | Logic and Algorithms

Потоки сверены с
`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/`.

**`PublicOgSongController` (`GET /api/public/og/song`)** — рендер
SEO-HTML для ботов:

1. `produces = TEXT_HTML + "; charset=UTF-8"`, параметры `id: Long?` и
   заголовок `User-Agent`.
2. `id == null || id <= 0` → HTML с ошибкой «Не указан id песни (добавьте
   `?id=NNN`)».
3. `Song.loadFromDbById(...)` вернул `null` → HTML «Песня не найдена:
   `id=$id`».
4. Иначе `buildSeoHtmlForBots(song)`: canonical
   `https://sm-karaoke.ru/song?id=<id>`, title `«<songName> — <author>»
   — Караоке на sm-karaoke.ru`, описание (`buildDescription`), JSON-LD
   (`buildJsonLd`), мета-секция (`buildMetaSection`), видимый
   semantic-контент (`buildListenSection`), картинка альбома
   (`buildAlbumImageUrl`), длительность (`formatDurationMs`).
5. Краевые случаи (FR-006): SKIP-тег при `isSkipped(song)`, отсутствие
   обложки, `truncateIfTooLarge(html)` как страховка от слишком большого
   ответа; всё пользовательское содержимое проходит через `escape` /
   `escapeJsonLd`.

**`PublicHistoryController` (`GET /api/public/account/history`)**:

1. Весь класс за `SiteAuthInterceptor`, поэтому `currentUser(request)`
   (атрибут `SITE_USER_ATTR`) уже авторизован и не забанен.
2. `ListeningHistory.getForUser(siteUserId = user.id, database = WORKING_DATABASE)`
   → маппинг в `HistoryEntryDto` → ответ `{items: [...]}`.
3. Ссылка на контракт — `specs/009-listening-history/contracts/history-api.md`.

**`PublicNewsController` (`/api/public/news`)**:

1. `GET ""` (лента) — `page` (default 0) и `size` (default 20),
   `offset = page * size`; `News.loadPublished(db, limit = size, offset = offset)`
   и `News.countPublished(db)`; ответ `{items, total, hasMore: offset + items.size < total}`.
   Отдаются только опубликованные новости (`publish_at` наступил), свежие
   сверху; постраничность введена спекой `090-news-pagination` из-за
   19000+ строк в `tbl_news`.
2. `GET /since` — polling-бейдж: `id` (default 0) — `lastSeenId` из
   localStorage анонима, для залогиненного — последний просмотренный id.
   `PollingCache` с TTL 60 с; ключ `news_since:anon` для анонима или
   `news_since:user:<userId>:since:<id>` для залогиненного.
3. Аноним получает `{count: 0, items: []}` без похода в БД (бейдж не
   показывается).
4. Залогиненный получает `News.loadPublishedSince(db, id, limit = 50)` →
   `{count, items}`; лимит 50 — страховка от выдачи всего архива при первом
   входе за годы. Причина ограничения: запрос `/since?id=0` отдавал
   3.5+ MB при ~19k строках, а массовый анонимный polling (45 с × вкладки ×
   пользователи) вычерпывал пул HikariCP (10 коннектов) и давал каскадные
   зависания сайта (Pass 52, 2026-08-13).

**`PublicChatController` (`/api/public/account/chat`)** — чат целиком
премиум-функция:

1. Общий гейт: `user.isEffectivePremium`; иначе `403 {error: premium_required}`
   (истёкший премиум теряет доступ ко всему разделу, не только к отправке).
2. `GET /messages` — курсорная пагинация по `id` (тред append-only):
   `beforeId` подгружает историю вверх, `afterId` — новые сообщения при
   поллинге, без курсоров — последние `SiteChatMessage.DEFAULT_PAGE_SIZE`.
   Ответ `{messages, total}`; после успешного гейта вызывается
   `SiteChatMessage.markThreadReadByUser(user.id, ...)` — отметка прочтения
   только для реально прошедшего гейт запроса.
3. `POST /send` — параметр `body` (request param): пустой →
   `400 {error: empty_body}`; иначе `SiteChatMessage.createNew(siteUserId =
   user.id, isFromAuthor = false, body = body, ...)` и DTO созданного
   сообщения.
4. `GET /unreadcount` — не-премиум получает `{count: 0}` без обращения к
   БД (нет доступа к чату — нет и сигнала); для премиума
   `PollingCache` с TTL 10 с (FR-008), ключ `chat_unread:user:<userId>`,
   значение — `SiteChatMessage.countUnreadForUser`.

**`PublicTypographController` (`POST /api/replacesymbolsinsong`)**:

1. Принимает обязательный `txt`, возвращает исправленный текст сырой
   строкой (**не JSON**), поэтому стоит `@ResponseBody`.
2. Вызывает fully-qualified top-level-функцию
   `com.svoemesto.karaokeweb.replaceSymbolsInSong(txt)` (PR #207 — без
   полного имени была бы рекурсия в собственный метод контроллера).
3. Набор правил: Ё-словарь, кавычки-«ёлочки», нормализация тире/дефисов,
   пробелы вокруг запятой/двоеточия, авто-переносы строк по заглавным
   буквам, удаление строк из одних «аккордовых» символов, транслитерация
   похожих латинских букв в кириллицу при наличии русских букв в тексте.
4. Endpoint намеренно дублирует рабочий маршрут `karaoke-app`
   (`webvue3` → nginx → `karaoke-app:8898`) — `karaoke-web` отрезан от
   `Constants.kt` `karaoke-app`, чей class init падает с
   `NoClassDefFoundError` (spec `155-editor-typograph-button`).

## Зависимости | Dependencies

- [public-controllers.md](public-controllers.md) — обзор всех 19.
- [composable-news-unread.md](../../../system/frontend/composable-news-unread.md) — consumer.

## Changelog

- **Pass 481** (2026-09-27, spec `481-knowledge-domain-karaoke-web`): секции
  по контроллерам перенесены под «Интерфейсы и Контракты»; добавлена
  «Логика и Алгоритмы» с проверенными по коду потоками OG-рендера,
  истории, пагинации/`since` новостей, премиум-чата (курсоры, TTL 10 с) и
  типографа; [WARN]-пометки про путь `webvue3/...` и расхождения в
  маппингах History/News/Chat. Автор: agent (Karaoke).
- **Pass 481-485** (2026-09-09): Initial. Автор: agent (Karaoke).
