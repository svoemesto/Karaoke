# Feature: Кнопка «Получить текст по ссылке» — обновление UI без закрытия модалки

> **Status**: active
> **Feature Key**: search-text-extract-btn
> **Last Updated**: 2026-09-27
> **Spec**: [specs/301-search-text-extract-btn/spec.md](../../specs/301-search-text-extract-btn/spec.md)
> **Source**: OpenProject #51 — «Кнопка "Получить текст по ссылке"»

## Что делает

В admin SPA `webvue3` в модалке «Поиск текста в интернете»
(`webvue3/src/components/Songs/edit/SearchText.vue`) кнопка «Получить текст по ссылке»:

1. Располагается **визуально под** кнопкой «Открыть на сайте» (столбиком, в одном
   контейнере).
2. После успешного извлечения текста — textarea справа **немедленно** показывает
   полученный текст, а соответствующий пункт в списке ссылок слева **перестаёт быть
   серым** — без закрытия и переоткрытия модалки.

## Зачем

Симптом из OpenProject #51:

> «После нажатия на эту кнопку когда/если удалось получить текст — он должен
> появляться в поле текста, а пункт в списке ссылок становиться активным (не серым).
> А то сейчас приходится закрывать модалку и открывать заново, чтобы увидеть
> изменения.»

То есть результат извлечения фактически не доходил до пользователя: он вынужден был
закрывать и переоткрывать модалку, чтобы увидеть изменения. Для админа, который
подбирает текст песни по ссылкам, это ломало основной сценарий работы в модалке.

## Как работает (кратко)

Кнопка вызывает `extractLyricsFromSelectedResult` (клиент) → backend
`POST /api/song/extractlyricsbysearchresultid` → `extractLyricsBySearchResultId`
(`UtilsAI.kt`) возвращает **обновлённый объект** результата поиска. Клиент должен
реактивно обновить два места: textarea справа (`resultText`) и строку в списке ссылок
слева (цвет фона `gray` → `white`). Обе поломки — фронтовые; backend-контракт
корректен и не менялся.

Ключевые компоненты:

- `webvue3/src/components/Songs/edit/SearchText.vue` — модалка, textarea, кнопка,
  CSS-раскладка.
