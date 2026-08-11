# Feature Specification: Воспроизводимая настройка Linux Mint для проекта Karaoke

**Feature Branch**: `170-mint-dev-setup`
**Created**: 2026-08-11
**Status**: Draft
**Input**: User description: "Задача - настройка компьютера для работы с проектом. Это свежеустановленный компьютер с Linux Mint. Необходимо установить на него недостающий для работы софт (докер и т.п.) и настроить его для работы с проектом, чтобы на нём локально поднимались и работали все контейнеры, включая базу данных и хранилище. Результат настроек надо запоминать, чтобы потом можно было воспользоваться этой спекой для настройки ещё одного компьютера."

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Разработчик с нуля поднимает рабочую среду (Priority: P1)

Новый разработчик (или существующий, купивший новый ноутбук) приходит на свежеустановленный Linux Mint 22.x, клонирует репозиторий `Karaoke`, читает спеку `specs/170-mint-dev-setup/spec.md` и связанный с ней артефакт-скрипт, и за один проход (1-2 часа, без VPN) получает рабочую машину, на которой локально поднимаются и стартуют все контейнеры проекта: `karaoke-app` (бэк), `karaoke-web` (фронт-бэк), `karaoke-webvue3` (админка), `karaoke-public` (публичный сайт), `searxng` + `fourget` (поиск), `karaoke-db` (PostgreSQL 16) и `karaoke-storage` (MinIO).

**Why this priority**: Без рабочей среды разработчик не может ни делать PR, ни запускать локальную отладку. Это базовый prerequisite для всех остальных сценариев.

**Independent Test**: Может быть проверено изолированно: на чистой VM с Linux Mint 22.2 выполнить скрипт из спеки и убедиться, что `docker ps` показывает 8+ работающих контейнеров, `http://localhost:7906` отвечает (webvue3), `http://localhost:8888` отвечает (karaoke-public), `http://localhost:9001` отвечает (MinIO console), `psql` от localhost:5432 подключается. Если этот сценарий работает — спека валидна.

**Acceptance Scenarios**:
1. **Given** чистый Linux Mint 22.2 + клонированный `~/Karaoke`, **When** пользователь выполняет скрипт `specs/170-mint-dev-setup/setup-mint.sh` (создаёт `do.env` из шаблона, подставляя свои локальные пути и пароли), **Then** все указанные контейнеры стартуют через `bash deploy/do.sh start all` + `bash deploy/do.sh start_app` + MinIO, и `docker ps` показывает 8+ контейнеров в статусе `Up`.
2. **Given** поднятые контейнеры, **When** разработчик открывает `http://localhost:7906` (webvue3) в браузере, **Then** админка загружается и показывает счётчики статистики (счётчики могут быть нулевыми — БД только что инициализирована).
3. **Given** поднятые контейнеры, **When** разработчик открывает `http://localhost:8888` (karaoke-public), **Then** публичный сайт загружается (главная страница с логотипом, без данных — норма для пустой БД).
4. **Given** `do.env` создан по инструкции из спеки, **When** разработчик делает `git status` после `git add -A`, **Then** `do.env` НЕ появляется в списке unstaged/staged файлов (он в `.gitignore`), и `git ls-files deploy/do.env` возвращает пусто (согласуется с Principle VIII.1).

---

### User Story 2 — Воспроизведение спеки на втором-третьем-десятом компьютере (Priority: P1)

Автор спеки или коллега через полгода копирует `specs/170-mint-dev-setup/spec.md` + `specs/170-mint-dev-setup/setup-mint.sh` + `deploy/do.env.template` (последний создаётся этой спекой) на новую машину с Linux Mint 22.x, и получает ту же самую рабочую среду без переизобретения — без поиска в Slack/чате «а как ставил в прошлый раз».

**Why this priority**: Это и есть основная мотивация спеки («результат настроек надо запоминать, чтобы потом можно было воспользоваться этой спекой»). Без воспроизводимости спека теряет смысл — будет ещё одной страницей «общих рекомендаций» вроде `docs/onboarding.md`, но с другой ОС.

