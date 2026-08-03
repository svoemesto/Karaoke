# API Contract: Backfill флагов публикаций готовых песен

**Branch**: `124-news-flags-backfill` | **Date**: 2026-08-03 | **Spec**: [spec.md](./spec.md)

## Обзор

Вводится 1 новый HTTP-endpoint (запуск backfill) и 1 новый ключ в `KaraokeProperties` (kill-switch). Существующие endpoint'ы (`/api/properties/setproperty`, `/api/properties/getproperty`, SSE `/api/subscribe`) используются без изменений.

## Endpoint: `POST /api/utils/backfillpublishflags`

Запуск backfill флагов публикаций готовых песен на LOCAL. По образцу существующего `POST /api/utils/backfillnewsavailable` (`ApiController.doBackfillNewsAvailable`, `ApiController.kt:5561`).

### Запрос

```
POST /api/utils/backfillpublishflags
Content-Type: application/x-www-form-urlencoded

target=local&dryRun=false
```

**Параметры**:

| Параметр | Тип | Обязательный | Default | Назначение |
|---|---|---|---|---|
| `target` | String | нет | `local` | БД для backfill. Только `local` (на PROD backfill не запускается — см. spec FR-009). Значение `remote` принимается для совместимости с существующим паттерном, но логически не используется (backfill всегда на LOCAL). |
| `dryRun` | Boolean | нет | `false` | Если `true` — отчёт о расхождениях БЕЗ записи в БД (FR-013). Если `false` — реальный backfill с записью. |

### Ответ (немедленный)

```
HTTP/1.1 200 OK
Content-Type: application/json

true
```

Возвращает `true` немедленно (операция ушла в фоновый поток). Реальный результат приходит через SSE-тост (см. ниже).

### SSE-события (фоновый поток)

Операция выполняется в фоновом потоке (`thread { ... }`, как в `doBackfillNewsAvailable`). Прогресс и финальный результат отправляются через `SNS.send(SseNotification.message(...))` — слушаются webvue3 через существующее SSE-соединение `/api/subscribe`.

**Прогресс-тосты** (каждые ~500 обработанных песен, FR-015):

```
SSE event: message
data: {
  "type": "info",
  "head": "Backfill флагов публикаций (local)",
  "body": "Обработано 500/15000 песен..."
}
```

```
SSE event: message
data: {
  "type": "info",
  "head": "Backfill флагов публикаций (local)",
  "body": "Обработано 1000/15000 песен..."
}
```
... и т.д. до завершения.

**Финальный тост** (отчёт, FR-014):

```
SSE event: message
data: {
  "type": "info",
  "head": "Backfill флагов публикаций (local) — завершено",
  "body": "{\"totalCandidates\":15000,\"fixedNewsAvailableAnnounced\":12000,\"fixedPremiumComplete\":11000,\"alreadyOk\":3000,\"skippedActivePublishing\":2,\"skippedNoMarkers\":5,\"durationMs\":480000,\"dryRun\":false}"
}
```

`body` — JSON-строка (как передаётся в существующем `Message.body`), webvue3 парсит и рендерит таблицу с разбивкой.

**Тост ошибки** (если backfill упал):

```
SSE event: error
data: {
  "type": "error",
  "head": "Backfill флагов публикаций (local)",
  "body": "Прервано ошибкой: <exception message>"
}
```

### Отчёт (формат JSON в `body`)

```json
{
  "totalCandidates": 15000,
  "fixedNewsAvailableAnnounced": 12000,
  "fixedPremiumComplete": 11000,
  "alreadyOk": 3000,
  "skippedActivePublishing": 2,
  "skippedNoMarkers": 5,
  "durationMs": 480000,
  "dryRun": false
}
```

| Поле | Тип | Назначение |
|---|---|---|
| `totalCandidates` | Int | Всего готовых песен (`id_status=6`), просканированных backfill. |
| `fixedNewsAvailableAnnounced` | Int | Песен, у которых `newsAvailableAnnounced` было `false` и стало `true`. |
| `fixedPremiumComplete` | Int | Песен, у которых premium-флаги были не в complete и стали complete. |
| `alreadyOk` | Int | Песен, у которых все флаги уже были в complete (no-op, `saveToDb` early return). |
| `skippedActivePublishing` | Int | Песен, пропущенных из-за `telegramAutoPublishState`/`vkAutoPublishState` в `rendering`/`publishing` (FR-007). |
| `skippedNoMarkers` | Int | Песен, пропущенных из-за пустых `source_markers` (FR-008). |
| `durationMs` | Long | Длительность backfill в мс. `0` для dry-run. |
| `dryRun` | Boolean | `true` если это был dry-run (без записи). |

### Идемпотентность

Повторный вызов `POST /api/utils/backfillpublishflags?target=local&dryRun=false` после успешного backfill возвращает `true`, отчёт показывает `alreadyOk=15000`, `fixedNewsAvailableAnnounced=0`, `fixedPremiumComplete=0` (FR-003, SC-008).

## Property: `newsAutoPublishKillSwitch`

Временный kill-switch, блокирующий создание auto-новостей на PROD во время sync-окна. Хранится в `KaraokeProperties` (base64-настройки), читается через `KaraokeProperties.getBoolean("newsAutoPublishKillSwitch")`.