- `webvue3/src/components/Songs/edit/SearchTextResultsTable.vue` — список ссылок
  слева; после `splice` prop `searchResultsList` реактивно перерисовывает строку с
  новым цветом фона.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`
  (`/song/extractlyricsbysearchresultid`) + `UtilsAI.kt` — backend извлечения.
- `SubsEdit.vue` — не задействован (лишь импортирует `<search-text>`).

### Корневые причины (подтверждены в `research.md`)

#### #1: `<textarea>` с `v-text` — неверный способ обновления

**Файл**: `webvue3/src/components/Songs/edit/SearchText.vue:36` (до фикса)

```vue
<textarea class="result-text" v-text="resultText" />
```

`v-text` в Vue 2 устанавливает DOM-свойство `el.textContent`, которое
**игнорируется для `<textarea>`** (textarea хранит значение в `.value`, не в
`textContent`). Дополнительно `<textarea />` — невалидный самозакрытый тег (HTML5):
`<textarea>` не может быть void-элементом, парсер может неправильно определить
границы. Совокупный эффект — текст НЕ появляется в textarea после извлечения, и
пользователь решает закрыть и переоткрыть модалку.

#### #2: кнопка «Получить текст по ссылке» не гарантированно столбиком

**Файл**: `webvue3/src/components/Songs/edit/SearchText.vue:500-505` (до фикса)

```css
.group-button {
  border: solid black thin;
  border-radius: 5px;
  background-color: white;
  width: 500px;
}
```

`<button>` по умолчанию `inline-block`. Хотя родитель `.st-body-column-2` —
`display: block` (default), явное `display: block` на `.group-button` гарантирует
стабильное вертикальное расположение кнопок в любых условиях.

#### #3: textarea занимает всю высоту, кнопки «уползают»

**Файл**: `webvue3/src/components/Songs/edit/SearchText.vue:476-484` +
`.st-body-column-2:430-441`

**Симптом**: после ручной проверки (итерация 2) кнопки «Открыть на сайте» /
«Получить текст по ссылке» визуально уходят за пределы правого столбца модалки.

**Корневая причина**: `.result-text` имел `height: calc(100vh - 327px)` —
фиксированная высота занимала всё доступное пространство, для двух добавленных
кнопок места не оставалось, они «наезжали» на `.st-footer`.

**Решение**: контейнер `.st-body-column-2` — `display: flex; flex-direction: column`,
textarea — `flex: 1 1 auto; min-height: 0`. Тогда textarea автоматически занимает всё
оставшееся пространство **минус** высоту двух кнопок — без магических чисел.

**Почему `min-height: 0` обязателен**: в flex-column по умолчанию `min-height: auto`,
что не даёт элементу сжаться меньше его содержимого. Без явного `min-height: 0`
textarea резервировала бы место под весь текст, и кнопки снова бы уползли.

### Fix template

**ВАЖНО**: проект на **Vue 3.5.21**, а не Vue 2 (как ошибочно предполагалось в
первоначальном `research.md`). Многие Vue2-паттерны в проекте — наследие, но Vue3 их
уже не поддерживает. `this.$set` / `this.$delete` / `Vue.set` / `Vue.delete` —
**не существуют** в Vue3 (вызывают `TypeError: this.$set is not a function`).

**Template** (`SearchText.vue:36`), было:

```vue
<textarea class="result-text" v-text="resultText" />
```

стало:

```vue
<!-- v-text в Vue2 устанавливает textContent, который игнорируется для <textarea>
     (textarea хранит значение в .value, не в textContent). Заменено на :value
     для реактивного обновления .value. Также исправлен самозакрытый тег
     <textarea /> → <textarea></textarea> для валидности HTML5.
     @see docs/features/search-text-extract-btn.md (OP#51) -->
<textarea class="result-text" :value="resultText"></textarea>
```

**Почему работает**: `:value` напрямую устанавливает DOM-свойство `.value`
реактивно; при изменении `resultText` computed Vue обновит `.value` textarea, что
отражается на отображаемом тексте.

**CSS** (`SearchText.vue:500-505`), было:

```css
.group-button {
  border: solid black thin;
  border-radius: 5px;
  background-color: white;
  width: 500px;
}
```

стало (добавлено `display: block`):

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

**Риск**: `.group-button` используется также в `.st-footer` модалки (кнопки «Искать
заново», «Удалить результаты поиска»). Если `.st-footer` имеет `display: flex` —
`display: block` на кнопках будет проигнорирован; если нет — кнопки встанут столбиком
(что, скорее всего, и так правильно). Проверяется в Scenario 4 из `quickstart.md`.

### Что НЕ меняется

- `extractLyricsFromSelectedResult` (`SearchText.vue:245`) — **логика** корректна
  (получить обновлённый объект, обновить массив + `currentResult`, показать alert при
  ошибке). **НО** в первоначальной реализации была ошибка:
  `this.$set(this.searchResultsList, idx, updated)` → `TypeError: this.$set is not a
  function` в Vue 3. **Фикс итерация 3**: `this.searchResultsList.splice(idx, 1,
  updated)` — Vue3-паттерн реактивного обновления массива (используется в
  Authors/Albums/Pictures/SiteUsers stores).
- `SearchTextResultsTable.vue` — корректен. После `splice` prop `searchResultsList`
  реактивно перерисовывает строку с новым цветом фона (`gray` → `white`).
- `SubsEdit.vue` — не задействован.
- Backend (`/api/song/extractlyricsbysearchresultid`) — без изменений. Контракт уже
  возвращает обновлённый объект.

### Ошибки в первоначальном research.md

При первоначальном код-ревью проект ошибочно определён как Vue 2 (см. `package.json`
→ `"vue": "^3.5.21"`). Это привело к:

1. **Неправильная гипотеза #1**: `<textarea v-text>` не реактивен из-за Vue 2
   footgun. На самом деле в Vue 3 `v-text` для `<textarea>` работает (Vue 3
   устанавливает `.value`, а не `.textContent`). Замена на `:value` не повредила, но и
   не была необходима.
2. **Пропущена ошибка #3**: `this.$set` не функция в Vue 3 — это и был **главный**
   симптом, проявившийся как `TypeError` после применения фикса `JSON.parse`.

**Урок**: всегда проверять версию фреймворка в `package.json` перед код-ревью,
особенно когда видишь Vue2-API (`$set`, `$delete`, `v-text` для input/textarea).

## Инварианты

- **MUST**: обновление textarea — реактивно через `:value`, не через `v-text`
  (`webvue3/src/components/Songs/edit/SearchText.vue`). См.
  [AGENTS.md](../../AGENTS.md).
- **MUST**: обновление элементов массива в Vue 3 — через `splice(idx, 1, updated)`,
  не через `this.$set` / `this.$delete` (их нет в Vue 3). См.
  [AGENTS.md](../../AGENTS.md).
- **MUST**: `.st-body-column-2` — `display: flex; flex-direction: column`,
  `.result-text` — `flex: 1 1 auto; min-height: 0` (иначе кнопки уползают за границу
  столбца).
- **MUST**: `.group-button` — `display: block` (гарантия вертикального столбика).
- **MUST**: backend-контракт `POST /api/song/extractlyricsbysearchresultid` не
  меняется — он уже возвращает обновлённый объект результата.
- **SHOULD**: перед код-ревью проверять версию фреймворка в `package.json`
  ([DEVELOPMENT.md](../../DEVELOPMENT.md)); FR-009 требует обновлять этот документ при
  правке кода ([constitution.md](../../.specify/memory/constitution.md)).

## Известные ловушки

- **`v-text` на `<textarea>`** — первоначальная гипотеза (Vue 2 footgun): `v-text`
  пишет `textContent`, который для textarea игнорируется. Гипотеза оказалась неверной
  для Vue 3, но замена на `:value` безопасна.
- **`this.$set` не существует в Vue 3** — реальная причина `TypeError` и главного
  симптома; правильный паттерн — `splice(idx, 1, updated)`.
- **Самозакрытый `<textarea />`** — невалиден в HTML5 (textarea не void-элемент);
  парсер может неверно определить границы DOM.
- **`min-height: auto` по умолчанию в flex-column** — не даёт textarea сжаться
  меньше содержимого, из-за чего кнопки снова уползают; обязателен `min-height: 0`.
- **`.group-button` также в `.st-footer`** — если `.st-footer` использует
  `display: flex`, `display: block` на кнопках проигнорируется (проверять Scenario 4
  из `quickstart.md`).
- **Закрытие модалки во время `extractLyricsBySearchResultId`** — `window.alert`
  показывается уже после размонтирования (UX-проблема); `AbortController` для отмены
  запросов пока не добавлен.
- **`currentId` не устанавливается автоматически** при успешном извлечении —
  подсветка переходит `gray → white`, а не `gray → blue` (`currentId` ставится только
  при явном клике пользователя на ссылку).

## Будущие улучшения (отдельные задачи)

- **AbortController** для отмены запросов, если пользователь закрывает модалку во
  время `extractLyricsBySearchResultId`.
- **Автоустановка `currentId`** в `SearchTextResultsTable` при успешном извлечении,
  чтобы подсветка переходила `gray → blue` (а не только `gray → white`).

## Ссылки

- [`specs/301-search-text-extract-btn/spec.md`](../../specs/301-search-text-extract-btn/spec.md)
  — спецификация (FR-001…FR-008, User Stories 1-2, Edge Cases).
- [`specs/301-search-text-extract-btn/plan.md`](../../specs/301-search-text-extract-btn/plan.md)
  — implementation plan.
- [`specs/301-search-text-extract-btn/research.md`](../../specs/301-search-text-extract-btn/research.md)
  — корневые причины, decisions, файл-список.
- [`specs/301-search-text-extract-btn/data-model.md`](../../specs/301-search-text-extract-btn/data-model.md)
  — client state + state transitions (до/после фикса).
- [`specs/301-search-text-extract-btn/contracts/README.md`](../../specs/301-search-text-extract-btn/contracts/README.md)
  — reference backend-эндпоинта + UI contract.
- [`specs/301-search-text-extract-btn/quickstart.md`](../../specs/301-search-text-extract-btn/quickstart.md)
  — 7 ручных validation scenarios.
- [`specs/301-search-text-extract-btn/tasks.md`](../../specs/301-search-text-extract-btn/tasks.md)
  — задачи.
- [`webvue3/src/components/Songs/edit/SearchText.vue`](../../webvue3/src/components/Songs/edit/SearchText.vue)
  — модалка «Поиск текста в интернете», textarea, `.group-button`,
  `extractLyricsFromSelectedResult`.
- [`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt)
  — `POST /song/extractlyricsbysearchresultid`.
- [`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsAI.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsAI.kt)
  — `extractLyricsBySearchResultId` (извлечение текста).

## Версионирование

- **v301.1.0** (эта итерация): исправлен `v-text` → `:value` в textarea + добавлен
  `display: block` в `.group-button`. Backend не менялся.
