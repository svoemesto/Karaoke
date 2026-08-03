# Phase 0: Research — Автопубликация новостей в группу ВКонтакте

**Feature**: `121-vk-news-auto-publish`
**Date**: 2026-08-01
**Spec**: [spec.md](./spec.md)

> Источник: VK API для разработчиков (`dev.vk.ru/method/*`), официальная
> документация. Страницы методов требуют JS для рендера, поэтому факты ниже
> собраны из хорошо задокументированных публичных знаний о VK API и
> кросс-проверены с общедоступной документацией.

## 1. Авторизация: Community access token (FR-018)

**Decision**: Токен сообщества (Community access token).

**Rationale**:
- Получается из настроек группы ВКонтакте: «Управление → Работа с API →
  Ключи доступа → Создать новый ключ». Включаются нужные scope (`wall`,
  `video`, `photos`).
- Постит от имени группы (`from_group=1` в `wall.post`), а не от имени
  пользователя — соответствует ответу пользователя Q2 (`/speckit.clarify`).
- **Автономен**: не имеет срока жизни, пока не отозван администратором
  вручную (явное отзыва в настройках группы). Не требует periodic re-auth
  (в отличие от VK user-токенов, которые живут ограниченное время через
  OAuth flow).
- Хранится в `KaraokeProperties` как `vkAccessToken` (base64-файл, в git
  НЕ лежит), по образцу `telegramBotToken`. Читается через
  `KaraokeProperties.getString("vkAccessToken")`.

**Альтернативы**:
- User access token (VK OAuth flow) — отвергнут: требует periodic re-auth,
  зависит от аккаунта конкретного пользователя.
- Implicit flow token — отвергнут: клиентский flow, не для server-side.

## 2. wall.post — публикация поста в группе (FR-001, FR-003, FR-006)

**Decision**: `wall.post` (VK API method) для создания поста.

**Параметры** (ключевые):
- `owner_id` — id группы **с минусом** (например, `-123456` для группы
  `vk.ru/club123456`). Берётся из `KaraokeProperties.getString("vkGroupId")`,
  бот добавляет минус-префикс.
- `from_group` — `1` (пост от имени сообщества, не от имени пользователя).
- `message` — текст поста (FR-003): формируется из `News.title` + `News.body`
  + ссылка (см. контракты). Лимит — **10 000 символов** для standalone-поста.
- `attachments` — список прикреплений через запятую. Для видео:
  `video<owner_id>_<video_id>` (см. §3). Для ссылки — не указывается в
  `attachments`, VK сам парсит URL из `message` и генерирует rich-preview.
- `access_token` — Community access token (§1).
- `v` — версия API, использовать актуальную стабильную (например, `5.199`).
  Фиксируется в `KaraokeProperties` или в коде (после research —
  рекомендуется хардкод `5.199` или аналогичная стабильная).

**Ответ**:
```json
{ "response": { "post_id": 12345 } }
```
`post_id` — id созданного поста в группе. Это значение записывается в
`Song.idVk` (FR-004) как `"-<group_id>_<post_id>"` (полный формат VK-ссылки
на пост, как `Song.linkVk` ожидает — см. `URL_PREFIX_VK` в `Song.kt:1156`).

**Rate limit** (FR-006):
- VK API не публикует точные цифры rate limit для `wall.post`, но на
  практике: не более **3 постов в час** и не более **50 постов в день** для
  одной группы (эвристически, по сообщениям разработчиков в open-source
  VK-ботах). Это **не** документированный лимит — фактический может
  отличаться, и VK может временно понизить лимит при подозрительной
  активности.
- Решение: ввести конфигурируемый `vkAutoPublishRateLimitPerHour`
  (дефолт `3`), бот соблюдает его сам — не отправляет более N постов
  за час, остаток переносит на следующий тик (FR-011, FR-006).
- На rate-limit-ошибку (`error_code=27`, `Too many requests`) бот
  трактует как retryable (FR-009), но НЕ немедленно — ждёт до начала
  следующего часа.

