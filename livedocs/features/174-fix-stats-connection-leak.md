---
status: Active
slug: 174-fix-stats-connection-leak
related:
  - ../domain/publishing.md
  - ../architecture/L3-components.md
  - ../architecture/data-sync.md
  - ../../specs/174-fix-stats-connection-leak/spec.md
  - ../../archive/docs/features/stats.md
  - ../architecture/observability.md
  - ../domain/stats.md
---

# 174 — Починить flood JDBC-соединений в «Статистике» (LiveDoc)

> Drill-down — [specs/174-fix-stats-connection-leak/spec.md](../../specs/174-fix-stats-connection-leak/spec.md).

## Что делает

При переходе админа на вкладку «Статистика» (`StatsView.vue`) в логе
`karaoke-app` шёл **каскад** `KaraokeConnection.getConnection Exception:
FATAL: sorry, too many clients already`. Через короткое время Postgres
переставал отдавать соединения — дашборд показывал пустые вкладки, параллельные
пользователи `karaoke-public` получали `503/504`.

**Корневая причина** — «слоёный пирог»:

1. `StatsView.vue:mounted()` → `reloadAll()` запускает **11 параллельных**
   HTTP-запросов (summary / timeseries / by-type / by-detail / channels /
   countries / referrers / top-users / by-song / webevents / top-listened /
   monetization / monetization-top-songs).
2. `StatsController` каждый endpoint создаёт свежий `Connection` через `resolveDb(target)`,
   а `Connection.local()/remote()` возвращает `new` (см. KDoc `Connection.kt:60-98`).
3. `withDb { ... }` уже был в `StatsController:34-47` (try-finally с close), но
   не покрывал все пути.

**Фикс**: интегрировать запросы (batching), переиспользовать `Connection` через
`ThreadLocal`, добавить лимит параллельных запросов в StatsView.vue, никогда
не создавать `Connection` без `close` в finally.

## User Stories (краткий список)

- **US1** (P1): В логе `karaoke-app` нет cascade `FATAL: too many clients` при открытии «Статистики».
- **US2** (P1): Параллельные пользователи `karaoke-public` НЕ получают 503/504 при открытой админке.
- **US3** (P2): После `Unmount` компонента StatsView все JDBC-соединения освобождаются.

## Functional Requirements (указатель)

- **FR-001**: Все `Connection` в `StatsController` обёрнуты в `withDb` с гарантированным `close` в `finally`.
- **FR-002**: `StatsView.vue` батчит 11 запросов в ≤ N параллельных (N = ~3) через `Promise.all` с concurrency-лимитом.
- **FR-003**: Cleanup при `beforeUnmount`: отменять pending HTTP-запросы через `AbortController`.
- **FR-004**: Логирование количества активных JDBC-соединений с `KaraokeConnection` (debug-mode).
- **FR-005**: Тест на N одновременных `StatsView:mounted()` не приводит к `too many clients`.

## Acceptance Criteria

- [ ] **AC1**: Открыть `/stats` 10 раз подряд через 1 сек → нет cascade `too many clients` в логе.
- [ ] **AC2**: Параллельно публичный сайт + `/stats` → `karaoke-public` не получает 503/504.
- [ ] **AC3**: При unmount StatsView все JDBC-соединения освобождаются (проверка через `pg_stat_activity`).
- [ ] **AC4**: Latency первого ответа `/stats` остаётся в допустимых пределах (≤ 3 сек).

## Связанные LiveDocs

- Domain: [publishing.md](../domain/publishing.md) (StatBySong — главный потребитель)
- Architecture: [L3-components.md](../architecture/L3-components.md) (StatsController + JDBC singleton)
- Architecture: [data-sync.md](../architecture/data-sync.md) (recordhash не задействован здесь, но принцип O(n) тот же)

## Код

- Backend: `karaoke-app/.../controllers/StatsController.kt` — `withDb { ... }` аудит
- Backend: `karaoke-app/.../KaraokeConnection.kt:42-44` — getConnection (root cause)
- Backend: `karaoke-app/.../Connection.kt:60-98` — статические фабрики (KDoc)
- Frontend: `webvue3/src/views/StatsView.vue:543-583` — `reloadAll()` + cleanup

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14