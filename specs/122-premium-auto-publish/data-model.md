# Data Model: Премиум-автопубликация в Telegram и ВК

Никаких новых таблиц, колонок или recordhash-триггеров. Все поля живут в
уже существующем JSON-блобе `tbl_songs.player_readiness_flags` (Constitution
Principle III — участвует в sync как единое поле; внутренняя структура JSON
не требует миграции при изменении).

## Song (существующая сущность, только уточнение полей)

### Поля, используемые без изменений (уже реализованы)

| Поле (Kotlin property) | Тип (в JSON) | Назначение |
|---|---|---|
| `newsAvailableAnnounced` | Boolean | Триггер «доступна» — при первом переходе false→true запускает и новость категории `premium`, и премиум-цикл (`newsPremiumPublishPending=true`). Никогда не сбрасывается обратно. |
| `newsPremiumPublishPending` | Boolean | `true` — премиум-задача открыта (хотя бы один канал ещё не закрыт), обрабатывается `PremiumAutoPublishScheduler`. `false` — задача закрыта (оба канала успешны, либо каждый исчерпал свой лимит попыток, см. FR-010). |
| `newsPremiumTelegramSent` | Boolean | `true` — Telegram-премиум пост отправлен успешно. Идемпотентность премиум-Telegram (без сохранения `idTelegramDemo`). |
| `newsPremiumVkSent` | Boolean | `true` — ВК-премиум пост отправлен успешно (текст, без видео — см. research.md R1). Идемпотентность премиум-ВК (без сохранения `idVk`). |
| `premiumAutoPublishState` | String (`""`/`RUNNING`/`COMPLETE`/`FAILED`) | Итоговое состояние ВСЕЙ премиум-задачи (оба канала). `FAILED` — только когда для обоих каналов исчерпан лимит попыток (уточнено FR-010, см. ниже). |
| `premiumAutoPublishLastError` | String | Текст последней ошибки (любого из каналов) — для FR-007 (видимость в UI). |
| `telegramAutoPublishState` | String (`scheduled`/`rendering`/`publishing`/`published`/`send_failed`/`cancelled`) | Общее для AIR и PREMIUM состояние Telegram-стороны — контекст (какой из двух типов оно описывает) определяется по `newsPremiumPublishPending`/`idTelegramDemo`, см. FR-009 spec.md. |
| `vkAutoPublishState` | String | Аналогично, для ВК. |

### Поля, вводимые/уточняемые этой фичей (FR-010)

| Поле (Kotlin property) | Тип (в JSON) | Назначение |
|---|---|---|
| `premiumAttemptCountTelegram` (новое) | String (int-as-string, как существующий `premiumAttemptCount`) | Число неудачных попыток премиум-публикации **именно** в Telegram. Инкрементируется в `PremiumAutoPublishScheduler.handleFailure` только при сбое Telegram-канала. |
| `premiumAttemptCountVk` (новое) | String (int-as-string) | То же для ВК. |
| `premiumAttemptCount` (существующее, deprecated для новой логики) | String (int-as-string) | Больше не инкрементируется новым кодом; оставлено для чтения старых записей/логов, не удаляется (без миграции). |

**Валидационное правило**: `newsPremiumPublishPending` переходит в `false`,
когда для КАЖДОГО канала выполнено одно из: канал закрыт успехом
(`newsPremiumTelegramSent`/`newsPremiumVkSent=true`) ИЛИ канал исчерпал
собственный лимит (`premiumAttemptCountTelegram`/`premiumAttemptCountVk >=
premiumAutoPublishMaxAttempts`). `premiumAutoPublishState="FAILED"`
выставляется, только если хотя бы один канал закрылся по исчерпанию
попыток (а не по успеху); `"COMPLETE"` — если оба канала закрылись
успехом.

## PremiumAutoPublishScheduler (существующий компонент, новая фаза)

Добавляется фаза `resumeRenderingSongs()` (аналог одноимённых методов в
`TelegramAutoPublishScheduler`/`VkAutoPublishScheduler`), вызываемая
БЕЗУСЛОВНО в начале `tick()` (не гейтится флагами AIR-фич):

```text
resumeRenderingSongs():
  for songId in cheap-select(player_readiness_flags LIKE '%newsPremiumPublishPending%'
                             AND (telegramAutoPublishState='rendering'
                                  OR vkAutoPublishState='rendering')):
    song = load(songId)
    if song.newsPremiumPublishPending == false: continue   # уже закрыто
    if song.telegramAutoPublishState == 'rendering'
       and not song.newsPremiumTelegramSent:
      process = findRenderDemoProcess(songId)   # тот же RENDER_MP4_DEMO
      if process is terminal (DONE/ERROR):
        TelegramAutoPublishService.onRenderCompleted(
          songId, publicationType=PREMIUM, persistMessageId=false,
          success=(process.status=='DONE'), error=...)
    if song.vkAutoPublishState == 'rendering'
       and not song.newsPremiumVkSent:
      # симметрично, на случай будущего {demoVideo} в vkTemplatePremium
      process = findRenderDemoProcess(songId)
      if process is terminal:
        VkAutoPublishService.onRenderCompleted(
          songId, type=PREMIUM, persistPostId=false,
          success=..., error=...)
```

Существующая фаза `publishPendingSongs()`/`processSong()` остаётся без
структурных изменений (см. plan.md — `PremiumAutoPublishScheduler.kt`
изменяется, но не переписывается).

## Производный UI-статус (client-side, `webvue3`)

Не хранится — вычисляется в `SongEdit.vue` из уже присутствующих в JSON
песни полей (см. research.md R3). Форма (для каждого канала независимо):

```text
PremiumChannelStatus = "not-applicable" | "pending" | "rendering"
                     | "publishing" | "published" | "failed"
```

| Значение | Условие (пример для Telegram; для ВК — симметрично) |
|---|---|
| `not-applicable` | `newsPremiumPublishPending=false` и `newsPremiumTelegramSent=false` (песня никогда не входила в премиум-цикл, либо цикл закрыт без попытки — не должно происходить в норме) |
| `published` | `newsPremiumTelegramSent=true` |
| `rendering`/`publishing` | `telegramAutoPublishState` в {`rendering`,`publishing`} |
| `failed` | `premiumAttemptCountTelegram >= premiumAutoPublishMaxAttempts` и не `published` |
| `pending` | иначе (ожидает своей очереди тика) |

## Contracts (внутренние endpoint'ы)

См. `contracts/internal-api.md` — существующие
`/api/song/publishPremiumTelegram`/`/api/song/publishPremiumVk` переиспользуются
как «Повторить» без изменения контракта; новых endpoint'ов эта фича не
вводит.
