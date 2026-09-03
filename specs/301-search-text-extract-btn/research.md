# Research: Кнопка «Получить текст по ссылке» — обновление UI без закрытия модалки

**Дата**: 2026-09-03
**Связанная спека**: [spec.md](spec.md)
**Ветка**: `301-search-text-extract-btn`

## Краткое summary

Баг в OP#51 состоит из двух связанных проблем в одном модальном окне (`webvue3/src/components/Songs/edit/SearchText.vue`):

1. **Расположение кнопки «Получить текст по ссылке»** — в задаче указано «должна быть ПОД кнопкой "Открыть на сайте" (в том же диве, одно под другой)». Текущая структура template (lines 33-50) уже содержит обе кнопки в `<div class="st-body-column-2">`. CSS родителя — `display: block` (default), `.group-button` имеет `width: 500px`. **По CSS они должны идти столбиком**. Возможно, проблема в том, что `<button>` без явного `display: block` ведёт себя как `inline-block` и при определённой ширине родителя ведёт себя неожиданно.

3. **UI не обновляется после нажатия** — textarea не отображает полученный текст, пункт в списке остаётся серым.

## Корневые причины (подтверждены код-ревью)

### Причина #1: `<textarea>` с `v-text` не реактивен в Vue2

**Файл**: `webvue3/src/components/Songs/edit/SearchText.vue:36`

```vue
<textarea class="result-text" v-text="resultText" />
```

**Проблема**: `v-text` в Vue2 устанавливает `el.textContent` — DOM-свойство, которое **игнорируется для `<textarea>`** (textarea хранит значение в `.value`, не в `textContent`). После изменения `resultText` (computed) Vue пытается обновить `textContent` textarea, но это не влияет на отображаемое значение.

**Дополнительный фактор**: `<textarea />` — самозакрытый тег, что невалидно для HTML5 (textarea **не может быть void-элементом**). HTML-парсер может интерпретировать это странно. Правильно — `<textarea ...></textarea>`.

**Совокупный эффект**: даже если бы `v-text` работал, самозакрытый тег мог сломать DOM-структуру (Vue может неправильно определить конец textarea).

### Причина #2: текущая логика обновления `searchResultsList` через `this.$set` должна работать

**Файл**: `webvue3/src/components/Songs/edit/SearchText.vue:244-248`

```js
const idx = this.searchResultsList.findIndex((r) => r.id === updated.id)
if (idx !== -1) {
  this.$set(this.searchResultsList, idx, updated)
}
this.currentResult = updated
```

`this.$set` для элемента массива — корректный способ реактивного обновления в Vue2. Дочерний `SearchTextResultsTable` получает `searchResultsList` как prop — он должен среагировать на изменение.

**Однако** есть нюанс: `currentId` хранится в `data()` `SearchTextResultsTable` и устанавливается при **клике** пользователя (line 45: `this.currentId = searchResult.id`). Если пользователь нажал «Получить текст по ссылке» **без предварительного клика** на ссылку в списке (например, выбрал через какой-то другой механизм), `currentId` будет undefined → проверка `currentId === id` не пройдёт → подсветка будет `gray` или `white`, но не `blue`.

**Но** задача говорит «пункт становится активным (не серым)» — это значит `text !== ''`, что достаточно для подсветки `white`. После `$set` элемент массива становится `{ text: '...' }` → `SearchTextResultsTable` перерендерит строку → `:style.backgroundColor` пересчитается → `gray` → `white`. **Это должно работать корректно**.

**Гипотеза-2 уточнение**: возможно, проблема в том, что **до нажатия кнопки «Получить текст» `currentResult` мог быть установлен через `selectedResult`** (line 226 — event от `SearchTextResultsTable`). После нажатия `extractLyricsFromSelectedResult` → `currentResult = updated` → `searchResultsList[idx] = updated` (через `$set`). Всё это в Vue2 синхронно, реактивность должна работать.

**Заключение**: причина #2 — скорее всего, `currentResult` обновляется правильно, но **textarea не показывает** (причина #1). Пользователь видит «не изменилось» → решает, что нужно закрыть и открыть. После переоткрытия textarea уже содержит результат (потому что при mounted() происходит `getResultsList`, и результат там уже с `text`).

### Причина #3 (опционально): расположение кнопок

Текущая структура template предполагает столбик. Если визуально они не столбиком — возможно, CSS `.group-button` нужно явно `display: block`.

## Решения

### Decision 1: фикс `<textarea>` через `:value` вместо `v-text`

**Выбор**: заменить `<textarea class="result-text" v-text="resultText" />` на `<textarea class="result-text" :value="resultText" />`.

**Rationale**:
- `:value` устанавливает DOM-свойство `value` напрямую — это **именно то, что нужно для `<textarea>`**.
- Vue2 реактивно обновит `.value` при изменении `resultText`.
- Минимальное изменение (1 строка).
- Также: исправить самозакрытый тег → `<textarea ...></textarea>` (полная форма) для валидности HTML5.

**Альтернативы, отклонённые**:

| Альтернатива | Почему отклонена |
|--------------|------------------|
| `v-model="resultText"` | Требует, чтобы `resultText` был writable (не computed без setter). Нужно рефакторить computed. Избыточно. |
| `:value="resultText" @input="..."` | Полноценный двусторонний binding — не нужен (textarea только отображает). |
| `ref` + `watch` | Добавляет boilerplate, сложнее поддерживать. |