**Альтернативы**:
- `wall.sendComment` — для комментария к существующему посту (не подходит,
  мы создаём новый пост, FR-001).
- `execute` (VK scripting) — отвергнут: избыточно для одного метода
  на вызов.

## 3. video.save + загрузка видео — прикрепление демо-MP4 (FR-019, FR-020)

**Decision**: Двухшаговый flow `video.save` → upload → `wall.post` с
`attachments=video...`.

**Шаг 1: `video.save`** (резервирование видео-записи):
- `group_id` — id группы (без минуса, для `video.save`).
- `name` — название видео (например, `"<author> — <songName> (демо)"`,
  аналогично `buildCaption` в Telegram-Фазе 2).
- `description` — описание (можно дублировать `name` или использовать
  `News.body`).
- `access_token`, `v` — как в §2.
- **Ответ**:
  ```json
  { "response": {
      "owner_id": -123456,
      "video_id": 1234567890,
      "upload_url": "https://uploader.vk.ru/upload.php?..."
  }}
  ```

**Шаг 2: загрузка видеофайла** на `upload_url`:
- `POST multipart/form-data` с полем `video_file` (демо-MP4 файл).
- Лимит размера видео через VK API: **2 ГБ** для пользовательских загрузок,
  но для групп через Community token — обычно меньше. На практике для
  демо-MP4 (фрагмент ~30 сек, ~5-20 МБ) лимит не проблема. Конкретный
  лимит фиксируется в `vkAutoPublishMaxVideoSizeMb` (дефолт `50`, тот же,
  что в Telegram-Фазе 2 — для безопасности).
- **Ответ**: JSON `{"size": N, "video_id": "..."}` — подтверждение загрузки.
  После этого видео становится доступным, но может потребоваться время
  на обработку (encoding) перед прикреплением к посту.

**Шаг 3: `wall.post` с `attachments`**:
- `attachments = "video<owner_id>_<video_id>"` (из ответа `video.save`).
- `message` — текст поста (§2).

**Gotcha — обработка видео**: VK обрабатывает загруженное видео асинхронно
(от нескольких секунд до минут). Прикрепление к посту возможно сразу после
`video.save` (по `video_id`), но виден пост станет после обработки. Бот
не ждёт обработки — `wall.post` с `attachments=video...` принимается сразу,
видео «подтянется» после обработки. Если `wall.post` вернуть ошибку
«видео не готово» (error_code для этого случая — уточнить, обычно
`error_code=1700` или `error_code=27`), бот трактует как retryable с
долгим backoff (см. FR-009).

**Альтернативы**:
- `docs.getWallUploadServer` + загрузка как документ (`doc`) — отвергнут:
  видео заметнее в ленте ВК, чем «голый» файл-документ.
- Прикрепление `link` вместо `video` — отвергнут в пользу видео (ответ
  пользователя Q3 `clarify`: «прикреплять демо-MP4 как видео»).

## 4. Прокси-fallback (FR-009, по образцу TelegramApiClient)

**Decision**: Прямой→прокси-fallback, по образцу `TelegramApiClient.send`
(`TelegramApiClient.kt:146`).

**Rationale**:
- admin-машина может быть за firewall/прокси (Constitution Principle I:
  «исторически проект развивался в условиях ограниченного/нестабильного
  интернета»). VK API может быть недоступен напрямую так же, как Telegram.
- Реализация: новый ключ `vkProxyUrl` (по образцу `telegramProxyUrl`),
  `VkApiClient.send` повторяет логику `TelegramApiClient.send` —
  пытается напрямую, при ошибке переключается на прокси на TTL-окно
  (`vkProxyModeTtlMs`, дефолт `60000` мс).
- `HttpClient` с `ProxySelector.of(InetSocketAddress(host, port))` —
  идентично `TelegramApiClient.proxyClient`.

**Альтернативы**:
- Только прокси — отвергнут: добавляет обязательную конфигурацию,
  ломает работу на машинах с прямым доступом.
- Только прямой — отвергнут: не работает на машинах за firewall.

## 5. Retry/backoff (FR-009)

