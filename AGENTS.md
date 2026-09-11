# AGENTS.md — инструкции для агентов

> **Версия**: 2.4.0 | **Last updated**: 2026-09-11 (Pass 367).
>
> **Изменения 2.4.0** (Pass 367, см. PR — sandbox-recipes):
> - Добавлен раздел «Sandbox DSH: границы и fallback-пути» — формализует workspace-аналоги для всех известных read-only блокировок (`~/.gradle/wrapper/dists/`, `~/.docker/buildx/activity/`, `~/.npm/`, `~/.cache/`).
> - Введён `tools/check-sandbox-ready.sh` — pre-flight скрипт, который создаёт workspace-аналоги и печатает готовые к use команды. При `source` экспортирует `GRADLE_USER_HOME`, `DOCKER_CONFIG` и функцию-обёртку `docker()`.
> - Прецедент: spec #361, #363, #302 (Pass 367) — агенты помечали `docker build` как DEFERRED-sandbox и ждали владельца. Теперь unblocked: `docker --config=/home/nsa/Karaoke/.docker` обходит блокировку buildx activity.
> - Формализован DEFERRED-формат для tasks.md: `TNNN [VERIFY] DEFERRED-sandbox: <операция>. Команда для пользователя: <cmd>. Ожидаемый результат: <exit 0 / artefact>.`
> - В `.gitignore` добавлен `.docker/` (workspace-аналог, не должен коммититься).
> - ADR: [`knowledge/adr/local-0010-sandbox-recipes.md`](knowledge/adr/local-0010-sandbox-recipes.md).
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

## Чтение логов контейнеров (Pass 367 follow-up, 2026-09-11)

> ⚠️ **Безусловно разрешено без явного согласия** (per owner, 2026-09-11):
> **чтение логов контейнеров** — это рид-онли операция, не изменяет состояние
> ни в workspace, ни на сервере. Агент MUST делать это самостоятельно при любых
> сомнениях о работе системы — без вопросов «можно ли?».

### Локальные контейнеры (nsa-i9)

```bash
# Все контейнеры Karaoke
DOCKER_CONFIG=/home/nsa/Karaoke/.docker docker ps --format '{{.Names}}\t{{.Status}}'

# Последние 100 строк (без -f, без остановки)
DOCKER_CONFIG=/home/nsa/Karaoke/.docker docker logs --tail 100 <container_name>

# Реалтайм (осторожно — большой поток)
DOCKER_CONFIG=/home/nsa/Karaoke/.docker docker logs -f --tail 50 <container_name>

# С момента старта контейнера
DOCKER_CONFIG=/home/nsa/Karaoke/.docker docker logs --since 5m <container_name>

# Только errors и WARN
DOCKER_CONFIG=/home/nsa/Karaoke/.docker docker logs --tail 200 <container_name> 2>&1 | grep -iE 'error|exception|warn|fatal'
```

**Список локальных контейнеров Karaoke**:
`karaoke-app`, `karaoke-web`, `karaoke-webvue3`, `karaoke-public`, `karaoke-storage`,
`karaoke-minio-proxy`, `karaoke-telegram-proxy`, `karaoke-db`, `searxng`, `fourget`.

### Удалённые контейнеры (прод через SSH)

```bash
# Хосты (см. /etc/hosts + docs/ops/log-correlation.md)
ssh -o BatchMode=yes root@188.119.64.111 'docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Status}}"'  # nginx + MinIO proxy
ssh -o BatchMode=yes root@89.125.103.63  'docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Status}}"'  # хранилище (MinIO)
ssh -o BatchMode=yes root@79.174.95.69   'docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Status}}"'  # старый prod (опционально)

# Логи конкретного контейнера
ssh -o BatchMode=yes root@<host> 'docker logs --tail 100 <container_name>'

# Системные логи nginx
ssh -o BatchMode=yes root@188.119.64.111 'tail -100 /var/log/nginx/access.log'
ssh -o BatchMode=yes root@188.119.64.111 'tail -100 /var/log/nginx/error.log'
```

