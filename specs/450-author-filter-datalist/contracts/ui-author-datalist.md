# Contract: UI — datalist подсказок авторов в фильтрах

**Branch**: `450-author-filter-datalist` | **Phase**: 1 | **Date**: 2026-09-24

Фича не вводит новых сетевых API. Единственный контракт — **UI-контракт**
поведения поля «Автор:» в двух модалках фильтров admin SPA `webvue3`.
Существующий backend-контракт `POST /api/songs/authors` не меняется и
описан ниже как зависимость.

## 1. Источник данных (существующий, не изменяется)

**Эндпоинт**: `POST /api/songs/authors`

- **Handler**: `karaoke-app/.../controllers/ApiController.kt:2095`
  (`fun authors()` → `Song.loadListAuthors(WORKING_DATABASE)`).
- **Response**: JSON `{"authors": ["<имя>", ...]}` — отсортированный список
  строк.
- **Доступ со фронта**: Vuex-геттер `getters.songAuthorsPromise`
  (`webvue3/src/components/Songs/store.js:941`) → `promisedXMLHttpRequest({ method: 'POST', url: '/api/songs/authors' })`,
  резолвится в JSON-строку; компонент парсит `.authors`.
- **Не меняется** этой фичей.

## 2. UI-контракт: `AuthorsFilterModal.vue`

### Разметка поля «Автор:»

```html
<datalist id="authorsDictAuthorsId">
  <option v-for="val in dictAuthors" :key="val" :value="val" />
</datalist>
...
<input
  v-model="authorsFilterAuthor"
  list="authorsDictAuthorsId"
  class="afm-input-field"
/>
```

### Поведение

| Условие | Ожидаемое поведение |
|---------|---------------------|
| `dictAuthors` пуст (загрузка/ошибка) | Поле — обычный текстовый ввод; ошибок нет |
| `dictAuthors` заполнен, поле пустое/фокус | Показываются все доступные подсказки |
| Пользователь вводит подстроку | Браузер фильтрует подсказки по подстроке |
| Пользователь выбирает подсказку | В поле подставляется точное имя |
| Применение фильтра при пустом поле | Параметр `filterAuthor` не отправляется (все записи) |
| Применение фильтра с именем | Отправляется `filterAuthor=<имя>` (точное совпадение) |
| Сохранённое значение (`getWebvueProp`) | Подставляется в поле; справочник его не перезаписывает |

### Инварианты

- **MUST**: `dictAuthors` заполняется асинхронно и не блокирует открытие
  модалки (FR-008).
- **MUST**: загрузка/ошибка справочника не сбрасывает `authorsFilterAuthor`.
- **MUST**: семантика `filterAuthor` не меняется (FR-004).
- **SHOULD**: `id` datalist уникален для страницы (`authorsDictAuthorsId`).

## 3. UI-контракт: `AlbumsFilterModal.vue`

### Разметка поля «Автор:»

```html
<datalist id="albumsDictAuthorsId">
  <option v-for="val in dictAuthors" :key="val" :value="val" />
</datalist>
...
<input
  v-model="albumsFilterAuthorName"
  list="albumsDictAuthorsId"
  class="afm-input-field"
/>
```

### Поведение

| Условие | Ожидаемое поведение |
|---------|---------------------|
| `dictAuthors` пуст | Поле — обычный текстовый ввод |
| `dictAuthors` заполнен | Подсказки по авторам, фильтрация подстроки браузером |
| Выбор подсказки | Точное имя подставляется в `albumsFilterAuthorName` |
| Применение фильтра | `filterAuthorName=<имя>` → бэкенд резолвит `author_id` (точное имя), либо пустой результат |
| Пустое поле | `filterAuthorName` не отправляется (все альбомы) |

### Инварианты

- **MUST**: прежняя привязка `list="list_authors"` (ссылавшаяся на
  несуществующий в этой модалке datalist) заменяется на локальный
  `albumsDictAuthorsId`.
- **MUST**: семантика `filterAuthorName` (точное имя → `author_id` через
  `Author.getAuthorByName`) не меняется.
- **MUST**: `dictAuthors` не блокирует открытие модалки.

## 4. Совместимость и границы

- **Backend**: без изменений (`karaoke-app`, `karaoke-web`).
- **Public frontend**: `karaoke-public` не затрагивается (Constitution V).
- **Персистентность фильтров**: server-side key/value
  (`setWebvueProp`/`getWebvueProp`) не меняется.
- **Новые зависимости**: нет.

## 5. Критерии приёмки контракта

- **AC-1**: В обеих модалках при непустом `dictAuthors` поле «Автор:»
  предлагает список авторов.
- **AC-2**: Набор подсказок идентичен набору в `SongsFilterModal` (один
  источник).
- **AC-3**: Выбор подсказки подставляет точное имя; фильтр применяется как
  раньше.
- **AC-4**: При недоступном `/api/songs/authors` модалки открываются и
  поля работают как текстовый ввод.
