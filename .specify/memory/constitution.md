<!--
  Sync Impact Report
  - Version change: 2.3.0 → 2.4.0 (MINOR: сокращение Principles I-IX до
    компактной формы с cross-references, Pass 379 wayfinder #101 #111).
  - Modified principles: все 9 Principles (I-IX) переписаны в компактный
    формат «Rule + 1-2 строки + cross-ref». Детали перенесены в:
    - knowledge/guidelines/architecture-conventions.md (R-04, R-05, R-06, R-08, R-11, R-44).
    - knowledge/adr/ (ADR-0001, ADR-0005, ADR-0006, etc.).
    - docs/features/ (per-feature docs по FR-009).
    - AGENTS.md (MUST #0, subagent isolation, etc.).
    - CLAUDE.md (operational checklist).
  - Added sections: none (только сокращение существующих).
  - Removed sections: длинные «Рациональ» параграфы в Principles I, II, IV, VI,
    VII, VIII — перенесены в cross-refs.
  - Templates requiring updates: none (consumers — AGENTS.md, knowledge/, ADR).
  - New artifacts to reference:
      - knowledge/guidelines/architecture-conventions.md (после wayfinder #108).
  - Follow-up TODOs: none.
  - Sync: pass-through с wayfinder #111 (Pass 379) — semver 2.3.0 → 2.4.0.

  Sync Impact Report (предыдущая версия, 2.2.0 → 2.3.0)
  - Version change: 2.2.0 → 2.3.0 (MINOR: добавление Principle IX.3 — Subagent
    workspace isolation, синхронизация с AGENTS.md v2.8.0 Pass 379 follow-up).
  - Modified principles: добавлен Principle IX.3 — Subagent workspace isolation.
    Прецедент Pass 379 — race condition при 3 параллельных субагентах в одном
    workspace (R-07 JPA, R-04/05 docker, R-11 MP4) привела к чужим коммитам в
    чужих PR'ах; чинилось rebase + amend + force-push вручную (~30 минут).
  - Added sections: Principle IX.3 «Subagent workspace isolation (NON-NEGOTIABLE)».
  - Removed sections: none
  - Templates requiring updates: none (consumers — AGENTS.md, future CI).
  - New artifacts to reference:
      - tools/check-subagent-isolation.sh (proposed guard, не в этом коммите)
  - Follow-up TODOs: none

  Sync Impact Report (предыдущая версия, 2.1.0 → 2.2.0)
  - Version change: 2.1.0 → 2.2.0 (MINOR: добавление Principle IX — Knowledge-first,
    усиление существующего п. 5 «Категорически запрещено» формулировкой про
    codegraph_explore до Knowledge-first как явный failure-stop).
  - Modified principles: п. 5 «Категорически запрещено» — добавлен пункт про
    codegraph_explore / grep по src/ ДО Knowledge-first.
  - Added sections: Principle IX «Knowledge-first при разработке фич
    (NON-NEGOTIABLE)» — прецедент 2026-09-09, spec #339 (агент изобрёл форму
    кеша вместо паттернов из caching-patterns.md).
  - Removed sections: none
  - Templates requiring updates:
      .specify/templates/plan-template.md   ✅ aligned (Constitution Check gate
        теперь включает Principle IX)
      .specify/templates/spec-template.md   🔄 ТРЕБУЕТ ОБНОВЛЕНИЯ — добавить
        секцию «Knowledge References (MANDATORY)»
      .specify/templates/tasks-template.md  ✅ aligned (no change needed)
      .specify/templates/checklist-template.md 🔄 ТРЕБУЕТ ОБНОВЛЕНИЯ — добавить
        секцию «Knowledge Compliance»
  - New artifacts to reference:
      - tools/spec-knowledge-preflight.sh — пре-хуковый скрипт для bootstrap
      - docs/governance/knowledge-first.md — операционная памятка (Pass 340)
  - Follow-up TODOs:
      - detekt (после выхода версии с поддержкой Kotlin 2.2) — см. T049
      - typedoc-plugin-vue (для парсинга .vue single-file components) — backlog
      - рефакторинг WORKING_DATABASE/KSS_APP в DI (Pass 15+)
      - ADR (Architecture Decision Records) в docs/adr/ (Pass 16+)
      - переписывание истории git (git filter-repo / BFG) для удаления
        утёкших секретов из старых коммитов — отдельная задача после смены
        всех утёкших секретов (см. docs/migration-prod-server.md)
      - CODEOWNERS для AGENTS.md / constitution.md (Pass 340 follow-up)
      - enforcement проверки непустой секции Knowledge References в spec.md
        (Pass 340 follow-up — добавить в tools/check-knowledge-structure.sh)
-->
<!--
  Sync Impact Report (предыдущая версия)
  - Version change: 2.0.0 → 2.1.0 (MINOR: добавление Principle VIII — секреты
    и git-гигиена, усиление существующего п. 5 «Категорически запрещено»).
  - Modified principles: п. 5 «Категорически запрещено» переформулирован
    с явным указанием механизма (git rm --cached + .gitignore), добавлена
    обязанность pre-commit проверки.
  - Added sections: Principle VIII «Секреты и git-гигиена (NON-NEGOTIABLE)» —
    новый Core Principle с чек-листом и инцидентом-прецедентом (2026-08-03:
    deploy/.env трекался 3 года в публичном репо с утёкшими паролями).
  - Removed sections: none
  - Templates requiring updates:
      .specify/templates/plan-template.md   ✅ aligned (Constitution Check gate
        теперь включает Principle VIII)
      .specify/templates/spec-template.md   ✅ aligned (no change needed)
      .specify/templates/tasks-template.md  ✅ aligned (no change needed)
      .specify/templates/checklist-template.md ✅ aligned (no change needed)
  - New artifacts to reference:
      - docs/migration-prod-server.md — чек-лист миграции прода, в ходе которой
        обнаружена утечка
  - Follow-up TODOs:
      - detekt (после выхода версии с поддержкой Kotlin 2.2) — см. T049
      - typedoc-plugin-vue (для парсинга .vue single-file components) — backlog
      - рефакторинг WORKING_DATABASE/KSS_APP в DI (Pass 15+)
      - ADR (Architecture Decision Records) в docs/adr/ (Pass 16+)
      - переписывание истории git (git filter-repo / BFG) для удаления
        утёкших секретов из старых коммитов — отдельная задача после смены
        всех утёкших секретов (см. docs/migration-prod-server.md)
-->
# Karaoke Constitution

Этот документ определяет непреложные принципы, технологический стек, ограничения
доступа и процедуру внесения изменений для проекта `Karaoke` (svoemesto). При
расхождении с `AGENTS.md` / `DEVELOPMENT.md` приоритет — у Конституции; вторичные
правила уточняют её, но не отменяют.

## Core Principles

> **Формат**: каждый Principle содержит **название**, **1-2 строки утверждения**,
> **cross-reference** на детали. Это **нормативная база** — детали реализации
> живут в `knowledge/` (домены, компоненты), `ADR-XXXX-*.md`, `architecture-conventions.md`.

### I. Self-contained автопайплайн (NON-NEGOTIABLE)

**Rule**: Пайплайн Karaoke (ffmpeg, melt/MLT, Demucs, Sheetsage) выполняется
на admin-машине через `ProcessBuilder` **без зависимости от внешних SaaS** в рантайме.
Допускаются локально развёрнутые ML (Ollama, Silero TTS, Sheetsage, SearXNG).

**Детали / рациональ**: см. ADR-0005 (self-hosted ML), sections
«Технологический стек» + «Рабочий процесс» ниже.

### II. Сырой JDBC + дифф по хэшам (NON-NEGOTIABLE)

**Rule**: БД — только через сырой JDBC (`KaraokeConnection`). Никакого
JPA/Hibernate/Exposed. Сравнение LOCAL↔SERVER — через `recordhash` + `associateBy { it.id }`.

**Enforcement**: `tools/check-no-jpa-imports.sh` (Pass 379, R-07).

**Детали**: см. ADR-0001-raw-jdbc.

### III. Двух-БД синхронизация через SyncRegistry

**Rule**: Сущности с LOCAL↔SERVER sync обязаны быть в `SyncRegistry.all`
+ 8 флагов `sync_<key>_<push|pull>_<insert|update|delete|move>_allowed`
в `KaraokeProperties.kt`.

**Детали**: см. `sync/SyncTarget.kt`.

### IV. Async-очередь задач с парсингом stdout

**Rule**: Длительные операции — через `KaraokeProcess*` (OS subprocess).
**ProcessBuilder.redirectErrorStream(true) обязательно**. Задания имеют
приоритет и `threadId`-лейны (`HEAVY_RENDER=0`, `LIGHT_BACKGROUND=-1`, etc).

**Детали**: см. ADR-0006 (redirectErrorStream).

### V. Двух-фронтенд: admin и public — разные приложения

**Rule**: `webvue3` (admin) и `karaoke-public` (public) — **разные приложения**.
Смешивание ЗАПРЕЩЕНО.

**Детали**: см. CLAUDE.md § «Двух-фронтенд» + cross-machine test setup.

### VI. Code Standards (NON-NEGOTIABLE)

**Rule (FR-006)**: публичные API MUST иметь KDoc/JSDoc + `@see` на
`docs/features/<slug>.md`. CI падает на missing description.

**Rule (FR-007)**: ktlint/ESLint в pre-commit + CI. Baseline в per-module
файлах. CI падает на **новые** нарушения. Темп сокращения ≥10%/мес.

**Rule (FR-009)**: при правке кода MUST обновить per-feature документ
в том же PR (чеклист в `.github/PULL_REQUEST_TEMPLATE.md`).

**Детали**: см. `specs/001-code-standards-docs/contracts/per-feature-doc.md`.

### VII. Cross-Machine Setup (NON-NEGOTIABLE)

**Rule**: Локальные AI-конфиги (`CLAUDE.md`, `.cursorrules`, `.aider*`,
`AGENTS.md.local`, `.claude/`) MUST быть в `.git/info/exclude` или
`~/.gitignore_global`. Только общие правила — в `AGENTS.md`.

**Rule**: `.git-blame-ignore-revs` MUST содержать хэши массовых коммитов
(prettier, baseline healing). `.gitattributes` — нормализация line endings.

**Детали**: см. `docs/onboarding.md`, `docs/claude-code-setup.md`,
`docs/architecture-notes.md`.

### VIII. Секреты и git-гигиена (NON-NEGOTIABLE)

**Прецедент**: 2026-08-03 — `deploy/.env` с паролями Postgres/MinIO/Docker Hub
трекался в публичном github.com/svoemesto/Karaoke **3 года**.

**Rule (VIII.1)**: Секрет-файлы MUST быть в `.gitignore` И НЕ трекаться git.
`.gitignore` НЕ достаточно — `git rm --cached` обязательно для файлов
уже в индексе.

**Rule (VIII.2)**: Never commit: `deploy/.env`, `deploy/do.env`,
`*.key`, `*.pem`, `*.p12`, `*.pfx`, `deploy/ollama_data/`, `dist/`,
`node_modules/`, `CLAUDE.md`, `.cursorrules`, `.aider*`.

**Rule (VIII.3)**: Pre-commit check MUST верифицировать, что ни один
секрет-файл не попадает в индекс:
`git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$'` → пусто.

**Rule (VIII.4)**: При обнаружении утёкшего секрета — **НЕМЕДЛЕННО**
сменить секрет (приоритет выше очистки истории), потом `git rm --cached`,
потом опционально `git filter-repo`.

**Rule (VIII.5)**: Секреты в коде (hardcoded) ЗАПРЕЩЕНЫ — только через
env-переменные (`@Value`, `System.getenv`). Дефолт в `${VAR:default}`
допустим, но дефолт MUST быть невалидным или публичным.

**Детали**: см. `docs/migration-prod-server.md`.

### IX. Knowledge-first при разработке фич (NON-NEGOTIABLE)

**Прецедент**: spec #339 — агент изобрёл форму кеша вместо паттернов
из `knowledge/domains/caching/components/caching-patterns.md`.

**Rule**: Перед ЛЮБОЙ новой фичей — 5 шагов Knowledge-first (README + 3+ grep
+ domain.md + components + ADR). Только после — `codegraph_explore` / grep.

**Failure-stop**: codegraph ДО Knowledge = нарушение Constitution.
Search без результатов = зафиксировать в spec.md явно.

**Enforcement**: `tools/spec-knowledge-preflight.sh` (Pass 340).

**Single source of truth**: `AGENTS.md` MUST #0 + constitution.md § IX.

**Детали**: см. AGENTS.md § MUST #0, `docs/governance/knowledge-first.md` (layer 2).

#### IX.3 — Subagent workspace isolation (NON-NEGOTIABLE, Pass 379)

**Rule**: Несколько субагентов для параллельных PR-веток MUST работать в
отдельных `git worktree`. Прецедент Pass 379 — race condition привела к
30 минутам на rebase всех PRов.

**Mandatory Action**: `git worktree add ../Karaoke-${N}-${slug} -b "${N}-${slug}" master`
перед запуском каждого субагента.

**Enforcement**: `tools/check-subagent-isolation.sh` (Pass 379).

**Single source of truth**: `AGENTS.md` § «Subagent workspace isolation» (Pass 379 follow-up, semver 2.7.0 → 2.8.0).

## Технологический стек

- **Backend**: Kotlin 1.x, Spring Boot 2.x/3.x, JDK 17, Gradle multi-module.
  Модули: `karaoke-app` (core engine, разворачивается ТОЛЬКО на admin-машине),
  `karaoke-web` (публичный API/Thymeleaf, тонкий слой над `karaoke-app`,
  разворачивается на проде), `karaoke-db` (legacy, не используется в продакшене).
  Корневой `pom.xml` — leftover от Maven, не использовать.
- **Frontend**: Vue 3 + Vite, Node 22 (LTS), Bootstrap 5 / Bootstrap-vue-next.
  `karaoke-vue` — legacy, не участвует в сборке.
- **Storage**: PostgreSQL (через сырой JDBC), MinIO (S3-compatible объектное
  хранилище для медиа), Redis — не используется.
- **ML/инфра**: Ollama (LLM), SearXNG (поиск), Playwright (headless Chromium для
  JS-рендера), Silero TTS (озвучка уведомлений), Demucs (стем-сепарация), Sheetsage
  (key/BPM/chords).
- **Runtime**: Docker + docker-compose. Образы: `eclipse-temurin:22-jre-jammy`
  (karaoke-web/app, JRE не JDK), `nginx:stable` (**не** `nginx:alpine` —
  compose использует `/bin/bash -c`, в alpine его нет), `node:22-alpine` (**не**
  `node:latest` — недетерминирован). Внутри `karaoke-app` образа установлен
  Docker CE намеренно — приложение само запускает `docker run`/`docker compose`
  из кода.
- **Деплой-окружения**: admin (LOCAL Postgres, контейнеры из `~/Karaoke/deploy`
  и `/sm-karaoke/system/deploy` — **разные папки**), прод-сервер (`<PROD_SERVER_IP>`,
  сервисы: БД, karaoke-web, karaoke-public, MinIO; **karaoke-app на проде не
  разворачивается вовсе**). Docker-сеть на сервере — `deploy_karaokenet` (не
  `karaokenet`).

## Ограничения и доступы агента

Эти правила имеют приоритет над AGENTS.md в случае конфликта.

**Категорически запрещено агенту:**
1. Пересобирать/перезапускать контейнер `karaoke-app` локально (только пользователь).
   **Исключение**: если агент работает на машине с hostname `dev-pc` под OS-пользователем
   `dev`, это ограничение снимается — агент может пересобирать/перезапускать `karaoke-app`
   (и любой другой локальный контейнер проекта) на этой машине без согласия пользователя.
   На любой другой машине и/или под любым другим пользователем действует общее правило.
2. Деплоить на сервер (`deploy_web.sh`, `deploy_public.sh`, rsync на `<PROD_SERVER_IP>`,
   прямые DDL/DML к серверной БД) — только по прямому согласию пользователя, на
   каждое действие отдельно.
3. Редактировать файлы на сервере напрямую.
4. Перезаписывать `deploy/do.env` (содержит секреты).
5. Коммитить `deploy/ollama_data/`, `dist/`, `node_modules/`, `deploy/.env`,
   `deploy/do.env` и любые другие секрет-файлы (см. Principle VIII.2 —
   полный список). **`.gitignore` НЕ достаточно**: если файл уже в индексе
   git — `git rm --cached <file>` обязателен. Pre-commit проверка:
   `git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$'` MUST быть пусто.
6. Печатать секреты (`DOCKER_PASSWORD`, токены, пароли БД) в вывод `do.sh` или
   в логи — секреты живут только в `do.env`/`.env` (в `.gitignore`).
7. Использовать `nginx:alpine`, `node:latest`, JDK вместо JRE в прод-образах.
8. **Полезть в `codegraph_explore` / `grep` по `src/` ДО Knowledge-first
   pre-flight** (см. Principle IX и `AGENTS.md` MUST #0). Это
   failure-stop: если шаги 1-4 Knowledge-first не выполнены — СТОП,
   не продолжать. Прецедент 2026-09-09 (spec #339).

**Разрешено агенту:**
1. Редактировать любой код во всех модулях.
2. Собирать gradle-джары (`./gradlew karaoke-app:bootJar`, `./gradlew karaoke-web:bootJar`).
3. Запускать `npm run dev` / `npm run build` для `webvue3` и `karaoke-public`.
4. Пересобирать/перезапускать локальные контейнеры `karaoke-web`, `webvue3`,
   `karaoke-public` через `deploy/do.sh` (но с учётом правила двух папок:
   `build_*` из `~/Karaoke/deploy`, `start_*` из `/sm-karaoke/system/deploy`,
   кроме `karaoke-public` — там одной командой `build_start_public`).
5. Самостоятельно собирать (без перезапуска) `karaoke-app`.
6. **На машине с hostname `dev-pc` под OS-пользователем `dev`**: пересобирать/перезапускать
   любой локальный контейнер проекта (включая `karaoke-app`) без согласия пользователя (см.
   исключение из п. 1 «Категорически запрещено» выше).
7. **На машине с hostname `dev-pc` под OS-пользователем `dev`**: выполнять любые операции с
   локальной базой данных (запросы, миграции, изменения схемы/данных) без согласия
   пользователя. Это не распространяется на серверную (прод) БД — прямые DDL/DML к ней
   остаются в п. 2 «Категорически запрещено» (только по прямому согласию пользователя).

**Граница доступа к MLT/Karaoke.properties** (настройки рендера, ~150 параметров):
персистятся в `/sm-karaoke/system/Karaoke.properties` (base64-properties), редактируются
через Properties UI/API без перекомпиляции. Прямые правки файла в обход UI —
только с согласия пользователя.

## Рабочий процесс

- **Сборка бэка** — `./gradlew clean karaoke-app:bootJar karaoke-web:bootJar --parallel`.
  Параллельные gradle-сборки над одним репозиторием ЗАПРЕЩЕНЫ: `clean` стирает
  общий `build/`, gradle держит эксклюзивный лок на `.gradle/`. Сериализация —
  `deploy/build-lock.sh` (`flock` + guard в `gradlew`).
- **Сборка фронта** — `cd webvue3 && npm run build` / `cd karaoke-public && npm run build`.
  В `karaoke-public` — через Docker (`do.sh build_start_public`).
- **Деплой** — только из `deploy/` (`bash do.sh ...` или `deploy_web.sh` /
  `deploy_public.sh`). Любая команда, не указанная в AGENTS.md/DEVELOPMENT.md,
  требует подтверждения.
- **Git** — не коммитить без явного запроса пользователя. Перед `git add` —
  обязательно `git status` + `git diff --stat`. Commit-сообщения — на русском,
  коротко и по существу, в стиле `area: краткое описание`. **На каждой машине**
  разработчик MUST настроить: `git config blame.ignoreRevsFile .git-blame-ignore-revs`
  (после клонирования). **Перед коммитом** проверить, что
  `.gitattributes` нормализовал line endings (`git diff --stat` показывает
  только значимые изменения, не `M` для всех строк).
- **Push-ловушка**: при падении `deploy_web.sh` по `EOF`/`400 Bad request` — попросить
  пользователя запустить вручную без VPN. После деплоя обязательно проверить
  `Status: Downloaded newer image` (не `Image is up to date`) и реальное
  содержимое env внутри контейнера.
- **nginx 80to8897** — отдельный файл (не симлинк). При rsync обновляется в
  `/root/Karaoke/deploy/`, но nginx читает из `/etc/nginx/sites-enabled/80to8897`.
  Нужно копировать вручную.
- **Тесты**: в CI нет. Существующие тесты (`karaoke-app/src/test`) — интеграционные,
  большинство `@Disabled`, требуют сеть/браузер/credentials. Не полагаться на
  них как на проверку — проверка делается пользователем вручную или
  в production-like окружении.

## Governance

1. **Приоритет**: Конституция > `AGENTS.md` > `DEVELOPMENT.md` > остальные документы.
   Конституция фиксирует непреложные принципы; `AGENTS.md` фиксирует рабочие
   инструкции для агента; `DEVELOPMENT.md` — архитектурный контекст и
   dated-историю конкретных фич (для durable-правил смотреть в этом файле секцию
   «Architecture notes», для dated-истории — `docs/architecture-notes-archive.md`).
   **Дополнение (Phase 002)**: в иерархии 9 уровней (см. таблицу в `AGENTS.md`,
   секция «Документация и иерархия») — добавились `docs/onboarding.md`,
   `docs/claude-code-setup.md`, `docs/architecture-notes.md` и
   `.git-blame-ignore-revs` / `.gitattributes`.
2. **Внесение изменений**: каждое изменение Конституции оформляется как
   `docs: amend constitution to vX.Y.Z (краткое описание)` в коммите. В commit-body
   указывается Sync Impact Report (какие принципы добавлены/удалены/переименованы,
   какие шаблоны обновлены, какие остались TODO).
3. **Версионирование (semver)**:
   - **MAJOR** (X.0.0) — обратно несовместимое изменение governance/принципов:
     удаление принципа, переопределение смысла существующего, изменение
     ограничений доступа агента.
   - **MINOR** (x.Y.0) — добавление нового принципа, новой секции, существенное
     расширение существующего принципа.
   - **PATCH** (x.y.Z) — уточнения формулировок, typo-фиксы, несемантические
     правки, обновление ссылок.
4. **Compliance review**: каждое изменение в коде, проходящее через `/speckit.plan`
   (или эквивалентный code review), обязано проверить Constitution Check —
   соответствие всем Core Principles. Нарушение должно быть явно обосновано в
   секции «Complexity Tracking» плана.
5. **Sync-обязательства**: при изменении Принципа обновить все зависящие
   артефакты (шаблоны планов, спецификаций, задач; runtime-guidance в
   `AGENTS.md`/`DEVELOPMENT.md`; agent-specific skills и команды, если
   ссылаются на принципы). Sync Impact Report пишется в HTML-комментарии
   в начале `constitution.md` при каждом amend. **Дополнение (Phase 002)**:
   при добавлении нового принципа обновлять `docs/architecture-notes.md`
   (запись о PR) + `AGENTS.md` (если меняется иерархия документации).
6. **Compliance-проверка в production** (на стороне пользователя, не агента):
   - после `deploy_web.sh` — логи push **не** содержат `EOF`/`400 Bad request`,
     на сервере `Status: Downloaded newer image`;
   - nginx reload только после `nginx -t`;
   - `docker exec karaoke-web env | grep <VAR>` для проверки реально прокинутых
     env-переменных.

**Version**: 2.4.0 | **Ratified**: 2026-07-20 | **Last Amended**: 2026-09-15 (Pass 379 wayfinder #111)