**Decision**: 3 попытки с экспоненциальным backoff `30 сек → 2 мин → 5 мин`,
как в `TelegramApiClient.sendVideo` (`backoffScheduleMs = listOf(30000,
120000, 300000)`, `TelegramApiClient.kt:237`).

**Non-retryable коды VK API** (аналог `NON_RETRYABLE_ERROR_CODES` в
`TelegramApiClient.kt:357`):
- `4` — Incorrect signature (auth/token problem — повтор бессмысленен)
- `5` — User authorization failed (токен невалиден — повтор бессмысленен)
- `10` — Server error (internal VK — retryable, но не non-retryable)
- `15` — Access denied (нет прав на posting в группу — повтор
  бессмысленен, проверка токена)
- `100` — One of the parameters is missing or invalid (проблема
  запроса — повтор бессмысленен)
- `2700` — Too many recipients (rate-limit-like — retryable с долгим
  backoff, не non-retryable)

**Retryable** (по умолчанию, всё что не в non-retryable): сеть/timeout/5xx,
`6` (too many requests per second), `9` (server error), `1` (unknown error).

## 6. Текст поста и шаблон (FR-003, FR-005, FR-023, FR-024)

**Decision**: Шаблоны с плейсхолдерами в `KaraokeProperties`, по типу
публикации. Заменяет хардкод `buildCaption` из Telegram-Фазы 2.

**Два типа публикаций** (FR-027 — расширяемо):

| Тип | Ключ шаблона | Триггер | Дефолт (если ключ пуст) |
|-----|--------------|--------|-------------------------|
| `air` | `vkTemplateAir` | Авто, по `tbl_news.category='air'` + `publish_at <= now()` | `"{author} — {songName} (демо)\n{link}\n#караоке #svoemesto"` (по образцу `TelegramAutoPublishService.buildCaption`) |
| `premium` | `vkTemplatePremium` | Ручной, кнопка в карточке песни | `"{author} — {songName} (премиум)\n{link}\n#караоке #svoemesto #премиум"` |

**Плейсхолдеры** (FR-023, в фигурных скобках):
- `{author}` → `Song.author`
- `{songName}` → `Song.songName`
- `{link}` → `https://sm-karaoke.ru/song?id={id}`
- `{id}` → `Song.id`
- `{body}` → `News.body` (для `air`; для `premium` — пустая строка,
  т.к. нет связанной новости)
- (Опц., расширяемо) `{album}`, `{year}` и т.п. — на усмотрение
  планирования

**Рендеринг шаблона** (`VkTemplateService.render(template, song, news?)`):
- Построчная замена плейсхолдеров на значения.
- Неизвестные плейсхолдеры (например, `{nonexistent}`) — оставляем как
  literal-текст (не падаем, не заменяем, FR-023). Это упрощает
  реализацию: регулярка `\{(\w+)\}` → если ключ известен, заменяем; если
  нет — оставляем как есть.
- Несбалансированные скобки (например, `{author` без `}`) — регулярка не
  матчит, текст остаётся как есть. Редактор шаблонов (FR-025) показывает
  предупреждение, но не блокирует сохранение.
- Усечение итогового текста до 10 000 символов (FR-005, VK лимит) с
  разумной границей (не разрывая слово), маркер `…` при усечении.

**Хранение** (FR-024): `KaraokeProperties` String-значения. Уже
поддерживается через `KaraokePropertySerializable.create` String-branch
(`Karaoke.kt:46-49`): значение сериализуется в JSON-строку → base64.
Многострочные значения — через `\n` в строке, сериализация корректна.
Без новых таблиц/колонок (Constitution Principle III).

**Альтернативы**:
- Конфигурируемый шаблон только через `vkAutoPublishCaptionTemplate`
  (один на все типы) — отвергнут: нужны разные шаблоны для `air` и
  `premium` (User Story 5, FR-023).
- Отдельная таблица `tbl_vk_templates` — отвергнута: избыточно для
  двух строковых значений, требует sync-регистрации (Constitution
  Principle III). `KaraokeProperties` достаточно.

## 6a. Редактор шаблонов (FR-025)

