# Domains (C4 L3 — Bounded Contexts)

> **Статус**: в разработке. Каркас развёрнут в
> `320-living-docs-v2-bootstrap`. Наполнение доменов ведётся
> в следующих спецификациях.

Каждый домен — это Bounded Context в смысле DDD: явная граница
ответственности, единый язык (Ubiquitous Language), инварианты.

## Структура директории домена

```
docs/domains/<name>/
├── domain.md          # L2: что делает домен, язык, инварианты
└── components/        # L3: конкретные компоненты и алгоритмы
    ├── <comp-1>.md
    ├── <comp-2>.md
    └── dictionaries.md  # обязательно, если есть «магические коды»
```

## Домены Karaoke (миграция из `livedocs/domain/`)

Список заполняется по мере переноса:

| Домен | Источник | Текущий статус |
| --- | --- | --- |
| `identity` | `livedocs/domain/identity.md` | запланировано |
| `catalog` | `livedocs/domain/catalog.md` | запланировано |
| `processing` | `livedocs/domain/processing.md` | запланировано |
| `rendering` | `livedocs/domain/rendering.md` | запланировано |
| `publishing` | `livedocs/domain/publishing.md` | запланировано |
| `editorial` | `livedocs/domain/editorial.md` | запланировано |
| `stats` | `livedocs/domain/stats.md` | запланировано |
| `caching` | `livedocs/domain/caching.md` | запланировано |
| `monitoring` | `livedocs/domain/monitoring.md` | запланировано |

## Linking Protocol

- **Новый домен** → добавить строку в таблицу выше **плюс** создать
  директорию `docs/domains/<name>/`.
- **Новый компонент** → создать файл `docs/domains/<name>/components/<comp>.md`
  **и** явно перечислить его в `domain.md` этого домена со ссылкой
  и кратким описанием ответственности.
- **Удаление компонента** → удалить ссылку из `domain.md`.

## Словари (dictionaries)

Если в коде или спецификациях используются технические коды, ID или
перечисления, они **обязаны** быть централизованы в
`docs/domains/<name>/components/dictionaries.md`. Хардкод «магических
кодов» в L3-спецификациях — критический дефект (см. `audit-living-docs`
«Magic Codes»).
