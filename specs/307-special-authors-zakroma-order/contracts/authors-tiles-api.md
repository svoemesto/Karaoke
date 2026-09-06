# API Contract: GET /api/public/authors-tiles

**Date**: 2026-09-06
**Branch**: `307-special-authors-zakroma-order`
**Spec**: [spec.md](../spec.md)

## Endpoint

```
GET /api/public/authors-tiles
```

**Authentication**: нет (публичный эндпоинт, как и весь `/api/public/*`).

## Query parameters

| Имя | Тип | Default | Описание |
|---|---|---|---|
| `scope` | string | `"main"` | `"main"` — авторы БЕЗ `is_special_order=true`; `"special"` — только спецзаказные; `"all"` — все. |

**Backward compatible**: новых параметров нет. Старые клиенты продолжат работать без изменений.

## Response

**HTTP 200 OK**. `Content-Type: application/json`.

Тело — массив `AuthorTilePublicDto`:

```json
[
  {
    "id": 42,
    "author": "The Beatles",
    "authorPictureUrl": "/minio/karaoke/The%20Beatles/The%20Beatles.preview.author.png",
    "songCount": 230,
    "isSpecialOrder": false,
    "sortOrder": 0
  },
  {
    "id": 7,
    "author": "Queen",
    "authorPictureUrl": "/minio/karaoke/Queen/Queen.preview.author.png",
    "songCount": 85,
    "isSpecialOrder": false,
    "sortOrder": 5
  }
  // ...
]
```

## Поля DTO

| JSON-имя | Тип | Описание | Default |
|---|---|---|---|
| `id` | long | PK автора в `tbl_authors` | required |
| `author` | string | Имя автора | required |
| `authorPictureUrl` | string | URL превью-картинки (через nginx `/minio/` proxy) | required (может быть пустой строкой, если нет картинки) |
| `songCount` | long | Число песен (`ready_songs_count` для анонимов, `total_songs_count` для редакторов) | required |
| `isSpecialOrder` | boolean | Спецзаказной автор (`tbl_authors.is_special_order`) | `false` |
| `sortOrder` | int | Явный порядок в сетке (`tbl_authors.sort_order`) | `0` |

**Новое поле**: `sortOrder` (int). См. FR-006, FR-007, SC-006.

## Изменения относительно текущей версии

### Что НЕ меняется

- URL, HTTP-метод, query-параметры.
- Status codes, Content-Type.
- Все существующие поля (`id`, `author`, `authorPictureUrl`, `songCount`, `isSpecialOrder`).
- Семантика кеша (`scope:onlyPublished:includeSkipped`, TTL ≤60с, dirty-инвалидация через `consumeDirty()`).

### Что добавляется

- Новое поле `sortOrder: int` в каждом элементе массива.
- Backend SQL: `ORDER BY sort_order ASC, author ASC` (вместо `ORDER BY author`).
- Backend SQL: `SELECT ... sort_order` (в дополнение к существующим колонкам).

### Что НЕ ломается

- Клиент, который не знает про поле `sortOrder`, продолжит работать: Jackson десериализует JSON в DTO без `sortOrder`, дефолт `Int = 0` сработает.
- Клиент, который игнорирует порядок массива, продолжит работать: порядок изменился, но это не ломает клиентов, которые читают по `id` или `author`.

## Пример запроса

```bash
curl 'http://localhost:8897/api/public/authors-tiles?scope=main'
```

## Пример response (с реальными порядками)

```json
[
  { "id": 1,  "author": "A1", "authorPictureUrl": "...", "songCount": 10, "isSpecialOrder": false, "sortOrder": 5  },
  { "id": 2,  "author": "A2", "authorPictureUrl": "...", "songCount": 12, "isSpecialOrder": false, "sortOrder": -1 },
  { "id": 3,  "author": "A3", "authorPictureUrl": "...", "songCount": 8,  "isSpecialOrder": false, "sortOrder": 0  },
  { "id": 4,  "author": "A4", "authorPictureUrl": "...", "songCount": 20, "isSpecialOrder": false, "sortOrder": 0  }
]
```

(Сортировка: `sortOrder=-1, sortOrder=5, sortOrder=0 (A3), sortOrder=0 (A4)`.)

## Error handling

Без изменений. Стандартное поведение Spring:
- 200 + пустой массив `[]`, если в БД нет подходящих авторов.
- 500 при SQLException (см. существующую обработку в `PublicApiController.authorsTiles`).

## Контроль версий

API **не версионируется** явно (нет `/v2/authors-tiles` и т.п.). Изменение backward-compatible (добавление поля, изменение порядка) применяется in-place.

## Связанные артефакты

- Spec: FR-006, FR-007, SC-006.
- Data model: [data-model.md](../data-model.md) §3.
- Реализация: `karaoke-web/src/main/kotlin/.../PublicApiController.kt::authorsTiles`, `karaoke-web/src/main/kotlin/.../dto/AuthorTilePublicDto.kt`.
