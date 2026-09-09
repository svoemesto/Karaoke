# Domains (C4 L3 — Bounded Contexts)

> **Статус**: Active. C4 L3 — bounded contexts Karaoke. Наполнялись
> в Pass 340-385 как часть Knowledge-аудита.

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
| [`karaoke-web`](karaoke-web/domain.md) | **New in Pass 362-365** | Прод-сервер, public API, YooKassa, share-линки, защита от DDoS |

## Компоненты по доменам (финальная сводка Pass 340-385)

| Домен | Компоненты |
|---|---|
| **catalog** | dictionaries, song-lifecycle, song-entity, entities-catalog, pictures, remaining-models |
| **processing** | async-process-queue, two-db-sync, run-entity-sync, schedulers, process-admin, karaoke-properties, playwright-rendering |
| **storage** | karaoke-storage-service, storage-api-client, storage-flow |
| **health** | health-report |
| **sse** | (singleton) |
| **persistence** | (singleton) |
| **monetization** | (singleton) |
| **integration** | external-api-clients, alignment-ml, dtos |
| **karaoke-web** | public-controllers, internal-controllers, services-overview, song-share-link-service, config |
| **rendering** | dictionaries, mlt-pipeline, mlt-generator |
| **publishing** | dictionaries, stats-cache, publishing-services |

## System (C4 L1 + L2 + infra)

См. [`system/`](../system/):

- `01-context.md` — C4 L1 (Pass 356).
- `02-containers.md` — C4 L2 (Pass 356).
- `utilities.md` — утилиты (Pass 341 P3c детальный, с security issue).
- `frontend/`:
  - `vuex-patterns.md` — webvue3 stores (Pass 349).
  - `karaoke-public-composables.md` — public composables (Pass 350).
  - `webvue3-views.md` — admin views (Pass 351).
  - `store-songs.md`, `store-song-editor.md`, `store-sync.md`,
    `store-properties.md`, `store-site-users.md` — отдельные
    детальные store docs (Pass 371, 382).
  - `composable-karaoke-editor.md`, `composable-player-readiness.md`,
    `composable-use-auth.md`, `composable-use-player-access.md`,
    `composable-use-share-link.md` — отдельные детальные composable
    docs (Pass 372, 383).
- `infra/`:
  - `ci-tools.md` — tools/ (Pass 353).
  - `deploy-overview.md` — deploy/ (Pass 354).
  - `do-sh.md` — главный entry point (Pass 355).
  - `specs-catalog.md`, `specs-catalog-detailed.md` — specs/ (Pass 358, 374).
  - `pre-commit-config.md` — pre-commit хуки (Pass 359).
  - `dev-pc-exception.md` — Pass 282 dev-pc (Pass 360).
  - `governance-docs.md` — CONTRIBUTING, DEVELOPMENT, constitution (Pass 376).
  - `per-feature-docs.md` — docs/features/ (Pass 380).
  - `sql-migrations.md` — deploy/karaoke-db/*.sql (Pass 381).

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

## Статистика (Pass 340-385)

- **15 доменов** покрыто.
- **~30 компонентов** в `domains/*/components/`.
- **~25 system файлов** (frontend, infra, C4 L1/L2).
- **~120+ файлов** Knowledge в целом.
- **~440+ cross-links** все валидны.
- **9/9 структурных** проверок OK.
- **~16 000+ строк** Knowledge.

## Changelog

- **Pass 340-385** (2026-09-09): Полное наполнение Knowledge. 15
  доменов, ~30 компонентов, ~25 system файлов. Всего ~120+ файлов,
  ~16 000+ строк, ~440+ cross-links.
- **Pass 386-425** (2026-09-09, ветка `340-governance-knowledge-first`):
  детализация Vuex stores, composables, Mko, monitor-checks, SQL
  migrations. +60 файлов, +1 600 строк. PR #440.
- **Pass 426-451** (2026-09-09, ветка `342-knowledge-detail-2`):
  detail Album/Author/SiteUser + karaoke-web controllers +
  Thymeleaf + SongPublicDto + **3 РЕАЛЬНЫХ code fix'а**:
  - **Crypto security**: ключ/IV из env (`CRYPTO_AES_KEY`/`CRYPTO_AES_IV`)
    с hardcoded fallback.
  - **Dead code removal**: `searchSongText2` удалена.
  - **getDiff field order fix**: сортировка по column name для
    детерминированного UPDATE.
  - **Race #65 hypothesis**: документирована (не применена —
    требует review).
  PR #441.