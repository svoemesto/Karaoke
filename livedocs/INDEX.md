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
│       (catalog | processing | rendering | publishing | identity | editorial | stats)
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
| [`016-fix-spec-tags-marker-loss-on-reopen.md`](features/016-fix-spec-tags-marker-loss-on-reopen.md) | Спецтеги: сохранение маркеров после `Apply → Save → reopen` |
| [`124-news-flags-backfill.md`](features/124-news-flags-backfill.md) | Backfill флагов публикаций готовых песен без создания новостей |
| [`125-player-status-gate.md`](features/125-player-status-gate.md) | Доступность плеера в таблице «Песни» при статусе ≥4 |
| [`190-playlist-play-button-and-stems-cancel.md`](features/190-playlist-play-button-and-stems-cancel.md) | Плейлисты: запуск с любой песни + превью альбома/автора + фикс задвоения вейвформ |

### DDD — `livedocs/domain/`

Bounded contexts проекта Karaoke + ubiquitous language glossary.

| Файл | Bounded Context | Ключевые Aggregate Roots |
|------|-----------------|--------------------------|
| [`README.md`](domain/README.md) | Index + glossary корневых терминов |
| [`catalog.md`](domain/catalog.md) | Каталог | Song, Album, Author, Genre |
| [`processing.md`](domain/processing.md) | Обработка | KaraokeVideo, MLTProject, RenderMp4Params |
| [`rendering.md`](domain/rendering.md) | Производство видео | KaraokeVideo (MP4 через MLT/melt) |
| [`publishing.md`](domain/publishing.md) | Публикация | PublishWindow, Subscription |
| [`identity.md`](domain/identity.md) | Идентификация | SiteUser, Session |
| [`editorial.md`](domain/editorial.md) | Редакторы | EditorAssignment, ReviewTask |
| [`stats.md`](domain/stats.md) | Аналитика | StatBySong, SiteEvent |

### C4 — `livedocs/architecture/`

Архитектурные диаграммы (Mermaid) + тематические документы.

| Файл | Уровень / Тема | Описание |
|------|----------------|----------|
| [`README.md`](architecture/README.md) | Index | Навигация по уровням |
| [`L1-system-context.md`](architecture/L1-system-context.md) | C4 L1 | Karaoke ↔ внешние системы |
| [`L2-containers.md`](architecture/L2-containers.md) | C4 L2 | karaoke-app, karaoke-web, SPA, БД, MinIO |
| [`L3-components.md`](architecture/L3-components.md) | C4 L3 | Model, MLT, Queue, LLM, SSE |
| [`database.md`](architecture/database.md) | topic | `tbl_public_settings` + recordhash-триггеры |
| [`invariants.md`](architecture/invariants.md) | topic | Ключевые инварианты (ловушки karaoke-web, MTU, Jackson is*) |
| [`deployment.md`](architecture/deployment.md) | topic | Серверы (Local dev, prod) |
| [`public-modules.md`](architecture/public-modules.md) | topic | Карта karaoke-public / плеер / аккаунт |
| [`data-sync.md`](architecture/data-sync.md) | topic | LOCAL ↔ SERVER синхронизация |
| [`queue-lanes.md`](architecture/queue-lanes.md) | topic | Async-очередь (threadId lanes, priority) |
| [`share-link.md`](architecture/share-link.md) | topic | Гостевой доступ: временные ссылки + claim + heartbeat + sweep |
| [`censoring.md`](architecture/censoring.md) | topic | Цензурирование: TextFileDictionary + String.censored() |
| [`monetization.md`](architecture/monetization.md) | topic | Модель free-vs-premium: подписки, YOOKASSA, авто-публикация |

### Тематические — паттерны / ловушки / конвенции

Мигрированы из `AGENTS.md` Q&A.

| Файл | Тема |
|------|------|
| [`jackson-conventions.md`](architecture/jackson-conventions.md) | Jackson `is`-prefix в Kotlin DTO |
| [`docker-conventions.md`](architecture/docker-conventions.md) | Образы Docker (nginx:stable, node:22-alpine, JRE) |
| [`documentation-conventions.md`](architecture/documentation-conventions.md) | KDoc backticks, JSDoc coverage, blame-ignore-revs |
| [`webvue3-patterns.md`](architecture/webvue3-patterns.md) | Персистентность страницы пагинации в webvue3 + postMessage-мост |

### Runbooks — `livedocs/runbooks/`

Операционные how-to (пошаговые инструкции).