**Independent Test**: Можно проверить, запустив скрипт спеки на второй чистой VM с тем же Linux Mint 22.2 и сравнив список контейнеров + доступность эндпоинтов с эталоном из сценария 1. Спека воспроизводима, если `bash do.sh start all` + MinIO дают тот же результат.

**Acceptance Scenarios**:
1. **Given** `specs/170-mint-dev-setup/setup-mint.sh` и `deploy/do.env.template` лежат в репо, **When** новый разработчик берёт эти 2 файла + клон репо на новую машину, **Then** после выполнения `setup-mint.sh` (одна команда) машина готова к разработке, без обращения к внешним источникам.
2. **Given** спека применена на 2+ машинах, **When** сравнивается `docker ps --format "{{.Names}}\t{{.Status}}"`, **Then** списки контейнеров и их статусы идентичны (с точностью до версии `BUILD_VERSION`).
3. **Given** в спеке указана конкретная версия Linux Mint (22.2 Zara), **When** на машине с другой версией (например, Mint 21.x или 23.x) запускается скрипт, **Then** скрипт явно сообщает «неподдерживаемая версия» и останавливается (fail-fast), а не молча ломается на полпути.

---

### User Story 3 — Документация для AI-агента (opencode) на этой машине (Priority: P2)

AI-агент, открывший сессию opencode на новой Linux Mint-машине разработчика, читает `AGENTS.md` + `constitution.md` + `specs/170-mint-dev-setup/spec.md` и понимает: какие контейнеры подняты, какие порты открыты, как читать логи (`docker logs <container>`), какие папки-маунты (`/sm-karaoke/system/Karaoke*`), какие переменные окружения в `do.env`, и какие у него ограничения (per Constitution «Ограничения агента»: на машине НЕ `dev-pc` под пользователем НЕ `dev` агенту запрещено пересобирать `karaoke-app` и пушить на сервер).

**Why this priority**: Вспомогательный сценарий — но без него спека неполна: AI-агенту на новой машине нужен явный «cheat sheet», чтобы не натыкаться на те же грабли, что и разработчик (например, не пытаться пересобрать `karaoke-app` на чужой машине).

**Independent Test**: Открыть новую opencode-сессию, спросить «какие контейнеры должны быть запущены локально?» и получить ответ с конкретным списком имён и портов. Спросить «могу ли я перезапустить karaoke-app?», получить корректный ответ «нет, эта машина не dev-pc, и пользователь не dev — пересборка только с согласия пользователя» (per Constitution).

**Acceptance Scenarios**:
1. **Given** спека написана и применена, **When** AI-агент читает её и связанные секции спеки, **Then** агент может перечислить имена 8+ локальных контейнеров, их порты, и расположение bind-mounts.
2. **Given** машина не `dev-pc` или пользователь не `dev`, **When** агент пытается выполнить `bash deploy/do.sh build_start_app` без явного согласия пользователя, **Then** агент останавливается и спрашивает разрешения (per Constitution, п. 1 «Категорически запрещено»).

---

### Edge Cases

