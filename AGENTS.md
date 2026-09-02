# AGENTS.md — инструкции для агентов

> **Версия**: 2.1.2 | **Last updated**: 2026-09-02 (Pass 295).

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

## Где смотреть логи прода

- **`docs/ops/log-correlation.md`** — карта логов прода, команды `docker logs`/`ssh`, grep-маркеры (`infra.prod.ping`/`infra.prod.db`/`LOG:  duration:`), сценарии диагностики. Создан в [specs/288-prod-diagnostics-logging](../specs/288-prod-diagnostics-logging/spec.md) (FR-019).
- Контракт WARN/INFO для `infra.prod.*`: [contracts/log-format.md](../specs/288-prod-diagnostics-logging/contracts/log-format.md).

## Иерархия документации (краткая)

`livedocs/` → `.specify/memory/constitution.md` → `AGENTS.md` → `CONTRIBUTING.md` → `DEVELOPMENT.md` → `specs/<NNN>-*/spec.md` → `archive/docs/features/*.md`. **При расхождении** — приоритет у файла с меньшим номером. Полная таблица — в [`livedocs/architecture-notes.md`](livedocs/architecture-notes.md).

## Где правила для разных AI-агентов

- opencode (primary) → `AGENTS.md` (✅ в гите)
- Claude Code, Cursor, Cody, Aider → локальные конфиги (❌)
- Setup — [`livedocs/onboarding.md`](livedocs/onboarding.md).

## Issue-tracker OpenProject (spec 295) — ВАЖНО

В начале **каждой сессии** (после чтения LiveDocs):

```bash
cd /home/nsa/Karaoke && source .env.local-tracker && bash tools/tracker-poll.sh
```

Если есть открытые задачи (`assignee=ai-agent, status=open`):
`spec-for-issue → claim → работа → add-comment + mark-review → close`.
Docs: [`docs/tracker-setup.md`](docs/tracker-setup.md), [`livedocs/features/295-jira-local-integration.md`](livedocs/features/295-jira-local-integration.md).

## Ограничения агента (NON-NEGOTIABLE)

**Запрещено:** пересобирать `karaoke-app` (исключения см. ниже), деплой без согласия, редактировать файлы на сервере, коммитить секреты (`deploy/.env`, `*.key`, `*.pem` — `git ls-files | grep -iE '\.env$|\.key$|\.pem$'` пусто), образы `nginx:alpine`/`node:latest`/JDK вместо JRE. **Разрешено:** править код, `gradle clean bootJar`, `npm run dev/build`, локальные контейнеры через `deploy/do.sh`. **Обновление LiveDocs (FR-014)**: при изменении bounded context или C4 уровня — обновить LiveDoc в том же PR.

### Машинно-специфичные исключения (Pass 282)

#### `nsa-i9` / `nsa` (текущая)
- ✅ `karaoke-app` пересобирать без явного согласия. ❌ Контейнер `karaoke-app` перезапускать только по согласию.
- ✅ Править любой код, пересобирать `karaoke-web`/`webvue3`/`karaoke-public`.
- ❌ Деплой на прод, правка файлов на сервере, `deploy/do.env` — только по согласию.
- **Новое исключение** → подсекция + semver bump `AGENTS.md` + `livedocs/architecture-notes.md`.

## Git — CI-gate для master (NON-NEGOTIABLE)

```bash
N=$(./tools/reserve-branch-number.sh my-slug)
git checkout -b "${N}-my-slug" master && # правки ...
git push -u origin "${N}-my-slug" && gh pr create --base master
gh pr checks && gh pr merge --merge   # БЕЗ --delete-branch
```

Прямые коммиты в `master` ЗАПРЕЩЕНЫ. Lifecycle: ветка живёт после мёрджа.

## Сборка / деплой / тесты

- **Сборка**: `./gradlew clean karaoke-app:bootJar karaoke-web:bootJar --parallel`.
- **Деплой**: `deploy/deploy_web.sh`, `deploy/deploy_public.sh`, `cd deploy && bash do.sh build_start_public`.
- **Тесты**: в CI нет; `karaoke-app/src/test` — `@Disabled`. Проверка — пользователем.

### Обязательная проверка после ЛЮБОГО изменения кода (NON-NEGOTIABLE)

> Pass 239 + 245: правки без локальной пересборки ломали прод. **Vite-build ≠ Docker-образ**.

**После ЛЮБОГО изменения ОБЯЗАТЕЛЬНО** (в этом порядке):

1. Backend compile: `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`
2. Линтеры: `./gradlew :karaoke-web:ktlintCheck` + `cd webvue3 && npm run lint` + `cd karaoke-public && npm run lint`
3. Backend bootJar: `./gradlew :karaoke-web:bootJar --parallel` (на `nsa-i9` — также `:karaoke-app:bootJar`)
4. Frontend Vite: `npm run build && npm run format:check` в `webvue3/` и `karaoke-public/`
5. Docker-образы: `cd deploy && bash do.sh build_webvue3`; если менялся `karaoke-public` — `bash do.sh build_public`

Только после всех 5 шагов OK — сообщать «готово к деплою». **НЕ ПРОПУСКАТЬ** даже для «очевидных» правок.

## Как обновлять этот файл

Правки governance — в ветке `0XX-agents-md-update`, semver bump. **НЕ дублировать** детали.