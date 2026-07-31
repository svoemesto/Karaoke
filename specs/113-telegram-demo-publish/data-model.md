# Data Model: Автопубликация демо-версий песен в Telegram-канал по расписанию

> Phase 1 — описание изменений в сущностях и переходов состояний.
> Никаких новых таблиц; никаких новых колонок; только новые
> ключи внутри уже существующего JSON-блоба `player_readiness_flags`.

## Song / Settings (`tbl_songs` / `tbl_settings`) — изменения

### Схема колонок

**Без изменений.** Все новые per-song данные живут внутри уже
существующего текстового JSON-поля `player_readiness_flags` (паттерн
`specs/101-song-news-flag`, миграция
`deploy/karaoke-db/26_player_readiness_flags.sql`).

### Новые ключи внутри `player_readiness_flags`

| Ключ (Kotlin) | Тип | Default | Участвует в sync |
|---|---|---|---|
| `telegramAutoPublishState` | String (enum) | `""` (отсутствует ⇒ читается как `"scheduled"` если `idTelegramDemo == ""`, иначе `"published"`) | Да — как часть уже синхронизируемого поля `player_readiness_flags`, без изменений recordhash-формулы |
| `telegramAutoPublishLastAttemptAt` | String (ISO-8601) | `""` (отсутствует) | Да — то же поле, тот же sync |
| `telegramAutoPublishLastError` | String (free text) | `""` (отсутствует) | Да — то же поле, тот же sync |

### Enum `TelegramAutoPublishState` (Kotlin)

```kotlin
enum class TelegramAutoPublishState(val code: String) {
    SCHEDULED("scheduled"),         // date/time заполнены, в будущем, бот ещё не начал
    RENDERING("rendering"),         // бот рендерит демо-MP4 (FR-003 сц. 2 или 3)
    PUBLISHING("publishing"),       // демо-MP4 готов, бот делает sendVideo (+ретраи FR-010)
    PUBLISHED("published"),         // idTelegramDemo заполнен успешно
    SEND_FAILED("send_failed"),     // все ретраи FR-010 исчерпаны
    CANCELLED("cancelled");         // админ очистил дату/время (бот не публикует)

    companion object {
        fun fromCode(code: String?): TelegramAutoPublishState? =
            values().firstOrNull { it.code == code }
    }
}
```

### Производное правило чтения `telegramAutoPublishState`

Для UI и для логики scheduler'а — единая функция
`Song.effectiveTelegramAutoPublishState()`:

```kotlin
val effectiveTelegramAutoPublishState: TelegramAutoPublishState
    get() {
        // "опубликована" определяется по факту заполненного idTelegramDemo
        // (FR-008), а не по полю state — чтобы любая попытка записи
        // (автоматическая Фазой 2 или ручная Фазой 1 через catch)
        // согласовано отражалась в UI
        if (idTelegramDemo.isNotEmpty()) return TelegramAutoPublishState.PUBLISHED

        // если state явно отменён — уважаем его до тех пор, пока
        // админ не переставит дату/время
        val raw = readinessFlag("telegramAutoPublishState")
        if (raw == "cancelled") return TelegramAutoPublishState.CANCELLED

        // если дата/время в прошлом — "опоздавшая" (Q1 spec.md, FR-001)
        val dt = dateTimePublish
        if (dt == null) return TelegramAutoPublishState.SCHEDULED
        if (dt.before(Date())) return TelegramAutoPublishState.SCHEDULED
            // "запланирована" с прошедшей датой = "опоздавшая" (UI маркирует)
            // бот её не публикует; админ должен либо переставить
            // дату/время, либо нажать "Опубликовать сейчас"

        // иначе — то, что явно записано в state, или "scheduled" по умолчанию
        return TelegramAutoPublishState.fromCode(raw)
            ?: TelegramAutoPublishState.SCHEDULED
    }
```

### Уже существующие поля (используются, без изменений)

