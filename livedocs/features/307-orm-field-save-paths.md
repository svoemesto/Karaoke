---
status: Active
slug: 307-orm-field-save-paths
related:
  - ../../specs/307-special-authors-zakroma-order/spec.md
  - ../../docs/features/zakroma-tiles-sort-order.md
  - ../runbooks/how-to-add-orm-field.md
  - ../runbooks/how-to-migrate-db.md
---

# 307 — ORM field save paths: lesson from `tbl_authors.sort_order` (LiveDoc)

> Drill-down — [specs/307-special-authors-zakroma-order/spec.md](../../specs/307-special-authors-zakroma-order/spec.md).
> Per-feature документ — [docs/features/zakroma-tiles-sort-order.md](../../docs/features/zakroma-tiles-sort-order.md).

## Что за урок

При добавлении нового ORM-поля (например, `tbl_authors.sort_order` в спеку 307)
**обновление аннотации `@KaraokeDbTableField` в Kotlin-модели НЕ достаточно**.
В проекте есть **минимум 4 независимых пути записи**, и каждый из них
нужно проверить и при необходимости обновить.

Этот LiveDoc фиксирует урок, чтобы в следующей ORM-фиче не повторить ту же ошибку.

## Как проявилась ошибка в спеке 307

1. Добавил поле `sortOrder: Int = 0` + `@KaraokeDbTableField(name = "sort_order")` в `Author.kt`.
2. Обновил `Author.loadAuthorTilesWithCounts` (SELECT + ORDER BY).
3. Добавил поле в `AuthorDTO` и `AuthorTilePublicDto` (для публичного API).
4. Обновил `PublicApiController.authorsTiles` (проброс `row.sortOrder`).
5. Добавил редактируемую колонку `Sort` в `webvue3/src/components/Authors/AuthorsTable.vue`.
6. Метод `onSortOrderChange` шлёт `POST /api/authors/updateauthor` с `fldName: "sortOrder", fldValue: value`.

**Пропустил**: эндпоинт `apisUpdateAuthor` в `karaoke-app/.../controllers/ApiController.kt`
— **белый список** `@RequestParam`-ов. Поле `sortOrder` там не было объявлено,
поэтому Spring **молча игнорировал** его при сохранении. Изменения в БД не
доходили. Пользователь заметил по поведению: «поле `sortOrder` при редактировании
на вебе не сохраняется».

После исправления (добавил `@RequestParam(required = false) sortOrder: Int?` +
`sortOrder?.let { v -> it.sortOrder = v }`) — заработало.

## 4 пути записи ORM-поля в Karaoke

При добавлении или изменении ORM-поля (`@KaraokeDbTableField`) проверь **все** эти пути:

### 1. Автоматический reflection-write через `KaraokeDbTable.toSqlToInsert()`

- Файл: `karaoke-app/.../model/KaraokeDbTable.kt::toSqlToInsert` (≈ строка 205).
- Работает через reflection по `@KaraokeDbTableField`. **Если поле аннотировано
  корректно, INSERT/UPDATE подхватит его автоматически.**
- Покрывает: `entity.save()` (используется почти везде в sync и в ORM-операциях).
- **НЕ покрывает**: ручные `connection.prepareStatement(...)` блоки в `*Service` /
  `*Controller`, отдельные `RawSql`-методы и — **особенно** — REST-эндпоинты
  с явными `@RequestParam`-whitelist'ами.

### 2. REST-эндпоинты с `@RequestParam` whitelist (самый частый blind spot)

- Spring **молча отбрасывает** параметры, которых нет в сигнатуре метода
  (`unknown=...` query не вызывает ошибку, просто игнорируется).
- Типичные места в проекте:
  - `karaoke-app/.../controllers/ApiController.kt` — `apisUpdateAuthor`,
    `apisUpdateAlbum`, `apisUpdateSong`, `apisUpdatePicture` и подобные.
  - Любой `@PostMapping` / `@GetMapping` с `?id=...&field1=...&field2=...`
    в URL (Spring `x-www-form-urlencoded`).
- **Чек**: при изменении ORM-модели — найти все такие эндпоинты по
  `grep -rn "RequestParam" karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/`
  и `grep -rn "RequestParam" karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/`
  и убедиться, что новые поля проброшены.

### 3. Прямые `connection.prepareStatement("UPDATE tbl_...")` блоки

- В `*Service`, `*Repository` (если такие есть), в `loadList`, `countWith*` методах.
- Файл-примеры: `karaoke-app/.../model/Song.kt`, `karaoke-app/.../model/Pictures.kt`,
  `karaoke-app/.../model/Zakroma.kt` — множество прямых SQL-блоков с `UPDATE`.
- **Чек**: при изменении ORM-модели — найти все `UPDATE tbl_<table_name>` блоки
  и убедиться, что новое поле либо включается в `UPDATE ... SET col1=?, col2=?, colN=?`,
  либо игнорируется намеренно.

