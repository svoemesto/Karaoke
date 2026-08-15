# AGENTS.md — инструкции для агентов

> **Версия**: 2.0.0 | **Last updated**: 2026-08-14 (Pass 62 — фича 189 + 17 follow-ups). Правки governance — в feature-ветке `0XX-agents-md-update`. Детали — в LiveDocs.

## АБСОЛЮТНОЕ ПРАВИЛО: язык общения

**Всё общение с пользователем — ТОЛЬКО на русском языке.** Наивысший приоритет.

## С чего начать сессию (AI-агент)

Прочитай **LiveDocs первым**:
1. [`livedocs/README.md`](livedocs/README.md) — манифест.
2. [`livedocs/INDEX.md`](livedocs/INDEX.md) — карта + decision tree.
3. Перейди в нужный слой: `livedocs/features/`, `livedocs/domain/`, `livedocs/architecture/`.

**Быстрый поиск**:
- **Slash-command**: `/livedocs-find '<query>' [--type TYPE] [--path SUBPATH]` (canonical `livedocs/commands/livedocs-find.md`, runtime в `.opencode/commands/`).
- **Shell-script**: `bash tools/search-livedocs.sh '<query>' [--type feature|domain|architecture|adr|runbook|all]`.

**Актуальное состояние** — [`livedocs/CHANGELOG.md`](livedocs/CHANGELOG.md) (Pass 62+, semantic changelog).

**Только если в LiveDocs нет** — лезть в `specs/<NNN>/spec.md`, `archive/docs/features/*.md` (legacy drill-down), этот файл.

## LiveDocs CI / pre-commit

| Проверка | Что | Где |
|----------|-----|-----|
| `tools/check-livedocs-structure.sh` | 7 структурных проверок (`≥5 фич`, `≥5 BC`, `L1+L2+L3`, frontmatter, `AGENTS.md ≤100`, CI) | **CI** + **pre-commit** |
| `tools/check-livedocs-cross-links.sh` | 818 cross-links (`../X.md` + `related:`) | **CI** + **pre-commit** |
| `tools/check-livedocs-external-links.sh` | External `https://` URLs (strict) | **CI** (strict) |

Локальные правки LiveDocs → запустить все три перед commit.

## Иерархия документации

| Приоритет | Файл | Зачем |
|-----------|------|-------|
| **0** | **`livedocs/`** | ПЕРВЫЙ источник для технических вопросов |
| 1 | `.specify/memory/constitution.md` | NON-NEGOTIABLE принципы |
| 2 | **AGENTS.md** (этот файл) | governance, workflow, ≤ 100 строк |
| 3 | `CONTRIBUTING.md` | стиль кода |
| 4 | `DEVELOPMENT.md` | архитектура + команды |
| 5 | `specs/<NNN>-*/spec.md` | полные спеки (drill-down) |
| 6 | `archive/docs/features/*.md` | per-feature legacy drill-down (после миграции Pass 51) |
| 7 | `livedocs/architecture-notes.md` | датированный changelog (бывший docs/) |
| 8 | `livedocs/strategy/growth.md` | стратегия роста (бывший docs/strategy/) |

**При расхождении** — приоритет у файла с меньшим номером.

## Где правила для разных AI-агентов

| Агент | Файл | В гите? |
|-------|------|---------|
| opencode (primary) | `AGENTS.md` | ✅ |
| Claude Code, Cursor, Cody, Aider | локальные конфиги | ❌ |

Setup — [`livedocs/onboarding.md`](livedocs/onboarding.md, [`livedocs/claude-code-setup.md`](livedocs/claude-code-setup.md)).

## Ограничения агента

**Категорически запрещено:**
1. Пересобирать `karaoke-app` (исключение: `dev-pc` под `dev`).
2. Деплой без явного согласия.
3. Редактировать файлы на сервере.
4. Коммитить секреты: `deploy/.env`, `*.key`, `*.pem` (проверка: `git ls-files | grep -iE '\.env$|\.key$|\.pem$'` пусто).
5. Образы: `nginx:alpine`, `node:latest`, JDK вместо JRE — ЗАПРЕЩЕНЫ.

**Разрешено:** править код, `gradle clean bootJar`, `npm run dev/build`, локальные контейнеры через `deploy/do.sh`.

**Обновление LiveDocs (FR-014)**: при изменении bounded context в `livedocs/domain/` или C4 уровня в `livedocs/architecture/` — в том же PR обновить LiveDoc. CI блокирует merge при failures.

Детали — Constitution § «Ограничения и доступы агента».

## Git — CI-gate для master (NON-NEGOTIABLE)

```bash
N=$(./tools/reserve-branch-number.sh my-slug)
git checkout -b "${N}-my-slug" master && # ... правки ...
git push -u origin "${N}-my-slug" &&
gh pr create --base master &&           # → CI lint.yml (8/8 — добавлен LiveDocs)
gh pr checks &&                         # дождаться PASS
gh pr merge --merge                      # БЕЗ --delete-branch
```

**Прямые коммиты в `master` ЗАПРЕЩЕНЫ.** Lifecycle: ветка живёт после мёрджа.

## Сборка / деплой / тесты

- **Сборка**: `./gradlew clean karaoke-app:bootJar karaoke-web:bootJar --parallel`.
- **Деплой**: `deploy/deploy_web.sh`, `deploy/deploy_public.sh`, `cd deploy && bash do.sh build_start_public`.
- **Тесты**: в CI нет; существующие (`karaoke-app/src/test`) — `@Disabled`. Проверка — пользователем.

Команды — в `DEVELOPMENT.md`.

## Q&A — где искать актуальные знания о проекте?

Детали (Jackson `is`-prefix, Dockerfile ловушки, KDoc backticks, пагинация, sync, queue lanes, тип песни, рендер MP4, StatBySong) — в [LiveDocs](livedocs/README.md). Q&A в этот файл **НЕ добавлять** — только governance.

## Как обновлять этот файл

Правки governance — в ветке `0XX-agents-md-update`, semver bump. **НЕ дублировать** детали и **НЕ добавлять** Q&A.