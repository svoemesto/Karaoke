# Component: контур проверок (guard'ы, CI, pre-commit)

> **Домен**: system (infrastructure)
> **Компонента**: guard'ы `tools/`, CI-джобы `.github/workflows/lint.yml`,
> pre-commit-хуки `.pre-commit-config.yaml` и baseline-файлы, на которых они
> держатся.

## Назначение

Контур проверок отвечает на два вопроса:

1. **Нарушено ли правило?** — если да, коммит не должен уйти в master.
2. **Проверка вообще выполнилась?** — если нет, это тоже провал, а не «всё хорошо».

Состав контура:

| Слой | Где живёт | Объём |
|---|---|---|
| Guard'ы | `tools/check-*.sh`, `tools/check-*.py` | 39 скриптов |
| CI-джобы | `.github/workflows/lint.yml` (GitHub) и `.forgejo/workflows/lint.yml` (Forgejo) | 30 джобов |
| Pre-commit-хуки | `.pre-commit-config.yaml` | 29 активных хуков |
| Baseline | `tools/*.baseline` | 5 файлов — известные допустимые нарушения |

Операционные скрипты контура (`tools/tracker*.sh`, `tools/generate-*.sh`,
`tools/install-tracker.sh`, `tools/auto-kdoc.py`) в CI не вызываются — они
работают по вызову человека.

## Где исполняется CI (с 2026-10-27)

- **Workflow-файлы**: `.github/workflows/lint.yml` — для GitHub-зеркала;
  `.forgejo/workflows/lint.yml` — копия для Forgejo с правками под окружение
  (установка `jq`, абсолютные URL действий, снятый `concurrency`).
- **Исполнение**: Forgejo Actions; раннер на `nsa-i9` в docker-режиме
  (`node:22-bookworm`), `docker.sock` монтируется в job-контейнеры для e2e.
  Раннер зарегистрирован на репозиторий, не на инстанс.
- **На infra-сервере раннер не живёт**: job-контейнер с `docker.sock` — это
  root на хосте, а рядом данные Forgejo, Plane и Harbor.
