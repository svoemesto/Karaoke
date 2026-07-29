# API Contracts: Добавить тип альбома «Архивные записи»

**Phase 1 output for**: `030-add-archive-album-type`
**Date**: 2026-07-29
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

## Сводка

Фича **не меняет существующие API-контракты**: ни новых эндпоинтов,
ни новых полей в DTO, ни изменений сигнатур. Изменение — **внутреннее**:
1) новая константа в `AlbumType` enum и 2) новый элемент в
`albumTypeCounts` JSON-ответа `ZakromaPublicDto`.

Документ фиксирует дифф «до / после» на уровне JSON для всех
затрагиваемых эндпоинтов.

---

## Эндпоинт: `GET /api/public/zakroma` (или эквивалент, отдающий `ZakromaPublicDto`)

### До фичи

```json
{
  "author": "Кино",
  "authorPictureUrl": "/api/public/picture?file=...",
  "authorDescription": "",
  "authorShortDescription": "",
  "authorWarning": "",
  "albumTypeCounts": [
    { "dbValue": "studio",     "groupLabel": "Студийные альбомы", "filterLabel": "Студийные", "count": 5 },
    { "dbValue": "single",     "groupLabel": "Синглы",           "filterLabel": "Синглы",    "count": 2 },
    { "dbValue": "live",       "groupLabel": "Концертные альбомы","filterLabel": "Концертные","count": 1 }
    // compilation/bootleg/archive отсутствуют — count == 0, не попадают в список
  ],
  "albums": [
    { "albumName": "Группа крови", "year": 1988, "albumType": "studio", "albumTypeLabel": "Студийный альбом", "albumPictureUrl": "...", "albumSettings": [...], "description": "", "shortDescription": "", "warning": "" },
    { "albumName": "Ночь",         "year": 1986, "albumType": "single", "albumTypeLabel": "Сингл",            "albumPictureUrl": "...", "albumSettings": [...], "description": "", "shortDescription": "", "warning": "" },
    { "albumName": "Концерт в ДК", "year": 1987, "albumType": "live",   "albumTypeLabel": "Концертный альбом",  "albumPictureUrl": "...", "albumSettings": [...], "description": "", "shortDescription": "", "warning": "" }
  ]
}
```

### После фичи (если у автора есть 1 архивный альбом)

```json
{
  "author": "Кино",
  "authorPictureUrl": "/api/public/picture?file=...",
  "authorDescription": "",
  "authorShortDescription": "",
  "authorWarning": "",
  "albumTypeCounts": [
    { "dbValue": "studio",  "groupLabel": "Студийные альбомы", "filterLabel": "Студийные", "count": 5 },
    { "dbValue": "single",  "groupLabel": "Синглы",            "filterLabel": "Синглы",    "count": 2 },
    { "dbValue": "live",    "groupLabel": "Концертные альбомы","filterLabel": "Концертные","count": 1 },
    { "dbValue": "archive", "groupLabel": "Архивные записи",   "filterLabel": "Архивные",  "count": 1 }   // ← НОВОЕ
  ],
  "albums": [
    /* ...3 альбома выше... */
    { "albumName": "Архив 1985", "year": 1985, "albumType": "archive", "albumTypeLabel": "Исторические/архивные записи", "albumPictureUrl": "...", "albumSettings": [...], "description": "", "shortDescription": "", "warning": "" }  // ← НОВЫЙ АЛЬБОМ
  ]
}
```

### Дифф

| Поле | Изменение |
|------|-----------|
| `albumTypeCounts[].dbValue` | может принимать значение `"archive"` (новое) |
| `albumTypeCounts[].groupLabel` | может принимать значение `"Архивные записи"` (новое) |
| `albumTypeCounts[].filterLabel` | может принимать значение `"Архивные"` (новое) |
| `albums[].albumType` | может принимать значение `"archive"` (новое) |
| `albums[].albumTypeLabel` | может принимать значение `"Исторические/архивные записи"` (новое) |
| Все остальные поля | без изменений |

### Backward compatibility

- Клиенты, которые НЕ знают про `archive`:
  - Если `archive` отсутствует в их enum'е — они просто
    игнорируют новые записи в `albumTypeCounts` и новые альбомы
    (или показывают `dbValue`/`albumType` как есть).
  - `ZakromaPublicDto` сериализуется Jackson'ом — старые клиенты
    не упадут, они просто увидят новое значение как непрозрачную
    строку.
