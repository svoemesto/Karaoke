# Implementation Plan: Воспроизводимая настройка Linux Mint для проекта Karaoke

**Branch**: `170-mint-dev-setup` | **Date**: 2026-08-11 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/170-mint-dev-setup/spec.md`

## Summary

Создать **воспроизводимый** setup для dev-машины с Linux Mint 22.2 Zara
(основана на Ubuntu 24.04 Noble). Артефакты:

1. `deploy/do.env.template` — env-шаблон с обязательными переменными для
   `deploy/do.sh` (через `set -a; source do.env`), без реальных секретов.
2. `specs/170-mint-dev-setup/setup-mint.sh` — идемпотентный bash-скрипт,
   ставит системные зависимости, Docker CE, Node 22 LTS, поднимает 8+ контейнеров,
   запускает smoke-test.
3. `docs/features/docker-deploy.md` — per-feature документ docker-стека
   (соглашение FR-009 Constitution: правка docker-стека = обновление per-feature).
4. Ссылка из `docs/onboarding.md` на эту спеку (как Mint-специфичный гайд).

**Технический подход** (из research.md): Docker CE из `download.docker.com`
(не snap, не `docker.io`); Node 22 через NodeSource; `apt install` для
системных пакетов; `dpkg-query` для идемпотентной проверки; 2-шаговый
`do.sh start all` + отдельный `docker compose -f docker-compose-storage.yml up`
для MinIO; smoke-test с retry-loop на Postgres init.

## Technical Context

**Language/Version**: Bash 5.2+ (system bash на Ubuntu 24.04); синтаксис POSIX-sh + bash-isms (`[[ ]]`, `local -a`, `<<<`).

**Primary Dependencies**:
- `apt` (Ubuntu 24.04 noble) — `openjdk-18-jdk`, `git`, `ffmpeg`, `python3.11+`, `pre-commit`, `melt` (MLT 7+), `postgresql-client-15`, `jq`, `curl`, `ca-certificates`, `apt-transport-https`, `software-properties-common`, `gnupg`.
- NodeSource — `nodejs` v22 LTS + `npm` 10.
- `download.docker.com` — `docker-ce`, `docker-ce-cli`, `containerd.io`, `docker-compose-plugin`, `docker-buildx-plugin`.
- `deploy/do.sh` — существующий, **не модифицируется** в этой спеке (только читается).

**Storage**:
- Bind-mounts на хосте: `/sm-karaoke/system/Караоке`, `/sm-karaoke/system/Караоке-1/2/3`, `/sm-karaoke/system/Караоке-store`, `/sm-karaoke/system/Караоке-storage`, `/sm-karaoke/system/Караоке-db` — создаются `setup-mint.sh` через `sudo mkdir -p` с правильными правами.
- Postgres data в `DB_FOLDER` (через `docker-compose-database.yml`).
- MinIO data в `STORAGE_FOLDER` (через `docker-compose-storage.yml`).
- `do.env` НЕ в репо; `do.env.template` — коммитится.

**Testing**:
- Ручной smoke-test (`curl` + `docker ps` + `psql`), см. quickstart.md § Шаг 4.
- Pre-commit хук на `git ls-files | grep -E '(do\.env|\.env)$'` (существующий; для спеки — `deploy/do.env.template` НЕ подпадает под этот regex, т.к. заканчивается на `.template`).
- `bash tools/check-feature-doc.sh docs/features/docker-deploy.md` — после создания per-feature дока.

**Target Platform**: Linux Mint 22.2 Zara (Ubuntu 24.04 Noble base), x86_64. Не-root пользователь с `sudo`. Интернет-доступ к `apt`, `deb.nodesource.com`, `download.docker.com`, `github.com`, Docker Hub.

**Project Type**: **devops/infrastructure** (bash-скрипт + env-шаблон + документация). Не library, не web-service, не CLI-утилита. **Skip** sections: data-model.md, contracts/ (нет data-entities, нет external interfaces).

**Performance Goals**:
- Время выполнения `setup-mint.sh` с нуля: **≤ 1,5 часа** (SC-001), включая `apt update` + `apt install` + скачивание ~500 МБ Docker-образов.
- Время повторного запуска (идемпотентно): **≤ 2 минуты** (всё уже стоит, только smoke-test).
- Время от `docker compose up -d` до зелёного smoke-test: **≤ 30 секунд** для всех 4 эндпоинтов (SC-004).

**Constraints**:
- Скрипт НЕ пересобирает Docker-образы (только `docker pull`); это экономит ~30 минут на первой установке.
- Ollama — **опционально** (`--with-ollama`); по умолчанию не ставится.
- `karaoke-app` НЕ пересобирается агентом на этой машине (per Constitution: hostname `nsa-G501VW` ≠ `dev-pc`, пользователь `nsa` ≠ `dev` → агенту запрещено). `setup-mint.sh` — **не** AI-агент, его выполняет пользователь; ограничение Constitution на скрипт **не** распространяется.
- `do.env.template` не должен содержать реальных секретов (SC-006, FR-012, Principle VIII.1).
- Bash-скрипт пишется в стиле `CONTRIBUTING.md` (секция «Shell»): `set -euo pipefail`, `set -a` ТОЛЬКО на время `source do.env` (PR #084).

**Scale/Scope**: 1 bash-скрипт (~250 строк, SC-005), 1 env-шаблон (~40-50 строк), 1 per-feature документ (~80-120 строк), 1 ссылка в `docs/onboarding.md`. 2-3 часа работы, 1 PR.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Principle I — Self-contained автопайплайн

**Совместим**: спека поднимает локальный docker-стек с self-hosted SearXNG,
4get, MinIO, Ollama (опционально), без зависимости от внешних SaaS в
рантайме обработки медиа. PASS.

### Principle II — Сырой JDBC + дифф по хэшам

**Не применимо**: спека — инфраструктурная, не трогает БД-слой.
PASS (N/A).

### Principle III — Двух-БД синхронизация через SyncRegistry

**Не применимо**: спека работает с **локальной** БД через `do.sh start all`
(`DATABASE="-f docker-compose-database.yml"`), не поднимает remote-БД.
PASS (N/A).

### Principle IV — Async-очередь задач с парсингом stdout

**Не применимо**: `setup-mint.sh` не парсит stdout процессов karaoke-app;
он только запускает/останавливает контейнеры. PASS (N/A).

### Principle V — Двух-фронтенд: админка и публичный сайт — разные приложения

**Совместим**: спека поднимает **оба** SPA (`karaoke-webvue3` + `karaoke-public`).
Никаких изменений в их коде. PASS.

### Principle VI — Code Standards (FR-006, FR-007, FR-009)

**Совместим**:
- FR-006 (KDoc/JSDoc на публичные API): скрипт и env-шаблон — **не** код,
  документация. Не применимо. PASS (N/A).
- FR-007 (линтеры): bash-скрипт пройдёт `shellcheck` (если есть) — но
  pre-commit хуки проекта не включают shellcheck, поэтому не блокирует CI.
  PASS.
- FR-009 (обновление per-feature дока при правке фичи): спека **создаёт**
  per-feature документ `docs/features/docker-deploy.md` и обновляет
  `docs/features/README.md` (если есть таблица фич) — это **выходит за рамки**
  простого коммита, но в чеклисте явно зафиксировано (CHK024). PASS.

### Principle VII — Cross-Machine Setup (VII.1-VII.4)

**Совместим**:
- VII.1 (локальные AI-конфиги НЕ в гите): `CLAUDE.md`, `.cursorrules` —
  не создаются в этой спеке, не в скоупе. PASS.
- VII.2 (`.git-blame-ignore-revs`): `setup-mint.sh` настраивает
  `git config blame.ignoreRevsFile .git-blame-ignore-revs` на машине
  разработчика. PASS.
- VII.3 (`.gitattributes`): `do.env.template` имеет LF (создаётся через
  `Write` tool, не трогает `.gitattributes`). PASS.
- VII.4 (cross-machine документация): спека **дополняет** `docs/onboarding.md`
  (общий setup) Mint-специфичными шагами; добавляется ссылка
  `docs/onboarding.md → specs/170-mint-dev-setup/`. PASS.

### Principle VIII — Секреты и git-гигиена

**Совместим**:
- VIII.1 (`.gitignore` + `git ls-files` пусто): `do.env` уже в `.gitignore`;
  `do.env.template` создаётся как **новый** файл, коммитится, **без секретов**.
  Перед коммитом проверить `git ls-files | grep do.env` — должно быть пусто
  (т.к. `.template` не подпадает). PASS.
- VIII.2 (список запрещённых паттернов): `do.env` НЕ трекается. PASS.
- VIII.3 (pre-commit check): после создания спеки — `git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$'` пусто. PASS.
- VIII.4 (если обнаружен утёкший секрет): не применимо (мы не редактируем
  историю). PASS (N/A).
