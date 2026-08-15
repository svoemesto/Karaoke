# Автопубликация новостей в группу ВКонтакте

> **Status**: active (specs/121-vk-news-auto-publish, specs/130-vk-preview-generation, specs/138-vk-photo-preview-attachment)
> **Feature Key**: vk-news-auto-publish
> **Last Updated**: 2026-08-05 (добавлена ссылка на vk-id-auth.md, секция Implicit Flow помечена как deprecated)

## Что делает

Бот автоматически публикует посты в группу ВКонтакте двух типов:

- **`air`** — авто, при выходе песни в эфир (по `tbl_news.category='air'`
  и `publish_at <= now()` в LOCAL-БД после sync LOCAL↔SERVER).
- **`premium`** — ручной, кнопка «Опубликовать во ВК (premium)» в карточке
  песни в `webvue3` (для премиум-выпуска).

К посту прикрепляется демо-MP4 песни (через VK API `video.save` + `wall.post`
`attachments=video...`). Каждый тип — свой шаблон текста с плейсхолдерами
(`vkTemplateAir` / `vkTemplatePremium`), редактируемый через спец. редактор
в `webvue3`.

## Зачем

Снять с администратора рутину «скопировать новость → открыть ВК → создать
пост» для каждой вышедшей песни, и дать отдельный шаблон для премиум-выпуска
(отличающийся формулировкой от `air`).

## Настройка бота с нуля

> Все ключи хранятся в `/sm-karaoke/system/Karaoke.properties` (admin-машина,
> base64-файл, в git НЕ лежит). Менять можно через Properties UI в `webvue3`
> или через редактор шаблонов ВК.

### Шаг 1. Создать группу ВКонтакте и Community token

1. Создать публичную группу (`vk.com/<group_short_name>`) или использовать
   существующую.
2. В настройках группы: «Управление → Работа с API → Ключи доступа →
   Создать новый ключ» с правами `wall`, `video`, `photos`.
3. Скопировать Community access token.

### Шаг 2. Заполнить KaraokeProperties

- `vkAutoPublishEnabled = true` — включить бот (только для `air`-автопостинга;
  endpoint `/api/song/publishToVkNow` работает независимо).
- `vkGroupId = <ID группы без минуса>` (например, `123456`).
- `vkAccessToken = <Community access token>` (секрет, НЕ в git).
- `vkApiVersion = 5.199` (актуальная стабильная).
- `vkProxyUrl` — если admin-машина за firewall (по образцу
  `telegramProxyUrl`); иначе пусто.
- `vkAutoPublishMaxVideoSizeMb = 50` (лимит размера демо-MP4, МБ).
- `vkAutoPublishRateLimitPerHour = 3` (max постов в час, FR-006).

### Шаг 3. Настроить шаблоны (опционально)

Через редактор шаблонов ВК в `webvue3` (раздел «Шаблоны ВК»). Если оставить
пустыми — бот использует дефолты из `VkTemplateService` (см. research.md §6).

### Шаг 4. Перезапустить karaoke-app

Только пользователь (см. AGENTS.md «Ограничения агента»; на `dev-pc`/`dev` —
агент может).

## Как работает

**Триггер `air`** — плановый `@Scheduled`-тик каждые 60 сек
(`VkAutoPublishScheduler`, по образцу `TelegramAutoPublishScheduler`).
Бот сканирует `tbl_news` на `air`-новости с `publish_at <= now()`, для
каждой определяет связанную песню (`News.song_id` или разбор `News.link`
`/song?id=<id>`), и если у песни `Song.idVk` пуст — публикует.

**Триггер `premium`** — ручной, кнопка в карточке песни в `webvue3` →
`POST /api/song/publishToVkNow?id=<songId>&type=premium` →
`VkAutoPublishService.publishToVk(song, PublicationType.PREMIUM)`.

**Поток публикации** (общий для обоих типов):
1. FR-008: если `Song.idVk` не пуст → skip (идемпотентность общая).
2. FR-022: если `!song.isContentReady` → skip (ждать готовности).
3. FR-020: если демо-MP4 отсутствует или превышает лимит → `KaraokeProcess`
   `RENDER_MP4_DEMO`, публикация после завершения (FR-003 сц. 2/3).
