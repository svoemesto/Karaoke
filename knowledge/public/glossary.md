# Glossary — Ubiquitous Language Karaoke

> **Домен**: `knowledge/public/`
> **Назначение**: сводный глоссарий терминов Karaoke для новых
> разработчиков и внешних читателей. Источник истины для каждого
> термина — соответствующий домен в `knowledge/domains/`.

Этот глоссарий — **входная точка** для понимания Karaoke. Термины
сгруппированы по доменам. Полные определения и инварианты — в
`knowledge/domains/<domain>/`.

## Identity (пользователи, авторизация)

| Термин | Определение | Источник |
| --- | --- | --- |
| **SiteUser** | Пользователь сайта (читатель, редактор, админ) | [identity](../domains/identity/domain.md) |
| **Session** | Серверная сессия Spring Security (cookie) | [identity](../domains/identity/domain.md) |
| **Roles** | `ADMIN`, `EDITOR`, `USER`, `GUEST` | [identity dictionaries](../domains/identity/components/dictionaries.md) |
| **canSelfAssign** | Флаг: редактор может брать задания | [identity](../domains/identity/domain.md) |
| **isSpecialOrder** | Boolean-поле, ловушка Jackson | [identity dictionaries](../domains/identity/components/dictionaries.md) |
| **JWT** | Не используется (только cookies) | [identity](../domains/identity/domain.md) |
| **principal** | Текущий пользователь в Spring Security | [security-config](../domains/identity/components/security-config.md) |

## Catalog (каталог)

| Термин | Определение | Источник |
| --- | --- | --- |
| **Song (Песня)** | Единица каталога, AR | [catalog](../domains/catalog/domain.md) |
| **Album (Альбом)** | Коллекция песен одного исполнителя | [catalog](../domains/catalog/domain.md) |
| **Author (Исполнитель)** | Музыкальный исполнитель | [catalog](../domains/catalog/domain.md) |
| **Genre (Жанр)** | Музыкальный жанр, справочник | [catalog](../domains/catalog/domain.md) |
| **IdStatus** | Статус обработки 1..6 | [catalog dictionaries](../domains/catalog/components/dictionaries.md) |
| **SourceMarkers** | Строка маркеров (формат `[time]text`) | [catalog dictionaries](../domains/catalog/components/dictionaries.md) |
| **SKIP** | Тег скрытой песни (заглушка) | [catalog dictionaries](../domains/catalog/components/dictionaries.md) |
| **Эфирная песня** | `publishDate` истёк → доступна всем | [catalog](../domains/catalog/domain.md) |

## Rendering (рендеринг)

| Термин | Определение | Источник |
| --- | --- | --- |
| **Render** | Задача KaraokeProcess с типом `RENDER_MP4_*` | [rendering](../domains/rendering/domain.md) |
| **mko (Melt Object)** | Kotlin-класс, генерирующий MLT-property | [mlt-pipeline](../domains/rendering/components/mlt-pipeline.md) |
| **MLT project** | composition слоёв (mko) + аудио | [mlt-pipeline](../domains/rendering/components/mlt-pipeline.md) |
| **RenderVersion** | enum `LYRICS` / `KARAOKE` / `DEMO` | [rendering dictionaries](../domains/rendering/components/dictionaries.md) |
| **MLT_CPU_LIMIT** | Per-render CPU limit (через Docker `--cpus`) | [rendering dictionaries](../domains/rendering/components/dictionaries.md) |
| **HEAVY_RENDER** | `threadId = 0` — основной lane для тяжёлого рендера | [rendering dictionaries](../domains/rendering/components/dictionaries.md) |

## Processing (производство)

| Термин | Определение | Источник |
| --- | --- | --- |
| **MLT (melt)** | Формат проекта видеоредактора melt | [processing](../domains/processing/domain.md) |
| **Стем (Stem)** | Разделённая аудио-дорожка (vocals / acc) | [processing dictionaries](../domains/processing/components/karaoke-properties.md) |
| **Demucs** | ML-модель стем-сепарации | [processing](../domains/processing/domain.md) |
| **Sheetsage** | ML-модель key/BPM/chords | [processing](../domains/processing/domain.md) |
| **Playwright** | Headless Chromium для рендера кадров | [playwright-rendering](../domains/processing/components/playwright-rendering.md) |
| **JPEG quality 95** | Оптимизация: PNG → JPEG = ×3 скорость | [playwright-rendering](../domains/processing/components/playwright-rendering.md) |
| **KaraokeProperties** | ~150 настраиваемых параметров рендера | [karaoke-properties](../domains/processing/components/karaoke-properties.md) |

## Publishing (публичный доступ)

| Термин | Определение | Источник |
| --- | --- | --- |
| **Эфир (On-Air)** | Песня в открытом доступе (`publishDate` истёк) | [publishing](../domains/publishing/domain.md) |
| **Exclusive** | Доступна только по подписке | [publishing dictionaries](../domains/publishing/components/dictionaries.md) |
| **premium-only** | Доступна только подписчикам | [publishing dictionaries](../domains/publishing/components/dictionaries.md) |
| **Подписка (Subscription)** | Premium-доступ на N дней | [publishing](../domains/publishing/domain.md) |
| **Visitor (посетитель)** | Один визит на сайт | [publishing dictionaries](../domains/publishing/components/dictionaries.md) |
| **BotScore** | 0.0..1.0, вероятность что посетитель — бот | [publishing dictionaries](../domains/publishing/components/dictionaries.md) |
| **StatBySong** | Счётчики главной страницы | [stats-cache](../domains/publishing/components/stats-cache.md) |
| **Grandfathered** | Старая песня, ставшая эфирной до введения premium | [publishing](../domains/publishing/domain.md) |
| **Воронка** | visitor→registration→premium | [event-funnel](../domains/stats/components/event-funnel.md) |