- VIII.5 (секреты в коде ЗАПРЕЩЕНЫ, дефолты в `${VAR:default}` — невалидные
  или публичные): `do.env.template` использует placeholder'ы `<SET-ME>` или
  безопасные дефолты (`svоеместо`, `localhost`, `0`). PASS.

### «Ограничения и доступы агента»

**Совместим**: спека **не** заставляет агента пересобирать `karaoke-app`
(per п. 1 «Категорически запрещено»). `setup-mint.sh` выполняется
**пользователем**, не агентом. Агент на этой машине может:
- читать и анализировать `do.env` (без вывода секретов в логи, per п. 6);
- выполнять `docker ps` / `docker logs` / `curl` для диагностики;
- запускать `bash do.sh start_web` / `start_public` (per п. 4 «Разрешено»,
  т.к. это **не** `karaoke-app`).
Агент НЕ может (per п. 1):
- `bash do.sh build_start_app` или `start_app` без явного согласия
  пользователя (т.к. hostname ≠ `dev-pc`, user ≠ `dev`).
PASS.

### Re-evaluation после Phase 1 (design)

Все 9 Принципов — PASS (или N/A). Никаких нарушений.

## Project Structure

### Documentation (this feature)

```text
specs/170-mint-dev-setup/
├── plan.md              # Этот файл (/speckit.plan command output)
├── research.md          # Phase 0 output — 10 решений (R-001..R-010)
├── data-model.md        # SKIP: проект инфраструктурный, нет data-entities
├── quickstart.md        # Phase 1 output — runnable validation guide
├── contracts/           # SKIP: проект внутренний, нет external interfaces
├── setup-mint.sh        # Phase 1 output — bash-скрипт (создаётся в /speckit.tasks)
├── checklists/
│   └── requirements.md  # Phase 0 output (16 + 4 + 5 пунктов, все зелёные)
└── spec.md              # Phase 0 input (из /speckit.specify)
```

