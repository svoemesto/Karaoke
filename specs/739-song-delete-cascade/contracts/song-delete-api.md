# Contract: API удаления песни с каскадной уборкой

**Фича**: `739-song-delete-cascade` | **Дата**: 2026-11-12 | **Спека**: [`spec.md`](../spec.md)
**План**: [`plan.md`](../plan.md) | **Data model**: [`data-model.md`](../data-model.md)

> Phase 1 (`/speckit.plan`). Контракт внешних интерфейсов фичи: админский HTTP-API
> (`karaoke-app`) и HTTP-контракт фоновой задачи уборки. Область — только
> `karaoke-app` (Principle V: админка `webvue3` и публичная часть `karaoke-public` —
> разные приложения; публичное поведение не меняется — FR-044).

---

## 1. Перечень эндпоинтов

| # | Метод | Путь | Назначение | Коды |
|---|---|---|---|---|
| 1 | POST | `/api/song/deletepreview` | предпросмотр: что будет удалено (FR-023…FR-027) | 200, 400, 404, 409, 503 |
| 2 | POST | `/api/song/delete` | удаление песни (FR-001…FR-009) | 200, 400, 404, 409, 503 |
| 3 | GET | `/api/song/cleanup/runs` | список прогонов уборки (FR-036) | 200 |
| 4 | GET | `/api/song/cleanup/runs/{runId}` | прогон + записи журнала (FR-034…FR-036) | 200, 404 |
| 5 | POST | `/api/song/cleanup/runs/{runId}/resume` | ручной добор остатка (FR-033) | 200, 404, 409 |
| 6 | POST | `/api/song/cleanup/one-time/plan` | разовая уборка: перечень кандидатов с размерами и отпечатком набора (FR-039…FR-042) | 200, 503 |
| 7 | POST | `/api/song/cleanup/one-time/apply` | разовая уборка: применение строго по утверждённому отпечатку (FR-040) | 200, 400, 409, 503 |
| 8 | GET | `/api/song/cleanup/one-time/status` | разовая уборка: сводка по последнему проходу (FR-043, SC-007) | 200, 404 |

Все эндпоинты — в admin-контуре `karaoke-app` (`@RequestMapping("/api")`,
`ApiController`), доступны любому администратору с текущими правами (FR-028).
Отдельная роль не вводится.

### 1.1 Эндпоинты разовой уборки (US4)

Разовое удаление осиротевших артефактов (FR-039…FR-043) — отдельный сценарий с
протоколом утверждённого набора. Реализуется задачей T051; каждое удаление
фиксируется в `tbl_song_cleanup_entries` с `run_kind = ONE_TIME_CLEANUP` (FR-043).

**Запрос `POST /api/song/cleanup/one-time/apply`**: `{"fingerprint": "<отпечаток набора>"}`.
Поле `fingerprint` обязательно — без него `400 not_confirmed`. Если состав набора
изменился (кандидат появился или исчез), ответ `409 one_time_set_changed`: уборка
не выполняется, требуется новое утверждение владельцем (FR-040).

**Ответ `POST /api/song/cleanup/one-time/plan` (200)**:

```json
{
  "fingerprint": "<отпечаток набора>",
  "total_bytes": 0,
  "candidates": [
    {"kind": "ORPHAN", "location": "<путь в хранилище или на диске>", "bytes": 0, "reason": "<причина>"}
  ]
}
```

`kind` — `ORPHAN` / `STALE_COPY` / `UNKNOWN`. Кандидаты, совпадающие с именем живой
песни после нормализации написания, в перечень **не** попадают (FR-042).

**Ответ `GET /api/song/cleanup/one-time/status` (200)**:

```json
{"run_id": "<id>", "state": "DONE", "entries": 0, "bytes_deleted": 0, "finished_at": "<ISO-8601>"}
```

Сумма `bytes_deleted` по проходам используется для проверки SC-007 (освобождено не
менее 10 ГБ). Ответ `404` — проходов разовой уборки ещё не было.

---

## 2. `POST /api/song/deletepreview`

