# Component: pre-commit config

> **Домен**: system (infrastructure)
> **Компонента**: локальные pre-commit-хуки `.pre-commit-config.yaml` — 29
> активных хуков, которые прогоняются перед каждым коммитом.

## Назначение

Pre-commit-контур — **первый фильтр** перед коммитом: он ловит нарушения
правил раньше, чем их увидит CI, и даёт разработчику ошибку за секунды, а не
за минуты. Правила, которые он проверяет, — те же, что проверяет CI
(см. [ci-tools.md](ci-tools.md)); разница в том, что guard'ы, требующие
полной истории git или сети, живут только в CI, а локальные проверки
исполняются всегда.

Три слоя защиты от коммита в master:

1. **GitHub branch protection** (server-side, главная защита);
2. **pre-commit-хуки** (client-side, ловят ошибку до push);
3. **CI-джоб `knowledge-structure`** (server-side, финальная сетка).

## Установка и запуск

```bash
pip install pre-commit
pre-commit install            # установить hook в .git/hooks/pre-commit
pre-commit run --all-files    # прогнать все хуки вручную на всех файлах
```

Обход хуков — `git commit --no-verify` (крайний случай).

Общие настройки в `.pre-commit-config.yaml`:

| Параметр | Значение | Зачем |
|---|---|---|
| `fail_fast` | `false` | прогоняются **все** хуки, даже если один упал — разработчик видит полный список проблем за один прогон |
| `default_install_hook_types` | `[pre-commit]` | устанавливается только pre-commit-хук |
| `default_stages` | `[pre-commit]` | хуки работают на стадии `pre-commit` |

## Каталог хуков (29 активных)

Все хуки — `repo: local`, `language: system`. Скрипты из `tools/` вызываются
с флагом `--quiet`: pre-commit показывает только код возврата, без деталей.

### Guard-хуки (Pass 372–375)

| Хук | Скрипт | Что проверяет |
|---|---|---|
| `gradle-user-home-guard` | `tools/check-gradle-user-home.sh` | `GRADLE_USER_HOME` задан и указывает на writable-каталог; если не задан — проектный `<repo-root>/.gradle` должен существовать и быть writable. `JAVA_HOME` не задан — warning, не failure |
| `docker-config-guard` | `tools/check-docker-config.sh` | `DOCKER_CONFIG` задан и указывает на writable-каталог; иначе проектный `<repo-root>/.docker` должен существовать и быть writable |
| `container-restart-guard` | `tools/check-container-restart.sh` | прямые `docker compose … restart`, `docker start <c>`, `docker restart -t 30 <c>` вместо `bash deploy/do.sh restart_*` |
| `frontend-build-guard` | `tools/check-frontend-build.sh` | `npm run <cmd>` без `cd webvue3` / `cd karaoke-public` |

Ожидаемые пути `GRADLE_USER_HOME` и `DOCKER_CONFIG` **не зашиты** как
`/home/nsa/Karaoke/...`: они выводятся от корня репозитория
(`git rev-parse --path-format=absolute --git-common-dir`). На второй машине
матрицы (`dev-pc`/`dev`) такого каталога нет, и зашитый путь ронял хук на
каждом коммите. Переопределение — переменные `KARAOKE_GRADLE_USER_HOME` /
`KARAOKE_DOCKER_CONFIG`; по умолчанию — `<main-repo-root>/.gradle` и
`<main-repo-root>/.docker`.

Тот же принцип действует для хука `ktlint`: он сам вызывает `./gradlew`,
поэтому **обязан** передать `GRADLE_USER_HOME`, вычисленный от корня
репозитория.

### Guard'ы архитектурных правил

