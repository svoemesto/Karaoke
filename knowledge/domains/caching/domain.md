---
id: domain-caching
title: "Domain: Caching (Кеширование)"
status: Active
slug: caching
related:
  - ../stats/domain.md
  - ../catalog/domain.md
  - ../publishing/domain.md
---

# Domain: Caching (Кеширование)

> Bounded context для in-memory и предрассчитанных кешей Karaoke.
>

## Обзор контекста (Bounded Context)

Хранение предрассчитанных агрегатов и runtime-кешей для ускорения
ответов на горячих эндпоинтах. Caching patterns:

- In-memory `AtomicInteger`.
- Фоновое обновление по cron.
- Денормализация в БД (specs 286, 248).
- Async cold-start refresh (spec 289).

**Граница**: контекст НЕ отвечает за:

- хранение бизнес-данных (домены сами знают, что нужно кешировать);
- HTTP-cache (CDN, `Cache-Control` заголовки) — это уровень выше.

## Ubiquitous Language | Единый язык

| Термин | Определение | Пример |
| --- | --- | --- |
| **`StatBySong`** | In-memory счётчики главной страницы | `StatBySong.totalSongs.get()` |
| **`StatsCacheScheduler`** | Cron-обновление кешей | `@Scheduled(cron = "0 0 * * * *")` |
| **`AuthorsCache`** | Денормализованные счётчики `tbl_authors.total_songs_count` | specs 286 |
| **`AuthorTilesCache`** | Кеш тайлов авторов для главной страницы | specs 248 |
| **`StorageMetadataCache`** | In-memory TTL-кеш `fileExists` / `fileIsActual` / `getFileInfo` в `karaoke-app` (2 инстанса: local + remote MinIO) | spec 344, OpenProject #69 |
| **Cold-start** | HTTP-тред возвращает fallback (0) за <100 мс | specs 289 |
| **Single-flight guard** | `AtomicBoolean refreshing` — только один поток запускает refresh | specs 289 |
| **Dirty-флаг** | Инвалидация при изменении сущности | `markDirty()` |

Полный реестр паттернов — см. [caching-patterns](components/caching-patterns.md).
Специфика `AuthorsCache` — см. [author-cache](components/author-cache.md).

## Ключевые компоненты

- **`StatBySong`** (`karaoke-web/.../StatBySong.kt`) — счётчики
  `total` / `collection` / `freeNow` / `subscriptionOnly` / `inWork`
  для главной страницы и Закромов. После спеки 289 — async cold-start
  refresh через `ScheduledExecutorService` + `AtomicBoolean`
  single-flight guard.
- **`StatsCacheScheduler`** (`karaoke-web/.../services/StatsCacheScheduler.kt`) —
  cron-обновление кешей раз в час + ежеминутная проверка dirty-флага.
- **`AuthorsCache`** (через `tbl_authors.total_songs_count` /
  `ready_songs_count`) — денормализованные счётчики песен по автору
  (spec 286).
- **`AuthorTilesCache`** (`karaoke-web/...`) — кеш тайлов авторов для
  главной (spec 248).

## Domain Invariants | Инварианты и правила бизнеса

1. **`AtomicInteger` для счётчиков**: read-mostly данные через
   `get()` — lock-free.
2. **Cron-обновление**: раз в час для свежести данных; допускается
   stale до 1 часа.
3. **Dirty-флаг**: при изменении сущности (Song, Album, Author)
   `markDirty()` планирует немедленный refresh.
4. **Async cold-start**: HTTP-тред **никогда** не блокируется на refresh.
   Возвращается fallback (0 для счётчиков), refresh в `bgExecutor`.
5. **Single-flight guard**: `AtomicBoolean refreshing` — только один
   поток запускает refresh, остальные видят `false` и не запускаются.

## Паттерны (краткий обзор)

| Паттерн | Где | Зачем |
| --- | --- | --- |
| In-memory `AtomicInteger` | `StatBySong` | Быстрое чтение без обращения к БД |
| Cron-обновление | `StatsCacheScheduler` | Обновление раз в час для свежести данных |
| Dirty-флаг | `StatBySong.dirty` | Инвалидация при изменении сущности (через `markDirty()` из karaoke-app) |
| Денормализация в БД | `tbl_authors.{total,ready}_songs_count` | SUM по 126 авторам (~2 мс) вместо full-scan |
| Async cold-start | `bgExecutor` (spec 289) | HTTP-тред возвращает fallback (0) за <100 мс |
| Single-flight guard | `AtomicBoolean refreshing` | Только один поток запускает refresh |

Детальное описание каждого паттерна — в [caching-patterns](components/caching-patterns.md).

## Логирование

Спека 289 добавила SLF4J через категорию `infra.cache.statbysong`:

- `WARN cache:coldStart triggering background refresh` — холодный старт.
- `INFO cache:refreshed total=N ... durationMs=X` — успешный refresh.
- `WARN cache:refreshFailed error="..."` — ошибка refresh.

Детали категорий — в [monitoring domain log-categories](../monitoring/components/log-categories.md).

## Структура компонентов (C4 L3)

- [caching-patterns](components/caching-patterns.md) — детальное
  описание 6 паттернов с примерами кода.
- [author-cache](components/author-cache.md) — специфика
  `AuthorsCache` (денормализация в БД) + `AuthorTilesCache`
  (in-memory кеш тайлов).

## Публичные контракты (API)

### Internal API

- `StatBySong.snapshot()` — синхронное чтение in-memory счётчиков.
- `StatBySong.refresh()` — принудительный refresh (используется в
  `markDirty()`).
- `StatsCacheScheduler.refresh()` — cron-обновление кешей.

### Внешний артефакт

- Денормализованные поля в БД: `tbl_authors.total_songs_count`,
  `tbl_authors.ready_songs_count`.

## Код (физическая реализация)

- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/StatBySong.kt`
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/StatsCacheScheduler.kt`
- `deploy/karaoke-db/44_author_song_counts.sql` (spec 286) —
  `tbl_authors.total_songs_count` / `ready_songs_count`.
