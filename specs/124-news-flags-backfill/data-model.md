# Data Model: Backfill флагов публикаций готовых песен

**Branch**: `124-news-flags-backfill` | **Date**: 2026-08-03 | **Spec**: [spec.md](./spec.md)

## Обзор

Backfill НЕ вводит новых сущностей или колонок в БД. Все изменения — модификация существующего JSON-блоба `tbl_songs.player_readiness_flags` для готовых песен (`id_status=6` + непустые `source_markers`). Этот документ описывает структуру JSON, состояние «до/после» backfill, и инварианты.

## Сущности (без изменений схемы)

### `tbl_songs` (существующая, БЕЗ миграции)

Колонка `player_readiness_flags` — TEXT, содержит JSON-объект. Recordhash-триггер уже учитывает эту колонку (см. `deploy/karaoke-db/26_player_readiness_flags.sql`) — изменение содержимого меняет md5 → sync видит расхождение и разносит на PROD.

**JSON-ключи, затрагиваемые backfill** (определены в `Song.kt:1038-1081`):

| Ключ | Тип | Getter/Setter | Назначение |
|---|---|---|---|
| `newsAvailableAnnounced` | Boolean | `Song.newsAvailableAnnounced` (`Song.kt:1038`) | Одноразовый флаг «новость «появилась в коллекции» уже была». Монотонный: только false→true, никогда не сбрасывается. |
| `newsPremiumPublishPending` | Boolean | `Song.newsPremiumPublishPending` (`Song.kt:1048`) | Триггер премиум-автопубликации. `true` = scheduler должен опубликовать. Backfill сбрасывает в `false`. |
| `newsPremiumTelegramSent` | Boolean | `Song.newsPremiumTelegramSent` (`Song.kt:1054`) | Премиум-публикация в TG выполнена. Backfill ставит `true`. |
| `newsPremiumVkSent` | Boolean | `Song.newsPremiumVkSent` (`Song.kt:1059`) | Премиум-публикация в VK выполнена. Backfill ставит `true`. |
| `premiumAutoPublishState` | String | `Song.premiumAutoPublishState` (`Song.kt:1074`) | Состояние: `""`/`"RUNNING"`/`"COMPLETE"`/`"FAILED"`. Backfill ставит `"COMPLETE"`. |
| `premiumAutoPublishLastError` | String | `Song.premiumAutoPublishLastError` (`Song.kt:1079`) | Текст последней ошибки. Backfill очищает `""`. |
| `premiumAttemptCount` | String (Int-as-string) | `Song.premiumAttemptCount` (`Song.kt:1067`) | Счётчик попыток. Backfill ставит `0` (записывается как `"0"`). |

**JSON-ключи, НЕ затрагиваемые backfill** (сохраняются как есть):

| Ключ | Тип | Почему не трогаем |
|---|---|---|
| `stemAccompanimentReady` | Boolean | Готовность стема аккомпанемента. Не относится к публикациям. |
| `stemVocalReady` | Boolean | Готовность стема вокала. |
| `pictureAlbumReady` | Boolean | Готовность обложки альбома. |
| `pictureAuthorReady` | Boolean | Готовность фото автора. |
| `telegramAutoPublishState` | String | Текущее состояние TG-публикации. Если в `rendering`/`publishing` — песня пропускается (FR-007). Иначе сохраняется как есть. |
| `telegramAutoPublishLastAttemptAt` | String | Timestamp последней попытки TG. Диагностика. |
| `telegramAutoPublishLastError` | String | Текст ошибки TG. Диагностика. |
| `vkAutoPublishState` | String | Текущее состояние VK-публикации. Аналогично TG. |
| `vkAutoPublishLastAttemptAt` | String | Timestamp последней попытки VK. |
| `vkAutoPublishLastError` | String | Текст ошибки VK. |

**Колонки `tbl_songs` вне JSON** (НЕ затрагиваются backfill):