### Запрос

`application/x-www-form-urlencoded` (конвенция проекта — `promisedXMLHttpRequest`):

| Параметр | Тип | Обязателен | Описание |
|---|---|---|---|
| `id` | long | да | id песни |

### Ответ 200

```json
{
  "ok": true,
  "songId": 30428,
  "songName": "Ночной дозор",
  "author": "Автор",
  "year": 1996,
  "album": "Альбом",
  "rootFolder": "/sm-karaoke/work/Автор/1996 - Альбом",

  "filesCount": 37,
  "filesBytes": 512345678,
  "filesMb": 488.6,

  "localStorageObjects": 12,
  "remoteStorageObjects": 12,
  "storageBytes": 104857600,

  "albumWillBeDeleted": true,
  "albumPictureWillBeDeleted": true,
  "albumFolderWillBeDeleted": false,
  "albumFolderReason": "album_has_live_songs",
  "albumFolderLeftoverFiles": 2,

  "descendantsCount": 2,
  "newsCount": 3,
  "linksCount": 1,
  "playlistItemsCount": 4,
  "assignmentsCount": 1,
  "draftsCount": 2,
  "historyCount": 158,

  "blocked": false,
  "blockReason": null,
  "previewId": "pv-7c1f0a9e"
}
```

### Поля ответа и их источник

| Поле | Источник | FR |
|---|---|---|
| `filesCount`, `filesBytes`, `filesMb` | обход `rootFolder` + подпапок артефактов, правило владения (R-003) | FR-023 |
| `localStorageObjects`, `remoteStorageObjects`, `storageBytes` | листинг бакета с префиксом `$автор/$год - $альбом/` в обоих хранилищах | FR-024 |
| `albumWillBeDeleted` | после удаления песни в альбоме не остаётся живых песен | FR-025 |
| `albumPictureWillBeDeleted` | у альбома есть запись в `tbl_pictures` | FR-019, FR-025 |
| `albumFolderWillBeDeleted` | в папке альбома нет файлов живых песен | FR-020, FR-021 |
| `albumFolderReason` | `album_has_live_songs` \| `empty` | FR-021 |
| `albumFolderLeftoverFiles` | файлы в папке альбома, не относящиеся к песне: уйдут вместе с альбомом | FR-020, FR-021 |
| `descendantsCount` | `tbl_songs.audio_parent_id = id` | FR-026 |
| `newsCount`, `linksCount`, `playlistItemsCount`, `assignmentsCount`, `draftsCount`, `historyCount` | счётчики связанных записей | FR-025, FR-026 |
| `blocked`, `blockReason` | `process_running` (FR-004) \| `not_owned` (R-009) | FR-004 |

**Точность (SC-004).** `filesCount` и `filesBytes` считаются **тем же** кодом
владения, что и уборка; расхождение между предпросмотром и фактом — 0.

### Ошибки

| Код | Условие | Тело |
|---|---|---|
| 400 | `id` не число | `{ "ok": false, "error": "bad_request" }` |
| 404 | песни нет | `{ "ok": false, "error": "song_not_found" }` |
| 409 | идёт фоновая обработка | `{ "ok": false, "error": "process_running", "message": "…" }` |
| 409 | запись вне проекта | `{ "ok": false, "error": "not_owned", "message": "…" }` |
| 503 | фича выключена флагом | `{ "ok": false, "error": "song_delete_disabled" }` |
| 503 | хранилище недоступно | `{ "ok": false, "error": "storage_unavailable" }` |

---

## 3. `POST /api/song/delete`

### Запрос

| Параметр | Тип | Обязателен | Описание |
|---|---|---|---|
| `id` | long | да | id песни |
| `confirm` | boolean | нет (по умолчанию `true`) | подтверждение; `false` — отказ, ничего не удалено |

### Ответ 200 (успех)

```json
{
  "ok": true,
  "status": "deleted",
  "songId": 30428,
  "deletedLocal": true,
  "deletedServer": true,
  "runId": 12345,
  "message": "Песня удалена, уборка запущена в фоне"
}
```