- **Что если уже стоит Docker другой версии (например, из snap)?** Скрипт спеки должен обнаружить это (через `docker --version` + `which docker` → путь) и либо использовать существующий (если версия 24+ и `compose` v2 встроен), либо удалить/переустановить (с подтверждением пользователя).
- **Что если диск зашифрован (LUKS) и точка монтирования `/sm-karaoke/system` отсутствует при загрузке?** Скрипт должен явно создать папки (с `sudo`) и предложить пользователю вынести их в `/etc/fstab` или `~/.config/systemd/user/` (но это уже за скоупом — достаточно просто создать).
- **Что если пользователь не в группе `docker`?** Скрипт добавляет пользователя в группу, перезагрузка требуется для применения — спека должна явно об этом написать и предложить `newgrp docker` как workaround для текущей сессии.
- **Что если `karaoke-db` уже была инициализирована с другим паролем Postgres?** Если контейнер запускается с другим `DB_LOCAL_POSTGRES_PASSWORD` в `do.env`, чем тот, что лежит в `DB_FOLDER` (volume), Postgres не стартует. Скрипт спеки должен явно предупредить: «если меняете пароль Postgres в `do.env` — удалите папку `DB_FOLDER` или мигрируйте данные, иначе контейнер упадёт при старте».
- **Что если Linux Mint версии 21.x (основана на Ubuntu 22.04), а не 22.2 (Ubuntu 24.04)?** Команды `apt install openjdk-18-jdk` и `nodejs` из коробки (через apt) дадут другие версии; нужно адаптировать. Спека покрывает **только 22.2 (Zara)**, на других версиях — fail-fast в начале скрипта.
- **Что если на машине нет доступа к Docker Hub (корпоративная сеть / VPN)?** `docker pull postgres:16` / `minio/minio:latest` / `searxng/searxng:latest` упадёт. Спека должна явно фиксировать «нужен публичный Docker Hub или приватный registry с зеркалами этих образов» как prerequisite, и `setup-mint.sh` — падать с понятной ошибкой на первом `docker pull`.
- **Что если уже есть контейнеры с такими же именами (`karaoke-db`, `karaoke-storage`)?** `docker compose up` падает с conflict. Скрипт должен либо использовать `docker rm -f` для конфликтующих контейнеров (с подтверждением), либо сообщать, какие именно мешают, и предлагать ручное удаление.
- **Что если порты 7906, 7905, 8888, 9001, 5432, 9000 уже заняты другими процессами?** `docker compose up` падает с "port is already allocated". Скрипт должен перед стартом сделать `ss -tlnp | grep -E ':(5432|7905|7906|8888|9000|9001|7900|7901) '` и предупредить, какие заняты.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Спека MUST покрывать установку и настройку Linux Mint 22.2 (Zara, основана на Ubuntu 24.04) для полноценной локальной разработки проекта `Karaoke`.
- **FR-002**: Спека MUST включать конкретные команды `apt install` для системных зависимостей: `openjdk-18-jdk` (или JDK 22, если есть), `nodejs`/`npm` (через NodeSource для версии 22 LTS), `python3.11+`, `git`, `postgresql-client-15`, `ffmpeg`, `pre-commit`, `melt` (MLT 7+), `docker-ce` + `docker-compose-plugin` (НЕ из snap), `curl`, `jq`.
- **FR-003**: Спека MUST содержать инструкцию по добавлению пользователя в группу `docker` (с предупреждением о необходимости `newgrp docker` или перелогина).
- **FR-004**: Спека MUST явно фиксировать **список всех локальных контейнеров**, которые поднимаются на dev-машине, с их портами и назначением: `karaoke-app` (8080/8900), `karaoke-web` (8090), `karaoke-webvue3` (7906), `karaoke-webvue` (7905, legacy — поднимать опционально), `karaoke-public` (8888), `searxng` (8888→8080 внутри), `fourget` (8889), `karaoke-db` (5432), `karaoke-storage` (9000 S3 + 9001 console), `ollama` (11434, опционально для LLM).
- **FR-005**: Спека MUST создать **артефакт `deploy/do.env.template`** (коммитится в репо), содержащий все обязательные переменные окружения из `deploy/do.sh` и `deploy/docker-compose*.yml` с **пустыми значениями** или **помеченными placeholder'ами** (`<SET-ME>`, `<LOCAL-PATH-TO-DB-FOLDER>`, и т.п.). Разработчик на новой машине копирует шаблон в `deploy/do.env` и заполняет под себя.
- **FR-006**: Спека MUST создать **артефакт-скрипт `specs/170-mint-dev-setup/setup-mint.sh`** (коммитится в репо), который:
  - проверяет версию ОС (Linux Mint 22.2) и архитектуру (x86_64);
  - проверяет наличие `sudo` (скрипт прерывается, если не передан `sudo` или пользователь не в `sudoers`);
  - проверяет, что `git`, `curl` уже стоят (ставит, если нет);
  - ставит системные зависимости через `apt install -y` идемпотентно;
  - ставит Node 22 LTS через NodeSource (если версия из apt < 22);
  - ставит Docker CE из официального репозитория Docker (НЕ из snap), идемпотентно;
  - добавляет текущего пользователя в группу `docker`;
  - проверяет, что `deploy/do.env` существует (если нет — копирует из `deploy/do.env.template` и предлагает заполнить, останавливаясь с понятной инструкцией);
  - клонирует (или обновляет) репозиторий `Karaoke` в `~/Karaoke` (если ещё не склонирован);
  - настраивает `git config blame.ignoreRevsFile .git-blame-ignore-revs` (per Principle VII.2);
  - создаёт папки для bind-mounts (`/sm-karaoke/system/Караоке-db`, `/sm-karaoke/system/Караоке-storage`, и т.д. — пути берутся из `do.env` после его заполнения);
  - запускает `bash deploy/do.sh start all` для контейнеров app/web/db/searxng/fourget;
  - запускает MinIO отдельной командой (через `docker compose -f deploy/docker-compose-storage.yml up -d` — не входит в `do.sh start all`);
  - **НЕ пересобирает** образы (для первого запуска достаточно `docker pull` через `do.sh pull`, а пересборка — на усмотрение пользователя);
  - опционально поднимает `ollama` (если пользователь ввёл `--with-ollama` или интерактивно выбрал «да»).