| Поле (Kotlin) | Колонка | Уже используется для |
|---|---|---|
| `idTelegramDemo` | `id_telegram_demo` (VARCHAR) | Хранение `message_id` от Telegram (FR-006) |
| `date` / `time` / `dateTimePublish` | `date` / `time` (VARCHAR) | Триггер FR-001 — наступление даты/времени публикации |
| `demoFragmentStartSeconds` и т.п. | (поля `Settings`) | Параметры рендера `RENDER_MP4_DEMO` (FR-003) |
| `idStatus` | `id_status` (BIGINT) | Условие FR-011 (песня публично готова) |

### Переходы `telegramAutoPublishState`

```
                              ┌──────────────────────────────────┐
                              │ date/time в прошлом              │
                              │ (Q1 clarify: "опоздавшая")       │
                              │ бот НЕ публикует                 │
                              ▼                                  │
[SCHEDULED] ─── date/time наступила ───▶ [RENDERING] ─── рендер ОК ───▶ [PUBLISHING]
    ▲                                       │                              │
    │                                       │ рендер не ОК                │ sendVideo ОК
    │                                       ▼                              ▼ (FR-006)
    │                                  [SCHEDULED]                    [PUBLISHED] (idTelegramDemo != '')
    │                                       ▲                              │
    │                                       │                              │
    │                                       │            ┌─────────────────┘
    │                                       │            │
    │                                       │     sendVideo провалился
    │                                       │     3 попытки исчерпаны (FR-010)
    │                                       │            │
    │                                       │            ▼
    │                                       │     [SEND_FAILED]
    │                                       │            │
    │                                       │            │ админ починил + ретрай
    │                                       │            │ (или нажал "Опубликовать сейчас")
    │                                       │            ▼
    │                                       └────── [RENDERING] / [PUBLISHING] / [PUBLISHED]
    │
    │
[CANCELLED] ◀── админ очистил date/time (или иной способ отмены)
    │
    │ админ переставил date/time в будущее + idTelegramDemo == ''
    │ (или админ очистил idTelegramDemo вручную через Settings UI)
    ▼
[SCHEDULED]
```

**Инварианты переходов**:
1. `[SCHEDULED] → [RENDERING]` — только если
   `dateTimePublish != null && dateTimePublish >= now() - window`
   (т.е. дата/время в пределах текущего тика), и `idTelegramDemo == ''`
   (FR-008), и `dateTimePublish >= now()` (т.е. не в прошлом —
   Q1 spec.md).
2. `[RENDERING] → [PUBLISHING]` — только если `KaraokeProcess`-
   задача `RENDER_MP4_DEMO` завершилась успешно, и файл
   удовлетворяет лимиту Telegram (FR-004), и `sendVideo` начат.
3. `[PUBLISHING] → [PUBLISHED]` — только если хотя бы одна из
   до 3 попыток `sendVideo` вернула `message_id` (FR-010),
   и `message_id` записан в `idTelegramDemo` через `Song.saveToDb()`
   (FR-006).
4. `[PUBLISHING] → [SEND_FAILED]` — только если все 3 попытки
   `sendVideo` исчерпаны (FR-010).
5. `[SEND_FAILED] → [RENDERING] / [PUBLISHING]` — если админ
   инициирует повтор через кнопку «Опубликовать сейчас»
   (FR-015), и `idTelegramDemo` всё ещё пусто.
6. `[SEND_FAILED] → [SCHEDULED]` — если админ очистил
   `telegramAutoPublishLastError` (опционально) и хочет, чтобы
   scheduler попробовал снова. **Поведение на усмотрение UI**:
   достаточно того, что нажатие «Опубликовать сейчас» снова
   триггерит (п.5).
7. `[SCHEDULED] → [CANCELLED]` — если админ очистил date/time
   (или явно нажал «Отменить публикацию» — на усмотрение UI).
8. `[CANCELLED] → [SCHEDULED]` — если админ снова указал
   дату/время в будущем, и `idTelegramDemo == ''`. Если
   `idTelegramDemo != ''` — остаётся `[PUBLISHED]`
   (не «[CANCELLED] → [SCHEDULED]»).
