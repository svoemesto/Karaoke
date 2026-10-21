# Contract: формат логов и операционная диагностика

**Feature**: `546-lyrics-search-preserve-song-fields`
**Совместимость**: продолжает формат `song.locked_save_diff_overlap` (спека 357),
поэтому существующие grep-и по `docs/ops/log-correlation.md` продолжают работать.

## 1. События

| Событие | Уровень / категория | Когда | Поля |
|---|---|---|---|
| `song.field_update_applied` | INFO | запись выполнена | `songId`, `columns` (через запятую), `reason` (операция) |
| `song.field_update_skipped` | INFO | guard не пропустил | `songId`, `reason` = `existing_text` / `empty_input` / `status_ahead`, `engine` |
| `song.field_update_not_found` | WARN `infra.prod.ping` | строки нет на момент записи | `songId`, `reason` |
| `song.field_update_failed` | WARN `infra.prod.ping` | SQL-ошибка / lock timeout / deadlock | `songId`, `sqlState`, `cause` |
| `song.stale_snapshot_ignored` | WARN `infra.prod.ping` | в объекте вызывающего были значения, отличные от БД, но в запись они не попали | `songId`, `reason`, `fields` (список `column=callerValue->dbValue`, до 10) |

## 2. Формат строки

```
[<timestamp>] <LEVEL> [infra.prod.ping] song.<event>: songId=<id> <key>=<value> <key>=<value>
```

Требования:

- `songId` — первым после имени события (нужно для grep по конкретной песне).
- значения, содержащие пробелы/переводы строк, обрезаются до 50 символов и не экранируются
  (текст песни в лог не пишется целиком — только факт и длина).
- никакой ПДн/секретов: текст песни не логируется, только `sourceTextLen`.

## 3. Примеры

```
[2026-10-18T12:00:03.512] INFO  song.field_update_applied: songId=48213 columns=source_text,id_status reason=lyrics_found engine=FOURGET sourceTextLen=742
[2026-10-18T12:00:03.518] WARN  [infra.prod.ping] song.stale_snapshot_ignored: songId=48213 reason=lyrics_found fields=song_name=ПММЛ->П.М.М.Л.,song_tone=->Am
[2026-10-18T12:00:04.001] INFO  song.field_update_skipped: songId=48214 reason=existing_text engine=SEARXNG
[2026-10-18T12:00:05.777] WARN  [infra.prod.ping] song.field_update_not_found: songId=48215 reason=lyrics_found
[2026-10-18T12:00:06.120] WARN  [infra.prod.ping] song.field_update_failed: songId=48216 sqlState=55P03 cause=canceling statement due to lock timeout
```

## 4. Как проверять (для приёмки)

```bash
docker logs karaoke-app 2>&1 | grep -E 'song\.(field_update|stale_snapshot)' | tail -50
docker logs karaoke-app 2>&1 | grep 'song.stale_snapshot_ignored' | wc -l   # ≥1 при сценарии #324
docker logs karaoke-app 2>&1 | grep 'song.field_update_applied' | grep -c 'columns=source_text'  # = числу подстановок
```

Ожидание по SC: в сценарии #324 есть `field_update_applied` с `columns=source_text…`,
`stale_snapshot_ignored` фиксирует именно отброшенные устаревшие значения (и ни одно из них
не попало в БД), `field_update_failed` — 0 при нормальной работе.
