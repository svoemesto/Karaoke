# Contract: API разовой чистки артефактов

**Feature**: [../spec.md](../spec.md) · **Issue**: [#321](http://localhost:8080/work_packages/321)

Контракт описывает HTTP-поверхность разовой чистки истории (`services/cleanup/`).
Базовый префикс — `/api/cleanup`; хост — бэкенд `karaoke-app`
(`http://localhost:8898`, прямой доступ; через nginx `:7906` эндпоинты не
проксируются — см. AGENTS.md § Pass 381).

Публичный сайт (`karaoke-web`) и фронтенды этот контракт не используют.

---

## Общие правила

1. **Ни один вызов `plan` не удаляет файлы.** Планировщик только читает.
2. **`apply` без отпечатка отчёта отклоняется** — утверждается конкретный набор файлов.
3. **Защищённые классы отклоняются всегда** (`PROTECTED`, `NOT_TOUCH`) — независимо
   от подтверждений.
4. **Работа идёт вне HTTP-потока** (Constitution IV): `apply` отвечает `202` и
   возвращает `runId`; прогресс — через `status`.
5. **Журнал пишется для каждого обработанного пути**, включая неудачные удаления.
6. Все ответы — `application/json`; ошибки — `{"error": "<код>", "message": "<текст>"}`.

---

## `GET /api/cleanup/classes`

Список классов из реестра.

**200**:

```json
{
  "classes": [
    {
      "classId": "DEMO_MP4",
      "title": "Демо-ролик публикации",
      "verdict": "DELETE_EXISTING",
      "protected": false,
      "cleanupOrder": 1,
      "expectedFileCount": 17885,
      "expectedBytes": 189700000000,
      "note": "удаляется в конце шага публикации начиная с этой поставки"
    }
  ]
}
```

---

## `POST /api/cleanup/plan`

Сухой прогон по классу. Тело: `{"classId": "DEMO_MP4"}`.

**200** — отчёт (`CleanupPlan` из [../data-model.md](../data-model.md) §3):

```json
{
  "planId": "9f1c…",
  "classId": "DEMO_MP4",
  "generatedAt": "2026-10-16T12:00:00Z",
  "fileCount": 17885,
  "totalBytes": 189741234567,
  "fingerprint": "sha256:6c1d…",
  "warnings": [],
  "sample": [
    {"path": "/sm-karaoke/done2/Lumen/2020 - Весна (сингл)/done_files/2020 (01) [Lumen] - Весна (2020) [demo].mp4",
     "sizeBytes": 6355850,
     "reason": "демо-ролик не хранится после публикации"}
  ]
}
```

Полный список путей доступен отдельным файлом-выгрузкой (отчёт на 17 885 строк не
кладётся в JSON ответа целиком): `GET /api/cleanup/plan/{planId}/entries.csv`.

**Ошибки**: `404` — неизвестный `classId`; `400` — класс защищён;
`503` — недоступен каталог журнала.

---

## `POST /api/cleanup/apply`

Запуск удаления по утверждённому отчёту.

```json
{
  "planId": "9f1c…",
  "fingerprint": "sha256:6c1d…",
  "approvedBy": "Сергей Новиков",
  "confirm": true
}
```

**202**:

```json
{"runId": "r-20261016-1", "classId": "DEMO_MP4", "state": "RUNNING",
 "plannedCount": 17885, "journalPath": "/sm-karaoke/system/cleanup-logs/2026-10-16-DEMO_MP4.tsv"}
```

**Ошибки**:

| Код | Когда |
|---|---|
| `400` | класс защищён; `confirm` не `true`; `approvedBy` пуст |
| `404` | неизвестный `planId` |
| `409` | `fingerprint` не совпадает с текущим набором файлов (состав изменился) → требуется новый `plan` |
| `409` | класс `STEMS_MP3_LOCAL` без выполненной правки модели здоровья (порядок работ, FR-004/FR-013) |
| `503` | каталог журнала недоступен для записи |

---

## `GET /api/cleanup/status?runId=…`

**200**:

```json
{"runId": "r-20261016-1", "classId": "DEMO_MP4", "state": "DONE",
 "plannedCount": 17885, "deletedCount": 17885, "failedCount": 0,
 "freedBytes": 189741234567,
 "journalPath": "/sm-karaoke/system/cleanup-logs/2026-10-16-DEMO_MP4.tsv"}
```

`state` ∈ `PLANNED|APPROVED|RUNNING|DONE|PARTIAL|FAILED` (см. `data-model.md` §5).

---

## Формат журнала

TSV, заголовок с колонками:

```
path\tsizeBytes\tclassId\tresult\tdeletedAt\tapprovedBy\trunId
```

`result` ∈ `deleted|failed:<причина>`. Файл дописывается построчно по мере обработки,
чтобы обрыв процесса не оставил «слепое» удаление (FR-012).

---

## Тестовые обязательства контракта

| Проверка | Ожидание |
|---|---|
| `plan` на защищённый класс (`SOURCE_FLAC`, `STEMS_FLAC`) | `400`, ни одного обращения к удалению |
| `apply` без `fingerprint` или с чужим отпечатком | `400` / `409`, ничего не удалено |
| `plan` не удаляет ни одного файла | после прогона число файлов в каталоге не изменилось |
| Журнал содержит неудачные удаления | `result=failed:…` присутствует, счётчик `failedCount` совпадает |
| `apply` для класса mp3 до правки модели здоровья | `409` |