| Хост | Что на нём | Контейнеры |
|---|---|---|
| `188.119.64.111` (karaoke-prod) | nginx + MinIO proxy | `karaoke-public`, `karaoke-web`, `karaoke-db` |
| `89.125.103.63` (karaoke-storage) | MinIO (отдельно) | `karaoke-storage` |
| `79.174.95.69` (karaoke-prod-old) | старый prod (миграция) | `Connection.remote()` — см. Connection.kt |

### Когда НЕ нужно спрашивать согласия

- Прочитать `docker logs`, `docker inspect`, `docker ps`.
- Прочитать любые файлы через `ssh ... 'cat | tail | head | grep'`.
- Прочитать системные логи (`/var/log/nginx/...`, journalctl, `dmesg`).
- Прочитать состояние БД (`docker exec karaoke-db psql -c 'SELECT ...'`).
- Прочитать размер бакетов MinIO, состояние объектов.

### Когда согласие НУЖНО

Любая операция, которая **меняет** состояние: перезапуск контейнера на проде,
правка файлов, деплой, `rsync`, `scp` в обратную сторону, прямой SQL `UPDATE/DELETE/INSERT`
на прод-БД.

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

### SSH-доступ к прод-серверам (Pass 367 follow-up, 2026-09-11)

- **Разрешено без явного согласия** (per owner, 2026-09-11): **рид-онли** операции через SSH —
  чтение логов контейнеров, чтение файлов в `/var/log/`, чтение состояния docker.
- **Требует явного согласия** (по умолчанию): **любые** операции, которые **меняют** состояние на
  сервере — правка файлов, деплой, перезапуск контейнеров на проде, `rsync`, `scp` в обратную сторону.
- Распознавание: если команда не содержит `>`, `>>`, `mv`, `cp ... /prod`, `rm`, `sed -i`, `tee`,
  `docker restart|stop|rm`, `git push`, `deploy_web.sh` и т.п. — это рид-онли.
- Список prod-хостов: см. `docs/ops/log-correlation.md`. Текущие:
  - `188.119.64.111` — nginx + MinIO proxy (containers: karaoke-public, karaoke-web, karaoke-db).

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

## Sandbox DSH: границы и fallback-пути (Pass 367)

> **Цель**: дать агенту набор рецептов для самостоятельного выполнения
> build / compile / restart / lint в режиме workspace-write **без эскалации
> на `danger-full-access`**.

DSH-сессии работают в режиме `workspace-write`:

- **writable**: `/home/nsa/Karaoke/**`, `/tmp`.
- **read-only** (DSH-sandbox): `~/.gradle/wrapper/dists/`, `~/.docker/`,
  `~/.npm/`, `~/.cache/`, `~/.kotlin/`.

### Pre-flight (в начале сессии, перед первым gradle/docker/npm вызовом)

```bash
bash /home/nsa/Karaoke/tools/check-sandbox-ready.sh
```

Скрипт: probe'ит read-only границы, создаёт workspace-аналоги
(`/home/nsa/Karaoke/.gradle/`, `/home/nsa/Karaoke/.docker/`), печатает
готовые команды для copy-paste. При `source` дополнительно экспортирует
`GRADLE_USER_HOME`, `DOCKER_CONFIG` и функцию-обёртку `docker()`.

### Рецепты (использовать всегда)

| Что делаем | Команда |
|---|---|
| Gradle compile / ktlint / bootJar | `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:compileKotlin --parallel` |
| Docker build / restart / logs | `docker --config=/home/nsa/Karaoke/.docker build ...` |
| npm / vite / prettier | `cd webvue3 && npm run lint && npm run build` (cache → `node_modules/.cache`) |
| Docker ps / logs / exec (без записи в activity) | `docker ps`, `docker logs`, `docker exec` — не требуют обхода |
| psql к локальной karaoke-db | `docker exec -it karaoke-db psql -U ...` (контейнер доступен через daemon) |

