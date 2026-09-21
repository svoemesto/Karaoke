# #83 Wayfinder Charter — DB Connection Pool Exhaustion

> **Сессия**: 2026-09-13. Wayfinder Charting pass.
> **Карта**: OpenProject #84.
> **Дочерние тикеты**: #85 (research), #86 (prototype), #87 (grilling).
> **Статус карты**: In progress (claimed by ai-agent, 2026-09-13).

## Что зафиксировано в этой сессии

1. **Knowledge-first MUST #0 выполнен**:
   - Прочитан `knowledge/README.md`, `knowledge/domains/README.md`.
   - Прочитаны домены `persistence`, `health`, `system/frontend/vuex-patterns`.
   - Проверены прецеденты #65, #69, #75 (MinIO), #087 (ThreadLocal), #234/#236 (connection leak).
   - Прочитан `KaraokeConnection.kt` — подтверждена ThreadLocal-per-thread архитектура.
   - Прочитан `SongsTable.vue:1312-1338` — текущая HR-очередь `HR_MAX_CONCURRENT=3`.
   - Прочитан `ApiController.kt:7674-7686` — `getHealthReportList` → `recomputeAndBroadcast`.

2. **Лог контейнера `karaoke-app` подтверждает задачу**:
   ```
   KaraokeConnection getConnection Exception: FATAL: sorry, too many clients already
   ```
   Видно на `pool-2-thread-156/158/160/182/183/184/188/...` — это треды async-fill `StorageMetadataCache`.

3. **Корневая гипотеза** (требует подтверждения через research #85):
   - `KaraokeConnection` через `ThreadLocal` даёт **каждому** worker-thread собственный JDBC-connection.
   - При HR-каскаде: HTTP-worker-thread (Tomcat) + pool-2 (StorageMetadataCache async) × N параллельных запросов.
   - `max_connections=100` Postgres быстро исчерпывается.

4. **Карта #84** + 3 тикета созданы, claimed:
   - **#85** [wayfinder:research] — root cause analysis (AFK, запущен субагент).
   - **#86** [wayfinder:prototype] — прототип приоритезированной очереди (HITL).
   - **#87** [wayfinder:grilling] — метрики и поведение приоритезации (HITL).

## Frontier (открыто, unblocked, claimable)

- **#87** (grilling) — HITL, можно запускать параллельно с research.
- **#85** (research) — AFK, субагент запущен `2026-09-13`.

## Not yet specified (в карте #84 → Not yet specified)

- Frontend-only лимит vs backend batching vs HikariCP migration vs Postgres tuning.
- Приоритизация страниц — насколько UX-критично.

## Out of scope (в карте #84 → Out of scope)

- Полная миграция ThreadLocal → HikariCP (отдельная спека).
- Замена Spring Boot runtime.
- Изменение `max_connections` без согласования.

## Следующие сессии

Следующая сессия должна:
1. **Выбрать frontier ticket**: приоритет — `#89` (фикс A — `cacheFillerExecutor`), затем `#90` (фикс B — `karaoke-web` Tomcat). Оба `wayfinder:task` (AFK, можно брать сразу).
2. После фикса — перезапустить соответствующий контейнер, прогнать `pre-commit run --all-files`, проверить логи (`docker logs --tail 500 karaoke-app | grep -c "getConnection Exception"` = 0 за 5 мин).
3. Опубликовать `report.md` через `add-comment` + `mark-review` + `close-issue` для #89 и #90.
4. **После обоих фиксов** — закрыть исходный #83 (`close-issue 83`).
5. Оценить: нужен ли `#86` (prototype приоритезации) — после фиксов A+B скорее всего нет, но владелец может настоять. И `#87` (grilling метрик) — можно сразу закрыть как «выполнено фиксами».

## Текущее состояние тикетов

| ID | Тип | Статус | Заметка |
|---|---|---|---|
| 84 | wayfinder:map | In progress | Обновлён (Decisions so far + Not yet specified) |
| 85 | wayfinder:research | **Closed** | Resolution записан, узкое место определено |
| 86 | wayfinder:prototype | In progress | Заблокирован фиксом A (#89) — после фикса можно закрыть как «не нужно» |
| 87 | wayfinder:grilling | In progress | Метрики — может быть закрыт после фиксов |
| 89 | wayfinder:task [A] | In progress | **СЛЕДУЮЩИЙ** — фикс `cacheFillerExecutor` |
| 90 | wayfinder:task [B] | In progress | Можно брать параллельно с #89 |
| 83 | (исходный) | New | Закрыть после фиксов #89 + #90 |

## Файлы сессии

- `specs/_wayfinder-83-db-pool/_charter.md` — этот файл (charter-резюме).
- `research/83-db-pool-root-cause/` — рабочая директория research-субагента.