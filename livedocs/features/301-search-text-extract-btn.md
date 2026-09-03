---
status: Active
slug: 301-search-text-extract-btn
related:
  - ../../specs/301-search-text-extract-btn/spec.md
  - ../../specs/301-search-text-extract-btn/plan.md
  - ../../specs/301-search-text-extract-btn/research.md
  - ../../docs/features/search-text-extract-btn.md
---

# 301 — Кнопка «Получить текст по ссылке» — обновление UI без закрытия модалки

> Drill-down — [specs/301-search-text-extract-btn/spec.md](../../specs/301-search-text-extract-btn/spec.md).

## Что делает

Исправляет баг (OpenProject #51) в admin SPA `webvue3`: в модалке «Поиск текста в интернете» (`webvue3/src/components/Songs/edit/SearchText.vue`) после нажатия кнопки «Получить текст по ссылке» UI обновляется **без закрытия модалки**:

- textarea справа показывает полученный текст;
- соответствующий пункт в списке ссылок перестаёт быть серым.

Кнопка «Получить текст по ссылке» визуально расположена **столбиком под** кнопкой «Открыть на сайте».

## Где в коде

### Template fix (`SearchText.vue:36`)

```vue
<!-- v-text в Vue2 устанавливает textContent, который игнорируется для <textarea>
     (textarea хранит значение в .value). Заменено на :value для реактивного
     обновления .value.
     @see docs/features/search-text-extract-btn.md (OP#51) -->
<!-- eslint-disable-next-line vue/html-self-closing -->
<textarea class="result-text" :value="resultText"></textarea>
```

### CSS fix (`SearchText.vue:500-512`)

```css
.group-button {
  border: solid black thin;
  border-radius: 5px;
  background-color: white;
  width: 500px;
  display: block;  /* ← добавлено: гарантирует вертикальное расположение */
}
```

### Layout fix — flex-column для `.st-body-column-2` (OP#51, итерация 2)

**Проблема** (обназа при ручной проверке): после добавления кнопки «Получить текст по ссылке» в правый столбец она «уползала под следующий блок», потому что textarea занимала всю доступную высоту (`height: calc(100vh - 327px)`), и для кнопок не оставалось места.

**Решение** — сделать `.st-body-column-2` `display: flex; flex-direction: column`, а textarea дать `flex: 1 1 auto; min-height: 0`. Тогда textarea автоматически занимает всё доступное пространство **минус** высоту двух кнопок (никаких магических чисел).

```css
/* ст-body-column-2 */
display: flex;
flex-direction: column;

/* .result-text */
flex: 1 1 auto;
min-height: 0;  /* обязательно — иначе браузер не сожмёт textarea меньше контента */
```

**Почему `min-height: 0`**: в flex-column по умолчанию `min-height: auto`, что не даёт элементу сжаться меньше его содержимого. Без явного `min-height: 0` textarea бы резервировала место для всего текста и кнопки снова уползли бы за пределы.

## Корневые причины

1. `<textarea class="result-text" v-text="resultText" />` — `v-text` в Vue2 устанавливает `textContent`, который **игнорируется для `<textarea>`** (textarea хранит значение в `.value`, не в `textContent`). Дополнительно: `<textarea />` — невалидный самозакрытый тег HTML5.
2. Кнопка «Получить текст по ссылке» без явного `display: block` могла вести себя как `inline-block` при `width: 500px` и не гарантированно располагаться столбиком.
3. **`extractLyricsBySearchResultId` action в `Songs/store.js` не парсил JSON** — возвращал raw string от `promisedXMLHttpRequest`. В результате в `SearchText.vue:extractLyricsFromSelectedResult`:
   - `updated.id === undefined` → `findIndex` возвращает -1 → `$set(searchResultsList, idx, updated)` не находит элемент → **пункт остаётся серым**.
   - `this.currentResult = updated` (строка) → `resultText = this.currentResult.text` (у строки нет `.text`) → textarea **пустая**.
   - Это **главная корневая причина** обоих симптомов OP#51. Итерация 1 (`:value` + CSS) не помогла, потому что `currentResult` оставался строкой.
   - Другие actions в этом store всегда парсят JSON внутри (`loadAuthorsDigests`, `loadAlbumsDigests`, и т.д.) — это исключение было регрессией.

## Fix #3 (итерация 2) — JSON.parse в action

**Файл**: `webvue3/src/components/Songs/store.js:2658-2673`

```js
extractLyricsBySearchResultId(ctx, payload) {
  // ... setup request ...
  return promisedXMLHttpRequest(request).then((data) => JSON.parse(data))
}
```

Полный комментарий в коде: `@see docs/features/search-text-extract-btn.md (OP#51)`.

**Результат**: после `await dispatch('extractLyricsBySearchResultId', {...})` получаем **объект** (не строку), `updated.id` корректный, `$set(searchResultsList, idx, updated)` находит элемент и реактивно обновляет его (`text !== ''` → подсветка `gray → white`), `this.currentResult = updated` (объект) → `resultText` computed возвращает `updated.text` → textarea через `:value="resultText"` реактивно показывает текст.

## Что НЕ меняется

- Backend (`/api/song/extractlyricsbysearchresultid`) — без изменений.
- Логика `extractLyricsFromSelectedResult` (`SearchText.vue:245-...`) — корректна (после итерации 3).

## Итерация 3 — Vue 3: `splice` вместо `this.$set`

**Ошибка** при тестировании итерации 2: `TypeError: this.$set is not a function`.

**Корневая причина**: проект на **Vue 3.5.21** (`package.json`), а не Vue 2. `this.$set` / `this.$delete` / `Vue.set` / `Vue.delete` **не существуют** в Vue 3 — они удалены вместе с миграцией на Proxy-реактивность.

**Эталон правильного подхода** — в других stores Vue 3:

- `webvue3/src/components/Authors/store.js:41` — `state.authorsDigest.splice(index, 1, updatedAuthor)`
- `webvue3/src/components/Albums/store.js:45` — `state.albumsDigest.splice(index, 1, updatedAlbum)`
- `webvue3/src/components/Pictures/store.js:77` — `state.picturesDigest.splice(index, 1, updatedPicture)`
- `webvue3/src/components/SiteUsers/store.js:70` — `state.siteUsersDigest.splice(index, 1, updatedUser)`

**Фикс** — `webvue3/src/components/Songs/edit/SearchText.vue:255` (после строки `const idx = ...`):

```js
// Было (Vue 2 паттерн):
this.$set(this.searchResultsList, idx, updated)

// Стало (Vue 3 паттерн):
this.searchResultsList.splice(idx, 1, updated)
```

**Урок**: при код-ревью в проекте на Vue 3 всегда проверять `package.json` на версию Vue перед анализомом legacy-кода, использующего `this.$set` / `Vue.set` / `$delete` — это Vue 2 API, в Vue 3 они не существуют.
- `SearchTextResultsTable.vue` — корректен.
- `SubsEdit.vue` — не задействован (лишь импортирует SearchText).

## Future work (отдельные задачи)

- AbortController для отмены запросов, если пользователь закрывает модалку во время `extractLyricsBySearchResultId`.
- Автоустановка `currentId` в `SearchTextResultsTable` при успешном извлечении, чтобы подсветка переходила `gray → blue` (а не только `gray → white`).