# Implementation Plan: 288-prod-diagnostics-logging

**Branch**: `288-prod-diagnostics-logging` | **Date**: 2026-09-01 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/288-prod-diagnostics-logging/spec.md`

## Summary

Расширить логирование на проде для post-hoc диагностики инцидентов «прод подвис» (например, после одобрения задания редактора). Три направления:

1. **PostgreSQL runtime-параметры** (`log_min_duration_statement`, `log_temp_files`, `log_lock_waits`, `log_autovacuum_min_duration`, `log_checkpoints`, `log_line_prefix`, `log_timezone`, `timezone`) — через `ALTER SYSTEM SET` + `pg_reload_conf()` без рестарта кластера. Префикс строк + TZ делают `pg_log` коррелируемым с другими логами.
2. **Структурированное SLF4J-логирование `ProdContainerCheck`** (заменить `println` на `log.warn` с категорией `infra.prod.ping`/`infra.prod.db`); WARN при сбое + INFO при восстановлении + NO-OP в обычном режиме.
3. **Синхронизация TZ Europe/Moscow** между PostgreSQL, JVM karaoke-web, контейнером `karaoke-db` — для однозначной корреляции логов по времени.

Дополнительно: документ `docs/ops/log-correlation.md` с картой логов, командами и типичными сценариями. Технические детали в [research.md](./research.md), сущности — в [data-model.md](./data-model.md), формат логов — в [contracts/log-format.md](./contracts/log-format.md).

## Technical Context

**Language/Version**: Kotlin 1.x (как в karaoke-app), JDK 17 (karaoke-app), JDK 22 JRE (karaoke-web). SLF4J 1.7.x (Logback default из Spring Boot).

**Primary Dependencies**:
- **Backend**: Spring Boot (karaoke-app, karaoke-web), Gradle multi-module.
- **Logging**: SLF4J + Logback (Spring Boot default) — НЕ вводим новых зависимостей.
- **PostgreSQL**: postgres:16 на проде; параметры через `ALTER SYSTEM SET` + `pg_reload_conf()`.

**Storage**: PostgreSQL 16 (настройки логирования в `postgresql.auto.conf`, переживают рестарт контейнера). Никаких миграций схемы, никаких новых таблиц.

**Testing**: ручное на admin-машине (per Constitution § Тесты — автоматические тесты `@Disabled`). Проверка через `docker logs` + grep + `docker exec psql`. Никаких новых unit-тестов (Constitution: «существующие тесты `@Disabled`, проверка делается пользователем вручную»).

**Target Platform**: Linux server (Ubuntu 22.04 + Docker Compose). Прод — `188.119.64.111` (`sm-karaoke.ru`), admin-машина — `nsa-i9` (где запущен `karaoke-app`).

**Project Type**: Web-service (multi-module Gradle: karaoke-app, karaoke-web, karaoke-public, webvue, webvue3). Изменения затрагивают:
- `karaoke-app/src/main/kotlin/.../monitor/checks/ProdContainerCheck.kt` — правка кода.
- `deploy/docker-compose-database.yml` — добавление `TZ: Europe/Moscow`.
- `deploy/.env` — добавление `-Duser.timezone=Europe/Moscow` в `WEB_JAVA_OPTS`.
- (опционально) `karaoke-app/src/main/resources/logback-spring.xml` — НЕ создаётся в этой фиче, см. Out of Scope.

**Performance Goals**:
- `pg_log`: ≤100 строк/день при нормальной нагрузке (SC-007).
- `ProdContainerCheck`: логирование НЕ замедляет пинг (WARN — после catch, INFO — после успеха, иначе no-op).
- JVM TZ flag: ноль overhead (один флаг `-Duser.timezone`).

**Constraints**:
- Constitution § «Категорически запрещено» п. 2: `ALTER SYSTEM SET` на проде **только по прямому согласию** пользователя на каждое действие.
- Constitution § VIII.5: никаких секретов в логах.
- Constitution § VI FR-006: KDoc обязателен на `ProdContainerCheck`.
- AGENTS.md: «Обязательная проверка после ЛЮБОГО изменения кода» (compile + lint + bootJar + vite + docker).
- Per Q2 (пользователь выбрал stderr): НЕ включать `logging_collector=on` (минимум воздействия на прод).

**Scale/Scope**:
- Текущая нагрузка ~50 RPS (visitor + admin + API) — оценка из спеки 241.
- 3 контейнера, 1 БД, 1 nginx. Не масштабируется горизонтально.
- 8 файлов для правки: 1 .kt (код) + 2 deploy-yaml/env + 1 docs/ops/log-correlation.md + 1 обновление livedocs/features/154 + 1 update AGENTS.md + 2 спека/policy файлы.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Статус | Комментарий |
|---------|--------|-------------|
| I. Self-contained автопайплайн | ✓ N/A | Эта фича — observability, не пайплайн рендера |
| II. Сырой JDBC + дифф по хэшам | ✓ N/A | Никаких SQL-операций в коде фичи (только runtime-параметры) |
| III. Двух-БД синхронизация | ✓ N/A | `recordhash`-триггеры не затрагиваются |
| IV. Async-очередь с парсингом stdout | ✓ N/A | Не меняется |
| V. Двух-фронтенд | ✓ N/A | Затрагивает только backend (karaoke-app) |
| **VI. Code Standards** | ✓ PASS | FR-017: KDoc на `ProdContainerCheck` обновляется с `@see local-0005-structured-logging-karaoke-app.md`. FR-018: per-feature документ в `livedocs/features/154-...md` обновляется. |
| VII. Cross-Machine Setup | ✓ N/A | Локальные конфиги не затрагиваются |
| **VIII. Секреты и git-гигиена** | ✓ PASS | FR-022: `Connection.remote()` URL НЕ логируется (только host/port). Никаких секретов в логах. `.gitignore`/`git ls-files` не меняются. |
| **«Категорически запрещено»** | ✓ PASS | FR-001..FR-007 (DDL к прод-БД) явно требуют согласия пользователя (A-009). FR-011..FR-016 — правка кода, разрешена агенту. FR-008 (правка `docker-compose-database.yml`) — разрешена (файл в git). |

**Constitution Check итог**: PASS, no violations.

## Project Structure

### Documentation (this feature)

```text
specs/288-prod-diagnostics-logging/
├── plan.md              # Этот файл (/speckit.plan output)
├── spec.md              # User-facing specification
├── research.md          # Phase 0 — технические решения D-1..D-8
├── data-model.md        # Phase 1 — 3 entities (parameter, logger, PingState)
├── contracts/
│   └── log-format.md    # Phase 1 — контракт формата WARN/INFO сообщений
├── quickstart.md        # Phase 1 — end-to-end validation
├── checklists/
│   └── requirements.md  # Spec quality checklist
└── tasks.md             # Phase 2 — будет /speckit.tasks (НЕ создано /speckit.plan)
```

### Source Code (repository root)

Эта фича **минимально** затрагивает код:

```text
karaoke-app/
└── src/main/kotlin/com/svoemesto/karaokeapp/monitor/checks/
    └── ProdContainerCheck.kt    # правка (FR-011..FR-017)

