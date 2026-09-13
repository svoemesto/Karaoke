# AGENTS.md — инструкции для агентов

> **Версия**: 2.7.0 | **Last updated**: 2026-09-13 (Pass 375).
>
> **Изменения 2.7.0** (см. PR #378 — governance-frontend-build-transitive):
> - Добавлена секция «Frontend build: `npm run` для webvue3 и karaoke-public (Pass 375)»:
>   правила применять ТРАНЗИТИВНО — agent должен запускать `npm run lint`/`build`/`format:check`
>   из правильного каталога (`cd webvue3` или `cd karaoke-public`), иначе результат
>   некорректный (node_modules ищется в текущей директории).
> - Прецедент: правила о фронтенд-сборке были **разбросаны по 30+ файлам**
>   (AGENTS.md, CLAUDE.md, knowledge/code-style.md, tasks.md для каждой спеки)
>   и **не machine-readable** — не было guard-скрипта, который бы ловил
>   запуск `npm run lint` без `cd webvue3`/`cd karaoke-public`.
> - Добавлен guard-скрипт `tools/check-frontend-build.sh` для pre-commit и CI.
> - Правило: **`cd <frontend-dir> && npm run <command>`** — единственный путь.
>   **`npm run lint`** из корня проекта — НЕ ДОЛЖЕН использоваться.
>
> **Изменения 2.6.0** (см. PR #377 — governance-container-restart-transitive):
> - Добавлена секция «Перезапуск контейнеров через `deploy/do.sh`» (Pass 374, OP #83 follow-up):
>   правило применять ТРАНЗИТИВНО для **ВСЕХ** способов перезапуска —
>   `deploy/do.sh start_*`, `restart_*`, прямые `docker restart`/`docker stop`+`start`.
> - Прецедент: правила о перезапуске были **разбросаны по 3 местам** (AGENTS.md § Ограничения,
>   AGENTS.md § Диагностика, deploy/do.sh help) и **не machine-readable** —
>   не было guard-скрипта, блокирующего прямой `docker restart karaoke-app` без согласия.
> - Добавлен guard-скрипт `tools/check-container-restart.sh` для pre-commit и CI.
> - Правило: **`deploy/do.sh start_*`** — единственный путь для агента.
>   **`docker restart <container>`** НЕ ДОЛЖЕН использоваться напрямую.
>
> **Изменения 2.5.0** (см. PR #376 — governance-docker-config-transitive):
> - Добавлена секция «Docker build: `DOCKER_CONFIG`» (Pass 373, OP #83 follow-up):
>   правило `DOCKER_CONFIG=/home/nsa/Karaoke/.docker` для всех `docker build` /
>   `bash deploy/do.sh build_*` (mirror-аналог Pass 372 для gradle).
> - Прецедент: buildx пишет в `~/.docker/buildx/activity/` (read-only в DSH-sandbox)
>   → read-only error. Решение — проектная `/home/nsa/Karaoke/.docker/` writable.
> - Добавлен guard-скрипт `tools/check-docker-config.sh` для pre-commit и CI.
>
> **Изменения 2.4.0** (см. PR #374 — governance-gradle-user-home-transitive):
> - Дополнена секция «Gradle: запуск с `GRADLE_USER_HOME`» правилом **транзитивности**:
>   правило применяется не только к прямому `./gradlew`, но и к скриптам, которые
>   его вызывают (`pre-commit`, `deploy/do.sh`, IDE-runner). Прецедент (Pass 372, OP #83).
> - Добавлен guard-скрипт `tools/check-gradle-user-home.sh` для pre-commit и CI.
> - `.pre-commit-config.yaml`: ktlint-hook теперь передаёт `GRADLE_USER_HOME` явно
>   (`bash -c 'GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ...'`).
> - `deploy/do.sh`: устанавливает `GRADLE_USER_HOME` и `JAVA_HOME` в начале, если не заданы.
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
>
> **Изменения 2.3.0** (Pass 350, см. PR #350 — spec-hooks-auto-tracker):
> - Добавлены auto-hooks (Pass 350) для OpenProject Tracker workflow:
>   `tools/tracker-bootstrap.sh` (auto-claim в before_specify) +
>   `tools/tracker-implement-done.sh` (auto-comment + mark-review
>   в after_implement). Зарегистрированы в `.specify/extensions.yml`
>   как optional hooks (см. AGENTS.md § Auto-hooks).
> - Section «Issue-tracker OpenProject» дополнена подсекцией
>   «Auto-hooks (Pass 350)» с описанием hooks.
> - Снижает риск повторения Pass 349 failure (OpenProject #69
>   обработан без claim/add-comment/mark-review).

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

## Диагностика на локальной машине (NON-NEGOTIABLE, Pass 358)

> **Прецедент** (Pass 358, issue #70 follow-up): при тестировании фичи в локальных
> контейнерах я рассуждал о данных в БД, не заглянув в логи `karaoke-web`.
> Ошибка `PSQLException: column "skip" does not exist` была видна в логах сразу —
> я потерял несколько итераций на гипотезы про данные, которые вообще не нужны
> были, потому что лог прямо указывал на причину.

### Правило

**При отладке/тестировании фичи на этой машине (nsa-i9) агент MUST самостоятельно
смотреть логи запущенных контейнеров**, особенно:

1. **При первом сообщении пользователя об ошибке** (HTTP 5xx, пустой ответ,
   « не работает », « странное поведение ») — **до** любых гипотез про данные
   или код, агент MUST первым делом запустить `docker logs` соответствующего
   контейнера и прочитать stack trace / SQL exception / WARN.
2. **После каждой пересборки и перезапуска контейнера** — MUST проверить, что
   новый контейнер стартовал без ошибок (`docker logs --tail 50 <container>`),
   прежде чем сообщать «готово к тестированию».
3. **Перед сообщением «минимальный фикс запушен»** — MUST запустить логи
   контейнера, в который был сделан фикс, и убедиться, что новая ошибка
   не появляется.

### Команды

Контейнеры на этой машине (см. `deploy/do.sh`):
- `karaoke-web` — backend для публичного сайта и админки
- `karaoke-public` — публичный SPA (Vue 3)
- `karaoke-app` — engine (admin-only)
- `webvue3` — админка (Vue 3)
- `nginx` — reverse proxy
- `postgres` / `karaoke-db` — БД
- `minio` — объектное хранилище

Базовые команды:

```bash
# Список запущенных контейнеров
docker ps --format '{{.Names}}\t{{.Status}}'

# Последние N строк логов (без -f)
docker logs --tail 100 <container_name>

# Логи в реальном времени (осторожно — большой поток)
docker logs -f --tail 50 <container_name>

# С момента старта контейнера
docker logs --since 5m <container_name>

# Только ошибки и WARN (фильтр)
docker logs --tail 200 <container_name> 2>&1 | grep -iE 'error|exception|warn|fatal'
```

Для более глубокой диагностики см. также `docs/ops/log-correlation.md`.

### Failure-stop

Если при проверке логов агент видит ошибку, которую раньше не видел — MUST
немедленно остановиться и сообщить пользователю, а не «пройти мимо» в надежде,
что это не связано с текущей задачей.

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

### Auto-hooks (Pass 350)

**`before_specify` → `tools/tracker-bootstrap.sh`** (опциональный, см. `.specify/extensions.yml`):
- Запускается как часть `tools/specify-bootstrap.sh` ПОСЛЕ резервирования NNN.
- Сканирует все аргументы (`$ARGUMENTS`, slug, description) на OpenProject ID
  (regex: `#NN`, `№NN`, `задача NN`, `task NN`, `OP #NN`, `OpenProject NN`).
- Если найден — вызывает `tracker.sh claim-issue <NN>` (idempotent).
- Если не найден — no-op (просто пропускает claim).

**`after_implement` → `tools/tracker-implement-done.sh`** (опциональный):
- Запускается агентом ПОСЛЕ merge PR (или владельцем вручную).
- Определяет Issue ID через: явный аргумент → автодетект через branch →
  auto-generates stub `report.md` из git log.
- Вызывает `add-comment + mark-review` (idempotent — повторные вызовы
  безопасны, comment может дублироваться).

Эти хуки ПОЛНОСТЬЮ OPTIONAL — агент может сделать шаги вручную если hook
не сработал (например, runtime не поддерживает extensions.yml). Главное —
workflow выполнен ДО merge и ДО `mark-review`.

### Compliance

- Спека НЕ ДОЛЖНА переходить в `/speckit.plan` без заполненной секции
  `## OpenProject Tracking` (см. `.specify/templates/spec-template.md`).
- CI gate `tools/check-spec-issue-link.py` валидирует наличие секции и
  обязательных полей для каждой modern-спеки (с `## Knowledge References`).
  Pre-Phase-002 спеки — grandfathered.
- **Governance failure (Pass 349)**: OpenProject #69 был обработан через
  `/speckit-full 69` БЕЗ выполнения workflow — отчёт опубликован задним
  числом. Это привело к amendment в спеке #349.
- **Автоматизация (Pass 350)**: добавлены хуки `tracker-bootstrap.sh`
  (auto-claim в before_specify) и `tracker-implement-done.sh` (auto-comment
  + mark-review в after_implement). Заявлено в `.specify/extensions.yml`
  как optional — не блокирует workflow при failure, но снижает риск
  повторения Pass 349 failure.

Docs: [`docs/tracker-setup.md`](docs/tracker-setup.md), [`knowledge/adr/0008-tracker-openproject-migration.md`](knowledge/adr/0008-tracker-openproject-migration.md).

## Ограничения агента (NON-NEGOTIABLE)

**Запрещено:** пересобирать `karaoke-app` (исключения см. ниже), деплой без согласия, редактировать файлы на сервере, коммитить секреты (`deploy/.env`, `*.key`, `*.pem` — `git ls-files | grep -iE '\.env$|\.key$|\.pem$'` пусто), образы `nginx:alpine`/`node:latest`/JDK вместо JRE. **Разрешено:** править код, `gradle clean bootJar`, `npm run dev/build`, локальные контейнеры через `deploy/do.sh`. **Обновление Knowledge (FR-014)**: при изменении bounded context или C4 уровня — обновить соответствующий файл в `knowledge/` в том же PR.

### Машинно-специфичные исключения (Pass 282)

#### `nsa-i9` / `nsa` (текущая)
- ✅ `karaoke-app` пересобирать без явного согласия. ❌ Контейнер `karaoke-app` перезапускать только по согласию.
- ✅ Править любой код, пересобирать `karaoke-web`/`webvue3`/`karaoke-public`.
- ❌ Деплой на прод, правка файлов на сервере, `deploy/do.env` — только по согласию.
- **Новое исключение** → подсекция + semver bump `AGENTS.md` + `docs/architecture-notes.md`.

## Git — CI-gate для master (NON-NEGOTIABLE) ⛔

> **⛔ ЗАПРЕЩЕНО** делать `git commit` напрямую на `master` или пушить в
> `master` через `git push`. ТОЛЬКО через feature-ветку + PR + CI.
>
> **Прецедент**: 2026-09-09, спека #354 (Pass 353) — agent закоммитил
> отчёты напрямую в `master` после мержа PR #452, owner поймал.
> Чтобы **100% заблокировать** подобное в будущем, добавлены 3 уровня защиты.
>
> **Enforcement layers (in order)**:
> 1. **GitHub branch protection** (server-side, primary defense) —
>    `master` requires pull request + status checks + **admin-enforced**
>    (no bypass). `git push origin master` → 403 rejected.
> 2. **Pre-commit hook** (client-side) — `tools/git-hooks/pre-commit-block-master.sh`
>    blocks `git commit` on `master` locally. Регистрируется через
>    `.pre-commit-config.yaml` (`block-master-commit` hook).
> 3. **CI lint step** (server-side, final safety net) —
>    `.github/workflows/lint.yml` step "No direct commits to master"
>    detects any direct commit in last 24h and fails. Защита от bypass
>    до того, как branch protection была включена, или любого future bypass.

```bash
N=$(./tools/reserve-branch-number.sh my-slug)
git checkout -b "${N}-my-slug" master && # правки ...
git push -u origin "${N}-my-slug" && gh pr create --base master
gh pr checks && gh pr merge --merge   # БЕЗ --delete-branch
```

Прямые коммиты в `master` ЗАПРЕЩЕНЫ (см. enforcement layers выше). Lifecycle: ветка живёт после мёрджа.

## Сборка / деплой / тесты

- **Сборка**: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew clean karaoke-app:bootJar karaoke-web:bootJar --parallel`.
- **Деплой**: `deploy/deploy_web.sh`, `deploy/deploy_public.sh`, `cd deploy && bash do.sh build_start_public`.
- **Тесты**: в CI нет; `karaoke-app/src/test` — `@Disabled`. Проверка — пользователем.

### Gradle: запуск с `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle`

> **NON-NEGOTIABLE** (см. полную версию в `docs/architecture-notes.md` или в git history `livedocs/architecture/dsh-sandbox-conventions.md`):
> все `./gradlew ...` команды должны идти с `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle`
> (папка `.gradle` ВНУТРИ проекта). Без этого wrapper пишет в read-only `/home/nsa/.gradle/wrapper/dists/...`.

#### Правило применяется ТРАНЗИТИВНО (Pass 372)

Прецедент (Pass 372, OP #83): агент помнит правило для прямого вызова `./gradlew`, но
**забывает применить его к скриптам, которые сами вызывают `./gradlew`**:

- `pre-commit run` → внутри хука `ktlint` запускается `./gradlew ktlintCheck`
  (см. `.pre-commit-config.yaml:20`) — **падает** без `GRADLE_USER_HOME`.
- `bash deploy/do.sh build_app` → внутри скрипта вызывается `${GRADLE}` (т.е. `./gradlew`)
  (см. `deploy/do.sh:13, 64, 118, 138`) — **падает** без `GRADLE_USER_HOME`.
- IDE-runner (Gradle Task Runner в IntelliJ IDEA / VSCode) — если запускается
  из shell'а без переменной — **падает**.

**Перед ЛЮБЫМ вызовом** `./gradlew` или скрипта, который его вызывает, проверь:

```bash
# Если НЕ задан — ОБЯЗАТЕЛЬНО установить.
export GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle
export JAVA_HOME=/usr/lib/jvm/jdk-18
```

**НЕПРАВИЛЬНО** (Pass 372 failure):
```bash
./gradlew :karaoke-app:compileKotlin              # ❌ read-only FS
pre-commit run --all-files                        # ❌ хук ktlint упадёт
bash deploy/do.sh build_app                       # ❌ gradle внутри упадёт
```

**ПРАВИЛЬНО**:
```bash
export GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle JAVA_HOME=/usr/lib/jvm/jdk-18
./gradlew :karaoke-app:compileKotlin              # ✅
pre-commit run --all-files                        # ✅ ktlint-хук работает
bash deploy/do.sh build_app                       # ✅ gradle внутри работает
```

**Enforcement**: `tools/check-gradle-user-home.sh` — guard-скрипт для pre-commit
и CI (Pass 372). Проверяет, что `$GRADLE_USER_HOME` либо `=/home/nsa/Karaoke/.gradle`,
либо не пуста и проектная `.gradle/` writable.

### Docker build: `DOCKER_CONFIG=/home/nsa/Karaoke/.docker` (Pass 373)

> **NON-NEGOTIABLE** (Pass 373, OP #83 follow-up): docker build в DSH-sandbox
> **падает** с `failed to update builder last activity time: open
> /home/nsa/.docker/buildx/activity/.tmp-default...: read-only file system`,
> если используется системная `~/.docker` директория.

**Решение**: docker-cli (включая `buildx` под капотом) при работе с buildx
записывает временные файлы в `$DOCKER_CONFIG/buildx/activity/`. В DSH-sandbox
`~/.docker` read-only, поэтому buildx падает. Но проектная
`/home/nsa/Karaoke/.docker/` (внутри проекта) — **writable**.

Прецедент (Pass 373, OP #83 follow-up): агент потратил несколько итераций,
пробуя разные флаги (`DOCKER_BUILDKIT=0`, `--load`, `BUILDX_CACHE_DIR=/tmp/buildx-cache`),
прежде чем нашёл правильный паттерн — `DOCKER_CONFIG=/home/nsa/Karaoke/.docker`.

**Перед ЛЮБЫМ** `docker build` / `docker image build` / `bash deploy/do.sh build_*`
проверь:

```bash
# Если НЕ задан — ОБЯЗАТЕЛЬНО установить.
export DOCKER_CONFIG=/home/nsa/Karaoke/.docker
```

**НЕПРАВИЛЬНО** (Pass 373 failure):
```bash
docker image build -t foo -f Dockerfile .   # ❌ ~/.docker/buildx/activity read-only
DOCKER_BUILDKIT=0 docker build ...           # ❌ legacy builder не поможет (mount=type=cache нужен BuildKit)
BUILDX_CACHE_DIR=/tmp docker buildx build   # ❌ buildx всё равно пишет в ~/.docker/buildx/activity
bash deploy/do.sh build_app                  # ❌ gradle OK (Pass 372 фикс), но docker внутри упадёт
```

**ПРАВИЛЬНО**:
```bash
export DOCKER_CONFIG=/home/nsa/Karaoke/.docker
docker image build -t foo -f Dockerfile .                       # ✅ legacy builder
docker buildx build --load -t foo -f Dockerfile .               # ✅ buildx с локальным config
bash deploy/do.sh build_app                                     # ✅ gradle + docker оба работают
```

**Важно**:
- `DOCKER_CONFIG` указывает docker-cli где искать конфиги и где хранить buildx state.
- Legacy `docker image build` тоже использует `$DOCKER_CONFIG` (для `config.json`,
  `token_seed`, `buildx/`).
- В DSH-sandbox `~/.docker` read-only — **НИКОГДА** не работает.
- В **продакшене** (DSH **нет**) — дефолт `~/.docker` writable, переменная не нужна.

**Enforcement**: `tools/check-docker-config.sh` — guard-скрипт для pre-commit
и CI (Pass 373). Проверяет, что `$DOCKER_CONFIG` либо `=/home/nsa/Karaoke/.docker`,
либо проектная `.docker/` writable.

### Перезапуск контейнеров через `deploy/do.sh` (Pass 374)

> **NON-NEGOTIABLE** (Pass 374, OP #83 follow-up): правила о перезапуске контейнеров
> были **разбросаны по 3 местам** (AGENTS.md § «Ограничения агента»,
> AGENTS.md § «Диагностика на локальной машине», `deploy/do.sh help`) и
> **не machine-readable**. Агент мог случайно использовать прямые
> `docker restart` / `docker stop` + `docker start` для контейнеров, которые
> требуют согласия (`karaoke-app`).

**Решение**: **единственный путь** для агента перезапустить контейнер —
через `deploy/do.sh start_<container>` или `restart_<container>`. Эти команды
выполняют `docker-compose` корректно (с зависимостями, в правильном порядке)
и **уважают машинно-специфичные исключения** из § «Ограничения агента».

#### Доступные команды

| Контейнер | Scoped start | Scoped restart | Build + start | Когда можно агенту |
|---|---|---|---|---|
| `karaoke-db` | `bash deploy/do.sh start_db` | `bash deploy/do.sh restart_db` | `bash deploy/do.sh build_start_db` | ✅ Без согласия |
| `karaoke-web` | `bash deploy/do.sh start_web` | `bash deploy/do.sh restart_web` | `bash deploy/do.sh build_start_web` | ✅ Без согласия |
| `karaoke-webvue` | `bash deploy/do.sh start_webvue` | `bash deploy/do.sh restart_webvue` | `bash deploy/do.sh build_start_webvue` | ✅ Без согласия |
| `karaoke-webvue3` | `bash deploy/do.sh start_webvue3` | `bash deploy/do.sh restart_webvue3` | `bash deploy/do.sh build_start_webvue3` | ✅ Без согласия |
| `karaoke-public` | `bash deploy/do.sh start_public` | `bash deploy/do.sh restart_public` | `bash deploy/do.sh build_start_public` | ✅ Без согласия |
| `karaoke-app` | `bash deploy/do.sh start_app` | `bash deploy/do.sh restart_app` | `bash deploy/do.sh build_start_app` | ❌ Только по согласию (`nsa-i9`/`nsa`, Pass 282) |
| `minio` / `nginx` | `bash deploy/do.sh start_minio` | `bash deploy/do.sh restart_minio` | `bash deploy/do.sh build_start_minio` | ⚠️ По согласованию |

Полный список: `bash deploy/do.sh help` (для агента **только чтение**, не запуск).

**НЕПРАВИЛЬНО** (Pass 374 failure):
```bash
docker restart karaoke-web                # ❌ минует do.sh, теряет зависимости (karaoke-minio-proxy)
docker restart karaoke-app                # ❌ ЗАПРЕЩЕНО без явного согласия (Pass 282)
docker stop karaoke-web && docker start karaoke-web   # ❌ то же
docker-compose -f deploy/docker-compose-web.yml restart   # ❌ не через do.sh
```

**ПРАВИЛЬНО**:
```bash
export GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle
export DOCKER_CONFIG=/home/nsa/Karaoke/.docker
bash deploy/do.sh start_web                # ✅ scoped: только karaoke-web (без сборки)
bash deploy/do.sh restart_web              # ✅ scoped restart
bash deploy/do.sh build_start_web          # ✅ собрать И запустить

# Только для karaoke-app — с явным согласием пользователя:
# "Перезапусти karaoke-app"
bash deploy/do.sh start_app                # ✅ после явного согласия
```

**Перед ЛЮБЫМ перезапуском** агент MUST:
1. Проверить, что у него есть разрешение для конкретного контейнера (таблица выше).
2. Установить `GRADLE_USER_HOME` и `DOCKER_CONFIG` (Pass 372, Pass 373).
3. Использовать **ТОЛЬКО** `bash deploy/do.sh start_*` / `restart_*`.
4. После перезапуска — проверить логи (`docker logs --tail 50 <container>`) —
   см. § «Диагностика на локальной машине», правило 2.

**Enforcement**: `tools/check-container-restart.sh` — guard-скрипт для pre-commit
и CI (Pass 374). Проверяет, что **новые коммиты** не содержат запрещённых
прямых вызовов `docker restart <container>` или `docker stop <container>`.

### Frontend build: `cd <frontend-dir> && npm run <cmd>` (Pass 375)

> **NON-NEGOTIABLE** (Pass 375, OP #83 follow-up): правила о фронтенд-сборке
> были **разбросаны по 30+ файлам** (AGENTS.md, CLAUDE.md, knowledge/code-style.md,
> tasks.md для каждой спеки) и **не machine-readable**. Не было guard-скрипта,
> который бы ловил запуск `npm run lint` без `cd webvue3`/`cd karaoke-public`.

**Решение**: `npm` ищет `node_modules` и `package.json` в **текущей директории**.
Запуск `npm run lint` из корня проекта найдёт корень `package.json` (если он есть)
или упадёт. **Правильный паттерн** — `cd <frontend-dir> && npm run <cmd>`.

Прецедент (Pass 375, OP #83 follow-up): на этой машине есть **только два**
фронтенд-проекта с собственным `node_modules`:
- `webvue3/` — админка (Vue 3 + bootstrap-vue-next).
- `karaoke-public/` — публичный SPA (Vue 3 + Bootstrap 5).
- В **корне проекта** `package.json` **нет** (есть только в подкаталогах).

#### Доступные команды

| Frontend | Команды lint | Команды build | Команды format | Docker (Pass 245) |
|---|---|---|---|---|
| `webvue3/` | `cd webvue3 && npm run lint` | `cd webvue3 && npm run build` | `cd webvue3 && npm run format:check` | `cd deploy && bash do.sh build_webvue3` |
| `karaoke-public/` | `cd karaoke-public && npm run lint` | `cd karaoke-public && npm run build` | `cd karaoke-public && npm run format:check` | `cd deploy && bash do.sh build_public` |

**НЕПРАВИЛЬНО** (Pass 375 failure):
```bash
npm run lint                       # ❌ нет package.json в корне — node_modules не найден
npm run build                      # ❌ то же
npm run lint:check                 # ❌ то же (или baseline-проверка работает на чужих файлах)
./gradlew :webvue3:test           # ❌ это не gradle-проект
```

**ПРАВИЛЬНО**:
```bash
cd webvue3 && npm run lint        # ✅ node_modules в webvue3/
cd webvue3 && npm run build       # ✅ vite build (8.03s, exit 0)
cd webvue3 && npm run format:check # ✅ prettier

cd karaoke-public && npm run lint  # ✅
cd karaoke-public && npm run build # ✅ vite build (3.98s, exit 0)
cd karaoke-public && npm run format:check  # ✅

# После — Docker (Pass 245: Vite-build ≠ Docker multi-stage)
cd deploy && bash do.sh build_webvue3     # ✅
cd deploy && bash do.sh build_public      # ✅
```

#### Правила

1. **`cd <frontend-dir>` обязателен** перед каждым `npm run <cmd>` —
   иначе `npm` не найдёт `package.json`/`node_modules` в текущей директории.
2. **`package.json` есть ТОЛЬКО в `webvue3/` и `karaoke-public/`** — не в корне.
3. **`node_modules` — локальный** (per-frontend, не общий). После `git pull` —
   может потребоваться `npm install`.
4. **Vite-build ≠ Docker-образ** (Pass 245) — после `npm run build` обязательно
   ещё `cd deploy && bash do.sh build_webvue3` / `build_public`.
5. **ESLint baseline**: `webvue3/.eslint-baseline.json` и
   `karaoke-public/.eslint-baseline.json` фиксируют legacy-нарушения.
   Новые нарушения **не должны** расти — иначе упасть CI.

#### Перед ЛЮБЫМ** `npm run` агент MUST:

1. Определить, **какой** frontend менялся: `webvue3/` или `karaoke-public/`.
2. `cd <frontend-dir>` перед командой (не выполнять из корня).
3. Запустить **lint + build + format:check** в этом каталоге.
4. Если ошибки — исправить, **НЕ** через `--no-verify` или baseline.
5. После merge — `cd deploy && bash do.sh build_<frontend>` для Docker multi-stage.

**Enforcement**: `tools/check-frontend-build.sh` — guard-скрипт для pre-commit
и CI (Pass 375). Проверяет, что **новые коммиты** в shell-файлах не содержат
запрещённых паттернов (`npm run lint` без `cd webvue3`/`cd karaoke-public`).

#### Scope rule (Pass 375)

- `cd webvue3 && npm run <cmd>` — ✅ правильно.
- `cd karaoke-public && npm run <cmd>` — ✅ правильно.
- `(cd webvue3 && npm run lint)` — ✅ правильно (subshell).
- `bash -c 'cd webvue3 && npm run lint'` — ✅ правильно.
- `npm run lint` из корня — ❌ **запрещено**.
- `npx eslint webvue3/src/...` из корня — ⚠️ допустимо (npx находит бинарь),
  но **не рекомендуется** (нет baseline-проверки).

Полная таблица ограничений: см. `AGENTS.md § Ограничения агента`.

#### Scope rule (Pass 374)

- `build_start_app` — агент НЕ ДОЛЖЕН использовать без явного согласия.
  `build_app` (только сборка) — ✅ разрешено.
- `start_app` / `restart_app` — агент НЕ ДОЛЖЕН без явного согласия.
- **Scoped-команды** (например, `bash deploy/do.sh build_app && start_app` — где `&&`
  между двумя командами) — это **не то же самое** что `build_start_app`. Если
  `&&` стоит между `build_app` и `start_app` — последняя тоже требует согласия.

Полная таблица ограничений: см. `AGENTS.md § Ограничения агента →
Машинно-специфичные исключения → nsa-i9 / nsa`.

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