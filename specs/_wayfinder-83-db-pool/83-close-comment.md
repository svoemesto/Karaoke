# OpenProject #83 — Close Reason (Pass 372)

> **Status**: ✅ RESOLVED 2026-09-13.
> **Закрыт как**: duplicate-by-resolution. Исходная проблема устранена через дочерние тикеты.

## Resolution

**Корневая причина** (`OP #85` — research, CLOSED): `cacheFillerExecutor` в `StorageMetadataCache.kt:65-68`
был неограничен (`maxPoolSize=Integer.MAX_VALUE`); каждый REMOTE cache miss создавал новый
ThreadLocal-connection к Postgres. При быстрое переключение страниц в `SongsTable.vue` это превышало
`max_connections=100` за минуты.

**Фикс A** (`OP #89` — task, CLOSED): `cacheFillerExecutor` ограничен `maxPoolSize=16`.
PR [svoemesto/Karaoke#470](https://github.com/svoemesto/Karaoke/pull/470), merge `152cff82`.

**Фикс B** (`OP #90` — task, CLOSED): `server.tomcat.threads.max: 50` для `karaoke-web`
(симметрично `karaoke-app`). PR [svoemesto/Karaoke#472](https://github.com/svoemesto/Karaoke/pull/472),
merge `49a94ee4`.

**Governance amendment** (`Pass 372`, отдельный PR [svoemesto/Karaoke#471](https://github.com/svoemesto/Karaoke/pull/471)):
правило `GRADLE_USER_HOME` теперь применяется транзитивно (pre-commit, deploy/do.sh).

## Acceptance criteria

После деплоя обоих фиксов (`#89` + `#90`):

- [ ] `docker logs --since 5m karaoke-app | grep -c "getConnection Exception"` = **0**
- [ ] `docker exec karaoke-db psql ... "SELECT count(*), client_addr FROM pg_stat_activity GROUP BY client_addr"` —
  суммарно ≤87 (текущий baseline) под нагрузкой.
- [ ] Симметрично для `karaoke-web` (после фикса B): ≤50 idle connections от karaoke-web.

## Дочерние тикеты

- `OP #85` research — **CLOSED**.
- `OP #89` task A — **CLOSED** (PR #470).
- `OP #90` task B — **CLOSED** (PR #472).
- `OP #86` prototype (приоритезация) — будет закрыт как «не нужно после фиксов A+B».
- `OP #87` grilling (метрики) — будет закрыт как «не нужно после фиксов A+B».

## Связанные

- **OpenProject #84** (wayfinder:map) — карта effort'а, обновляется.
- **research** — `research/83-db-pool-root-cause/REPORT.md`.
- **Governance** — `AGENTS.md` v2.4.0 (Pass 372).

— close для #83