| Поле | Смысл |
|---|---|
| `status` | `deleted` (полностью) \| `partially_deleted` (LOCAL удалена, SERVER недоступна) |
| `deletedLocal` | строка и связанные записи удалены в LOCAL-БД |
| `deletedServer` | строка удалена в SERVER-БД |
| `runId` | идентификатор прогона уборки (журнал, FR-035) |

### Ответ 200 (идемпотентный повтор)

Повторное удаление уже удалённой песни — **успех**, а не ошибка (US1-edge,
FR-002 «отсутствие такой песни»):

```json
{ "ok": true, "status": "already_deleted", "songId": 30428, "deletedLocal": true, "deletedServer": true, "runId": null }
```

### Ответ 200 (частично)

```json
{
  "ok": true,
  "status": "partially_deleted",
  "songId": 30428,
  "deletedLocal": true,
  "deletedServer": false,
  "serverError": "server_db_unavailable",
  "runId": 12345,
  "message": "Песня удалена локально; удаление на серверной БД — в очереди на добор"
}
```

Это и есть требование FR-045: «если серверная база недоступна, результат удаления
MUST отражать это, а не показывать успех».

### Ошибки

| Код | Условие | Тело | FR |
|---|---|---|---|
| 400 | `id` не число | `{ "ok": false, "error": "bad_request" }` | — |
| 404 | песни нет **и** это не повтор (нет прогона) | `{ "ok": false, "error": "song_not_found" }` | FR-002 |
| 409 | идёт фоновая обработка | `{ "ok": false, "error": "process_running", "message": "…" }` | FR-004 |
| 409 | запись вне проекта | `{ "ok": false, "error": "not_owned", "message": "…" }` | FR-011, FR-046 |
| 409 | `confirm = false` | `{ "ok": false, "error": "not_confirmed" }` | FR-027 |
| 500 | отказ LOCAL-БД | `{ "ok": false, "error": "db_error", "message": "…" }` | FR-002, SC-003 |
| 503 | фича выключена флагом | `{ "ok": false, "error": "song_delete_disabled" }` | — |

**Изменение контракта (обратная несовместимость).** Текущий эндпоинт отвечает
`200` с пустым телом при любом исходе, включая отказ БД
(`ApiController.doDeleteSong`, `ApiController.kt:4357`; `Song.deleteFromDb`
возвращает `Unit` и глотает исключения — `Song.kt:6898`). Новый контракт отдаёт
явный результат. Клиент `webvue3` (`Songs/store.js` → `deleteCurrentSong`)
уже ждёт ответа и не закрывает карточку при отказе (BR-127) — правки фронта
нуждаются только в разборе нового тела.

---

## 4. `GET /api/song/cleanup/runs`

### Запрос

| Параметр | Тип | Обязателен | Описание |
|---|---|---|---|
| `songId` | long | нет | фильтр по песне |
| `limit` | int | нет (50) | сколько прогонов вернуть |
| `offset` | int | нет (0) | смещение |

### Ответ 200

```json
{
  "ok": true,
  "total": 3,
  "runs": [
    {
      "runId": 12345,
      "songId": 30428,
      "songName": "Ночной дозор",
      "runKind": "SONG_DELETE",
      "state": "RUNNING",
      "initiator": "admin",
      "startedAt": "2026-11-12T13:40:00",
      "finishedAt": null,
      "filesTotal": 37, "filesDeleted": 20, "filesKept": 17, "filesFailed": 0,
      "objectsTotal": 24, "objectsDeleted": 12, "objectsKept": 12, "objectsFailed": 0,
      "bytesDeleted": 104857600,
      "error": null
    }
  ]
}
```

---

## 5. `GET /api/song/cleanup/runs/{runId}`

### Ответ 200

