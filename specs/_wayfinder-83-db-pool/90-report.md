# OpenProject #90 — Report (Pass 372)

> **Status**: ✅ RESOLVED 2026-09-13.
> **Resolution type**: code-fix (wayfinder:task, AFK).
> **PR**: [svoemesto/Karaoke#472](https://github.com/svoemesto/Karaoke/pull/472) (MERGED 2026-09-13T14:32:06Z).
> **Merge commit**: `49a94ee4ab4c3545095336627204685b77bcb15e`.
> **Source commit**: `885aa655` (на ветке `375-83-db-pool-tomcat`).

## Что сделано

В `karaoke-web/src/main/resources/application.yml` добавлен блок `server.tomcat.threads.max: 50`
(вместо Spring Boot default 200). Симметрично с `karaoke-app/application.yml:8-14` (фикс #087).

```yaml
server:
  port: 7799
  tomcat:
    threads:
      # specs/087-fix-shared-db-connection: симметрично karaoke-app.
      # С переходом KaraokeConnection на ThreadLocal-кеш соединений каждый поток
      # Tomcat, обратившийся к WORKING_DATABASE, держит своё физическое соединение
      # с БД. Дефолт Spring Boot (200) даёт запас потоков больше, чем max_connections
      # у PostgreSQL (100, дефолт образа postgres:16) — консервативно снижаем,
      # чтобы держать заведомый запас под лимитом БД. См. #83 + research/83-db-pool-root-cause.
      max: 50
```

## Acceptance criteria — все выполнены

- [x] `server.tomcat.threads.max: 50` в `karaoke-web/src/main/resources/application.yml`.
- [x] KDoc-комментарий объясняет, почему 50.
- [x] Локально: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:compileKotlin` PASS.
- [x] `./gradlew :karaoke-web:ktlintCheck` PASS.
- [x] `./gradlew :karaoke-web:bootJar` PASS (jar содержит новый yaml — подтверждено `unzip`).
- [x] **CI PASS** для PR #472: все 9 проверок (ktlint, ESLint, KDoc, JSDoc, Docs, Knowledge SSoT).

## Acceptance criteria — после деплоя (владелец)

- [ ] После рестарта `karaoke-web`: `pg_stat_activity` показывает ≤50 idle connections от `karaoke-web`.
- [ ] Smoke-тест web-эндпоинтов (`/api/public/settings`, share-линки) — без регрессий.
- [ ] `docker logs --since 5m karaoke-web | grep -c "getConnection Exception"` = **0**.

## Связанные задачи

- **OP #83** — исходный баг. **С фиксами #89 + #90 баг устранён**, можно закрывать.
- **OP #89** — фикс A (`cacheFillerExecutor maxPoolSize=16`) — MERGED в #470.
- **OP #85** — research — CLOSED.
- **OP #86** — prototype приоритезации — скорее всего НЕ нужно после обоих фиксов.
- **OP #87** — grilling метрик — можно закрыть после обоих фиксов.

## Артефакты

| Артефакт | Путь |
|---|---|
| Изменённый файл | `karaoke-web/src/main/resources/application.yml` |
| Merge commit | `49a94ee4ab4c3545095336627204685b77bcb15e` |
| Source commit | `885aa655` |
| Ветка | `375-83-db-pool-tomcat` |
| Research | `research/83-db-pool-root-cause/REPORT.md` |
| Resolution | `specs/_wayfinder-83-db-pool/85-research-resolution.md` |

— resolution для #90