# Gradle 9 — обновление сборочного инструментария

> **Status**: active
> **Feature Key**: gradle-9-upgrade
> **Last Updated**: 2026-10-26
> **Issue**: OpenProject #336 (аудит), волна E — инструментарий сборки
> **Branch**: `658-gradle-9-eval`
> Per-feature документ (Constitution § VI FR-009).

## Что делает

Переводит проект на **Gradle 9.8.2** (актуальная стабильная на 2026-10-26): все
wrapper'а репозитория, guard версии и контрольной суммы, базовая версия в SSoT.

> Обновление 2026-10-26 (после merge PR #766): модуль `karaoke-db` удалён,
> wrapper'ов теперь **три** — см.
> [`docs/features/karaoke-db-module-removal.md`](karaoke-db-module-removal.md).

## Зачем

В 2025 году Gradle 9.1.0 уже пробовали, но **откатили** на 8.14 — и откат был
не из-за несовместимости: модульные wrapper'ы (`karaoke-app`, `karaoke-web`)
остались на 9.1.0, а корневой — на 8.14 (у `karaoke-db` был 7.5.1). «Зелёная
сборка» в таком состоянии означала разные вещи для CI и для разработчика
(см. `docs/features/pkg-s3-34.md`).

С тех пор toolchain обновился: Kotlin 2.2.20, Spring Boot 3.5.6, ktlint-gradle
12.3.0 — они работают с Gradle 9 без изменений. Разнобой wrapper'ов устранён
guard'ом `tools/check-gradle-wrapper.sh`, поэтому обновление можно сделать
целиком и под guard'ом.

## Что изменилось

| Файл | Что |
|---|---|
| `gradle/wrapper/gradle-wrapper.properties` | 8.14 → 9.8.2, `distributionSha256Sum` — официальная сумма `gradle-9.8.2-bin.zip` |
| `karaoke-app/gradle/wrapper/gradle-wrapper.properties` | То же |
| `karaoke-web/gradle/wrapper/gradle-wrapper.properties` | То же |
| `tools/check-gradle-wrapper.sh` | В таблицу `KNOWN_CHECKSUMS` добавлена сумма 9.8.2; `MIN_VERSION` поднята с `8.14` до `9.8.2` (понижение теперь отлавливается); self-test переведён на базовую версию |
| `karaoke-app/build.gradle.kts` | `sourceSets.creating { }` → `sourceSets.create("manualTest") { }` (delegate-синтаксис удалён в Gradle 9) |
| `knowledge/guidelines/architecture-conventions.md` | R-46: минимальная версия `8.14` → `9.8.2` |
| `knowledge/system/infra/ci-tools.md`, `knowledge/system/infra/pre-commit-config.md` | Описание guard'а — новая базовая версия |

## Как работает

Guard `tools/check-gradle-wrapper.sh` требует, чтобы все три wrapper'а
(`gradle/`, `karaoke-app/`, `karaoke-web/`) указывали на одну версию, чтобы версия не была ниже `MIN_VERSION` и чтобы
`distributionSha256Sum` совпадала с официально опубликованной (таблица
`KNOWN_CHECKSUMS` продублирована в guard'е, чтобы проверка была офлайновой и
детерминированной; `--online` сверяет её с services.gradle.org).

Сборка идёт **корневым** wrapper'ом: и CI, и `deploy/do.sh` (`GRADLE="$BASE_DIR/gradlew"`),
поэтому образы собираются тем же Gradle, что и CI.

## Инварианты

- все три wrapper'а — одна версия `9.8.2` и одна официальная сумма;
- `MIN_VERSION` в guard'е = базовая версия: понижение Gradle отлавливается;
- `self-test` guard'а проходит: хорошее дерево → PASS, разнобой версий,
  устаревшая версия, отсутствие и неверность суммы → FAIL;
- проект собирается целиком: компиляция обоих модулей, все тесты, ktlint
  (main и test), оба `bootJar`;
- Docker-образ приложения собирается (путь доставки `do.sh build_app`).

## Известные ловушки

- **Gradle 9 требует JDK 17+.** В образе `deploy/karaoke-app/Dockerfile` —
  `openjdk:17` ✓, в CI — JDK 17 ✓; обе среды соответствуют требованию.
- **`sourceSets.creating { }` удалён.** Delegate-синтаксис даёт предупреждение
  об устаревании (снятие в Gradle 10). Замена — `sourceSets.create("name") { }`;
  ktlint при этом требует переноса многострочного выражения на новую строку и
  отступ 8 пробелов внутри блока.
- **Одно предупреждение об устаревании остаётся** — `Configuration.setVisible`
  вызывает плагин (в нашем коде вызовов `isVisible` нет); сборку не ломает,
  удаление запланировано на Gradle 11.
- **Gradle 9.8.2 требует на скачивание ~150 МБ** дистрибутива — первый запуск
  на чистой машине заметно дольше.

## Ссылки

- Правило R-46 (один Gradle, один Jackson):
  [`knowledge/guidelines/architecture-conventions.md`](../../knowledge/guidelines/architecture-conventions.md)
- Guard версии и контрольной суммы wrapper'ов:
  [`knowledge/system/infra/ci-tools.md`](../../knowledge/system/infra/ci-tools.md)
- Предыдущая унификация wrapper'ов и причина отката с 9.1.0:
  [`docs/features/pkg-s3-34.md`](pkg-s3-34.md)
- Реестр аудита #336: [`specs/336-code-audit/registry.csv`](../../specs/336-code-audit/registry.csv)