```json
{
  "ok": true,
  "run": { "...": "как в списке" },
  "entries": [
    {
      "id": 1,
      "runId": 12345,
      "kind": "FILE",
      "location": "LOCAL_FILESYSTEM",
      "pathOrKey": "/sm-karaoke/work/Автор/1996 - Альбом/Ночной дозор.flac",
      "sizeBytes": 52428800,
      "result": "DELETED",
      "reason": null,
      "attempt": 1,
      "createdAt": "2026-11-12T13:40:03",
      "initiator": "admin"
    },
    {
      "id": 2,
      "runId": 12345,
      "kind": "STORAGE_OBJECT",
      "location": "REMOTE_STORAGE",
      "pathOrKey": "Автор/1996 - Альбом/Ночной дозор.vocals.mp3",
      "sizeBytes": 4194304,
      "result": "FAILED",
      "reason": "storage_unavailable",
      "attempt": 5,
      "createdAt": "2026-11-12T13:41:12",
      "initiator": "admin"
    },
    {
      "id": 3,
      "runId": 12345,
      "kind": "FILE",
      "location": "LOCAL_FILESYSTEM",
      "pathOrKey": "/sm-karaoke/work/Автор/1996 - Альбом/Ночной дозор (ручная правка).png",
      "sizeBytes": 102400,
      "result": "KEPT",
      "reason": "not_recognized",
      "attempt": 1,
      "createdAt": "2026-11-12T13:40:04",
      "initiator": "admin"
    }
  ]
}
```

Поля журнала — ровно FR-035: путь или ключ, размер, результат, дата, инициатор,
идентификатор прогона.

---

## 6. `POST /api/song/cleanup/runs/{runId}/resume`

### Ответ 200

```json
{
  "ok": true,
  "runId": 12345,
  "state": "APPROVED",
  "message": "Прогон поставлен в очередь на добор остатка"
}
```

### Ошибки

| Код | Условие |
|---|---|
| 404 | прогона нет |
| 409 | прогон в состоянии `RUNNING` (уже идёт) или `DONE` (остатка нет) |
| 503 | фича выключена флагом |

---

## 7. Контракт фоновой задачи уборки

Задание очереди `KaraokeProcess`:

| Поле | Значение |
|---|---|
| `process_type` | `SONG_DELETE_CLEANUP` (новое значение `KaraokeProcessTypes`) |
| `thread_id` | `-1` (`THREAD_LANE_LIGHT_BACKGROUND`) |
| `song_id` | id удаляемой песни |
| `process_chain_id` | id кортежа (= `id` его головы, см. примечание ниже) |
| `process_args` | `[["runFunctionWithArgs", "songDeleteCleanup", "songId=<id>", "runId=<runId>"]]` — **связь журнала с заданием** |
| `process_status` | `CREATING` → `WAITING` → `WORKING` → `DONE` \| `ERROR` |

**Связь прогона журнала с заданием — через `runId` в аргументах, а не через
`process_chain_id`.** `process_chain_id` у кортежа — резерв очереди: он обязан
совпадать с `id` головы кортежа (`KaraokeProcess.markAsHead`:
`SET process_chain_id = id, process_order = 0`; выбор головы — условие
`process_chain_id = id`). Подстановка туда `id` прогона из другого пространства
идентификаторов дала бы коллизию с чужой головой и «неисполнимое задание»:
задание никогда не было бы выбрано в работу. Поэтому `runId` передаётся
аргументом задания (`process_args`), а сам прогон лежит в
`tbl_song_cleanup_runs` и связан с журналом записей по `run_id`.

**Исполнение.** `KaraokeProcessWorker` при `args[0][0] == "runFunctionWithArgs"`
разбирает аргументы и вызывает исполнитель по типу задания: для
`SONG_DELETE_CLEANUP` — `executeSongDeleteCleanup(params)`
(`KaraokeProcessWorker.run`), а тот — `SongCleanupExecutor.execute(runId, songId,
initiator)`. Общий диспетчер `Utils.runFunctionWithArgs` (`Utils.kt:5047`) также
поддерживает функцию `songDeleteCleanup` — тем же исполнителем.

**Контракт функции** `songDeleteCleanup(params)`:

- параметры: `songId`, `runId`;
- порядок шагов (FR-031): **диск → хранилища → альбом и его обложка → папка
  альбома**;
