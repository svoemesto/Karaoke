# Implementation Plan: Локальная Jira для AI-агента

**Branch**: `295-jira-local-integration` | **Date**: 2026-09-02 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/295-jira-local-integration/spec.md`

**Note**: Этот план сгенерирован командой `/speckit.plan`; его определение описывает
выполненный workflow (Phase 0 research + Phase 1 design).

## Summary

Развёртывание локального Jira Data Center в Docker-контейнере на `nsa-i9` для
двусторонней интеграции "пользователь ↔ AI-агент через задачи". Пользователь
заводит задачи в Jira UI; AI-агент через bash CLI (`tools/jira.sh`) забирает их,
выполняет и публикует отчёт-комментарий с автоматическим закрытием. Включает
бэкап через systemd-timer, логирование CLI в JSON и rate-limit retry.

**Технический подход** (из research.md):
- **Jira Software Data Center 9.12.x LTS** в официальном Docker-образе.
- **Postgres 13+** в named-volume `jira-postgres-data` для персистентности.
- **CLI на bash + curl + jq** — без новых зависимостей (только system-wide).
- **Бэкап через systemd-timer** (по образцу `karaoke-db-backup.{timer,sh}`).
- **Структура**: новый `deploy/jira-docker-compose.yml` (НЕ в существующем `do.sh`),
  новые скрипты в `tools/`, новая документация в `docs/jira-setup.md`.

## Technical Context

**Language/Version CLI**: Bash 4.4+ (Ubuntu 22.04+, Debian 11+, RHEL 9+).
**Primary Dependencies**: `curl` ≥7.80, `jq` ≥1.6, `bash` ≥4.4 — все есть на admin-машине.
**Storage**: PostgreSQL 13+ (named volume `jira-postgres-data`), pg_dump custom format
(`-Fc`) для бэкапов в named volume `jira-backups`.
**Testing**: ручное + `tools/jira-smoke-test.sh` (по образцу Karaoke — нет unit-тестов
в CI, тесты `@Disabled`).
**Target Platform**: Linux x86_64 (Docker 20.10+, Docker Compose v2).
**Project Type**: CLI-утилита + docker-compose сервис (НЕ web-service, НЕ library,
НЕ часть Karaoke-приложения).
**Performance Goals**:
- `list-issues` ≤5 секунд @ ≤1000 задач (SC-002).
- Healthcheck `/status` ≤3 секунды (SC-008).
- Бэкап пустой Jira ≤2 минуты, ≤10 минут @ 1 GB БД (SC-009).
**Constraints**:
- Offline после первой установки (только Docker-образ скачивается один раз).
- Healthcheck с `start_period=600s` (Jira стартует 5-10 минут при первом запуске).
- Rate-limit retry 2s/4s/8s до 3 попыток, ≤14 сек суммарного ожидания (SC-012).
- Polling ≤1 req/30s (default), чтобы не превышать 100 req/min Atlassian DC.
**Scale/Scope**: 1 пользователь (`ai-agent`) + 1 admin, проект ≤1000 задач,
бэкап ≤1 GB, retention 7 дней.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Статус (pre-Phase 0) | Статус (post-Phase 1) | Комментарий |
|---------|----------------------|----------------------|-------------|
| **I. Self-contained автопайплайн** | ✅ Clear | ✅ Clear | Jira — локальный сервис, не в горячем пути Karaoke; образ скачивается один раз |
| **II. Сырой JDBC + дифф по хэшам** | ✅ N/A | ✅ N/A | Jira — внешняя БД; CLI не использует нашу БД Karaoke |
| **III. Двух-БД синхронизация** | ✅ N/A | ✅ N/A | Jira не участвует в SyncRegistry Karaoke |
| **IV. Async-очередь с парсингом stdout** | ✅ N/A | ✅ N/A | CLI не запускает ffmpeg/melt; `redirectErrorStream` не применяется |
| **V. Двух-фронтенд** | ✅ N/A | ✅ N/A | Jira UI — собственный фронтенд Atlassian |
| **VI. Code Standards (KDoc/JSDoc, линтеры)** | ✅ Clear | ✅ Clear | Новый bash-код с комментариями; не затрагивает существующие Kotlin/Vue-модули |
| **VII. Cross-Machine Setup** | ✅ Clear | ✅ Clear | `docs/jira-setup.md` часть onboarding |
| **VIII. Секреты и git-гигиена** | ✅ Clear | ✅ Clear | FR-009: `.env.local-jira` в `.gitignore` (паттерн `*.env.*` уже есть); SC-006 проверяет `git ls-files` |
| **«Категорически запрещено» п.1** | ✅ Clear | ✅ Clear | Не затрагиваем `karaoke-app` — это новый сервис |
| **«Категорически запрещено» п.5** | ✅ Clear | ✅ Clear | FR-009 явно требует `.env.local-jira` НЕ в git; pre-commit проверка |
| **«Категорически запрещено» п.7** | ✅ Clear | ✅ Clear | Используем официальный `atlassian/jira-software` образ, не кастомные сборки |

**GATE: PASS** — никаких нарушений, дополнительных обоснований не требуется.

**Re-check после Phase 1**: подтверждено, что:
- Data model entities не утекают в нашу БД Karaoke (изолированы в Jira).
- CLI контракт использует env-переменные (`$JIRA_TOKEN`), не hardcoded секреты.
- Все секреты — в `.env.local-jira` (вне `deploy/`, чтобы не путать с прод-секретами Karaoke).
- Backup-скрипт использует `set -a; source .env.local-jira` (без вывода секретов в лог).

## Project Structure

### Documentation (this feature)

```text
specs/295-jira-local-integration/
├── plan.md              # Этот файл (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   └── jira-cli.md      # CLI contract: env, subcommands, HTTP handling, errors
├── checklists/
│   └── requirements.md  # Spec Quality Checklist (Phase 1 re-validation)
├── spec.md              # User input (/speckit.specify command output)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
# Новые файлы (создаются при реализации)
/home/nsa/Karaoke/
├── deploy/
│   ├── jira-docker-compose.yml       # Docker Compose для Jira + Postgres
│   ├── jira-db-backup.sh             # Backup-скрипт (по образцу karaoke-db-backup.sh)
│   ├── jira-db-backup.service        # systemd-user unit для backup
│   └── jira-db-backup.timer          # systemd-user timer (03:00 daily)
├── tools/
│   ├── jira.sh                       # Главный CLI (FR-005: 8 подкоманд)
│   ├── jira-backup.sh                # Обёртка для systemd-timer (вызывает docker exec pg_dump)
│   ├── jira-restore.sh               # DR drill: восстановление из дампа
│   ├── jira-smoke-test.sh            # End-to-end проверка
│   ├── install-jira.sh               # First-run setup (one-shot скрипт)
│   └── jira-lib.sh                   # Общие функции (HTTP wrapper, retry, logging)
├── docs/
│   ├── jira-setup.md                 # Пошаговая документация (FR-010)
│   └── features/
│       └── jira-local-integration.md # Per-feature документ (Constitution § VI FR-009)
├── logs/
│   └── .gitkeep                      # Директория для jira-agent.log (ротация через logrotate)
└── .env.local-jira.example           # Шаблон для .env.local-jira (коммитится; реальный файл — в .gitignore)
```

**Structure Decision**: новые файлы добавляются в существующую структуру Karaoke
без модификации существующих модулей. Это соответствует **NON-NEGOTIABLE** правилу
«не затрагивать существующий стек» (FR-011). Все новые компоненты изолированы в
`deploy/jira-docker-compose.yml` (отдельный compose) и `tools/jira*.sh` (новые скрипты,
не меняющие существующие).

### Изменения в существующих файлах

```text
# Минимальные изменения (≤5 строк в каждом)
/home/nsa/Karaoke/
├── .gitignore                         # +1 строка: ".env.local-jira" (уже покрыто паттерном *.env.*, но добавим явно для clarity)
└── .pre-commit-config.yaml            # (опционально) +1 hook: запрет коммита .env.local-jira
```

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

Нет нарушений — секция пуста.

## Open Questions / Deferred

### Отложено (для будущих фаз)

- **Webhook integration** (FR-014 — SHOULD, не MUST). Реализация требует публичный
  endpoint (туннель типа ngrok) — отдельный класс задач. Polling достаточен для MVP.
- **Custom fields / issue links / agile boards** — вне scope MVP, см. data-model.md → Out of Scope.
- **Multi-project support** — спека описывает один проект `KARAOKE`. Для multi-project
  потребуется расширение CLI (`--project` уже поддерживается, но UI-настройка не
  автоматизирована).

### Риски (см. research.md → Open Questions / Risks)

| Риск | Митигация |
|------|-----------|
| Долгий старт Jira (5-15 мин) | `HEALTHCHECK start_period=600s interval=30s` |
| Evaluation license expiry (30 дней) | Документировано в `docs/jira-setup.md`; nag-screen не блокирует работу |
| Порт 8080 занят на admin-машине | `install-jira.sh` автодетект с фолбэком на 8090 |

## Implementation Order (для следующей фазы `/speckit.tasks`)

Задачи будут сгенерированы в указанном порядке (dependency-ordered):

1. **Инфраструктура** (compose + Postgres + Jira): создать `deploy/jira-docker-compose.yml`.
2. **Backup**: создать `deploy/jira-db-backup.{sh,service,timer}`.
3. **CLI ядро**: создать `tools/jira-lib.sh` (HTTP wrapper, retry, logging).
4. **CLI подкоманды**: создать `tools/jira.sh` с 8 подкомандами из FR-005.
5. **Helper scripts**: `tools/jira-backup.sh`, `tools/jira-restore.sh`, `tools/jira-smoke-test.sh`, `tools/install-jira.sh`.
6. **Документация**: `docs/jira-setup.md` + `docs/features/jira-local-integration.md`.
7. **Per-feature документ**: `docs/features/jira-local-integration.md` (Constitution § VI FR-009).
8. **Gitignore + pre-commit**: добавить `.env.local-jira` в `.gitignore`, добавить hook.
9. **Verification**: end-to-end прохождение quickstart.md пользователем.

## Next Steps

→ `/speckit.tasks` для генерации tasks.md с конкретными шагами и dependency-ordering.
