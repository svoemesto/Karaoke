# Architecture Conventions

> **Домен**: `knowledge/guidelines/`
> **Назначение**: сводный справочник архитектурных конвенций Karaoke.

Этот документ — **краткий справочник** по архитектурным конвенциям
Karaoke. Детальные описания — в `knowledge/domains/<name>/` и
`knowledge/adr/`.

## Контейнеры и инфраструктура

| Тема | Ключевые правила |
| --- | --- |
| **L1 System Context** | Внешние актёры: посетители, Telegram, VK ID OAuth. См. [system/01-context.md](../system/01-context.md). |
| **L2 Containers** | `karaoke-app`, `karaoke-web`, `webvue3`, `karaoke-public`, PostgreSQL, MinIO. См. [system/02-containers.md](../system/02-containers.md). |
| **L3 Components** | Технические компоненты внутри контейнеров. См. `domains/<name>/components/*.md`. |
| **Dual-DB access** | LOCAL ↔ SERVER sync, `recordhash`-триггеры. |
| **Database** | PostgreSQL 16, схемы, индексы. |
| **Deployment** | Деплой через `deploy/do.sh`. |
| **Nginx conventions** | `nginx:stable` (НЕ `nginx:alpine`!), production headers. |
| **Docker conventions** | `node:22-alpine`, минимизация слоёв. |
| **CI/CD pipeline** | GitHub Actions, проверки knowledge/ и code style. |

## Бизнес-конвенции

| Тема | Ключевые правила |
| --- | --- |
| **Monetization** | Эфир-N-дней + 1 трек/исполнитель + 1 альбом/исполнитель free. |
| **Conversion funnel** | visitor → registration → premium. См. [stats domain](../domains/stats/components/event-funnel.md). |
| **Public modules** | Доступ — только онлайн (НЕ упоминать MP4/скачивание). |
| **Share-link** | Гостевой доступ по ссылке. |
| **Idempotency** | `sanitize(sanitize(s)) == sanitize(s)`. |
| **Concurrent editing** | Optimistic locking, `recordhash`. |
| **Data sync** | LOCAL ↔ SERVER. |
| **DB migration playbook** | Миграции: append-only, никогда не удалять. |
| **Censoring** | Цензурирование мата. |

## Операционные паттерны

| Тема | Ключевые правила |
| --- | --- |
| **DSH sandbox conventions** | Работа в DSH-окружении. |
| **Queue lanes** | HEAVY_RENDER lane (threadId=0). См. [rendering domain](../domains/rendering/components/mlt-pipeline.md). |
| **MLT pipeline** | MLT/melt рендеринг, JPEG quality 95. |
| **Cache invalidation** | Cron-update + dirty-флаг. См. [caching domain](../domains/caching/components/caching-patterns.md). |
| **Observability** | SLF4J-категории `infra.*`, мониторинг. См. [monitoring domain](../domains/monitoring/). |
| **Invariants** | Список инвариантов Karaoke. |

## Языковые/форматные конвенции

| Тема | Ключевые правила |
| --- | --- |
| **Jackson conventions** | `is`-prefix запрещён, `@JsonProperty` обязателен. См. [identity dictionaries](../domains/identity/components/dictionaries.md). |
| **Webvue3 patterns** | Vue 3 + Bootstrap-vue-next. |
| **Documentation conventions** | Markdown, frontmatter, mermaid. |

## Куда смотреть при изменениях

| Что меняется | Сначала прочитать | Потом проверить |
| --- | --- | --- |
| Авторизация | [security-config](../domains/identity/components/security-config.md) | nginx conventions |
| БД / миграция | [ADR-0001-raw-jdbc](../adr/0001-raw-jdbc.md) + db-migration playbook | KR 022 |
| Deploy | deploy workflow | docker conventions |
| MLT/рендер | [mlt-pipeline](../domains/rendering/components/mlt-pipeline.md) + [ADR-0002](../adr/0002-mlt-instead-of-ffmpeg.md) | [rendering domain](../domains/rendering/) |
| Auth (Spring) | [security-config](../domains/identity/components/security-config.md) | [ADR-0006](../adr/0006-processbuilder-redirect-errorstream.md) |

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