### Decision 2: гарантия вертикального расположения кнопок через CSS

**Выбор**: добавить `display: block` в `.group-button` явно.

**Rationale**: гарантирует, что кнопки идут столбиком даже если родитель имеет `display: flex; flex-direction: row` (`.st-body`). Сейчас это работает случайно — фикс делает поведение явным.

**Альтернатива** (отклонена): завернуть кнопки в `<div class="st-body-column-2-buttons">` с `display: flex; flex-direction: column`. Более громоздко, требует дополнительного HTML.

### Decision 3: НЕ менять логику `extractLyricsFromSelectedResult`

**Выбор**: текущая логика обновления (lines 244-248) — корректна. Не трогаем.

**Rationale**: `$set` + присвоение `currentResult = updated` — это правильный Vue2 паттерн для реактивного обновления prop-массива и текущего объекта. Если после фикса #1 пользователь всё ещё видит серую подсветку — баг в другом месте, и это уже отдельная задача.

**Если после фикса #1 проблема остаётся**: добавить явный `v-if="searchAsyncId"` с key на `SearchTextResultsTable` (принудительный re-mount) или emit'ить событие из `extractLyricsFromSelectedResult` в `SearchTextResultsTable` через ref. Это **out of scope** для текущей задачи.

### Decision 4: AbortController для race-condition (НЕ делаем в этой задаче)

**Выбор**: оставить как есть — если пользователь закроет модалку во время запроса, `window.alert` после закрытия будет показан.

**Rationale**: scope этой задачи — UX-фикс «обновить UI без закрытия модалки», а не отмена запросов. Edge case «закрытие во время запроса» — отдельная задача (можно добавить в spec/audit как known limitation).

## Технические детали

### Vue 2 подтверждение

- `v-text` действительно устанавливает `textContent` (см. исходник Vue2 `src/platforms/web/compiler/modules/text.js` и runtime `node-ops.js`).
- Для `<textarea>` (и `<input>`) значение хранится в `.value` — `textContent` игнорируется.
- `:value` напрямую устанавливает DOM `.value`, что и нужно.

### Файлы, которые будут изменены

| Файл | Изменение |
|------|-----------|
| `webvue3/src/components/Songs/edit/SearchText.vue` | (a) `<textarea v-text>` → `<textarea :value>` (1 строка + закрывающий тег); (b) `display: block` в `.group-button` (1 строка CSS); (c) JSDoc-комментарий с обоснованием фикса |

**Объём**: 3 строки кода + JSDoc + правка CSS = ~5 строк.

### Файлы, которые НЕ будут изменены

- `webvue3/src/components/Songs/edit/SearchTextResultsTable.vue` — корректно работает.
- `webvue3/src/components/Songs/edit/SubsEdit.vue` — не задействован (лишь импортирует SearchText).
- `webvue3/src/components/Songs/store.js` — actions `extractLyricsBySearchResultId` уже существуют и работают.
- Backend (`karaoke-app`, `karaoke-web`) — не затрагивается (контракт `/api/song/extractlyricsbysearchresultid` тот же).

## Риски

- **R1**: после замены `v-text` → `:value` нужно убедиться, что при **первом** рендере textarea не пустая, если `currentResult.text` уже есть (например, при открытии модалки с ранее извлечённым результатом). `:value` инициализируется при mount, как и `v-text`. OK.
- **R2**: `:value` не триггерит `input` event, поэтому `@input` не сработает. Но в текущем коде нет `@input` на textarea — OK.
- **R3**: `display: block` на `.group-button` может повлиять на другие места, где используется `group-button` (например, `.st-footer` line 56-89 — там кнопки тоже есть, но они **уже должны быть** столбиком/в строку). Проверка в plan-фазе покажет, не сломает ли это `.st-footer`. **Mitigation**: применить `display: block` только в локальном scope (например, `.st-body-column-2 .group-button`), либо принять, что кнопки в `.st-footer` тоже станут столбиком (если они не в flex — это и так должно быть нормально).

## Что подтвердит ручное тестирование

- **Сценарий 1 (OP#51)**: выбрать серый результат → нажать «Получить текст по ссылке» → textarea показывает текст, пункт перестаёт быть серым, модалка остаётся открытой.
- **Сценарий 2 (расположение)**: при выбранном сером результате видны обе кнопки в правом столбце, столбиком, одинаковой ширины.
- **Сценарий 3 (регрессия)**: при выбранном результате с уже полученным `text` — кнопка «Получить текст по ссылке» скрыта (как раньше), кнопка «Открыть на сайте» работает.
- **Сценарий 4 (регрессия footer)**: после фикса `.group-button` кнопки в `.st-footer` (низ модалки) — должны остаться видимыми и кликабельными (проверить визуально).

## Заключение

**Минимальный фикс** решает обе проблемы (расположение + UI-обновление) в одном файле. Объём — **~5 строк**. Backend не затрагивается. Backend-контракт не меняется. Тесты не нужны (нет автотестов для `webvue3`).

Полная инструкция по валидации — в [quickstart.md](quickstart.md).