- **Серверные правила** — только лёгкие: branch protection (прямой push в
  `master` отклоняется pre-receive hook'ом), обязательное ревью, защита тегов.
  Статус-чеки **не** обязательны для merge, иначе выключенный `nsa-i9`
  блокирует работу.
- **Автозапуск раннера**: cron `@reboot` + watchdog каждые 5 минут; ручной
  запуск — `start.sh` в каталоге раннера.

## Границы контура

**Контур ловит** то, что выражается в тексте репозитория:

- конфигурация сборки (версии Gradle, Jackson, дампы кучи, мёртвые дубли
  build-конфига);
- запреты на уровне импортов и текста (JPA/Hibernate, mp4/скачивание);
- соответствие файлов правилам (права доступа, секреты в argv, `@Value`
  плейсхолдеры, заголовки безопасности в nginx);
- покрытие полей UI ↔ backend, структура per-feature документов, целостность
  SSoT (`knowledge/` ↔ код);
- Git-гигиена: прямые коммиты в master, плавающие `uses:` в workflow.

**Контур НЕ ловит** (это зона code review и владельца):

- правила, требующие понимания смысла кода: идемпотентность санитайзера
  (R-08), `redirectErrorStream` (R-43), корректность MLT/melt-рендера (R-44),
  выбор версии Gradle (переход на 9.x — решение владельца);
- архитектурные решения и их обоснование — это ADR
  (`knowledge/adr/`) и ревью, а не grep;
- «зелёный CI» ≠ «правильный продукт»: e2e публичного SPA поднимается без
  бэкенда, поэтому полный стек в CI не проверяется.

Границы зафиксированы в
[architecture-conventions.md](../../guidelines/architecture-conventions.md) —
раздел «Ловушки» и правила R-04, R-05, R-07, R-11, R-46, **R-47** (контрактный
прогон проверок перед PR) и **R-48** (правило, повторяющееся в компонентах,
живёт в общем модуле — поэтому, например, адрес источника запроса считает
единственный `util/ClientIpResolver`, а не своя копия правила в каждом
интерцепторе).

## Контракт кодов возврата

Единый контракт для всех guard'ов:

| Код | Значение | Что это значит для гейта |
|---|---|---|
| `0` | успех | проверка выполнилась, нарушений нет |
| `1` | нарушение | правило нарушено, нарушение найдено и показано |
| `2` | проверка не выполнена | данных не хватило: нет ref'а, нет доступа, инструмент упал |

**Правило: guard, который не смог выполнить проверку, обязан вернуть ненулевой
код.** Отсюда следствие, которое запрещено нарушать: **«нет данных» ≠ «нет
нарушений»**. Отсутствие данных — это код `2`, а не «зелёный» результат.

Типичные случаи кода `2`: недоступен `git`-ref (`check-direct-commits.sh`),
не разобран YAML-файл пар (`check-endpoint-field-coverage.sh`), не найден
guard для проверки результата (`auto-kdoc.py`), счётчики оказались не числами
(`check-enforcement.sh`).

Инструменты операционного контура используют собственные, но столь же
однозначные коды — например `tools/tracker-poll.sh`: `0` — есть задачи, `1` —
всё ОК, `2` — OpenProject недоступен, `3` — токен истёк.

## Контрактный прогон перед PR (R-47)

Проверки пакета **не равны** CI: пакетный набор (тесты модуля, его lint,
prettier) — дополнительный слой, а не замена CI-набору. Перед открытием PR
прогоняются **все** `run:`-команды из `.github/workflows/lint.yml`, и список
команд **извлекается из воркфлоу** (парсинг `run:`), а не по памяти: воркфлоу
меняется — память нет.

```bash
python3 - <<'PY' > /tmp/ci_runs.txt
import re, pathlib
s = pathlib.Path('.github/workflows/lint.yml').read_text(encoding='utf-8')
for m in re.finditer(r'^\s*-?\s*run:\s*(.+)$', s, re.M):
    cmd = m.group(1).strip()
    if cmd.startswith(('bash tools/', 'python3 tools/', 'python tools/', './tools/')):
        print(cmd)
PY
while read -r cmd; do cmd="${cmd//\$\{\{ matrix.spa \}\}/webvue3}"; \
  eval "$cmd" >/tmp/g.out 2>&1 || echo "НЕ-НУЛЕВОЙ: $cmd"; done < /tmp/ci_runs.txt
```

Список `run:`-команд извлекается парсингом воркфлоу, а не перечислением по
памяти: иначе новая проверка в CI не попадёт в локальный прогон.

## Прогон backend-тестов и лимит соединений БД

Тестовые потоки **тоже** оставляют JDBC-соединения — ровно так же, как фоновые
операции приложения: `KaraokeConnection` кэширует физическое соединение в
`ThreadLocal` потока, и поток, который умер, не закрывает его (см.
[домен persistence](../../domains/persistence/domain.md), § «Соединение на
поток», и [local-ADR-0009](../../adr/local-0009-thread-local-connection-lifecycle.md)).

Практическое следствие: длинный прогон backend-тестов исчерпывает лимит
соединений локальной БД, и тест падает с `FATAL: sorry, too many clients
already` — не по своей причине. Именно это наблюдалось на локальной БД с
пределом в 100 соединений во время длинных прогонов (BR-627, аудит #336,
OpenProject #410).

**Требуемый запас по лимиту соединений**: `max_connections=300`
(переопределяется `DB_MAX_CONNECTIONS`; параметр задан в
`docker-compose-database.yml` и `prod-single-host/docker-compose-database.yml`).
Для тестовой БД запас должен быть не меньше.

**Перед длинным прогоном** — проверить состояние БД:

```sql
SELECT count(*) FROM pg_stat_activity;
```

Сравнить с `max_connections`. Если число занятых соединений близко к лимиту —
сначала освободить висящие соединения (перезапустить стенд или поднять лимит),
и только потом запускать прогон.

**Регламент прогона**:

1. `GRADLE_USER_HOME` и `DOCKER_CONFIG` — от корня репозитория
   (AGENTS.md, Pass 372/373).
2. Backend-тесты запускаются с `env -u DISPLAY` — иначе падают 6 тестов.
3. `./gradlew … --max-workers=2 -Dorg.gradle.jvmargs="-Xmx1536m
   -XX:MaxMetaspaceSize=512m" -Djava.awt.headless=true`.
4. После rebase или чистки — `--rerun-tasks`, иначе Gradle покажет UP-TO-DATE
   и спрячет ошибки компиляции.
5. Перед длинным прогоном — проверить число занятых соединений (см. выше) и
   убедиться, что запас по лимиту есть.

## Конвенция `--self-test`

Guard, который что-то проверяет, обязан уметь проверить **сам себя**:
`bash tools/check-<name>.sh --self-test` (для Python-guard'ов —
`python3 tools/check-<name>.py --self-test`). Самопроверка поднимает временный
репозиторий (или копию дерева) и проверяет две фикстуры:

- «корректное дерево» → скрипт завершается с кодом `0`;
- «подсовытое нарушение» → скрипт завершается с **ненулевым** кодом.

Третья обязательная фикстура — «проверка не выполнилась» (недоступный ref,
битый YAML): ожидается код `2`, а не `0`. Без третьей фикстуры guard, который
умеет только «падать», выглядит рабочим, пока данные недоступны.

`--self-test` есть у guard'ов: `tools/check-actions-pinned.sh`,
`tools/check-build-config.sh`, `tools/check-container-restart.sh`,
`tools/check-deploy-hardening.sh`, `tools/check-direct-commits.sh`,
`tools/check-endpoint-field-coverage.sh`, `tools/check-enforcement.sh`,
`tools/check-file-permissions.sh`, `tools/check-frontend-build.sh`,
`tools/check-gradle-wrapper.sh`, `tools/check-secret-in-argv.sh`,
`tools/check-spec-issue-link.py`.

## Таблица guard'ов

Коды в последней колонке — по контракту выше: `0` успех, `1` нарушение,
`2` проверка не выполнена.

### Инфраструктура сборки и runtime

| Guard | Что проверяет | Где вызывается | Коды |
|---|---|---|---|
| `tools/check-gradle-wrapper.sh` | все три gradle-wrapper'а (`gradle/`, `karaoke-app/`, `karaoke-web/`) указывают на **одну** версию дистрибутива не ниже `9.8.2`, и в каждом есть `distributionSha256Sum`, совпадающая с официально опубликованной | CI-джоб `build-config-guard`; pre-commit-хуки `gradle-wrapper-guard` | 0 / 1 / 2 |
| `tools/check-build-config.sh` | в build-файлах нет явных версий `com.fasterxml.jackson.*` (версию задаёт BOM Spring Boot 3.5.6 → 2.19.2); в корневом `build.gradle.kts` нет мёртвого блока `dependencies`; в корне репозитория нет `pom.xml` и ссылок на него в `.idea/misc.xml`; в `org.gradle.jvmargs` нет `-XX:+HeapDumpOnOutOfMemoryError` | CI-джоб `build-config-guard`; pre-commit-хуки `build-config-guard` | 0 / 1 / 2 |
| `tools/gradle-heap-dump.sh` | opt-in дамп кучи: `umask 077`, каталог `0700` внутри `GRADLE_USER_HOME`, файл дампа `0600` (в общем `/tmp` дамп был доступен любому пользователю, а в нём — карта `System.getenv()`) | CI-джоб `build-config-guard` | 0 / 1 / 2 |
| `tools/check-gradle-user-home.sh` | `GRADLE_USER_HOME` указывает на **writable** каталог; путь выводится от корня репозитория, без хардкода пути машины | pre-commit-хуки `gradle-user-home-guard` | 0 / 1 / 2 |
| `tools/check-docker-config.sh` | `DOCKER_CONFIG` указывает на writable-каталог; путь — от корня репозитория | pre-commit-хуки `docker-config-guard` | 0 / 1 / 2 |
| `tools/check-docker-image-tags.sh` | R-04/R-05: `FROM nginx:` и `FROM node:` только из whitelist (`nginx:stable`, `node:22-alpine`); запрещены `nginx:alpine`, `nginx:latest`, `node:alpine`, `node:latest`, `node:lts`; игнорируются backup-файлы (`DockerfileOld`, `DockerfileBackup`, `DockerfileFull`, `DockerfileDemucs`) | CI-джоб `docker-image-tags-guard`; pre-commit-хуки `docker-image-tags-guard` | 0 / 1 / 2 |
| `tools/check-deploy-hardening.sh` | hardening-конфигурация deploy (проверяет, в том числе, что `do.sh` проверяет код возврата `docker image push` и не считает «Image is up to date» без проверки) | вручную; в CI не вызывается | 0 / 1 / 2 |
| `tools/check-container-restart.sh` | прямые `docker compose … restart`, `docker start <c>`, `docker restart -t 30 <c>` вместо `bash deploy/do.sh restart_*`; проверяет только `.sh`/`.bash`, `deploy/do.sh` и `.github/workflows/` (в Markdown и ADR бывают примеры) | pre-commit-хуки `container-restart-guard` | 0 / 1 / 2 |
| `tools/check-frontend-build.sh` | `npm run <cmd>` без `cd webvue3` / `cd karaoke-public`; контекст нарушения берётся из **полного содержимого файла**, номера строк — из diff'а (контекст не выдумывается) | pre-commit-хуки `frontend-build-guard` | 0 / 1 / 2 |

### Правила кода и текста

| Guard | Что проверяет | Где вызывается | Коды |
|---|---|---|---|
| `tools/check-no-jpa-imports.sh` | R-07: запрет импортов `org.springframework.data.jpa` / `javax.persistence` / `hibernate` в `.kt`/`.java` файлах `karaoke-app/src/main` и `karaoke-web/src/main` (тесты и build-артефакты исключены) | CI-джоб `no-jpa-imports-guard`; pre-commit-хуки `no-jpa-imports-guard` | 0 / 1 / 2 |
| `tools/check-no-mp4-mentions.sh` | R-11: запрет упоминаний mp4 и «скачивания» в production-коде (`webvue3`, `karaoke-public`, `karaoke-app`, `karaoke-web`); baseline сопоставляет записи **по содержимому строки**, а не по номеру | CI-джоб `no-mp4-mentions-guard`; pre-commit-хуки `no-mp4-mentions-guard` | 0 / 1 / 2 |
| `tools/check-value-placeholders.sh` | `@Value` не теряет плейсхолдер: `@Value("$$"{свойство}")` без `$` внутри кавычек компилируется в литерал и Spring внедряет текст | CI-джоб `value-placeholders-guard`; pre-commit-хуки `check-value-placeholders` | 0 / 1 |
| `tools/check-secret-in-argv.sh` | секрет не уходит в `argv` (виден любому пользователю машины через `ps -eo args` и `/proc/<pid>/cmdline`): `docker exec -e PGPASSWORD=…`, `docker login --password …`, `curl -H "Authorization: …"`, `curl -u user:pass`, пароль в строке подключения и в URL. Безопасные формы (PGPASSWORD в окружении, `--password-stdin`, `curl --config -`, `.pgpass`) нарушением не считаются | CI-джоб `secret-in-argv-guard`; pre-commit-хуки `secret-in-argv-guard` | 0 / 1 / 2 |
| `tools/check-file-permissions.sh` | world-writable права на файлах репозитория | CI-джоб `file-permissions-guard` | 0 / 1 / 2 |
| `tools/check-root-junk.sh` | артефакты редиректов (мусор) в корне репозитория | CI-джоб `root-junk-guard` | 0 / 1 |
| `tools/check-no-personal-fixtures.sh` | персональные фикстуры и зашитые учётные данные в репозитории | CI-джоб `personal-fixtures-guard` | 0 / 1 / 2 |
| `tools/check-security-headers.py` | каждый server-блок прод-nginx объявляет `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy` (для TLS-сервера — ещё `Strict-Transport-Security`) с флагом `always`; location'ы со своими `add_header` объявляют нужные заголовки явно либо помечены исключением с причиной | CI-джоб `security-headers-guard`; pre-commit-хуки `security-headers-guard` | 0 / 1 / 2 |
| `tools/check-recordhash-consistency.py` | контракт recordhash: «голое» имя колонки в теле триггерной функции; колонка модели, не попавшая в md5; колонка из `Song.getSqlToInsert(sync=true)`, которой нет в `tbl_songs_sync` | CI-джоб `knowledge-structure`; pre-commit-хуки `recordhash-consistency-guard` | 0 / 1 / 2 |
| `tools/check-confirm-escape.py` | экранирование текста подтверждений: R1 — компонент не принимает сырую разметку по умолчанию; R2 — `allowHtml: true` объявлен тогда и только тогда, когда в тексте есть статичная разметка; R3 — пользовательские значения экранированы | CI-джоб `confirm-escape-guard`; pre-commit-хуки `confirm-escape-guard` | 0 / 1 |
| `tools/check-marker-escape-guard.sh` | guard «до/после» для регресс-теста экранирования метки комментария: тест **взведён** (падает на уязвимом снимке кода, проходит на исправленном) и его skip-состояние соответствует состоянию production-кода. Фикстуры — `tools/fixtures/marker-escape/` | CI-джоб `marker-escape-guard`; pre-commit-хуки `marker-escape-guard` | 0 / 1 |

### Покрытие контрактов UI ↔ backend

| Guard | Что проверяет | Где вызывается | Коды |
|---|---|---|---|
| `tools/check-endpoint-field-coverage.sh` | все пары UI ↔ backend из `tools/endpoint-pairs.yml`: пары с отсутствующим component/controller/method или без v-model полей **не пропускаются**, а список поддерживаемых полей строится из контроллера **этой** пары, а не из единственного `SongUpdateMapper.kt` | pre-commit-хуки `endpoint-field-coverage` | 0 / 1 / 2 |
| `tools/check-songedit-field-coverage.sh` | пара `SongEdit.vue` ↔ `/api/song/update` — поля UI есть в backend-модели | pre-commit-хуки `songedit-field-coverage` | 0 / 1 / 2 |

### Документация и SSoT

| Guard | Что проверяет | Где вызывается | Коды |
|---|---|---|---|
| `tools/check-feature-doc.sh` | 6 обязательных секций per-feature документа, slug == имя файла, Status ∈ {active, deprecated, experimental}; **отсутствующий документ — ошибка** (код `1`, а не `ERROR` в stdout при exit `0`); проверяются оба каталога — живые `docs/features/` и архив `archive/docs/features/` | CI-джоб `docs-lint`; pre-commit-хуки `check-feature-doc` | 0 / 1 / 2 |
| `tools/check-knowledge-structure.sh` | 9 структурных проверок `knowledge/`: обязательные директории и README'и, ≥ 1 ADR, cross-link L1 ↔ L2 ↔ L3, frontmatter в `domain.md` | CI-джоб `knowledge-structure` | 0 / 1 |
| `tools/check-knowledge-cross-links.sh` | относительные ссылки внутри `knowledge/` (плейсолдеры `templates/`, `system/01-*`, `system/02-*`, `adr/` исключены) | CI-джоб `knowledge-structure` | 0 / 1 |
| `tools/lint-knowledge.py` | стиль и структура: эмодзи запрещены, обязательные секции по шаблону, структурный аудит доменов | CI-джоб `knowledge-structure` | 0 / 1 |
| `tools/check-doc-references.py` | относительные `.md`-ссылки, пути к файлам кода в backticks и `/api`-эндпоинты в `knowledge/`, `docs/`, `archive/docs/`, `.github/`; осознанные исключения — в `config/knowledge/doc-references-whitelist.txt` | CI-джоб `knowledge-structure`; pre-commit-хуки `check-doc-references` | 0 / 1 |
| `tools/check-ssot-impact.py` | изменения кода сопровождаются синхронным изменением `knowledge/` (карта `.ssot-map.yml`) | CI-джоб `ssot-impact` | 0 / 1 |
| `tools/check-kdoc-coverage.sh` | покрытие KDoc (порог 50%) | CI-джоб `kdoc-coverage` | 0 / 1 |
| `tools/check-jsdoc-coverage.sh` | покрытие JSDoc (порог 50%) | CI-джоб `jsdoc-coverage` | 0 / 1 |
| `tools/check-spec-issue-link.py` | связь спеки ↔ OpenProject issue: Issue ID извлекается в обоих форматах; регулярка не даёт ложных срабатываний на словах «опубликация», «operation», «drop» | CI-джоб `knowledge-structure` | 0 / 1 |
| `tools/verify-doc-links.sh` | lychee — офлайн-проверка ссылок в документации; в CI проверяются живые per-feature документы `docs/features/`, архив `archive/docs/features/` и `CONTRIBUTING.md` | pre-commit-хуки `verify-doc-links`; CI-джоб `docs-lint` (lychee-action, `--offline`) | 0 / 1 |
| `tools/check-no-legacy-tbl-settings.sh` | устаревшее имя таблицы `tbl_settings` (6 секций: [1/6] новые миграции `deploy/karaoke-db/`, [2/6] recordhash-файлы `deploy/recordhash_*.sql`, [3/6] Kotlin-файлы, [4/6] code-block'и SQL в `specs/NNN-*/`, [5/6] code-block'и SQL в `docs/features/*.md`, [6/6] легаси-документация `karaoke-app/.../docs/*.sql`). Секция [6/6] требует действующее имя таблицы песен — `tbl_songs` (миграция 28), а не `tbl_settings` | CI-джоб `knowledge-structure` | 0 / 1 |
| `tools/check-enforcement.sh` | MUST-правила из `CONTRIBUTING.md` (`enforcedBy` ≠ code-review-only) покрыты baseline'ом или дают 0 нарушений при текущем прогоне; подсчёт переносимый (POSIX ERE, без PCRE) | вручную; в CI не вызывается | 0 / 1 / 2 |
| `tools/check-audit-coverage.sh` | покрытие аудитом по реестру аудита | вручную; в CI не вызывается | 0 / 1 |

### Git-гигиена и CI-конфигурация

| Guard | Что проверяет | Где вызывается | Коды |
|---|---|---|---|
| `tools/check-direct-commits.sh` | в master — только merge-коммиты (результат merge PR); коммит с **одним** родителем в first-parent-цепочке — прямой коммит. Считает через `git log --first-parent` с разбором числа родителей — без off-by-one. Известные исторические нарушения — в `tools/check-direct-commits.baseline`; новые в baseline не добавляются | CI-джоб `knowledge-structure` | 0 / 1 / 2 |
| `tools/check-actions-pinned.sh` | каждый сторонний `uses:` в `.github/workflows/*.yml` указывает **полный SHA коммита** (40 hex) с комментарием версии (`# vX.Y.Z`): плавающий тег (`@v4`) можно перенаправить на другой коммит в чужом репозитории, и CI исполнит новый код без изменения нашего репозитория и без ревью. Фикстуры «до/после» — `tools/fixtures/actions-pinned/` | CI-джоб `actions-pinned-guard` | 0 / 1 / 2 |
| `tools/git-hooks/pre-commit-block-master.sh` | прямой `git commit` в `master`/`main` — client-side слой защиты | pre-commit-хуки `block-master-commit` | 0 / 1 |

### Frontend-гейты

| Guard | Что проверяет | Где вызывается | Коды |
|---|---|---|---|
| `tools/check-eslint-baseline.sh` | ESLint по baseline: различает «eslint нашёл нарушения» (`1`) и «eslint упал» (≥ `2`), stderr пишется в лог и не глушится; ключ baseline устойчив к сдвигу строк и к другой машине | CI-джоб `frontend-lint` | 0 / 1 / ≥ 2 |
| `tools/generate-eslint-baseline.sh` | пересборка baseline: пишет файл через `mktemp` + `mv` (а не частично), различает «нарушения» (`1`) и «ошибка eslint» (≥ `2`) | вручную | 0 / 1 / ≥ 2 |
| `karaoke-public` / `webvue3` unit-тесты | `npm run test:unit` в обоих SPA (node --test) | CI-джобы `webvue3-unit-tests`, `karaoke-public-unit-tests`; pre-commit-хуки `webvue3-unit-tests`, `karaoke-public-unit-tests` | 0 / 1 |
| `karaoke-public-e2e` | Playwright: блокирующие тесты (`--grep-invert @smoke`) и отдельно дымовые (`--grep @smoke`, `continue-on-error: true`); контейнер поднимается без бэкенда | CI-джоб `karaoke-public-e2e` | 0 / 1 |
| CI-джоб `frontend-build` | `npm run build` для `webvue3` и `karaoke-public`: без этого шага PR с не собирающимся SPA проходил CI и мержился в master | CI-джоб `frontend-build` | 0 / 1 |

## Baseline

Baseline — список **известных и сознательно оставленных** нарушений, чтобы
guard не превращался в вечный красный сигнал. Файлы:

| Baseline | Что хранит | Как обновлять |
|---|---|---|
| `tools/check-direct-commits.baseline` | исторические прямые коммиты в master | вручную; новые нарушения не добавляются |
| `tools/check-no-mp4-mentions.baseline` | legacy-упоминания mp4/скачивания (сопоставление по содержимому строки) | `bash tools/check-no-mp4-mentions.sh --update-baseline` |
| `tools/check-secret-in-argv.baseline` | legacy-нарушения «секрет в argv» | `bash tools/check-secret-in-argv.sh --update-baseline` |
| `tools/check-no-legacy-tbl-settings.baseline` | legacy-упоминания `tbl_settings` | `bash tools/check-no-legacy-tbl-settings.sh --update-baseline` |
| `tools/check-file-permissions.baseline` | файлы с нестандартными правами | `bash tools/check-file-permissions.sh --update-baseline` |

Правила обращения с baseline:

- baseline — это «список долгов», а не «список разрешений на будущее»: новое
  нарушение в baseline не добавляется, а чинится;
- сопоставление идёт **по содержимому строки**, а не по номеру строки, иначе
  любая правка выше по файлу ломает baseline;
- если запись baseline перестала быть нужна (код исправлен), запись удаляется,
  а не остаётся «на всякий случай».

## Как добавить новый guard

Новый guard регистрируется **целиком** (R-47, п. 3): скрипт в `tools/`, джоб в
`.github/workflows/lint.yml`, pre-commit-хук и `--self-test` с негативной
фикстурой. Половина регистрации — это ещё не guard.

1. **Скрипт** — положить в `tools/` под именем `check-<что-проверяем>.sh`
   (или `.py`). Обязательные свойства:
   - `--quiet` для pre-commit (предупреждения не должны засорять вывод хука);
   - `--self-test` с двумя обязательными фикстурами («корректное дерево» →
     `0`; «подсовытое нарушение» → ненулевой код) и, если guard умеет
     работать с внешними данными, третьей («данные недоступны» → `2`);
   - коды возврата строго по контракту: `0` / `1` / `2`;
   - список обхода файлов — из `git ls-files` (ровно то, что видит CI), а не
     из `find` по каталогу: в главном checkout'е лежат `.worktrees/*` со своими
     файлами, и обход всего каталога даёт ложные нарушения;
   - никаких предсказуемых имён в `/tmp` — приватные временные файлы.
2. **Джоб CI** — добавить в `.github/workflows/lint.yml` отдельным джобом
   (имя джоба — `check-<что-проверяем>-guard`), с `actions/checkout@<SHA>`
   (пин по SHA, см. `tools/check-actions-pinned.sh`) и явным
   `run: bash tools/check-<что-проверяем>.sh`.
3. **Pre-commit-хуки** — добавить в `.pre-commit-config.yaml` блок `hooks:`
   с `id`, `entry: bash tools/check-<что-проверяем>.sh --quiet`,
   `language: system`, `pass_filenames: false` и, если guard работает по
   изменениям, — `files:` с регуляркой по путям.
4. **Baseline** — если правило нарушается в существующем коде, завести
   baseline-файл `tools/check-<что-проверяем>.baseline` и записи — **по
   содержимому строки**, по одной на нарушение. Если нарушений нет — baseline
   не нужен.
5. **Регламент** — если guard проверяет архитектурное правило, продублировать
   его в
   [architecture-conventions.md](../../guidelines/architecture-conventions.md)
   (формат Rule / Protocol / Failure / Enforcement) и сослаться оттуда на
   guard'а. Для правил сборки это R-46, для регистрации guard'ов — R-47.
   Если проверяемое правило повторяется в двух и более компонентах, оно
   принадлежит общему модулю, а guard' проверяет именно его (R-48).
6. **Документация** — обновить этот документ: строка в таблице guard'ов плюс
   per-feature документ фичи в `docs/features/<slug>.md` (FR-009).

## Инструменты операционного контура

Эти скрипты в CI не вызываются — они обслуживают процесс, а не проверяют
дерево. Общее правило для всех: **код возврата не теряется**.

| Скрипт | Что делает | Требование к кодам возврата |
|---|---|---|
| `tools/tracker.sh` | CLI трекера OpenProject (claim, comment, mark-review, close) | `0` успех, `1` общая ошибка, `2` OpenProject недоступен, `3` HTTP 401/403 (токен истёк) |
| `tools/tracker-lib.sh` | общие функции трекера (`source`-подключается) | код `curl` проверяется явно, статус приводится к `"000"`; ошибка записи аудит-лога не теряется; режим шелла вызывающего не меняется |
| `tools/tracker-poll.sh` | polling-сводка задач для агента | `0` — есть задачи, `1` — всё ОК, `2` — OpenProject недоступен, `3` — токен истёк; stderr трекера не смешивается с данными; `--json` разбирает TSV (`tracker.sh list-issues --raw`) |
| `tools/tracker-bootstrap-board.sh` | первичная настройка борда OpenProject | код `docker exec` проверяется — падение `rails-runner` не остаётся незамеченным |
| `tools/install-tracker.sh` | установка трекера | код `docker compose up` проверяется и сохраняется отдельно |
| `tools/generate-docs.sh` | генерация документации (Dokka/typedoc) | код возврата пайпа равен коду возврата dokka/typedoc (`pipefail`), а не коду `tail` (всегда `0`); `GRADLE_USER_HOME` задаётся скриптом |
| `tools/auto-kdoc.py` | автогенерация KDoc | не пишет «JPA-сущность»; **результат проверяется** guard'ом `check-no-jpa-imports.sh` (код `2`, если guard не найден, — тоже ошибка); детектор существующего KDoc не опирается на фиксированное окно в 8 строк |
| `tools/check-do-sh-guards.sh` | доказательство, что деплой-путь не может быть «молча зелёным» | `0` — опыты прошли, `1` — провал; опыт с падающей сборкой идёт на **копии** `deploy/` во временном каталоге и не переписывает отслеживаемый `deploy/karaoke-public/Dockerfile` |

Креды к трекеру и к registry не передаются через `argv` — через stdin-конфиг
(`curl --config -`). Проверка «неверный пароль → 401» обязана **реально**
отправлять креды, иначе она дублирует анонимную и registry со сломанным
`htpasswd` (принимающим любой пароль) проходит приёмку с ложным PASS.

### Плагин `dsh-forgejo-ci` — наблюдение за CI из DSH (2026-10-28)

CI исполняется раннером вне DSH, поэтому в списке фоновых задач агента прогонов
не видно, и слежение шло ad-hoc watcher'ами (серия `curl` и забытые проверки).
Плагин закрывает это: состояние CI видно и человеку (панель), и агенту (tool'ы).

| Что | Где / чем |
|---|---|
| Код | отдельный репозиторий `svoemesto/dsh-forgejo-ci`; в репозитории Karaoke — только эта запись |
| Установка | профиль `web`, `plugin_manager install_bundle` с путём локального клона; host-половина подхватывается HMR, клиентская — только перезагрузкой страницы |
| Токен | `FORGEJO_CI_TOKEN` в хранилище секретов DSH (`~/.dsh/.credentials.yaml`, секция `refs`), права только на чтение репозитория; значение в конфиг плагина не попадает и в UI/логи не выводится |
| Маршрут | `GET /forgejo-ci/state` — снимок для браузера (JSON, `Cache-Control: no-store`, `405` на не-GET) |
| Tool'ы | `forgejo_ci_status` (мгновенно из кеша) и `forgejo_ci_wait` (ожидание завершения прогона; реакция ограничена интервалом опроса) |
| Панель | **кнопка в верхней панели** с popover (список прогонов, статус раннера, «открыть полностью») и страница в центральной панели; на кнопке — индикатор (точка — последний прогон упал, пульс — идёт); toast о завершении прогона |

Поведение при сбоях: недоступность Forgejo не роняет плагин — снимок остаётся,
помечается `stale` с временем последнего успешного опроса; отказ `actions/tasks`
скрывает только строку живой очереди, данные прогонов и раннера остаются
свежими. Состояние раннера вычисляется по свежести активности, а не по
`status: active` из API.

Ограничения: история прогонов живёт в памяти процесса (Forgejo хранит её
недолго, наблюдался пурж четырёх прогонов до одного за 40 минут), разбивки
«прогон → его джобы» API не отдаёт, ручной запуск workflow и джоб-логи
недоступны — плагин только читает.

## Известные ловушки

1. **Счётчик `git log … | wc -l` занижается на единицу.** Вывод
   `git log --pretty=format:…` не заканчивается переводом строки, поэтому
   `DIRECT_COMMITS = 0` даже при одном прямом коммите — guard не падал
   никогда. Правильный способ — `git log --first-parent` с разбором числа
   родителей (`tools/check-direct-commits.sh`).
2. **Счётчик не равен числу нарушений.** Проверка `docker-compose … restart`,
   выполненная внутри цикла по контейнерам, теряла нарушения. Счётчик
   обязан совпадать с числом найденных нарушений.
3. **`grep -cP` (PCRE) — не переносимо.** На сборках без PCRE (BusyBox, macOS)
   grep падал, stderr глушился, счётчик был пуст, и guard завершался с `0`.
   Переносимый подсчёт — `grep -cE` (POSIX ERE) с проверкой, что счётчики —
   числа.
4. **Guard, который молчит при отсутствии данных, хуже отсутствующего guard'а.**
   Он выдаёт «зелёный» результат ровно тогда, когда проверять нечего.
5. **Обход каталога `find`'ом цепляет чужие checkout'ы.** В главном checkout'е
   Karaoke лежат `.worktrees/*` со своими `gradle-wrapper.properties` —
   обход всего каталога видел их и давал ложные нарушения (в CI, где checkout
   свежий, этого нет). Список файлов — из `git ls-files`.
6. **Baseline, привязанный к номерам строк, ломается при любой правке выше.**
   Сопоставление — по содержимому строки.
7. **Плавающий `uses: @v4` в workflow — это удалённый код без ревью.** Тег
   можно перенаправить на другой коммит в чужом репозитории, и CI исполнит
   его без изменения нашего репозитория.
8. **Gradle-wrapper без `distributionSha256Sum`** скачивает и распаковывает
   ~130 МБ и запускает дистрибутив без сверки с официальной суммой;
   `validateDistributionUrl=true` проверяет только доступность URL.
9. **`-XX:HeapDumpPath=/tmp/…` — дамп читает любой пользователь машины.** Права
   файла дампа — `0666 & ~umask` (обычно `0644`), а в дампе — карта
   `System.getenv()`, то есть секреты окружения сборки.
10. **Линтер фронтенда без шага сборки пропускает не собирающееся SPA.** Шаг
    `npm run build` (`frontend-build`) — обязательная часть контура.

## Связь с другими компонентами

- **[pre-commit-config.md](pre-commit-config.md)** — состав хуков
  `.pre-commit-config.yaml` и правила их подключения.
- **[registry.md](registry.md)** — креды registry не уходят в argv; правила
  приёмки и вендоринга.
- **[do-sh.md](do-sh.md)** — `deploy/do.sh` и её guard'ы; контейнеры
  перезапускаются только через `do.sh`.
- **[per-feature-docs.md](per-feature-docs.md)** — структура per-feature
  документов, которую проверяет `tools/check-feature-doc.sh`.
- **[deploy-overview.md](deploy-overview.md)** — раскладка `deploy/`, к которой
  относятся guard'ы R-04/R-05.
- **[architecture-conventions.md](../../guidelines/architecture-conventions.md)** —
  регламент правил; **R-46** — конфигурация сборки (один Gradle, один
  Jackson, без мёртвых дублей), проверяется `tools/check-gradle-wrapper.sh`
  и `tools/check-build-config.sh`.
- **[security-headers.md](../../guidelines/security-headers.md)** — заголовки
  безопасности прод-nginx, проверяет `tools/check-security-headers.py`.
- **[karaoke-public-tests.md](../frontend/karaoke-public-tests.md)** —
  тестовая инфраструктура публичного SPA и guard экранирования метки.
- **[sql-migrations.md](sql-migrations.md)** — recordhash-контракт и
  `tbl_songs` (миграция 28), которые проверяют `check-recordhash-consistency.py`
  и `check-no-legacy-tbl-settings.sh`.
- **[governance-docs.md](governance-docs.md)** — процессные документы
  (`CONTRIBUTING.md`, `AGENTS.md`), MUST-правила которых проверяет
  `tools/check-enforcement.sh`.

## Источники деталей

Per-feature документы пакетов аудита — канонические подробности по конкретным
находкам и правкам:

- [`docs/features/pkg-s3-30.md`](../../../docs/features/pkg-s3-30.md) —
  24 дефекта guard'ов: четыре класса, унификация кодов возврата, `--self-test`.
- [`docs/features/pkg-s3-34.md`](../../../docs/features/pkg-s3-34.md) —
  конфигурация сборки: единый Gradle, checksum дистрибутива, Jackson, дампы
  кучи, мёртвые дубли build-конфига.
- [`docs/features/pkg-s3-35.md`](../../../docs/features/pkg-s3-35.md) —
  остаток аудита: прямые коммиты в master, шаг сборки фронтенда, офлайн-проверка
  ссылок, легаси-документация по `tbl_songs`.
- [`archive/docs/features/ci-lint-enforcement.md`](../../../archive/docs/features/ci-lint-enforcement.md) —
  пин сторонних actions по SHA.
