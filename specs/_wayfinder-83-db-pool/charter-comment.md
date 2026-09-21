# Charter Summary

> **Pass**: Wayfinder charting for #83 — 2026-09-13.
> **Status**: карта и 3 тикета созданы, claimed. Research-субагент запущен в background.

## Что сделано

1. **Knowledge-first MUST #0 выполнен**: прочитаны `knowledge/README.md`, `domains/README.md`, `domains/persistence/domain.md`, `domains/health/components/health-report.md`, `system/frontend/vuex-patterns.md`, `KaraokeConnection.kt`, `Connection.kt`, `SongsTable.vue` (HR-очередь), `ApiController.kt` (HR-endpoint).

2. **Лог `karaoke-app` подтверждает** проблему: `KaraokeConnection getConnection Exception: FATAL: sorry, too many clients already` на `pool-2-thread-156/158/160/...` — async-fill треды `StorageMetadataCache`.

3. **Корневая гипотеза** (требует подтверждения research #85): ThreadLocal-per-thread JDBC + параллельные HR-запросы превышают `max_connections=100`.

## Карта и тикеты

| ID | Type | Subject | Status |
|---|---|---|---|
| 84 | wayfinder:map | Connection pool exhaustion при быстром переключении страниц | In progress (claimed) |
| 85 | wayfinder:research | Корневая причина: кто и как забивает DB connection pool | In progress (claimed, AFK запущен) |
| 86 | wayfinder:prototype | Прототип приоритезированной HR-очереди в SongsTable.vue | In progress (claimed) |
| 87 | wayfinder:grilling | Метрики успеха и поведение приоритезации | In progress (claimed) |

## Frontier

- **#87 (grilling)** — HITL, готов к следующей сессии.
- **#85 (research)** — AFK, субагент работает в background.

## Следующая сессия

Должна:
1. Проверить результат research #85 (отчёт в `research/83-db-pool-root-cause/REPORT.md`).
2. Выбрать frontier ticket.
3. Если research дал ответ — graduate в `[wayfinder:task]` для реализации фикса #83.
4. После фикса — `add-comment` + `mark-review` + `close-issue`.

## Файлы

- `specs/_wayfinder-83-db-pool/_charter.md` — детальный charter.
- `research/83-db-pool-root-cause/` — рабочая директория research.

— boss (wayfinder charting)