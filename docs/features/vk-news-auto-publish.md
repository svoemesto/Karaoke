# Автопубликация новостей в группу ВКонтакте

> **Status**: active (specs/121-vk-news-auto-publish)
> **Feature Key**: vk-news-auto-publish
> **Last Updated**: 2026-08-01

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
5. FR-019: `VkApiClient.sendPostWithVideo` — `video.save` → загрузка →
   `wall.post` с `attachments=video<owner_id>_<video_id>`.
6. FR-004: при успехе — `song.fields[SongField.ID_VK] = postId`,
   `song.saveToDb()` (с диффом, recordhash-триггером, SSE).
7. FR-009: при сбое — 3 ретрая с backoff 30с→2мин→5мин; затем
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
- **Non-retryable VK API коды** (FR-010): `4`, `5`, `15`, `100` —
  немедленный выход без backoff (токен невалиден / нет прав / проблема
  запроса).

## Ссылки

- Spec: [`specs/121-vk-news-auto-publish/spec.md`](../../specs/121-vk-news-auto-publish/spec.md)
- Plan: [`specs/121-vk-news-auto-publish/plan.md`](../../specs/121-vk-news-auto-publish/plan.md)
- Tasks: [`specs/121-vk-news-auto-publish/tasks.md`](../../specs/121-vk-news-auto-publish/tasks.md)
- Research: [`specs/121-vk-news-auto-publish/research.md`](../../specs/121-vk-news-auto-publish/research.md)
- Образец (Telegram-Фаза 2): [`telegram-auto-publish.md`](./telegram-auto-publish.md),
  `specs/113-telegram-demo-publish/`
- Связанные фичи: `specs/089-auto-news-song-release` (авто-новости
  сайта), `specs/101-song-news-flag` (паттерн `player_readiness_flags`)

## Премиум-публикация (одновременно с Telegram)

Премиум-публикация в VK работает в паре с Telegram-премиум-публикацией через
единый хук `Song.markNewsAvailableIfReady` (см. секцию «Премиум-публикация
(автоматическая при становлении песни доступной)» в
[`telegram-auto-publish.md`](./telegram-auto-publish.md)). Ключевая особенность:
`post_id` НЕ записывается в `Song.idVk` при `persistPostId=false` — этот же слот
заполняется будущей AIR-публикацией при выходе песни в эфир. PREMIUM-выпуск и
AIR-выпуск — разные посты ВК с разным текстом (отдельные шаблоны
`vkTemplatePremium` и `vkTemplateAir`).