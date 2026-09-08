# Implementation Plan: Prod DB Log Audit (последняя неделя)

**Branch**: `318-prod-db-log-audit` | **Date**: 2026-09-08 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/318-prod-db-log-audit/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Разовое (one-shot) исследование: забрать `docker logs karaoke-db` за последнюю
168 часов с прода через `ssh root@188.119.64.111`, структурировать в JSONL,
прокатегоризировать через воспроизводимый bash + jq-конвейер, и опубликовать
`report.md` с вердиктом «оставлять» или «отключать» подробное логирование.
Если вердикт — «отключать», дополнительно сформировать **готовый план правки
конфигурации `karaoke-db`** (env-переменные + конфиг Postgres), но **не применять
его** без явного согласия владельца (Constitution п.2 «Деплой — только по
согласию»).

Технический подход: чистый bash + jq + awk. Без нового runtime-кода в
`karaoke-app`/`karaoke-web`, без зависимостей, без Docker-образов. Артефакты —
только в `specs/318-prod-db-log-audit/{data,scripts,report.md}`.

## Technical Context

**Language/Version**: bash 5.x (POSIX-совместимые флаги), jq ≥1.6, awk (gawk).
Скрипты запускаются локально на admin-машине (`nsa-i9`).

**Primary Dependencies**:
- `ssh` + ключ `~/.ssh/id_rsa` (уже проверен, BatchMode=yes работает)
- `docker` (через `ssh root@188.119.64.111 "docker logs …"`)
- `jq` (категоризация + агрегации)
- `awk`/`gawk` (парсинг timestamp/level из строк лога)
- `md5sum` (проверка воспроизводимости по SC-002)
- `git` (для коммита артефактов)

**Storage**: только локальная файловая система под `specs/318-prod-db-log-audit/`.
Никаких изменений в БД, MinIO, Redis. Никаких новых таблиц.

**Testing**: ручная верификация владельцем (SC-001: ≤10 мин чтения `report.md`).
Smoke-проверка ERROR после отключения (US2 Scenario 3) — отдельный
одноразовый SQL-запрос через существующий API.

**Target Platform**: Linux x86_64 (admin-машина `nsa-i9`, ядро 6.x) + удалённый
Linux на `188.119.64.111` (ssh-only, без агентских доступов).

**Project Type**: **one-shot CLI-аналитика** (разовая операционная задача, не
библиотека, не сервис). Артефакты — bash-скрипты + данные + Markdown-отчёт.

**Performance Goals**: не релевантно (one-shot, не горячий путь). Единственное
ограничение — общий объём логов должен обрабатываться **за ≤5 минут** на
admin-машине (включая ssh-копирование). Это верхняя граница для удобства
владельца, не SLO.

**Constraints**:
- Constitution Principle II (сырой JDBC) — не нарушаем (только чтение stdout).
- Constitution Principle VIII (секреты) — `data/raw.log` и `data/logs.jsonl`
  MUST быть очищены от любых паролей/токенов **до коммита** (FR-007).
- **Read-only** на прод-сервере: только `docker logs` и `scp`. Никаких
  `docker exec`, `docker restart`, `sed -i` на сервере.
- Анализ **offline**: регексы прогоняются на скачанных данных, не на проде.

