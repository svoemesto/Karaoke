<!--
  Sync Impact Report
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

### I. Self-contained автопайплайн (NON-NEGOTIABLE)

Пайплайн производства караоке-видео (ffmpeg, melt/MLT, Demucs, Sheetsage) выполняется
на admin-машине через `ProcessBuilder` без зависимости от внешних SaaS в рантайме
обработки аудио/видео. Допускаются локально развёрнутые ML-модели (Ollama, Silero TTS,
Sheetsage) и локальный SearXNG. Любая новая фича, требующая внешнего API в горячем
пути обработки медиа, должна сначала получить одобрение пользователя.

Рациональ: исторически проект развивался в условиях ограниченного/нестабильного
интернета на admin-машине; cloud-only зависимости ломали прод.

### II. Сырой JDBC + дифф по хэшам (NON-NEGOTIABLE)

Доступ к БД — только через сырой JDBC (`KaraokeConnection`, `Connection.local()/remote()/`virtual()`).
Никакого JPA/Hibernate/Exposed. Сравнение LOCAL↔SERVER — через `recordhash` (md5 от
канонизированной строки таблицы), реализованный триггерами в БД + reflection-diff
в `KaraokeDbTable.save()`. **Любое сравнение рекордов между двумя БД обязано идти
через `associateBy { it.id }` (O(n)) — не через вложенные `.any`/`.none` (O(n²))**.
Загрузка записей для diff — пакетно `WHERE id IN (..)`, не по одной в цикле.

Рациональ: 18k+ записей на проде; O(n²) сравнения занимали 3+ минуты, O(n) — секунды.

### III. Двух-БД синхронизация через SyncRegistry

Любая сущность, которая должна расходиться между LOCAL и SERVER, обязана быть
явно добавлена в `SyncRegistry.all` (`sync/SyncTarget.kt`) и получить свои
8 флагов `sync_<key>_<push|pull>_<insert|update|delete|move>_allowed` в
`KaraokeProperties.kt`. Наличие `recordhash`-триггера в SQL-миграции **не**
означает участие в «Синхронизации в 1 клик». При добавлении/изменении колонок
таблицы, участвующей в sync, **обязательно** пересоздаётся `recordhash`-триггер
для затронутых таблиц (LOCAL и PROD) — иначе md5 разойдётся и sync сломается.

### IV. Async-очередь задач с парсингом stdout

Все длительные операции (ffmpeg, melt, Demucs, Sheetsage, загрузка в MinIO,
стим-джебы) — через `KaraokeProcess*` как OS-подпроцесс (`ProcessBuilder`).
Прогресс парсится из stdout по регексам (ffmpeg `time=`, Sheetsage `NN%|`).
**ProcessBuilder.redirectErrorStream(false) ЗАПРЕЩЁН** — буфер stderr переполняется
и блокирует процесс; всегда `redirectErrorStream(true)`. Задания имеют приоритет
и `threadId`-лейны (`HEAVY_RENDER=0`, `LIGHT_BACKGROUND=-1`, `REMOTE_STORE_UPLOAD=-2`,
`STEM_JOBS=…`); CPU ограничивается тремя слоями (docker `--cpus`, `MLT_CPU_LIMIT`,
`docker update`).

### V. Двух-фронтенд: админка и публичный сайт — разные приложения

- `webvue3` — admin SPA (Vue 3 + Vite + Vuex + Bootstrap-vue-next), `permitAll()` в
  `SecurityConfig.kt`, без авторизации. Хранит состояние через Vuex-модули
  (`<Entity>/store.js`) и персистит фильтры таблиц через `<Entity>/filter/store.js` +
  `setWebvueProp`/`getWebvueProp` (server-side key/value, переживает F5).
- `karaoke-public` — публичный SPA (Vue 3 + Vite + Bootstrap 5) с двумя дизайнами
  (`classic` / `modern`, выбор в `localStorage`), CSS-переменные `--km-*`.
- Смешивание ответственностей между admin и public ЗАПРЕЩЕНО. `<select>` в обеих SPA
  — с классом `form-select` (не `form-control`). Картинки — только MinIO, поле
  `picture_full` всегда `""`. Тег `SKIP` отдаёт заглушку «удалено по требованию
  правообладателя», теги наружу не утекают.

### VI. Code Standards (NON-NEGOTIABLE)

- **FR-006**: Публичные API (`class`, `fun`, `interface`, `export default` Vue-компонент)
  MUST сопровождаться KDoc/JSDoc-комментариями с `@see`-ссылкой на соответствующий
  per-feature документ (`docs/features/<slug>.md`). Документация генерируется через
  Dokka (`./tools/generate-docs.sh` → `docs/api/dokka/`) и typedoc
  (`docs/api/typedoc-*/`); CI/pre-commit MUST падать при `missing description`.
- **FR-007**: Линтеры ktlint (Kotlin) и ESLint (Vue/JS) MUST запускаться через
  pre-commit hooks (см. `.pre-commit-config.yaml`) и в `./tools/baseline-stats.sh`.
  Известные нарушения фиксируются в per-module baseline-файлах
  (`config/ktlint/baseline-*.xml`, `webvue3/.eslint-baseline.json`,
  `karaoke-public/.eslint-baseline.json`); CI MUST падать на **новые** нарушения
  через `./tools/check-eslint-baseline.sh` и `./gradlew ktlintCheck`. Темп
  сокращения baseline — **≥10%/мес** (SC-002 `spec.md`).
- **FR-009**: При правке кода одной из 9 ключевых подсистем
  (`docs/features/README.md`) разработчик MUST в том же PR обновить
  соответствующий per-feature документ (см. секцию «Контракт per-feature документа»
  в `specs/001-code-standards-docs/contracts/per-feature-doc.md`). Чек-лист
  включается в `.github/PULL_REQUEST_TEMPLATE.md`.
- **Рациональ**: единые стандарты кода снижают bus-factor, ускоряют онбординг и
  делают рефакторинг безопасным. Сокращение baseline — ежемесячная метрика
  качества, отслеживаемая в `tools/baseline-stats.sh` (текущее значение —
  см. `git log -p baseline-*.xml`).

### VII. Cross-Machine Setup (NON-NEGOTIABLE)

> **Контекст.** Phase 002 (PR #27-#30) зафиксировала правила для
> **нескольких разработчиков** с **разными AI-агентами** (opencode / Claude
> Code / Cursor / другие). Эти правила НЕОБХОДИМЫ для согласованной работы
> на разных машинах.

- **VII.1. Локальные AI-конфиги НЕ коммитить.** Персональные файлы
  (`CLAUDE.md`, `.cursorrules`, `.aider*`, `AGENTS.md.local`, `.claude/`)
  MUST быть в `.git/info/exclude` или `~/.gitignore_global`. Только общие
  правила (для всех opencode-сессий) — в `AGENTS.md` (в гите).
  **Рациональ**: личные настройки отличаются у разных разработчиков;
  коммит в master = `git pull` merge conflict + потерянная локальная работа
  (см. PR #29 как пример).
- **VII.2. `.git-blame-ignore-revs`** MUST содержать хэши **всех** коммитов,
  которые меняли сотни файлов без изменения логики (prettier formatting,
  baseline healing, авто-KDoc/JSDoc, документация). После настройки
  `git config blame.ignoreRevsFile .git-blame-ignore-revs` (один раз на машине)
  `git blame` показывает автора оригинальной строки, а не автора рефакторинга.
  **Рациональ**: 7 коммитов Phase 001 затронули 548 файлов (+57K/−28K строк);
  без `.git-blame-ignore-revs` `git blame` показывает шум.
- **VII.3. `.gitattributes`** MUST нормализовать line endings (`* text=auto eol=lf`)
  и помечать бинарные файлы (`*.png binary`, `*.jar binary`). Без этого
  разработчики на Windows получают `git diff` «всё изменилось» в каждом PR.
  **Рациональ**: CRLF→LF нормализация при commit + lock-файлы `-diff`.
- **VII.4. Cross-machine документация** MUST включать как минимум:
  - [`docs/onboarding.md`](../docs/onboarding.md) — общий setup для любого AI-агента.
  - [`docs/claude-code-setup.md`](../docs/claude-code-setup.md) — настройка Claude Code
    (локальный `CLAUDE.md`, FAQ, troubleshooting).
  - [`docs/architecture-notes.md`](../docs/architecture-notes.md) — датированный
    changelog (Pass 1-14), чтобы новый разработчик понимал «почему так, а не иначе».
  **Рациональ**: новый разработчик за 30-60 минут должен привести машину
  в состояние «готова к PR, который пройдёт CI без правок».
- **Рациональ**: Phase 002 зафиксировала, что «общее в гите, персональное
  локально» — единственный масштабируемый подход для команд с разными
  AI-агентами. Без этих правил каждый разработчик изобретает свой setup,
  что ломает consistency и on-call.

### VIII. Секреты и git-гигиена (NON-NEGOTIABLE)

> **Контекст.** Инцидент 2026-08-03: при миграции прода обнаружено, что
> `deploy/.env` (с паролями Postgres, MinIO key/secret, Docker Hub PAT)
> **трекался в публичном git-репозитории github.com/svoemesto/Karaoke
> с мая 2023 года** — более 3 лет. Файл был в `.gitignore`, но был закоммичен
> **до** добавления в `.gitignore`; git продолжает трекать файл, даже если
> он позже добавлен в `.gitignore`. То же самое с `deploy/do.env`,
> `deploy/web-server-deploy/deploy/.env`, `deploy/new_comp/sm-karaoke-system/deploy/.env`.
> Утёкшие секреты: `KaRaOkE-47912130-password` (Postgres prod, активен),
> `minio_key`/`minio_secret` (MinIO, активны), `dckr_pat_SxLnc...` /
> `dckr_pat_p8qXV...` (Docker Hub PAT, старые). Все секреты доступны
> кому угодно в публичной истории git.

- **VIII.1. Секрет-файлы MUST быть в `.gitignore` И НЕ трекаться git.**
  `.gitignore` игнорирует только **ещё не трекаемые** файлы. Если файл
  уже в индексе git — добавление в `.gitignore` НЕ убирает его из трекинга.
  Проверка: `git ls-files deploy/.env` — MUST возвращать пусто. Если
  возвращает путь — файл трекается, срочно `git rm --cached <file>`.

- **VIII.2. Список файл-паттернов, которые MUST быть в `.gitignore`
  и НЕ трекаться git (never commit):**
  - `deploy/.env`, `deploy/do.env` (секреты: пароли БД, MinIO, Docker PAT,
    YOOKASSA, VK)
  - `deploy/web-server-deploy/deploy/.env`,
    `deploy/web-server-deploy/deploy/do.env`
  - `deploy/new_comp/sm-karaoke-system/deploy/.env`,
    `deploy/new_comp/sm-karaoke-system/deploy/do.env`
  - `*.key`, `*.pem`, `*.p12`, `*.pfx` (SSL-сертификаты и приватные ключи)
  - `deploy/ollama_data/`, `dist/`, `node_modules/`
  - `CLAUDE.md`, `.cursorrules`, `.aider*` (см. Principle VII.1)

- **VIII.3. Pre-commit check MUST верифицировать, что ни один секрет-файл
  не попадает в индекс.** Перед каждым `git add` / `git commit`:
  ```bash
  git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$|\.p12$|\.pfx$'
  ```
  MUST возвращать пусто. Если возвращает пути — коммит ЗАПРЕЩЁН,
  сначала `git rm --cached <file>` для каждого.

- **VIII.4. При обнаружении утёкшего секрета в истории git:**
  1. **НЕМЕДЛЕННО** сменить секрет (пароль / ключ / PAT) на новый —
     даже до переписывания истории. Смена секрета — приоритет выше
     очистки истории, потому что переписывание не отменяет того, что
     секрет уже мог быть скопирован.
  2. Убрать файл из индекса: `git rm --cached <file>`.
  3. Проверить `.gitignore` — паттерн MUST присутствовать.
  4. Переписывание истории (`git filter-repo` / BFG) — опционально,
     если репо приватное и доступ ограничен. Если репо публичное —
     **обязательно** после смены всех утёкших секретов (старые значения
     уже невалидны, но переписывание убирает их из клонов/forks).
  5. Зафиксировать инцидент в `docs/architecture-notes.md`.

- **VIII.5. Секреты в коде (hardcoded) ЗАПРЕЩЕНЫ.** IP-адреса серверов,
  пароли, ключи, токены MUST приходить из env-переменных (`@Value`,
  `System.getenv`), не быть захардкожены в `.kt`/`.yml`/`.sh` файлах.
  Дефолты в `${VAR:default}` допустимы, но дефолт MUST быть невалидным
  или публичным значением (доменное имя, localhost), не секретом.
  Проверка: `grep -rE 'password|secret|token|pat' --include='*.kt'`
  MUST возвращать только `@Value`/`System.getenv`/пустые дефолты.

- **Рациональ**: публичный репозиторий с утёкшими паролями = компрометация
  всей инфраструктуры. `.gitignore` без `git rm --cached` = иллюзия
  защиты. Смена секрета после утечки — единственный надёжный путь;
  переписывание истории — косметика (секрет уже мог быть скопирован).

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

**Version**: 2.1.0 | **Ratified**: 2026-07-20 | **Last Amended**: 2026-08-03
