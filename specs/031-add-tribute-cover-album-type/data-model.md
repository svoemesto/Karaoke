# Data Model: Добавить тип альбома «Трибьют/Кавер»

**Phase 1 output for**: `031-add-tribute-cover-album-type`
**Date**: 2026-07-29
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) |
**Research**: [research.md](./research.md)

## Сущности

### AlbumType (enum) — РАСШИРЯЕТСЯ

**Где**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/AlbumType.kt`

**Структура** (без изменений сигнатуры, добавляется 1 константа):

```kotlin
enum class AlbumType(
    val dbValue: String,
    val description: String,
    val groupLabel: String,
    val filterLabel: String,
) : Serializable {
    STUDIO(dbValue = "studio", description = "Студийный альбом", groupLabel = "Студийные альбомы", filterLabel = "Студийные"),
    LIVE(dbValue = "live", description = "Концертный альбом", groupLabel = "Концертные альбомы", filterLabel = "Концертные"),
    COMPILATION(dbValue = "compilation", description = "Сборник", groupLabel = "Сборники", filterLabel = "Сборники"),
    BOOTLEG(dbValue = "bootleg", description = "Бутлег", groupLabel = "Бутлеги", filterLabel = "Бутлеги"),
    SINGLE(dbValue = "single", description = "Сингл", groupLabel = "Синглы", filterLabel = "Синглы"),
    ARCHIVE(dbValue = "archive", description = "Исторические/архивные записи", groupLabel = "Архивные записи", filterLabel = "Архивные"),
    TRIBUTE(dbValue = "tribute", description = "Альбом каверов/трибьютов", groupLabel = "Трибьют/Кавер", filterLabel = "Трибьют/Кавер"),
    ;

    companion object {
        fun fromDb(value: String?): AlbumType = entries.find { it.dbValue == value } ?: STUDIO

        val ZAKROMA_GROUP_ORDER: List<AlbumType> = listOf(STUDIO, SINGLE, LIVE, COMPILATION, BOOTLEG, ARCHIVE, TRIBUTE)
    }
}
```

**Изменения** (только 2 строки):
1. Добавить константу `TRIBUTE(...)` последней.
2. Добавить `TRIBUTE` в `ZAKROMA_GROUP_ORDER` последним.

**Поля** (без изменений):
- `dbValue: String` — значение, хранимое в БД (lowercase).
- `description: String` — каноническое русское описание.
- `groupLabel: String` — заголовок раздела на Закромах.
- `filterLabel: String` — подпись кнопки быстрого фильтра.

**Валидация**:
- `fromDb(null)` → `STUDIO` (default, без изменений).
- `fromDb("")` → `STUDIO` (default, без изменений).
- `fromDb("tribute")` → `TRIBUTE` (новое поведение).
- `fromDb("Tribute")` (с большой буквы) → `STUDIO` (default,
  case-sensitive lowercase match).
- `fromDb("неизвестное значение")` → `STUDIO` (default, без
  падений).

**State transitions**: N/A (enum, без состояний).

---

### Album — БЕЗ ИЗМЕНЕНИЙ

**Где**: `karaoke-app/.../model/Album.kt`

**Поле `albumType`**: `@KaraokeDbTableField(name = "album_type")`,
тип `String` (lowercase dbValue), длина до 20 символов. Новая
константа `"tribute"` укладывается в существующий VARCHAR (7
символов).

**Геттер/сеттер `albumTypeEnum`** (без изменений):
```kotlin
var albumTypeEnum: AlbumType
    get() = AlbumType.fromDb(albumType)
    set(value) { albumType = value.dbValue }
```

**Поведение**:
- Запись: `album.albumTypeEnum = AlbumType.TRIBUTE` →
  `albumType = "tribute"` в БД.
- Чтение: `album.albumType = "tribute"` →
  `album.albumTypeEnum == TRIBUTE`.

**Связи** (без изменений):
- `Album.authorId` → `Author.id` (FK не enforced, lookup по id).
- `Album` ↔ `Song` (через `Song.albumId`, обратная сторона).

---

### AlbumDTO — БЕЗ ИЗМЕНЕНИЙ

**Где**: `karaoke-app/.../model/AlbumDTO.kt`

**Поле `albumType: String`**: хранит `dbValue` как есть. Новое
значение `"tribute"` сериализуется в JSON без изменений DTO.

---

### ZakromaAlbumPublicDto — БЕЗ ИЗМЕНЕНИЙ

**Где**: `karaoke-web/.../dto/ZakromaPublicDto.kt`

**Поле `albumType: String`**: dbValue как есть.
**Поле `albumTypeLabel: String`**: канонический русский лейбл
(`AlbumType.description`), формируется бэкендом через
`alb.albumTypeLabel` (см. Zakroma.kt).

---

### AlbumTypeSummaryDto — БЕЗ ИЗМЕНЕНИЙ (НО С НОВЫМ КЛЮЧОМ В СПИСКЕ)

**Где**: `karaoke-web/.../dto/ZakromaPublicDto.kt:73-78`

**Структура** (без изменений):
```kotlin
data class AlbumTypeSummaryDto(
    val dbValue: String,
    val groupLabel: String,
    val filterLabel: String,
    val count: Int,
)
```

**Поведение**:
- Поле `albumTypeCounts: List<AlbumTypeSummaryDto>` в
  `ZakromaPublicDto` формируется из
  `AlbumType.ZAKROMA_GROUP_ORDER`
  (см. `ZakromaPublicDto.kt:111-129`).
- После фичи в JSON автоматически появляется элемент:
  ```json
  {
    "dbValue": "tribute",
    "groupLabel": "Трибьют/Кавер",
    "filterLabel": "Трибьют/Кавер",
    "count": 1
  }
  ```
  — только для авторов, у которых есть хотя бы 1 альбом с
  `album_type = 'tribute'`.

---

### Vuex state `albumsFilterAlbumType` — БЕЗ ИЗМЕНЕНИЙ

**Где**: `webvue3/src/components/Albums/filter/store.js:14`

**Структура**: `albumsFilterAlbumType: ''` (String, dbValue или
пустая строка).

**Поведение**: при выборе «Трибьют/Кавер» в фильтре в state
записывается `"tribute"`. Передаётся на бэкенд через
`params.filterAlbumType` (см. `AlbumsFilterModal.vue:ok()`).

---

## Связи (ER)

```text
tbl_albums (Album)
├── album_type VARCHAR(20)  ← "studio" | "live" | "compilation" | "bootleg" | "single" | "archive" | "tribute"
├── author_id → tbl_authors.id
└── ...

