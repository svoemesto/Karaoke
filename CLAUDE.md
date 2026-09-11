# Karaoke Project Guidelines

## Project Overview
"Karaoke" (svoemesto) is a self-hosted pipeline for automated karaoke video production.

## Key Documentation Files
This project has two main documentation files that contain detailed technical information:

1. **DEVELOPMENT.md** — Main development guide with:
   - Project structure and modules
   - Build and deployment commands
   - Architecture notes and key invariants
   - Common pitfalls and solutions

2. **docs/architecture-notes-archive.md** — Detailed history of features and bug fixes:
   - Chronological records of implemented features
   - Debugging notes and troubleshooting guides
   - Technical decisions and their rationale

## Working with Documentation
When you need to understand:
- How the project is structured → read DEVELOPMENT.md
- Why a specific feature works the way it does → check docs/architecture-notes-archive.md
- Common issues and their fixes → both files contain relevant information

**Important:** Before making significant changes, consult these files to understand the existing patterns and avoid known pitfalls.

## Tech Stack
- Backend: Kotlin/Spring Boot, Gradle, JDK 17
- Frontend: Vue 3 + Vite (webvue3 for admin, karaoke-public for public site)
- Database: PostgreSQL
- Storage: MinIO
- Video rendering: MLT framework (melt CLI)
- Audio processing: Demucs, ffmpeg, Sheetsage

## Development Workflow
- All build/deploy commands are in `deploy/do.sh`
- Always run commands from the `deploy/` directory
- Check DEVELOPMENT.md for specific command syntax and common issues
- The project uses a dual-database sync system (LOCAL ↔ SERVER)

## Code Style
- Follow existing patterns in the codebase
- Use nullable types for database columns that allow NULL
- Avoid `is*` prefix for boolean fields in DTOs (Jackson serialization issue)
- Always URL-encode query parameters with special characters

---

## 🚦 MUST-CHECKLIST при старте сессии (NON-NEGOTIABLE)

> Этот список **дублирует** MUST #0 из `AGENTS.md` и **обязательный
> failure-stop**: если шаг пропущен — СТОП, дальше не продолжать.
> Полная нормативная база — [`AGENTS.md`](AGENTS.md), MUST #0.

Перед **любыми** правками агент MUST выполнить эти шаги **именно в этом порядке**:

