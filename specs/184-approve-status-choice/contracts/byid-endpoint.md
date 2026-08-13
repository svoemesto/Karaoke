# Contract: `POST /api/songeditor/byId` (дельта для feature 184)

**Feature**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md) | **Research**: [research.md](../research.md) D-5

Базовый эндпоинт `SongEditorController.byId`
(`SongEditorController.kt:266-297`) — **изменения чисто аддитивные**: одно
новое поле в JSON-ответе. Маршрут, метод, обязательные параметры, остальные
поля — без изменений.

## Request

Без изменений:

```
POST /api/songeditor/byId
Content-Type: application/x-www-form-urlencoded

id=<Long>             # id записи tbl_song_assignments
target=remote|<пусто> # опционально; "remote" — читать задание/черновик с Connection.remote()
```

## Response — ДО фикса (текущее поведение, baseline)

```jsonc
{
  "id": <Long>,
  "assigneeId": <Long>,
  "assigneeEmail": "<string>",
  "assigneeName": "<string>",
  "songId": <Long>,
  "songName": "<string>",
  "author": "<string>",
  "album": "<string>",
  "year": <Int>,
  "status": "<assignmentStatus.dbValue>",
  "adminStatus": "<SongAssignmentStatus>",
  "reviewComment": "<string>",
  "assignedAt": <Timestamp|null>,
  "reviewedAt": <Timestamp|null>,
  "submittedAt": <Timestamp|null>,
  "draftSourceTexts": ["<string>", ...],
  "draftMarkersPerVoice": [[<Marker>, ...], ...]
}
```

## Response — ПОСЛЕ фикса (дельта этой спеки)

Одно новое поле:

```diff
   "submittedAt": <Timestamp|null>,
+  "idStatus": <Long>,    // NEW: текущий id_status ПЕСНИ (не задания!)
   "draftSourceTexts": ["<string>", ...],
   "draftMarkersPerVoice": [[<Marker>, ...], ...]
 }
```

**`idStatus`** — текущее значение `tbl_songs.id_status` для песни
`songId` данного задания. Источник — уже загруженный в `byId` объект
`Song s` (`SongEditorController.kt:276`). Никаких дополнительных SQL-запросов.

Значение: `s?.idStatus ?: 0L`. Поле всегда присутствует (Jackson не сериализует
`null` для ненулевых Long), но при `s == null` будет `0L` (несуществующая
песня — `songId` остаётся в ответе, но маркеров не будет).

**Обратная совместимость**: поле аддитивное. Существующие потребители
(`ReviewModal.vue`, `SongsTable.vue`, `SongEdit.vue`) получают лишнее поле
в JSON, которое не читают — `JSON.parse` в `store.js:117-121` не упадёт,
`getAssignmentCurrent` — просто объект, `ReviewModal` не обращается к `a.idStatus`
в существующем коде. Никаких правок в вызывающих компонентах не требуется.

## Связь с другими эндпоинтами

- `/byId` теперь возвращает `idStatus` песни **синхронно с заданием** —
  ровно для того, чтобы `ReviewModal` мог показать radio-group (US2) без
  дополнительного round-trip.
- Статус ЗАДАНИЯ (поле `status`, `"На проверке"` / `"approved"` / ...) — **не
  путать** со статусом ПЕСНИ (`idStatus`, `0..6`). Первое — это
  `SongAssignmentStatus.resolve(adminStatus, userStatus, reviewedAt, submittedAt)`,
  второе — `tbl_songs.id_status`. В ответе присутствуют оба.

## Инварианты контракта (проверяются в quickstart.md)

- **INV-B1**: ответ содержит поле `idStatus` (`Long`), значение соответствует
  `SELECT id_status FROM tbl_songs WHERE id = <songId>` в той же БД, из которой
  прочитан `Song s` (LOCAL при `target` пустом/null/`local`, REMOTE при
  `target=remote`).
- **INV-B2**: для НЕсуществующего `songId` (`s == null`) — `idStatus = 0L` (не
  падает в NPE; старая логика `s?.songName ?: ""` уже так делает).
- **INV-B3**: запрос без параметров (старый клиент) — без изменений в поведении
  (создание нового клиента не требуется; это «запрос данных», не апдейт).
- **INV-B4**: ни одно из существующих полей ответа не удалено, не переименовано,
  не меняет тип.
