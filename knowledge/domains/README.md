# Domains (C4 L3 — Bounded Contexts)

> **Статус**: Active. C4 L3 — bounded contexts Karaoke. Наполнялись
> в Pass 340-361 как часть Knowledge-аудита.

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

| Домен | Статус | Комментарий |
| --- | --- | --- |
| [`identity`](identity/domain.md) | Migrated (spec 326) | SiteUser, security, role |
| [`catalog`](catalog/domain.md) | Migrated (spec 327) | Song, Album, Author, Pictures, News, Dictionary, share-линки, chat, events, история, плейлисты, задания |
| [`rendering`](rendering/domain.md) | Migrated (spec 327) | MLT, melt, рендер MP4 |
| [`processing`](processing/domain.md) | Migrated (spec 328) | Async process queue, Two-DB sync, schedulers (Pass 344), runEntitySync (Pass 343) |
| [`publishing`](publishing/domain.md) | Migrated (spec 328) | Авто-публикация (TG, VK, Sponsr) |
| [`editorial`](editorial/domain.md) | Migrated (spec 329) | Задания редакторов |
| [`monitoring`](monitoring/domain.md) | Migrated (spec 329) | Monitor checks (7 шт), log categories |
| [`stats`](stats/domain.md) | Migrated (spec 330) | Статистика, счётчики |
| [`caching`](caching/domain.md) | Migrated (spec 330) + Pass 341 | Добавлены DedupCache + PollingCache (готовые паттерны для #69) |
| [`health`](health/domain.md) | **New in Pass 341** | HealthReport и auto-repair (прецедент: #65, #69) |
| [`storage`](storage/domain.md) | **New in Pass 341** | MinIO + локальные файлы (прецедент: #65, #69) |
| [`monetization`](monetization/domain.md) | **New in Pass 341** | Тарифы, промо, корзина, подписки (visitor→premium) |
| [`sse`](sse/domain.md) | **New in Pass 341 P2** | Real-time SSE-уведомления (14 типов событий) |
| [`persistence`](persistence/domain.md) | **New in Pass 341 P2** | `KaraokeDbTable`, reflection-based save, `recordDiff` |
| [`integration`](integration/domain.md) | **New in Pass 345** | Внешние HTTP API (VK, TG, LM Studio, Whisper, Alignment, GeoIp, Yandex Captcha) |

## Компоненты по доменам (сводка Pass 340-361)

| Домен | Компоненты |
|---|---|
| **catalog** | dictionaries, song-lifecycle, entities-catalog (Pass 341+), pictures, remaining-models (Pass 348) |
| **processing** | async-process-queue, two-db-sync, run-entity-sync (Pass 343), schedulers (Pass 344), process-admin (Pass 347) |
| **storage** | karaoke-storage-service, storage-api-client, storage-flow |
| **health** | health-report |
| **sse** | (singleton, без components) |
| **persistence** | (singleton, без components) |
| **integration** | external-api-clients, alignment-ml, dtos |
| **caching** | author-cache, caching-patterns, web-caches (Pass 341) |
| **monitoring** | log-categories, monitor-checks |
| **identity** | dictionaries, security-config |
| **publishing** | dictionaries, stats-cache |
| **rendering** | dictionaries, mlt-pipeline |
| **stats** | dictionaries, event-funnel |
| **editorial** | dictionaries, assignment-lifecycle |
| **monetization** | (singleton, без components) |

## System (C4 L1 + L2)

См. [`system/`](../system/):

- `01-context.md` — C4 L1 (Pass 356).
- `02-containers.md` — C4 L2 (Pass 356).
- `utilities.md` — утилиты (Pass 341 P3c детальный, с security issue).
- `frontend/vuex-patterns.md` — webvue3 stores (Pass 349).
- `frontend/karaoke-public-composables.md` — public composables (Pass 350).
- `frontend/webvue3-views.md` — admin views (Pass 351).
- `infra/ci-tools.md` — tools/ (Pass 353).
- `infra/deploy-overview.md` — deploy/ (Pass 354).
- `infra/do-sh.md` — главный entry point (Pass 355).
- `infra/specs-catalog.md` — specs/ (Pass 358).
- `infra/pre-commit-config.md` — pre-commit хуки (Pass 359).
- `infra/dev-pc-exception.md` — Pass 282 dev-pc (Pass 360).

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

## Changelog

- **Pass 340-361** (2026-09-09): Полное наполнение доменов. 14 доменов,
  23 компонента, 11 system файлов. Всего 329 cross-links.