| Файл | Описание |
|------|----------|
| [`README.md`](runbooks/README.md) | Index runbooks |
| [`how-to-deploy.md`](runbooks/how-to-deploy.md) | Деплой web/public на прод |
| [`how-to-migrate-db.md`](runbooks/how-to-migrate-db.md) | SQL-миграция (SyncRegistry + recordhash) |
| [`how-to-migrate-prod-server.md`](runbooks/how-to-migrate-prod-server.md) | Чек-лист миграции прода на новый сервер |
| [`how-to-demo-publish-links.md`](runbooks/how-to-demo-publish-links.md) | Публикация DEMO на VK/Dzen/Telegram/Max |
| [`how-to-stemjobs.md`](runbooks/how-to-stemjobs.md) | Руководство по stemjobs (demucs) |
| [`how-to-debug-connection-leak.md`](runbooks/how-to-debug-connection-leak.md) | Диагностика connection leak |
| [`how-to-add-new-feature.md`](runbooks/how-to-add-new-feature.md) | SDD workflow (/speckit.specify → plan → tasks → implement) |
| [`how-to-add-new-domain.md`](runbooks/how-to-add-new-domain.md) | Новый bounded context |
| [`how-to-add-new-topic.md`](runbooks/how-to-add-new-topic.md) | Новый topic в architecture/ |
| [`how-to-add-new-adr.md`](runbooks/how-to-add-new-adr.md) | Новый ADR |
| [`how-to-update-livedocs.md`](runbooks/how-to-update-livedocs.md) | Sync кода с LiveDocs |

### Strategy — `livedocs/strategy/`

Стратегические документы (рост, AI-выбор, audit).

| Файл | Описание |
|------|----------|
| [`growth.md`](strategy/growth.md) | Стратегия роста: аноним → регистрация → premium |
| [`growth-audit.md`](strategy/growth-audit.md) | Аудит гипотез роста (567 строк) |
| [`about-page-draft.md`](strategy/about-page-draft.md) | Draft страницы «О проекте» |
| [`models-comparison.md`](strategy/models-comparison.md) | Сравнение LLM-моделей для speckit |

### Onboarding — `livedocs/`

Документы для новых сотрудников / машин.

| Файл | Описание |
|------|----------|
| [`onboarding.md`](onboarding.md) | Настройка новой машины разработчика (30-60 мин) |
| [`claude-code-setup.md`](claude-code-setup.md) | Настройка Claude Code (если не opencode) |

### Onboarding handoff — `livedocs/onboarding-handoff/`

Конкретные инструкции для новых сотрудников.

| Файл | Описание |
|------|----------|
| [`011-m23-special-orders-pickup.md`](onboarding-handoff/011-m23-special-orders-pickup.md) | m23: специальные заказы |
| [`012-vk-id-401-token-exchange.md`](onboarding-handoff/012-vk-id-401-token-exchange.md) | VK ID 401 token exchange |

### Meta — `livedocs/`

Мета-документы о LiveDocs (не проект).

| Файл | Описание |
|------|----------|
| [`README.md`](README.md) | Корневой манифест |
| [`INDEX.md`](INDEX.md) | Этот файл — карта слоёв |
| [`CONVENTIONS.md`](CONVENTIONS.md) | Конвенции LiveDocs |
| [`CHANGELOG.md`](CHANGELOG.md) | История изменений (Pass 43+) |
| [`SESSION-SUMMARY.md`](SESSION-SUMMARY.md) | Итоги follow-up сессии |
| [`architecture-notes.md`](architecture-notes.md) | Датированный changelog архитектуры |
| [`architecture-notes-archive.md`](architecture-notes-archive.md) | Старый changelog |

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
- **Per-feature документы (legacy)**: [`../../archive/docs/features/`](../../archive/docs/features/) — drill-down (бывший docs/features/).
- **Стратегия роста**: [`strategy/growth.md`](strategy/growth.md).
- **Архитектурный changelog**: [`architecture-notes.md`](architecture-notes.md) (бывший docs/architecture-notes.md).
- **Архивный changelog**: [`architecture-notes-archive.md`](architecture-notes-archive.md) (бывший docs/architecture-notes-archive.md).
- **Onboarding**: [`onboarding.md`](onboarding.md) (бывший docs/onboarding.md).
- **Claude Code setup**: [`claude-code-setup.md`](claude-code-setup.md) (бывший docs/claude-code-setup.md).
- **LiveDocs-конвенции**: [`CONVENTIONS.md`](CONVENTIONS.md) (бывший docs/livedocs-conventions.md).

## Когда обновлять INDEX

- Добавлен новый LiveDoc в любой слой → добавить строку в таблицу соответствующего слоя.
- Добавлен новый слой → создать секцию с таблицей.
- Переименован / удалён LiveDoc → обновить таблицу.