9. **Нет** переходов `[PUBLISHED] → [*]` кроме `[PUBLISHED] → [PUBLISHED]`
   (идемпотентность FR-007/FR-008). Перепубликация требует
   явной очистки `idTelegramDemo` администратором.

### Что не вводится как новая таблица

- `tbl_telegram_publish_attempts` (полный журнал попыток) —
  **не вводится**. Хранится только последняя попытка
  (`LastAttemptAt`, `LastError`). Если в будущем понадобится
  журнал — это отдельная фича.
- `tbl_telegram_publish_settings` (per-song overrides для
  `telegramAutoPublishChannelId` и т.п.) — **не вводится**.
  Настройки глобальны (`KaraokeProperties`), per-song overrides
  не предусмотрены спецификацией.

## KaraokeProcess — изменения

**Без изменений схемы.** `KaraokeProcess` с типом
`KaraokeProcessTypes.RENDER_MP4_DEMO` уже существует и
используется для разовых рендеров демо-MP4 (например, из
UI `webvue3`). Фаза 2 просто **переиспользует** эту
существующую задачу для постановки в очередь из
`TelegramAutoPublishService` (если демо отсутствует или
превышает лимит, FR-003 сценарии 2/3).

**Альтернативы рассмотрены**:
- Новый тип `KaraokeProcessTypes.RENDER_MP4_DEMO_LOW_QUALITY`
  (для перерендера с уменьшенными параметрами) — отклонён:
  параметры передаются в уже существующую задачу
  (`demoFragmentStartSeconds`, `demoFragmentEndSeconds`,
  новые поля, если планирование решит их добавить,
  например, `demoResolution` или `demoBitrate`).

## KaraokeProperties — изменения

**Новые ключи** (в `KaraokeProperties.kt`):

| Ключ | Тип | Default | Описание |
|---|---|---|---|
| `telegramAutoPublishEnabled` | Boolean | `false` | Включён ли плановый бот автопубликации. Если `false` — `TelegramAutoPublishScheduler` не стартует (аналог `telegramPollingEnabled` для Фазы 1). Endpoint `/api/song/publishToTelegramNow` работает независимо (по прямому запросу администратора). |
| `telegramAutoPublishChannelId` | String | `""` | ID/username Telegram-канала, в который публиковать (например, `-1001234567890` или `@svoemesto`). Тот же канал, что парсится в Фазе 1, но явно задан — не берётся из параметров `channel_post`. |
| `telegramAutoPublishWindowMinutes` | Long | `5` | Ширина скользящего окна тика (в минутах). Настраивается в пределах 1–30 (Q1 spec.md: «5–10 минут»). |
| `telegramAutoPublishMaxFileSizeMb` | Long | `50` | Лимит размера MP4-файла (в МБ) для проверки FR-004. Стандартный Telegram Bot API — 50. Локальный Bot API сервер — больше. |

**Без изменений**:
- `telegramBotToken` — тот же токен, что для Фазы 1.
- `telegramProxyUrl` — переиспользуется (FR-010).
- `telegramProxyModeTtlMs` — без изменений.

## НЕ вводимые изменения

- `tbl_news` — фича не создаёт новостей, только Telegram-посты
  (Out of Scope spec.md). Уже существующий механизм
  авто-новостей (`specs/101-song-news-flag`) — отдельная фича.
- `tbl_publications` / `model/Publication.kt` — фича **не
  модифицирует** сущность Publication. Расписание автопубликации
  живёт на уровне самой песни, не на уровне Publication
  (Q1 clarify spec.md).
- `tbl_pictures`, `tbl_authors`, `tbl_albums` — фича не
  касается этих таблиц.
- `tbl_song_news_announced` — уже удалена в `specs/101-song-news-flag`
  (миграция `34_cleanup_song_news_announced.sql`).
- `Settings.idTelegramLyrics`, `idTelegramKaraoke`, `idTelegramChords`,
  `idTelegramMelody` — фича использует **только** `idTelegramDemo`.
  Поведение Фазы 1 для остальных версий не меняется (FR-009).
