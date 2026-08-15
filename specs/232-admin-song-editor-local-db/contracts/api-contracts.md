# Contracts: 232-admin-song-editor-local-db

**Feature**: 232-admin-song-editor-local-db
**Date**: 2026-08-15

## Затронутые контракты

Два HTTP-эндпоинта в `SongEditorController`
(`karaoke-app/.../controllers/SongEditorController.kt`):

1. `POST /api/songeditor/edit/byId` — открыть песню/задание в редакторе.
2. `POST /api/songeditor/edit/save` — сохранить правки текста/маркеров.

Оба используются webvue3 (`webvue3/.../SongKaraokeEditorModal.vue:176,257`),
а `/edit/byId` дополнительно — `webvue3/.../SongEdit.vue` через тот же URL.

Контракт фиксируется **до и после** фичи для ясности.

---

## `POST /api/songeditor/edit/byId`

### До фичи

**Запрос** (form-encoded params):
| Поле | Тип | Обязательность | Описание |
|------|-----|----------------|----------|
| `id` | long | required | ID песни (mode='song') или ID задания (mode='assignment') |
| `mode` | string | optional, default `"song"` | `"song"` или `"assignment"` |
| `target` | string | optional | `"local"` или `"remote"` — определяет БД для задания/черновика |

**Поведение**: `Song` всегда читается из `WORKING_DATABASE` (глобал karaoke-app, на admin-машине = LOCAL). Задание/черновик — из `db = withDb(target)`.

**Ответ 200** (mode='song'):
```json
{
  "found": true,
  "mode": "song",
  "id": 123,
  "songId": 123,
  "songName": "...",
  "author": "...",
  "album": "...",
  "year": 2024,
  "track": 1,
  "key": "Am",
  "bpm": 120,
  "voiceCount": 2,
  "sourceTexts": ["voice 0 text", "voice 1 text"],
  "markersPerVoice": [[{...}], [{...}]],
  "audioVocalsUrl": "/api/song/123/filevoice.mp3",
  "audioAccompanimentUrl": "/api/song/123/fileminus.mp3",
  "audioBassUrl": null,
  "audioDrumsUrl": null,
  "albumImageUrl": "/api/picture/file?file=...",
  "artistImageUrl": "/api/picture/file?file=...",
  "exportBaseName": "Author - Album - Track [id-123]",
  "canEdit": true,
  "assignmentId": null,
  "reviewComment": "",
  "status": "song"
}
```

**Ответ 200** (Song не найден):
```json
{ "found": false, "id": 123, "songId": 123 }
```

### После фичи

**Запрос** — без изменений (контракт не ломается).

**Поведение**: `Song` всегда читается из `Connection.local()` (явно LOCAL-БД). Параметр `target` игнорируется для mode='song' (для mode='assignment' — по-прежнему target-aware).

**Ответ** — структура JSON не меняется. Опционально (FR-005 спеки):
- Если Song отсутствует в LOCAL-БД, возвращается **отличимый** код ошибки `song_not_found_in_local_db` в теле (вместо/дополнительно к `found=false`). Это опциональное улучшение UX, не блокер. Если не реализовано — текущее поведение `found=false` сохраняется.

**Обратная совместимость**: ✅ фронтенд (`SongKaraokeEditorModal.vue:179-205`) читает `body.found`, `body.songId`, `body.sourceTexts`, `body.markersPerVoice` — все эти поля остаются на месте и в том же формате.

---

## `POST /api/songeditor/edit/save`

### До фичи

**Запрос** (form-encoded params):
| Поле | Тип | Обязательность | Описание |
|------|-----|----------------|----------|
| `id` | long | required | ID песни (mode='song') или ID задания (mode='assignment') |
| `mode` | string | optional, default `"song"` | `"song"` или `"assignment"` |
| `target` | string | optional | `"local"` или `"remote"` — определяет БД для чтения/записи |
| `sourceTexts` | string (JSON array of strings) | required | Тексты по голосам |
| `markersPerVoice` | string (JSON array of arrays of SourceMarker) | required | Маркеры по голосам |

**Поведение** (mode='song'):
1. `db = withDb(target)` → `Connection.local()` или `Connection.remote()` в зависимости от target.
2. `Song.loadFromDbById(id, db, …)`.
3. Для каждого голоса: `song.setSourceMarkers(v, markers)` + `song.setSourceText(v, text)` → `saveToDb()` внутри пишет в `db`.
4. При необходимости: `song.truncateVoicesTo(parsedMarkers.size)`.

**Ответ 200** (успех):
```json
{ "ok": true, "voiceCount": 2, "idStatus": 5 }
```

**Ответ 200** (Song не найден):
```json
{ "ok": false, "error": "song_not_found" }
```

**Ответ 200** (невалидный mode):
```json
{ "ok": false, "error": "bad_mode" }
```

**Ответ 200** (невалидный payload):
```json
{ "ok": false, "error": "bad_payload" }
```

### После фичи

**Запрос** — без изменений (контракт не ломается). Параметр `target` остаётся в сигнатуре, но для mode='song' **игнорируется** серверной стороной при выборе БД.

**Поведение** (mode='song'):
1. `db = Connection.local()` (явно LOCAL-БД).
2. `Song.loadFromDbById(id, db, …)`.
3. Далее без изменений (`setSourceMarkers`/`setSourceText` → `saveToDb` в `db`).

**Поведение** (mode='assignment') — без изменений (target-aware через `withDb(target)`).

**Ответ** — структура JSON не меняется. `ok=true/false`, `error="song_not_found"` и т.п. — всё на месте.

**Обратная совместимость**: ✅ фронтенд (`SongKaraokeEditorModal.vue:264-265`) проверяет `body && body.ok === true` для индикатора «Сохранено ✓». Это поле не меняется. Если Song не найден в LOCAL-БД — вернётся `ok=false, error="song_not_found"` (как и раньше при target='local'), фронт покажет «Ошибка сохранения» (как и раньше).

---

## Что НЕ меняется в контрактах

- `POST /api/songeditor/digest`, `/statusbysongids`, `/assign`, `/revoke`, `/delete`, `/approve`, `/reject` — все эти target-aware эндпоинты остаются **без изменений**. См. комментарий в шапке `SongEditorController.kt:47-56` и спеку FR-004.
- Параметр `target` остаётся в сигнатуре `/edit/byId` и `/edit/save` (обратная совместимость), но для `mode='song'` **не влияет** на выбор БД.
- Структура JSON-ответов, имена полей, HTTP-статусы (200 OK для всех ответов контроллера, включая ошибки — это согласуется с тем, как `SongKaraokeEditorModal.vue:264-265` их обрабатывает).
- Семантика `mode='assignment'` — задания и черновики по-прежнему target-aware.

## Что меняется

- Внутренняя реализация двух методов: вместо `WORKING_DATABASE` (глобал) и `withDb(target)` (для mode='song') используется `Connection.local()` напрямую.
- Опционально (FR-005): отличимый `error="song_not_found_in_local_db"` в `editById` при отсутствии Song в LOCAL-БД.
