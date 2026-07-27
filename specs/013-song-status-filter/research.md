# Phase 0 Research: Показ на проде только песен со статусом готовности >= 3

Technical Context не содержал `NEEDS CLARIFICATION` — все решения ниже приняты
на основе уже существующих в кодовой базе конвенций (найдено через
`codegraph_explore`), а не гипотез.

## Decision 1: Где физически добавлять фильтр

**Decision**: Фильтр `id_status >= 3` добавляется точечно в 4 публичных
call-site'а `karaoke-web` (`PublicApiController.zakroma`,
`PublicApiController.songs`, legacy `MainController.zakroma`,
`MainController.filter`) — не внутрь общих model-функций
`Song.loadListFromDb`/`Zakroma.getZakroma` по умолчанию.

**Rationale**: `Zakroma.getZakroma`/`getZakromaBySpecialOrder` и
`Song.loadListFromDb` — общий код в `karaoke-app`, используемый и
admin-контроллером (`karaoke-app/controllers/MainController.kt`, работает
только на admin-машине, но правило Principle V «двух-фронтенд» требует не
смешивать ответственности). Если зашить фильтр внутрь этих функций как
default-поведение, редакторы в админке случайно перестанут видеть песни в
процессе производства — а именно их отслеживание и есть основная задача
редактора.

**Alternatives considered**:
- Хардкодить `WHERE id_status >= 3` прямо в SQL `Song.loadListFromDb` —
  отклонено: ломает admin (Constitution Principle V), плюс не соответствует
  паттерну проекта «фильтрация через `args`».

## Decision 2: Как выразить фильтр в существующем query-слое

**Decision**: Передавать `args["id_status"] = ">=3"` — используя уже
существующий generic-механизм в `Song.getWhereList`
(`karaoke-app/.../model/Song.kt:7130-7148`, `listFields`), который уже
поддерживает `id_status` с операторами `>=`/`>`/`<=`/`<`/`!=`/`=` через
`split("&&")`.

**Rationale**: Этот механизм уже используется для других численных полей
(`filter_result_version`, `filter_rate` и т.п.) — переиспользование не требует
новой SQL-генерации и не рискует внести регрессию в парсинг `WHERE`.

**Alternatives considered**:
- Собственный булев флаг `onlyPublished` с отдельной веткой `where +=
  "id_status >= 3"` внутри `getWhereList` — отклонено: дублирует уже готовый
  generic-механизм, увеличивает поверхность для расхождения поведения.

## Decision 3: Источник истины для порога "3"

**Decision**: Используется то же самое значение `3`, что уже задокументировано
и используется в `StatsCacheScheduler`/`StatBySong` как определение «песня в
коллекции» (`id_status>=3 + непустой source_markers + без SKIP`, см.
`karaoke-public/src/store/modules/stats.js` и
`karaoke-web/.../services/StatsCacheScheduler.kt`).

**Rationale**: Именно расхождение между этим уже существующим определением
(видно в публичных счётчиках) и отсутствием того же фильтра в
листингах/поиске — источник бага, который фиксирует эта фича (см. spec.md
SC-003). Совпадение порога — не только упрощение, но и требование
согласованности (FR-007 spec.md).

**Alternatives considered**:
- Вынести порог в `Karaoke.properties` как настраиваемый параметр —
  отклонено как переусложнение: нет ни одного сигнала от пользователя, что
  порог должен быть настраиваемым, а не константой; статья `CLAUDE.md` прямо
  просит не проектировать под гипотетические будущие требования.

## Decision 4: Как не задеть admin

**Decision**: `Zakroma.getZakroma`/`getZakromaBySpecialOrder` получают новый
опциональный параметр (например, `onlyPublished: Boolean = false`). Публичные
call-site'ы (`karaoke-web`) передают `true`; единственный admin call-site
(`karaoke-app/controllers/MainController.kt`) не передаёт его вовсе —
поведение не меняется.

**Rationale**: Явный opt-in на стороне публичного кода надёжнее, чем
opt-out — новый publicly-callable код по умолчанию не сможет случайно
скрыть песни от админки, даже если про фильтр забудут при будущих правках.

**Alternatives considered**:
- Проверять «текущий модуль» рантаймом (например, по активному Spring
  профилю) — отклонено: неявно, сложнее тестировать вручную, не соответствует
  простому и явному стилю остального кода проекта.

## Decision 5: Какие per-feature документы обновить (FR-009)

**Decision**: `docs/features/special-orders.md` (там прямо документирована
`getZakromaBySpecialOrder`, которая теперь дополнительно фильтрует по
статусу) и `docs/features/stats.md` (там документировано определение
«коллекция» `id_status>=3`; после этой фичи стоит зафиксировать, что
листинги/поиск теперь используют то же определение, закрывая ранее
существовавшее расхождение).

**Rationale**: FR-009 Конституции требует обновления per-feature документа
при правке кода соответствующей подсистемы; обе фичи прямо затронуты этим
изменением по содержанию (не только по коду).

**Alternatives considered**: заводить отдельный 13-й per-feature документ
специально под «публичную видимость по статусу» — отклонено: это не новая
самостоятельная подсистема, а уточнение поведения двух уже существующих.