4. FR-023: текст поста по шаблону (`vkTemplateAir` / `vkTemplatePremium`)
   через `VkTemplateService.render(template, song, news?)` с заменой
   плейсхолдеров.
5. **specs/130 — прогрев превью**: под локом по `song.id` синхронный GET
   `{vkPreviewWarmupUrl}{songId}` → HTTP 200 + валидный PNG (1200×630, см.
   [Превью через photos.saveWallPhoto](#превью-через-photossavewallphoto-specs138)).
   Успех означает, что `karaoke-web` уже записал `/tmp/vk_<id>.png` и VK-бот
   при первом обращении получит готовый файл. При ошибке — `SEND_FAILED` с
   префиксом `preview prewarm failed:`; `wall.post` НЕ вызывается.
6. **specs/138 — загрузка фото-превью**: `VkPhotoUploadClient.uploadCover`
   (см. отдельную секцию ниже) — `photos.getWallUploadServer` → POST multipart →
   `photos.saveWallPhoto` (user-token). При `error_code=27/15/5/29` — fallback
   на `docs.*` (`docs.getWallUploadServer` → POST → `docs.save`,
   community-token). При полном сбое обоих — деградация (пост без превью).
7. FR-019: `VkApiClient.sendPostWithVideo` — `video.save` → загрузка →
   `wall.post` с `attachments=photo<owner>_<id>,video<owner_id>_<video_id>`
   (или только `video...` если фото не загрузилось).
8. FR-004: при успехе — `song.fields[SongField.ID_VK] = postId`,
   `song.saveToDb()` (с диффом, recordhash-триггером, SSE).
9. FR-009: при сбое — 3 ретрая с backoff 30с→2мин→5мин; затем
   `SEND_FAILED` в `vkAutoPublishState`.

**Идемпотентность** — по `Song.idVk` (один пост на песню, независимо от
типа). Переопубликование — очистить `idVk` вручную, нажать нужную кнопку.
Бот не удаляет старый пост ВК (FR-010).

**Редкий случай** (FR-004a, FR-021) — ручная `air`-новость без `song_id`:
публикуется без видео, только текст; идемпотентность через ключ
`vkAutoPublishState` в `News.playerReadinessFlags` JSON-блобе (по образцу
`telegramAutoPublishState`).

## Инварианты / правила

- Бот работает ТОЛЬКО на admin-машине (`karaoke-app`), на проде не
  разворачивается (Constitution: «karaoke-app на проде не
  разворачивается вовсе»).
- Источник истины для `air` — `tbl_news` (категория `air`,
  `publish_at <= now()` в LOCAL-БД после sync LOCAL↔SERVER).
- Источник истины для `premium` — кнопка в карточке песни (нет связи
  с `tbl_news`).
- Идемпотентность — по `Song.idVk` (общая для обоих типов).
- `tbl_settings` → `tbl_songs` (колонка `id_vk` уже существует,
  участвует в sync, recordhash-триггер на месте — новых миграций НЕ
  требуется, Constitution Principle III).
- Секрет `vkAccessToken` — в `KaraokeProperties` (base64, в git НЕ
  попадает, Constitution Principle VII).
- Шаблоны `vkTemplateAir` / `vkTemplatePremium` — многострочные
  String в `KaraokeProperties`; редактор — отдельный UI в `webvue3`
  (`VkTemplatesEditor.vue`), не generic Properties UI.
- Неизвестные плейсхолдеры в шаблоне (например, `{nonexistent}`) —
  literal-текст (FR-023, не падаем, не заменяем).
- Готовность песни (FR-022) — статус ≥ 6 + флаги готовности плеера (то
  же условие, что `specs/113-telegram-demo-publish` FR-011,
  `specs/089-auto-news-song-release` FR-009).
- **specs/130: прогрев превью — обязательный шаг перед `wall.post`**
  (`vkPreviewWarmupEnabled=true` по умолчанию). Успех — HTTP 200 + ненулевой
  валидный PNG. 3xx без follow, 4xx (кроме 429), 5xx после retry, пустое
  тело, повреждённый PNG → `FAILED` → `SEND_FAILED` с префиксом
  `preview prewarm failed:`. Retry только transient-сетевых и 5xx (настройка
  `vkPreviewWarmupMaxAttempts`).
- **specs/130: process-local lock по `song.id`** — сериализует публикацию
  одной песни. Под локом перечитывается `Song.idVk` из БД (защита от
  stale-instance race `publishToVk` ↔ `onRenderCompleted`). Lock не в БД.
- **specs/130: атомарная запись web-кэша** — `PublicApiController.songVkImage`
  пишет PNG во временный файл и затем `Files.move` с `ATOMIC_MOVE`
  (fallback на `REPLACE_EXISTING`). Чтение проверяет PNG magic-signature
  (8 байт) — частично записанный или повреждённый кэш удаляется и
  перегенерируется.

## Прогрев превью (specs/130)

Гипотеза проверяется синхронным прогревом публичного изображения перед
`wall.post`: VK-бот при первом обращении к `karaoke-web/api/public/song-vk-image/{id}`
получает уже готовый PNG, без ожидания первой генерации (которая занимает
секунды из-за MinIO + сборки PNG).

### Поток прогрева

1. `VkAutoPublishService` под локом по `song.id` синхронно вызывает
   `VkPreviewWarmupClient.warmup(songId)`.
2. `VkPreviewWarmupClient` строит URL `{vkPreviewWarmupUrl}{songId}` и
   делает GET через `java.net.http.HttpClient` с `Redirect.NEVER`.
3. Успех = HTTP 200 + ненулевое тело + декодируется как PNG через
   `ImageIO.read`.
4. Ошибка (3xx, 4xx (кроме 429), 5xx после retry, тайм-аут, пустое тело,
   повреждённый PNG) → `SEND_FAILED` с `vkAutoPublishLastError`,
   начинающимся с `preview prewarm failed: ...`. `wall.post` НЕ вызывается.
5. Успех → текущий `VkApiClient.sendPostWithVideo` / `wallPost` (FR-019).
   Ошибка VK API после успешного прогрева — отдельная ошибка VK, не маскируется
   под ошибку превью.

### Настройки (`KaraokeProperties`)

| Ключ | Дефолт | Назначение |
|---|---|---|
| `vkPreviewWarmupEnabled` | `true` | Включатель нового шага. `false` — bypass, файл по-прежнему доступен VK-боту, но без синхронного ожидания готовности. |
| `vkPreviewWarmupUrl` | `https://sm-karaoke.ru/api/public/song-vk-image/` | Базовый URL endpoint'а (без токенов, в git не секрет). |
| `vkPreviewWarmupTimeoutMs` | `30000` | Тайм-аут одного запроса (мс). |
| `vkPreviewWarmupMaxAttempts` | `2` | Число попыток (retry только transient-сетевых ошибок и 5xx). |

### Ручная проверка (quickstart)

См. [`specs/130-vk-preview-generation/quickstart.md`](../../specs/130-vk-preview-generation/quickstart.md)
— 5 сценариев: отдельный прогрев, AIR/PREMIUM публикация, идемпотентность,
отказ при ошибке prewarm, отделение ошибки VK от ошибки изображения.

## Превью через photos.saveWallPhoto (specs/138)

> **Полная спецификация**: [`specs/138-vk-photo-preview-attachment/spec.md`](../../specs/138-vk-photo-preview-attachment/spec.md).
> **Контракты**: [`specs/138-vk-photo-preview-attachment/contracts/vk-photo-upload.md`](../../specs/138-vk-photo-preview-attachment/contracts/vk-photo-upload.md).
> **Quickstart**: [`specs/138-vk-photo-preview-attachment/quickstart.md`](../../specs/138-vk-photo-preview-attachment/quickstart.md).

### Проблема и решение

VK API `wall.post` при бот-публикации **не парсит URL** в тексте для генерации
сниппета (в отличие от ручной публикации через UI). Поэтому все ранее принятые
меры с Open Graph (nginx rewrite, прогрев PNG-кэша `specs/130`) работают
только для ручной публикации. Прикрепление фото через `attachments` —
**надёжное** решение: VK берёт фото из API-параметра, не парся URL.

`specs/138` добавляет новый шаг **между прогревом PNG и `video.save`+`wall.post`**:
бот загружает PNG-обложку песни как фото группы через `photos.getWallUploadServer`
→ POST multipart на `upload_url` → `photos.saveWallPhoto` (user-token со scope
`photos`, настраивается через VK ID, см. секцию
[Авторизация через VK ID](./vk-id-auth.md) и секцию
[User-token через Implicit Flow](#user-token-через-implicit-flow-для-air-публикации-с-видео)
для истории).
Полученный `photo<owner>_<id>` передаётся в `wall.post` через параметр
`attachments` **первым** — VK берёт первое прикрепление как превью поста.

При сбое `photos.*` (например, `error_code=27` «Group authorization failed»,
если user-token потерял scope) — fallback на `docs.*` через community-token
(право `docs` доступно сообществам по умолчанию). Прикрепление идёт как
`attachments=doc<owner>_<id>` — отображается как файл-документ, не сниппет.
При полном сбое обоих методов — **деградация**: пост создаётся без превью,
администратор видит предупреждение в `vkAutoPublishLastError` (префикс
`photo attach failed:`). Пост **не** проваливается полностью.

### Поток (полный)

```
[прогрев PNG из specs/130]  →  PNG 1200×630 в памяти (pngBytes)
                                 │
                                 ▼
              VkPhotoUploadClient.uploadCover()
                                 │
            ┌────────────────────┼─────────────────────────┐
            │                    │                         │
   photos.getWallUploadServer  (fallback)        оба метода
   POST <upload_url>           docs.*             не сработали
   photos.saveWallPhoto                              │
            │                    │                  ▼
            ▼                    ▼           VkBothAttachFailedException
  PhotoAttachment =         DocAttachment =    photoAttachment = null
  "photo<o>_<id>"           "doc<o>_<id>"      (деградация)
            └────────────────────┼────────────────────┘
                                 ▼
              VkApiClient.sendPostWithVideo(groupId, message, videoFile, songId, photoAttachment)
                                 │
                                 ▼
              wall.post с attachments = "photo<o>_<id>,video<o>_<v>"
                                 (или "video<o>_<v>" если photoAttachment=null)
```

### Настройки (`KaraokeProperties`)

| Ключ | Default | Назначение |
|------|---------|------------|
| `vkPhotoAttachEnabled` | `true` | Включатель загрузки через `photos.*`. `false` — пропустить шаг, fallback сразу на `docs.*` (или деградация). |
| `vkDocAttachEnabled` | `true` | Включатель fallback на `docs.*`. `false` — при сбое `photos.*` сразу деградация (пост без превью). |
| `vkPreviewImageWidth` | `1200` | Ширина PNG-превью (px), стандарт Open Graph. Используется endpoint `/api/public/song-vk-image/{id}` (`karaoke-web`). |
| `vkPreviewImageHeight` | `630` | Высота PNG-превью (px), стандарт Open Graph. |

### Префиксы ошибок `vkAutoPublishLastError`

| Префикс | Значение | Когда |
|---------|----------|-------|
| `preview prewarm failed:` | Сбой прогрева PNG (`specs/130`) | `karaoke-web` не вернул PNG или вернул мусор. Публикация блокируется. |
| `photo upload failed: invalid params (code 100): ...` | `photos.saveWallPhoto` вернул `error_code=100` | Наша ошибка (повреждённый PNG?). Публикация блокируется. |
| `photo upload failed: transient (code N): ...` | 5xx / timeout после retry | VK недоступен. Публикация блокируется. |
| `photo upload failed: VkPhotoUploadException: ...` | Прочее (empty response / invalid JSON) | Внутренняя ошибка VK API. Публикация блокируется. |
| `photo attach failed: photos=... docs=...` | Оба метода (`photos.*` + `docs.*`) не сработали | **Деградация** — пост создан без превью, администратор видит предупреждение. |

### Известные ловушки (специфичные для specs/138)

- **`photos.*` требует user-token** — community-token возвращает `error_code=27`
  «Group authorization failed: method is unavailable with group auth». User-token
  со scope `photos` настраивается через VK ID (`vkIdAccessToken`); если
  потребуется переполучить — см. [docs/features/vk-id-auth.md](./vk-id-auth.md)
  (рекомендуемый путь) или секцию
  [User-token через Implicit Flow](#user-token-через-implicit-flow-для-air-публикации-с-видео)
  (deprecated — `oauth.vk.ru` заблокирован VK 05.08.2026, см. specs/151).
- **Одна публикация = +1 фото в альбоме группы** — при 3 постах/день ~90 фото/мес,
  ~1000/год. Очистка через `photos.delete` — отдельная задача (backlog).
- **`vkPhotoAttachEnabled=false` + `vkDocAttachEnabled=false`** — обе цепочки
  отключены, бот ведёт себя как до specs/138 (только текст + видео). Полезно
  для аварийного отката.
- **PNG-кэш в `/tmp/vk_<id>.png`** — эфемерный (живёт в контейнере `karaoke-web`).
  После рестарта контейнера PNG перегенерируется при первом запросе.
- **Размер PNG строго 1200×630** — VK рекомендует Open Graph стандарт; меньший
  размер растягивается и выглядит менее качественно. Больший — обрезается
  на превью в ленте.

## Известные ловушки

- **`vkGroupId` без минуса** — бот добавляет `-` сам для `owner_id`
  `wall.post`. В `video.save` — `group_id` без минуса. Не путать.
- **VK API rate limit** (FR-006) — не документирован точно, эвристически
  ~3 поста/час на группу. Бот считает свои посты за час и переносит
  остаток на следующий тик (`vkAutoPublishRateLimitPerHour`).
- **Асинхронная обработка видео VK** — после `video.save` + загрузки VK
  обрабатывает видео секунды-минуты. Прикрепление к посту через `wall.post`
  принимается сразу, видео «подтянется» после обработки.
- **Прокси-fallback** — по образцу `TelegramApiClient.send`
  (прямой→прокси на TTL-окно `vkProxyModeTtlMs`). На admin-машинах за
  firewall — обязательно `vkProxyUrl`.
- **Общая идемпотентность air/premium** — один пост на песню. Если
  администратор хочет переопубликовать другим типом — очистить `idVk`
  вручную; старый пост ВК не удаляется ботом (FR-010).
- **Non-retryable VK API коды** (FR-010): `4`, `5`, `15`, `27`, `29`, `100` —
  немедленный выход без backoff (токен невалиден / нет прав / group authorization
  failed / rate limit / проблема запроса). Коды `27/29` дополнительно триггерят
  fallback на `docs.*` в шаге загрузки фото (specs/138).
- **specs/130: редирект НЕ считается готовностью превью** — если
  `karaoke-web` отдаёт 3xx (например, fallback-логотип), prewarm возвращает
  `FAILED`, пост НЕ создаётся. Это защищает от ситуации, когда VK-бот
  сохраняет пост с чужим логотипом вместо превью песни.
- **specs/130: эфемерный `/tmp`-кэш** — `/tmp/vk_<id>.png` живёт в контейнере
  `karaoke-web` и пропадает при перезапуске. Следующий prewarm заново
  сгенерирует файл.
- **specs/130: `wall.post` после успешного prewarm** — если VK API вернул
  ошибку (например, `error_code=5`), это отдельная ошибка VK и НЕ считается
  ошибкой прогрева. Администратор видит обычное сообщение VK API в логах;
  префикс `preview prewarm failed:` в `vkAutoPublishLastError` отсутствует.
  Повторный запуск переиспользует уже готовый PNG, повторная генерация
  не требуется.

## Ссылки

- Spec: [`specs/121-vk-news-auto-publish/spec.md`](../../specs/121-vk-news-auto-publish/spec.md)
- Plan: [`specs/121-vk-news-auto-publish/plan.md`](../../specs/121-vk-news-auto-publish/plan.md)
- Tasks: [`specs/121-vk-news-auto-publish/tasks.md`](../../specs/121-vk-news-auto-publish/tasks.md)
- Research: [`specs/121-vk-news-auto-publish/research.md`](../../specs/121-vk-news-auto-publish/research.md)
- **specs/130 прогрев превью**:
  - [`specs/130-vk-preview-generation/spec.md`](../../specs/130-vk-preview-generation/spec.md)
  - [`specs/130-vk-preview-generation/research.md`](../../specs/130-vk-preview-generation/research.md)
  - [`specs/130-vk-preview-generation/contracts/vk-preview-warmup.md`](../../specs/130-vk-preview-generation/contracts/vk-preview-warmup.md)
  - [`specs/130-vk-preview-generation/quickstart.md`](../../specs/130-vk-preview-generation/quickstart.md)
- **specs/138 надёжное превью через прикрепление фото**:
  - [`specs/138-vk-photo-preview-attachment/spec.md`](../../specs/138-vk-photo-preview-attachment/spec.md)
  - [`specs/138-vk-photo-preview-attachment/contracts/vk-photo-upload.md`](../../specs/138-vk-photo-preview-attachment/contracts/vk-photo-upload.md)
  - [`specs/138-vk-photo-preview-attachment/quickstart.md`](../../specs/138-vk-photo-preview-attachment/quickstart.md)
- Образец (Telegram-Фаза 2): [`telegram-auto-publish.md`](./telegram-auto-publish.md),
  `specs/113-telegram-demo-publish/`
- Связанные фичи: `specs/089-auto-news-song-release` (авто-новости
  сайта), `specs/101-song-news-flag` (паттерн `player_readiness_flags`),
  `specs/123-vk-og-preview-fix` (история попыток через Open Graph — заменена specs/138).

## Премиум-публикация (одновременно с Telegram)

Премиум-публикация в VK работает в паре с Telegram-премиум-публикацией через
единый хук `Song.markNewsAvailableIfReady` (см. секцию «Премиум-публикация
(автоматическая при становлении песни доступной)» в
[`telegram-auto-publish.md`](./telegram-auto-publish.md)). Ключевая особенность:
`post_id` НЕ записывается в `Song.idVk` при `persistPostId=false` — этот же слот
заполняется будущей AIR-публикацией при выходе песни в эфир. PREMIUM-выпуск и
AIR-выпуск — разные посты ВК с разным текстом (отдельные шаблоны
`vkTemplatePremium` и `vkTemplateAir`).

## User-token через Implicit Flow (для AIR-публикации с видео)

> **DEPRECATED (specs/151, 05.08.2026)**: `oauth.vk.ru` заблокирован VK —
> все варианты `/oauth.vk.ru/authorize` возвращают `Security Error`. Используйте
> **[docs/features/vk-id-auth.md](./vk-id-auth.md)** (VK ID, рекомендуемый путь).
> Эта секция оставлена для истории.

02.08.2026 выяснилось: VK API метод `video.save` (необходим для загрузки
демо-видео в группу) требует **user-token с правом `video`**. С community-token
он возвращает `error_code=5 "invalid token type"` — это ограничение VK API design,
обойти нельзя. Аналогично: `photos.*` методы недоступны с community-token
(`error_code=27 "Group authorization failed: method is unavailable with group auth"`).

Для полноценной AIR-публикации (с видео) и опционального превью (обложка через
`photos.getWallUploadServer` → `photos.saveWallPhoto`) нужен **user-token**, полученный
через Implicit Flow Standalone-приложения:

### Шаг 1. Создать Standalone-приложение

1. Войдите в VK как **владелец группы `svoemestokaraoke`** (тот, кто имеет права
   администратора).
2. Откройте https://vk.com/apps?act=manage → **«Создать приложение»**:
   - Платформа: **Standalone**
   - Категория: **«Другое»**
   - Название: любое, например «karaoke-bot»
3. В **«Настройки»** приложения:
   - Запомните **App ID** (число)
   - Задайте **Redirect URI** — например, `https://sm-karaoke.ru/api/utils/vkOAuthCallback`
     (URI можно любой, главное чтобы был доступен, но `https://sm-karaoke.ru/...` безопаснее).
   - Сохраните настройки.

### Шаг 2. Сохранить App ID и Redirect URI

В `Karaoke.properties` (admin-машина, в git НЕ лежит):
```
vkAppId=1234567                         # числовой ID Standalone-приложения
vkRedirectUri=https://sm-karaoke.ru/api/utils/vkOAuthCallback
```

Либо через UI webvue3 (раздел «Свойства → ВК»), либо через API:
```bash
curl -s -X POST "http://localhost:8898/api/properties/setproperty" \
     -d "key=vkAppId&stringValue=1234567"
curl -s -X POST "http://localhost:8898/api/properties/setproperty" \
     --data-urlencode "key=vkRedirectUri" \
     --data-urlencode "stringValue=https://sm-karaoke.ru/api/utils/vkOAuthCallback"
```

### Шаг 3. Получить готовую ссылку для авторизации

```bash
curl -s "http://localhost:8898/api/utils/vkOAuthUrl" | python3 -m json.tool
```

Ответ содержит поле **`url`** — готовая ссылка для авторизации с scopes
`video,photos,wall,offline`. **Откройте её в браузере** (от лица владельца группы).

### Шаг 4. Подтвердить scopes и получить токен

VK покажет диалог с разрешениями для **«karaoke-bot»**:
- ✓ Доступ к видео
- ✓ Доступ к фотографиям
- ✓ Доступ к стене
- ✓ Доступ в любое время (offline)

После подтверждения VK редиректит на ваш `Redirect URI` с фрагментом вида:
```
https://sm-karaoke.ru/api/utils/vkOAuthCallback#access_token=vk1.a.ваш_токен&expires_in=0&user_id=ваш_id
```

**Скопируйте токен** (значение после `#access_token=` до `&expires_in`).

### Шаг 5. Сохранить токен

```bash
curl -s -X POST "http://localhost:8898/api/utils/vkSaveUserToken" \
     --data-urlencode "token=vk1.a.ваш_токен"
```

Endpoint проверит токен через `users.get`, и если всё валидно — сохранит в
`vkUserAccessToken`. В ответ вы получите `userId` и имя.

### Шаг 6. Проверить AIR-публикацию (после сохранения токена)

После сохранения user-token `VkApiClient.video.save` начнёт **использовать
`vkUserAccessToken`**, а `wall.post` останется на community-token (он работает
для большинства случаев). Можно протестировать через ручной вызов:
```bash
curl -s -X POST "http://localhost:8898/api/song/publishToVkNow" -d "id=23217&type=air"
```

Если в логах `karaoke-app` видно `video.save error`, проверьте, что токен
содержит scope `video` (для проверки можно посмотреть на https://oauth.vk.ru/debug).

### Безопасность

- **Standalone-приложение не хранит `client_secret`** (Implicit Flow — client-side flow,
  в отличие от Authorization Code). Это безопаснее: токен выдаётся **прямо**
  пользователю в браузер.
- **Токен бессрочный** (благодаря `scope=offline`), пока не отзовёте руками.
  VK может отозвать токен при смене пароля владельца.
- **Если токен утечёт** — отзовите его в https://vk.com/settings?act=安全和登录 (или через
  Standalone-приложение) и получите новый.

### Когда нужен новый токен

- Сменился пароль владельца группы
- Владелец удалил приложение / его Standalone-приложение отключили
- VK отозвал токен автоматически (подозрительная активность)

В любом случае — повторяете шаги 3-5 с тем же `vkAppId` и `vkRedirectUri`.