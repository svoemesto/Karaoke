# Report: Backfill etag/size по всем записям кеша (Pass 448, #181)

**Issue**: OpenProject #181
**Branch**: `448-backfill-all-records`
**Дата**: 2026-09-24

## Проблема

Кнопка «Заполнить etag/size кеша хранилища» (спека #435, #159) обрабатывала строки
только с `exists = true AND (size/etag пусты)`. В реальной БД это была **1 строка**,
тогда как `exists=false` строк — **80 279 LOCAL + 933 REMOTE**, и они никогда не
проверялись. После переезда (#165) и сброса REMOTE-кеша это стало заметно: кеш
нельзя было «прогреть»/актуализировать.

## Решение

Расширен SQL-фильтр выборки `loadRowsNeedingBackfill()`:

```sql
-- было
WHERE exists = true AND (size IS NULL OR etag IS NULL OR etag = '')
-- стало
WHERE NOT exists OR size IS NULL OR etag IS NULL OR etag = ''
```

Логика обработки уже поддерживала оба направления самокоррекции:
- `size >= 0` → `UPDATE` (`exists=true` + etag/size);
- `size < 0` → `MARK_MISSING` (`exists=false` + WARN).

## Изменения

- `CacheEtagSizeBackfill.kt` — SQL-выборка + KDoc (Pass 435 + 448).
- `CacheEtagSizeBackfillTest.kt` — +2 теста (exists-независимость решения).
- `docs/features/storage-metadata-cache.md` — V2.11 + пункт #181.
- `knowledge/domains/storage/domain.md` — раздел «Backfill/актуализация».
- `specs/448-backfill-all-records/` — spec/plan/tasks/report.

## Проверки

| Проверка | Результат |
|---|---|
| SQL-выборка на реальной БД | было 1 → стало 81 213 (80 279 LOCAL + 934 REMOTE) |
| `:karaoke-app:compileKotlin` | OK |
| `CacheEtagSizeBackfillTest` | 6/6 PASS |
| ktlint / pre-commit | OK |

## Что НЕ входит

- Прогрев **удалённых** (сброшенных) ключей: `refreshKeys` делает DELETE, этих строк
  в таблице нет → backfill их не видит. Вернёт health-report или отдельный warm-обход
  по песням (вне scope).
- Изменение кнопки/endpoint: используется существующая кнопка, поведение просто
  стало полнее.

## После merge

Пересборка/рестарт `karaoke-app` (по согласию владельца) → нажать кнопку на главной.