### 4. Sync (LOCAL↔SERVER) через `recordhash`

- Файл: `deploy/karaoke-db/NN_*.sql` — функция `update_tbl_<table>_recordhash()`.
- Если поле добавлено в таблицу и ORM-аннотацию, но **забыли добавить в
  recordhash-md5** — sync перестанет видеть изменения, записи навсегда
  «разъедутся» между LOCAL и SERVER.
- **Чек**: в любой миграции, которая добавляет колонку в sync-таблицу,
  ОБЯЗАТЕЛЬНО пересоздать `update_tbl_<table>_recordhash()` с новым полем
  в md5-формуле + backfill recordhash для существующих строк
  (см. миграцию `46_author_sort_order.sql` как шаблон).

## 5. SQL WHERE / ORDER BY — бизнес-логика фильтрации и сортировки

> **Pass 311 lesson**: при изменении ORM-поля, влияющего на публичный SQL (например,
> `WHERE` или `ORDER BY` в `loadList`-методах), нужно проверить **обе части**:
>
> 1. **WHERE**: новая колонка может влиять на условие включения/исключения строк.
>    В спеке 307 мы добавили `sort_order != 0` ИЛИ `ready_songs_count > 0` — потому
>    что авторы с принудительным порядком должны быть видны даже без готовых песен
>    («Саундтреки» в нашей БД имеет `sort_order=1` и `ready_songs_count=0`).
>
> 2. **ORDER BY**: направление сортировки должно соответствовать **бизнес-смыслу**.
>    В спеке 307 сказано «ненулевые `sort_order` **ПЕРЕД** нулевыми», но я реализовал
>    `ORDER BY sort_order ASC` — что означает «от меньшего к большему», и `0 < 1`,
>    поэтому нулевые оказывались **раньше** ненулевых. Правильно:
>    `ORDER BY (sort_order = 0), sort_order ASC, author ASC` — булево `(sort_order = 0)`
>    для нулевых = `true`, в Postgres `false < true`, поэтому нулевые уезжают в конец.

**Чек при изменении SQL в `loadList` / `loadAuthorTilesWithCounts`**:
- [ ] WHERE: новые поля учтены в условиях включения.
- [ ] ORDER BY: направление сортировки соответствует спеке (проверить **граничные значения**
      отрицательных, нулевых, положительных — не только «один ненулевой vs все нулевые»).
- [ ] Верификация на фикстуре из спеки: воспроизвести сценарий в psql.

## Чек-лист «я добавил ORM-поле, всё ли я обновил»

Используй этот чек-лист **до коммита**:

- [ ] Миграция БД: колонка добавлена, **recordhash-триггер пересоздан с новым полем**.
- [ ] Kotlin-модель: поле аннотировано `@KaraokeDbTableField(name = "...")`.
- [ ] DTO: поле проброшено в JSON-DTO (если используется через API).
- [ ] SELECT-запросы: `*loadList*`, `*loadById*`, `*loadAuthorTilesWithCounts*`
      и подобные обновлены для включения нового поля (если нужно в payload).
- [ ] UPDATE-запросы: ручные `UPDATE` блоки в `*Service`/`*Repository` обновлены.
- [ ] REST-эндпоинты: **все `@RequestParam` whitelist'ы**, через которые пишется
      поле, обновлены. Поиск: `grep -rn "RequestParam" karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/`
      и `karaoke-web/src/main/kotlin/.../controllers/`.
- [ ] Sync: recordhash-md5 включает новое поле (Constitution Principle II/III).
- [ ] Frontend admin: если поле редактируется через админку — соответствующий
      метод `onXChange` шлёт правильный `fldName/fldValue` (см. примеры в
      `webvue3/src/components/Authors/AuthorsTable.vue`).
- [ ] Compile + ktlint + ESLint + bootJar + Vite build (AGENTS.md §"Обязательная
      проверка после ЛЮБОГО изменения").

## Связанные документы

- Спека 307 (урок случился здесь): [specs/307-special-authors-zakroma-order/spec.md](../../specs/307-special-authors-zakroma-order/spec.md)
- Per-feature документ 307: [docs/features/zakroma-tiles-sort-order.md](../../docs/features/zakroma-tiles-sort-order.md)
- Runbook «как добавить ORM-поле правильно»: [livedocs/runbooks/how-to-add-orm-field.md](../runbooks/how-to-add-orm-field.md)
- Runbook «как мигрировать БД»: [livedocs/runbooks/how-to-migrate-db.md](../runbooks/how-to-migrate-db.md)
- Constitution: `.specify/memory/constitution.md` §III (sync через recordhash)
- Архитектурные заметки: [architecture-notes.md](../../docs/architecture-notes.md) (Pass 307)