1. **MUST прочитать** `AGENTS.md` — runtime-инструкции, иерархия документов, ловушки.
2. **MUST прочитать** `.specify/memory/constitution.md` (NON-NEGOTIABLE) — если нарушаешь принцип, должен явно обосновать **в спеке/PR**.
3. **MUST прочитать** `CONTRIBUTING.md` (стиль кода, Kotlin/Vue/SQL/MD/Sh/Docker).
4. **MUST прочитать** `DEVELOPMENT.md` (архитектура, как устроен проект).
5. **MUST прочитать** `knowledge/README.md` + `knowledge/domains/README.md` — **полностью** (Knowledge-first pre-flight, см. AGENTS.md MUST #0).
6. **MUST определить релевантные домены** через `grep -r '<keyword>' knowledge/` — минимум **3 попытки**.
7. **MUST прочитать** `domain.md` + **все** `components/*.md` для каждого релевантного домена.
8. **MUST прочитать** все `local-*.md` ADR из `knowledge/adr/` (принятые решения, запрещено переизобретать).
9. **MUST прочитать** `docs/features/<slug>.md` — per-feature документ, **если правлю код этой фичи** (Constitution FR-009).
10. **MUST прочитать** `docs/strategy/growth.md` — если правлю публичный модуль или монетизацию.

**Failure-stop**: если grep по `knowledge/` ничего не дал — зафиксировать в `spec.md` явно («Searched: ... → no relevant docs»). Если релевантное содержимое найдено, но проигнорировано — спека MUST быть возвращена на `/speckit.clarify`.

> ⚠️ **Не игнорируй этот список.** Без `AGENTS.md` ты не знаешь про ktlint-ловушки,
> KDoc coverage 100%, pre-commit хуки, `redirectErrorStream(true)` и кучу другого.

---

## 🚦 Sandbox DSH (Pass 367)

> ⚠️ **NON-NEGOTIABLE**: в режиме workspace-write НЕ эскалируй на
> `danger-full-access` для gradle compile/ktlint/bootJar, docker build/restart/logs,
> npm/vite/prettier — для всего есть workspace-обход через
> `/home/nsa/Karaoke/.gradle/` и `/home/nsa/Karaoke/.docker/`.
>
> **Эскалируй** только если (а) операция меняет состояние **вне** workspace
> (прод-БД, файлы на сервере, `/etc/...`), **и** (б) нет технического обхода,
> **и** (в) задача не может быть отложена без потери смысла.

**Pre-flight** (в начале сессии, перед первым gradle/docker/npm вызовом):

```bash
bash /home/nsa/Karaoke/tools/check-sandbox-ready.sh
```

Скрипт probe'ит read-only границы, создаёт workspace-аналоги, печатает
готовые команды. При `source` экспортирует `GRADLE_USER_HOME`,
`DOCKER_CONFIG` и функцию-обёртку `docker()`.

**Рецепты** (использовать всегда, см. таблицу в `AGENTS.md` § «Sandbox DSH»):

```bash
# Gradle — обязательно GRADLE_USER_HOME
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:compileKotlin --parallel
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:ktlintCheck
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:bootJar --parallel

# Docker — обязательно --config
docker --config=/home/nsa/Karaoke/.docker build ...
docker --config=/home/nsa/Karaoke/.docker restart karaoke-web

# npm / vite — workspace-local cache, обход не нужен
cd webvue3 && npm run lint && npm run build
```

Подробности: [`AGENTS.md` § «Sandbox DSH: границы и fallback-пути»](AGENTS.md),
[`knowledge/adr/local-0010-sandbox-recipes.md`](knowledge/adr/local-0010-sandbox-recipes.md).

---

## 🚦 ОБЯЗАТЕЛЬНО перед каждым `git commit`

> ⚠️ **NON-NEGOTIABLE** (Pass 367): все `./gradlew ...` команды ДОЛЖНЫ идти с
> `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle`; все `docker` команды —
> через `docker --config=/home/nsa/Karaoke/.docker`. Без этого wrapper/buildx
> пытается писать в read-only `~/.gradle/wrapper/dists/` и `~/.docker/buildx/activity/`.
> См. `AGENTS.md` § «Sandbox DSH» и раздел «Sandbox DSH» выше.

```bash
# 1. Линтеры (все gradle — с GRADLE_USER_HOME, все docker — с --config)
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew ktlintCheck                                      # Kotlin
cd webvue3       && npm run lint:check && cd ..            # webvue3
cd karaoke-public && npm run lint:check && cd ..          # karaoke-public

# 2. Покрытие документацией (FR-006)
bash tools/check-kdoc-coverage.sh                          # 100%
bash tools/check-jsdoc-coverage.sh webvue3                 # 100%
bash tools/check-jsdoc-coverage.sh karaoke-public          # 100%

# 3. Prettier --check (CI падает на любых warning)
cd webvue3       && npx prettier --check "src/**/*.{vue,js,ts,json}" && cd ..
cd karaoke-public && npx prettier --check "src/**/*.{vue,js,ts,json}" && cd ..

# 4. Pre-commit (7 проверок, единая точка)
pre-commit run --all-files
```

Если что-то падает — **исправь**, не коммить с `--no-verify` (исключение: срочный hotfix).

---

## 🚦 CI 7/7 PASS — обязательно перед merge

| Проверка | Команда для локальной проверки |
|----------|------------------------------|
| ktlint (Kotlin/Java) | `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew ktlintCheck` |
| ESLint + Prettier (webvue3) | `cd webvue3 && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}" && cd ..` |
| ESLint + Prettier (karaoke-public) | `cd karaoke-public && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}" && cd ..` |
| Docs (structure + offline links) | автоматически в CI |
| Baseline stats | автоматически в CI (informational) |
| KDoc coverage (≥50%, блокирует) | `bash tools/check-kdoc-coverage.sh --strict` |
| JSDoc coverage (≥50%, блокирует) | `bash tools/check-jsdoc-coverage.sh --strict` |

**Если хотя бы одна падает — PR не мержится** (CI блокирует merge). Единственная
информационная (не блокирующая) проверка — Baseline stats.

---

## 🚦 TOP-10 ловушек (из реальных багов)

1. **Backticks в KDoc** ломают парсер ktlint. Заменять `` `multitrack` `` → «multitrack».
2. **`redirectErrorStream(false)`** для `ProcessBuilder` блокирует процесс. Всегда `true` (CONTRIBUTING.md).
3. **`nginx:alpine`** — нет bash, контейнер падает. Использовать `nginx:stable`.
4. **`node:latest`** — недетерминированный. Использовать `node:22-alpine`.
5. **Двух-фронтенд**: admin (`webvue3`) и public (`karaoke-public`) — **разные приложения**, не смешивать.
6. **Сырой JDBC + recordhash** (никакого JPA/Hibernate), `associateBy { it.id }` для diff — не O(n²).
7. **Per-feature документ** обновлять при правке кода фичи (FR-009) — иначе PR rejection.
8. **Git push через VPN** — может упасть `EOF` / `400 Bad request`. Запускать без VPN.
9. **CI блокирует merge** при любом failing check. Не использовать `gh pr merge --admin` для обхода (только в исключительных случаях, по согласованию).
10. **«Доступ — только онлайн»** (см. оферту). **НЕ упоминать** MP4/скачивание в рекламных материалах и комментариях к коду.
11. **Санитайзер идемпотентен** (`SanitizePath.kt`, `Extentions.kt`). Удаление символа (drop) вместо замены (replace) ломает идемпотентность и ведёт к потере данных при импорте (issue #53: `!`/`?` молча удалялись → файлы не находились). Все правки санитайзера MUST соблюдать контракт `sanitize(sanitize(s)) == sanitize(s)` (см. `docs/features/idempotent-path-sanitize.md`).

---

## 🚦 Стратегия проекта (visitor→registration→premium)

**Главный фокус**: visitor → registration (конверсия 0.4%, потенциал ×5-13).
Модель монетизации и trial — вторичные приоритеты.

**Принятые решения** (полный список в `docs/strategy/growth.md`):
- **Модель D (гибрид)**: эфир-N-дней + 1 трек/исполнитель + 1 альбом/исполнитель free
- **11 097 grandfathered** (эфирные): «решим позже», не трогаем в первом раунде
- **Без trial** в первом раунде (anti-fraud дороже выгоды)
- **Сайт-центричная модель**: площадки (Sponsr/Dzen/VK/Max/TG) = технический канал (DEMO), **не реклама**
- **Только онлайн** (оферта): не упоминать MP4/скачивание

**Top-3 фичи первого раунда**: QW-9 (страница «О проекте»), QW-2 (5 причин зарегистрироваться), QW-1 (таблица FREE vs PREMIUM).

---

## 🚦 Git workflow

```bash
# Перед серьёзной работой
git pull && git status

# Перед правкой кода фичи — обновить docs/features/<slug>.md (FR-009)
# Перед commit — 7 проверок (см. выше)

# НЕ делать:
# - Прямые коммиты в master (только в feature-ветке: 0XX-name)
# - git add . / git add *  (всегда проверять git status)
# - git commit --no-verify (только в крайнем случае)
# - Force-push в main/master (своя ветка — можно)
```

---

## 🚦 Tech Stack (краткая выжимка)

- **Backend**: Kotlin 2.x, Spring Boot 3.x, JDK 17, Gradle
- **Frontend**: Vue 3 + Vite + Bootstrap 5 (karaoke-public) / Bootstrap-vue-next (webvue3)
- **DB**: PostgreSQL (raw JDBC, без JPA/Hibernate)
- **Storage**: MinIO (S3-compatible)
- **ML**: Ollama, Demucs, Sheetsage (локально)
- **Deploy**: Docker + docker-compose

---

## 🚦 Документы проекта (докуда ходить)

| Файл | Зачем |
|------|-------|
| `AGENTS.md` | Правила opencode-стиля (читай обязательно) |
| `DEVELOPMENT.md` | Архитектура + команды |
| `CONTRIBUTING.md` | Стиль кода (Kotlin/Vue/SQL/MD/Sh/Docker) |
| `docs/onboarding.md` | Setup новой машины |
| `docs/claude-code-setup.md` | Эта инструкция + детали для Claude Code |
| `docs/architecture-notes.md` | Changelog последних PR |
| `docs/features/<slug>.md` | Per-feature (11 + 1 документ) |
| `docs/strategy/growth.md` | Стратегия роста (воронка, гипотезы, roadmap) |
| `docs/strategy/growth-audit.md` | Полный аудит (37+ гипотез) |
| `docs/api/` | API-эндпоинты (сгенерировано из кода) |
| `.specify/memory/constitution.md` | NON-NEGOTIABLE принципы |

---

## 🚦 MCP-серверы (если доступны)

- **`codegraph`** — read-only индекс символов. **Использовать ТОЛЬКО ПОСЛЕ Knowledge-first pre-flight** (см. MUST #0 в `AGENTS.md` и MUST-CHECKLIST выше). Понимание кода — это **шаг после** Knowledge, а не вместо. (Прецедент 2026-09-09: spec #339 — агент полез в `codegraph_explore` ДО Knowledge и изобрёл форму кеша вместо паттернов из `caching-patterns.md`.)

---

## 🚦 НЕ делать

- ❌ Коммитить в master (только в feature-ветке)
- ❌ Менять `AGENTS.md`, `constitution.md`, `.gitignore` без согласования — **исключение**: governance-PR с явной формулировкой «governance-knowledge-first» / «agents-md-update» / «constitution-amendment» в slug допустим, **но PR всё равно проходит через обязательное ревью** (см. ниже).
- ❌ Менять конфигурацию линтеров (`.pre-commit-config.yaml`, baseline-файлы) без согласования
- ❌ Использовать `nginx:alpine`, `node:latest` (см. ловушки)
- ❌ Импортировать JPA/Hibernate (только raw JDBC)
- ❌ Обещать в коде/рекламе MP4/скачивание (см. оферту)
- ❌ Упоминать площадки в рекламных материалах (сайт-центричная модель)
- ❌ Добавлять «trial»-механику (отложено до следующего раунда)
- ❌ Полезть в `codegraph_explore` / `grep` по `src/` **до** Knowledge-first pre-flight (см. MUST #0 в `AGENTS.md`)

**Governance-review требования** (для PR, меняющих `AGENTS.md` / `constitution.md` / `.gitignore`):

1. PR MUST содержать секцию «Governance Impact» с перечислением **всех** правил, которые меняются/добавляются/удаляются.
2. PR MUST иметь версионный bump (semver) в header изменяемого файла.
3. PR MUST получить одобрение владельца перед merge (CODEOWNERS для `AGENTS.md` / `constitution.md`).
4. В changelog (`docs/architecture-notes.md`) MUST быть запись «Pass N: governance amendment — <summary>».

---

**Версия**: 1.2.0 (2026-09-11, Pass 367 — sandbox-recipes)
**Изменения 1.2.0** (Pass 367):
- Добавлен раздел «Sandbox DSH» — синхронизация с `AGENTS.md` § «Sandbox DSH: границы и fallback-пути». Pre-flight `tools/check-sandbox-ready.sh`, рецепты для gradle/docker/npm без эскалации на `danger-full-access`.
- В чеклисте «Перед каждым git commit» добавлены docker-команды с `docker --config=/home/nsa/Karaoke/.docker`.
- Версия `AGENTS.md` синхронизирована: 2.4.0.

**Изменения 1.1.0**:
- MUST-CHECKLIST приведён в соответствие с MUST #0 (Knowledge-first pre-flight).
- Правило про `codegraph` переписано: ТОЛЬКО ПОСЛЕ Knowledge-first (раньше было «ПЕРЕД grep/Read», что прямо противоречило MUST #0).
- Добавлены governance-review требования для правок `AGENTS.md` / `constitution.md` / `.gitignore`.
**Связанные документы**:
- `docs/claude-code-setup.md` — детальная инструкция
- `AGENTS.md` — runtime-правила
- `docs/strategy/growth.md` — стратегия
