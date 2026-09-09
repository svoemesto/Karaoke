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

## Домены Karaoke (Bounded Contexts)

| Домен | Текущий статус |
| --- | --- |
| [`identity`](identity/domain.md) | Migrated (spec 326) |
| [`catalog`](catalog/domain.md) | Migrated (spec 327) |
| [`rendering`](rendering/domain.md) | Migrated (spec 327) |
| [`processing`](processing/domain.md) | Migrated (spec 328), обновлён Pass 341 (drift fix) |
| [`publishing`](publishing/domain.md) | Migrated (spec 328) |
| [`editorial`](editorial/domain.md) | Migrated (spec 329) |
| [`monitoring`](monitoring/domain.md) | Migrated (spec 329) |
| [`stats`](stats/domain.md) | Migrated (spec 330) |
| [`caching`](caching/domain.md) | Migrated (spec 330), дополнен Pass 341 (web-caches) |
| [`health`](health/domain.md) | **New in Pass 341** — HealthReport и auto-repair (прецедент: #65, #69) |
| [`storage`](storage/domain.md) | **New in Pass 341** — MinIO + локальные файлы (прецедент: #65, #69) |

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
