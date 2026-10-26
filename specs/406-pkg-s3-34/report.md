# OpenProject #406 — пакет `PKG-S3-34` (5 BR, модуль «прочее»: сборка и безопасность репозитория)

> **Issue**: OpenProject #406 · **Пакет**: `PKG-S3-34` · **Волна**: E ·
> **Аудит**: #336 · **Severity**: S3 · **Ветка**: `640-pkg-s3-34` ·
> **Worktree**: `/home/nsa/Karaoke/.worktrees/waveE-s3-34`
> **Коммит (реализация)**: `4464309b36f6cd2595caa6bf723ba01509a44bc9` (PR #744 → merge `53c0c5b5`)
> **Per-feature документ**: [`docs/features/pkg-s3-34.md`](../../docs/features/pkg-s3-34.md)
> **SSoT**: [`knowledge/guidelines/architecture-conventions.md` § R-46](../../knowledge/guidelines/architecture-conventions.md),
> [`knowledge/system/infra/ci-tools.md`](../../knowledge/system/infra/ci-tools.md)

## Итог

**5 из 5 записей закрыты.** Изменено 19 файлов (+560 / −154 строк), добавлены
3 инструмента (`tools/`) и 2 guard'а подключены к CI.

Пакет — про «прочее»: конфигурация сборки и безопасность репозитория. Общий
признак дефектов — **конфигурация сборки расходилась сама с собой и ничего не
проверяла по целостности**.

| Тема | BR | Суть дефекта | Статус |
|---|---|---|---|
| `gradle-wrapper-outdated` | BR-499 | три разных gradle-wrapper'а (8.14 / 9.1.0 / 9.1.0 / 7.5.1) | закрыто, версии выровнены на 8.14 |
| `gradle-wrapper-no-checksum` | BR-528 | ни в одном wrapper'е нет `distributionSha256Sum` | закрыто, суммы официальные |
| `jackson-version-skew` | BR-527 | пин `jackson-databind:2.14.2` поверх BOM'а (2.19.2) | закрыто, весь стек 2.19.2 |
| `heap-dump-world-readable` | BR-529 | `-XX:HeapDumpPath=/tmp/gradle-heap.hprof` (0644) | закрыто, opt-in + 0700/0600 |
| `dead-duplicate-build-config` | BR-530 | мёртвый блок `dependencies` в корне + `pom.xml` в корне | закрыто, удалено |

Ключевое правило пакета (из #402): **guard, который не смог выполнить
проверку, обязан вернуть ненулевой код** — `0` успех, `1` нарушение,
`2` проверка не выполнена. Оба новых guard'а этому соответствуют и имеют
`--self-test` с негативными фикстурами.

## Карточки записей

### BR-499 — `karaoke-db/gradle/wrapper/gradle-wrapper.properties:3` (`gradle-wrapper-outdated`)

**Что не так.** В репозитории было **три разных** gradle-wrapper'а:

| Файл | `distributionUrl` (было) |
|---|---|
| `gradle/wrapper/gradle-wrapper.properties:3` | `gradle-8.14-bin.zip` |
| `karaoke-app/gradle/wrapper/gradle-wrapper.properties:3` | `gradle-9.1.0-bin.zip` |
| `karaoke-web/gradle/wrapper/gradle-wrapper.properties:3` | `gradle-9.1.0-bin.zip` |
| `karaoke-db/gradle/wrapper/gradle-wrapper.properties:3` | `gradle-7.5.1-bin.zip` |

Фактическая сборка приложения и все CI-джобы бэкенда (`backend-lint`,
`backend-build`, `backend-test`) и `deploy/do.sh` (`deploy/do.sh:47,147,201,222,248`)
идут через **корневой** wrapper (8.14). А `./gradlew` из каталога модуля — через
9.1.0 / 7.5.1. То есть «то, что проверяет CI» и «то, что запускает разработчик
в модуле» — разные Gradle; при этом 9.1.0 не используется **ни в одной** сборке
и не проверен с Kotlin 2.2.20 / Spring Boot 3.5.6.

**Как правильно.** Один Gradle на репозиторий — на тот, на котором зелёная
CI-сборка. ТЗ предлагало «выровнять на 9.1.0 (как в app/web) либо убрать
модуль `karaoke-db`»; выбрано **выравнивание на 8.14** (версия корневого
wrapper'а), потому что:

- 8.14 — единственная версия, на которой реально собирается продукт и на
  которой зелёный CI; переход на 9.x — это апгрейд тулчейна, а не выравнивание;
- Kotlin 2.2.20 (KGP) официально поддерживает Gradle до 8.14 — 9.1.0 в
  репозитории не проверен;
- модуль `karaoke-db` не удалялся: он в `settings.gradle.kts` и не деплоится,
  но удаление модуля — не задача этого пакета (и оно не входило в ТЗ по
  BR-499 как единственный вариант).

Удаление модуля `karaoke-db` целиком (второй вариант ТЗ) **за владельцем** —
это отдельное решение с правками `settings.gradle.kts` и `AGENTS.md`.

**Правка.** Все четыре `gradle-wrapper.properties` приведены к одному виду
(одинаковые побайтово, sha256 `9b55399e…f14e0`):

```
distributionUrl=https\://services.gradle.org/distributions/gradle-8.14-bin.zip
distributionSha256Sum=61ad310d3c7d3e5da131b76bbf22b5a4c0786e9d892dae8c1658d4b484de3caa
networkTimeout=10000
validateDistributionUrl=true
```

**До/после (регрессия).**

- до: `karaoke-db` → 7.5.1, `karaoke-app`/`karaoke-web` → 9.1.0, корень → 8.14;
- после: все четыре → 8.14;
- guard `tools/check-gradle-wrapper.sh --self-test`: фикстура «разнобой версий»
  → FAIL (код 1), фикстура «устаревшая версия 7.5.1» → FAIL (код 1),
  фикстура «корректное дерево» → PASS (код 0);
- эмпирика: `cd karaoke-db && ./gradlew --version` до правки печатал
  `Gradle 7.5.1`, после — `Gradle 8.14`.

### BR-528 — `gradle/wrapper/gradle-wrapper.properties:1–7` (`gradle-wrapper-no-checksum`)

**Что не так.** Ни в одном из четырёх wrapper'ов не было `distributionSha256Sum`
(`grep -rn "distributionSha256Sum" --include="gradle-wrapper.properties" .` → 0
совпадений). Gradle скачивал и распаковывал `gradle-8.14-bin.zip` (≈130 МБ) и
запускал его в локальной JVM **без проверки целостности**;
`validateDistributionUrl=true` проверяет только доступность URL, а не
подлинность содержимого. TLS защищает канал, но не содержимое на origin'е или
зеркале. Побочный эффект — невоспроизводимость: один и тот же `distributionUrl`
со временем может отдать другой бинарник.

**Как правильно.** Зафиксировать официально опубликованную сумму в каждом
wrapper'е; при несовпадении — отказ сборки.

**Правка.** `distributionSha256Sum=61ad310d3c7d3e5da131b76bbf22b5a4c0786e9d892dae8c1658d4b484de3caa`
во всех четырёх wrapper'ах. Сумма **не выдумана** — получена официально и
подтверждена тремя способами:

1. `https://services.gradle.org/distributions/gradle-8.14-bin.zip.sha256` →
   `61ad310d…3caa`;
2. `https://downloads.gradle.org/distributions/gradle-8.14-bin.zip.sha256` →
   то же значение (второй хост Gradle);
3. локальная загрузка архива (137 391 539 байт) → `sha256sum` → то же значение.

**До/после (регрессия).**

- до: `bash gradlew --version` в чистом `GRADLE_USER_HOME` распаковывает
  дистрибутив без сверки; неверная подмена прошла бы незамеченной;
- после (правильная сумма): `GRADLE_USER_HOME=<tmp> bash gradlew --version` →
  `Gradle 8.14`, `BUILD SUCCESSFUL`-эквивалент, код 0;
- после (неверная сумма, негативный тест): wrapper **отказывается** работать:

  ```
  Your Gradle distribution may have been tampered with.
  Expected checksum: '0000000000000000000000000000000000000000000000000000000000000000'
  Actual checksum:   '61ad310d3c7d3e5da131b76bbf22b5a4c0786e9d892dae8c1658d4b484de3caa'
  ```
  код возврата `1`;
- guard `tools/check-gradle-wrapper.sh --self-test`: «нет checksum» → FAIL,
  «неверная checksum» → FAIL, «версия вне таблицы сумм» → код 2
  (проверка не выполнена, а не «всё хорошо»).

### BR-527 — `build.gradle.kts:45` (`jackson-version-skew`)

**Что не так.** Явный пин `com.fasterxml.jackson.core:jackson-databind:2.14.2`
(сентябрь 2022) перебивал версию, управляемую BOM'ом Spring Boot 3.5.6
(2.19.2). Пин стоял в трёх местах: `build.gradle.kts:45` (корневой — мёртвый),
`karaoke-app/build.gradle.kts:36` и `karaoke-web/build.gradle.kts:36` (они и
формировали артефакт). В поставляемом jar'е оказывался **смешанный стек**:
`jackson-databind-2.14.2` рядом с `jackson-core-2.19.2`,
`jackson-annotations-2.19.2`, `jackson-datatype-jsr310-2.19.2`,
`jackson-datatype-jdk8-2.19.2`, `jackson-module-parameter-names-2.19.2` —
разные minor-версии одного семейства, поддержкой производителя не покрытые.
Оправдание в файле было фактически неверным: комментарий `build.gradle.kts:12`
утверждал «Jackson 2.14 (используется в проекте)», тогда как в проекте 2.19.2.

**Как правильно.** Версию всего семейства задаёт BOM — явных версий в
build-файлах нет. Комментарий, оправдывавший пин, исправлен (он и был
«обоснованием» мёртвого дубля).

**Правка.**

- `karaoke-app/build.gradle.kts:36` и `karaoke-web/build.gradle.kts:36`:
  `implementation("com.fasterxml.jackson.core:jackson-databind")` — без версии;
- `build.gradle.kts`: пин исчез вместе с мёртвым блоком `dependencies`
  (BR-530); комментарий про Dokka/Jackson переписан.

**До/после (регрессия).**

- до: `unzip -l karaoke-app/build/libs/karaoke-app-1.jar | grep jackson` →
  `BOOT-INF/lib/jackson-databind-2.14.2.jar` + `jackson-core-2.19.2.jar`
  (смешанный стек);
- после (тот же jar, пересобранный `:karaoke-app:bootJar`):

  ```
  BOOT-INF/lib/jackson-databind-2.19.2.jar
  BOOT-INF/lib/jackson-core-2.19.2.jar
  BOOT-INF/lib/jackson-annotations-2.19.2.jar
  BOOT-INF/lib/jackson-datatype-jsr310-2.19.2.jar
  BOOT-INF/lib/jackson-datatype-jdk8-2.19.2.jar
  BOOT-INF/lib/jackson-module-parameter-names-2.19.2.jar
  ```
- обязательные по ТЗ прогоны после правки:
  `python3 tools/check-recordhash-consistency.py` → `OK recordhash-контракт…`
  (код 0); `bash tools/check-value-placeholders.sh` → `PASS: все @Value…`
  (код 0);
- сборка и тесты: `:karaoke-app:test :karaoke-web:test` — зелёные (Jackson
  используется в `MediaInfo.kt`, `AlbumCoverFinder.kt`, `AdminTaskService.kt`,
  `SseNotificationService.kt` и др. — компиляция и тесты проходят).

### BR-529 — `gradle.properties:9` (`heap-dump-world-readable`)

**Что не так.**

```
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/gradle-heap.hprof
```

При OOM JVM писал **полный дамп кучи** в общий `/tmp` (режим 1777). Права
файла дампа = `0666 & ~umask` → при обычном umask 022 — **0644**, то есть файл
читался любым локальным пользователем. JVM дамп не удаляет, и он переживал
последующие сборки. В дампе лежит карта `System.getenv()` daemon'а — то есть
**все секреты окружения**, из которого запущена сборка (пароли БД, токены,
ключи), плюс состояние build-скриптов и resolved-адреса зависимостей.

**Как правильно.** Флагов дампа в общих `jvmargs` нет; если дамп нужен для
диагностики OOM (в `gradle.properties:3–6` OOM на `:karaoke-app:compileKotlin`
задокументирован) — только явным opt-in, в приватный каталог, с приватными
правами.

**Правка.**

1. `gradle.properties:9` — флаги дампа убраны, в комментарии зафиксирована
   причина и новый порядок;
2. `tools/gradle-heap-dump.sh` (новый) — opt-in запуск Gradle с дампом:
   `umask 077` (права файла дампа = `0666 & ~umask` = **0600**) + каталог
   `$GRADLE_USER_HOME/heap-dumps` с правами **0700** (`chmod 700`).

**До/после (регрессия).**

- до: флаги дампа в общих `jvmargs` → дамп в `/tmp` с правами 0644 (при
  umask 022) — проверяемо статикой по флагам JVM и документации
  `-XX:HeapDumpPath`;
- после: в `gradle.properties` флагов нет; `bash tools/gradle-heap-dump.sh
  --self-test` →

  ```
  ✓ self-test: права каталога дампов = 700
  ✓ self-test: права файла дампа = 600
  ✅ self-test: каталог 700, файл дампа 600 — OK
  ```
  (эмпирическая проверка: создаётся каталог и пробный файл, сверяется
  `stat -c '%a'`);
- end-to-end: `GRADLE_USER_HOME=/tmp/hdtest bash tools/gradle-heap-dump.sh -q help`
  → код 0, daemon'у передан `-XX:HeapDumpPath=/tmp/hdtest/heap-dumps/gradle-heap.hprof`
  (каталог `700` — проверено `stat`).

### BR-530 — `build.gradle.kts:34–68` (`dead-duplicate-build-config`)

**Что не так.** Две копии конфигурации сборки, которые не участвуют в сборке и
расходятся с фактической:

1. **Блок `dependencies` в корневом `build.gradle.kts:34–68`** — мёртвый: у
   корневого проекта нет каталога `src/`, а `implementation`/`testImplementation`
   **не наследуются** в subprojects (каждый модуль имеет свой build-файл).
   При этом версии в корне другие, чем в модулях: `postgresql 42.7.7`
   (корень) против `42.7.8` (`karaoke-app/build.gradle.kts:39`), `commons-io
   2.14.0` против `2.20.0` (`:40`), `commons-lang3 3.18.0` против `3.14.0`
   (`:56`). Правка зависимости в корне меняла **ровно ничего** в собираемом
   приложении, но файл выглядел как основной и читался первым.
2. **`pom.xml` в корне (97 строк)** — второй build-дескриптор (Maven),
   не используемый ни одной задачей сборки (`grep -rn "pom.xml" .github/ tools/
   deploy/` → 0 совпадений). Он задавал **другой** результат сборки:
   `<version>1.0-SNAPSHOT</version>` (`:9`) против Gradle `version = "1"`,
   `<source>16</source><target>16</target>` (`:92–93`) против
   `java.sourceCompatibility = VERSION_17`, и другие версии зависимостей:
   `kotlin-stdlib 1.8.0` (`:31`), `kotlinx-serialization-json 1.5.0` (`:49`),
   `postgresql 42.6.0` (`:72`), `commons-io 2.11.0` (`:79`). Плюс
   `maven-compiler-plugin` (`:89–90`) **без версии** → версия определяется
   super-POM Maven, то есть даже в рамках Maven сборка невоспроизводима.
   Реалистичный путь вреда — автоимпорт Maven-проекта в IDE и ручной
   `mvn package`, собирающий отдельный артефакт с Java-target 16.

**Как правильно.** Maven-сборка не используется → Maven-дескрипт удалён
(это прямо разрешение ТЗ: «удалить `pom.xml` … либо явно пометить его как
справочный»; выбран первый вариант, потому что «справочный» POM, расходящийся
с Gradle, остался бы тем же мёртвым дубликатом). Мёртвый блок `dependencies` в
корне удалён; на его месте — комментарий, объясняющий, где истина по
зависимостям (чтобы правки не уходили «не туда»).

**Правка.**

- `build.gradle.kts` — блок `dependencies { … }` (35 строк) удалён, вместо него
  комментарий с причиной и ссылкой на BR-530;
- `pom.xml` — **удалён** (`git rm`);
- `.idea/misc.xml` — убран компонент `MavenProjectsManager` со ссылкой на
  `pom.xml` (это и был путь автоимпорта фантомного проекта);
- `.specify/memory/constitution.md` — пункт «корневой `pom.xml` — leftover от
  Maven, не использовать» → «удалён 2026-10-26 (BR-530)»;
- `DEVELOPMENT.md` — та же правка в описании структуры репозитория.

**До/после (регрессия).**

- до: `pom.xml` в корне (97 строк) + мёртвый блок `dependencies` (35 строк) в
  корневом build-файле; `find . -maxdepth 2 -name "pom.xml"` → 1 файл;
- после: `pom.xml` в корне нет, блока `dependencies` в корневом
  `build.gradle.kts` нет, ссылок IDE на `pom.xml` нет;
- guard `tools/check-build-config.sh --self-test`: фикстуры «`pom.xml` в
  корне» → FAIL, «ссылка IDE на `pom.xml`» → FAIL, «мёртвый блок
  `dependencies`» → FAIL, «корректная конфигурация» → PASS;
- сборка после удаления: `:karaoke-app:test :karaoke-web:test ktlintCheck
  --rerun-tasks` → **BUILD SUCCESSFUL** (мёртвый блок на артефакт не влиял,
  но его удаление это подтверждает).

## Новые guard'ы и CI

| Инструмент | Записи | Правила | Коды |
|---|---|---|---|
| `tools/check-gradle-wrapper.sh` | BR-499, BR-528 | `wrapper-version-skew`, `wrapper-version-too-old`, `wrapper-no-checksum`, `wrapper-checksum-mismatch`, `wrapper-checksum-malformed`, `wrapper-checksum-table-stale`, `wrapper-version-unparsable` | 0 / 1 / 2 |
| `tools/check-build-config.sh` | BR-527, BR-529, BR-530 | `jackson-explicit-version`, `jackson-version-skew`, `dead-root-dependencies`, `maven-pom-present`, `heap-dump-global-enabled`, `heap-dump-outside-helper`, `heap-dump-shared-path`, `heap-dump-unsafe-perms` | 0 / 1 / 2 |
| `tools/gradle-heap-dump.sh` | BR-529 | opt-in запуск с дампом в приватный каталог; `--self-test` эмпирически проверяет права 700/600 | 0 / 1 / 2 |

Оба guard'а имеют `--self-test` с негативными фикстурами «до/после»
(11 фикстур у `check-build-config.sh`, 7 у `check-gradle-wrapper.sh`);
фикстуры «проверка невыполнима» дают код **2**, а не 0.

**Подключение к CI** — новый джоб `build-config-guard` в
`.github/workflows/lint.yml` (пять шагов: два guard'а, два `--self-test'а и
эмпирическая проверка прав дампа). Дополнительно — pre-commit-хуки
`gradle-wrapper-guard` и `build-config-guard` в `.pre-commit-config.yaml`.

## Проверки

| Проверка | Результат |
|---|---|
| `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" -Djava.awt.headless=true :karaoke-app:test :karaoke-web:test ktlintCheck --rerun-tasks` | **BUILD SUCCESSFUL** in 1m 8s, 29 tasks executed |
| `:karaoke-app:bootJar` | BUILD SUCCESSFUL; весь стек Jackson 2.19.2 в `BOOT-INF/lib` |
| `python3 tools/check-recordhash-consistency.py` | OK (код 0) |
| `bash tools/check-value-placeholders.sh` | PASS (код 0) |
| Все `run:`-команды `.github/workflows/lint.yml` (36 команд, включая 2 новых guard'а и их self-test'ы) | **все OK**, кроме `check-doc-references.py` — падал только из-за ещё не созданного `specs/406-pkg-s3-34/report.md`; после создания отчёта — OK |
| `bash tools/check-gradle-wrapper.sh` / `--self-test` | OK / все 7 фикстур |
| `bash tools/check-build-config.sh` / `--self-test` | OK / все 11 фикстур |
| `bash tools/gradle-heap-dump.sh --self-test` | OK (700 / 600) |
| `bash tools/check-feature-doc.sh docs/features/*.md archive/docs/features/*.md` | OK |
| `python3 tools/check-doc-references.py`, `lint-knowledge.py`, `check-knowledge-cross-links.sh`, `check-knowledge-structure.sh` | OK |
| Фронтенд (`webvue3`, `karaoke-public`) | пакет **не трогает** фронтенд (изменены только корневые build-файлы и `tools/`), поэтому фронт-проверки не запускались |

Замечание о `--rerun-tasks`: команда выполнена с `--rerun-tasks`, иначе Gradle
рапортует `UP-TO-DATE` по закешированным классам и пропускает ошибку
компиляции.

## Дополнения после merge PR #744

PR #744 смержен в master (`53c0c5b5`). Два дефекта, найденные уже после
merge, исправлены отдельной веткой (`641-fix-406-report-hash`):

1. **Хэш коммита реализации в отчёте.** Отчёт публиковался до rebase, поэтому
   в нём стоял хэш «до rebase» (`f129315a…`), которого в истории master нет.
   Заменён на фактический merged-коммит реализации `4464309b…`.
2. **Ложные срабатывания `tools/check-gradle-wrapper.sh` на чужих checkout'ах.**
   Guard собирал список wrapper'ов через `find` по всему каталогу, поэтому в
   **главном** checkout'е Karaoke (где лежат `.worktrees/*` — отдельные
   git-worktree с собственными gradle-wrapper'ами) он рапортовал 16
   «нарушений» из чужих копий: версии 7.5.1/9.1.0 и отсутствие checksum.
   В CI (свежий checkout) такой ситуации нет, поэтому дефект не проявился.
   Теперь список берётся из файлов под контролем версий текущего репозитория
   (`git ls-files` — ровно то, что видит CI); для не-git каталогов (фикстуры
   `--self-test`) остаётся `find` с исключением вложенных репозиториев и
   `.worktrees/*`. Добавлена фикстура «вложенный чужой checkout не учитывается»
   (всего фикстур у guard'а теперь 8).

## Что за владельцем

1. **Апгрейд Gradle 8.14 → 9.x** — сознательно не сделан: Kotlin 2.2.20
   (KGP) официально поддерживает Gradle до 8.14, Spring Boot 3.5.6 — тоже.
   Задача пакета — выравнивание версий, а не апгрейд тулчейна. Решение
   (отдельный PR с зелёной сборкой + новые официальные суммы в
   `tools/check-gradle-wrapper.sh`) — за владельцем.
2. **Удаление модуля `karaoke-db` целиком** (второй вариант ТЗ по BR-499) —
   не сделано: модуль живой (`settings.gradle.kts`), не деплоится, но его
   удаление тянет правки `settings.gradle.kts`, `AGENTS.md` и конституции —
   решение за владельцем.
3. **Версия `MIN_VERSION=8.14` в guard'е** — сейчас это «зелёная CI-сборка».
   Если владелец поднимет версию, значение в `tools/check-gradle-wrapper.sh`
   надо синхронно обновить (guard это подскажет: версия вне таблицы сумм даёт
   код 2).
4. **`.idea/misc.xml`: `ProjectType` = `jpab`** (тип проекта «JPA» в IntelliJ) —
   тоже legacy (JPA в проекте запрещён, R-07), но это метаданные IDE, а не
   build-конфиг; в объём пакета не входило. Отдельная запись — за владельцем.

## Хэши

**Коммит (реализация):** `4464309b36f6cd2595caa6bf723ba01509a44bc9` (ветка `640-pkg-s3-34`, PR #744, merge в master — `53c0c5b5`).

**Контрольные суммы файлов (sha256):**

| Файл | sha256 |
|---|---|
| `gradle/wrapper/gradle-wrapper.properties` | `9b55399efbcbda211f1fdc20cd216a37da878db14546567fa809d536896f14e0` |
| `karaoke-app/gradle/wrapper/gradle-wrapper.properties` | `9b55399efbcbda211f1fdc20cd216a37da878db14546567fa809d536896f14e0` |
| `karaoke-web/gradle/wrapper/gradle-wrapper.properties` | `9b55399efbcbda211f1fdc20cd216a37da878db14546567fa809d536896f14e0` |
| `karaoke-db/gradle/wrapper/gradle-wrapper.properties` | `9b55399efbcbda211f1fdc20cd216a37da878db14546567fa809d536896f14e0` |
| `gradle.properties` | `216b6e75f8a425db4ba40f5974d6a04aa96b59361a243aa59ba3794aa02b9491` |
| `build.gradle.kts` | `f934d1ed6b8c7832e45ac4f05c904bdd3b5c1946cad85f9c72075f1167e256fc` |
| `karaoke-app/build.gradle.kts` | `799c811289e844393027b4b6cf5ac384a9d8350023eab59d4421492b73ccc3e1` |
| `karaoke-web/build.gradle.kts` | `261736e9953964fed7f713fc02b9878cc8c42fe60984d1f6416e5af8cedc01fb` |
| `tools/check-gradle-wrapper.sh` | `fa653b9e87a1bd6ecbadb48eb8267f76a3c85d6e10bbd9eeb4f435a9ec25944d` |
| `tools/check-build-config.sh` | `70abb3e499d5be6cebc403f0095d848c4208e9e5287b0fdeb2c1772158ce48fe` |
| `tools/gradle-heap-dump.sh` | `60a67ce3c6bbc4264986ece19cd579da3c58dc7b428adba3c604f8b48d6d33f4` |
| `docs/features/pkg-s3-34.md` | `d726603d6042589526a5b0ca15a452d09e02dfdcb4bc456067939f55837d5611` |

**Официальная сумма дистрибутива Gradle 8.14:**

```
61ad310d3c7d3e5da131b76bbf22b5a4c0786e9d892dae8c1658d4b484de3caa  gradle-8.14-bin.zip
```

(подтверждена `services.gradle.org`, `downloads.gradle.org` и локальной
загрузкой архива; зафиксирована в четырёх wrapper'ах и в таблице
`tools/check-gradle-wrapper.sh`.)

**Вспомогательное (для сверки, если версия поменяется):**

```
a17ddd85a26b6a7f5ddb71ff8b05fc5104c0202c6e64782429790c933686c806  gradle-9.1.0-bin.zip
f6b8596b10cce501591e92f229816aa4046424f3b24d771751b06779d58c8ec4  gradle-7.5.1-bin.zip
```

## Ссылки

- Реестр аудита #336: [`specs/336-code-audit/registry.csv`](../336-code-audit/registry.csv)
  (фильтр `work_package=406`)
- Бэклог аудита: [`specs/336-code-audit/backlog.md`](../336-code-audit/backlog.md),
  блок `### PKG-S3-34`
- Карточки находок: [`specs/336-code-audit/findings/P-74.md`](../336-code-audit/findings/P-74.md),
  [`specs/336-code-audit/findings/P-41.md`](../336-code-audit/findings/P-41.md)
- Per-feature документ (FR-009): [`docs/features/pkg-s3-34.md`](../../docs/features/pkg-s3-34.md)
- Регламент R-46: [`knowledge/guidelines/architecture-conventions.md`](../../knowledge/guidelines/architecture-conventions.md)
- SSoT по инструментам CI: [`knowledge/system/infra/ci-tools.md`](../../knowledge/system/infra/ci-tools.md)
- CI workflow: [`.github/workflows/lint.yml`](../../.github/workflows/lint.yml) (джоб `build-config-guard`)
