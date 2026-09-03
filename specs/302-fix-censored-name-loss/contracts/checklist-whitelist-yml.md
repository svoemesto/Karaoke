# Config Contract: `tools/check-*-coverage.whitelist.yml`

**Spec**: [spec.md](../spec.md) | **Research**: [research.md](../research.md) → Decision 3

## Назначение

Whitelist для чек-скриптов (`check-songedit-field-coverage.sh` и
`check-endpoint-field-coverage.sh`). Поля, перечисленные в whitelist,
НЕ считаются «потерянными» при проверке покрытия UI↔backend.

Whitelist — это **явный список исключений с обоснованием**, видимый
в diff каждого PR. Размер ≤10 полей для SongEdit-чека (Session 2026-09-03
Q4→B); при росте >15 — это сигнал для редизайна чека (AST-анализ).

## Формат (две версии)

### `tools/check-songedit-field-coverage.whitelist.yml` (FR-005)

Простой whitelist по имени поля (без указания компонента/эндпоинта,
т.к. чек покрывает ровно одну пару).

```yaml
# tools/check-songedit-field-coverage.whitelist.yml
#
# Whitelist для check-songedit-field-coverage.sh.
# Каждое поле = одно исключение из чека покрытия SongEdit ↔ /api/song/update.
#
# Формат: "<имя_поля>": "<обоснование, почему не покрыто автоматически>"
#
# Предзаполнен при создании (Session 2026-09-03 Q4→B) нестандартными
# setter'ами, которые чек не может распознать автоматически.

whitelist:
  # path-param (идентификатор песни, не редактируется через v-model)
  "id": "path-param, не v-model поле Song"

  # специальная обработка через ?.let { rawAlbumId -> ... } + cross-author check
  "albumId": "обработка через rawAlbumId.toLongOrNull() + Album.getAlbumById cross-author check"

  # enum, setter через SongType.entries.firstOrNull { ... }
  "songType": "enum SongType, нестандартный setter"

  # не-String типы, не подходят под общий шаблон fields[SongField.X] = it
  "free": "Boolean? нестроковый тип"
  "idStatus": "Long? нестроковый тип"
  "rate": "Int? нестроковый тип"
  "rootId": "Long? нестроковый тип"
  "audioParentId": "Long? нестроковый тип"
  "audioSimilarityPercent": "Int? нестроковый тип"
  "audioDeltaMs": "Long? нестроковый тип"
  "idTariff": "Int? нестроковый тип"
  "diffBeats": "Int? нестроковый тип"

  # специальная обработка через sanitize + collision + active-process
  "fileName": "специальная обработка через sanitizeSongFileName() + collision check + KaraokeProcess.hasActiveProcess"

  # прямые setter'ы (не fields[...])
  "tags": "прямой songValue.tags = it, не fields[...]"
  "rootFolder": "прямой songValue.rootFolder = it, не fields[...]"
  "description": "прямой songValue.description = it, не fields[...]"
  "shortDescription": "прямой songValue.shortDescription = it, не fields[...]"
  "warning": "прямой songValue.warning = it, не fields[...]"
```

**Объём**: ≤20 полей (включая все не-String типы). Если в ходе реализации
обнаружится >25 → вернуться к Decision и пересмотреть.

### `tools/check-endpoint-field-coverage.whitelist.yml` (FR-008)

Глобальный whitelist с указанием компонента и эндпоинта (т.к. общий
чек покрывает несколько пар).

```yaml
# tools/check-endpoint-field-coverage.whitelist.yml
#
# Глобальный whitelist для check-endpoint-field-coverage.sh.
# Применяется ПОСЛЕ специфичных whitelist'ов каждой пары (если есть).
#
# Формат: "ComponentName/endpointName/fieldName": "<обоснование>"

whitelist:
  "SongEdit.vue//api/song/update/id": "path-param, не v-model поле"
  "SongEdit.vue//api/song/update/albumId": "cross-author check, специальная обработка"
  "SongEdit.vue//api/song/update/songType": "enum SongType"
  # ... остальные поля SongEdit (если не покрыты специфичным whitelist)
  # Поля из других пар (если добавляются):
  # "AlbumEdit.vue//albums/updatealbum/foo": "причина"
```

## Семантика

**Применение** (в `check-songedit-field-coverage.sh`):

1. Поле извлечено из `SongEdit.vue` (`v-model="song.X"`).
2. Поиск `@RequestParam X` или ссылки на `X` в `songs2Update`.
3. Если найдено → OK, поле покрыто.
4. Если НЕ найдено → поиск в `whitelist` (top-level keys).
5. Если в whitelist → OK с пометкой `SKIPPED (reason)`.
6. Если НЕ в whitelist → ERROR `MISSING: X` → exit 1.

**Применение** (в `check-endpoint-field-coverage.sh`):

1. Для каждой пары из `endpoint-pairs.yml`.
2. Извлечь поля из компонента.
3. Поиск setter'а в эндпоинте.
4. Если не найдено → поиск в локальном whitelist пары
   (формат: `whitelist[component][endpoint][field]`).
5. Если не найдено → поиск в глобальном whitelist
   (формат: `whitelist["ComponentName//endpointName/fieldName"]`).
6. Если не найдено → ERROR.

## Парсинг

Чек-скрипты используют **минимальный YAML-парсер** (без yq):
- `grep -E '^  "[^"]+":' file.yml` — извлечение top-level ключей.
- Игнорирование комментариев (строки начинающиеся с `#`).
- Игнорирование пустых строк.

Если в whitelist есть сложная структура (вложенные объекты), нужно
перейти на `yq` или Python. На текущем этапе НЕ планируется —
формат плоский.

## Edge Cases

- **Whitelist-файл не существует**: парсер использует пустой whitelist
  (без ошибки). Это удобно для нового чек-скрипта.
- **Whitelist-файл невалиден** (например, не yaml): парсер выводит
  ERROR и exit 2.
- **Поле в whitelist, но уже покрыто**: WARNING (duplicate), exit 0.
- **Поле в whitelist, но НЕ существует в UI**: WARNING (dead entry),
  exit 0 (для cleanup).
- **Пустой whitelist** (`whitelist: {}`): OK, никакие поля не исключены.
