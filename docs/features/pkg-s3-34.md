# PKG-S3-34 — конфигурация сборки: один Gradle, один Jackson, без мёртвых дублей

> **Status**: active
> **Feature Key**: pkg-s3-34
> **Last Updated**: 2026-10-26
> **Spec**: [specs/406-pkg-s3-34/report.md](../../specs/406-pkg-s3-34/report.md)
> **Issue**: OpenProject #406 (пакет `PKG-S3-34`, волна E бэклога аудита #336)
> **Branch**: `640-pkg-s3-34`
> Per-feature документ (Constitution § VI FR-009).

## Что делает

Пакет закрывает 5 записей аудита #336 в модуле «прочее» (сборка и безопасность
репозитория). Общий признак дефектов: **конфигурация сборки в репозитории
расходилась сама с собой и ничего не проверяла по целостности** — три разных
gradle-wrapper'а без контрольных сумм, явный пин Jackson поверх BOM'а, дамп
кучи в общем `/tmp` и две мёртвые копии build-конфига.

Темы пакета: `gradle-wrapper-outdated`, `jackson-version-skew`,
`gradle-wrapper-no-checksum`, `heap-dump-world-readable`,
`dead-duplicate-build-config`.

По файлам:

| Файл | Записи | Что изменилось |
|---|---|---|
| `gradle/wrapper/gradle-wrapper.properties` | BR-528 | Добавлен `distributionSha256Sum=61ad310d…3caa` — официальная сумма `gradle-8.14-bin.zip` |
| `karaoke-app/gradle/wrapper/gradle-wrapper.properties` | BR-499, BR-528 | Версия 9.1.0 → 8.14 (версия, на которой зелёная CI-сборка) + `distributionSha256Sum`; добавлены `networkTimeout`/`validateDistributionUrl` |
| `karaoke-web/gradle/wrapper/gradle-wrapper.properties` | BR-499, BR-528 | То же: 9.1.0 → 8.14 + `distributionSha256Sum` |
| `karaoke-db/gradle/wrapper/gradle-wrapper.properties` | BR-499, BR-528 | То же: 7.5.1 → 8.14 + `distributionSha256Sum` |
| `karaoke-app/build.gradle.kts` | BR-527 | Снят явный пин `jackson-databind:2.14.2` → версия из BOM Spring Boot 3.5.6 (2.19.2) |
| `karaoke-web/build.gradle.kts` | BR-527 | То же |
| `build.gradle.kts` | BR-527, BR-530 | Удалён мёртвый блок `dependencies` (у корневого проекта нет `src/`, `implementation` не наследуется в subprojects); исправлен комментарий про «Jackson 2.14» |
| `pom.xml` | BR-530 | **Удалён** — второй, неиспользуемый build-дескриптор (Maven не используется ни CI, ни скриптами) |
| `.idea/misc.xml` | BR-530 | Убрана ссылка IDE на `pom.xml` (автоимпорт фантомного Maven-проекта) |
| `.specify/memory/constitution.md` | BR-530 | Пункт «корневой `pom.xml` — leftover от Maven» → «удалён» |
| `gradle.properties` | BR-529 | Убраны `-XX:+HeapDumpOnOutOfMemoryError` и `-XX:HeapDumpPath=/tmp/gradle-heap.hprof`; зафиксирована новая политика дампов |
| `tools/gradle-heap-dump.sh` | BR-529 | **Новый**: opt-in запуск Gradle с дампом кучи в приватный каталог (`umask 077` → файл `0600`, каталог `0700`) |
| `tools/check-gradle-wrapper.sh` | BR-499, BR-528 | **Новый guard**: единая версия wrapper'ов, версия не ниже минимума, `distributionSha256Sum` совпадает с официальной |
| `tools/check-build-config.sh` | BR-527, BR-529, BR-530 | **Новый guard**: нет явных версий Jackson, нет разнобоя версий, нет мёртвого блока `dependencies` в корне, нет `pom.xml`, дампы кучи — только в sanctioned-скрипте и с приватными правами |
| `.github/workflows/lint.yml` | BR-499, BR-527, BR-528, BR-529, BR-530 | Новый джоб `build-config-guard` (guard + self-test'ы + эмпирическая проверка прав 700/600) |
| `.pre-commit-config.yaml` | BR-499, BR-527, BR-528, BR-529, BR-530 | Pre-commit-хуки `gradle-wrapper-guard` и `build-config-guard` |

## Зачем

- **Одна сборка — один Gradle.** CI (`backend-lint`, `backend-build`,
  `backend-test`) и `deploy/do.sh` собирают **корневым** wrapper'ом (8.14), а
  разработчик, запустивший `./gradlew` в каталоге модуля, получал 9.1.0 или
  7.5.1. «Зелёная сборка» в этом случае не означает, что проверяется то же
  самое, что запускает разработчик.
- **Целостность дистрибутива.** `validateDistributionUrl=true` проверяет
  доступность URL, а не содержимое: Gradle распаковывал ~130 МБ бинарника и
  запускал его в локальной JVM без сверки с официальной суммой.
- **Один стек Jackson.** `jackson-databind:2.14.2` (сентябрь 2022) лежал в
  артефакте рядом с `jackson-core/annotations/datatype-*:2.19.2` — разные
  minor-версии одного семейства, поддержкой производителя не покрытые.
- **Секреты в дампах кучи.** Полный дамп heap'а Gradle-daemon'а содержит карту
  `System.getenv()`, то есть все секреты окружения, из которого запущена
  сборка. В общем `/tmp` файл дампа читался любым локальным пользователем.
- **Правки «не туда».** Мёртвый блок `dependencies` в корне и `pom.xml` в
  корне выглядели как основная конфигурация, но не влияли на артефакт (или
  влияли на «фантомную» сборку).

## Как работает

Три инструмента, все — с едиными кодами возврата (правило пакета #402):
`0` — успех, `1` — нарушение, `2` — проверка не могла быть выполнена.

1. **`tools/check-gradle-wrapper.sh`** — находит все `*/gradle/wrapper/
   gradle-wrapper.properties` и проверяет: (1) все на одной версии дистрибутива
   (опорная — корневой wrapper, его используют CI и `deploy/do.sh`);
   (2) версия не ниже `MIN_VERSION` (8.14 — версия зелёной CI-сборки);
   (3) в каждом есть `distributionSha256Sum`; (4) сумма совпадает с
   официально опубликованной (таблица `KNOWN_CHECKSUMS` — офлайн,
   детерминированно; `--online` перепроверяет по `services.gradle.org`).
   Версия, которой нет в таблице, даёт код 2, а не «всё хорошо».
2. **`tools/check-build-config.sh`** — проверяет конфигурацию сборки:
   явные версии Jackson и разнобой версий (BR-527); мёртвый блок
   `dependencies` в корневом `build.gradle.kts` и наличие `pom.xml`/ссылок на
   него (BR-530); глобальное включение дампов кучи, дампы вне sanctioned-скрипта,
   дампы в общих каталогах и отсутствие `umask 077`/`chmod 700` в
   `tools/gradle-heap-dump.sh` (BR-529).
3. **`tools/gradle-heap-dump.sh`** — opt-in запуск Gradle с дампом кучи:
   `umask 077` → JVM создаёт файл дампа с правами `0666 & ~umask` = `0600`;
   каталог `$GRADLE_USER_HOME/heap-dumps` — `0700`. `--self-test` проверяет
   права **эмпирически** (создаёт каталог и пробный файл, сверяет `stat -c %a`).

Каждый guard имеет `--self-test` с негативными фикстурами «до/после»
(11 фикстур для `check-build-config.sh`, 7 для `check-gradle-wrapper.sh`):
«корректное дерево → PASS», «подсовытое нарушение → FAIL с ненулевым кодом»,
«проверка невыполнима → код 2».

## Инварианты

1. **В репозитории один Gradle.** Все четыре wrapper'а — на одной версии, не
   ниже 8.14, и с официальной `distributionSha256Sum`.
2. **Дистрибутив проверяется.** Неверная сумма → wrapper отказывается
   запускать дистрибутив («Your Gradle distribution may have been tampered
   with»), сборка падает.
3. **Jackson — одной версии.** В build-файлах нет явных версий
   `com.fasterxml.jackson.*`; версию задаёт BOM Spring Boot 3.5.6 (2.19.2).
4. **Дампов кучи в общих каталогах нет.** Флагов дампа в `gradle.properties` нет;
   включить дамп можно только через `tools/gradle-heap-dump.sh` (каталог `0700`,
   файл `0600`).
5. **Мёртвого дублирующего build-конфига нет.** В корневом `build.gradle.kts`
   нет блока `dependencies`; `pom.xml` в корне нет; зависимости модулей
   объявляются только в build-файлах модулей.
6. **Guard, который не смог проверить, возвращает ненулевой код.** Отсутствие
   wrapper'ов, отсутствие файлов конфигурации, версия вне таблицы сумм —
   код 2, а не «нарушений нет».
7. **Правки минимальны и проверяемы.** Сборка остаётся зелёной
   (`:karaoke-app:test :karaoke-web:test ktlintCheck --rerun-tasks`).

## Известные ловушки

1. **Корень сборки — не место для зависимостей.** У корневого проекта нет
   каталога `src/`, а `implementation`/`testImplementation` **не наследуются** в
   subprojects: каждый модуль имеет свой `build.gradle.kts`. Правка
   зависимости в корне меняет ровно ничего в собираемом приложении — но файл
   выглядит как основной и читается первым. Правило: зависимости — только в
   build-файлах модулей.
2. **Явная версия зависимости перебивает BOM.** `jackson-databind:2.14.2` рядом
   с `jackson-core:2.19.2` — смешанный стек одного семейства. Правило: версии
   семейства задаёт BOM, в build-файле версии нет.
3. **«Версия 2.14 используется в проекте» — комментарий-ложь.** Именно такой
   комментарий (`build.gradle.kts:12`) служил «обоснованием» мёртвого пина.
   Правило: комментарий, оправдывающий версию, проверяется тем же guard'ом,
   что и сама версия.
4. **`validateDistributionUrl=true` — не проверка целостности.** Она проверяет
   доступность URL. Единственный барьер против подмены бинарника — сумма.
   Правило: `distributionSha256Sum` обязателен, сумма берётся из официального
   `gradle-<v>-bin.zip.sha256`, а не из памяти и не из локального кеша.
5. **Сумму надо подтверждать, а не выдумывать.** Официальная сумма публикуется
   на двух хостах (`services.gradle.org` и `downloads.gradle.org`) — их
   совпадение плюс локальная сверка `sha256sum` скачанного архива дают
   уверенность, что `bash gradlew --version` не сломается.
6. **`-XX:HeapDumpPath=/tmp/…` — дамп читает любой пользователь.** Права файла
   дампа = `0666 & ~umask` (при обычном umask 022 — 0644), а в дампе —
   `System.getenv()` daemon'а, то есть секреты окружения сборки. Правило:
   каталог `0700` внутри `GRADLE_USER_HOME` + `umask 077` → файл `0600`.
7. **Флаги JVM не умеют задавать права файла.** У `-XX:HeapDumpPath` нет
   «режима 600»: JVM создаёт файл через `open(..., 0666)`, и итоговые права
   определяются umask процесса. Правило: приватность дампа обеспечивается
   `umask 077` в запускающем скрипте, а не «настройкой JVM».
8. **«Выровнять версии» ≠ «обновить версии».** Переход на Gradle 9.x ломает
   Kotlin 2.2.20 / Spring Boot 3.5.6 (не проверено). Правило: guard держит
   минимум и требует равенства версий; смена версии — отдельный PR с зелёной
   сборкой (решение владельца).
9. **`pom.xml` в корне Gradle-проекта — не безобидный legacy.** IDE
   автоимпортирует его как отдельный Maven-проект (8 устаревших зависимостей,
   Java-target 16), а `mvn package` собирает отдельный артефакт, не
   приложение. Правило: в корне Gradle-проекта Maven-дескрипта нет, и ссылок
   на него в `.idea/` тоже нет.
10. **Присваивание перед вызовом функции не сохраняется.** В этом bash
    `ROOT="$dir" collect_wrappers` не оставляет `ROOT` за пределами вызова —
    переменная обнуляется, и следующая проверка идёт по другому каталогу
    (в self-test'ах это давало «проверка не выполнена» вместо ожидаемого
    вердикта). Правило: присваивание — отдельной строкой.
11. **Guard не должен сканировать сам себя.** В тексте guard'а есть строки с
    примерами нарушений (`-XX:HeapDumpPath=/tmp/…`, `jackson-databind:2.14.2`)
    — это не исполняемый код. Правило: guard исключает собственный файл из
    сканирования, примеры держатся в фикстурах.
12. **Guard, который обходит каталог `find`'ом, цепляет чужие checkout'ы.**
    В главном checkout'е Karaoke лежат `.worktrees/*` — отдельные git-worktree
    с собственными `gradle-wrapper.properties`. `find` по всему каталогу видел
    их и рапортовал ложные нарушения (16 штук) — в CI (свежий checkout) этого
    нет, поэтому дефект не проявился. Правило: список файлов для проверки
    брать из `git ls-files` (ровно то, что видит CI) либо явно исключать
    вложенные репозитории.

## Ссылки

- Отчёт пакета: [`specs/406-pkg-s3-34/report.md`](../../specs/406-pkg-s3-34/report.md)
- Реестр аудита #336: [`specs/336-code-audit/registry.csv`](../../specs/336-code-audit/registry.csv)
  (фильтр `work_package=406`)
- Бэклог аудита: [`specs/336-code-audit/backlog.md`](../../specs/336-code-audit/backlog.md),
  блок `### PKG-S3-34`
- Карточки находок: [`specs/336-code-audit/findings/P-74.md`](../../specs/336-code-audit/findings/P-74.md)
  (P-74-01…P-74-04) и [`specs/336-code-audit/findings/P-41.md`](../../specs/336-code-audit/findings/P-41.md)
  (P-41-01)
- Регламент: **R-46** в
  [`knowledge/guidelines/architecture-conventions.md`](../../knowledge/guidelines/architecture-conventions.md)
- SSoT по инструментам CI:
  [`knowledge/system/infra/ci-tools.md`](../../knowledge/system/infra/ci-tools.md)
- Guard'ы: [`tools/check-gradle-wrapper.sh`](../../tools/check-gradle-wrapper.sh),
  [`tools/check-build-config.sh`](../../tools/check-build-config.sh),
  [`tools/gradle-heap-dump.sh`](../../tools/gradle-heap-dump.sh)
- CI workflow: [`.github/workflows/lint.yml`](../../.github/workflows/lint.yml)
  (джоб `build-config-guard`)
- Governance: [`AGENTS.md`](../../AGENTS.md) § «Каталог guards»,
  [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md)
