# Feature: Кнопка «Получить текст по ссылке» — обновление UI без закрытия модалки

**Spec**: [`specs/301-search-text-extract-btn/spec.md`](../../specs/301-search-text-extract-btn/spec.md)
**Status**: In progress (Pass 301, реализация в feature-ветке `301-search-text-extract-btn`)
**Created**: 2026-09-03
**Last updated**: 2026-09-03
**Source**: OpenProject #51 — «Кнопка "Получить текст по ссылке"»

## Назначение

В admin SPA `webvue3` в модалке «Поиск текста в интернете» (`webvue3/src/components/Songs/edit/SearchText.vue`) кнопка «Получить текст по ссылке» должна:

1. Располагаться **визуально под** кнопкой «Открыть на сайте» (столбиком, в одном контейнере).
2. После успешного извлечения текста — textarea справа **немедленно** показывать полученный текст, а соответствующий пункт в списке ссылок слева **переставать быть серым** — без закрытия и переоткрытия модалки.

## Bug description

**Симптом** (из OpenProject #51):

> «После нажатия на эту кнопку когда/если удалось получить текст - он должен появляться в поле текста, а пункт в списке ссылок становиться активным (не серым). А то сейчас приходится закрывать модалку и открывать заново, чтобы увидеть изменения.»

## Корневые причины (подтверждены в `research.md`)

### #1: `<textarea>` с `v-text` не реактивен в Vue 2

**Файл**: `webvue3/src/components/Songs/edit/SearchText.vue:36`

```vue
<textarea class="result-text" v-text="resultText" />
```

`v-text` в Vue 2 устанавливает DOM-свойство `el.textContent`, которое **игнорируется для `<textarea>`** (textarea хранит значение в `.value`, не в `textContent`). После изменения `resultText` computed Vue пытается обновить `textContent` textarea, но это не влияет на отображаемое значение.

Дополнительно: `<textarea />` — невалидный самозакрытый тег (HTML5). `<textarea>` не может быть void-элементом — парсер может неправильно определить границы, что усугубляет проблему.

**Совокупный эффект**: даже если бы `v-text` работал, самозакрытый тег мог сломать DOM-структуру. В результате текст НЕ появляется в textarea после извлечения, и пользователь видит «ничего не изменилось» → решает закрыть и переоткрыть модалку.

### #2: кнопка «Получить текст по ссылке» визуально не гарантированно столбиком

**Файл**: `webvue3/src/components/Songs/edit/SearchText.vue:500-505`

```css
.group-button {
  border: solid black thin;
  border-radius: 5px;
  background-color: white;
  width: 500px;
}
```

`<button>` по умолчанию `inline-block`. Хотя родитель `.st-body-column-2` — `display: block` (default), явное `display: block` на `.group-button` гарантирует стабильное вертикальное расположение кнопок в любых условиях.

### #3: textarea занимает всю высоту, кнопки «уползают»

**Файл**: `webvue3/src/components/Songs/edit/SearchText.vue:476-484` + `.st-body-column-2:430-441`

**Симптом**: после ручной проверки (итерация 2) — кнопки «Открыть на сайте» / «Получить текст по ссылке» визуально уходят за пределы правого столбца модалки.

**Корневая причина**: `.result-text` имел `height: calc(100vh - 327px)` — фиксированная высота занимала всё доступное пространство. Для двух добавленных кнопок не оставалось места, и они «наезжали» на следующий блок (`.st-footer`).

**Решение**: контейнер `.st-body-column-2` — `display: flex; flex-direction: column`. Textarea — `flex: 1 1 auto; min-height: 0`. Тогда textarea автоматически занимает всё оставшееся пространство **минус** высоту двух кнопок. Никаких магических чисел, никакой хрупкости.

**Почему `min-height: 0` обязателен**: в flex-column по умолчанию `min-height: auto`, что не даёт элементу сжаться меньше его содержимого. Без явного `min-height: 0` textarea бы резервировала место для всего текста, и кнопки снова бы уползли.

## Fix template

**ВАЖНО**: **проект на Vue 3.5.21**, а не Vue 2 (как ошибочно предполагалось в первоначальном research.md). Многие Vue2-паттерны в проекте — наследие, но Vue3 их уже не поддерживает. `this.$set` / `this.$delete` / `Vue.set` / `Vue.delete` — **не существуют** в Vue3 (вызывают `TypeError: this.$set is not a function`).

### Template fix (`SearchText.vue:36`)

**Было**:

```vue
<textarea class="result-text" v-text="resultText" />
```

**Стало**:

```vue
<!-- v-text в Vue2 устанавливает textContent, который игнорируется для <textarea>
     (textarea хранит значение в .value, не в textContent). Заменено на :value
     для реактивного обновления .value. Также исправлен самозакрытый тег
     <textarea /> → <textarea></textarea> для валидности HTML5.
     @see docs/features/search-text-extract-btn.md (OP#51) -->
<textarea class="result-text" :value="resultText"></textarea>
```

**Почему работает**: `:value` напрямую устанавливает DOM-свойство `.value` реактивно. При изменении `resultText` computed Vue обновит `.value` textarea, что отражается на отображаемом тексте.

### CSS fix (`SearchText.vue:500-505`)

**Было**:

```css
.group-button {
  border: solid black thin;
  border-radius: 5px;
  background-color: white;
  width: 500px;
}
```

**Стало**:

```css
/* Гарантирует вертикальное расположение кнопок в .st-body-column-2
   (одна под другой). Без этого <button> как inline-block может
   вести себя неожиданно при width: 500px.
   @see docs/features/search-text-extract-btn.md (FR-001, OP#51) */
.group-button {
  border: solid black thin;
  border-radius: 5px;
  background-color: white;
  width: 500px;
  display: block;
}
```

**Риск**: `.group-button` используется также в `.st-footer` модалки (кнопки «Искать заново», «Удалить результаты поиска»). Если `.st-footer` имеет `display: flex` — `display: block` на кнопках будет проигнорирован. Если нет — кнопки встанут столбиком (что, скорее всего, и так правильно). Проверяется в Scenario 4 из `quickstart.md`.

## Что НЕ меняется

- `extractLyricsFromSelectedResult` (lines 245-...) — **логика** корректна (получить обновлённый объект, обновить массив + currentResult, показать alert при ошибке). **НО** в первоначальной реализации была ошибка: `this.$set(this.searchResultsList, idx, updated)` → `TypeError: this.$set is not a function` в Vue 3. **Фикс итерация 3**: `this.searchResultsList.splice(idx, 1, updated)` — Vue3-паттерн реактивного обновления массива (используется в Authors/Albums/Pictures/SiteUsers stores).
- `SearchTextResultsTable.vue` — корректен. После `splice` prop `searchResultsList` реактивно перерисовывает строку с новым цветом фона (`gray` → `white`).
- `SubsEdit.vue` — не задействован (лишь импортирует `<search-text>`).
- Backend (`/api/song/extractlyricsbysearchresultid`) — без изменений. Контракт уже возвращает обновлённый объект.

## Ошибки в первоначальном research.md

При первоначальном код-ревью я ошибочно определил проект как Vue 2 (см. `package.json` → `"vue": "^3.5.21"`). Это привело к:

1. **Неправильная гипотеза #1**: я предположил, что `<textarea v-text>` не реактивен из-за Vue 2 footgun. На самом деле в Vue 3 `v-text` для `<textarea>` работает (Vue 3 устанавливает `.value`, а не `.textContent`). Замена на `:value` не повредила, но и не была необходима.
2. **Пропущена ошибка #3**: `this.$set` не функция в Vue 3 — это и был **главный** симптом, который проявился как `TypeError` после применения фикса JSON.parse.

**Урок**: всегда проверять версию фреймворка в `package.json` перед код-ревью, особенно когда видишь Vue2-API (`$set`, `$delete`, `v-text` для input/textarea и т.д.).

## Будущие улучшения (отдельные задачи)

- **AbortController** для отмены запросов, если пользователь закрывает модалку во время `extractLyricsBySearchResultId` (сейчас после закрытия `window.alert` будет показан уже после размонтирования — UX-проблема).
- **Автоустановка `currentId`** в `SearchTextResultsTable` при успешном извлечении, чтобы подсветка переходила `gray → blue` (а не только `gray → white`). Сейчас `currentId` устанавливается только при явном клике пользователя на ссылку.

## Связанные документы

- [`specs/301-search-text-extract-btn/spec.md`](../../specs/301-search-text-extract-btn/spec.md) — спецификация (FR-001…FR-008, User Stories 1-2, Edge Cases)
- [`specs/301-search-text-extract-btn/plan.md`](../../specs/301-search-text-extract-btn/plan.md) — implementation plan
- [`specs/301-search-text-extract-btn/research.md`](../../specs/301-search-text-extract-btn/research.md) — корневые причины, decisions, файл-список
- [`specs/301-search-text-extract-btn/data-model.md`](../../specs/301-search-text-extract-btn/data-model.md) — client state + state transitions (до/после фикса)
- [`specs/301-search-text-extract-btn/contracts/README.md`](../../specs/301-search-text-extract-btn/contracts/README.md) — reference backend-эндпоинта + UI contract
- [`specs/301-search-text-extract-btn/quickstart.md`](../../specs/301-search-text-extract-btn/quickstart.md) — 7 ручных validation scenarios
- [`specs/301-search-text-extract-btn/tasks.md`](../../specs/301-search-text-extract-btn/tasks.md) — задачи
- OpenProject #51 — исходный баг-репорт

## Версионирование

- **v301.1.0** (эта итерация): исправлен `v-text` → `:value` в textarea + добавлен `display: block` в `.group-button`. Backend не менялся.