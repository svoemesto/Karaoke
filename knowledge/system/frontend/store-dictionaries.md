# Vuex store: Dictionaries

> **Домен**: system (frontend)
> **Компонента**: `webvue3/src/components/Dictionaries/store.js` —
> словари (жанры, языки, и т.д.).

## Файл

`webvue3/src/components/Dictionaries/store.js` (119 строк)

## State

```javascript
state: {
    dictionariesDigest: [],          // пары (dict_name, dict_value)
    dictionariesDigestIsLoading: false,
    dictNames: [],                   // список уникальных dict_name
    dictionariesTableCurrentPage: 1, // persistent
}
```

## Назначение

Словари (`tbl_dictionaries: пары `dict_name`/`dict_value` — "Слова с Ё",
"Censored", "Sync Ids").

**Только LOCAL** (см. `DictionariesController`):
- Правки уходят на сервер **штатной синхронизацией** (Sync, `key=dictionaries`).
- **Отдельного** `target=local|remote` **НЕ требуется** здесь.

## Hot paths

- **`/api/dictionaries/list`** — список значений.
- **`/api/dictionaries/create`** / **`/update`** / **`/delete`** — CRUD.
- **`/api/dictionaries/test`** — зачем (Pass 343+).

## Связь

- **Dictionary** ([entities-catalog.md#dictionary](../../domains/catalog/components/entities-catalog.md#dictionary)) —
  entity.
- **Two-DB sync** ([two-db-sync.md](../../domains/processing/components/two-db-sync.md)) —
  DictionariesSyncTarget.
- **dictionaries.md** (catalog/components) — список словарей.

## Changelog

- **Pass 386** (2026-09-09): Initial. Автор: agent (Karaoke).