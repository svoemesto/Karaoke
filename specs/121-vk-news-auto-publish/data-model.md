# Phase 1: Data Model — Автопубликация новостей в группу ВКонтакте

**Feature**: `121-vk-news-auto-publish`
**Date**: 2026-08-01
**Spec**: [spec.md](./spec.md) | [research.md](./research.md)

## Обзор

Фича **не вводит** новых таблиц или колонок в БД (Constitution Principle III —
без миграций и пересозданий recordhash-триггеров). Используются существующие
поля:

- `tbl_songs.id_vk` (существующее, `SongField.ID_VK`) — признак «песня
  опубликована во ВКонтакте» (FR-004). Заполненное значение = id поста ВК
  в формате `-<group_id>_<post_id>` (см. `Song.linkVk`, `URL_PREFIX_VK`).
- `tbl_songs.player_readiness_flags` (существующий JSON-блоб) — для редкого
  случая новости `air` без `song_id` (FR-004a), по образцу
  `telegramAutoPublishState`.
- `tbl_news.publish_at`, `tbl_news.category`, `tbl_news.song_id`,
  `tbl_news.link` (существующие) — источник триггера автопубликации.

## Сущности

### Song (существующая, `tbl_songs`)

Без структурных изменений. Используются существующие поля и getter'ы:

| Поле / getter | Тип | Назначение в фиче |
|---------------|-----|-------------------|
| `id` | Long | Идентификация песни |
| `idVk` (`SongField.ID_VK`, колонка `id_vk`) | String | **Признак «опубликовано во ВК»**: пусто → не опубликовано, заполнено → id поста ВК (FR-004, FR-008). Заполняется после успешной `wall.post`. Формат: `-<group_id>_<post_id>`. |
| `author`, `songName` | String | Для формирования текста поста (FR-003) — авто-новости `air` уже содержат их в `title` |
| `publish_date`, `publish_time` | String | Не используется напрямую (источник — `tbl_news.publish_at`, не дата песни) |
| `player_readiness_flags` | JSON-блоб | Для редкого случая (FR-004a): ключ `vkAutoPublishState` (по образцу `telegramAutoPublishState`). Для типичного случая — не нужен, состояние определяется по `idVk`. |
| `pathToFileRenderMp4ForVersion(DEMO)` | String | Путь к демо-MP4 файлу (тот же, что в Telegram-Фазе 2, FR-020) |
| `isContentReady` (computed) | Boolean | Проверка готовности для рендера (FR-022) — статус ≥ 6 + флаги готовности плеера. Переиспользуется существующая логика (как `specs/113-telegram-demo-publish` FR-011). |

**Новые getter'ы** (в `Song.kt`, computed, без колонок):
- `effectiveVkAutoPublishState`: `PUBLISHED` если `idVk` не пуст (по образцу
  `effectiveTelegramAutoPublishState`), иначе — из `player_readiness_flags`.
