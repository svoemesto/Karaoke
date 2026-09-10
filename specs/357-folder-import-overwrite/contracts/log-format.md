# Contract: log-format.md — `song.locked_save_diff_overlap` WARN-лог

**Спека**: 357 — Folder Import Overwrite (Audit #73)
**FR**: FR-160, FR-170
**Дата**: 2026-09-10

> Контракт унаследован из спеки 288 (`specs/288-prod-diagnostics-logging/contracts/log-format.md`). Применяется к новому WARN-логу `song.locked_save_diff_overlap`.

## Формат лога

```
[TIMESTAMP_ISO_8601] WARN infra.prod.ping song.locked_save_diff_overlap: songId=<LONG> field=<STRING> oldInMemory="<STRING>" newInDb="<STRING>"
```

### Компоненты

| Компонент | Тип | Описание |
|---|---|---|
| `TIMESTAMP_ISO_8601` | string | ISO 8601 timestamp в UTC, например `2026-09-10T12:34:56.789Z` |
| `songId` | Long | ID песни в `tbl_songs.id` |
| `field` | string | Имя поля (из `SongField` enum) с устаревшим значением в diff, например `AUTHOR`, `NAME` |
| `oldInMemory` | string | Значение поля в in-memory snapshot (то, что пытаемся записать) |
| `newInDb` | string | Актуальное значение поля в БД (которое параллельная транзакция успела обновить) |

### Префикс

- **`[infra.prod.ping]`** — обязательный префикс для лог-фильтра в `docs/ops/log-correlation.md`.

### Уровень

- **WARN** — не ERROR (операция не провалилась, просто обнаружен overlap). ERROR было бы слишком громким для информационного сигнала.

## Пример

```
[2026-09-10T12:34:56.789Z] WARN infra.prod.ping song.locked_save_diff_overlap: songId=12345 field=AUTHOR oldInMemory="Aaa Bbb" newInDb="Aaa Bbbb"
```

## Когда пишется

Пишется в `Song.saveToDbLocked()` **после reload из БД**, **перед `getDiff()`**, если:

1. Поле `field` имеет значение `value` в `this` (in-memory), которое отличается от:
   - значения `value` в `savedSong` (из БД), и
   - **начального значения** `originalValue` в in-memory snapshot на момент загрузки объекта.
2. `value != savedValue` (то есть, параллельная транзакция успела обновить).
3. `value != originalValue` (то есть, текущий код пытается перезатереть изменение).

Если все 3 условия — пишется WARN.

Если `value == originalValue` (то есть, текущий код ничего не менял, просто diff включает поле как no-op) — НЕ пишется (это нормальный случай reload-from-db).

## Метрика мониторинга

- **Допустимая частота**: < 1 раза в час на проде (см. спека 299 SC-007).
- **Алерт**: если `> 1/час` за 24-часовое окно — сигнал, что новый путь `saveToDb()` не защищён, требуется re-аудит (запустить `bash tools/check-saveToDb-locked.sh` — если существует, иначе ручной grep).

## Связь с другими логами

- `song.locked_save_skipped` (спека 299 FR-060): пишется при `readonly=true`.
- `song.locked_save_failed` (спека 299 FR-060): пишется при `connection=null`.
- `song.locked_save_fallback` (спека 299 FR-060): пишется при `savedSong == null` (песня удалена).
- `song.locked_save_lock_timeout` (спека 299 FR-060): пишется при PSQLException lock_timeout.
- **`song.locked_save_diff_overlap` (спека 357 FR-160)**: пишется при попытке перезатереть параллельную правку.

Все 5 логов имеют префикс `infra.prod.ping` и уровень WARN. Они **взаимодополняющие** — каждый сигнализирует о своём аспекте.

## Changelog

- **Pass 357** (2026-09-10): добавлен новый лог `song.locked_save_diff_overlap` для спеки 357 (FR-160..FR-180).
