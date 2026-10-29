# Contract: админский REST очереди (состояние кортежа, retry, отчёт целостности)

**Feature**: `674-kortege-execution` · **Тип**: HTTP JSON, префикс `/api`
**Владелец**: `controllers/KaraokeProcessAdminController.kt`, `services/KaraokeProcessAdminService.kt`,
`dto/admin/KaraokeProcessAdminDTO.kt`, `services/ArtifactIntegrityScheduler.kt`, `webvue3`
**Связанные FR**: FR-015, FR-016, FR-014

Существующие маршруты (`KaraokeProcessAdminController.kt:32-150`) сохраняют пути и коды ответов;
контракт расширяет **поля** и **семантику retry**, добавляет один read-only маршрут отчёта.

---

## 1. `GET /api/admin/processes`

Query-параметры — без изменений (`status`, `type`, `threadId`, `chainId`, `name`, `topLevelOnly`,
`parentId`, `limit`, `offset`, `includeDeleted`).

### 1.1 Изменение `topLevelOnly`

| Было | Стало |
|---|---|
| `WHERE process_chain_id IS NULL` | `WHERE (process_chain_id IS NULL OR process_chain_id = id)` |

Причина: голова кортежа имеет `process_chain_id = id` (`KaraokeProcess.kt:2288-2290`) и сейчас
невидима в top-level списке. Дети по-прежнему запрашиваются `topLevelOnly=false&parentId=<headId>`.

### 1.2 DTO — новые поля (аддитивно, обратная совместимость)

```jsonc
{
  "id": 1650737,
  "name": "[Сектор Газа] - [...] - «Лирика»",
  "status": "WORKING",
  "type": "DEMUCS2",
  "command": "",
  "description": "Демукс 2",
  "songId": 30390,
  "order": 0,
  "priority": 0,
  "threadId": 1,
  "processChainId": 1650737,
  "processDeletedAt": null,
  "updatedAt": "2026-10-29T08:55:44Z",
  "startedAt": "2026-10-29T08:54:48Z",
  "endedAt": null,
  "recordhash": "…",

  // новое — состояние кортежа (вычисляется, не хранится)
  "chainId": 1650737,
  "chainStepIndex": 5,        // 0-based индекс текущего (или последнего) шага
  "chainStepsTotal": 11,      // N
  "chainState": "WORKING",    // WAITING | WORKING | ERROR | DONE
  "chainFailedStep": null,    // order шага, на котором упал кортеж (при chainState=ERROR)
  "chainReason": null,        // причина сбоя (копия description шага с ERROR), при ERROR
  "progress": 42.5            // процент текущего шага (как сейчас в отдельном канале SSE)
}
```

Правила вычисления:

| Поле | Правило |
|---|---|
| `chainId` | `process_chain_id ?: id` |
| `chainStepsTotal` | `COUNT(*)` по `process_chain_id = chainId` |
| `chainStepIndex` | `process_order` строки `WORKING`, иначе последней по `process_order` не-`WAITING`; `0`, если шагов нет |
| `chainState` | `WORKING`, если есть `WORKING`; иначе `WAITING`, если есть `WAITING`; иначе `ERROR`, если есть `ERROR`; иначе `DONE` |
| `chainFailedStep` / `chainReason` | из первой строки `ERROR` кортежа (`order`, `description`) |

Вычисление — одним SQL-запросом (агрегат по `process_chain_id` + `LEFT JOIN`), без N+1;
`limit ≤ 1000`, `offset ≥ 0` (как сейчас).

### 1.3 Сортировка

`ORDER BY process_priority, process_order, id` сохраняется для строк. Для top-level списка
дополнительно допускается `chainState`-фильтр (см. §1.4) и сортировка по `chainId` — на усмотрение
реализации, контрактом не фиксируется.

### 1.4 Фильтр по состоянию кортежа

Новый необязательный query-параметр `chainState` (`WAITING|WORKING|ERROR|DONE`):
эквивалент `EXISTS`-условия на строки кортежа (§1.2). Неизвестное значение — `400` (как сейчас для
невалидных числовых фильтров, `buildWhere`).

