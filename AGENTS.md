# AGENTS.md — инструкции для агентов

> **Версия**: 2.0.0 | **Last updated**: 2026-08-14 (Pass 62 — фича 189).
> Правки governance — в feature-ветке `0XX-agents-md-update`. Детали — в LiveDocs.

## АБСОЛЮТНОЕ ПРАВИЛО: язык общения

**Всё общение с пользователем — ТОЛЬКО на русском языке.** Наивысший приоритет.

## С чего начать сессию (AI-агент)

Прочитай **LiveDocs первым**:
1. [`livedocs/README.md`](livedocs/README.md) — манифест.
2. [`livedocs/INDEX.md`](livedocs/INDEX.md) — карта + decision tree.
3. Перейди в нужный слой: `livedocs/features/`, `livedocs/domain/`, `livedocs/architecture/`.

**Только если в LiveDocs нет** — лезь в `specs/<NNN>/spec.md`, `docs/features/*.md` (legacy), этот файл (governance).

## Иерархия документации

| Приоритет | Файл | Зачем |
|-----------|------|-------|
| **0** | **`livedocs/`** | ПЕРВЫЙ источник для технических вопросов |
| 1 | `.specify/memory/constitution.md` | NON-NEGOTIABLE принципы |
| 2 | **AGENTS.md** (этот файл) | governance, workflow, ≤ 100 строк |
| 3 | `CONTRIBUTING.md` | стиль кода |
| 4 | `DEVELOPMENT.md` | архитектура + команды |
| 5 | `specs/<NNN>-*/spec.md` | полные спеки (drill-down) |
| 6 | `docs/features/*.md` | per-feature legacy drill-down |
| 7 | `docs/architecture-notes.md` | датированный changelog |
| 8 | `docs/strategy/growth.md` | стратегия роста |

**При расхождении** — приоритет у файла с меньшим номером.

## Где правила для разных AI-агентов

| Агент | Файл | В гите? |
|-------|------|---------|
| opencode (primary) | `AGENTS.md` | ✅ |
| Claude Code, Cursor, Cody, Aider | локальные конфиги | ❌ |

Setup — [`docs/onboarding.md`](docs/onboarding.md, `docs/claude-code-setup.md`).

## Ограничения агента

**Категорически запрещено:**
1. Пересобирать `karaoke-app` (исключение: `dev-pc` под `dev`).
2. Деплой без явного согласия.
3. Редактировать файлы на сервере.
4. Коммитить секреты: `deploy/.env`, `*.key`, `*.pem` (проверка: `git ls-files | grep -iE '\.env$|\.key$|\.pem$'` пусто).
5. Образы: `nginx:alpine`, `node:latest`, JDK вместо JRE — ЗАПРЕЩЕНЫ.

**Разрешено:** править код, `gradle clean bootJar`, `npm run dev/build`,
собирать локальные контейнеры через `deploy/do.sh`.

**Обновление LiveDocs (FR-014)**: при изменении bounded context в `livedocs/domain/` или C4 уровня в `livedocs/architecture/` — в том же PR обновить LiveDoc. CI (`tools/check-livedocs-structure.sh`) блокирует merge при failures.

Детали — Constitution § «Ограничения и доступы агента».

## Git — CI-gate для master (NON-NEGOTIABLE)

```bash
N=$(./tools/reserve-branch-number.sh my-slug)
git checkout -b "${N}-my-slug" master
# ... правки ...
git push -u origin "${N}-my-slug"
gh pr create --base master         # → CI lint.yml (7/7)
gh pr checks                       # дождаться PASS
gh pr merge --merge                # БЕЗ --delete-branch
```

**Прямые коммиты в `master` ЗАПРЕЩЕНЫ.** Lifecycle: ветка живёт после мёрджа.

## Сборка / деплой / тесты

- **Сборка**: `./gradlew clean karaoke-app:bootJar karaoke-web:bootJar --parallel` (параллельные gradle-сборки запрещены).
- **Деплой**: `deploy/deploy_web.sh`, `deploy/deploy_public.sh`, `cd deploy && bash do.sh build_start_public`.
- **Тесты**: в CI нет; существующие (`karaoke-app/src/test`) — `@Disabled`. Проверка — пользователем.

Команды — в `DEVELOPMENT.md`.

## Q&A — где искать

Детали (Jackson `is`-prefix, Dockerfile ловушки, KDoc backticks, пагинация,
sync, queue lanes, тип песни, рендер MP4, StatBySong) мигрированы в LiveDocs:

| Тема | LiveDoc |
|------|---------|
| Jackson `is`-prefix | `livedocs/architecture/jackson-conventions.md` |
| Docker-образы | `livedocs/architecture/docker-conventions.md` |
| KDoc / JSDoc | `livedocs/architecture/documentation-conventions.md` |
| webvue3 пагинация, SKIP | `livedocs/architecture/webvue3-patterns.md` |
| LOCAL ↔ SERVER sync | `livedocs/architecture/data-sync.md` |
| Async-очередь | `livedocs/architecture/queue-lanes.md` |
| Тип песни | `livedocs/domain/catalog.md` |
| Рендер MP4 | `livedocs/domain/processing.md` |
| Счётчики StatBySong | `livedocs/domain/publishing.md` |

## Как обновлять этот файл

Правки governance (CI-gate, lifecycle, иерархия) — в ветке `0XX-agents-md-update`, semver bump. **НЕ дублировать** детали (→ LiveDocs). **НЕ добавлять** Q&A (→ LiveDocs).