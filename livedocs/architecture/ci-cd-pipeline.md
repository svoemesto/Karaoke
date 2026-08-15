---
status: Active
slug: ci-cd-pipeline
type: topic
related:
  - ./architecture/L2-containers.md
  - ./liverefs CI workflows (../.github/workflows/lint.yml)
  - ../runbooks/how-to-deploy.md
  - ../features/002-ci-lint-enforcement.md
---

# CI/CD pipeline — как работает master PR

> Drill-down для GitHub Actions. Этот документ — **обзор pipeline** для
> AI-агента, чтобы быстро понять, что произойдёт после push в PR.

## Обзор

Pipeline состоит из **9 jobs** в одном workflow `.github/workflows/lint.yml`:

| Job | Что проверяет | Где блокирует merge |
|-----|---------------|---------------------|
| **ktlint (Kotlin/Java)** | Форматирование Kotlin (baseline-aware) | ✅ обязательно |
| **ESLint + Prettier (webvue3)** | Vue/TS в админке | ✅ обязательно |
| **ESLint + Prettier (karaoke-public)** | Vue/TS в публичном | ✅ обязательно |
| **Docs (structure + offline links)** | Структура per-feature + lychee offline | ✅ обязательно |
| **LiveDocs structure** | 7 проверок структуры (≥5 фич, ≥5 BC, L1+L2+L3, frontmatter, AGENTS.md ≤100, CI) | ✅ обязательно |
| **Baseline stats** | Статистика по baseline (informational) | ❌ informational |
| **KDoc coverage** | KDoc ≥ 100% (informational) | ❌ informational |
| **JSDoc coverage** | JSDoc ≥ 100% (informational) | ❌ informational |
| **LiveDocs external links (advisory)** | External https:// через lychee | ⚠️ advisory (continue-on-error: true) |

**Триггеры**: push в master, pull_request в master, workflow_dispatch.

**Concurrency**: `lint-<branch>` — отменяет более старые запуски для той же ветки.

## Как проходит PR

```
1. PR создан → push → checkout → setup JDK 17 / Node 24
2. Параллельно:
   - backend-lint (ktlint) — gradle с --no-daemon
   - frontend-lint matrix[webvue3, karaoke-public] — npm ci + lint:check + prettier --check
   - docs-lint (lychee offline)
   - livedocs-structure (7 проверок)
   - livedocs-external-links (lychee strict)
   - baseline-stats (informational)
   - kdoc-coverage (informational)
   - jsdoc-coverage (informational)
3. Все обязательные jobs должны PASS → merge разрешён
4. При любом FAIL → PR блокируется (см. AGENTS.md «Git CI-gate»)
```

## Кэширование

- **Gradle**: `setup-java@v4` cache=gradle (`~/.gradle/caches`)
- **npm**: `setup-node@v4` cache=npm per-SPA (`package-lock.json`)

Время CI: ~5-7 минут на PR (с cache), ~10-15 минут без cache.

## Команды для локальной проверки

```bash
# Backend
./gradlew ktlintCheck --no-daemon

# Frontend
cd webvue3       && npm ci && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}" && cd ..
cd karaoke-public && npm ci && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}" && cd ..

# LiveDocs
bash tools/check-livedocs-structure.sh
bash tools/check-livedocs-cross-links.sh
bash tools/check-livedocs-external-links.sh

# Coverage (informational)
bash tools/check-kdoc-coverage.sh
bash tools/check-jsdoc-coverage.sh webvue3
bash tools/check-jsdoc-coverage.sh karaoke-public

# Pre-commit (единая точка)
pre-commit run --all-files
```

## Baseline-подход для линтеров

Все линтеры используют **baseline-файлы** для подавления исторических нарушений:

- `config/ktlint/baseline-*.xml` — Kotlin (per-module)
- `webvue3/.eslint-baseline.json` — webvue3
- `karaoke-public/.eslint-baseline.json` — karaoke-public

**Правило**: новые нарушения (не в baseline) валят CI. Старые (в baseline) — проходят.