- **FR-007**: Спека MUST явно разделять **что агенту (opencode) разрешено** на этой машине и что нет, ссылаясь на Constitution (раздел «Ограничения и доступы агента»). В частности: на машине с hostname НЕ `dev-pc` или под пользователем НЕ `dev` агенту ЗАПРЕЩЕНО пересобирать/перезапускать `karaoke-app` и ЗАПРЕЩЕНО деплоить на сервер.
- **FR-008**: Спека MUST включать **smoke-test** после поднятия контейнеров: проверка `docker ps` (все 8+ контейнеров в статусе `Up`), проверка `curl http://localhost:7906` (webvue3 отвечает 200), проверка `curl http://localhost:8888` (karaoke-public отвечает 200), проверка `curl http://localhost:9001/minio/health/live` (MinIO healthcheck OK), проверка `psql -h localhost -U postgres -c '\l'` подключается к `karaoke` БД.
- **FR-009**: Спека MUST включать **rollback-инструкцию**: как остановить все контейнеры (`bash deploy/do.sh stop all` + `docker compose -f deploy/docker-compose-storage.yml down`), как удалить данные (снести `DB_FOLDER` и `STORAGE_FOLDER`), как полностью снести окружение (`docker compose down -v`).
- **FR-010**: Спека MUST документировать **как обновлять её саму** при изменении зависимостей (новая версия Node, новая версия Docker, новые контейнеры в `docker-compose.yml`). Trigger для обновления: `git log -1 --format=%H -- deploy/docker-compose.yml deploy/do.sh deploy/do.env.template` показывает изменение после последнего коммита спеки.
- **FR-011**: Спека MUST быть **согласована** с `docs/onboarding.md` (общий setup для любого AI-агента): не дублировать, а дополнять (Mint-specific шаги, БД+MinIO, `do.env.template` как новый артефакт). Ссылка между ними обязательна.
- **FR-012**: Спека MUST учитывать **Principle VIII** (секреты и git-гигиена): `do.env` НЕ в гите; `do.env.template` — коммитится, но с **пустыми/placeholder-значениями**; pre-commit-check на `git ls-files | grep do.env` MUST быть зелёным после коммита спеки.
- **FR-013**: Артефакт `setup-mint.sh` MUST быть **идемпотентным**: повторный запуск не ломает уже работающее окружение, а только проверяет версии и пропускает уже установленные пакеты.

### Key Entities *(include if feature involves data)*

