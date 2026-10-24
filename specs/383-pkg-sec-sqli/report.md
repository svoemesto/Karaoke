# Отчёт OpenProject #383 — `PKG-SEC-SQLI` (SQL-инъекции)

> **Пакет**: `PKG-SEC-SQLI`, severity S2, волна D.
> **Бэклог аудита**: [#336](https://openproject.local/work_packages/336) (`specs/336-code-audit/backlog.md`, блок `### PKG-SEC-SQLI`).
> **Ветка**: `587-pkg-sqli`, база — `origin/master` @ `07126462`.
> **Worktree**: `/home/nsa/Karaoke/.worktrees/waveD-sqli` (главная копия не изменялась).
> **Дата**: 2026-10-24.
> **Записи**: BR-028, BR-100, BR-175, BR-182, BR-237.

## Что сделано (по записям)

| BR | Где было | Механизм до | Стало |
|---|---|---|---|
| **BR-028** | `karaoke-app/.../KaraokeProcess.kt:885–904` (P-04-01) | `process_name LIKE '%…%'`, `process_status = '…'`, `ids` → `IN (…)`, числа/`process_type`/`process_prioritet` — конкатенацией; исполнение `Statement.executeQuery` | `SqlWhereBuilder` + `?`; `id/ids/order/priority/song_id/prioritet/thread_id` → `Long` (`setLong`), `ids` → `IN (?, …)`; невалидное число — `1=0`; `PreparedStatement` |
| **BR-100** | `karaoke-app/.../Song.kt:7817–8068` (P-18-17); исполнение — `loadListFromDb`/`countMatchingAttr` | восемь семейств сырых значений: `ids`, `album_id`, `publish_date/time`, `status`, `song_bpm/tone/year/track`, `song_type`, `flag_*`, все `filter_*`; `Statement.executeQuery` | `getWhereList` возвращает `SqlWhere` (фрагменты с `?` + параметры); числа → `Long`; операторы `>=|>|<=|<|!=` — белый список; флаги — только `+`/`-`; LIKE/tsquery/даты — параметрами; `PreparedStatement` + `bind` |
| **BR-175** | `karaoke-app/.../Pictures.kt:213–219` (P-16-01) | `id=${…}`, `LOWER(picture_name) LIKE '%…%'`, `picture_name = '…'` | `?` + параметры; `id` → `Long`, невалидный → `1=0` |
| **BR-182** | `karaoke-app/.../Author.kt:174–180` (P-15-01) | `id`, `author`, `ym_id`, `vk_id`, `last_album_*` — конкатенацией | `?` + параметры; `id` → `Long`; булевы фильтры — прежний белый список `+/-/true/false` (константы SQL) |
| **BR-237** | `karaoke-app/.../Album.kt:227–229` (P-14-01) | `id`, `author_id`, `year`, `album_type` без кавычек; `name`/`name_search` — ручное `''` | `?` + параметры; числа → `Long`; `album_type` — только `AlbumType.entries`, иначе `1=0`; ручное экранирование кавычек больше не нужно |

Ключевой шов — новый `model/SqlWhere.kt` (`SqlWhere` + `SqlWhereBuilder`) и
перегрузка `KaraokeDbTable.loadList(where = SqlWhere, …)`, которая исполняет
запрос через `PreparedStatement`, если есть параметры. Старый overload
`whereList: List<String>` сохранён для моделей, не входящих в пакет, — публичное
поведение API не менялось.

## «До/после»: доказательство инъекции

Тесты лежат в `karaoke-app/src/test/.../model/`:

- `SqlInjectionBeforeAfterTest.kt` — по одному тесту на BR, использует только
  API, существовавший до фикса (поэтому компилируется и на старом коде);
- `SqlInjectionRegressionTest.kt` — ветки, которые одним payload не проверить
  (`ids` → `Long`, `filter_*`, `album_type`, `1=0`);
- `SqlInjectionTestSupport.kt` — мок JDBC и эвристика «оператор вне литерала».

Каждый тест «до/после» содержит `legacy*Sql(...)` — дословную репродукцию
старого построителя. Проверяется, что на старом SQL payload выходит за пределы
строкового литерала, а на новом SQL его нет, и значение приходит в
`PreparedStatement` (`verify(ps).setString/setLong`). Живая БД не нужна.

### Прогон на старом коде (ветка `master` @ `07126462`)

```
GRADLE_USER_HOME=… ./gradlew --max-workers=1 :karaoke-app:test \
  --tests 'com.svoemesto.karaokeapp.model.SqlInjectionBeforeAfterTest'

SqlInjectionBeforeAfterTest > BR-100 — фильтр status песен биндится, а не склеивается() FAILED
    org.opentest4j.AssertionFailedError: в SQL не должно быть пользовательских значений ==> expected: <false> but was: <true>
SqlInjectionBeforeAfterTest > BR-237 — фильтр названия альбома биндится() FAILED
    Wanted but not invoked: connection.prepareStatement(<Capturing argument: String>)
SqlInjectionBeforeAfterTest > BR-175 — фильтр picture_name картинок биндится() FAILED
    Wanted but not invoked: connection.prepareStatement(<Capturing argument: String>)
SqlInjectionBeforeAfterTest > BR-182 — фильтры авторов биндятся() FAILED
    Wanted but not invoked: connection.prepareStatement(<Capturing argument: String>)
SqlInjectionBeforeAfterTest > BR-028 — фильтры процессов не попадают в текст SQL() FAILED
    Wanted but not invoked: connection.prepareStatement(<Capturing argument: String>)
5 tests completed, 5 failed
BUILD FAILED
```

Расшифровка: BR-100 — старый `countMatchingAttr` уже звал `prepareStatement`, но
склеивал `WHERE`, поэтому payload был прямо в тексте SQL; остальные четыре —
старый код шёл через `Statement`, и `PreparedStatement` не вызывался вовсе, а
payload уходил в `executeQuery`.

### Прогон после фикса

```
GRADLE_USER_HOME=… ./gradlew --max-workers=1 :karaoke-app:test \
  --tests 'com.svoemesto.karaokeapp.model.SqlInjection*'
BUILD SUCCESSFUL
```

«До»-прогон выполнен временной подменой шести production-файлов версиями
`07126462` (`git checkout -- <files>`), после прогона файлы восстановлены из
резервной копии и сверены по SHA-1; резервная копия удалена.

## Тесты

`SqlInjectionBeforeAfterTest` (5):
BR-028 (фильтры процессов), BR-100 (`status`), BR-175 (`picture_name`),
BR-182 (`author`/`ym_id`), BR-237 (`name_search`).

`SqlInjectionRegressionTest` (6):
BR-028 `ids` → `IN (?, ?, ?)` + `setLong`, невалидный `ids` → `1=0`;
BR-100 `getWhereList` (константы + параметры, `publish_date` форматом-константой),
числовые `filter_rate`/`ids`;
BR-175 невалидный `id` → `1=0`;
BR-182 невалидный `id` → `1=0`;
BR-237 `album_type` по белому списку и отказ на неизвестном значении.

## Проверки

| Проверка | Команда | Результат |
|---|---|---|
| ktlint | `./gradlew ktlintCheck` | BUILD SUCCESSFUL |
| karaoke-app tests | `./gradlew :karaoke-app:test` | 475 тестов; 4 падения — только `VkPreviewWarmupClientTest` с `java.awt.AWTError: Can't connect to X11 window server using ':0'` (окружение sandbox, к пакету не относится); см. ниже headless-прогон |
| karaoke-app tests (headless) | `JAVA_TOOL_OPTIONS=-Djava.awt.headless=true ./gradlew :karaoke-app:test` | см. «Итог проверок» |
| karaoke-web tests | `./gradlew :karaoke-web:test` | см. «Итог проверок» |
| knowledge structure | `bash tools/check-knowledge-structure.sh` | `OK: knowledge/ structure valid (9/9 checks passed)` |
| doc references | `python3 tools/check-doc-references.py` | см. «Итог проверок» |
| subagent isolation | `bash tools/check-subagent-isolation.sh` | exit 0; предупреждения о базовых коммитах, общих с чужими PR-ветками (`589-pkg-race-publish`, `586-registry-365`, `583-pkg-money-idempotency`) — следствие общего клона, не наших правок |
| feature doc | `./tools/check-feature-doc.sh docs/features/sql-injection-hardening.md` | см. «Итог проверок» |

## Изменённые файлы

- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SqlWhere.kt` — новый `SqlWhere`/`SqlWhereBuilder`.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/KaraokeDbTable.kt` — overload `loadList(where = SqlWhere)`, `PreparedStatement` + `bind`, закрытие в `finally`.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt` — `loadList` (BR-028).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` — `getWhereList`, `countMatchingAttr`, `loadListFromDb` (BR-100).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Pictures.kt`, `Author.kt`, `Album.kt` — `getWhereList` + вызов `loadList(where = …)`.
- `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/model/SqlInjection*Test.kt`, `SqlInjectionTestSupport.kt` — регрессия «до/после».
- `knowledge/domains/persistence/domain.md` — раздел «Параметризованный `WHERE` (`SqlWhere`)».
- `knowledge/domains/catalog/components/{author-entity,album-entity,pictures,song-entity}.md` — отметки о параметризации.
- `docs/features/sql-injection-hardening.md` — per-feature документ (FR-009).

## Границы

Не трогал: `specs/336-code-audit/registry.csv` (ведёт лид), чужие пакеты/BR,
деплой, контейнеры, прод, БД, главную копию `/home/nsa/Karaoke`. Публичные
контракты API не менялись: те же параметры фильтров, те же результаты на
валидных значениях; невалидные числовые значения по-прежнему дают пустую
выборку.

## Итог проверок

| Проверка | Итог |
|---|---|
| `:karaoke-app:test` (headless) | `BUILD SUCCESSFUL`, 475 тестов, 0 падений |
| `:karaoke-app:test` (как в брифе, без headless) | 475 тестов, 4 падения только в `VkPreviewWarmupClientTest`: `java.awt.AWTError: Can't connect to X11 window server using ':0'` — окружение sandbox, к пакету не относится |
| `:karaoke-web:test` | `BUILD SUCCESSFUL`, 0 падений |
| `ktlintCheck` | `BUILD SUCCESSFUL` |
| `check-knowledge-structure.sh` | `OK: knowledge/ structure valid (9/9 checks passed)` |
| `check-doc-references.py` | 0 проблем после коммита (новые пути `SqlWhere.kt`, тестов и отчёта существуют в git) |
| `check-feature-doc.sh` | `OK [docs/features/sql-injection-hardening.md]` |
| `check-subagent-isolation.sh` | exit 0; предупреждения о базовых коммитах, общих с чужими PR-ветками, — общий клон, не наши правки |

## Хэши коммитов

- `b34699f7` — production-код (`SqlWhere`, `KaraokeDbTable`, `KaraokeProcess`, `Song`, `Pictures`, `Author`, `Album`).
- `7bf87c2b` — регрессионные тесты «до/после».
- коммит документации/отчёта — см. `git log --oneline -3 587-pkg-sqli`.
