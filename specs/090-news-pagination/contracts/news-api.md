# Contract: News pagination endpoints

## `GET /api/public/news` (karaoke-web, `PublicNewsController`)

**Было**:
- Параметров нет.
- Ответ: `NewsDto[]` (весь список опубликованных новостей).

**Станет**:

Параметры (query, оба необязательны, с дефолтами):
- `page: Int = 0` (0-based)
- `size: Int = 20`

Ответ:
```json
{
  "items": [ /* NewsDto[] — та же форма, что и раньше, без изменений полей */ ],
  "total": 19042,
  "hasMore": true
}
```

`hasMore = (page + 1) * size < total`. Сортировка — `publishAt DESC, id DESC`
(см. research.md п.5) — не меняется по составу критериев, только становится
детерминированной на границах страниц.

**Совместимость**: единственный потребитель — `karaoke-public/src/services/newsApi.js`
в этом же репозитории; меняется в этом же PR. `/api/public/news/since` —
без изменений.

## `POST /api/news/list` (karaoke-app, `NewsController`)

**Было**:
- Параметр: `target` (LOCAL/remote).
- Ответ: `{ "news": NewsDto[] }` (весь список).

**Станет**:

Параметры (form, все опциональны):
- `target: String?` — без изменений.
- `page: Int = 0` (0-based)
- `pageSize: Int = 50`

Ответ:
```json
{
  "news": [ /* NewsDto[] — только запрошенная страница */ ],
  "total": 19042
}
```

Сортировка — `id DESC` (см. research.md п.5).

**Совместимость**: единственный потребитель — `webvue3/src/components/News/store.js`
в этом же репозитории; меняется в этом же PR. `create`/`update`/`delete` —
без изменений контракта.

## Без изменений

- `GET /api/public/news/since?id=` — контракт прежний (`{count, items}`),
  малый объём, не участвует в пагинации.
- `POST /api/news/create|update|delete` — без изменений.
- `POST /api/news/backfill-announcements` — без изменений (не относится к
  просмотру списка).
