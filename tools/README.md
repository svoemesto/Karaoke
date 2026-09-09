# tools/ — Index of operational scripts

> Скрипты для CI, knowledge валидации, и операционного обслуживания.
> Все скрипты — POSIX bash + стандартные Unix-утилиты, без новых зависимостей.

## Knowledge SSoT валидация

| Скрипт | Назначение |
|--------|------------|
| [`check-knowledge-structure.sh`](check-knowledge-structure.sh) | CI gate: 9 проверок структуры `knowledge/` (директории, шаблоны, 9 доменов, ≥1 ADR, cross-link в README на каждый домен, frontmatter в domain.md, CI integration). **Запускается в GitHub Actions.** |
| [`check-knowledge-cross-links.sh`](check-knowledge-cross-links.sh) | Проверяет 243+ cross-links (`../X.md` и `related:`). ADR исключены (legacy формат). **Запускается в GitHub Actions.** |
| [`lint-knowledge.py`](lint-knowledge.py) | Проверяет эмодзи (запрещены), mandatory headers (для domain/component), structural integrity L2 → L1. **Запускается в GitHub Actions** в baseline-режиме (Pass 347+): `--baseline config/knowledge/baseline-knowledge-lint.txt` — ранее известные violations игнорируются, новые блокируют merge. Локально без флага — для прогона всех правил. Чтобы уменьшить baseline: фиксить violations и `python3 tools/lint-knowledge.py --generate-baseline FILE`. |
| [`check-spec-issue-link.py`](check-spec-issue-link.py) | CI gate (Pass 349): валидирует наличие `## OpenProject Tracking` секции в `specs/NNN-*/spec.md` (для modern-спек с `## Knowledge References`). Pre-Phase-002 спеки grandfathered. Опциональный `Issue ID: none` для spontaneous-работы. Запускается в GitHub Actions. |

## Код и CI

| Скрипт | Назначение |
|--------|------------|
| `check-kdoc-coverage.sh` | KDoc ≥ 50% (Constitution Principle VI / FR-006). |
| `check-jsdoc-coverage.sh` | JSDoc ≥ 50%. |
| `check-eslint-baseline.sh` | ESLint baseline-aware для `webvue3` / `karaoke-public`. |
| `check-feature-doc.sh` | Per-feature документация (структура + slug == имя файла). |
| `baseline-stats.sh` | Baseline counters (informational). |
| `generate-docs.sh` | Генерация Dokka + typedoc. |
| `check-censored-public.sh` | Smoke-test цензурирования на `karaoke-web` без `karaoke-app`. |
| `check-audit-coverage.sh` | 100% audit coverage. |
| `check-songedit-field-coverage.sh` | UI↔backend field coverage для SongEdit. |
| `check-endpoint-field-coverage.sh` | Общий UI↔backend audit (все пары из endpoint-pairs.yml). |

## Резерв (build / deploy)

| Скрипт | Назначение |
|--------|------------|
| `deploy_web.sh`, `deploy_public.sh` | Деплой на прод (rsync + nginx). |
| `do.sh` | docker-compose build/start. |
| `build-lock.sh` | Сериализация параллельных Gradle-сборок (flock). |
| `lint-*.sh` | Прочие линтеры/проверки (не-документация). |

## OpenProject Tracker (Pass 295/349/350)

| Скрипт | Назначение |
|--------|------------|
| `tracker.sh` | CLI OpenProject: `list-issues`, `get-issue`, `claim-issue`, `add-comment`, `mark-review`, `close-issue`, `reopen-issue`, `create-issue`, `healthcheck`. |
| `tracker-lib.sh` | Общие helpers (env-loading, logging, request). Source'ится из остальных tracker-скриптов. |
| `tracker-poll.sh` | Polling-сводка задач для AI-агента в начале сессии. |
| `tracker-bootstrap-board.sh` | Bootstrap kanban-доски (Pass 295). |
| [`tracker-bootstrap.sh`](tracker-bootstrap.sh) | **NEW (Pass 350)** auto-claim hook before_specify. Detect OpenProject ID в `$ARGUMENTS` regex'ом; вызывает `tracker.sh claim-issue <NN>`. No-op если не найден. |
| [`tracker-implement-done.sh`](tracker-implement-done.sh) | **NEW (Pass 350)** auto-comment + mark-review hook after_implement. Использует или auto-generates `report.md`; вызывает `add-comment + mark-review`. Idempotent. |
| `tracker-smoke-test.sh` | Smoke-test OpenProject подключения. |
| `tracker-spec-for-issue.sh` | Создать specs/NNN-slug/spec.md из OpenProject work package (`claim → bootstrap → spec` workflow). |

## Утилиты для новых скриптов

Раздел об утилитах для разработки новых bash-скриптов.

## См. также

- [AGENTS.md § Перед каждым git commit](../AGENTS.md) — обязательный pre-commit checklist.
- [knowledge/guidelines/code-style.md](../knowledge/guidelines/code-style.md) — code style для shell-скриптов.
