# Feature: Параметризованные SQL-фильтры админских таблиц (SQL-инъекции)

> **Status**: active
> **Feature Key**: sql-injection-hardening
> **Last Updated**: 2026-10-24
> **Spec**: [`specs/383-pkg-sec-sqli/report.md`](../../specs/383-pkg-sec-sqli/report.md)
> **Branch**: `587-pkg-sqli`
> **Created**: 2026-10-24
> **Source**: OpenProject #383 (пакет `PKG-SEC-SQLI`, S2) бэклога аудита #336

## Что делает

Админские фильтры, которые раньше склеивались в `WHERE` конкатенацией
(значения из `@RequestParam` → строка SQL → `Statement.executeQuery`), теперь
уходят в `PreparedStatement` как `?`-параметры. Текст запроса содержит только
имена таблиц и колонок-константы; пользовательские значения не могут изменить
структуру SQL.

Пакет закрывает пять записей аудита:

| BR | Точка входа | Было | Стало |
|---|---|---|---|
| BR-028 | `KaraokeProcess.loadList` (`POST /api/processesdigests`, `GET /processes`) | `process_name LIKE '%…%'`, `ids` в `IN (…)`, числа/`process_type` — конкатенацией | `?` + `setString`/`setLong`, `ids` → `IN (?, …)`, невалидное число → `1=0` |
| BR-100 | `Song.getWhereList` → `loadListFromDb` / `countMatchingAttr` (`POST /api/songs`, `/api/songsdigests`) | восемь семейств (`ids`, `album_id`, `publish_date/time`, `status`, `song_bpm/tone/year/track`, `song_type`, `flag_*`, `filter_*`) — сырые | `SqlWhere` + `?`; числа → `Long`, операторы `>=|>|<=|<|!=` — белый список, флаги — только `+`/`-` |
| BR-175 | `Pictures.getWhereList` (`POST /api/pictures/picturesdigests`) | `id=…`, `picture_name LIKE '%…%'`, `name = '…'` | `?`; `id` → `Long`, невалидный → `1=0` |
| BR-182 | `Author.getWhereList` (`POST /api/authors/authorsdigests`, `albumsdigests?filterAuthorName=`) | `id`, `author`, `ym_id`, `vk_id`, `last_album_*` — конкатенацией | `?`; `id` → `Long`; булевы фильтры остались белым списком `+/-/true/false` |
| BR-237 | `Album.getWhereList` (`POST /api/albums/albumsdigests`) | `id`, `author_id`, `year`, `album_type` — без кавычек; `name`/`name_search` — ручное `''` | `?`; числа → `Long`; `album_type` — только `AlbumType.entries`, иначе `1=0` |

## Зачем

До фикса любой, кто имел сетевой доступ к приложению (`:8898`/`:7906`), мог
передать в фильтр `x' OR '1'='1` и получить все строки таблицы, а через
`UNION SELECT` — читать произвольные таблицы локальной БД. Аутентификации на
`/api/**` нет (`SecurityConfig` — `permitAll`, только `/api/private/**`
закрыт), поэтому инъекция была достижима без учётной записи администратора.
Пакет — часть аудита #336 (S2, данные/безопасность).

## Как работает

### `SqlWhere` и `SqlWhereBuilder`

`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SqlWhere.kt`:

- `SqlWhere(fragments, params)` — фрагменты SQL с `?` и значения в том же
  порядке; `bind(ps)` привязывает их (`setLong` для `Long`, `setString` для
  строк) и проверяет, что число `?` совпадает с числом параметров;
- `SqlWhereBuilder` — `add("LOWER(name) LIKE ?", "%$value%")` добавляет
  фрагмент и значения парой (рассинхронизировать нельзя), `raw("…")` — для
  константных условий кода, `none()` — безопасный отказ `1=0`;
- `KaraokeDbTable.loadList(where = SqlWhere, …)` исполняет запрос через
  `PreparedStatement`, если есть параметры, и через `Statement` — если их нет;
  старый overload `whereList: List<String>` оставлен для моделей, ещё не
  переведённых на `SqlWhere`.

### Числовые фильтры и белые списки