## 2. `POST /api/admin/processes/{id}/retry`

Путь, тело и коды ответов — без изменений. Меняется семантика для строки кортежа:

| Вход | Ответ | Побочные эффекты |
|---|---|---|
| `id` — `ERROR`-шаг кортежа (chain > 1) | `200` + DTO **головы** кортежа с `chainState=WAITING` | все `ERROR`-шаги этого `chain_id` → `WAITING`; `priority` всех строк = приоритет головы (если голова утрачена — `1`); `process_end`/прогресс сброшены; одна audit-запись `RETRY` (actor, old/new) |
| `id` — одиночный `ERROR` (`chain_id = id`) | `200` + DTO строки | `ERROR → WAITING`, как сейчас |
| `id` — не `ERROR` | `409 Conflict` | без изменений |
| `id` — soft-deleted | `409 Conflict` | без изменений |
| `id` — нет строки | `404 Not Found` | без изменений |

Идемпотентность: повторный retry уже `WAITING`-кортежа — `409` (статус не `ERROR`), как сейчас.

## 3. `GET /api/admin/processes/{id}` — без изменений пути, DTO с §1.2

`GET /api/admin/processes/{id}/audit` — без изменений.

## 4. `GET /api/admin/artifacts/integrity` — новый read-only маршрут

Отдаёт последний отчёт ежедневной проверки (FR-014). Правок не делает.

```jsonc
{
  "generatedAt": "2026-10-30T04:00:12Z",
  "scannedSongs": 812,
  "findingsTotal": 2,
  "reportPath": "/sm-karaoke/system/logs/artifact-integrity/2026-10-30.json",
  "findings": [
    {
      "songId": 30390,
      "kind": "foreignDuration",
      "artifact": "/sm-karaoke/done2/…/hdemucs_mmi/…-accompaniment.flac",
      "expected": "275044 ms",
      "actual": "223387 ms",
      "detectedAt": "2026-10-30T04:00:09Z"
    }
  ]
}
```

| Код | Условие |
|---|---|
| `200` | отчёт есть (в том числе пустой: `findingsTotal=0`) |
| `204 No Content` | проверка ещё ни разу не запускалась (или отчёт удалён) |
| `403` | нет админских прав (как у остальных `/api/admin/**`) |

## 5. `POST /api/admin/artifacts/repair` — новый маршрут явного ремонта

Тело:

```jsonc
{
  "songIds": [30390, 30391, 30392, 30393, 30394, 30395, 30396, 30397],
  "dryRun": true,          // по умолчанию true
  "includeKeyBpm": true    // ставить ли KEY_BPM_FROM_FILE
}
```

| `dryRun` | Поведение | Ответ |
|---|---|---|
| `true` (default) | только `ArtifactIntegrity.verifySong` для каждого `songId` | `200` + `{ "dryRun": true, "findings": [...] }` |
| `false` | ставит кортежи `DEMUCS2` (или `DEMUCS5`, если у песни 5 стемов) и, при `includeKeyBpm`, `KEY_BPM_FROM_FILE` в лейн `1`, приоритет `0` | `200` + `{ "dryRun": false, "tuples": [{"songId":30390,"demucsChainId":…,"keyBpmChainId":…}] }` |

Границы: до 100 `songId` за вызов (`400` иначе); несуществующая песня — пропускается с
предупреждением в ответе, не `404`; никаких правок БД, кроме постановки заданий; ремонт не запускается
автоматически (FR-014).

## 6. SSE

Каналы и формат — без изменений: `recordChange` для `tbl_processes` (строка текущего шага +
прогресс). Фронт `webvue3` получает обновления по тем же событиям; новые поля DTO приходят при
перезапросе списка (отдельного SSE-события для `chainState` не вводится).

## 7. Ошибки

| Ситуация | Ответ |
|---|---|
| нет соединения с БД | `500` + `{"status":500,"error":"Internal Server Error","message":"Cannot connect to database …"}` (как сейчас) |
| невалидный `limit`/`offset`/`chainState` | `400` |
| `id` не число | `400` |
| неизвестный маршрут | `404` (как сейчас) |