| Колонка | Тип | Назначение |
|---|---|---|
| `id_status` | Int | Статус жизненного цикла. Backfill работает с `id_status=6`. Не меняется. |
| `source_markers` | TEXT | Маркеры. Backfill проверяет `sourceMarkersList.isNotEmpty()`. Не меняется. |
| `id_telegram_demo` | VARCHAR | ID поста DEMO в TG. Backfill НЕ трогает — факт публикации сохраняется. |
| `id_vk` | VARCHAR | ID поста в VK. Backfill НЕ трогает. |
| `publish_date` / `publish_time` | VARCHAR | Дата/время эфира. Не меняются. |

### `tbl_news` (существующая, БЕЗ изменений)

Backfill НЕ добавляет сюда строки. Kill-switch (`newsAutoPublishKillSwitch`) блокирует `News.createAutoAnnouncement` на время sync-окна. Поля `tbl_news` не меняются — существующая схема: `id`, `title`, `body`, `category` (`air`/`premium`), `link`, `song_id`, `publish_at`, `created_at`, `source` (`auto`/`manual`).

### `KaraokeProperties` (base64-настройки, БЕЗ миграции)

Новый ключ `newsAutoPublishKillSwitch` (Boolean, default `false`). Читается через `KaraokeProperties.getBoolean("newsAutoPublishKillSwitch")`, управляется через `POST /api/properties/setproperty` (без рестарта контейнера). НЕ хранится в БД — в base64-файле настроек, как другие свойства.

## Состояния песни «до/после» backfill

### До backfill (примеры типичных неконсистентных состояний)

```json
// Случай A: песня готова, но newsAvailableAnnounced=false (забыли выставить)
{
  "stemAccompanimentReady": true,
  "stemVocalReady": true,
  "pictureAlbumReady": true,
  "pictureAuthorReady": true,
  "newsAvailableAnnounced": false,
  "newsPremiumPublishPending": false,
  "premiumAutoPublishState": ""
}

// Случай B: premiumAutoPublishState=RUNNING, newsPremiumPublishPending=true (зависла)
{
  "newsAvailableAnnounced": true,
  "newsPremiumPublishPending": true,
  "premiumAutoPublishState": "RUNNING",
  "premiumAttemptCount": "1"
}

// Случай C: premiumAutoPublishState=FAILED (неудачная попытка)
{
  "newsAvailableAnnounced": true,
  "newsPremiumPublishPending": false,
  "premiumAutoPublishState": "FAILED",
  "premiumAutoPublishLastError": "Telegram API: 429 Too Many Requests",
  "premiumAttemptCount": "3"
}

// Случай D: пустой/невалидный JSON
{}
```

### После backfill (единое complete-состояние для всех готовых песен)

```json
{
  "stemAccompanimentReady": true,      // сохранено (не трогаем)
  "stemVocalReady": true,               // сохранено
  "pictureAlbumReady": true,            // сохранено
  "pictureAuthorReady": true,           // сохранено
  "newsAvailableAnnounced": true,       // выставлено
  "newsPremiumPublishPending": false,   // выставлено
  "newsPremiumTelegramSent": true,      // выставлено
  "newsPremiumVkSent": true,            // выставлено
  "premiumAutoPublishState": "COMPLETE", // выставлено
  "premiumAutoPublishLastError": "",     // очищено
  "premiumAttemptCount": "0",           // сброшено
  "telegramAutoPublishState": "...",    // сохранено (если не rendering/publishing)
  "vkAutoPublishState": "..."           // сохранено
}
```

## Инварианты

### INV-001: Идемпотентность backfill

После backfill повторный backfill на той же песне НЕ вызывает `saveToDb()`. Проверка: `getDiff(this, savedSong)` в `Song.saveToDb()` (`Song.kt:5280`) возвращает пустой список, если все целевые флаги уже имеют целевые значения → early return.

### INV-002: Монотонность `newsAvailableAnnounced`

