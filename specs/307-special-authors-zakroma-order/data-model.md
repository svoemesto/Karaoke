# Data Model: Порядок плашек в Закромах и явная сортировка спец-авторов

**Date**: 2026-09-06
**Branch**: `307-special-authors-zakroma-order`
**Spec**: [spec.md](./spec.md)

## Обзор

Фича вводит одну новую колонку в существующую таблицу `public.tbl_authors` и прокидывает её через ORM → DTO → API → frontend. Никаких новых таблиц или сущностей.

```
PostgreSQL.tbl_authors (sort_order INTEGER NOT NULL DEFAULT 0)
    ↓ @KaraokeDbTableField(name="sort_order")
Kotlin Author.sortOrder: Int
    ↓ проброс
Kotlin AuthorDTO.sortOrder
    ↓ (опционально) проброс
Kotlin AuthorTilePublicDto.sortOrder
    ↓ Jackson
JSON { "sortOrder": 0|... }
    ↓ HTTP /api/public/authors-tiles
Vuex authorTiles (payload)
```

---

## 1. PostgreSQL: `public.tbl_authors` (модификация)

### Существующие колонки (для контекста)

| Имя колонки | Тип | Описание |
|---|---|---|
| `id` | BIGSERIAL | PK |
| `author` | TEXT | Имя автора (отображается на плашке) |
| `ym_id`, `vk_id` | TEXT | ID на площадках |
| `last_album_*` | TEXT | Даты последнего альбома (YM/VK/processed) |
| `watched` | BOOLEAN | Отслеживается ли автор |
| `skip` | BOOLEAN | Пропустить автора |
| `aliases` | TEXT | Алиасы |
| `is_special_order` | BOOLEAN | Спецзаказной автор (см. миграцию 27) |
| `ready_songs_count` | BIGINT | Готовых песен (см. миграцию 44) |
| `total_songs_count` | BIGINT | Всего песен (см. миграцию 44) |
| `description`, `short_description`, `warning` | TEXT | Пометки |
| `recordhash` | TEXT | md5 для sync LOCAL↔SERVER |

### Новая колонка

| Имя колонки | Тип | Default | NOT NULL | Описание |
|---|---|---|---|---|
| `sort_order` | INTEGER | `0` | YES | Явный порядок в сетке Закромов. `0` = алфавит по имени автора (как сейчас). `!= 0` = принудительный порядок перед нулевыми (сортировка по возрастанию `sort_order`, затем по алфавиту). |

### Constraints

- `sort_order >= -2^31` и `sort_order <= 2^31-1` (диапазон `INTEGER` в Postgres).
- Отрицательные значения допустимы (Clarification Q1) — идут раньше нуля в `ORDER BY ASC`.
- `0` — дефолт. Существующие авторы после миграции автоматически получают `0`.

### Миграция `46_author_sort_order.sql` (контракт)

```sql
-- См. полный SQL в спека §Миграции БД. Здесь — контрактные точки:

ALTER TABLE public.tbl_authors
    ADD COLUMN IF NOT EXISTS sort_order INTEGER NOT NULL DEFAULT 0;

CREATE OR REPLACE FUNCTION public.update_tbl_authors_recordhash() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    NEW.recordhash = md5(
        -- ...все существующие поля... ||
        COALESCE(NEW.sort_order::TEXT, '')         -- НОВОЕ поле в md5
    );
RETURN NEW;
END;
$$;

UPDATE public.tbl_authors SET recordhash = md5(
    -- ...все существующие поля... ||
    COALESCE(sort_order::TEXT, '')                -- Backfill recordhash
) WHERE id > 0;
```

### Идемпотентность

- `ADD COLUMN IF NOT EXISTS` — повторный запуск не падает.
- `CREATE OR REPLACE FUNCTION` — повторный запуск безопасен (перезаписывает функцию).
- `UPDATE` с `WHERE id > 0` — повторный запуск пересчитывает `recordhash` детерминированно (тот же md5).

---

## 2. Kotlin ORM: `Author.kt` (модификация)

Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt`

### Новое поле

```kotlin
/**
 * Явный порядок автора в публичной сетке «Закромов» (/zakroma).
 *
 * Логика сортировки в SQL:
 *  - ORDER BY sort_order ASC, author ASC
 *  - При sort_order = 0 — алфавитная сортировка (как раньше).
 *  - При sort_order != 0 — принудительный порядок (выше нулевых).
 *
 * @see specs/307-special-authors-zakroma-order/spec.md
 */
