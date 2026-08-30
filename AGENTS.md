# AGENTS.md — инструкции для агентов

> **Версия**: 2.1.0 | **Last updated**: 2026-08-30 (Pass 245 — добавил обязательную Docker-сборку образов webvue3 и public; ранее агент мог пропустить, считая локальный `npm run build` достаточным). Правки governance — в ветке `0XX-agents-md-update`. Детали — в LiveDocs.

## АБСОЛЮТНОЕ ПРАВИЛО: язык общения

**Всё общение с пользователем — ТОЛЬКО на русском языке.** Наивысший приоритет.

## С чего начать сессию (AI-агент)

Прочитай **LiveDocs первым**: [`livedocs/README.md`](livedocs/README.md) → [`livedocs/INDEX.md`](livedocs/INDEX.md) → нужный слой (`features/`, `domain/`, `architecture/`).

**Быстрый поиск**: `bash tools/search-livedocs.sh '<query>'` или `/livedocs-find '<query>'`.

**Только если в LiveDocs нет** — `specs/<NNN>/spec.md`, `archive/docs/features/*.md`, этот файл.

## LiveDocs CI / pre-commit

| Проверка | Что | Где |
|----------|-----|-----|
| `tools/check-livedocs-structure.sh` | 7 структурных проверок (`≥5 фич`, `≥5 BC`, `L1+L2+L3`, frontmatter, `AGENTS.md ≤100`, CI) | **CI** + **pre-commit** |
| `tools/check-livedocs-cross-links.sh` | cross-links (`../X.md` + `related:`) | **CI** + **pre-commit** |
| `tools/check-livedocs-external-links.sh` | External `https://` URLs (strict) | **CI** (strict) |

Локальные правки LiveDocs → запустить все три перед commit.

## Иерархия документации (краткая)

`livedocs/` → `.specify/memory/constitution.md` → `AGENTS.md` → `CONTRIBUTING.md` → `DEVELOPMENT.md` → `specs/<NNN>-*/spec.md` → `archive/docs/features/*.md`. **При расхождении** — приоритет у файла с меньшим номером. Полная таблица — в [`livedocs/architecture-notes.md`](livedocs/architecture-notes.md).

## Где правила для разных AI-агентов

- opencode (primary) → `AGENTS.md` (✅ в гите)
- Claude Code, Cursor, Cody, Aider → локальные конфиги (❌)
- Setup — [`livedocs/onboarding.md`](livedocs/onboarding.md).

## Ограничения агента (NON-NEGOTIABLE)

**Категорически запрещено:** пересобирать `karaoke-app` (исключение: `dev-pc`/`dev`), деплой без согласия, редактировать файлы на сервере, коммитить секреты (`deploy/.env`, `*.key`, `*.pem` — проверка `git ls-files | grep -iE '\.env$|\.key$|\.pem$'` пусто), образы `nginx:alpine`/`node:latest`/JDK вместо JRE.

**Разрешено:** править код, `gradle clean bootJar`, `npm run dev/build`, локальные контейнеры через `deploy/do.sh`.

**Обновление LiveDocs (FR-014)**: при изменении bounded context или C4 уровня — обновить LiveDoc в том же PR.

Детали — Constitution § «Ограничения и доступы агента».

## Git — CI-gate для master (NON-NEGOTIABLE)

```bash
N=$(./tools/reserve-branch-number.sh my-slug)
git checkout -b "${N}-my-slug" master && # ... правки ...
git push -u origin "${N}-my-slug" &&
gh pr create --base master &&           # → CI lint.yml
gh pr checks &&                         # дождаться PASS
gh pr merge --merge                      # БЕЗ --delete-branch
```

**Прямые коммиты в `master` ЗАПРЕЩЕНЫ.** Lifecycle: ветка живёт после мёрджа.

## Сборка / деплой / тесты

- **Сборка**: `./gradlew clean karaoke-app:bootJar karaoke-web:bootJar --parallel`.
- **Деплой**: `deploy/deploy_web.sh`, `deploy/deploy_public.sh`, `cd deploy && bash do.sh build_start_public`.
- **Тесты**: в CI нет; существующие (`karaoke-app/src/test`) — `@Disabled`. Проверка — пользователем.

### Обязательная проверка после ЛЮБОГО изменения кода (NON-NEGOTIABLE)

> **Контекст.** Pass 239: агент внёс правки в `Zakroma.kt`, но не пересобрал — Kotlin mismatch
> (`Int` vs `Long`) пойман только на стороне пользователя. **Pass 245 (2026-08-30)**: агент добавил
> импорт `formatText` из `karaoke-public/src/composables/useKaraokeEditor` в `webvue3/src/components/
> SongEditor/ReviewModal.vue`; локальный `npm run build` прошёл, но `bash do.sh build_webvue3` упал —
> `Rollup failed to resolve "../../../../karaoke-public/..."`. Причина: multi-stage Dockerfile
> (`deploy/karaoke-webvue3/Dockerfile`) делает `COPY ./webvue3/ .` — в `/app/` попадает ТОЛЬКО
> webvue3, кросс-импорты выходят за пределы контекста. **Vite-build ≠ Docker-образ.**

**После ЛЮБОГО изменения в коде ОБЯЗАТЕЛЬНО** (в этом порядке):

1. **Backend compile**: `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`
2. **Линтеры**: `./gradlew :karaoke-web:ktlintCheck` + `cd webvue3 && npm run lint` + `cd karaoke-public && npm run lint` (`tools/check-eslint-baseline.sh <pkg>`) — никаких НОВЫХ нарушений (baseline OK).
3. **Backend bootJar**: `./gradlew :karaoke-web:bootJar --parallel`
4. **Frontend Vite (оба)**: `cd webvue3 && npm run build && npm run format:check`, затем `cd karaoke-public && npm run build && npm run format:check` (Pass 244: prettier — всегда, не только в pre-commit).
5. **Docker-образы (оба, NON-NEGOTIABLE)**: `cd deploy && bash do.sh build_webvue3`; если менялся `karaoke-public` (или есть кросс-импорты) — `bash do.sh build_public`. Vite-build на хосте ≠ multi-stage Docker: `COPY ./webvue3/ .` / `COPY ./karaoke-public/ .` копируют ТОЛЬКО свой каталог; кросс-импорты `../../karaoke-public/...` или `../../webvue3/...` падают внутри контейнера.
6. **Только после всех 5 шагов OK** — сообщать «готово к деплою».

**НЕ ПРОПУСКАТЬ** шаги даже для «очевидных» правок. Детали — в
[livedocs/architecture-notes.md](livedocs/architecture-notes.md).

## Как обновлять этот файл

Правки governance — в ветке `0XX-agents-md-update`, semver bump. **НЕ дублировать** детали.
Детали проекта (Jackson `is`-prefix, Dockerfile, sync, MP4, StatBySong и т.д.) — в
[LiveDocs](livedocs/README.md) (не в этом файле).