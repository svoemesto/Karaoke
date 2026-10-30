# Implementation Plan: Устойчивость karaoke-app к исчерпанию памяти

**Branch**: `676-oom-karaoke-app` | **Date**: 2026-10-30 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/676-oom-karaoke-app/spec.md`

**Phase**: 0 (research) и 1 (design) выполнены. Артефакты:
[research.md](research.md) · [data-model.md](data-model.md) ·
[contracts/](contracts/) · [quickstart.md](quickstart.md)

---

## Summary

Три превентивные меры против исчерпания памяти в `karaoke-app` (admin-машина
`nsa-i9`) плюс закрепление инварианта конечности раскладки публикаций тестом
уровня вызывающих.

1. **Блок B (FR-006…FR-008)** — в `deploy/docker-compose-app.yml` появляется
   явный предел памяти контейнера `mem_limit: 8g` и `-Xmx6g` в JVM-опциях;
   пара «heap ↔ предел» видна в репозитории и защищена guard'ом.
2. **Блок C (FR-009…FR-013)** — `-XX:+HeapDumpOnOutOfMemoryError` +
   `-XX:HeapDumpPath=/dumps`, том `./karaoke-db/dumps:/dumps`, политика хранения
   (последние 2 дампа, ≤ 20 ГБ, вытеснение по возрасту) — host-side скрипт +
   systemd-юнит/таймер по образцу `deploy/karaoke-db-backup.*`.
3. **Блок D (FR-014…FR-019)** — 8-я проверка мониторинга `HeapUsageCheck` в
   домене `monitoring`: пороги 75 % / 90 % из конфигурации, алерт в существующий
   канал (`infra.monitor.heap`, формат `event:key=value`), recovery-сообщение,
   состояние в памяти.
4. **Блок A (FR-001…FR-005)** — починка бесконечного цикла уже в master
   (`4d643554`, BR-188); **не переписывается**. Добавляется недостающее:
   регрессионный тест уровня вызывающих `getUnPublicationList` /
   `getSkipedPublicationList` (FR-004) на 500 неопубликованных песнях.
5. **Блок E (FR-020…FR-021)** — 12 документов базы знаний приводятся в
   соответствие фактам, плюс новый ADR, per-feature документ и Linking Protocol
   L3→L2→L1.

**Ключевое техническое решение**: подмена границы JDBC в тесте вместо
изменения product-кода легаси-пути (research.md R6) — так FR-003 («поведение
легаси-пути прежнее») выполняется тривиально.

---

## Technical Context

**Language/Version**: Kotlin 2.2.20 / JDK 17 (модуль `karaoke-app`); рантайм-образ
`sm-karaoke.ru:5443/library/ubuntu:22.04` + `openjdk-17-jre-headless`
(`deploy/karaoke-app/Dockerfile:23`, `:48`)

**Primary Dependencies**: Spring Boot 3.5.6, Spring Scheduling, SLF4J/Logback,
сырой JDBC (`KaraokeConnection`) — новых зависимостей **не добавляем**
(тесты — на Mockito из `spring-boot-starter-test`)

**Storage**: PostgreSQL (локальная БД admin-машины) + bind-mount
`deploy/karaoke-db/dumps` для heap-дампов; **новых персистентных ключей нет**
(FR-019)

**Testing**: JUnit 5 (`useJUnitPlatform()`), Mockito (уже в
`spring-boot-starter-test`); юнит-тесты в `karaoke-app/src/test/kotlin/...`;
проверка деплой-контракта — `tools/check-deploy-hardening.sh` + bash-скрипт с
self-test

**Target Platform**: Linux (admin-машина `nsa-i9`, Docker + docker-compose)

**Project Type**: backend-сервис (`karaoke-app`) + контур развёртывания

**Performance Goals**: проверка мониторинга укладывается в бюджет тика
(< 5 с, фактически — микросекунды: чтение `Runtime`); тик мониторинга — раз
в минуту (`fixedDelay = 60_000`, `MonitoringService.kt:61`)

**Constraints**:
- предел памяти контейнера 8 ГБ, максимум heap JVM 6 ГБ (FR-007);
- пороги алерта 75 % / 90 % (FR-015);
- том дампов: последние 2 файла, ≤ 20 ГБ, вытеснение по возрасту (FR-011);
- ни одна из трёх легаси-точек входа не меняется (FR-005 — современные пути);
- `karaoke-app` не развёрнут на прод-хосте — правки только admin-контура.

**Scale/Scope**: 1 контейнер, 1 том, 1 новая проверка мониторинга (8-я),
12+2 документов базы знаний, 1 ADR, 1 per-feature документ, 1 новый bash-скрипт
+ 2 systemd-юнита, 1 guard-правило, 3–4 теста.

---

## Constitution Check

*GATE: пройден до Phase 0 и перепроверен после Phase 1.*

| Принцип | Требование | Как соблюдается | Вердикт |
|---|---|---|---|
| **I** Self-contained | пайплайн без внешних SaaS | проверка heap читает `Runtime` локально; внешних сервисов не добавлено | PASS |
| **II** Сырой JDBC | никаких JPA/Hibernate | product-код не меняет доступ к БД; в тесте — подмена JDBC-границы (не JPA) | PASS |
| **III** Двух-БД sync | флаги `sync_*` | не затрагивается | PASS |
| **IV** Async-очередь | `redirectErrorStream` | не затрагивается | PASS |
| **V** Два фронтенда | webvue3 / karaoke-public | фронтенд не трогаем | PASS |
| **VI** Code Standards (FR-006/007/009) | KDoc + `@see`, ktlint, per-feature документ | KDoc на новом классе + `@see` на `knowledge/product/features/heap-memory-guardrails.md`; ktlint — обязательный гейт; per-feature документ заведён (R11) | PASS |
| **VII** Cross-machine setup | локальные конфиги вне git | `deploy/.env` не меняется и не печатается; новые значения — в **отслеживаемом** compose | PASS |
| **VIII** Секреты и git-гигиена | `.env` не в git, нет секретов в коде | новых секретов нет; `deploy/.env` не коммитится (проверка `git ls-files`) | PASS |
| **IX** Knowledge-first | 5 шагов до кода | выполнено: `knowledge/README.md` + `knowledge/domains/README.md` целиком; 4 grep'а по `knowledge/`; `domain.md` + компоненты доменов `monitoring`, `catalog`, `system/infra`; все `local-*.md` ADR (`local-0001`, `local-0005`, `local-0013`, README) | PASS |
| **IX.3** Subagent isolation | worktree для параллельных PR | работа один субагент в существующей ветке `676-oom-karaoke-app` — конфликтов нет | PASS (не применимо) |

**Complexities Tracking**: нарушений нет — Complexity Tracking пуст.

---

## Project Structure

### Documentation (this feature)

```text
specs/676-oom-karaoke-app/
├── spec.md                     # контракт (заморожен; правки — только словом владельца)
├── plan.md                     # этот файл
├── research.md                 # Phase 0 — 12 решений с альтернативами
├── data-model.md               # Phase 1 — сущности, поля, переходы состояний
├── quickstart.md               # Phase 1 — сценарии проверки end-to-end
├── contracts/
│   ├── heap-monitor-check.md   # контракт проверки мониторинга (логи, алерты, пороги)
│   ├── deploy-memory-limits.md # контракт деплой-конфигурации (пара heap ↔ предел, дампы, retention)
│   └── publication-grid.md     # контракт раскладки публикаций (инвариант + границы теста)
├── analysis.md                 # Phase 3 (/speckit-analyze)
├── tasks.md                    # Phase 2 (/speckit-tasks)
└── report.md                   # отчёт о выполнении (REQUIRED-артефакт Plane-workflow)
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── monitor/
│   ├── checks/HeapUsageCheck.kt        # НОВОЕ — 8-я проверка (FR-014…FR-019)
│   └── MonitorRegistry.kt              # ПРАВКА — +1 строка регистрации
└── model/Publication.kt                # НЕ МЕНЯЕТСЯ (починка уже в master, FR-003)

karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/
├── monitor/HeapUsageCheckTest.kt       # НОВОЕ — пороги, severity, recovery, бюджет времени
└── model/PublicationGridCallerTest.kt  # НОВОЕ — FR-004, уровень вызывающих, 500 песен

deploy/
├── docker-compose-app.yml              # ПРАВКА — mem_limit, -Xmx6g, HeapDumpPath, том дампов, env порогов
├── heap-dump-retention.sh              # НОВОЕ — политика хранения дампов
├── heap-dump-retention.service         # НОВОЕ — systemd-юнит (oneshot)
└── heap-dump-retention.timer           # НОВОЕ — systemd-таймер

tools/check-deploy-hardening.sh         # ПРАВКА — новое правило BR-629 на конфигурацию karaoke-app

knowledge/
├── adr/local-0014-heap-memory-guardrails-karaoke-app.md   # НОВОЕ
├── adr/README.md                       # ПРАВКА — реестр ADR
├── domains/monitoring/domain.md        # ПРАВКА
├── domains/monitoring/components/{monitor-core,monitor-checks,monitor-checks-detailed,log-categories}.md  # ПРАВКА
├── domains/catalog/components/remaining-models.md        # ПРАВКА
├── system/infra/deploy-overview.md     # ПРАВКА
├── system/infra/audit-336-registry.md  # ПРАВКА — BR-188 + новая запись
├── system/02-containers.md             # ПРАВКА
├── guidelines/runbooks/log-correlation.md  # ПРАВКА — порядок действий при алерте
├── adr/local-0013-infra-backups-and-monitoring.md          # ПРАВКА — область решения
└── product/features/heap-memory-guardrails.md              # НОВОЕ (per-feature, FR-009)

archive/docs/architecture-notes.md      # ПРАВКА — запись об инциденте 2026-10-23
```

**Structure Decision**: существующая структура репозитория не меняется —
новые файлы добавляются по уже принятым конвенциям (проверка → `monitor/checks/`,
тест → `src/test/...`, скрипт развёртывания → `deploy/`, ADR → `knowledge/adr/`,
per-feature → `knowledge/product/features/`). Модулей и пакетов не добавляем.

---

## Порядок работ и точки проверки

| Этап | Что делается | Гейт |
|---|---|---|
| A | Проверка мониторинга: `HeapUsageCheck` + регистрация + тесты | `:karaoke-app:test`, ktlint |
| B | Тест уровня вызывающих (FR-004) | `:karaoke-app:test` (в т.ч. существующие `PkgS212*`) |
| C | Конфигурация развёртывания: compose + retention-скрипт + юниты + guard | `check-deploy-hardening.sh`, `bash -n`, self-test |
| D | База знаний: 12 документов + ADR + per-feature + Linking Protocol | `lint-knowledge.py`, `check-knowledge-structure.sh`, `check-ssot-impact.py` |
| E | Отчёт `report.md` + финальный коммит | `kara-pre-commit` (без SKIP) |

---

## Complexity Tracking

> Нарушений Constitution Check нет — раздел пуст.