- `ids`/`album_id`/`author_id`/`year`/`song_bpm`/`process_order`/`thread_id`…
  разбираются `toLongOrNull()`; невалидное значение даёт `1=0` (пустая
  выборка), а не «пропустить условие» — иначе фильтр расширил бы выборку.
- `filter_*` в `Song.getWhereList` принимают только число и оператор из
  белого списка `>=|>|<=|<|!=` (длинные префиксы проверяются первыми).
- `flag_*` — только `+`/`-`; `album_type` — только `AlbumType.entries`.
- `publish_date`/`publish_time` — формат (`DD.MM.YY`) остаётся константой SQL,
  значение уходит параметром.

## Инварианты

- **MUST**: значения из HTTP-фильтров не попадают в текст SQL — только в
  `SqlWhere.params`; имена таблиц/колонок — константы кода или белый список.
- **MUST**: невалидное числовое значение фильтра — `1=0` (пустая выборка), а не
  игнорирование фильтра.
- **MUST**: новый фильтр добавляется в `getWhereList` как
  `where.add("<колонка> = ?", value)`, а не строковой интерполяцией.
- **MUST**: если условие нельзя параметризовать (имя таблицы/колонки), значение
  берётся из белого списка, а не экранируется.
- **SHOULD**: исполнитель выбирает `PreparedStatement` при непустых `params` и
  закрывает его в `finally` (или через `use { }`).

## Известные ловушки

- **Старый overload `loadList(whereList: List<String>)` остался.** Он нужен
  непереведённым моделям (`SearchResult`, `SiteUser`, `Dictionary` и др.); новый
  код должен использовать `where = SqlWhere`, иначе инъекция вернётся.
- **`rightFileName()` — не защита от SQL-инъекции.** Он заменяет кавычки для
  путей и исторически маскировал часть payload, но `=`, `-`, `(`, `)` не
  трогает; после фикса значения всё равно биндятся.
- **`1=0` вместо исключения.** Раньше невалидное число давало `SQLException` →
  пустой список; теперь пустой список получается явной константой. Это
  сохранение поведения, а не новая семантика API.
- **LIKE-шаблон — данные, не структура.** `%`/`_` внутри значения передаются
  параметром; это не ошибка (поиск по подстроке), но не стоит считать
  параметризацию заменой валидации бизнес-правил.
- **Параметризация не заменяет авторизацию.** `/api/**` по-прежнему
  `permitAll`; вопрос периметра — отдельные записи аудита (P-08-05 и др.).

## Ссылки

- [`specs/383-pkg-sec-sqli/report.md`](../../specs/383-pkg-sec-sqli/report.md) — отчёт пакета: было/стало, тесты «до/после», проверки.
- [`specs/336-code-audit/backlog.md`](../../specs/336-code-audit/backlog.md) — блок `### PKG-SEC-SQLI`.
- [`specs/336-code-audit/findings/P-04.md`](../../specs/336-code-audit/findings/P-04.md) — P-04-01 (BR-028).
- [`specs/336-code-audit/findings/P-18b.md`](../../specs/336-code-audit/findings/P-18b.md) — P-18-17 (BR-100).
- [`specs/336-code-audit/findings/P-16.md`](../../specs/336-code-audit/findings/P-16.md) — P-16-01 (BR-175).
- [`specs/336-code-audit/findings/P-15.md`](../../specs/336-code-audit/findings/P-15.md) — P-15-01 (BR-182).
- [`specs/336-code-audit/findings/P-14.md`](../../specs/336-code-audit/findings/P-14.md) — P-14-01 (BR-237).
- [`knowledge/domains/persistence/domain.md`](../../knowledge/domains/persistence/domain.md) — SSoT слоя БД, раздел «Параметризованный `WHERE`».
- [`knowledge/adr/0001-raw-jdbc.md`](../../knowledge/adr/0001-raw-jdbc.md) — сырой JDBC, без JPA/Hibernate.
- [`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SqlWhere.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SqlWhere.kt) — `SqlWhere`/`SqlWhereBuilder`.
- [`karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/model/SqlInjectionBeforeAfterTest.kt`](../../karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/model/SqlInjectionBeforeAfterTest.kt) — тесты «до/после».