**Scale/Scope**: одна неделя × один контейнер × одно приложение. Ожидаемый
объём сырого лога — десятки МБ (по умолчанию), верхняя граница — единицы ГБ
(см. Assumption 4 спеки). Если >1 ГБ — сэмплируем (см. research.md).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Статус | Комментарий |
|---|---|---|
| I. Self-contained пайплайн | ✅ pass | Только локальный анализ, никаких новых внешних зависимостей в горячем пути медиа-обработки |
| II. Сырой JDBC | ✅ pass | JDBC не используется; читаем stdout-логи |
| III. SyncRegistry | ✅ pass | Новые таблицы/сущности не добавляются |
| IV. Async-очередь (ProcessBuilder) | ✅ pass | Новых OS-процессов в Karaoke не добавляем; скрипты выполняются локально через bash |
| V. Двух-фронтенд | ✅ pass | UI не затрагивается |
| VI. Code Standards | ✅ pass | Нового кода в проекте нет; bash-скрипты в `scripts/` имеют header с `# SPDX-License-Identifier: MIT` (см. research.md) |
| VII. Cross-Machine | ✅ pass | Все артефакты в feature-ветке; кросс-машинных конфигов не меняем |
| VIII. Секреты | ✅ pass | FR-007 явно требует пустой `git ls-files \| grep -iE 'env\|key\|pem'`; data/raw.log санитайзится pre-commit-хук-скриптом |

**Gate result**: ✅ pass — нет нарушений, требующих обоснования в «Complexity Tracking».

### Constitution Check (повторно, после Phase 1 design)

| Принцип | Статус | Изменения после Phase 1 |
|---|---|---|
| I. Self-contained | ✅ pass | Без изменений |
| II. Сырой JDBC | ✅ pass | Без изменений |
| III. SyncRegistry | ✅ pass | Без изменений |
| IV. ProcessBuilder | ✅ pass | Без изменений; bash-скрипты `ssh`/`docker logs`/`jq` не подпадают (это не Karaoke-код) |
| V. Двух-фронтенд | ✅ pass | Без изменений |
| VI. Code Standards | ✅ pass | Уточнено: bash-скрипты в `specs/.../scripts/` — one-shot, не публичное API, FR-006 не применяется. KDoc/JSDoc не требуется |
| VII. Cross-Machine | ✅ pass | Без изменений |
| VIII. Секреты | ✅ pass | Добавлена явная sanitization (research R-3, contract `04-sanitize-for-commit.sh`); raw.log **НЕ коммитится** в git, в репозиторий идут только агрегаты + санитизированный logs.jsonl |

**Re-check gate result**: ✅ pass.

## Project Structure

### Documentation (this feature)

```text
specs/318-prod-db-log-audit/
├── plan.md              # Этот файл
├── research.md          # Phase 0: research notes (best practices, regex, edge cases)
├── data-model.md        # Phase 1: LogRecord / CategoryRule / IncidentCandidate
├── quickstart.md        # Phase 1: как воспроизвести анализ end-to-end
├── contracts/           # Phase 1: контракт отчёта + контракт конфигурации (если вердикт "отключать")
│   ├── report-format.md       # структура report.md (FR-003)
│   └── disable-config.md      # env-переменные + Postgres GUC (если вердикт "отключать")
├── scripts/             # (создаётся на Stage 6) bash-скрипты анализа
│   ├── 01-fetch-logs.sh
│   ├── 02-parse-and-categorize.sh
│   ├── 03-generate-report.sh
│   └── 04-sanitize-for-commit.sh
├── data/                # (создаётся на Stage 6; в git НЕ коммитится raw.log)
│   ├── raw.log          # скачанный docker logs (sanitized)
│   ├── logs.jsonl       # структурированные LogRecord
│   ├── categories.json  # правила категоризации (FR-002)
│   ├── incidents.jsonl  # IncidentCandidate (FR-003b)
│   └── metrics.json     # метрики нагрузки (FR-003c)
├── report.md            # итоговый отчёт (FR-003)
└── tasks.md             # Stage 4 output (отдельно)
```

### Source Code (repository root)

**Structure Decision**: **Option 1: Single project (DEFAULT)** — но в форме
**one-shot CLI-скриптов** под `specs/318-prod-db-log-audit/scripts/`. Никаких
изменений в `karaoke-app/`, `karaoke-web/`, `webvue3/`, `karaoke-public/`,
`deploy/`. Это **намеренное** решение: задача №66 — операционная, не feature.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| (нет) | — | — |

Нет нарушений Constitution — секция пустая по дизайну.