- **`do.env.template`** (новый файл в `deploy/`): текстовый файл с переменными окружения, которые читает `deploy/do.sh` через `source` под `set -a`. Содержит все обязательные ключи (`DOCKER_REGISTRY`, `BUILD_VERSION`, `APP_PORT_HOST`, `APP_PORT_INSIDE_CONTAINER`, `WEB_PORT_HOST`, `WEB_PORT_INSIDE_CONTAINER`, `DB_PORT_HOST`, `DB_PORT_INSIDE_CONTAINER`, `STORAGE_PORT_HOST`, `STORAGE_CONSOLE_PORT_HOST`, `STORAGE_KEY`, `STORAGE_SECRET`, `DB_LOCAL_POSTGRES_USER`, `DB_LOCAL_POSTGRES_PASSWORD`, `APP_FOLDER_HOST`, `APP_FOLDER_K1`, `APP_FOLDER_K2`, `APP_FOLDER_K3`, `APP_FOLDER_SYSTEM`, `APP_FOLDER_STORE`, `STORAGE_FOLDER`, `DB_FOLDER`, `WEBVUE3_PATH_TO_NGINX_CONF`, `WEBVUE_PATH_TO_NGINX_CONF`, `ENABLE_APP_GPU`, `APP_JAVA_OPTS`, `WEB_JAVA_OPTS`, и т.д.). Значения — либо пустые строки, либо безопасные дефолты (`localhost`, `0`, доменные имена), НИКОГДА не реальные секреты.
- **`setup-mint.sh`** (новый файл в `specs/170-mint-dev-setup/`): bash-скрипт, воспроизводящий настройку. Идемпотентный, с понятными сообщениями об ошибках, fail-fast на неподдерживаемой ОС, ссылками на разделы спеки при ошибках.
- **`spec.md`** (этот файл, `specs/170-mint-dev-setup/spec.md`): спецификация, описывающая сценарии, требования и success criteria.
- **`checklists/requirements.md`** (новый файл в `specs/170-mint-dev-setup/checklists/`): quality-checklist, генерируемый `/speckit.specify`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Новый разработчик с чистого Linux Mint 22.2 получает рабочую среду **не более чем за 1,5 часа** (включая `apt update`, скачивание ~500 МБ Docker-образов, клонирование репо) при выполнении инструкций спеки последовательно. Smoke-test (FR-008) зелёный.
- **SC-002**: Спека воспроизводима: при запуске `setup-mint.sh` на **второй** чистой VM с тем же Linux Mint 22.2 результат идентичен (тот же набор контейнеров, те же порты, тот же smoke-test зелёный) — **без изменений в скрипте и шаблоне**.
- **SC-003**: После применения спеки `docker ps` показывает **ровно 9 контейнеров** в статусе `Up` (8 обязательных + 1 опциональный `ollama`): `karaoke-app`, `karaoke-web`, `karaoke-webvue3`, `karaoke-public`, `searxng`, `fourget`, `karaoke-db`, `karaoke-storage`, `ollama` (если выбран).
- **SC-004**: Все указанные в FR-008 эндпоинты возвращают HTTP 200 в течение **30 секунд** после `docker compose up -d` (без перезапусков и без ручного `docker logs`).
- **SC-005**: Размер артефакта-скрипта `setup-mint.sh` — **не более 300 строк** (если больше — это симптом, что часть логики должна быть вынесена в отдельные скрипты или в спек-файлы).
- **SC-006**: `do.env.template` **не содержит реальных секретов** (проверка: `grep -E '(password|secret|key|token).*=.[^<].*[a-zA-Z0-9]{8,}' deploy/do.env.template` возвращает пусто — все реальные значения либо `<SET-ME>`, либо пустые).
- **SC-007**: pre-commit-check `git ls-files | grep -E '(do\.env|\.env)$'` возвращает **пусто** (принцип VIII.3 соблюдён).
- **SC-008**: В спеке явно перечислены **≥3 известных ловушки** Linux Mint 22.2 (например: `nodejs` из `apt` идёт версии 12, а нужен 22; `docker.io` из `apt` — устаревший, нужен `docker-ce` из репозитория Docker; `nginx:alpine` в `docker-compose` не работает — это уже в AGENTS.md, но спека фиксирует связь).

## Assumptions