**Темп сокращения baseline**: ≥10%/мес (см. baseline-stats job).

Скрипты в `tools/`:
- `baseline-stats.sh` — статистика baseline
- `generate-eslint-baseline.sh` — генерация baseline для ESLint
- `check-eslint-baseline.sh` — проверка отсутствия новых нарушений
- `check-enforcement.sh` — проверка что MUST-правила покрыты baseline

## LiveDocs в CI

**LiveDocs structure** (обязательный):
- ≥ 5 фич в `livedocs/features/`
- ≥ 5 BC в `livedocs/domain/`
- L1 + L2 + L3 в `livedocs/architecture/`
- valid frontmatter во всех .md
- AGENTS.md ≤ 100 строк
- CI integration (ссылка на check-livedocs-structure.sh в lint.yml)

**LiveDocs cross-links** (обязательный):
- Все `../X.md` ссылки валидны
- Все `related:` в frontmatter указывают на существующие файлы
- **Исключения**: templates (плейсхолдеры), runbooks/decisions (без frontmatter)

**LiveDocs external links (advisory)**:
- lychee strict mode для external https://
- Whitelist для known-false-positives: 10.0.0.1, 188.119.64.111, karaoke-web, localhost, id.vk.ru/oauth2, thinkrelevance.com, id.vk.com, nsa-i9, svoemesto.ru, sm-karaoke.ru, smartcaptcha.yandexcloud.net, 127.0.0.1, minio-proxy

## Почему некоторые jobs informational

**Baseline stats, KDoc coverage, JSDoc coverage** — advisory, не блокируют merge.

Причина: исторически baseline 96% (сокращён в PR #190 до 0%), KDoc 100% — ужесточаем постепенно.

Если упадёт до < минимума — это warning, не blocker.

## Как добавить новый job

1. Добавить step в `.github/workflows/lint.yml`
2. Если job обязательный — не ставить `continue-on-error: true`
3. Если advisory — добавить `continue-on-error: true`
4. Проверить локально: `git push` в feature-ветку → проверить `Actions` tab
5. Документировать в этом файле

## Как читать failed CI

1. **Открыть PR** → вкладка **Checks** → выбрать failed job
2. **Развернуть failed step** — посмотреть ошибку
3. **Типичные failures**:
   - ktlint: "Filename `X.kt` should be `X.kt`?" — переименовать; "Format error" — ktlintCheck
   - ESLint: "ERROR: foo is assigned a value but never used" — убрать
   - Prettier: "Code style issues" — `npx prettier --write`
   - LiveDocs structure: "Files with frontmatter: N/M" — добавить frontmatter
   - LiveDocs cross-links: "BROKEN LINK" — исправить relative path

## Связь с Pre-commit

`.pre-commit-config.yaml` дублирует 4 проверки локально:
- `check-yaml`, `check-json`, `check-toml`, `check-xml`
- `check-merge-conflict`, `check-case-conflict`
- `mixed-line-ending`
- `LiveDocs coverage gap warning` (custom hook)

**Pre-commit** запускается **только локально** в `.git/hooks/pre-commit`. В CI не запускается (проверки CI exhaustive).

→ См. `CONTRIBUTING.md` «Pre-commit» секцию.

## Связанные артефакты

- `.github/workflows/lint.yml` — основной workflow
- `.pre-commit-config.yaml` — pre-commit hooks
- `config/ktlint/baseline-*.xml` — Kotlin baseline
- `webvue3/.eslint-baseline.json` — webvue3 baseline
- `karaoke-public/.eslint-baseline.json` — karaoke-public baseline
- `tools/check-*.sh` — LiveDocs скрипты

## См. также

- `../runbooks/how-to-deploy.md` — deploy workflow
- `../features/002-ci-lint-enforcement.md` — per-feature документ (FR-013)
- `../architecture/L2-containers.md` — что где запускается
- `../CONTRIBUTING.md` — стиль кода

## История

- Создан: 2026-08-14 (Pass 51+ follow-up спеки 189)
- Автор: opencode (MiniMax-M3)
- Последнее обновление: 2026-08-14