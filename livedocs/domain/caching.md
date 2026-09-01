---
status: Active
slug: caching
type: bounded-context
related:
  - ../domain/stats.md
  - ../domain/catalog.md
  - ../features/286-author-song-counts-cache.md
  - ../features/248-authors-tiles-cache.md
  - ../features/289-fix-statbysong-cache-on-cold-start.md
  - ../architecture/observability.md
---

# Domain: Caching

> Bounded context для in-memory и предрассчитанных кешей Karaoke.

## Что делает

Хранение предрассчитанных агрегатов и runtime-кешей для ускорения ответов на горячих эндпоинтах. Caching patterns: in-memory `AtomicInteger`, фоновое обновление по cron, денормализация в БД (specs/286, 248), async cold-start refresh (specs/289).

## Ключевые компоненты

- **`StatBySong`** (`karaoke-web/.../StatBySong.kt`) — счётчики `total` / `collection` / `freeNow` / `subscriptionOnly` / `inWork` для главной страницы и Закромов. После фичи 289 — async cold-start refresh через `ScheduledExecutorService` + `AtomicBoolean` single-flight guard.
- **`StatsCacheScheduler`** (`karaoke-web/.../services/StatsCacheScheduler.kt`) — cron-обновление кешей раз в час + ежеминутная проверка dirty-флага.
- **`AuthorsCache`** (через `tbl_authors.total_songs_count` / `ready_songs_count`) — денормализованные счётчики песен по автору (specs/286).
- **`AuthorTilesCache`** (`karaoke-web/...`) — кеш тайлов авторов для главной (specs/248).

## Паттерны

| Паттерн | Где | Зачем |
|---------|-----|-------|
| In-memory `AtomicInteger` | `StatBySong` | Быстрое чтение без обращения к БД |
| Cron-обновление | `StatsCacheScheduler` | Обновление раз в час для свежести данных |
| Dirty-флаг | `StatBySong.dirty` | Инвалидация при изменении сущности (через `markDirty()` из karaoke-app) |
| Денормализация в БД | `tbl_authors.{total,ready}_songs_count` | SUM по 126 авторам (~2 мс) вместо full-scan |
| Async cold-start | `bgExecutor` (specs/289) | HTTP-тред возвращает fallback (0) за <100 мс |
| Single-flight guard | `AtomicBoolean refreshing` | Только один поток запускает refresh |

## Логирование

Фича 289 добавила SLF4J через категорию `infra.cache.statbysong`:
- `WARN cache:coldStart triggering background refresh` — холодный старт.
- `INFO cache:refreshed total=N ... durationMs=X` — успешный refresh.
- `WARN cache:refreshFailed error="..."` — ошибка refresh.

## Код

- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/StatBySong.kt`
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/StatsCacheScheduler.kt`
- `deploy/karaoke-db/44_author_song_counts.sql` (specs/286) — `tbl_authors.total_songs_count` / `ready_songs_count`.

## История

- Создан: 2026-09-01 (фича 289-fix-statbysong-cache-on-cold-start — добавлены LiveDocs cross-links).