- **Целевая ОС — Linux Mint 22.2 "Zara"** (основана на Ubuntu 24.04 Noble). Другие версии Mint (21.x, 23.x) — out of scope для первой версии спеки; спека явно падает с понятной ошибкой, если запущена на другой версии. Это **допустимое** ограничение: новые релизы Mint выходят раз в 2 года, и спека адаптируется под каждый из них отдельной ревизией.
- **Архитектура — x86_64.** ARM-машины (Apple Silicon через Asahi, Raspberry Pi) — out of scope.
- **Пользователь — НЕ root, имеет `sudo`.** Скрипт не запускается из-под `root` напрямую — он использует `sudo` для привилегированных операций.
- **Доступ в интернет есть**, в том числе к `apt repos`, `download.docker.com`, `deb.nodesource.com`, `github.com`, `registry-1.docker.io` (Docker Hub). Если интернета нет — спека неприменима (см. Edge Case «корпоративная сеть без Docker Hub»).
- **GitHub credentials уже настроены** (через `git config credential.helper store` или SSH-ключ) — спека полагается на это, но явно упоминает в troubleshooting: «если `git clone` падает с auth error — настройте `gh auth login` или `ssh-keygen` + `gh ssh add`».
- **Параметры окружения (`APP_FOLDER_*`, `STORAGE_FOLDER`, `DB_FOLDER`, порты) берутся из `do.env`** — спека не хардкодит пути в скрипте, а парсит `do.env` после его заполнения.
- **Папки bind-mounts (`/sm-karaoke/system/...`) — НЕ обязаны существовать** до запуска скрипта; скрипт их создаёт с `sudo` (с разрешениями на запись для текущего пользователя в группе `docker`).
- **Пересборка Docker-образов `karaoke-app`, `karaoke-web`, `karaoke-webvue3`, `karaoke-public` НЕ требуется** для smoke-test — `do.sh pull` подтягивает готовые образы из `DOCKER_REGISTRY`. Если разработчик планирует править код и пересобирать — это отдельный шаг (после smoke-test), который спека упоминает, но **не делает автоматически** (чтобы не занять 30+ минут на gradle-сборку при первом запуске).
- **AI-агент на этой машине — opencode.** Constitution упоминает также Claude Code, Cursor, Cody — спека не покрывает их установку (это вне её скоупа), но ссылается на `docs/onboarding.md` шаг 4 «Настроить AI-агент».
- **Ollama — опциональна.** Для базовой разработки (правка UI, MLT, фичи без LLM) Ollama не нужна. Спека ставит Ollama **только по явному запросу** (`--with-ollama` или интерактивный «да»), потому что образ `ollama/ollama` весит >1 ГБ и не всем нужен.

## Связанные документы

- [`docs/onboarding.md`](../../docs/onboarding.md) — общий setup для **любой** ОС (macOS/Ubuntu/Arch); спека **дополняет** его Linux Mint-специфичными шагами и БД+MinIO.
- [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md) — Principle VII (cross-machine setup), Principle VIII (секреты и git-гигиена), раздел «Ограничения агента».
- [`AGENTS.md`](../../AGENTS.md) — реестр команд `deploy/do.sh`, нумерация веток `NNN-slug`, «CI-gate для master» (спека ляжет в PR, который пройдёт CI).
- [`DEVELOPMENT.md`](../../DEVELOPMENT.md) — архитектура и обзор `deploy/do.sh`, `do.env`, `build-lock.sh`.
- [`CONTRIBUTING.md`](../../CONTRIBUTING.md) — стиль кода (скрипт `setup-mint.sh` пишется в этом стиле; shell-скрипты — в секции «Shell»).
- [`docs/architecture-notes.md`](../../docs/architecture-notes.md) — будет дополнен записью о PR `170-mint-dev-setup` после merge.
- [`docs/features/docker-deploy.md`](../../docs/features/docker-deploy.md) — per-feature документ для docker-стека (если существует на момент работы) — должен быть обновлён/создан вместе со спекой (per FR-009 Constitution).
- [`docs/onboarding-handoff/`](../../docs/onboarding-handoff/) — шаблоны передачи задачи другому AI-агенту; применимо, если задача спеки передаётся между машинами.
