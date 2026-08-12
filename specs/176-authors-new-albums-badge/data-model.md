# Data Model: Бейдж «новые альбомы» в пункте меню «Авторы»

**Feature**: 176-authors-new-albums-badge
**Date**: 2026-08-12
**Spec**: [spec.md](./spec.md)

## Сущности

Фича **не вводит новых сущностей** и **не меняет схему БД**. Переиспользует существующую сущность `Author` (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt`).

## Author (существующая)

**Таблица**: `tbl_authors` (PostgreSQL).

**Колонки, задействованные фичей**:

| Колонка | Тип | Смысл | Используется в `haveNewAlbum` |
|---------|-----|-------|------------------------------|
| `watched` | `BOOLEAN NOT NULL DEFAULT false` | Автор помечен как «следим» | да (`watched = true`) |
| `ym_id` | `VARCHAR` (пустая строка = нет) | ID автора на Яндекс.Музыке | да (`ym_id <> ''`) |
| `vk_id` | `VARCHAR` (пустая строка = нет) | ID автора на VK | да (`vk_id <> ''`) |
| `last_album_ym` | `VARCHAR` (пустая строка = нет) | Название/идентификатор последнего альбома по YM | да (`last_album_ym <> last_album_processed`) |
| `last_album_vk` | `VARCHAR` (пустая строка = нет) | Название/идентификатор последнего альбома по VK | да (`last_album_vk <> last_album_processed`) |
| `last_album_processed` | `VARCHAR` (пустая строка = нет) | Последний обработанный альбом (после `last_album_processed` мы «обработали всё до этого») | да (см. выше) |

**Вычисляемое свойство** (источник истины, уже существует в `Author.kt:94-97`):

```kotlin
val haveNewAlbum: Boolean get() =
    watched &&
        (ymId != "" || vkId != "") &&
        (lastAlbumYm != lastAlbumProcessed || lastAlbumVk != lastAlbumProcessed)
```

**SQL-зеркало** (уже существует в `Author.getWhereList` `Author.kt:170-178`):

```sql
watched = true
  AND (ym_id <> '' OR vk_id <> '')
  AND (last_album_ym <> last_album_processed OR last_album_vk <> last_album_processed)
```

### Связь бейджа и фильтра AuthorsFilterModal

| Источник | Что использует | Где |
|----------|----------------|-----|
| UI-фильтр «Новый альбом» | `Author.loadList(whereArgs = mapOf("haveNewAlbum" to "+"))` | `AuthorsFilterModal.vue`, `ApiController.apisAuthorsDigest` |
| Бейдж в сайдбаре | `Author.countWithNewAlbum(db)` (новый) | `App.vue`, новый endpoint `/api/authors/withnewalbumcount` |

Оба источника ОБЯЗАНЫ использовать идентичное SQL-условие. Фича **не** дублирует логику — новый companion-метод `Author.countWithNewAlbum` копирует SQL-условие (с комментарием-ссылкой на `Author.haveNewAlbum`) из `Author.getWhereList["haveNewAlbum=+"]`.

## Клиентские state-поля (Vuex)

| Поле | Тип | Default | Назначение |
|------|-----|---------|------------|
| `authorsWithNewAlbumCount` | `Number` | `0` | Значение для бейджа в `App.vue` |

Хранится в `webvue3/src/components/Authors/store.js` (рядом с `authorsDigest`, `authorsDigestIsLoading`, `authorsTableCurrentPage`).

## БД-миграции

**Не требуются.** Существующая схема `tbl_authors` уже содержит все нужные колонки, никаких новых полей/индексов/триггеров не добавляется.

Запрос бейджа — простой `SELECT COUNT(*) FROM tbl_authors WHERE ...` по 5 колонкам без индексов. На таблице из ~18k строк это ~миллисекунды даже с Seq Scan (Postgres seq_page_cost ~4 на SSD), в пределах SC-004 (≤ 100 ms).

## Связи и инварианты

- `Author` синхронизируется LOCAL → SERVER через `SyncTarget<Author>` (см. `karaoke-app/.../sync/SyncTarget.kt`). Бейдж читает только LOCAL (`WORKING_DATABASE`), что согласовано с уже существующим `/api/authors/authorsdigests`.
- `last_album_processed` обновляется при обработке альбома (логика вне scope этой фичи). Бейдж автоматически отражает это изменение в течение ≤ 20 сек благодаря polling.
- Тег SKIP (`tbl_authors.skip = true`) НЕ исключается из подсчёта — это намеренно: пользователь хочет видеть «новый альбом даже у автора с SKIP» (см. существующее поведение фильтра `haveNewAlbum=+`, который тоже не учитывает `skip`).
