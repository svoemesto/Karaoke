# Feature: Список авторов в фильтрах админки

> **Status**: active
> **Feature Key**: author-filter-datalist
> **Last Updated**: 2026-09-24

## Что делает

В admin SPA `webvue3` поля «Автор:» в модальных окнах фильтров компонентов
«Авторы» и «Альбомы» показывают нативный список подсказок (`<datalist>`) с
именами авторов — так же, как это уже работает в фильтре компонента «Песни».

## Зачем

Раньше администратор вводил имя автора в фильтр «по памяти»: опечатка или
незнание точного написания приводили к пустому результату фильтрации без
объяснения. Подсказки убирают ручной ввод полного имени, снижают число
опечаток и приводят поведение всех трёх фильтров к единому виду.

Источник задачи: OpenProject #183 «Список авторов в фильтрах».

## Как работает

Единый источник данных для всех трёх фильтров — Vuex-геттер
`getters.songAuthorsPromise` (`webvue3/src/components/Songs/store.js`),
который выполняет `POST /api/songs/authors` и возвращает JSON-строку
`{"authors": [...]}` (бэкенд: `Song.loadListAuthors` в
`karaoke-app/.../ApiController.kt`).

Поток данных:

1. Модалка фильтра монтируется (`v-if` по кнопке «Фильтр»).
2. В `mounted()` компонент асинхронно читает `songAuthorsPromise` и
   заполняет локальный `dictAuthors` (`JSON.parse(data).authors`).
3. `<datalist>` с модалко-уникальным `id` (`authorsDictAuthorsId` /
   `albumsDictAuthorsId`) рендерит `<option>` по `dictAuthors`.
4. Поле «Автор:» связано с datalist через атрибут `list`.
5. При ошибке загрузки `dictAuthors` остаётся `[]` — поле работает как
   обычный текстовый ввод.

Изменённые файлы:

- `webvue3/src/components/Authors/filter/AuthorsFilterModal.vue`
- `webvue3/src/components/Albums/filter/AlbumsFilterModal.vue`

## Инварианты

- **MUST**: все три фильтра (Песни, Авторы, Альбомы) используют один
  источник подсказок — `songAuthorsPromise` → `POST /api/songs/authors`
  ([contracts/ui-author-datalist.md](../../specs/450-author-filter-datalist/contracts/ui-author-datalist.md)).
- **MUST**: загрузка справочника асинхронна и не блокирует открытие модалки;
  она не изменяет сохранённое значение фильтра.
- **MUST**: семантика фильтрации не меняется — «Авторы» отправляет
  `filterAuthor` (точное имя), «Альбомы» — `filterAuthorName` (точное имя →
  `author_id` на бэкенде). Пустое поле = фильтр не применяется.
- **MUST**: изменение только в `webvue3` (admin SPA); `karaoke-public` не
  затрагивается (Constitution V).
- **SHOULD**: `id` у `<datalist>` уникален в пределах страницы.

## Известные ловушки

- **Коллизия `id` datalist**: `SongsFilterModal.vue` уже использует
  `dictAuthorsId`, а `HomeView.vue` — `list_authors`. Повторное использование
  тех же `id` даёт неоднозначность `list=`, если компоненты одновременно в
  DOM. Поэтому в модалках взяты `authorsDictAuthorsId` / `albumsDictAuthorsId`.
- **«Мёртвая» привязка в фильтре альбомов**: до фикса поле ссылалось на
  `list="list_authors"`, но `<datalist id="list_authors">` объявлен только в
  `HomeView.vue` — подсказки в модалке альбомов фактически не работали.
- **Двойной парсинг**: `songAuthorsPromise` резолвится в JSON-строку, а не в
  объект; обязателен `JSON.parse(data).authors` (как в
  `SongsFilterModal.vue`).
- **Нет тест-раннера**: в `webvue3` нет unit-тестов для Vue-компонентов,
  проверка — ручной E2E (`quickstart.md`).

## Ссылки

- [Спецификация](../../specs/450-author-filter-datalist/spec.md)
- [План](../../specs/450-author-filter-datalist/plan.md)
- [Контракт UI](../../specs/450-author-filter-datalist/contracts/ui-author-datalist.md)
- [Quickstart](../../specs/450-author-filter-datalist/quickstart.md)
- [`SongsFilterModal.vue`](../../webvue3/src/components/Songs/filter/SongsFilterModal.vue) — эталон datalist
- [`AuthorsFilterModal.vue`](../../webvue3/src/components/Authors/filter/AuthorsFilterModal.vue) — правка US1
- [`AlbumsFilterModal.vue`](../../webvue3/src/components/Albums/filter/AlbumsFilterModal.vue) — правка US2
- [`Songs/store.js`](../../webvue3/src/components/Songs/store.js) — геттер `songAuthorsPromise`