deploy/
├── docker-compose-database.yml  # добавление TZ: Europe/Moscow (FR-008)
└── .env                          # добавление -Duser.timezone (FR-010)

docs/ops/                          # НОВАЯ директория
└── log-correlation.md             # FR-019

livedocs/features/
└── 154-remove-scheduled-publications-monitoring.md  # обновление (FR-018)

AGENTS.md                           # ссылка на log-correlation.md (FR-020)
```

**Structure Decision**: используется существующая multi-module структура (karaoke-app + karaoke-web + webvue3 + karaoke-public). Создаётся одна новая директория `docs/ops/` для операционной документации (per research.md решение D-7).

## Technical Decisions (summary, см. research.md для деталей)

| ID | Решение | Обоснование |
|----|---------|-------------|
| **D-1** | Per-feature документ — обновление существующего `livedocs/features/154-...md` | Избыточно создавать новый; 154 уже покрывает `ProdContainerCheck` |
| **D-2** | Категория `infra.prod.ping`/`infra.prod.db` через `getLogger("infra.prod.ping")` | Лучше для grep'а, чем `getLogger(javaClass)` |
| **D-3** | Префикс строк `%m [%p] %q%u@%d from %h ` | Полный контекст для корреляции; IP — infrastructure, не PII |
| **D-4** | Формат WARN-сообщения per local-0005 (key=value) | Соответствие конвенции проекта |
| **D-5** | `log_min_duration_statement = 1000` (1 сек) | Баланс по текущей нагрузке 50 RPS |
| **D-6** | Применение ALTER SYSTEM через `docker exec karaoke-db psql` | Стандартный паттерн Karaoke-проекта |
| **D-7** | Документ в `docs/ops/log-correlation.md`, не в `livedocs/runbooks/` | Операционный runbook, не architectural knowledge |
| **D-8** | Per-feature ссылка через обновление 154, не создание 288 | Избежание дублирования |

## Risks & Mitigations

| Риск | Митигация |
|------|-----------|
| Объём `pg_log` окажется > 100 строк/день при `log_min_duration_statement = 1000` | Пользователь может поднять порог без правки кода (через ещё один `ALTER SYSTEM SET log_min_duration_statement = 2000;`). Мониторинг — через `grep -c 'LOG: ' $(docker logs karaoke-db --since '1d')`. |
| `TZ=Europe/Moscow` в `docker-compose-database.yml` не применится без рестарта | Per A-001 — `ALTER SYSTEM SET timezone` (FR-007) уже работает для SQL-уровня без рестарта. Контейнерная TZ применяется при следующем `--force-recreate`. |
| `firstFailureAt` сбрасывается неожиданно | Существующая логика `ProdContainerCheck.kt:41` — `firstFailureAt = null` только когда `siteUp && dbUp`. Сохраняем (FR-014 ссылается на это). |
| WARN-сообщения о пинге создают избыточный шум при «мигании» сайта | Per Edge Cases в spec.md: `firstFailureAt` сбрасывается только при успехе. Если сайт «мигает» (1 сек даунтайм) — будет WARN каждый тик, что **и есть цель** (детектировать именно такие случаи). |
| `-Duser.timezone` в JVM перезатрёт локаль | Нет, это устанавливает только TZ JVM, не локаль (`LC_ALL` остаётся). Проверено в Java docs. |

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

Не заполняется — Constitution Check PASS, no violations.

## Open Items (для `/speckit.tasks`)

1. **T-001**: применить `ALTER SYSTEM SET ...` к прод-БД (FR-001..FR-007) — **по явному согласию пользователя**.
2. **T-002**: добавить `TZ: Europe/Moscow` в `deploy/docker-compose-database.yml` (FR-008).
3. **T-003**: добавить `-Duser.timezone=Europe/Moscow` в `WEB_JAVA_OPTS` в `deploy/.env` (FR-010).
4. **T-004..T-009**: правка `ProdContainerCheck.kt` (FR-011..FR-016): SLF4J WARN/INFO, duration tracking, pingRemoteDb logging, KDoc.
5. **T-010**: создать `docs/ops/log-correlation.md` (FR-019).
6. **T-011**: обновить `livedocs/features/154-remove-scheduled-publications-monitoring.md` (FR-018).
7. **T-012**: добавить ссылку в `AGENTS.md` (FR-020).
8. **T-013**: post-deploy validation per `quickstart.md`.

Детальная разбивка — в `/speckit.tasks` (Phase 2).

## History

- Создан: 2026-09-01 (Phase 0 + Phase 1 завершены)
- Phase 0: research.md — 8 технических решений, все NEEDS CLARIFICATION резолвнуты
- Phase 1: data-model.md (3 entities), contracts/log-format.md, quickstart.md