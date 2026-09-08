# Architecture Conventions

> **Домен**: `knowledge/guidelines/`
> **Назначение**: сводный индекс архитектурных конвенций Karaoke.
> **Источник**: [livedocs/architecture/](../../livedocs/architecture/) (полная коллекция).

Этот документ — **краткий справочник** по архитектурным конвенциям.
Полные тексты живут в `livedocs/architecture/` (архивная коллекция
v1). По мере миграции ADR и компонентов ссылки будут переезжать в
`knowledge/`.

## Контейнеры и инфраструктура

| Тема | Где | Ключевые правила |
| --- | --- | --- |
| **L1 System Context** | [L1-system-context.md](../../livedocs/architecture/L1-system-context.md) | Внешние актёры: посетители, Telegram, VK ID OAuth. |
| **L2 Containers** | [L2-containers.md](../../livedocs/architecture/L2-containers.md) | `karaoke-app`, `karaoke-web`, `webvue3`, `karaoke-public`, PostgreSQL, MinIO. |
| **L3 Components** | [L3-components.md](../../livedocs/architecture/L3-components.md) | Технические компоненты внутри контейнеров. |
| **Dual-DB access** | [dual-db-access.md](../../livedocs/architecture/dual-db-access.md) | LOCAL ↔ SERVER sync, `recordhash`-триггеры. |
| **Database** | [database.md](../../livedocs/architecture/database.md) | PostgreSQL 16, схемы, индексы. |
| **Deployment** | [deployment.md](../../livedocs/architecture/deployment.md) | Деплой через `deploy/do.sh`. |
| **Nginx conventions** | [nginx-conventions.md](../../livedocs/architecture/nginx-conventions.md) | `nginx:stable` (НЕ `nginx:alpine`!), production headers. |
| **Docker conventions** | [docker-conventions.md](../../livedocs/architecture/docker-conventions.md) | `node:22-alpine`, минимизация слоёв. |
| **CI/CD pipeline** | [ci-cd-pipeline.md](../../livedocs/architecture/ci-cd-pipeline.md) | GitHub Actions, 7 проверок. |

## Бизнес-конвенции

| Тема | Где | Ключевые правила |
| --- | --- | --- |
| **Monetization** | [monetization.md](../../livedocs/architecture/monetization.md) | Эфир-N-дней + 1 трек/исполнитель + 1 альбом/исполнитель free. |
| **Conversion funnel** | [conversion-funnel.md](../../livedocs/architecture/conversion-funnel.md) | visitor → registration → premium. |
| **Public modules** | [public-modules.md](../../livedocs/architecture/public-modules.md) | Доступ — только онлайн (НЕ упоминать MP4/скачивание). |
| **Share-link** | [share-link.md](../../livedocs/architecture/share-link.md) | Гостевой доступ по ссылке. |
| **Idempotency** | [idempotency.md](../../livedocs/architecture/idempotency.md) | `sanitize(sanitize(s)) == sanitize(s)`. |
| **Concurrent editing** | [concurrent-editing.md](../../livedocs/architecture/concurrent-editing.md) | Optimistic locking, `recordhash`. |
| **Data sync** | [data-sync.md](../../livedocs/architecture/data-sync.md) | LOCAL ↔ SERVER. |
| **DB migration playbook** | [db-migration-playbook.md](../../livedocs/architecture/db-migration-playbook.md) | Миграции: append-only, никогда не удалять. |
| **Censoring** | [censoring.md](../../livedocs/architecture/censoring.md) | Цензурирование мата. |

## Операционные паттерны

| Тема | Где | Ключевые правила |
| --- | --- | --- |
| **DSH sandbox conventions** | [dsh-sandbox-conventions.md](../../livedocs/architecture/dsh-sandbox-conventions.md) | Работа в DSH-окружении. |
| **Queue lanes** | [queue-lanes.md](../../livedocs/architecture/queue-lanes.md) | HEAVY_RENDER lane (threadId=0). |
| **MLT pipeline** | [mlt-pipeline.md](../../livedocs/architecture/mlt-pipeline.md) | MLT/melt рендеринг, JPEG quality 95. |
| **Cache invalidation** | [cache-invalidation.md](../../livedocs/architecture/cache-invalidation.md) | Cron-update + dirty-флаг. |
| **Observability** | [observability.md](../../livedocs/architecture/observability.md) | SLF4J-категории, мониторинг. |
| **Invariants** | [invariants.md](../../livedocs/architecture/invariants.md) | Список инвариантов Karaoke. |

## Языковые/форматные конвенции

| Тема | Где | Ключевые правила |
| --- | --- | --- |
| **Jackson conventions** | [jackson-conventions.md](../../livedocs/architecture/jackson-conventions.md) | `is`-prefix запрещён, `@JsonProperty` обязателен. |
| **Webvue3 patterns** | [webvue3-patterns.md](../../livedocs/architecture/webvue3-patterns.md) | Vue 3 + Bootstrap-vue-next. |
| **Documentation conventions** | [documentation-conventions.md](../../livedocs/architecture/documentation-conventions.md) | Markdown, frontmatter, mermaid. |

## Куда смотреть при изменениях

| Что меняется | Сначала прочитать | Потом проверить |
| --- | --- | --- |
| Авторизация | [security-config](../domains/identity/components/security-config.md) | [nginx-conventions.md](../../livedocs/architecture/nginx-conventions.md) |
| БД / миграция | [db-migration-playbook.md](../../livedocs/architecture/db-migration-playbook.md) + [0001-raw-jdbc.md](../adr/0001-raw-jdbc.md) | [database.md](../../livedocs/architecture/database.md) |
| Deploy | [deployment.md](../../livedocs/architecture/deployment.md) | [docker-conventions.md](../../livedocs/architecture/docker-conventions.md) |
| MLT/рендер | [mlt-pipeline.md](../../livedocs/architecture/mlt-pipeline.md) + [0002-mlt-instead-of-ffmpeg.md](../adr/0002-mlt-instead-of-ffmpeg.md) | [rendering domain](../domains/rendering/) |
| Auth (Spring) | [security-config](../domains/identity/components/security-config.md) | [0006-processbuilder-redirect-errorstream.md](../adr/0006-processbuilder-redirect-errorstream.md) |

## Ловушки (TOP-10 из AGENTS.md)

1. **Backticks в KDoc** ломают парсер ktlint. Заменять `` `multitrack` `` → «multitrack».
2. **`redirectErrorStream(false)`** для `ProcessBuilder` блокирует процесс. Всегда `true`.
3. **`nginx:alpine`** — нет bash, контейнер падает. Использовать `nginx:stable`.
4. **`node:latest`** — недетерминированный. Использовать `node:22-alpine`.
5. **Двух-фронтенд**: admin (`webvue3`) и public (`karaoke-public`) — **разные приложения**.
6. **Сырой JDBC + recordhash** (никакого JPA/Hibernate).
7. **Per-feature документ** обновлять при правке кода фичи (FR-009).
8. **Git push через VPN** — может упасть `EOF` / `400 Bad request`.
9. **CI блокирует merge** при любом failing check.
10. **«Доступ — только онлайн»** (см. оферту). НЕ упоминать MP4/скачивание.
11. **Санитайзер идемпотентен** (`SanitizePath.kt`, `Extentions.kt`).