| Хук | Скрипт | Что проверяет | Файлы, на которых срабатывает |
|---|---|---|---|
| `docker-image-tags-guard` | `tools/check-docker-image-tags.sh` | R-04/R-05: теги базовых образов (`nginx:stable`, `node:22-alpine`) | всегда |
| `no-jpa-imports-guard` | `tools/check-no-jpa-imports.sh` | R-07: запрет импортов JPA/Hibernate | всегда |
| `no-mp4-mentions-guard` | `tools/check-no-mp4-mentions.sh` | R-11: запрет упоминаний mp4/скачивания | всегда |
| `subagent-isolation-guard` | `tools/check-subagent-isolation.sh` | изоляция worktree для параллельных субагентов; fail-closed при неразрешённом git-ref | всегда |
| `security-headers-guard` | `tools/check-security-headers.py` | заголовки безопасности в прод-nginx | всегда |
| `recordhash-consistency-guard` | `tools/check-recordhash-consistency.py` | контракт recordhash (sync-движок сравнивает строки только по recordhash) | всегда |
| `confirm-escape-guard` | `tools/check-confirm-escape.py` | R1/R2/R3 экранирования текста подтверждений | `webvue3/src/**`, сам guard, `config/frontend/confirm-safe-expressions.txt` |
| `secret-in-argv-guard` | `tools/check-secret-in-argv.sh` | секрет не передаётся через `argv` | `deploy/**`, `tools/**`, `specs/**` (`.sh`, `.bash`) + сам baseline |
| `gradle-wrapper-guard` | `tools/check-gradle-wrapper.sh` | единая версия gradle-wrapper ≥ 9.8.2 + `distributionSha256Sum` | `*/gradle/wrapper/gradle-wrapper.properties` |
| `build-config-guard` | `tools/check-build-config.sh` | Jackson без явных версий, мёртвый корневой build-конфиг, дампы кучи | корневые и модульные build-файлы, `gradle.properties`, корневой Maven-дескрипт, `.idea/misc.xml`, `tools/gradle-heap-dump.sh` |
| `marker-escape-guard` | `tools/check-marker-escape-guard.sh` | тест экранирования метки комментария «взведён» и его skip-состояние соответствует production-коду | редактор караоке, ReviewModal, тестовые файлы, фикстуры `tools/fixtures/marker-escape/` |
| `check-value-placeholders` | `tools/check-value-placeholders.sh` | `@Value` не теряет плейсхолдер | `karaoke-web/src/**`, `karaoke-app/src/**` |
| `songedit-field-coverage` | `tools/check-songedit-field-coverage.sh` | пара `SongEdit.vue` ↔ `/api/song/update` | `SongEdit.vue`, `ApiController.kt`, `SongUpdateMapper.kt`, whitelist |
| `endpoint-field-coverage` | `tools/check-endpoint-field-coverage.sh` | все пары UI ↔ backend из `tools/endpoint-pairs.yml` | `webvue3/src/**`, `karaoke-public/src/**`, `karaoke-app/src/**`, `tools/endpoint-pairs.yml` |

### Линтеры и формат

| Хук | Команда | Файлы |
|---|---|---|
| `ktlint` | `./gradlew ktlintCheck` (с `GRADLE_USER_HOME` от корня репозитория) | Kotlin/Java |
| `eslint-webvue3` | `cd webvue3 && npm run lint:check` | `webvue3/**` |
| `eslint-karaoke-public` | `cd karaoke-public && npm run lint:check` | `karaoke-public/**` |
| `prettier-webvue3` | `cd webvue3 && npx prettier --check "src/**/*.{vue,js,ts,json}"` | `webvue3/**` |
| `prettier-karaoke-public` | `cd karaoke-public && npx prettier --check "src/**/*.{vue,js,ts,json}"` | `karaoke-public/**` |

### Тесты

| Хук | Команда | Файлы |
|---|---|---|
| `webvue3-unit-tests` | `cd webvue3 && npm run test:unit` | `webvue3/src/**` |
| `karaoke-public-unit-tests` | `cd karaoke-public && npm run test:unit` | `karaoke-public/src/**` |

Тесты публичного SPA (`src/**/__tests__/*.test.js`) запускаются и в хуке, и в
CI-джобе `karaoke-public-unit-tests` — регрессия не проходит молча ни на
одном контуре.

### Документация

| Хук | Команда | Файлы |
|---|---|---|
| `verify-doc-links` | `./tools/verify-doc-links.sh` (lychee) | `*.md`, `*.markdown` |
| `check-feature-doc` | `bash -c './tools/check-feature-doc.sh docs/features/*.md archive/docs/features/*.md'` | `docs/features/**`, `archive/docs/features/**` |
| `check-doc-references` | `python3 tools/check-doc-references.py` | `knowledge/**`, `docs/**`, `archive/docs/**` |

`check-feature-doc` проверяет **оба** каталога — живые `docs/features/` и
архив `archive/docs/features/`. Раньше хук срабатывал только на архиве, и
нарушения FR-009 в живых per-feature документах не ловились ни локально, ни в
CI.

### Git-гигиена

| Хук | Скрипт | Что проверяет |
|---|---|---|
| `block-master-commit` | `tools/git-hooks/pre-commit-block-master.sh` | прямой `git commit` в `master`/`main`; срабатывает всегда, независимо от изменённых файлов |

