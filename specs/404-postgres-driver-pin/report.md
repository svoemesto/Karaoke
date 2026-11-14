# Отчёт по KARAOKE-404 «сборка: ручной пин версии драйвера PostgreSQL перекрывает BOM Spring Boot (42.7.8)»

> **Задача**: KARAOKE-404 (Plane, workspace `svoemesto`, проект `KARAOKE`).
> **Ветка**: `768-postgres-driver-pin` (feature-ветка от `master`).
> **Дата**: 2026-11-14.
> **Статус**: пин снят, версия драйвера приходит из BOM Spring Boot 3.5.6.

## 1. Что требовалось

В `karaoke-app/build.gradle.kts` и `karaoke-web/build.gradle.kts` драйвер PostgreSQL
подключён с ручной версией (`org.postgresql:postgresql:42.7.8`). Проект применяет
`io.spring.dependency-management` (Spring Boot 3.5.6), который управляет своей версией
драйвера, но ручной пин её перекрывает: `dependencyInsight` показывает
«42.7.8 (selected by rule)».

Что сделать:
1. снять ручной пин версии драйвера, чтобы версия приходила из BOM Spring Boot;
2. проверить resolved-версию через `dependencyInsight` **до и после**: если после снятия
   пина версия меняется (пин не мёртвый) — оставить пин и добавить комментарий-обоснование,
   какая именно правка в драйвере требуется и почему;
3. остальные зависимости не трогать.

Критерии готовности: `dependencyInsight` — версия определена (**из BOM** *или* обоснованный
пин); `:karaoke-app:compileKotlin` — зелёный; `:karaoke-app:test` — зелёный; изменения
закоммичены.

## 2. Проверка ДО (пин на месте)

```
$ ./gradlew :karaoke-app:dependencyInsight --configuration runtimeClasspath --dependency postgresql
org.postgresql:postgresql:42.7.8 (selected by rule)
org.postgresql:postgresql -> 42.7.8
```

BOM Spring Boot 3.5.6 задаёт `<postgresql.version>42.7.7</postgresql.version>`
(`spring-boot-dependencies-3.5.6.pom`, строка 159) — ручной пин перекрывает её.

## 3. Проверка ПОСЛЕ (пин снят)

```
$ ./gradlew :karaoke-app:dependencyInsight --configuration runtimeClasspath --dependency postgresql
org.postgresql:postgresql:42.7.7 (selected by rule)
org.postgresql:postgresql -> 42.7.7

$ ./gradlew :karaoke-web:dependencyInsight --configuration runtimeClasspath --dependency postgresql
org.postgresql:postgresql:42.7.7 (selected by rule)
org.postgresql:postgresql -> 42.7.7
```

Версия теперь приходит из BOM — ровно так же, как Jackson (`jackson-databind:2.19.2`
из BOM Spring Boot).

## 4. Ключевой факт: пин жив механически, но ни одна правка 42.7.8 проекту не нужна

Процедура задачи требует: если версия меняется (пин не мёртвый) — оставить пин и обосновать
**конкретную правку драйвера**. Проверено: таких правок для этого кода нет. Все 13
изменений релиза 42.7.8 (18.09.2025) сверены с фактическим использованием API драйвера
в `karaoke-app` и `karaoke-web`:

| Изменение в 42.7.8 | Нужно проекту? | Что проверено |
|---|---|---|
| PR #3796 — конвертация boolean→number в геттерах | Нет | boolean-колонки не читаются как числа (`getInt`/`getLong` по boolean-полям — 0 вхождений) |
| PR #3783 — perf `getMetaData` без QUERY_ONESHOT | Нет | вызовов `getMetaData()` в коде нет (0 вхождений) |
| PR #3750 — `BufferedInputStream` + `FileInputStream` | Нет | драйвер не открывает файлы |
| server-prepared statements для `DatabaseMetaData` | Нет | `DatabaseMetaData` не используется (0 вхождений) |
| PR #3778 — `IllegalStateException: Timer already cancelled` в `StatementCancelTimerTask` | Нет | таймер отмены работает только при `queryTimeout`; `setQueryTimeout`/`queryTimeout` не используются (0 вхождений) |
| NPE при отмене запроса с неизвестным cancel key | Нет | `Statement.cancel()` / `cancelQuery()` не вызываются (0 вхождений) |
| PR #3746 — длина данных в `PgArray` для многомерных массивов | Нет | массивы в проекте только одномерные (`bigint[]`), читаются через `getArray(...)`, а не `getResultSet()`; ветка `calcRemainingDataLength` достигается только при `dims.length > 1` |
| `getImportedExportedKeys` — стабильный порядок колонок | Нет | `DatabaseMetaData` не используется |
| PR #3660 — `SELF_REFERENCING_COL_NAME` в `getTables` | Нет | `DatabaseMetaData` не используется |
| репликация на серверах < 12 | Нет | репликация не используется |
| `ResultSet#close()` драйвера не закрывает statement | Нет | `closeOnCompletion` не используется (0 вхождений), `PreparedStatement#getMetaData()` не вызывается |
| пустая метаинформация для пустых имён каталога | Нет | `DatabaseMetaData` не используется |
| проверка класса в `PGXmlFactoryFactory` | Нет | xml-тип PostgreSQL не используется |