- Клиенты, которые ЗНАЮТ про `archive`:
  - Видят новый раздел на Закромах, новую кнопку в фильтре.
- Никаких `null`-полей, никаких изменений типов — JSON полностью
  совместим.

---

## Эндпоинт: `GET /api/albums/digest` (admin-таблица)

### До фичи

```json
[
  { "id": 1, "authorId": 10, "authorName": "Кино", "year": 1988, "name": "Группа крови", "albumType": "studio", "sortOrder": 1, "description": "", "shortDescription": "", "warning": "", "authorPicturePreviewUrl": "...", "albumPicturePreviewUrl": "...", "authorPictureId": 0, "albumPictureId": 0, "songsCount": 12 },
  { "id": 2, "authorId": 10, "authorName": "Кино", "year": 1986, "name": "Ночь",         "albumType": "single", "sortOrder": 2, /* ... */ }
]
```

### После фичи

То же самое, но у одного из альбомов может быть `"albumType": "archive"`.
Поле `albumType` уже типизировано как `String` (dbValue), новое
значение проходит без изменений DTO.

### Дифф

- `albums[].albumType` — может принимать значение `"archive"`.
- Никаких других изменений.

---

## Эндпоинт: `POST /api/album/update` (или `apisUpdateAlbum`)

### Параметры

```
albumId: Long
albumType: String  ← может принимать "archive" (новое)
```

### До фичи

`albumType` принимает значения: `studio`, `live`, `compilation`, `bootleg`, `single`.
Любое другое значение → сервер сохраняет как есть (строковое VARCHAR)
или тихо подменяет на `STUDIO` (зависит от реализации; см. анализ
ниже).

### После фичи

`albumType` дополнительно принимает значение `archive`.
Поведение для невалидных значений — без изменений (backward compat).

### Дифф

- Множество допустимых значений `albumType`: `+ "archive"`.
- Все остальные параметры/поля — без изменений.

---

## Поведение для невалидных значений

Это не «API contract» в строгом смысле, но определяет поведение,
которое должны соблюдать все контроллеры.

| Входное значение | `albumTypeEnum` (Kotlin) | Запись в БД (`tbl_albums.album_type`) |
|------------------|--------------------------|----------------------------------------|
| `null` | `STUDIO` | `STUDIO` (default) |
| `""` (пустая строка) | `STUDIO` | `STUDIO` (default) |
| `"studio"` | `STUDIO` | `studio` |
| `"single"` | `SINGLE` | `single` |
| `"live"` | `LIVE` | `live` |
| `"compilation"` | `COMPILATION` | `compilation` |
| `"bootleg"` | `BOOTLEG` | `bootleg` |
| `"archive"` (новое) | `ARCHIVE` | `archive` |
| `"Archive"` (с большой буквы) | `STUDIO` (case-sensitive: не матчится) | записывается как есть: `"Archive"` |
| `"неизвестное значение"` | `STUDIO` (default) | записывается как есть: `"неизвестное значение"` |

**Важно**: `fromDb()` мапит неизвестные значения на `STUDIO` для
in-memory представления, но в БД строка хранится «как есть» (если
API-контроллер не нормализует). Для согласованности после фичи
контроллеры, принимающие `albumType` от пользователя, должны
нормализовать вход (тихо подменять на `STUDIO` если не
распознано) — **но это уже существующее поведение** (например,
`SongType` для типа песни уже работает так же), не часть текущей
фичи.

## Cross-references

- `karaoke-web/.../dto/ZakromaPublicDto.kt:73-78, 95, 111-129` —
  формирование `albumTypeCounts` из `ZAKROMA_GROUP_ORDER`.
- `karaoke-app/.../controllers/ApiController.kt` — `apisUpdateAlbum`
  (точный путь проверить в `tasks.md` при реализации).
- `karaoke-app/.../model/AlbumType.kt:21-43` — источник истины для
  всех лейблов и порядка.
- `docs/features/dual-db-sync.md` — sync LOCAL↔SERVER, JSON
  сериализуется одинаково на обеих сторонах.