- каждый шаг пишет запись в `tbl_song_cleanup_entries`;
- при недоступности хранилища — до 5 попыток с паузами 2s → 60s (FR-032);
- после исчерпания попыток — остаток пишется в журнал, прогон получает
  `PARTIAL` (FR-033);
- возврат: строка статуса; непустая — успех, пустая — остаток (по конвенции
  `runFunctionWithArgs`: успех → `"Success for 'songDeleteCleanup'"`).

**Восстановление после рестарта (FR-030).** Механизм
`KaraokeProcess.setWorkingToWaiting` при старте переводит `WORKING` → `WAITING`;
прогон уборки при этом остаётся в состоянии `RUNNING` и продолжается с
незавершённых шагов (шаги идемпотентны: удалённый файл/объект считается уже
удалённым, повторная попытка — `SKIPPED`, не `FAILED`).

---

## 8. Контракт веб-интерфейса (webvue3)

Существующий `CustomConfirm.vue` используется без изменений: окно подтверждения
(FR-027) — текстовый режим по умолчанию, экранирование пользовательского ввода
(`knowledge/system/frontend/confirm-escape.md`).

Тело окна (FR-023…FR-026):

```html
Удалить песню «Ночной дозор»?
Будет удалено: 37 файлов (488,6 МБ), 12 объектов в локальном хранилище,
12 объектов в удалённом хранилище.
Вместе с песней будет удалён альбом «Альбом» (обложка и папка не удаляются:
в папке есть файлы живых песен).
Родственных песен: 2 (связь с ними будет обнулена).
```

| Требование | Как закрыто |
|---|---|
| FR-023 | «37 файлов (488,6 МБ)» |
| FR-024 | «12 объектов в локальном хранилище, 12 объектов в удалённом» |
| FR-025 | строка про альбом (+ причина, если папка остаётся) |
| FR-026 | «Родственных песен: 2» |
| FR-027 | без дополнительного ввода, без лимитов |
| FR-003 | карточка закрывается сразу после подтверждения; признак фактического удаления (успех / частично / отказ) приходит уведомлением (правка владельца 2026-11-13) |

---

## 9. Контракт флага

| Флаг | Тип | По умолчанию | Смысл |
|---|---|---|---|
| `songDeleteEnabled` | boolean | `false` | включает конвейер удаления. Выключен → все эндпоинты фичи отвечают `503 song_delete_disabled` |

Объявление — в `KaraokeProperties` по конвенции проекта: запись в
`listKaraokeProperties` вида
`KaraokeProperty(key = "songDeleteEnabled", defaultValue = false, description = …)`
и чтение через `KaraokeProperties.getBoolean("songDeleteEnabled")`. Значение
хранится в файле свойств `/sm-karaoke/system/Karaoke.properties`
(`PATH_TO_KARAOKE_PROPERTIES_FILE`) и может быть изменено на лету через
существующий endpoint `/api/properties/setproperty`.

Дефолт `false` — как у существующих флагов автоуборки (`cleanupEnabled`,
`cleanupDryRun`): поведение системы при выключенном флаге идентично текущему.
Секретов нет, дефолт невалидный для включения (fail-safe).

Откат — установить `songDeleteEnabled=false`; откатывать миграции не требуется (R-010).

---

## 10. Обратная совместимость

| Потребитель | Изменение |
|---|---|
| `webvue3` (`Songs/store.js` → `deleteCurrentSong`) | тело ответа меняется с пустого на JSON; правка разбора обязательна |
| `karaoke-web` (публичная часть) | не меняется (FR-044) |
| `karaoke-public` | не меняется (FR-044) |
| `SyncTarget.kt` (`SongSyncTarget`) | не меняется; удаление строки песни на SERVER идёт отдельной фазой |
| `Utils.setSongToSyncRemoteTable` | не меняется |
| `Song.deleteFromDb` | меняется сигнатура (возвращает результат); 4 вызывающих в `Utils.kt`, `ApiController.kt`, `MainController.kt`, `SyncTarget.kt` требуют правки |
