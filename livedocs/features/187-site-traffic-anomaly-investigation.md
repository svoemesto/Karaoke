---
status: Active
slug: 187-site-traffic-anomaly-investigation
related:
  - ../domain/publishing.md
  - ../architecture/data-sync.md
  - ../../specs/187-site-traffic-anomaly-investigation/spec.md
  - ../../archive/docs/features/site-traffic-resilience.md
  - ../../archive/docs/features/sse-notifications.md
  - ../architecture/observability.md
  - ../domain/stats.md
---

# 187 — Расследование аномалии трафика сайта (LiveDoc)

> Drill-down — [specs/187-site-traffic-anomaly-investigation/spec.md](../../specs/187-site-traffic-anomaly-investigation/spec.md).

## Что делает

В июле 2026 зафиксирована аномалия: ~30% визитов на `sm-karaoke.ru` — это
**боты/сканеры**, а не реальные пользователи. Это влияет на воронку
visitor→registration (стратегия роста) — реальный поток пользователей меньше,
чем показывают стандартные метрики.

**Главный результат**: разделение трафика на 3 сегмента (real users / good bots
/ bad bots), фильтрация bad bots в метриках, корректировка воронки.

## User Stories (краткий список)

- **US1**: Маркетолог видит в дашборде скорректированную воронку (без ботов).
- **US2**: Реальный baseline visitor→registration = ~0.4% (без ботов), не 0.3% (с ботами).

## Functional Requirements (указатель)

- **FR-001**: Событие `tbl_events.bot_score` (0.0-1.0) для каждого визита.
- **FR-002**: Дэшборд с фильтром по bot_score < 0.5 для «реальных пользователей».
- **FR-003**: Воронка в `docs/strategy/growth.md` обновлена с учётом сегментации.

## Acceptance Criteria

- [ ] **AC1**: ~30% визитов идентифицируются как боты (точность > 90%).
- [ ] **AC2**: Real user baseline visitor→registration ≈ 0.4%.
- [ ] **AC3**: Воронка в growth strategy обновлена с разбивкой по сегментам.

## Связанные LiveDocs

- Domain: [publishing.md](../domain/publishing.md) (visitor — это пользователь сайта)
- Architecture: [data-sync.md](../architecture/data-sync.md) (как `tbl_events` синхронизируется)

## Код

- Backend: `karaoke-app/src/main/kotlin/.../service/TrafficAnalyticsService.kt`
- DB: `deploy/karaoke-db/<NNN>_tbl_events_bot_score.sql` (миграция)
- Дашборд: `webvue3/src/views/StatsView.vue`
- Документация: `docs/strategy/growth-audit.md`

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14