@KaraokeDbTableField(name = "sort_order")
var sortOrder: Int = 0
```

### Существующий `isSpecialOrder` (для контекста)

```kotlin
@KaraokeDbTableField(name = "is_special_order")
var isSpecialOrder: Boolean = false
```

— `sortOrder` идёт по аналогии с этим полем.

### Изменение `loadAuthorTilesWithCounts`

```kotlin
// Было:
.append("ORDER BY author")

// Стало:
.append("ORDER BY sort_order ASC, author ASC")
```

### `AuthorDTO` (модификация)

Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/AuthorDTO.kt`

Добавить поле в data class и пробросить в `Author.toDTO()`:

```kotlin
val sortOrder: Int = 0,
```

---

## 3. Kotlin DTO: `AuthorTilePublicDto` (модификация)

Файл: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/AuthorTilePublicDto.kt`

### Новое поле

```kotlin
data class AuthorTilePublicDto(
    val id: Long,
    val author: String,
    val authorPictureUrl: String,
    val songCount: Long,
    /**
     * Флаг «по спецзаказу» — автор с 1-2 песнями, не вся дискография.
     */
    @get:JsonProperty("isSpecialOrder")
    val isSpecialOrder: Boolean = false,
    /**
     * Явный порядок в сетке «Закромов». Значение из `tbl_authors.sort_order`.
     * Ненулевые идут перед нулевыми; внутри обеих групп — по возрастанию
     * `sortOrder`, затем по алфавиту `author`.
     *
     * @see specs/307-special-authors-zakroma-order/spec.md
     */
    val sortOrder: Int = 0,    // НОВОЕ
) {
    companion object {
        fun fromAuthorName(
            id: Long,
            author: String,
            songCount: Long,
            isSpecialOrder: Boolean = false,
            sortOrder: Int = 0,    // НОВОЕ
        ): AuthorTilePublicDto {
            // ...
            return AuthorTilePublicDto(
                id = id,
                author = author,
                authorPictureUrl = "/minio/$BUCKET/$encoded",
                songCount = songCount,
                isSpecialOrder = isSpecialOrder,
                sortOrder = sortOrder,    // НОВОЕ
            )
        }
    }
}
```

### Изменение в `PublicApiController.authorsTiles`

```kotlin
// В маппере rows → DTO:
AuthorTilePublicDto.fromAuthorName(
    id = row.id,
    author = row.author,
    songCount = ...,
    isSpecialOrder = row.isSpecialOrder,
    sortOrder = row.sortOrder,    // НОВОЕ — нужно сначала прочитать из SELECT
)
```

### Изменение в `Author.loadAuthorTilesWithCounts` (SELECT)

```kotlin
// Было:
append("SELECT id, author, ready_songs_count, total_songs_count, is_special_order ")

// Стало:
append("SELECT id, author, ready_songs_count, total_songs_count, is_special_order, sort_order ")
```

---

## 4. Vuex state (нет изменений)

Файл: `karaoke-public/src/store/modules/zakroma.js`

`authorTiles` теперь приходит с уже правильным порядком — Vuex **не делает** дополнительной сортировки. Поле `sortOrder` присутствует в payload, но UI его **игнорирует** (порядок уже правильный).

---

## 5. Отношения

```text
tbl_authors (1) ─── (N) tbl_songs
       │
       └── (логически) виртуальная плашка «Отдельные песни разных авторов»
            (где is_special_order = true)
```

Нет новых FK-связей. Нет новых индексов — `sort_order` обычно используется в комбинации с другими полями в WHERE, и для типичной выборки в сотни-тысячи строк индекс не критичен. Если профайлинг покажет иное — добавим индекс в отдельном PR.

---

## 6. Состояние/переходы

Нет state machine. Каждый автор имеет статические `sortOrder` и `isSpecialOrder`. Изменения — через админку, после чего sync LOCAL↔SERVER пушит изменения; кеш `authorsTilesCache` истекает по TTL ≤60с.

---

## 7. Валидация

- **DB-level**: `NOT NULL DEFAULT 0` гарантирует, что любое значение в БД либо `0`, либо явно заданное редактором.
- **API-level**: дефолт `sortOrder = 0` в DTO означает, что если в payload не пришло поле — оно трактуется как `0`.
- **UI-level (админка)**: input type=number, опционально min=`-2147483648` (если решим валидировать). См. US3 acceptance 3 — «UI блокирует сохранение с понятной ошибкой валидации (поле целое число, опционально — отрицательное)».
