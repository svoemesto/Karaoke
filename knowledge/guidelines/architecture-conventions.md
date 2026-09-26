# Architecture Conventions

> **Домен**: `knowledge/guidelines/`
> **Назначение**: **primary reference** для build/runtime/docker конвенций
> Karaoke (R-04, R-05, R-06, R-08, R-11, R-44). Детальные описания —
> в `knowledge/domains/<name>/` и `knowledge/adr/`.
>
> **Статус**: Этот документ стал **single source of truth** для всех build/docker
> правил после Pass 379 wayfinder #101 (#104 resolution, Q2). До этого
> правила были **разбросаны** по AGENTS.md / CLAUDE.md / constitution.md.
> Теперь — единый справочник с явным форматом **Rule/Protocol/Failure**.

Этот документ содержит **краткий справочник** по архитектурным конвенциям
Karaoke. Каждое правило — в формате `Rule / Protocol / Failure / Enforcement`.

## Build / runtime / docker конвенции

Эти правила machine-readable enforceable через `tools/check-*.sh` guards
(см. Pass 372-375, Pass 379). Изменения здесь — через governance-PR.

### R-04 — `nginx:stable` (НЕ `nginx:alpine`)

**Rule**: В `Dockerfile` для production должна быть строка `FROM nginx:stable`
(или `nginx:stable-alpine`, `nginx:1.27-alpine` для LTS-версий). **Запрещены**
теги `nginx:alpine`, `nginx:latest`, без тега.

**Protocol**:
- Использовать `nginx:stable` (production preferred).
- Если нужен alpine: `nginx:stable-alpine`.
- Указать **major.minor** для воспроизводимости: `nginx:1.27-alpine`.

**Failure**: Использование `nginx:alpine` → контейнер падает с
`bash: not found` (Pass 245 прецедент).

**Source**: `deploy/karaoke-public/Dockerfile:16`, `deploy/karaoke-webvue3/Dockerfile:16`.

**Enforcement**: `tools/check-docker-image-tags.sh` (Pass 379, R-04).

### R-05 — `node:22-alpine` (НЕ `node:latest`)

**Rule**: В `Dockerfile` для production должна быть строка `FROM node:22-alpine`
(или `node:20-alpine`, `node:20-bookworm-slim` для LTS-версий). **Запрещены**
теги `node:alpine`, `node:latest`, `node:lts`, без тега.

**Protocol**:
- Использовать `node:22-alpine` (current LTS).
- Если нужна конкретная версия: `node:22.x-alpine`.
- **Никогда** `:latest` (недетерминированный, может сломаться).

**Failure**: Использование `node:latest` → непредсказуемые версии
зависимостей, невоспроизводимые сборки.

**Source**: `deploy/karaoke-public/Dockerfile:1`, `deploy/karaoke-webvue3/Dockerfile:1`.

**Enforcement**: `tools/check-docker-image-tags.sh` (Pass 379, R-05).

### R-06 — TOP-10 ловушек (краткий список)

**Rule**: См. раздел «Ловушки» ниже. Полный список с примерами —
в AGENTS.md § «TOP-10 ловушек» и `AGENTS.md` § «HARD GATES».

**Failure**: Каждая ловушка имеет свой failure-stop (см. AGENTS.md).

**Enforcement**: Ряд pre-commit hooks (`tools/check-*.sh`).

### R-08 — Sanitizer idempotency

**Rule**: `sanitize(sanitize(s)) == sanitize(s)` — функция sanitize должна
быть **идемпотентна**. Применение дважды = применение один раз.

**Protocol**:
- Никогда не использовать `replace` если `drop` сохраняет идемпотентность.
- Тесты: `tests/unit/SanitizePathTest.kt` проверяет идемпотентность.

**Failure**: Нарушение → потеря данных при импорте (issue #53: `!`/`?`
молча удалялись → файлы не находились).

**Reference**: `docs/features/idempotent-path-sanitize.md`.

**Enforcement**: `tools/check-no-mp4-mentions.sh` baseline (125 записей legacy).

### R-11 — MP4/скачивание запрет (оферта «доступ только онлайн»)

**Rule**: В production-коде (webvue3, karaoke-public, karaoke-app, karaoke-web)
запрещено упоминание `\bmp4\b` или `скачивани` в **публичном контексте**.

**Исключения** (legitimate use, в baseline):
- Backend MP4-рендеринг (`PlayerMp4RenderService.kt`, mlt/Consumer) —
  производственный пайплайн.
- Имена файлов `*.mp4` в model (Song.kt, SongOutputFile.kt).
- Auto-publish в VK/Telegram (admin-пайплайн, не публичное скачивание).
- KaraokeProcess.kt, AlbumCoverFinder.kt (admin-render).
- UI-текст ОФЕРТЫ (явно говорит «без скачивания»).
- webvue3/karaoke-public player (headless mp4 export, admin).

**Failure**: Упоминание MP4 в публичных UI-обещаниях → нарушение оферты
(`offer.html`), legal/compliance риск.

**Enforcement**: `tools/check-no-mp4-mentions.sh` (Pass 379, R-11).