### Source Code (repository root)

```text
Karaoke/
├── deploy/
│   ├── do.env.template    # NEW: env-шаблон (коммитится, без секретов)
│   ├── do.env             # (НЕ в гите, создаётся пользователем из template)
│   ├── do.sh              # существующий, не модифицируется
│   ├── docker-compose.yml       # существующий, не модифицируется
│   ├── docker-compose-database.yml  # существующий
│   ├── docker-compose-storage.yml   # существующий
│   └── ...                       # остальные compose-файлы не трогаются
├── docs/
│   ├── onboarding.md      # ИЗМЕНЯЕТСЯ: добавить ссылку на спеку в секции
│   │                       # «Целевая ОС — Linux Mint»
│   └── features/
│       ├── README.md      # ИЗМЕНЯЕТСЯ: добавить docker-deploy в таблицу
│       └── docker-deploy.md  # NEW: per-feature документ docker-стека
└── specs/
    └── 170-mint-dev-setup/   # NEW: эта спека и её артефакты
        ├── spec.md
        ├── plan.md
        ├── research.md
        ├── quickstart.md
        ├── setup-mint.sh   # NEW
        └── checklists/
            └── requirements.md
```

**Structure Decision**: опция **«Pure docs/scripts»** (нет `src/`, нет
`tests/`). Проект Karaoke — multi-module monorepo, и эта фича
**дополняет** инфраструктуру, не вводя новых модулей. `setup-mint.sh`
лежит в `specs/170-mint-dev-setup/` (per конвенции спецификаций:
артефакты спеки рядом со спекой), `do.env.template` — рядом с другими
deploy-артефактами в `deploy/`, per-feature документ — рядом с другими
per-feature в `docs/features/`.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

