# Спецзаказные авторы (Special Orders)

> **Status**: active
> **Feature Key**: special-orders
> **Last Updated**: 2026-07-24

## Что делает

Помечает авторов, у которых в коллекции всего 1-2 песни (сделаны по индивидуальному
заказу, а не вся дискография) отдельным флагом `tbl_authors.is_special_order`, скрывает
их из основного списка авторов в `/zakroma` и показывает виртуальной плашкой «Отдельные
песни разных авторов» в конце того же экрана — с той же структурой Автор→Альбом→Песни,
что и у обычных авторов.

## Зачем

Гипотеза H1.20 (`docs/strategy/growth-audit.md`): смешение авторов «с полной дискографией»
и авторов «с одной песней по спецзаказу» в одном алфавитном списке путает пользователя
(«почему у этого автора всего 1 песня?»). Отдельная категория снимает вопрос, не убирая
контент из каталога. Roadmap: **M-23**.

## Как работает (кратко)

### Признак `is_special_order`

- Колонка `tbl_authors.is_special_order BOOLEAN DEFAULT FALSE NOT NULL`
  (`deploy/karaoke-db/27_author_special_order.sql`), входит в `recordhash`.
- Backfill при миграции: `true` для авторов с `<3` готовых песен
  (`tbl_settings WHERE id_status>=3 GROUP BY song_author HAVING count(*)<3`).
- Ручное управление — `webvue3/src/components/Authors/AuthorsTable.vue`: колонка «Спец»
  (🪙) + модалка `changeValue` (`fldIsBoolean: true`) → `ApiController.apisUpdateAuthor`
  сохраняет напрямую через `it.save()` (не через прямой SQL UPDATE, т.к. `Author` — не
  syncable в том же смысле, что `Settings`; см. Инварианты).

### Backend: batch-эндпоинт (без N+1)

- `Zakroma.getZakroma(author, ...)` — как раньше, один автор по точному совпадению имени.
- **`Zakroma.getZakromaBySpecialOrder(database, storageService, storageApiClient)`** (новое) —
  находит все имена авторов с `is_special_order=true` через
  `Settings.loadListAuthors(isSpecialOrder = true)`, затем одним SQL-запросом грузит все
  их песни через уже существующий фильтр `author_in` в `Settings.getWhereList` (тот же
  фильтр, что используется для резолва алиасов авторов при поиске). Обе функции
  переиспользуют общую приватную `buildFromSettings()` (группировка Автор→Альбом→Песни).
- `PublicApiController.zakroma()` — параметр `specialBucket: Boolean` (`?specialBucket=true`)
  переключает на batch-путь вместо `author`.

### Frontend

- `store/modules/zakroma.js` → `loadSpecialBucket()` — один запрос
  `GET /api/public/zakroma?specialBucket=true`, результат кладётся в `state.specialBucket`
  (тот же тип `List<ZakromaPublicDto>`, что и обычный `zakroma`).
- `ZakromaView.vue`:
  - Плашка-тайл встраивается **внутрь** сетки тайлов авторов через именованный слот
    `trailing` в `AuthorTiles.vue` — визуально не отличается по размеру от обычных тайлов.
  - При клике (`?specialBucket=true` в URL) `displayedZakroma` возвращает `specialBucket`
    напрямую (реальная структура Автор→Альбом→Песни на каждого спецзаказного автора,
    без схлопывания в один искусственный «плоский» автор).
  - `watch.specialBucket` подгружает `readiness`/`membership` для id-шников этих песен —
    без этого иконки готовности плеера вечно висели в `loading` (watcher был только на
    `zakroma`, не на `specialBucket`).
- `AuthorPlaylistView.vue` — баннер «По спецзаказу» вверху страницы конкретного автора,
  если `isSpecialOrder=true`.

## Инварианты / правила

- **MUST**: любое новое boolean-поле `isXxx` в публичных DTO (`AuthorTilePublicDto`,
  `AuthorDTO`) — с `@get:JsonProperty("isXxx")`, иначе Jackson роняет префикс и фронт не
  получает значение (см. Q&A в `AGENTS.md`).
- **MUST**: batch-загрузка спецзаказных авторов — одним SQL-запросом через `author_in`
  (`Settings.getWhereList`), не циклом `for (author in names) loadOne(author)` — иначе
  N+1 и «вечный» индикатор загрузки при большом числе спецзаказных авторов.
- **MUST**: при изменении набора полей `tbl_authors`, участвующих в `recordhash`,
  пересоздавать триггер `update_tbl_authors_recordhash()` на LOCAL и PROD (см.
  [dual-db-sync.md](./dual-db-sync.md)).
- **SHOULD**: визуальные элементы спец-тайла, скопированные из `AuthorTiles.vue`
  (`.at-pic`/`.at-namerow`/`.at-name`/`.at-count`), стилизуются отдельно в `ZakromaView.vue`
  под префиксом `.km-special-tile` — Vue `<style scoped>` не пробрасывает стили компонента
  на content, переданный через `<slot>` из родителя.

## Известные ловушки

- **Схлопывание в один «виртуальный автор»** (исторический баг): ранняя реализация строила
  единственный псевдо-автор с одним псевдо-альбомом и подмешивала имя реального автора в
  `songName` (`"Автор — Песня"`), из-за чего иерархия Автор→Альбом визуально исчезала.
  Правильный путь — рендерить реальный `specialBucket` (массив по авторам) через тот же
  шаблон, что и обычные авторы.
- **Скопированные CSS-классы без стилей**: `.at-pic`/`.at-namerow`/`.at-name`/`.at-count`
  использовались в разметке спец-тайла, но были определены только в `<style scoped>`
  `AuthorTiles.vue` — на элементы, отрендеренные в другом компоненте (даже если переданы
  через `<slot>`), scoped-стили не действуют. Нужно либо дублировать нужные правила в
  компоненте-потребителе, либо использовать `:slotted()`.
- **Отдельный watcher на каждый источник данных**: если добавляется третий источник
  данных с песнями (кроме `zakroma`/`specialBucket`), не забыть завести для него свой
  `watch` на `readiness.load()`/`membership.load()` — иначе тот же баг «вечный спиннер».

## Ссылки

### Ключевые классы и файлы

- [`Zakroma.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Zakroma.kt) — `getZakroma`/`getZakromaBySpecialOrder`/`buildFromSettings`
- [`PublicApiController.kt`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt) — `/api/public/zakroma`, `/api/public/authors`, `/api/public/authors-tiles`
- [`ZakromaPublicDto.kt`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/ZakromaPublicDto.kt) — DTO Автор→Альбом→Песни
- [`ZakromaView.vue`](../../karaoke-public/src/views/ZakromaView.vue), [`AuthorTiles.vue`](../../karaoke-public/src/components/AuthorTiles.vue), [`AuthorPlaylistView.vue`](../../karaoke-public/src/views/AuthorPlaylistView.vue)
- [`zakroma.js`](../../karaoke-public/src/store/modules/zakroma.js) — Vuex-модуль
- [`27_author_special_order.sql`](../../deploy/karaoke-db/27_author_special_order.sql) — миграция + backfill

### Связанные документы

- [specs/008-special-orders/spec.md](../../specs/008-special-orders/spec.md) — исходная спецификация
- [dual-db-sync.md](./dual-db-sync.md) — recordhash-триггеры, `author_in`-фильтр
- [docs/strategy/growth-audit.md](../strategy/growth-audit.md) — гипотеза H1.20
- [docs/strategy/growth.md](../strategy/growth.md) — M-23 в roadmap
- [AGENTS.md](../../AGENTS.md) — Jackson `is`-boolean Q&A