- `vkAutoPublishState` / `vkAutoPublishLastError` / `vkAutoPublishLastAttemptAt`:
  расширения для чтения/записи ключей в `player_readiness_flags` (по образцу
  `telegramAutoPublishState` — см. `Song.kt` getter'ы для Telegram).

### News (существующая, `tbl_news`)

Без структурных изменений. Используются существующие поля:

| Поле | Тип | Назначение в фиче |
|------|-----|-------------------|
| `id` | Long | Идентификация новости |
| `title` | String | Заголовок поста ВК (FR-003) |
| `body` | String | Текст поста ВК (FR-003) |
| `category` | String | Фильтр `= 'air'` (FR-017) — только эти новости публикуются |
| `link` | String | Разбор `songId` для ручных `air`-новостей (формат `/song?id=<id>`) |
| `song_id` | Long (nullable) | Прямая привязка к песне (авто-новости, FR-021) |
| `publish_at` | Timestamp | Фильтр `<= now()` (FR-001, FR-002) — триггер |

### VkAutoPublishState (НОВЫЙ, enum)

Состояние автопубликации для редкого случая (новость `air` без `song_id`,
FR-004a). Хранится как ключ `vkAutoPublishState` в `News.playerReadinessFlags`
JSON-блобе (по образцу `telegramAutoPublishState` в `Song.playerReadinessFlags`).

**Значения** (аналог `TelegramAutoPublishState`):

| Значение | Код | Когда |
|----------|-----|-------|
| `SCHEDULED` | `scheduled` | Новость опубликована (`publish_at <= now()`), `idVk` у связанной песни пуст (или новость без `song_id`), бот ещё не начинал |
| `RENDERING` | `rendering` | Бот рендерит демо-MP4 (FR-020 сценарий 2/3) |
| `PUBLISHING` | `publishing` | Демо-MP4 готов, бот делает `video.save` + `wall.post` |
| `PUBLISHED` | `published` | `idVk` заполнен успешно (FR-004) — типичный случай; для редкого — признак в `player_readiness_flags` |
| `SEND_FAILED` | `send_failed` | Все ретраи FR-009 исчерпаны |
| `CANCELLED` | `cancelled` | (Опц.) администратор удалил новость — только для редкого случая |

**Хранение**: для типичного случая (новость с `song_id`) — **не хранится
отдельно**, состояние выводится из `Song.idVk`:
- `idVk` пуст + не в `RENDERING`/`PUBLISHING` → `SCHEDULED`
- `idVk` пуст + в `player_readiness_flags` `vkAutoPublishState=rendering` → `RENDERING`
- `idVk` не пуст → `PUBLISHED`

### VkAutoPublishResult (НОВЫЙ, data class)

Результат одного цикла публикации (по образцу `TelegramAutoPublishResult`):

```kotlin
data class VkAutoPublishResult(
    val state: VkAutoPublishState,
    val postId: String? = null,   // id поста ВК (записывается в Song.idVk)
    val error: String? = null,
)
```

### Конфигурация (существующая `KaraokeProperties`, новые ключи)

Без новой сущности — новые ключи в существующем `listKaraokeProperties` (см.
research.md §10). Секрет `vkAccessToken` хранится в base64-файле, в git НЕ
попадает (Constitution Principle VII). Шаблоны `vkTemplateAir` /
`vkTemplatePremium` — многострочные String-значения (FR-024),
`KaraokePropertySerializable.create` String-branch корректно сериализует.

### PublicationType (НОВЫЙ, enum)

Тип публикации ВК (FR-027 — расширяемо). Не хранится в БД — передаётся
как параметр в `VkAutoPublishService.publishToVk(song, type)`.

**Значения**:
- `AIR` (`"air"`) — авто, по `tbl_news.category='air'` + `publish_at <= now()`
  (FR-001). Шаблон: `vkTemplateAir`.
- `PREMIUM` (`"premium"`) — ручной, кнопка в карточке песни (FR-026).
  Шаблон: `vkTemplatePremium`.

Идемпотентность — общая по `Song.idVk` (один пост на песню,
независимо от типа, FR-007/FR-016/FR-026). Будущие типы (FR-027)
добавляются новыми значениями enum + новыми ключами `vkTemplate<Name>`,
без структурных изменений.

### Template (шаблон поста, не отдельная сущность)

Многострочная строка в `KaraokeProperties` (`vkTemplateAir`,
`vkTemplatePremium`). Рендерится `VkTemplateService.render(template,
song, news?)` (FR-023) с заменой плейсхолдеров:
- `{author}` → `Song.author`
- `{songName}` → `Song.songName`
- `{link}` → `https://sm-karaoke.ru/song?id={id}`
- `{id}` → `Song.id`
- `{body}` → `News.body` (для `air`; пусто для `premium`)

Неизвестные плейсхолдеры — literal-текст (FR-023). Усечение итога до
10 000 символов (FR-005).

## Validation Rules

| Правило | Источник | Реализация |
|---------|----------|------------|
| Категория `air` только | FR-017 | SQL `WHERE category = 'air'` в `VkAutoPublishScheduler.loadCandidates` |
| `publish_at <= now()` | FR-001, FR-002 | SQL `WHERE publish_at IS NOT NULL AND publish_at <= now()` |
| `idVk` пуст | FR-008 | Kotlin проверка `song.idVk.isBlank()` после `Song.loadFromDbById` |
| Готовность песни | FR-022 | `song.isContentReady` (существующая computed) — статус ≥ 6 + флаги плеера |
| Размер демо-MP4 | FR-020, FR-004 | `KaraokeProperties.getLong("vkAutoPublishMaxVideoSizeMb")`, дефолт 50 МБ |
| Длина текста поста | FR-005 | Усечение до 10 000 символов с разумной границей |
| Rate limit | FR-006 | `vkAutoPublishRateLimitPerHour` (дефолт 3), бот считает посты за час |
| Идемпотентность | FR-007, FR-008 | `Song.idVk` — если заполнено, пропустить (как `idTelegramDemo` в Telegram-Фазе 2) |

## State Transitions

### Типичный случай (новость `air` с `song_id`)

```
[новость air опубликована, idVk пуст]
    ↓ @Scheduled тик → loadCandidates
SCHEDULED (idVk пуст, не в RENDERING)
    ↓ bot: isContentReady? 
    ├─ нет → пропустить, ждать следующий тик (FR-022)
    └─ да →
        ↓ demoFile exists & size ≤ limit?
        ├─ да → publishFile
        └─ нет → startRenderAndReturn → RENDERING
RENDERING (state=rendering в player_readiness_flags)
    ↓ @Scheduled тик → resumeRenderingSongs: findRenderDemoProcess
    ├─ DONE → publishFile → PUBLISHING
    └─ ERROR → SEND_FAILED
PUBLISHING (state=publishing в player_readiness_flags)
    ↓ VkApiClient.sendPost (video.save + wall.post с retry FR-009)
    ├─ success → Song.idVk = postId → PUBLISHED
    └─ all retries failed → SEND_FAILED
PUBLISHED (idVk заполнен) — стабильное, бот пропускает (FR-008)
SEND_FAILED — стабильное, админ может очистить idVk и retry
```

### Редкий случай (ручная `air`-новость без `song_id`)

Аналогично, но состояние хранится в `News.playerReadinessFlags`
(`vkAutoPublishState`), видео не прикрепляется (нет песни для рендера),
публикуется только текст со ссылкой (FR-021).

## Итоги

- **Новых таблиц**: 0
- **Новых колонок**: 0 (переиспользуется `tbl_songs.id_vk`, существующий)
- **Новых миграций БД**: 0 (Constitution Principle III соблюдён)
- **Новых getter'ы в Song.kt**: ~3 (computed, без колонок)
- **Новых классов**: 8 (`VkAutoPublishService`, `VkApiClient`,
  `VkAutoPublishScheduler`, `VkAutoPublishSchedulerStarter`,
  `VkAutoPublishState`, `VkAutoPublishResult`, `VkTemplateService`,
  `PublicationType`)
- **Новых `KaraokeProperties`-ключей**: 11 (включая 2 шаблона, см. research.md §10)
- **Новых UI-компонентов webvue3**: 2 правки/компонента (две кнопки в
  `SongEdit.vue`, новый `VkTemplatesEditor.vue`)