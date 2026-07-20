<!--
  Sync Impact Report
  - Version change: 1.0.0 → 1.1.0 (amendment: добавлен Принцип VI «Code Standards»)
  - Modified principles: none (Principle I-V без изменений)
  - Added sections: Core Principle VI «Code Standards (NON-NEGOTIABLE)» — MUST
    ktlint/eslint baseline-процесс, MUST KDoc/JSDoc на публичных API, MUST
    per-feature документ при правке кода фичи (FR-009).
  - Removed sections: none
  - Templates requiring updates:
      .specify/templates/plan-template.md   ✅ aligned (Constitution Check 6/6)
      .specify/templates/spec-template.md   ✅ aligned (FR-006/FR-009 references)
      .specify/templates/tasks-template.md  ✅ aligned (Phase 6 KDoc/JSDoc + Phase 7 Polish)
      .specify/templates/checklist-template.md ✅ aligned (FR-009 verification gate)
  - Follow-up TODOs:
      - detekt (после выхода версии с поддержкой Kotlin 2.2) — см. T049
      - typedoc-plugin-vue (для парсинга .vue single-file components) — backlog
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
2. Деплоить на сервер (`deploy_web.sh`, `deploy_public.sh`, rsync на `<PROD_SERVER_IP>`,
   прямые DDL/DML к серверной БД) — только по прямому согласию пользователя, на
   каждое действие отдельно.
3. Редактировать файлы на сервере напрямую.
4. Перезаписывать `deploy/do.env` (содержит секреты).
5. Коммитить `deploy/ollama_data/`, `dist/`, `node_modules/`, `deploy/.env`,
   `deploy/do.env` и любые другие секрет-файлы.
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
  коротко и по существу, в стиле `area: краткое описание`.
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
   в начале `constitution.md` при каждом amend.
6. **Compliance-проверка в production** (на стороне пользователя, не агента):
   - после `deploy_web.sh` — логи push **не** содержат `EOF`/`400 Bad request`,
     на сервере `Status: Downloaded newer image`;
   - nginx reload только после `nginx -t`;
   - `docker exec karaoke-web env | grep <VAR>` для проверки реально прокинутых
     env-переменных.

**Version**: 1.1.0 | **Ratified**: 2026-07-20 | **Last Amended**: 2026-07-20
