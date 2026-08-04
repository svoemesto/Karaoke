# Contracts: фича 131 — внутренний контракт пайплайна approve

> **Объём**: фича **не вводит новых HTTP-эндпоинтов, DTO, SSE-событий,
> миграций БД**. Контракт здесь описывает только **внутренние** правила
> оркестрации — последовательность вызовов внутри `SongEditorController.approve()`
> и пост-хук callback из `KaraokeProcessThread`.
>
> Этот документ рассчитан на ревьюеров, которые будут читать код, и на
> будущего автора тестов. Он НЕ описывает публичные API.

## Содержание

1. [Точка входа — `POST /editor/song/approve`](#1-точка-входа--posteditorsongapprove)
2. [Контракт оркестрации в `approve()`](#2-контракт-оркестрации-в-approve)
3. [Контракт пост-хука рендера → публикация Telegram](#3-контракт-пост-хука-рендера--публикация-telegram)
4. [Контракт гарантий идемпотентности](#4-контракт-гарантий-идемпотентности)
5. [Контракт изоляции сбоев](#5-контракт-изоляции-сбоев)
6. [Границы — что этот документ не покрывает](#6-границы--что-этот-документ-не-покрывает)

---

## 1. Точка входа — `POST /editor/song/approve`

Существующий эндпоинт. **Параметры и ответ не меняются.** Поведение
back-end'а расширяется:

| Параметр | Источник | Без изменений? |
|---|---|---|
| `id: Long` (path или query) | клиент (`SubsEdit.vue`) | да |
| `target: String?` | клиент | да |
| Markers/text payloads | клиент | да |

| Поле ответа | Тип | Что меняется |
|---|---|---|
| `ok`, `status`, `error` | map | **не меняется** — формат ответа не трогаем (см. A-007, обратная совместимость). |
| Side-effects на сервере | — | расширяются: новые строки в `tbl_processes`, новые строки в `tbl_news`, новый Telegram-пост, sync `tbl_pictures/authors/albums`. |

---

## 2. Контракт оркестрации в `approve()`

### 2.1 Предусловия (до этой фичи, гарантируются существующим кодом)

- `song.id` валиден (иначе earlier `return@withDb … error`).
- Маркеры/текст уже применены к `song` (блок до 374 строки файла `SongEditorController.kt`).
- `song.fields[SongField.ID_STATUS] = "6"` уже выставлен и `saveToDb()` уже выполнен.
- После существующего `updateRemoteSongFromLocalDatabase(song.id)` — `tbl_songs`
  на сервере уже содержит обновлённую строку с `newsAvailableAnnounced=true`.
  Серверная `MainController.doChangeRecords` либо уже, либо **в ближайшие секунды**
  применит эту строку и создаст новость — это вне нашего управления.

### 2.2 Шаги, добавляемые фичей

Выполняются **строго в этом порядке** после существующего
`updateRemoteSongFromLocalDatabase(song.id)` и до существующего `aRead.save()`:

```text
Шаг 1. triggerRenderMp4DemoIfNeeded(song)
        │
        ├─ SELECT process_status, id FROM tbl_processes
        │    WHERE song_id = ? AND process_type = 'RENDER_MP4_DEMO'
        │      AND process_status IN ('WAITING','WORKING')
        │
        ├─ если есть хотя бы одна строка → skip
        │
        └─ если нет → KaraokeProcess.createProcess(
                song = song,
                action = KaraokeProcessTypes.RENDER_MP4_DEMO,
                doWait = false,
                prior = 5,
                threadId = 0,             // HEAVY_RENDER lane
            )

Шаг 2. thread {
            try {
                val pushStart = System.currentTimeMillis()
                val result = updateRemoteDatabaseFromLocalDatabase(
                    updateSongs = false,    // tbl_songs уже пушнута выше
                    updatePictures = true,
                    updateAuthors = true,
                )
                println(
                    "[approve/sync-related] push related на SERVER: " +
                    "${System.currentTimeMillis() - pushStart} ms, " +
                    "created=${result.created.size} updated=${result.updated.size}"
                )
            } catch (e: Exception) {
                println(
                    "[approve/sync-related] ошибка sync related: ${e.message}"
                )
            }
        }

Шаг 3. (не меняем, существующий код) aRead.save()  // assignment → approved
```

### 2.3 Постусловия

| Что должно произойти после approve (SC из `spec.md`) | Как гарантируется |
|---|---|
| SC-001: DEMO-MP4 существует на диске после ≤10 мин | Шаг 1 создаёт процесс; воркер берёт, рендерит, выставляет `DONE`. |
| SC-002: Telegram-пост появился в канале в течение ≤60 с после DEMO-MP4 | Пост-хук в `KaraokeProcessThread` (см. контракт 3) вызывает `publishToTelegram` сразу после `DONE`. |
| SC-003: HTTP-ответ approve возвращается в течение ≤5 с после клика | Длинные операции (Шаг 2 и публикация Telegram) выполняются в `thread { ... }`, не блокируют HTTP-поток. Сам `createProcess` — лёгкая запись в `tbl_processes`, единицы мс. |
| SC-004: новость «появилась в коллекции» видна на сервере после sync | Контракт `updateRemoteSongFromLocalDatabase(song.id)` уже обеспечивает; этот пункт — инвариант существующего кода. |
| FR-005: `tbl_pictures/authors/albums` засинканы (best-effort) | Шаг 2 в `thread`. |

### 2.4 Что НЕ меняется в `approve()`

- Pre-existing блоки 350-388 (применение markers/text, выставление idStatus=6,
  `updateRemoteSongFromLocalDatabase(song.id)`).
- Финальный `aRead.save()`.
- Формат ответа (map `ok`/`status`/`error`).
- Логика gate'а «уже одобрено → return with same status» (см. specs/094/095).
- Хэндлинг ошибок DB-соединения в `withDb` (выше начала approve-блока).

---

## 3. Контракт пост-хука рендера → публикация Telegram

### 3.1 Точка вызова

`KaraokeProcessThread.run()` — место, где процесс завершается (status становится
`DONE`/`ERROR`, `priority = 999` или `-1`).

### 3.2 Условие срабатывания

```kotlin
if (karaokeProcess.type == "RENDER_MP4_DEMO" &&
    karaokeProcess.status == KaraokeProcessStatuses.DONE.name
) {
    thread {
        try {
            val song = Song.loadFromDbById(
                id = karaokeProcess.songId,
                database = WORKING_DATABASE,
                storageService = KSS_APP,
                storageApiClient = SAC_APP,
            ) ?: return@thread

            TelegramAutoPublishService.publishToTelegram(
                song = song,
                allowPastDate = true,
                publicationType = PublicationType.AIR,
                persistMessageId = true,
            )
        } catch (e: Exception) {
            println("[render-demo/post-hook] ошибка публикации: ${e.message}")
        }
    }
}
```

### 3.3 Предусловия (для самой публикации, гарантируются `publishToTelegram`)

- `song.idTelegramDemo` — пусто (иначе ранний `return PUBLISHED`).
- `song.isContentReady == true` (иначе ранний `return SCHEDULED`).
- Демо-файл существует и ≤50 МБ (иначе `publishToTelegram` сам поставит **новый**
  процесс рендера и сделает вид «опоздавшая отправка через `onRenderCompleted`»).

### 3.4 Постусловия

| Что должно произойти | Как гарантируется |
|---|---|
| Telegram-пост опубликован в канале | `publishToTelegram` → `publishFile` → `TelegramApiClient.sendVideo`. |
| `tbl_songs.id_telegram_demo` заполнен | `persistMessageId=true` сохраняет `msgId` через `song.saveToDb()`. |
| `tbl_songs.telegram_auto_publish_state = PUBLISHED` | Обычный путь внутри `publishToTelegram`. |

### 3.5 Что НЕ делается в пост-хуке

- Не отправляем повторно, если файл уже отправлялся (гард `idTelegramDemo`).
- Не пытаемся принудительно отправить для не-ready песни (гард `isContentReady`).
- Не дёргаем `VkAutoPublish*` (VK идёт плановым `VkAutoPublishScheduler`).
- Не вызываем `publishToTelegram` для `RENDER_MP4_LYRICS/KARAOKE/CHORDS/TABS` —
  только для `RENDER_MP4_DEMO`.

---

## 4. Контракт гарантий идемпотентности

| Уровень | Что проверяется | Где |
|---|---|---|
| 1. Не создавать лишний `RENDER_MP4_DEMO`-процесс | `SELECT … WHERE process_status IN ('WAITING','WORKING')` | approve, Шаг 1 |
| 2. Не публиковать второй Telegram-пост | `song.idTelegramDemo.isNotEmpty()` → early-return | `publishToTelegram` |
| 3. Не создавать лишнюю `tbl_news` | `wasAvailableBefore=true` пропускается, либо check `existsAnnouncement` | `SongReleaseAnnouncementService.detectAndAnnounceAvailability` |
| 4. Sync дифф-идемпотентен | `tbl_settings` recordhash diff, `apply recordhash` | `updateDatabases` / `tbl_settings_sync` |

Повторное нажатие approve на уже одобренную песню:

- Шаг 1: уже есть процесс — skip.
- Шаг 2: bulk-sync делает diff, неизменённых записей не пушит.
- Шаг 3: `aRead.save()` возвращает `already_approved` (existing behavior).
- Пост-хук: если процесс каким-то образом завершился и файл ещё не отправлен —
  `publishToTelegram` отправит (один раз; второй пропустит по `idTelegramDemo`).

Идемпотентность также зависит от уже существующих свойств системы:
запись `tbl_processes` создаётся заново каждым `createProcess` (апсерт по `(id)`,
а не upsert), так что в редком случае после зависания процесса между
`WAITING` и `WORKING` (не должно быть при нормальной работе воркера, но
диагностируется через HealthReport) возможен «лишний» процесс — это
**существующее** поведение, фича его не меняет.

---

## 5. Контракт изоляции сбоев

| Сценарий | Поведение |
|---|---|
| DB-соединение в `updateRemoteSongFromLocalDatabase(song.id)` упало | Существующий код имеет блок `try { ... } catch (_: Exception) { println(...) }` (см. файл:389-400 строки). Approve возвращает `ok=true` несмотря на сбой синка. Наша новая логика выполняется уже после него. |
| `createProcess` выбросил (нет записи в `tbl_processes`) | Шаг 1 — в `try { ... } catch (e: Exception) { println("[approve/render-demo] ошибка: ${e.message}") }`. Approve всё равно возвращает `ok=true`. |
| Sync-related бросил (Шаг 2) | Уже внутри `thread { try {...} catch {} }` — не пробрасывается. Approve всё равно возвращает `ok=true`. |
| Telegram-публикация выбросила | Внутри `thread { try {...} catch {} }` в пост-хуке. Не влияет ни на статус процесса, ни на HTTP-ответ approve. |
| Worker погиб между `WORKING` и `DONE` | Существующий код ставит задаче `ERROR` через общий exception-handler — отрабатывает тот же пост-хук HealthReport (а не наш — наш срабатывает только при `status='DONE'`). Telegram-пост опубликует scheduler на следующем тике. |

Гарантия: **никакое исключение внутри approve-блока не должно приводить к
откату уже выполненного `song.saveToDb()` со сменой `id_status` на `6`**.
Это достигается тем, что:

1. `song.saveToDb()` (`idStatus = 6`) уже произошёл до наших новых шагов.
2. Каждый наш шаг обёрнут в свой `try { ... } catch (_: Exception) { println(...) }`.
3. `aRead.save()` в конце approve закоммитит факт одобрения независимо от
   исхода наших шагов (так же, как и сейчас при сбое
   `updateRemoteSongFromLocalDatabase`).

---

## 6. Границы — что этот документ не покрывает

- **HTTP-эндпоинты публичного сайта** (`karaoke-web`, `karaoke-public`) —
  фича их не трогает.
- **Админ-таблица `/processes`** (`webvue3`) — рендер появится там автоматически
  без изменений в Vue (используются стандартные SSE-обновления).
- **Админ-таблица `/news`** (`webvue3`) — новость появится там автоматически
  через `SseNotification.recordChange`/`crud`.
- **Премиум-цикл** (PREMIUM-канал) — вне scope фичи (D-6 в research.md).
- **VK-публикация** — вне scope фичи (спека требует только Telegram-канал как
  реактивный шаг после approve; VK идёт плановым `VkAutoPublishScheduler`).
- **Backfill и миграции данных** (`specs/124-news-flags-backfill`) — фича не
  требует ничего от них.