Итог: **ни одна правка 42.7.8 этому коду не требуется**. Значит, требуемое процедурой
обоснование («какая именно правка в драйвере требуется и почему») дать честно нельзя —
писать его значило бы заявить неподтверждённое.

Поэтому выбран путь, прямо разрешённый критерием готовности задачи («версия определена —
**из BOM** *или* обоснованный пин»): пин снят, версия приходит из BOM. Прецедент в
проекте ровно такой же — Jackson (BR-527): мёртвый пин убран, версия приходит из BOM.

## 5. Почему это лучше оставленного пина (проверенные факты)

- **Пин замораживал драйвер.** Ручная версия перебивает BOM, поэтому обновление
  драйвера вместе с обновлением Spring Boot было невозможно — пришлось бы править
  build-файлы вручную.
- **Пин блокировал бы доставку исправлений уязвимостей.** По changelog pgjdbc
  (`CHANGELOG.md`, релиз 42.7.14, 2026-10-07) advisory
  [GHSA-f64h-wr5q-3qf3](https://github.com/pgjdbc/pgjdbc/security/advisories/GHSA-f64h-wr5q-3qf3)
  («значение короче объявленной длины … могло содержать SQL-текст и значения параметров
  предыдущих запросов») затрагивает версии **42.7.4 — 42.7.13** и исправлен в 42.7.14.
  И 42.7.7, и 42.7.8 в этот диапазон входят, то есть снятие пина не создаёт новой
  уязвимости — но с пином исправление не дошло бы из BOM никогда.
- **Обратная совместимость по коду.** Разница 42.7.7 ↔ 42.7.8 для этого проекта — пустая
  (см. таблицу в § 4), и подтверждена тестами (см. § 6).

**Если владелец всё же хочет именно 42.7.8** — правильный механизм — обновление BOM
Spring Boot (или явный `platform(...)` во всём проекте), а не ручной пин в модуле:
пин в модуле перекрывает BOM и в этом случае.

## 6. Проверки

| Проверка | Результат |
|---|---|
| `:karaoke-app:dependencyInsight --dependency postgresql` | 42.7.7 — из BOM |
| `:karaoke-web:dependencyInsight --dependency postgresql` | 42.7.7 — из BOM |
| `:karaoke-app:compileKotlin` | зелёный |
| `:karaoke-web:compileKotlin` | зелёный |
| `:karaoke-app:test` | **1173 теста, 181 класс, 0 падений** (7 skipped — существующие пропуски) |
| `:karaoke-web:test` | **240 тестов, 45 классов, 0 падений** |
| `tools/check-build-config.sh --self-test` | все 12 правил работают (плохое → FAIL, хорошее → PASS) |
| `tools/check-build-config.sh` | OK |

Тесты прогонялись против локального PostgreSQL 16 (`karaoke-db`, порт 8832) — то есть
реальные обращения к БД через драйвер 42.7.7, а не только моки.

## 7. Изменения

- `karaoke-app/build.gradle.kts` — пин снят, `implementation("org.postgresql:postgresql")`
  + комментарий-обоснование (почему версия из BOM и каких правок 42.7.8 проект не использует).
- `karaoke-web/build.gradle.kts` — то же.
- `tools/check-build-config.sh` — новое правило `postgresql-explicit-version`
  (явная версия драйвера в build-файлах запрещена) + self-test-фикстуры; в фикстуре
  «хорошее дерево» пин убран, добавлен кейс «пин → FAIL».
- `knowledge/guidelines/architecture-conventions.md` — R-46: правило 6 (драйвер из BOM),
  пункт в Protocol, пункт в Failure, Enforcement.

## 8. Что НЕ менялось

- Остальные зависимости — все как были (Jackson, commons-io, gson, jsoup, TarsosDSP и т. д.).
- Код приложения — ни строчки: изменены только build-файлы, guard и знание.
- Поведение — функционально идентично: правок 42.7.8 проект не использует (§ 4), тесты
  обоих модулей зелёные на 42.7.7 (§ 6).

## 9. Знание (MUST #0, выполнено)

Произведён поиск по `knowledge/` (3+ попытки: `postgresql`, `pgjdbc`, `драйвер Postgres`,
`JDBC-драйвер`, `BR-527`, `jackson`) и прочитаны релевантные документы:
`knowledge/guidelines/architecture-conventions.md` (R-46), `knowledge/system/infra/audit-336-registry.md`
(реестр BR-527/529/530), `knowledge/system/agent-mechanics.md`. Прецедент BR-527
(Jackson) использован как образец: снятие мёртвого пина + guard, запрещающий его возврат.
