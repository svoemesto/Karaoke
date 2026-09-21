# OpenProject #89 — Report (Pass 372)

> **Status**: ✅ RESOLVED 2026-09-13.
> **Resolution type**: code-fix (wayfinder:task, AFK).
> **PR**: [svoemesto/Karaoke#470](https://github.com/svoemesto/Karaoke/pull/470) (MERGED 2026-09-13T14:27:53Z).
> **Merge commit**: `152cff82f097788be4d0d12a31411c61c893bbfa`.
> **Source commit**: `95cd3ae1` (на ветке `373-83-db-pool-executor`).

## Что сделано

Заменён неограниченный `Executors.newCachedThreadPool()` (фактически `maxPoolSize=Integer.MAX_VALUE`)
в `StorageMetadataCache.kt:65-68` на явный `ThreadPoolExecutor` с `maxPoolSize=16`.

```kotlin
// Было (KDoc врёт):
private val cacheFillerExecutor =
    Executors.newCachedThreadPool().also { executor ->
        Runtime.getRuntime().addShutdownHook(Thread { executor.shutdown() })
    }

// Стало (соответствует KDoc):
private val cacheFillerExecutor =
    ThreadPoolExecutor(
        0,
        16,
        60L,
        TimeUnit.SECONDS,
        LinkedBlockingQueue(),
    ).also { executor ->
        Runtime.getRuntime().addShutdownHook(Thread { executor.shutdown() })
    }
```

Также:
- Удалён импорт `java.util.concurrent.Executors`.
- Добавлены импорты `ThreadPoolExecutor`, `LinkedBlockingQueue`, `TimeUnit`.
- Обновлён KDoc с явным указанием `#83` и ссылкой на research.

## Acceptance criteria — все выполнены

- [x] `cacheFillerExecutor` имеет **фиксированный** `maxPoolSize=16` (не `Integer.MAX_VALUE`).
- [x] `corePoolSize=0`, `keepAlive=60s` сохранены (поведение cached pool).
- [x] KDoc обновлён: реальные параметры соответствуют коду.
- [x] **CI PASS**: `ktlint (Kotlin/Java)` — 58s, все остальные проверки (Docs, ESLint, KDoc, JSDoc, Knowledge SSoT) — passed.
- [x] **Локально PASS**: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin` PASS, ktlintCheck PASS, bootJar собран.
- [x] KDoc coverage 96.5% (>50%).

## Acceptance criteria — после деплоя (владелец)

- [ ] После перезапуска `karaoke-app`: `docker logs --since 5m karaoke-app | grep -c "getConnection Exception"` = **0**.
- [ ] `docker exec karaoke-db psql ... "SELECT count(*), client_addr FROM pg_stat_activity GROUP BY client_addr;"` — НЕ должно превышать 87 (текущий baseline).
- [ ] `docker logs ... | grep "ol-2-thread" | sort -u | wc -l` — НЕ должно показывать более 16 уникальных `ol-2-thread-N` одновременно.

## Связанные артефакты

| Артефакт | Путь |
|---|---|
| Research (root cause) | `research/83-db-pool-root-cause/REPORT.md` (SHA256 `ac86fc25b991ca7f4bf6564b9a847fcfaab6b3b1268b738de44e4de5ec8f4ffe`) |
| Resolution #85 | `specs/_wayfinder-83-db-pool/85-research-resolution.md` |
| Charter | `specs/_wayfinder-83-db-pool/_charter.md` |
| Map update | `specs/_wayfinder-83-db-pool/84-map-update-1.md` |
| Изменённый файл | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageMetadataCache.kt` |

## Связанные задачи

- **OP #83** — исходный баг (закроется после #90).
- **OP #85** — research (CLOSED, resolution опубликован).
- **OP #86** — prototype (можно закрыть после фикса A как «не нужно»).
- **OP #87** — grilling метрик (можно закрыть после фиксов).
- **OP #90** — фикс B: `server.tomcat.threads.max=50` для `karaoke-web` (TODO, next session).

## Governance amendment (Pass 372, отдельный PR #471)

Поскольку в процессе работы над #89 был выявлен **Pass 372 failure** (агент помнит правило
`GRADLE_USER_HOME` для прямого `./gradlew`, но забывает применить к `pre-commit` и `deploy/do.sh`),
создан **отдельный governance PR**:

- **PR #471**: `docs(governance): Pass 372 — GRADLE_USER_HOME правило применяется транзитивно`
  ([svoemesto/Karaoke#471](https://github.com/svoemesto/Karaoke/pull/471))
- Семвер bump `AGENTS.md` 2.3.0 → 2.4.0.
- Новый guard `tools/check-gradle-user-home.sh`.
- Правки `.pre-commit-config.yaml` (ktlint-hook с env var) и `deploy/do.sh`.

Этот PR — требует отдельного одобрения владельца (CODEOWNERS для `AGENTS.md`).

— resolution для #89