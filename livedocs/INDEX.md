# LiveDocs — карта слоёв (INDEX)

> Этот файл — карта всех документов LiveDocs + decision tree для навигации.

## Decision tree

```
Задача про...
│
├─ конкретную фичу (NNN)?
│   └─ → livedocs/features/<NNN-slug>.md
│       (если нет — spec.md в specs/<NNN>-*/spec.md)
│
├─ модуль / домен (Song, Album, KaraokeVideo, ...)?
│   └─ → livedocs/domain/<context>.md
│       (catalog | processing | publishing | identity | editorial)
│
├─ архитектуру (как устроена система)?
│   ├─ общее (кто пользуется) → livedocs/architecture/L1-system-context.md
│   ├─ контейнеры (приложения + хранилища) → livedocs/architecture/L2-containers.md
│   └─ компоненты внутри karaoke-app → livedocs/architecture/L3-components.md
│
└─ конкретный паттерн / ловушку?
    └─ → AGENTS.md (Q&A секция) или livedocs/architecture/<topic>.md
        (jackson-conventions | docker-conventions | ...)
```

## Слои и их содержимое

### SDD — `livedocs/features/`

SDD-сводки существующих фич (1-2 страницы каждая). Drill-down — в `specs/`.

| Файл | Краткое описание |
|------|------------------|
| [`README.md`](features/README.md) | Index + конвенции для features |
| [`182-editor-self-assign-tasks.md`](features/182-editor-self-assign-tasks.md) | Self-assign заданий редакторами (atomic SELECT FOR UPDATE) |
| [`184-approve-status-choice.md`](features/184-approve-status-choice.md) | Условный запуск конвейера при выборе idStatus 5/6 |
| [`185-song-dto-audit-sponsr-remove.md`](features/185-song-dto-audit-sponsr-remove.md) | Аудит Song DTO, удаление спонсорских полей |
| [`186-zakroma-songs-fast-load.md`](features/186-zakroma-songs-fast-load.md) | Оптимизация загрузки песен в Закромах |
| [`187-site-traffic-anomaly-investigation.md`](features/187-site-traffic-anomaly-investigation.md) | Расследование аномалии трафика сайта |

### DDD — `livedocs/domain/`

Bounded contexts проекта Karaoke + ubiquitous language glossary.

| Файл | Bounded Context | Ключевые Aggregate Roots |
|------|-----------------|--------------------------|
| [`README.md`](domain/README.md) | Index + glossary корневых терминов |
| [`catalog.md`](domain/catalog.md) | Каталог | Song, Album, Author, Genre |
| [`processing.md`](domain/processing.md) | Обработка | KaraokeVideo, MLTProject, RenderMp4Params |
| [`publishing.md`](domain/publishing.md) | Публикация | PublishWindow, Subscription |
| [`identity.md`](domain/identity.md) | Идентификация | SiteUser, Session |
| [`editorial.md`](domain/editorial.md) | Редакторы | EditorAssignment, ReviewTask |

### C4 — `livedocs/architecture/`

Архитектурные диаграммы (Mermaid) + тематические документы.

| Файл | Уровень / Тема | Описание |
|------|----------------|----------|
| [`README.md`](architecture/README.md) | Index | Навигация по уровням |
| [`L1-system-context.md`](architecture/L1-system-context.md) | C4 L1 | Karaoke ↔ внешние системы |
| [`L2-containers.md`](architecture/L2-containers.md) | C4 L2 | karaoke-app, karaoke-web, SPA, БД, MinIO |
| [`L3-components.md`](architecture/L3-components.md) | C4 L3 | Model, MLT, Queue, LLM, SSE |
| [`data-sync.md`](architecture/data-sync.md) | topic | LOCAL ↔ SERVER синхронизация |
| [`queue-lanes.md`](architecture/queue-lanes.md) | topic | Async-очередь (threadId lanes, priority) |

### Тематические — паттерны / ловушки / конвенции

Мигрированы из `AGENTS.md` Q&A.

| Файл | Тема |
|------|------|
| [`jackson-conventions.md`](architecture/jackson-conventions.md) | Jackson `is`-prefix в Kotlin DTO |
| [`docker-conventions.md`](architecture/docker-conventions.md) | Образы Docker (nginx:stable, node:22-alpine, JRE) |
| [`documentation-conventions.md`](architecture/documentation-conventions.md) | KDoc backticks, JSDoc coverage, blame-ignore-revs |
| [`webvue3-patterns.md`](architecture/webvue3-patterns.md) | Персистентность страницы пагинации в webvue3 |

### Шаблоны — `livedocs/templates/`

Заготовки для новых LiveDoc-документов.

| Шаблон | Назначение |
|---------|------------|
| [`templates/feature-summary.md`](templates/feature-summary.md) | SDD-сводка новой фичи |
| [`templates/bounded-context.md`](templates/bounded-context.md) | DDD bounded context |
| [`templates/c4-level-L1.md`](templates/c4-level-L1.md) | C4 уровень 1 |
| [`templates/c4-level-L2.md`](templates/c4-level-L2.md) | C4 уровень 2 |
| [`templates/c4-level-L3.md`](templates/c4-level-L3.md) | C4 уровень 3 |

## Связь с другими документами

- **Спеки фич (drill-down)**: [`../../specs/`](../../specs/) (полные SDD-документы).
- **AGENTS.md (governance)**: [`../AGENTS.md`](../AGENTS.md) — общие правила для AI-агентов.
- **Constitution (принципы)**: [`../.specify/memory/constitution.md`](../.specify/memory/constitution.md) — NON-NEGOTIABLE принципы.
- **Per-feature документы (legacy)**: [`../docs/features/`](../docs/features/) — старые drill-down документы.
- **Стратегия роста**: [`../docs/strategy/growth.md`](../docs/strategy/growth.md).

## Когда обновлять INDEX

- Добавлен новый LiveDoc в любой слой → добавить строку в таблицу соответствующего слоя.
- Добавлен новый слой → создать секцию с таблицей.
- Переименован / удалён LiveDoc → обновить таблицу.