tbl_songs (Song)
├── album_id → tbl_albums.id
└── ...
```

**Инварианты**:
- `tbl_albums.album_type` — VARCHAR, допускает любые строки
  (включая `NULL` и пустую строку для старых записей).
- `Album.albumTypeEnum` НИКОГДА не возвращает `null` —
  `fromDb(null) → STUDIO` (default).
- `dbValue` сравнивается case-sensitive: только lowercase-значения
  матчатся (`"tribute"` ≠ `"Tribute"`).

## Схема БД (DDL)

**Нет изменений**. Колонка `tbl_albums.album_type` уже
поддерживает новое значение:

```sql
-- Существующая колонка (миграция не требуется):
ALTER TABLE tbl_albums ADD COLUMN album_type VARCHAR(20);
-- или эквивалентное определение в исходной миграции.
```

## Миграция данных

**Нет**. Существующие записи с `album_type = 'studio'` остаются
«Студийные». Если в БД уже есть записи с `album_type = 'tribute'`
(от предыдущих ручных правок или попыток), они автоматически
начнут отображаться как «Трибьют/Кавер» после релиза без миграции
(см. FR-010 спеки).

## Sample data (для ручной проверки)

```sql
-- Найти существующие альбомы без трибьют/кавер (baseline):
SELECT id, name, year, album_type
FROM tbl_albums
WHERE album_type = 'tribute'
LIMIT 10;
-- Ожидаемый результат до фичи: 0 строк (или случайные строки
-- от ручных правок).

-- После фичи: можно вручную проставить для проверки:
UPDATE tbl_albums
SET album_type = 'tribute'
WHERE id = <some_album_id>
  AND author_id = <some_author_id>;
-- Затем проверить на Закромах — должен появиться раздел
-- «Трибьют/Кавер» с этим альбомом.
```

## Backward compatibility

| Сценарий | Поведение |
|----------|-----------|
| `album_type IS NULL` | `albumTypeEnum == STUDIO` (default, без изменений) |
| `album_type = ''` | `albumTypeEnum == STUDIO` (default, без изменений) |
| `album_type = 'studio'` | `albumTypeEnum == STUDIO` (без изменений) |
| `album_type = 'tribute'` (новое) | `albumTypeEnum == TRIBUTE` (новое поведение) |
| `album_type = 'unknown_xxx'` | `albumTypeEnum == STUDIO` (default, без падений) |
| `album_type = 'Tribute'` (с большой буквы) | `albumTypeEnum == STUDIO` (default, case-sensitive) |
| Существующие альбомы (любой тип, кроме `tribute`) | Без изменений (0 регрессий) |
| Sync LOCAL↔SERVER (recordhash) | Идентичный md5 для одной и той же записи (строки хешируются посимвольно) |

## Совместимость с параллельной фичей 030 (ARCHIVE)

Если обе фичи (030-add-archive-album-type с ARCHIVE и
031-add-tribute-cover-album-type с TRIBUTE) смёржены в одной
сборке, итоговая структура `AlbumType.kt`:

```kotlin
enum class AlbumType(...) : Serializable {
    STUDIO(...),
    LIVE(...),
    COMPILATION(...),
    BOOTLEG(...),
    SINGLE(...),
    ARCHIVE(dbValue = "archive", description = "Исторические/архивные записи", groupLabel = "Архивные записи", filterLabel = "Архивные"),  // из 030
    TRIBUTE(dbValue = "tribute", description = "Альбом каверов/трибьютов", groupLabel = "Трибьют/Кавер", filterLabel = "Трибьют/Кавер"),  // из 031
    ;

    companion object {
        fun fromDb(value: String?): AlbumType = entries.find { it.dbValue == value } ?: STUDIO
        val ZAKROMA_GROUP_ORDER: List<AlbumType> = listOf(STUDIO, SINGLE, LIVE, COMPILATION, BOOTLEG, ARCHIVE, TRIBUTE)  // из 031
    }
}
```

**Конфликта при слиянии нет**: правки в одном файле, но в разных
строках (ARCHIVE на строке 33, TRIBUTE на строке 34; ZAKROMA_GROUP_ORDER
расширяется в 031). Git merge разрешит автоматически.

## Cross-references

- `karaoke-app/.../model/AlbumType.kt:21-44` — сам enum.
- `karaoke-app/.../model/Album.kt:71-75` — геттер/сеттер.
- `karaoke-web/.../dto/ZakromaPublicDto.kt:73-78, 95, 111-129` — DTO.
- `docs/features/dual-db-sync.md` — sync LOCAL↔SERVER.
- `specs/011-album-song-rename/` — предыдущая фича с тем же
  паттерном (dbValue + lowercase).
- `specs/030-add-archive-album-type/` — близнец-фича (ARCHIVE).