## Editorial (задания редакторов)

| Термин | Определение | Источник |
| --- | --- | --- |
| **Self-assign** | Редактор сам назначает себя на задание | [editorial dictionaries](../domains/editorial/components/dictionaries.md) |
| **EditorAssignment** | Назначение редактора на песню | [editorial](../domains/editorial/domain.md) |
| **ReviewTask** | Задача на ревью при апруве | [editorial dictionaries](../domains/editorial/components/dictionaries.md) |
| **Render (idStatus=5)** | Апрув → запуск LYRICS/KARAOKE рендера | [editorial dictionaries](../domains/editorial/components/dictionaries.md) |
| **Demo (idStatus=6)** | Апрув → запуск DEMO рендера + новости | [editorial dictionaries](../domains/editorial/components/dictionaries.md) |
| **Race protection** | `SELECT FOR UPDATE` для защиты от гонок | [assignment-lifecycle](../domains/editorial/components/assignment-lifecycle.md) |

## Stats (аналитика)

| Термин | Определение | Источник |
| --- | --- | --- |
| **tbl_events** | Append-only таблица событий | [stats](../domains/stats/domain.md) |
| **BotDetectionService** | Классификация трафика по BotScore | [stats dictionaries](../domains/stats/components/dictionaries.md) |
| **EventType** | VISIT, REGISTRATION, PREMIUM_PURCHASE, PLAY_START, ... | [stats dictionaries](../domains/stats/components/dictionaries.md) |
| **REAL_USER / GOOD_BOT / BAD_BOT** | Сегменты трафика по BotScore | [stats dictionaries](../domains/stats/components/dictionaries.md) |
| **Funnel (Воронка)** | visitor → registration → premium | [event-funnel](../domains/stats/components/event-funnel.md) |

## Caching (кеширование)

| Термин | Определение | Источник |
| --- | --- | --- |
| **StatBySong** | In-memory счётчики главной страницы | [caching](../domains/caching/domain.md) |
| **StatsCacheScheduler** | Cron-обновление кешей | [caching-patterns](../domains/caching/components/caching-patterns.md) |
| **AuthorsCache** | Денормализованные счётчики `tbl_authors.total_songs_count` | [author-cache](../domains/caching/components/author-cache.md) |
| **Cold-start** | HTTP-тред возвращает fallback (0) за <100 мс | [caching-patterns](../domains/caching/components/caching-patterns.md) |
| **Single-flight guard** | `AtomicBoolean refreshing` — только один поток запускает refresh | [caching-patterns](../domains/caching/components/caching-patterns.md) |
| **Dirty-флаг** | Инвалидация при изменении сущности | [caching-patterns](../domains/caching/components/caching-patterns.md) |

## Monitoring (мониторинг)

| Термин | Определение | Источник |
| --- | --- | --- |
| **MonitorCheck** | Интерфейс одной проверки | [monitoring](../domains/monitoring/domain.md) |
| **MonitorAlert** | Структура алерта (severity + message) | [monitor-checks](../domains/monitoring/components/monitor-checks.md) |
| **Stalled** | Задача/лейн не двигается > N минут | [monitor-checks](../domains/monitoring/components/monitor-checks.md) |
| **infra.prod.ping** | SLF4J-категория для HTTP-пинга сайта | [log-categories](../domains/monitoring/components/log-categories.md) |
| **infra.prod.db** | SLF4J-категория для JDBC-пинга прод-БД | [log-categories](../domains/monitoring/components/log-categories.md) |
| **infra.cache.statbysong** | SLF4J-категория для кеша статистики | [log-categories](../domains/monitoring/components/log-categories.md) |

## Архитектурные слои

| Термин | Определение | Источник |
| --- | --- | --- |
| **karaoke-app** | Kotlin/Spring backend (админ-API, ML-пайплайн) | [L2-containers](../../livedocs/architecture/L2-containers.md) |
| **karaoke-web** | Kotlin/Spring backend (админский web, security) | [L2-containers](../../livedocs/architecture/L2-containers.md) |
| **webvue3** | Vue 3 admin SPA | [L2-containers](../../livedocs/architecture/L2-containers.md) |
| **karaoke-public** | Vue 3 публичный сайт | [L2-containers](../../livedocs/architecture/L2-containers.md) |
| **PostgreSQL** | Основная БД (raw JDBC, без JPA) | [ADR-0001](../adr/0001-raw-jdbc.md) |
| **MinIO** | S3-compatible storage для стемов, MP4 | [L2-containers](../../livedocs/architecture/L2-containers.md) |
| **MLT/melt** | Видеоредактор для караоке-видео | [ADR-0002](../adr/0002-mlt-instead-of-ffmpeg.md) |
| **Demucs** | ML-модель стем-сепарации | [processing](../domains/processing/domain.md) |
| **Sheetsage** | ML-модель key/BPM/chords | [processing](../domains/processing/domain.md) |

## Связанные документы

- [README.md](../README.md) — главная SSoT-карта.
- [domains/README.md](../domains/README.md) — реестр доменов.
- [onboarding.md](onboarding.md) — для новых разработчиков.