**Decision**: Отдельный UI в `webvue3` (`VkTemplatesEditor.vue`),
читает/пишет шаблоны через `GET/POST /api/vk/templates` (см.
контракты).

**Rationale**: Generic Properties UI (`PropertiesView.vue`/`PropertiesTable`)
— однострочные поля; многострочные шаблоны с плейсхолдерами неудобно
править. Спец. редактор:
- Список типов (`air`, `premium`) → многострочный `<textarea>` для
  каждого.
- Подсказка по плейсхолдерам (static список `{author}`, `{songName}`,
  `{link}`, `{body}`, `{id}`).
- Предпросмотр (опц.) — пример подстановки на тестовой песне.
- Валидация скобок (предупреждение, не блокировка — User Story 6
  сценарий 3).
- Сохранение → `POST /api/vk/templates` → запись в `KaraokeProperties`
  → без перезапуска `karaoke-app` (FR-015).

## 6b. Premium-flow (FR-026)

**Decision**: Кнопка «Опубликовать во ВК (premium)» в карточке песни
(`SongEdit.vue`, рядом с `air`-кнопкой). Триггерит тот же путь, что и
`air`, но с `type=PREMIUM`:

1. `POST /api/song/publishToVkNow?id=<songId>&type=premium` (см.
   контракты).
2. `ApiController.publishToVkNow` → `VkAutoPublishService.publishToVk(song,
   PublicationType.PREMIUM)`.
3. FR-008: если `idVk` не пуст → отказ (общая идемпотентность, FR-016).
4. FR-022: если `!isContentReady` → отказ.
5. FR-023: текст по `vkTemplatePremium` (через `VkTemplateService`).
6. FR-019..FR-021: прикрепление демо-MP4 (тот же flow, что и `air` —
   `video.save` + `wall.post`).
7. FR-004: запись `idVk` после успеха.

**Не** зависит от `tbl_news` — `premium`-публикация не требует
существования новости. Текст строится из шаблона и полей песни, без
`News.body`.

**Идемпотентность**: общая с `air` по `Song.idVk` — один пост на песню,
независимо от типа. Переопубликование другим типом — очистка `idVk`
вручную (User Story 5 сценарий 3). Бот не удаляет старый пост ВК (FR-010).

## 7. Состояние публикации (FR-012, FR-022, FR-004a)

**Decision**: Для типичного случая (новость `air` связана с песней через
`song_id`) — состояние определяется по `Song.idVk` (см. data-model.md).
Для редкого случая (ручная `air`-новость без `song_id`) — `VkAutoPublishState`
enum в `News.playerReadinessFlags`-подобном JSON-блобе (по образцу
`telegramAutoPublishState`).

**Значения `VkAutoPublishState`** (аналог `TelegramAutoPublishState`):
- `SCHEDULED` — новость опубликована на сайте (`publish_at <= now()`),
  `idVk` пуст, бот ещё не начал.
- `RENDERING` — бот рендерит демо-MP4 (FR-020 сценарий 2/3).
- `PUBLISHING` — демо-MP4 готов, бот делает `video.save` + `wall.post`.
- `PUBLISHED` — `idVk` заполнен успешно (FR-004).
- `SEND_FAILED` — все ретраи FR-009 исчерпаны.
- `CANCELLED` — не используется для типичного случая (нет «расписания»
  на уровне песни); для редкого случая — администратор удалил новость.

**Rationale**: переиспользование паттерна `telegramAutoPublishState` (Constitution
Principle III — без новой колонки, без правки recordhash-триггера).
Основное состояние (типичный случай) — по `Song.idVk` (FR-008 — если
заполнено, `PUBLISHED`).

## 8. Источник истины — `tbl_news` (FR-001, FR-002a)

**Decision**: Бот сканирует `tbl_news` на `air`-новости с
`publish_at <= now()`, для каждой определяет связанную песню через
`News.song_id` (авто-новости) или разбор `News.link` (формат `/song?id=<id>`,
ручные `air`-новости). Для песен с пустым `idVk` — публикует.

