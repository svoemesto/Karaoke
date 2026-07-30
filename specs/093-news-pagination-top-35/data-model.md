# Data Model: Новости — пагинация над таблицей, не больше 35 строк

**Branch**: `093-news-pagination-top-35` | **Date**: 2026-07-30 | **Plan**: [plan.md](./plan.md)

**Замечание**: фича **не вносит структурных изменений** в модель данных.
Документ фиксирует, что остаётся как есть и что меняется только в UI/Vuex-сторе.

## Сущности (без изменений)

### Новость (News, таблица `tbl_news`)

Существующая сущность из `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/News.kt`.
Структура, поля, индексы, `recordhash`-триггер, sync-флаги в `KaraokeProperties.kt`
не меняются. Полный список полей — в `specs/090-news-pagination/data-model.md`
(используется без изменений).

Ключевые поля, видимые в `NewsTable.vue`:
- `id` (PK) — отображается как ID в таблице.
- `title` — заголовок, по клику открывается на редактирование.
- `category` — `air` / `premium` / `feature` / `general`.
- `publishAt` — `java.sql.Timestamp` → `yyyy-MM-dd HH:mm:ss.SSS` (см. комментарий в `NewsTable.vue:246-247`).
- `published` — флаг «Опубликовано / Запланировано».
- `source` — `auto` / `manual`.

### UI-состояние пагинации (Vuex state, `webvue3/src/components/News/store.js`)

Изменяется **только** одно значение — `newsPerPage` (через константу `NEWS_PER_PAGE`):

| Поле стора | Тип | До фичи | После фичи | Описание |
|---|---|---|---|---|
| `newsList` | `Array<News>` | без изменений | без изменений | Текущая страница списка (уже содержит только страницу, см. `specs/090-news-pagination`). |
| `newsListIsLoading` | `boolean` | без изменений | без изменений | Флаг загрузки (`<b-spinner>`). |
| `newsTarget` | `'local' \| 'remote'` | без изменений | без изменений | Целевая БД (LOCAL/REMOTE). |
| `newsTotalCount` | `number` | без изменений | без изменений | Полное число строк в `tbl_news` для текущей целевой БД. |
| `newsCurrentPage` | `number` | без изменений | без изменений | Номер текущей страницы (1-based). |
| `newsPerPage` | `number` | **50** | **35** | Размер страницы (константа `NEWS_PER_PAGE` в `store.js:11`). |

Геттеры, мутации и действия (`getNewsPerPage`, `loadNews`, `setNewsCurrentPage`,
`setNewsTarget`, `createNewsPromise`, `updateNewsPromise`, `deleteNewsPromise`)
не меняются.

## Валидации

- `totalCount` >= 0 (бэкенд всегда возвращает неотрицательное `COUNT(*)`).
- `page` в диапазоне `[0, ceil(totalCount/pageSize) - 1]`; невалидный `page`
  → пустой список + `totalCount` (см. Edge Cases спеки).
- `pageSize = 35` — фиксирован в сторе, не приходит из URL/UI (FR-004 спеки).

## Связи (без изменений)

- `News` ↔ `SyncRegistry.all` (sync `news`) — без изменений.
- `News` ↔ публичный `PublicNewsController` — без изменений (там свой `pageSize`).
- `News` ↔ `SongReleaseAnnouncementService` (auto) — без изменений.

## Что НЕ меняется

- SQL DDL — нет.
- Миграции — нет (миграция `091-fix-connection-leak` и так недавняя, в этой
  фиче схема не затрагивается).
- Сущности других модулей — нет.
- API-контракты — нет (см. `contracts/README.md`).
