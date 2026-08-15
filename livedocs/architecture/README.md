# architecture/ — C4 слой (индекс)

> Архитектурные диаграммы (Mermaid) — C4 L1/L2/L3 + тематические документы + ADR.

## Содержимое

### C4 уровни

| Файл | Уровень | Что показывает |
|------|---------|----------------|
| [L1-system-context.md](L1-system-context.md) | L1 | Karaoke ↔ внешние системы (браузер, Postgres, MinIO, Ollama, ...) |
| [L2-containers.md](L2-containers.md) | L2 | Приложения + хранилища внутри Karaoke (karaoke-app, karaoke-web, SPA, БД) |
| [L3-components.md](L3-components.md) | L3 | Компоненты внутри karaoke-app (Model, MLT, Queue, LLM, SSE) |

### Тематические (drill-down по конкретной теме)

| Файл | Тема |
|------|------|
| [dual-db-access.md](dual-db-access.md) | `KaraokeConnection` (local/remote/virtual), ThreadLocal, retry — drill-down для JDBC |
| [mlt-pipeline.md](mlt-pipeline.md) | MLT-генератор: mko-объекты, Playwright рендер текста, ~150 параметров |
| [concurrent-editing.md](concurrent-editing.md) | OptimisticConcurrency + `tbl_audits` + `VoteEnd` |
| [nginx-conventions.md](nginx-conventions.md) | nginx-конфиг: User-Agent routing, NDJSON-стримы (proxy_buffering off) |
| [observability.md](observability.md) | SSE + heartbeat + self-healing — observability patterns |
| [cache-invalidation.md](cache-invalidation.md) | `setWebvueProp` + Vuex + SSE — кросс-клиентский кэш |
| [idempotency.md](idempotency.md) | Idempotency-Key + UNIQUE + lease — паттерны по эндпоинтам |
| [data-sync.md](data-sync.md) | LOCAL ↔ SERVER синхронизация (SyncRegistry, recordhash) |
| [queue-lanes.md](queue-lanes.md) | Async-очередь (threadId lanes, HEAVY_RENDER, LIGHT_BACKGROUND, REMOTE_STORE_UPLOAD, STEM_JOBS) |
| [share-link.md](share-link.md) | Паттерн гостевого доступа: временные ссылки + claim + heartbeat + sweep |
| [censoring.md](censoring.md) | Паттерн цензурирования матерных слов в публикациях (Telegram/VK/новости) |
| [monetization.md](monetization.md) | Модель free-vs-premium: подписки, YOOKASSA, авто-публикация, share-link |
| [ci-cd-pipeline.md](ci-cd-pipeline.md) | GitHub Actions: 9 jobs, блокирующие merge, baseline-подход |
| [db-migration-playbook.md](db-migration-playbook.md) | Production database migrations: когда, как, чеклист, типичные ошибки |
| [conversion-funnel.md](conversion-funnel.md) | Воронка visitor→registration→premium→retention: точки, метрики, anti-patterns |

### Паттерны / конвенции (мигрированы из `AGENTS.md` Q&A)

| Файл | Тема |
|------|------|
| [jackson-conventions.md](jackson-conventions.md) | Jackson `is`-prefix в Kotlin DTO (`@JsonProperty`) |
| [docker-conventions.md](docker-conventions.md) | Образы Docker (nginx:stable, node:22-alpine, JRE) |
| [documentation-conventions.md](documentation-conventions.md) | KDoc backticks, JSDoc coverage, blame-ignore-revs |
| [webvue3-patterns.md](webvue3-patterns.md) | Персистентность страницы пагинации в webvue3 (Vuex store + watcher) |

### ADR (Architecture Decision Records)

| Файл | Решение |
|------|---------|
| [decisions/0001-raw-jdbc.md](decisions/0001-raw-jdbc.md) | Сырой JDBC без JPA/Hibernate для доступа к БД |
| [decisions/0002-mlt-instead-of-ffmpeg.md](decisions/0002-mlt-instead-of-ffmpeg.md) | MLT/melt как основной стек для генерации караоке-видео |
| [decisions/0003-livedocs-markdown-yaml-mermaid.md](decisions/0003-livedocs-markdown-yaml-mermaid.md) | LiveDocs = Markdown + YAML frontmatter + Mermaid (не MkDocs/Docusaurus) |
| [decisions/0004-karaoke-app-admin-only.md](decisions/0004-karaoke-app-admin-only.md) | KaraokeApp только на admin-машине, не на проде |
| [decisions/0005-self-hosted-ml.md](decisions/0005-self-hosted-ml.md) | Self-hosted ML вместо SaaS |
| [decisions/0006-processbuilder-redirect-errorstream.md](decisions/0006-processbuilder-redirect-errorstream.md) | ProcessBuilder + redirectErrorStream(true) |
| [decisions/local-0001-karaoke-properties-defaults.md](decisions/local-0001-karaoke-properties-defaults.md) | Local ADR — конвенция для дефолтов в KaraokeProperties |
| [decisions/local-0002-save-exception-handling.md](decisions/local-0002-save-exception-handling.md) | Local ADR — паттерн обработки исключений в save() |
| [decisions/local-0003-shared-minio-image-cache.md](decisions/local-0003-shared-minio-image-cache.md) | Local ADR — Shared MinIO Image Cache |
| [decisions/local-0004-lazy-eager-load-webvue3-pagination.md](decisions/local-0004-lazy-eager-load-webvue3-pagination.md) | Local ADR — lazy/eager-load в webvue3 |
| [decisions/local-0005-structured-logging-karaoke-app.md](decisions/local-0005-structured-logging-karaoke-app.md) | Local ADR — структурированное логирование |
| [decisions/local-0006-logging-and-error-handling-karaoke-web.md](decisions/local-0006-logging-and-error-handling-karaoke-web.md) | Local ADR — логирование + error handling в karaoke-web |
| [decisions/README.md](decisions/README.md) | Index ADR — конвенции формата |

## Конвенции

- C4 уровни: имя файла `L<n>-<topic>.md`, тип `c4-level`, frontmatter с `level: L1|L2|L3`.
- Тематические: имя файла `<topic>.md`, тип `topic`.
- ADR: имя файла `NNNN-<slug>.md` (в `decisions/`), без frontmatter (см. [decisions/README.md](decisions/README.md)).
- Все архитектурные документы содержат Mermaid-блок (минимум 1 диаграмма).
- Размер: ≤ 2 стр. (≤ 80 строк) для C4 уровней, ≤ 3 стр. (≤ 120 строк) для topic.

## Иерархия drill-down

```
L1 (system context)
 └─ L2 (containers)
     └─ L3 (components внутри karaoke-app)
         ├─ topic: data-sync
         ├─ topic: queue-lanes
         ├─ topic: dual-db-access
         └─ pattern: jackson-conventions, docker-conventions, ...
         ↓
         ADR (architecture decisions)
```

## Когда добавлять новый документ

1. Новая крупная подсистема или изменение архитектуры → обновить L3 или создать новый topic.
2. Новая ловушка / паттерн, мигрированная из AGENTS.md → создать pattern-документ.
3. Новая внешняя интеграция → обновить L1.
4. Значимое архитектурное решение → создать ADR в `decisions/`.