Нет нарушений Constitution. Таблица пустая.

## Phase 0 — Research summary

10 решений зафиксировано в [`research.md`](./research.md):
- **R-001** — Node 22 через NodeSource (не apt, не nvm).
- **R-002** — Docker CE из `download.docker.com` (не snap, не `docker.io`).
- **R-003** — fail-fast на неподдерживаемой ОС.
- **R-004** — полный список переменных в `do.env.template` (40-50 строк).
- **R-005** — `dpkg-query` для идемпотентности.
- **R-006** — порядок поднятия контейнеров (db → storage → app → web → public).
- **R-007** — smoke-test с retry-loop (5 попыток × 3 сек).
- **R-008** — rollback-стратегия (снести volumes отдельно от контейнеров).
- **R-009** — per-feature документ `docs/features/docker-deploy.md`.
- **R-010** — `set -a; source do.env` корректно пропускает `#`-комментарии в bash 5.2.

## Phase 1 — Design summary

Создано:
- `research.md` (Phase 0) — 10 решений.
- `quickstart.md` (Phase 1) — 5 шагов + troubleshooting + 4 группы smoke-test.

**Пропущено** (с обоснованием):
- `data-model.md` — проект инфраструктурный, нет data-entities. Спека
  не вводит новых БД-таблиц, не валидирует схемы. **Skip**.
- `contracts/` — проект внутренний, не предоставляет external interfaces
  пользователям. `setup-mint.sh` запускается разработчиком Karaoke на
  его dev-машине, не API/CLI для внешних пользователей. **Skip**.

**Tasks** (для `/speckit.tasks`):
1. Создать `deploy/do.env.template` (~40-50 строк).
2. Создать `specs/170-mint-dev-setup/setup-mint.sh` (~250 строк).
3. Создать `docs/features/docker-deploy.md` (per-feature документ, ~80-120 строк).
4. Обновить `docs/features/README.md` (добавить docker-deploy в таблицу 9→10 фич).
5. Обновить `docs/onboarding.md` (добавить ссылку на спеку в раздел «Целевая ОС»).
6. Проверить `git ls-files | grep do.env` — пусто (Principle VIII.1).
7. Запустить `bash tools/check-feature-doc.sh docs/features/docker-deploy.md` — зелёный.
8. Commit + PR + дождаться CI 7/7 PASS + merge без `--delete-branch` (per AGENTS.md).
9. После merge: запись в `docs/architecture-notes.md` о PR `170-mint-dev-setup`.

## Связанные документы

- [`spec.md`](./spec.md) — основная спека.
- [`research.md`](./research.md) — 10 технических решений.
- [`quickstart.md`](./quickstart.md) — 5 шагов + smoke-test + troubleshooting.
- [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md) — все 9 Принципов, проверены в Constitution Check.
- [`AGENTS.md`](../../AGENTS.md) — нумерация веток, CI-gate, «Ограничения агента».
- [`DEVELOPMENT.md`](../../DEVELOPMENT.md) — архитектура, обзор `do.sh`.
- [`docs/onboarding.md`](../../docs/onboarding.md) — общий setup (обновляется ссылкой).
- [`docs/features/`](../../docs/features/) — per-feature документы (добавляется `docker-deploy.md`).
- [`CONTRIBUTING.md`](../../CONTRIBUTING.md) — стиль bash (секция «Shell»).