### Karaoke-specific build pipeline (Pass 367 follow-up, верифицировано 2026-09-11)

Для сборки образов **через `deploy/do.sh`** (а не ручные `gradle` + `docker build`)
нужны **обе** переменные окружения — иначе `do.sh` упадёт на read-only
`~/.docker/buildx/activity/` или `~/.gradle/wrapper/dists/`:

```bash
cd /home/nsa/Karaoke/deploy
DOCKER_CONFIG=/home/nsa/Karaoke/.docker GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle \
  bash do.sh build_app           # сборка karaoke-app
DOCKER_CONFIG=/home/nsa/Karaoke/.docker GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle \
  bash do.sh build_web           # сборка karaoke-web (webvue3)
DOCKER_CONFIG=/home/nsa/Karaoke/.docker GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle \
  bash do.sh build_public        # сборка karaoke-public
DOCKER_CONFIG=/home/nsa/Karaoke/.docker GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle \
  bash do.sh build_start_app     # сборка + перезапуск (nsa-i9: перезапуск karaoke-app по согласию)
```

**Тонкости**:
- `DOCKER_CONFIG` — нативная переменная docker CLI; работает для всех вызовов
  `docker` внутри `do.sh` без необходимости передавать `--config` явно.
- `GRADLE_USER_HOME` — нужен для `gradlew clean bootJar`, который `do.sh` запускает
  первым шагом.
- После `build_*` контейнер **не перезапускается автоматически** — это отдельная
  операция `start_*` / `restart_*` (см. Pass 282 — на nsa-i9 перезапуск
  `karaoke-app` только по явному согласию).
- Проверить что новый образ собран: `DOCKER_CONFIG=/home/nsa/Karaoke/.docker docker images <repo>:<tag> --format '{{.ID}} {{.CreatedSince}}'`.
- **Проверить что в образе правильный bootJar** (Spring Boot с ENTRYPOINT `java -jar /app.jar`):
  `docker run --rm <image>` без `--entrypoint` сразу стартует Spring и падает вне compose-сети.
  Использовать `docker create + docker cp`:
  ```bash
  DOCKER_CONFIG=/home/nsa/Karaoke/.docker docker create --name tmp-<tag> <image>:<tag>
  DOCKER_CONFIG=/home/nsa/Karaoke/.docker docker cp tmp-<tag>:/app.jar /tmp/check.jar
  sha256sum /tmp/check.jar /home/nsa/Karaoke/karaoke-web/build/libs/*.jar
  DOCKER_CONFIG=/home/nsa/Karaoke/.docker docker rm tmp-<tag>
  ```
  Хеши должны совпасть. Это валидирует, что в образе именно тот jar, что собрал gradle.
- **Перезапуск контейнера после `build_*`** (на nsa-i9 — `karaoke-web` без согласия, `karaoke-app` по согласию):
  ```bash
  cd /home/nsa/Karaoke/deploy
  DOCKER_CONFIG=/home/nsa/Karaoke/.docker bash do.sh start_web     # перезапуск karaoke-web
  DOCKER_CONFIG=/home/nsa/Karaoke/.docker bash do.sh start_app     # перезапуск karaoke-app
  DOCKER_CONFIG=/home/nsa/Karaoke/.docker bash do.sh start_public  # перезапуск karaoke-public
  DOCKER_CONFIG=/home/nsa/Karaoke/.docker bash do.sh start_webvue3 # перезапуск webvue3
  ```
  Семантика `do.sh start_*`: `compose down` + `compose up -d` (полный пересоздать с новым образом).
  Warnings `version is obsolete` и `orphan containers` — **некритичны**, не блокируют рестарт.