`newsAvailableAnnounced` только переходит `false→true`, никогда `true→false`. Backfill ставит `true` (если было `false`) — после этого обычный `saveToDb()` через `markNewsAvailableIfReady` не может сбросить его обратно (в `markNewsAvailableIfReady` нет ветки `newsAvailableAnnounced = false`). Гарантия отсутствия рецидивов (FR-012).

### INV-003: Блокировка повторной премиум-публикации

`markNewsAvailableIfReady` (`Song.kt:5101-5116`) устанавливает `newsPremiumPublishPending=true` ТОЛЬКО если `premiumAutoPublishState.isBlank() || premiumAutoPublishState == "RUNNING"`. После backfill `premiumAutoPublishState="COMPLETE"` → условие ложно → `newsPremiumPublishPending` не переустанавливается → `PremiumAutoPublishScheduler` не находит песню (ищет по `"newsPremiumPublishPending":true` в JSON) → автопубликация не активируется повторно (FR-016, FR-005).

### INV-004: Kill-switch блокирует только auto-новости

`News.createAutoAnnouncement` (`News.kt:337`) — единственная точка создания auto-новостей (`source="auto"`). Kill-switch проверяется здесь. Ручные новости (`News.createNew`, `source="manual"`) НЕ блокируются — админ может создавать ручные новости во время sync-окна. Это соответствует `News.listHashes` (`News.kt:118`), которая исключает `source='auto'` из sync-пула (auto-новости не синхронизируются между LOCAL и PROD — создаются на той стороне, где сработал триггер).

### INV-005: Sync разносит только изменение `player_readiness_flags`

Backfill меняет ТОЛЬКО `player_readiness_flags` (через `setReadinessFlag`/`setReadinessStringFlag` → `saveToDb()` → UPDATE только этой колонки через `getDiff`). Другие колонки `tbl_songs` (`id_telegram_demo`, `id_vk`, `source_markers`, `publish_date` и т.д.) не затрагиваются → sync разносит только изменение флагов, не трогая остальные поля. Recordhash пересчитывается триггером на UPDATE → sync видит расхождение только если флаги реально изменились (идемпотентность: повторный backfill не меняет флаги → recordhash не меняется → sync не находит расхождений).

### INV-006: Пропуск активных публикаций

Backfill НЕ меняет флаги песен, у которых `telegramAutoPublishState in ("rendering", "publishing")` ИЛИ `vkAutoPublishState in ("rendering", "publishing")`. Эти песни помечаются в отчёте `skippedActivePublishing`. Админ дождётся завершения публикации (transition в `published`/`send_failed`) и запустит backfill повторно — тогда эти песни будут обработаны.

### INV-007: Пропуск песен без маркеров

Backfill НЕ меняет флаги песен с `id_status=6`, но `sourceMarkersList.isEmpty()`. Помечаются в отчёте `skippedNoMarkers`. Такие песни — аномалия (готовая песня без маркеров), разбираются отдельно (FR-008).

## State transitions (песни в процессе backfill)

```
[BEFORE BACKFILL]
  ├─ Случай A: newsAvailableAnnounced=false, premium бланк → backfill → complete
  ├─ Случай B: newsPremiumPublishPending=true, RUNNING → backfill → complete (forced)
  ├─ Случай C: premiumAutoPublishState=FAILED → backfill → complete (forced, error cleared)
  ├─ Случай D: пустой JSON → backfill → валидный JSON с complete-набором
  ├─ Случай E: уже complete → backfill → complete (no-op, saveToDb early return)
  ├─ Случай F: activePublishing (rendering/publishing) → SKIP (skippedActivePublishing)
  └─ Случай G: no markers → SKIP (skippedNoMarkers)

[AFTER BACKFILL + SYNC + KILL-SWITCH WINDOW]
  ├─ Все готовые песни на LOCAL и PROD в complete-состоянии (INV-002, INV-003)
  ├─ tbl_news на PROD: 0 новых записей source='auto' за окно kill-switch
  └─ Kill-switch снят → нормальный flow для truly новых песен сохранён
```