**SQL-кандидат** (по образцу `TelegramAutoPublishScheduler.loadWindowCandidateIds`,
`TelegramAutoPublishScheduler.kt:148`):

```sql
SELECT id, title, body, link, song_id
FROM tbl_news
WHERE category = 'air'
  AND publish_at IS NOT NULL
  AND publish_at <= now()
  AND (song_id IS NOT NULL OR link LIKE '%/song?id=%')
```

Для каждой найденной новости бот:
1. Определяет `songId` (из `song_id` или из `link`).
2. `Song.loadFromDbById(songId)` — загружает песню.
3. Если `song.idVk` не пуст → пропускает (FR-008, идемпотентность).
4. Если `song.idVk` пуст → проверяет готовность (FR-022), рендерит демо-MP4
   при необходимости (FR-020), публикует (FR-019), записывает `idVk` (FR-004).

**Снимок-бэклог FR-012**: не нужен как отдельная операция — песни с уже
заполненным `idVk` автоматически исключаются (FR-008). Это упрощает
реализацию по сравнению с `specs/089-auto-news-song-release` (где
снимок был нужен, т.к. признак «новость создана» не существовал ранее).

## 9. Периодичность тика (FR-002a)

**Decision**: `@Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)`
— 60 секунд, как в `TelegramAutoPublishScheduler.kt:53`. Короче окна 5-15
минут, чтобы любая опубликованная новость гарантированно поймана.
`fixedDelay` (не `fixedRate`) — гарантия, что долгий тик (несколько
публикаций с retry) не наезжает на следующий.

## 10. Лимиты и итоговый набор `KaraokeProperties`-ключей

**Decision**: Новый набор ключей в `listKaraokeProperties` (по образцу
`telegramAutoPublish*`):

| Ключ | Тип | Дефолт | Назначение |
|------|-----|--------|------------|
| `vkAutoPublishEnabled` | Boolean | `false` | Глобальный вкл/выкл бота (air-автопостинг) |
| `vkGroupId` | String | `""` | ID группы ВК (без минуса, бот добавляет `-`) |
| `vkAccessToken` | String | `""` | Community access token (секрет, в git НЕ попадает) |
| `vkApiVersion` | String | `"5.199"` | Версия VK API (стабильная) |
| `vkProxyUrl` | String | `""` | Прокси для VK API (по образцу `telegramProxyUrl`) |
| `vkProxyModeTtlMs` | Long | `60000` | TTL окна прокси-режима (мс) |
| `vkAutoPublishWindowMinutes` | Long | `5` | Окно (оставлено для консистентности с Telegram) |
| `vkAutoPublishMaxVideoSizeMb` | Long | `50` | Лимит размера демо-MP4 (МБ) |
| `vkAutoPublishRateLimitPerHour` | Long | `3` | Max постов в час (FR-006) |
| `vkTemplateAir` | String | `""` (дефолт в коде, см. §6) | Шаблон поста типа `air` с плейсхолдерами (FR-023, FR-024). Многострочная строка. |
| `vkTemplatePremium` | String | `""` (дефолт в коде, см. §6) | Шаблон поста типа `premium` с плейсхолдерами (FR-023, FR-024). |

## Итоги Phase 0

Все NEEDS CLARIFICATION из Technical Context разрешены:
- ✅ Лимиты VK API (размер видео, длина поста, rate limit)
- ✅ Тип токена (Community access token)
- ✅ Прокси-fallback (переиспользование паттерна)
- ✅ Шаблон поста (два типа: `vkTemplateAir`, `vkTemplatePremium` с плейсхолдерами, FR-023/FR-024)
- ✅ Редактор шаблонов (отдельный UI в webvue3, FR-025)
- ✅ Premium-flow (ручная кнопка, FR-026)
- ✅ Состояние публикации (по `Song.idVk` + `VkAutoPublishState` для редкого случая)
- ✅ SQL-кандидат сканирования `tbl_news`
- ✅ Набор `KaraokeProperties`-ключей (11 ключей, включая 2 шаблона)

Готово к Phase 1 (`data-model.md`, `contracts/`, `quickstart.md`).