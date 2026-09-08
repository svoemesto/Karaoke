# Domains (C4 L3 — Bounded Contexts)

> **Статус**: в разработке. Каркас развёрнут в
> `322-knowledge-scaffold`. Наполнение доменов ведётся
> в следующих спецификациях.

Каждый домен — это Bounded Context в смысле DDD: явная граница
ответственности, единый язык (Ubiquitous Language), инварианты.

## Структура директории домена

```
knowledge/domains/<name>/
├── domain.md          # L2: что делает домен, язык, инварианты
└── components/        # L3: конкретные компоненты и алгоритмы
    ├── <comp-1>.md
    ├── <comp-2>.md
    └── dictionaries.md  # обязательно, если есть «магические коды»
```

## Домены Karaoke (миграция из `livedocs/domain/`)

| Домен | Источник | Текущий статус |
| --- | --- | --- |
| [`identity`](identity/domain.md) | `livedocs/domain/identity.md` | Migrated (spec 326) |
| [`catalog`](catalog/domain.md) | `livedocs/domain/catalog.md` | Migrated (spec 327) |
| [`rendering`](rendering/domain.md) | `livedocs/domain/rendering.md` | Migrated (spec 327) |
| [`processing`](processing/domain.md) | `livedocs/domain/processing.md` | Migrated (spec 328) |
| [`publishing`](publishing/domain.md) | `livedocs/domain/publishing.md` | Migrated (spec 328) |
| [`editorial`](editorial/domain.md) | `livedocs/domain/editorial.md` | Migrated (spec 329) |
| [`monitoring`](monitoring/domain.md) | `livedocs/domain/monitoring.md` | Migrated (spec 329) |
| [`stats`](stats/domain.md) | `livedocs/domain/stats.md` | Migrated (spec 330) |
| [`caching`](caching/domain.md) | `livedocs/domain/caching.md` | Migrated (spec 330) |

## Linking Protocol

- **Новый домен** → добавить строку в таблицу выше **плюс** создать
  директорию `knowledge/domains/<name>/`.
- **Новый компонент** → создать файл `knowledge/domains/<name>/components/<comp>.md`
  **и** явно перечислить его в `domain.md` этого домена со ссылкой
  и кратким описанием ответственности.
- **Удаление компонента** → удалить ссылку из `domain.md`.

## Словари (dictionaries)

Если в коде или спецификациях используются технические коды, ID или
перечисления, они **обязаны** быть централизованы в
`knowledge/domains/<name>/components/dictionaries.md`. Хардкод «магических
кодов» в L3-спецификациях — критический дефект (см. `audit-living-docs`
«Magic Codes»).