- **Верификация что контейнер на свежем образе** после рестарта:
  ```bash
  DOCKER_CONFIG=/home/nsa/Karaoke/.docker docker inspect --format '{{.Image}}' <container_name>
  DOCKER_CONFIG=/home/nsa/Karaoke/.docker docker images --digests <repo>:<tag>
  ```
  Image ID контейнера должен совпадать с Image ID образа. Если не совпадает — контейнер
  всё ещё на старом (проверь что `build_*` действительно создал новый image, не закэшировался).

### Когда эскалировать на `danger-full-access`

Только если выполнены **все три** условия:

1. Операция изменяет состояние **вне** workspace (прод-БД, файлы на сервере, `/etc/...`).
2. Нет технического обхода через workspace-аналог.
3. Задача не может быть выполнена отложенно без потери смысла.

**Не эскалировать** для: gradle compile/ktlint/bootJar, docker build/restart/logs, npm/vite/prettier — для всего есть workspace-обход.

### DEFERRED-формат для tasks.md

Если sandbox всё-таки блокирует **и** обхода нет, запись в `tasks.md`:

```markdown
- [ ] TNNN [VERIFY] DEFERRED-sandbox: <операция>. Команда для пользователя: `<cmd>`. Ожидаемый результат: <exit 0 / new artefact>.
```

Не оставлять DEFERRED без явной команды — это превращает «агент не смог» в «план для пользователя».

### Подробности

- ADR: [`knowledge/adr/local-0010-sandbox-recipes.md`](knowledge/adr/local-0010-sandbox-recipes.md).
- Helper: [`tools/check-sandbox-ready.sh`](tools/check-sandbox-ready.sh) + [`tools/README.md`](tools/README.md).
- Прецеденты: spec #305 (GRADLE_USER_HOME workaround), #361/#363 (DEFERRED docker build → теперь unblocked), #302 (DEFERRED gradle → теперь unblocked).

## Сборка / деплой / тесты

- **Сборка**: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew clean karaoke-app:bootJar karaoke-web:bootJar --parallel`.
- **Деплой**: `deploy/deploy_web.sh`, `deploy/deploy_public.sh`, `cd deploy && bash do.sh build_start_public`.
- **Тесты**: в CI нет; `karaoke-app/src/test` — `@Disabled`. Проверка — пользователем.

### Gradle: запуск с `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle`

> Стандартный pre-flight (см. § «Sandbox DSH» выше): все `./gradlew ...` команды
> должны идти с `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle` — workspace-аналог
> для read-only `~/.gradle/wrapper/dists/`. Без этого wrapper падает на
> первой загрузке дистрибутива.

### Обязательная проверка после ЛЮБОГО изменения кода (NON-NEGOTIABLE)

> Pass 239 + 245: правки без локальной пересборки ломали прод. **Vite-build ≠ Docker-образ**.

**После ЛЮБОГО изменения ОБЯЗАТЕЛЬНО** (в этом порядке; **все gradle-команды с `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle`, все docker-команды — через `docker --config=/home/nsa/Karaoke/.docker`**):

1. Backend compile: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`
2. Линтеры: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:ktlintCheck` + `cd webvue3 && npm run lint` + `cd karaoke-public && npm run lint`
3. Backend bootJar: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:bootJar --parallel` (на `nsa-i9` — также `:karaoke-app:bootJar`)
4. Frontend Vite: `npm run build && npm run format:check` в `webvue3/` и `karaoke-public/`
5. Docker-образы: `cd deploy && DOCKER_CONFIG=/home/nsa/Karaoke/.docker bash do.sh build_webvue3`; если менялся `karaoke-public` — `bash do.sh build_public`. Для проверки готового образа: `docker --config=/home/nsa/Karaoke/.docker run --rm <image> <cmd>`.

Только после всех 5 шагов OK — сообщать «готово к деплою». **НЕ ПРОПУСКАТЬ** даже для «очевидных» правок.

## Как обновлять этот файл

Правки governance — в ветке `0XX-agents-md-update`, semver bump. **НЕ дублировать** детали.