# AGENTS.md — инструкции для агентов

> **Версия**: 2.2.0 | **Last updated**: 2026-09-09 (Pass 340).
>
> **Изменения 2.2.0** (см. PR #340 — governance-knowledge-first):
> - Добавлен MUST #0 «Knowledge-first pre-flight (NON-NEGOTIABLE)».
>   Прецедент: spec #339 (2026-09-09) — агент пропустил Knowledge,
>   изобрёл форму кеша вместо использования паттернов из
>   `knowledge/domains/caching/components/caching-patterns.md`.
> - Введён failure-stop: при обнаружении релевантного содержимого
>   в Knowledge, которое проигнорировано, спека MUST быть возвращена
>   на `/speckit.clarify`.
> - Введён `tools/spec-knowledge-preflight.sh` (пре-хуковый скрипт
>   для `tools/specify-bootstrap.sh`).
> - Синхронизировано с Constitution Principle IX (Knowledge-first)
>   и обязательной секцией «Knowledge References» в `spec.md`.

## АБСОЛЮТНОЕ ПРАВИЛО: язык общения

**Всё общение с пользователем — ТОЛЬКО на русском языке.** Наивысший приоритет.

## MUST #0 — Knowledge-first pre-flight (NON-NEGOTIABLE)

> **Цель**: НИ ОДНА новая спека / серьёзная правка кода НЕ ДОЛЖНА
> появляться без предварительной проверки Knowledge на релевантные
> bounded contexts, паттерны и ADR.
>
> **Прецедент (NON-NEGOTIABLE)** — Spec #339, 2026-09-09: агент
> пропустил Knowledge-first, пошёл сразу в `codegraph_explore` по
> `HealthReport` и `fileExists`, и **изобрёл форму кеша** вместо
> использования устоявшихся паттернов из
> `knowledge/domains/caching/components/caching-patterns.md`.
> Результат: спека приведена в негодность, ветка удалена, NNN 339
> освобождён.

### Шаги MUST (выполнять ВСЕ до любых codegraph_explore / grep по src/)

1. **MUST прочитать** [`knowledge/README.md`](knowledge/README.md) +
   [`knowledge/domains/README.md`](knowledge/domains/README.md) —
   **полностью**.
2. **MUST определить релевантные домены** через
   `grep -r '<keyword>' knowledge/` — минимум **3 попытки** с разными
   ключевыми словами задачи (имена сущностей, технологии, действия).
3. **MUST прочитать** `domain.md` + **все** `components/*.md` для каждого
   релевантного домена — **до** обращения к коду.
4. **MUST прочитать** **все** `local-*.md` ADR из [`knowledge/adr/`](knowledge/adr/) —
   они фиксируют **принятые** решения, которые ЗАПРЕЩЕНО переизобретать.
5. **Только после шагов 1-4** — идти в `codegraph_explore` / `grep`
   по коду.

### Failure-stop правила

- Если grep по `knowledge/` **не дал результата** — зафиксировать
  в `spec.md` явно: «Searched: `<queries>` → `<files checked>` →
  no relevant docs».
- Если релевантное содержимое найдено, но проигнорировано —
  спека считается сломанной и **MUST быть возвращена на
  `/speckit.clarify`** для переработки.
- Любой инструмент, предлагающий альтернативу (Sonar/CodeQL/etc.)
  **НЕ ЗАМЕНЯЕТ** Knowledge-first; это дополнительный слой.

### Синхронизация с другими правилами

- Это MUST-предшественник для Constitution Principle VI (FR-009 —
  per-feature документ).
- Это MUST-предшественник для `tools/spec-knowledge-preflight.sh`,
  который enforce'ит шаги 1-4 перед `tools/specify-bootstrap.sh`.
- В шаблоне `spec.md` (см. `.specify/templates/spec-template.md`)
  секция «Knowledge References» — **MANDATORY**.

## Knowledge SSoT CI / pre-commit

| Проверка | Что | Где |
|----------|-----|-----|
| `tools/check-ssot-impact.py` | SSoT impact surface: изменения в коде (по `.ssot-map.yml`) требуют синхронного обновления `knowledge/`. Если правил нет в маппинге — no-op. | **CI** (strict) |
| `tools/check-knowledge-structure.sh` | 9 структурных проверок (директории, шаблоны, 9 доменов, ≥1 ADR, cross-link в README, frontmatter) | **CI** |
| `tools/check-knowledge-cross-links.sh` | cross-links (`../X.md` + `related:`), 243+ проверок | **CI** |
| `tools/lint-knowledge.py` | Эмодзи (запрещены), mandatory headers, structural integrity L2 → L1 | **CI** |

Локальные правки Knowledge → запустить все четыре перед commit.

### `.ssot-map.yml` — карта обязательных обновлений

Файл `.ssot-map.yml` в корне проекта определяет, какие изменения в коде
требуют синхронного обновления `knowledge/`. Используется
`tools/check-ssot-impact.py` как CI-gate.

**Формат**:

```yaml
- code: "karaoke-app/**/model/SiteUser.kt"   # glob паттерн кода
  requires: "knowledge/domains/identity/components/dictionaries.md"  # путь в knowledge/
  reason: "SiteUser AR — UserRole, canSelfAssign и другие поля должны быть отражены в dictionaries.md"
```

**Правила**:

- Если `.ssot-map.yml` пуст или не существует — check no-op (всё OK).
- Если для изменённого файла в коде есть правило — должно быть
  соответствующее изменение в `knowledge/<requires>`.
- Если правила нет — файл пропускается (advisory).

**Когда добавлять новое правило**: при создании нового AR, изменении
бизнес-инварианта, изменении API-контракта. **Не** добавлять для каждой
правки — маппинг должен быть **стабильным** и описывать только SSoT-critical
изменения.

## Где смотреть логи прода

- **`docs/ops/log-correlation.md`** — карта логов прода, команды `docker logs`/`ssh`, grep-маркеры (`infra.prod.ping`/`infra.prod.db`/`LOG:  duration:`), сценарии диагностики. Создан в [specs/288-prod-diagnostics-logging](../specs/288-prod-diagnostics-logging/spec.md) (FR-019).
- Контракт WARN/INFO для `infra.prod.*`: [contracts/log-format.md](../specs/288-prod-diagnostics-logging/contracts/log-format.md).

## Иерархия документации и AI-агенты

Иерархия: `knowledge/` → `constitution.md` → `AGENTS.md` → `CONTRIBUTING.md` → `DEVELOPMENT.md` → `specs/NNN-*/spec.md` → `archive/`.
При расхождении приоритет у файла с меньшим номером (полная таблица в `docs/architecture-notes.md`).
opencode (primary) → этот файл (✅ в гите). Claude Code / Cursor / Cody / Aider — локальные конфиги.
Setup новых AI: [`knowledge/public/onboarding.md`](knowledge/public/onboarding.md).

## Issue-tracker OpenProject (spec 295) — ВАЖНО (NON-NEGOTIABLE)

В начале **каждой сессии** (после чтения Knowledge): `cd /home/nsa/Karaoke && source .env.local-tracker && bash tools/tracker-poll.sh`.

### WORKFLOW (NON-NEGOTIABLE при наличии Issue ID)

| Шаг | Команда | Когда | Кто |
|---|---|---|---|
| 1. **Claim** | `bash tools/tracker.sh claim-issue <NNN>` | **ПЕРЕД первой строкой кода спеки**. Переводит `New` → `In progress`, assignee=ai-agent. | Agent |
| 2. **Add comment с отчётом** | `bash tools/tracker.sh add-comment <NNN> --file specs/<NNN>-<slug>/report.md` | **После merge, ПЕРЕД `mark-review`**. Файл `report.md` — REQUIRED артефакт governance'а. | Agent |
| 3. **Mark review** | `bash tools/tracker.sh mark-review <NNN>` | После публикации `add-comment`. Переводит `In progress` → `In review`. | Agent |
| 4. **Close** | `bash tools/tracker.sh close-issue <NNN>` | После ревью владельцем. (Опционально: владелец закрывает сам.) | Agent или Owner |

### Compliance

- Спека НЕ ДОЛЖНА переходить в `/speckit.plan` без заполненной секции
  `## OpenProject Tracking` (см. `.specify/templates/spec-template.md`).
- CI gate `tools/check-spec-issue-link.py` валидирует наличие секции и
  обязательных полей для каждой modern-спеки (с `## Knowledge References`).
  Pre-Phase-002 спеки — grandfathered.
- **Governance failure (Pass 349)**: OpenProject #69 был обработан через
  `/speckit-full 69` БЕЗ выполнения workflow — отчёт опубликован задним
  числом. Это привело к amendment в спеке #349.

Docs: [`docs/tracker-setup.md`](docs/tracker-setup.md), [`knowledge/adr/0008-tracker-openproject-migration.md`](knowledge/adr/0008-tracker-openproject-migration.md).

## Ограничения агента (NON-NEGOTIABLE)

**Запрещено:** пересобирать `karaoke-app` (исключения см. ниже), деплой без согласия, редактировать файлы на сервере, коммитить секреты (`deploy/.env`, `*.key`, `*.pem` — `git ls-files | grep -iE '\.env$|\.key$|\.pem$'` пусто), образы `nginx:alpine`/`node:latest`/JDK вместо JRE. **Разрешено:** править код, `gradle clean bootJar`, `npm run dev/build`, локальные контейнеры через `deploy/do.sh`. **Обновление Knowledge (FR-014)**: при изменении bounded context или C4 уровня — обновить соответствующий файл в `knowledge/` в том же PR.

### Машинно-специфичные исключения (Pass 282)

#### `nsa-i9` / `nsa` (текущая)
- ✅ `karaoke-app` пересобирать без явного согласия. ❌ Контейнер `karaoke-app` перезапускать только по согласию.
- ✅ Править любой код, пересобирать `karaoke-web`/`webvue3`/`karaoke-public`.
- ❌ Деплой на прод, правка файлов на сервере, `deploy/do.env` — только по согласию.
- **Новое исключение** → подсекция + semver bump `AGENTS.md` + `docs/architecture-notes.md`.

## Git — CI-gate для master (NON-NEGOTIABLE)

```bash
N=$(./tools/reserve-branch-number.sh my-slug)
git checkout -b "${N}-my-slug" master && # правки ...
git push -u origin "${N}-my-slug" && gh pr create --base master
gh pr checks && gh pr merge --merge   # БЕЗ --delete-branch
```

Прямые коммиты в `master` ЗАПРЕЩЕНЫ. Lifecycle: ветка живёт после мёрджа.

## Сборка / деплой / тесты

- **Сборка**: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew clean karaoke-app:bootJar karaoke-web:bootJar --parallel`.
- **Деплой**: `deploy/deploy_web.sh`, `deploy/deploy_public.sh`, `cd deploy && bash do.sh build_start_public`.
- **Тесты**: в CI нет; `karaoke-app/src/test` — `@Disabled`. Проверка — пользователем.

### Gradle: запуск с `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle`

> **NON-NEGOTIABLE** (см. полную версию в `docs/architecture-notes.md` или в git history `livedocs/architecture/dsh-sandbox-conventions.md`):
> все `./gradlew ...` команды должны идти с `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle`
> (папка `.gradle` ВНУТРИ проекта). Без этого wrapper пишет в read-only `/home/nsa/.gradle/wrapper/dists/...`.

### Обязательная проверка после ЛЮБОГО изменения кода (NON-NEGOTIABLE)

> Pass 239 + 245: правки без локальной пересборки ломали прод. **Vite-build ≠ Docker-образ**.

**После ЛЮБОГО изменения ОБЯЗАТЕЛЬНО** (в этом порядке, **все gradle-команды с `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle`**):

1. Backend compile: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`
2. Линтеры: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:ktlintCheck` + `cd webvue3 && npm run lint` + `cd karaoke-public && npm run lint`
3. Backend bootJar: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:bootJar --parallel` (на `nsa-i9` — также `:karaoke-app:bootJar`)
4. Frontend Vite: `npm run build && npm run format:check` в `webvue3/` и `karaoke-public/`
5. Docker-образы: `cd deploy && bash do.sh build_webvue3`; если менялся `karaoke-public` — `bash do.sh build_public`

Только после всех 5 шагов OK — сообщать «готово к деплою». **НЕ ПРОПУСКАТЬ** даже для «очевидных» правок.

## Как обновлять этот файл

Правки governance — в ветке `0XX-agents-md-update`, semver bump. **НЕ дублировать** детали.