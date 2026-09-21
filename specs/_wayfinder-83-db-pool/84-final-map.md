# #84 — Final Map Update (Pass 372)

> **Status**: ✅ Карта effort'а #83 завершена, 2026-09-13.
> **Все дочерние тикеты**: CLOSED (см. ниже).

## Что сделано в этом effort

### Цепочка выполнения

1. **#85 [research]** (CLOSED) — root cause найден: `cacheFillerExecutor` без лимита создаёт
   новый ThreadLocal-connection при каждом REMOTE cache miss.
2. **#89 [task A]** (CLOSED, PR #470) — `cacheFillerExecutor maxPoolSize=16`.
3. **#90 [task B]** (CLOSED, PR #472) — `server.tomcat.threads.max: 50` для `karaoke-web`.
4. **#86 [prototype]** (CLOSED) — приоритезированная HR-очередь: **не нужна** после A+B.
5. **#87 [grilling]** (CLOSED) — метрики: **не нужны** после A+B.
6. **#83 [исходный баг]** (CLOSED) — устранён фиксами A+B.
7. **Governance** (Pass 372, PR #471) — правило `GRADLE_USER_HOME` теперь применяется транзитивно.

### Финальные артефакты

| Артефакт | Путь / URL |
|---|---|
| Карта | [OpenProject #84](https://localhost:8080/work_packages/84) |
| Исходный тикет | [OpenProject #83](https://localhost:8080/work_packages/83) — CLOSED |
| Research | `research/83-db-pool-root-cause/REPORT.md` |
| Resolution | `specs/_wayfinder-83-db-pool/85-research-resolution.md` |
| Charter | `specs/_wayfinder-83-db-pool/_charter.md` |
| Report A | `specs/_wayfinder-83-db-pool/89-report.md` |
| Report B | `specs/_wayfinder-83-db-pool/90-report.md` |
| PR A | [svoemesto/Karaoke#470](https://github.com/svoemesto/Karaoke/pull/470) — MERGED |
| PR B | [svoemesto/Karaoke#472](https://github.com/svoemesto/Karaoke/pull/472) — MERGED |
| Governance PR | [svoemesto/Karaoke#471](https://github.com/svoemesto/Karaoke/pull/471) — ждёт |
| Карта документация | `AGENTS.md` v2.4.0 (Pass 372) |

## Что осталось неизвестным (низкий приоритет)

- `repairExecutor` (4 потока) и `KaraokeProcessWorker` (main loop) — держат ThreadLocal
  connections, но их вклад мал (см. research/83-db-pool-root-cause).
- Полная миграция ThreadLocal → HikariCP — **out of scope** для этого effort'а,
  отдельная спека.

## Что нужно от владельца

- Применить **governance PR #471** (Pass 372 — правило транзитивности `GRADLE_USER_HOME`).
  Это разблокирует guard hook для будущих коммитов.
- Перезапустить `karaoke-app` и `karaoke-web` для активации фиксов A+B.
- После деплоя — проверить acceptance criteria из #89-report.md и #90-report.md:
  - `docker logs --since 5m ... | grep -c "getConnection Exception"` = 0 (обоих контейнеров).
  - `pg_stat_activity` показывает ≤50 idle connections от каждого контейнера.

— final map для #84