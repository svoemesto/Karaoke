# AGENTS.md — инструкции для агентов

> **Версия**: 2.1.1 | **Last updated**: 2026-08-31 (Pass 282 — добавлена секция «Машинно-специфичные исключения» с явным исключением для `nsa-i9`/`nsa` по сборке `karaoke-app`). Правки governance — в ветке `0XX-agents-md-update`. Детали — в LiveDocs.

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

## Ограничения агента (NON-NEGOTIABLE)

**Категорически запрещено:** пересобирать `karaoke-app` (исключения: `dev-pc`/`dev`, см. также машинно-специфичные исключения ниже), деплой без согласия, редактировать файлы на сервере, коммитить секреты (`deploy/.env`, `*.key`, `*.pem` — проверка `git ls-files | grep -iE '\.env$|\.key$|\.pem$'` пусто), образы `nginx:alpine`/`node:latest`/JDK вместо JRE.

**Разрешено:** править код, `gradle clean bootJar`, `npm run dev/build`, локальные контейнеры через `deploy/do.sh`.
**Обновление LiveDocs (FR-014)**: при изменении bounded context или C4 уровня — обновить LiveDoc в том же PR.
Детали — Constitution § «Ограничения и доступы агента».

## Машинно-специфичные исключения (NON-NEGOTIABLE в пределах машины)

**Зафиксировано 2026-08-31 (Pass 282).** Исключения из правила «Категорически запрещено пересобирать `karaoke-app`», явно одобренные пользователем для конкретных машин. **НЕ** действуют на других машинах.
### `nsa-i9` / `nsa` (текущая машина)
- ✅ **МОЖНО** пересобирать `karaoke-app` (`./gradlew :karaoke-app:bootJar`) — без явного согласия, на каждой итерации.
- ❌ **НЕЛЬЗЯ** перезапускать контейнер `karaoke-app` (как для всех машин) — только по прямому согласию.
- ✅ **МОЖНО** редактировать любой код, пересобирать `karaoke-web`/`webvue3`/`karaoke-public` через `./gradlew :<module>:bootJar` или `deploy/do.sh`.
- ❌ **НЕЛЬЗЯ** деплоить на прод, редактировать файлы на сервере, перезаписывать `deploy/do.env` — только по прямому согласию.
- **Рациональ:** сборка разрешена для ускорения итераций (Pass 282, T009); на других машинах действуют общие правила Constitution (на `dev-pc`/`dev` — общее исключение).
- **Новое исключение**: добавить подсекцию сюда, обновить версию `AGENTS.md` (semver bump), зафиксировать в `livedocs/architecture-notes.md`.

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

> **Контекст.** Pass 239 + 245: правки без локальной пересборки ломали прод (`Int` vs `Long` mismatch, Rollup кросс-импорты). **Vite-build ≠ Docker-образ** — multi-stage Dockerfile копирует только свой каталог.

**После ЛЮБОГО изменения в коде ОБЯЗАТЕЛЬНО** (в этом порядке):

1. **Backend compile**: `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`
2. **Линтеры**: `./gradlew :karaoke-web:ktlintCheck` + `cd webvue3 && npm run lint` + `cd karaoke-public && npm run lint` (`tools/check-eslint-baseline.sh <pkg>`) — никаких НОВЫХ нарушений (baseline OK).
3. **Backend bootJar**: `./gradlew :karaoke-web:bootJar --parallel` (на `nsa-i9`/`nsa` — также `:karaoke-app:bootJar`; на остальных машинах только `karaoke-web`).
4. **Frontend Vite (оба)**: `cd webvue3 && npm run build && npm run format:check`, затем `cd karaoke-public && npm run build && npm run format:check`.
5. **Docker-образы (оба, NON-NEGOTIABLE)**: `cd deploy && bash do.sh build_webvue3`; если менялся `karaoke-public` — `bash do.sh build_public`.
6. **Только после всех 5 шагов OK** — сообщать «готово к деплою».

**НЕ ПРОПУСКАТЬ** шаги даже для «очевидных» правок.

## Как обновлять этот файл

Правки governance — в ветке `0XX-agents-md-update`, semver bump. **НЕ дублировать** детали.