База исключений сопоставляется **по содержимому строки**, не по номеру
(Pass 455): ключ — `<путь>:sha256(trim(строки))[:16]`. До этого база была
`file:line` и ломалась от любой вставки строк выше — guard рапортовал «новое
нарушение R-11» там, где упоминания не добавлялись (трижды за одну сессию:
Pass 451, 452, 454). Вставка/удаление строк и смена отступа теперь базу не
ломают; изменение самого текста упоминания — ломает, и это намеренно.
Перегенерация: `bash tools/check-no-mp4-mentions.sh --update-baseline`.

### R-44 — MLT/melt рендеринг (НЕ ffmpeg)

**Rule**: Караоке-видео рендеринг — через MLT framework (`melt` CLI).
**Не использовать** прямой `ffmpeg` для основного рендеринг-пайплайна
(допускается для вспомогательных операций: extract audio, probe, конвертация).

**Protocol**:
- Все rendering jobs → `melt` (MLT framework).
- Queue lanes: HEAVY_RENDER=0, LIGHT_BACKGROUND=-1, REMOTE_STORE_UPLOAD=-2.
- JPEG quality: 95.

**Reference**: [ADR-0002-mlt-instead-of-ffmpeg.md](../adr/0002-mlt-instead-of-ffmpeg.md),
[rendering domain](../domains/rendering/components/mlt-pipeline.md).

**Enforcement**: code review (no guard script — требует domain knowledge).

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
| **Public modules** | Доступ — только онлайн (НЕ упоминать MP4/скачивание). См. **R-11**. |
| **Share-link** | Гостевой доступ по ссылке. |
| **Idempotency** | `sanitize(sanitize(s)) == sanitize(s)`. См. **R-08**. |
| **Concurrent editing** | Optimistic locking, `recordhash`. |
| **Data sync** | LOCAL ↔ SERVER. |
| **DB migration playbook** | Миграции: append-only, никогда не удалять. |
| **Censoring** | Цензурирование мата. |

## Операционные паттерны

| Тема | Ключевые правила |
| --- | --- |
| **DSH sandbox conventions** | Работа в DSH-окружении (см. AGENTS.md § Gradle/Docker). |
| **Queue lanes** | HEAVY_RENDER lane (threadId=0). См. [rendering domain](../domains/rendering/components/mlt-pipeline.md). См. **R-44**. |
| **MLT pipeline** | MLT/melt рендеринг, JPEG quality 95. См. **R-44**. |
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
| Авторизация | [security-config](../domains/identity/components/security-config.md) | nginx conventions (R-04) |
| БД / миграция | [ADR-0001-raw-jdbc](../adr/0001-raw-jdbc.md) + db-migration playbook | KR 022 |
| Deploy | deploy workflow | docker conventions (R-04, R-05) |
| MLT/рендер | [mlt-pipeline](../domains/rendering/components/mlt-pipeline.md) + [ADR-0002](../adr/0002-mlt-instead-of-ffmpeg.md) | [rendering domain](../domains/rendering/) и **R-44** |
| Auth (Spring) | [security-config](../domains/identity/components/security-config.md) | [ADR-0006](../adr/0006-processbuilder-redirect-errorstream.md) |

## Ловушки (TOP-11 из AGENTS.md)

См. AGENTS.md § «HARD GATES» — полный список. Краткий preview:

1. **Backticks в KDoc** ломают парсер ktlint. Заменять `` `multitrack` `` → «multitrack».
2. **`redirectErrorStream(false)`** для `ProcessBuilder` блокирует процесс. Всегда `true`.
3. **`nginx:alpine`** — нет bash, контейнер падает. Использовать `nginx:stable`. → **R-04**.
4. **`node:latest`** — недетерминированный. Использовать `node:22-alpine`. → **R-05**.
5. **Двух-фронтенд**: admin (`webvue3`) и public (`karaoke-public`) — **разные приложения**.
6. **Сырой JDBC + recordhash** (никакого JPA/Hibernate). → guard в tools/check-no-jpa-imports.sh.
7. **Per-feature документ** обновлять при правке кода фичи (FR-009).
8. **Git push через VPN** — может упасть `EOF` / `400 Bad request`.
9. **CI блокирует merge** при любом failing check.
10. **«Доступ — только онлайн»** (см. оферту). НЕ упоминать MP4/скачивание. → **R-11**.
11. **Санитайзер идемпотентен** (`SanitizePath.kt`, `Extentions.kt`). → **R-08**.

## Когда добавлять правило

**Добавить новый раздел** здесь если:
- Это правило **повторяется** в 2+ файлах (AGENTS.md, CLAUDE.md, constitution.md).
- Это правило **может быть проверено** через grep/guard-скрипт.
- Изменение в **исходном коде** (Dockerfile, package.json, build.gradle.kts)
  может нарушить это правило.

**Не добавлять** если:
- Это **knowledge-fact** (не правило, а описание).
- Это **специфичная настройка** одной машины (→ AGENTS.md § «Машинно-специфичные исключения»).
- Это **experiment-specific** (Pass 379+: можно в `specs/<NNN>-<slug>/`).

## Compliance

- [ ] Каждое правило в формате `Rule/Protocol/Failure/Enforcement`.
- [ ] Source-of-truth указан (файл + строка или ADR).
- [ ] cross-reference в соответствующий ADR или domain компонент.
- [ ] Правило enforce'ится через guard-скрипт **ИЛИ** задокументировано почему нет.