## Как добавить новый хук

Хук — часть **полной** регистрации guard'а (R-47): скрипт в `tools/`, джоб в
`.github/workflows/lint.yml`, pre-commit-хук и `--self-test`. Хук без джоба в CI
не закрывает правило — контур остаётся локальным.

1. Скрипт — в `tools/`, с `--quiet` (только код возврата) и, если он что-то
   проверяет, с `--self-test`.
2. Хук — в `.pre-commit-config.yaml`, блок `hooks:` репозитория `local`:
   ```yaml
   - id: <короткое-имя>
     name: <человекочитаемое имя>
     entry: bash tools/check-<имя>.sh --quiet
     language: system
     pass_filenames: false
     always_run: true        # если проверка не зависит от файлов
     files: '<регулярка по путям>'   # если проверка зависит от файлов
     stages: [pre-commit]
   ```
   - `pass_filenames: false` — обязательно для скриптов, которые сами
     определяют, что проверять;
   - `always_run: true` — для проверок, не зависящих от изменённых файлов
     (gradle-user-home, docker-config, container-restart, frontend-build,
     docker-image-tags, no-jpa-imports, no-mp4-mentions, subagent-isolation,
     security-headers, recordhash-consistency, block-master-commit);
   - `files:` — регулярка по путям, если guard работает по изменениям.
3. Тот же скрипт добавляется в CI отдельным джобом в
   `.github/workflows/lint.yml` — локальная проверка не заменяет серверную.
4. Новый хук и новый джоб фиксируются в [ci-tools.md](ci-tools.md) — там
   таблица guard'ов.

## Известные ловушки

1. **Зашитый путь к машине.** `/home/nsa/Karaoke/...` в guard'е или в hook'е
   роняет проверку на второй машине матрицы (`dev-pc`/`dev`). Пути выводятся
   от корня репозитория; переопределение — `KARAOKE_GRADLE_USER_HOME` /
   `KARAOKE_DOCKER_CONFIG`.
2. **Хук, который выводит подробности, забивает вывод коммита.** Хуки
   вызывают guard'ы с `--quiet` — детали остаются в stdout самого guard'а при
   ручном запуске.
3. **`pass_filenames: true` у скрипта, который сам обходит файлы.** Guard
   получит список файлов в argv и, скорее всего, обработает не то.
4. **Проверка только на архиве.** Хук должен смотреть **все** актуальные
   каталоги, иначе нарушения в живых документах не ловятся.
5. **`fail_fast: true` прячет остальные проблемы.** Оставлен `false`: один
   прогон — полный список.
6. **Обход хуков (`--no-verify`) бездумно.** Хуки — первый фильтр; их
   отключение отключает и проверку.
7. **Хук, который ловит не то.** `container-restart-guard` и
   `frontend-build-guard` проверяют только `.sh`/`.bash`, `deploy/do.sh` и
   `.github/workflows/` — в Markdown и ADR встречаются примеры команд, и
   их сквозная проверка дала бы ложные срабатывания.

## Связь с другими компонентами

- **[ci-tools.md](ci-tools.md)** — guard'ы, контракт кодов возврата, CI-джобы.
- **[governance-docs.md](governance-docs.md)** — процессные документы
  (`AGENTS.md`, `CONTRIBUTING.md`), правила которых проверяют хуки.
- **[../../product/README.md](../../product/README.md)** — индекс продуктовой
  ветки: структура per-feature документов (проверяет хук `check-feature-doc`);
  шаблон профиля B — [../../templates/product-feature.md](../../templates/product-feature.md).
- **[do-sh.md](do-sh.md)** — `deploy/do.sh`, к которому обращаются
  container-restart-guard и frontend-build-guard.
- **[security-headers.md](../../guidelines/security-headers.md)** — регламент
  заголовков безопасности (хук `security-headers-guard`).
- **[architecture-conventions.md](../../guidelines/architecture-conventions.md)** —
  правила R-04, R-05, R-07, R-08, R-11, R-46, за которыми стоят хуки;
  **R-47** — регистрация guard'а целиком (скрипт + джоб + хук + `--self-test`)
  и контрактный прогон всех `run:`-команд CI перед PR; **R-48** — общее
  правило живёт в общем модуле, а не дублируется по компонентам.
- **[karaoke-public-tests.md](../frontend/karaoke-public-tests.md)** —
  тесты публичного SPA, которые запускает хук `karaoke-public-unit-tests`.