### Включение (перед sync LOCAL→PROD)

```
POST /api/properties/setproperty
Content-Type: application/x-www-form-urlencoded

key=newsAutoPublishKillSwitch&stringValue=true
```

Выполняется на PROD (через админский UI или прямой curl). Без рестарта контейнера — `KaraokeProperties` читается на каждый запрос `getBoolean`.

### Проверка

```
POST /api/properties/getproperty
Content-Type: application/x-www-form-urlencoded

key=newsAutoPublishKillSwitch
```

Возвращает `"true"` или `"false"`.

### Снятие (после sync и проверки 0 новостей в `tbl_news`)

```
POST /api/properties/setproperty
Content-Type: application/x-www-form-urlencoded

key=newsAutoPublishKillSwitch&stringValue=false
```

### Точка проверки

Kill-switch читается в `News.createAutoAnnouncement` (`News.kt:337`). При `getBoolean("newsAutoPublishKillSwitch") == true` — метод возвращает `null` без INSERT в `tbl_news` (INV-004). Покрывает обе точки создания auto-новостей:
- `SongReleaseAnnouncementService.detectAndAnnouncementService.detectAndAnnounceAvailability` (sync-точка, premium) — вызывает `News.createAutoAnnouncement`.
- `SongReleaseAnnouncementService.checkOnAirWindow` (scheduler, air) — вызывает `News.createAutoAnnouncement`.

### Default

`newsAutoPublishKillSwitch = false` (kill-switch выкл) — нормальный flow новостей сохранён. Включается только администратором вручную на время sync-окна после backfill (FR-010).

### Не синхронизируется между LOCAL и PROD

`KaraokeProperties` — локальные настройки каждой стороны (base64-файл на контейнере). Kill-switch включается на PROD отдельно, НЕ через sync. На LOCAL kill-switch не нужен (на LOCAL backfill не создаёт новости — нет `doChangeRecords`-точки в момент backfill).

## Существующие endpoint'ы (используются без изменений)

### `POST /api/subscribe` (SSE)

Существующий SSE-endpoint (`ApiController.subscribeSse`, `ApiController.kt:5763`). webvue3 слушает через `EventSource`, получает `message`/`error` события. Backfill использует его для прогресс-тостов и финального отчёта.

### `POST /api/properties/setproperty` / `getproperty`

Существующие endpoint'ы для управления `KaraokeProperties`. Используются для kill-switch (см. выше). Без изменений.

### `POST /changerecords` (sync, PROD)

Существующий endpoint на PROD (`MainController.doChangeRecords`, `MainController.kt:266`). Sync LOCAL→PROD вызывает его для каждой затронутой песни. Для каждой строки `tbl_songs`:
1. Запоминает `wasAvailableBefore = Song.readNewsAvailableFlag(songId)` ДО UPDATE (`MainController.kt:315`).
2. Применяет UPDATE.
3. После батча вызывает `SongReleaseAnnouncementService.detectAndAnnouncementService.detectAndAnnounceAvailability(songId, wasAvailableBefore)` (`MainController.kt:342`).
4. `detectAndAnnounceAvailability` (`SongReleaseAnnouncementService.kt:71`) вызывает `News.createAutoAnnouncement` (если `wasAvailableBefore=false` и `newsAvailableAnnounced=true`).
5. `News.createAutoAnnouncement` (`News.kt:337`) проверяет kill-switch → если `true`, возвращает `null` без INSERT.

Backfill НЕ меняет `doChangeRecords` — kill-switch живёт внутри `News.createAutoAnnouncement`, который вызывается из `detectAndAnnouncementService.detectAndAnnounceAvailability`.

## Контракт ошибок

| Сценарий | Поведение |
|---|---|
| `target=remote` на backfill | Принимается (для совместимости с паттерном), но backfill всегда работает с LOCAL. Если передан `remote` — в отчёте `totalCandidates=0` (на PROD нет готовых песен в смысле LOCAL/PROD-разделения, либо работает с remote-БД, что не рекомендуется). Рекомендуется всегда `target=local`. |
| `karaoke-app` контейнер не запущен | Endpoint недоступен (HTTP-ошибка соединения). Пользователь запускает контейнер. |
| БД недоступна | `Song.loadListFromDb` возвращает пустой список или падает с `SQLException` → ловится в backfill, отчёт `totalCandidates=0`, тост с ошибкой. |
| Невалидный JSON в `player_readiness_flags` | `setReadinessFlag`/`setReadinessStringFlag` пересоздают валидный JSON с пустой map → backfill записывает complete-набор (FR-017). |
| Песня в активной публикации | Пропускается, помечается `skippedActivePublishing` (FR-007). |
| Песня без маркеров | Пропускается, помечается `skippedNoMarkers` (FR-008). |
| Sync стартовал до завершения backfill | Backfill меняет флаги на LOCAL → recordhash меняется → sync видит «полу-исправленные» записи → разносит на PROD. Kill-switch на PROD блокирует новости даже для частичного sync. Рекомендуется запускать backfill в окне без активного sync (sync ручной, по кнопке) — см. spec edge case. |