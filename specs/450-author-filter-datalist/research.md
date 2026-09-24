# Phase 0 Research: Список авторов в фильтрах

**Feature**: `450-author-filter-datalist` | **Date**: 2026-09-24

Все NEEDS CLARIFICATION из Technical Context отсутствуют — unknowns были
закрыты на этапе анализа кода. Ниже зафиксированы решения и альтернативы.

## Decision 1: Механизм подсказок — нативный `<datalist>`

**Decision**: Использовать нативный HTML `<datalist>` + `list="<id>"` на
`<input>`, ровно как в `SongsFilterModal.vue` (строки 12–24, 490, 839–841).

**Rationale**:
- Issue буквально требует «вести себя так же, как в фильтре компонента
  Песни» — эталон уже реализован нативным datalist.
- Ноль новых зависимостей (bootstrap-vue-next уже подключён, но datalist —
  родной элемент).
- Браузер сам фильтрует подстроку по мере ввода — не нужен JS-поиск.
- Работает с существующим `class="afm-input-field"` без правки CSS.

**Alternatives considered**:
- *`<b-form-select>` с полным списком авторов* — rejected: поле должно
  оставаться свободным текстовым вводом (пользователь может ввести имя,
  которого нет в справочнике; плюс смена семантики фильтра).
- *Кастомный combobox-компонент* — rejected: overengineering, нет в
  проекте, ломает единообразие с эталоном.
- *`<b-form-datalist>` из bootstrap-vue-next* — rejected: тот же
  `<datalist>`, но добавляет зависимость от версии библиотеки; эталон
  использует сырой `<datalist>`.

## Decision 2: Источник данных — геттер `songAuthorsPromise`

**Decision**: Загружать список через существующий Vuex-геттер
`getters.songAuthorsPromise` (модуль `Songs/store.js:941`), который
дёргает `POST /api/songs/authors` и возвращает `JSON.parse(...).authors`.

**Rationale**:
- Это ровно тот источник, что уже использует эталонный
  `SongsFilterModal.vue` (`mounted()`: `songAuthorsPromise.then(...)`).
- Endpoint `POST /api/songs/authors` → `Song.loadListAuthors(WORKING_DATABASE)`
  (`karaoke-app/.../ApiController.kt:2095`) возвращает `List<String>` имён
  авторов. Серверных изменений не требуется.
- Единый набор авторов во всех трёх фильтрах (SC-003).

**Alternatives considered**:
- *Новый endpoint (`/api/authors/listnames`)* — rejected: дублирование,
  нарушает FR-003 и «без новых эндпоинтов».
- *`Authors`-дайджест (`getAuthorsDigest`)* — rejected: требует
  предварительной загрузки таблицы авторов, тяжелее и не гарантированно
  загружен при открытии фильтра; даёт объекты, а не плоские строки нужного
  вида.
- *Локальный `promisedXMLHttpRequest` прямо в компоненте* — rejected:
  дублирует геттер, эталон уже централизован.

## Decision 3: Локальный `dictAuthors` в `data()` + загрузка в `mounted()`

**Decision**: В каждой из двух модалок добавить `dictAuthors: []` в
`data()` и в `mounted()` подписаться на `songAuthorsPromise`, заполнив
`dictAuthors`. Рендерить модалко-локальный `<datalist>`.

**Rationale**:
- Изоляция на уровне компонента: модалки монтируются по требованию
  (`v-if`), список грузится только при открытии фильтра — не грузит
  страницу таблицы (US-требование: не блокировать открытие, FR-008).
- Ошибка сети при загрузке списка не ломает модалку: `dictAuthors`
  остаётся `[]`, поле работает как текстовый ввод (FR-006).
- Совпадение с эталонным паттерном.

**Alternatives considered**:
- *Загружать в Vuex `Authors/store.js` и брать оттуда* — rejected: лишний
  state/мутация, дублирование уже существующего геттера.
- *Кешировать список глобально (module-level)* — rejected: преждевременная
  оптимизация; список небольшой и грузится только при открытии модалки.

## Decision 4: Уникальные `id` для `<datalist>` (коллизии)

**Decision**: Использовать модалко-уникальные идентификаторы:
`authorsDictAuthorsId` (в `AuthorsFilterModal`) и
`albumsDictAuthorsId` (в `AlbumsFilterModal`).

**Rationale**:
- `SongsFilterModal` уже использует `dictAuthorsId`; `HomeView.vue` —
  `list_authors`; повторный `id="dictAuthorsId"` в другом компоненте
  создаст неоднозначность `list=` при их одновременном присутствии в DOM.
- В `AlbumsFilterModal.vue` поле «Автор:» сейчас ссылается на
  `list="list_authors"`, но `<datalist id="list_authors">` объявлен
  **только** в `HomeView.vue` — т.е. в модалке альбомов подсказки сегодня
  фактически «мёртвые» (работают лишь случайно, если HomeView смонитрован).
  Это исправляется новой привязкой.

**Alternatives considered**:
- *Переиспользовать `dictAuthorsId`* — rejected: риск коллизий id в DOM.
- *Оставить `list="list_authors"` и добавить datalist с тем же id* —
  rejected: дублирование id, если HomeView открыт.

## Decision 5: Семантика фильтрации не меняется

**Decision**: Значения полей по-прежнему уходят как `filterAuthor` /
`filterAuthorName` (точное совпадение имени). Datalist — только помощь
вводу, `v-model` не меняется.

**Rationale**:
- Issue просит только показ списка; FR-004/FR-005 фиксируют сохранение
  текущей семантики.
- Бэкенд альбомов (`ApiController.apisAlbumsDigest`) резолвит точное имя
  через `Author.getAuthorByName` → `author_id`; бэкенд авторов матчит по
  `filterAuthor`. Смена семантики — вне scope.

**Alternatives considered**:
- *Фильтровать по `authorId` вместо имени* — rejected: расширение scope,
  обратная несовместимость с сохранёнными фильтрами.

## Decision 6: Тестирование — ручной E2E + сборка/линтинг

**Decision**: Верификация — ручной E2E по `quickstart.md`, плюс
`npm run lint`, `npm run build`, `npm run format:check` в `webvue3`.

**Rationale**:
- В `webvue3` нет unit-тестового раннера для Vue-компонентов
  (`node --test` применяется только к чистым util'ам, напр.
  `src/utils/__tests__/dateFormat.test.js`); добавление vitest — вне scope
  этой небольшой фичи.
- В CI тестов нет (см. Constitution § «Рабочий процесс»): проверка —
  сборка + линт + ручной сценарий.

**Alternatives considered**:
- *Написать vitest-тест на рендер datalist* — rejected: потребует
  введения раннера/конфига в проект, несоразмерно объёму правки.
- *E2E Playwright на admin SPA* — rejected: инфраструктура E2E в проекте
  не настроена для `webvue3`.

## Decision 7: SSoT / per-feature документы

**Decision**: Создать `docs/features/author-filter-datalist.md` (FR-009) и
синхронно дополнить `knowledge/system/frontend/filter-stores.md` отметкой
об источнике подсказок для полей «Автор:».

**Rationale**:
- Constitution VI/FR-009: при правке кода обновлять per-feature документ в
  том же PR.
- `knowledge/system/frontend/filter-stores.md` описывает filter stores и
  эталон Songs-datalist — новая деталь принадлежит туда (Linking Protocol).
- `.ssot-map.yml` не содержит правила для `webvue3/**` — SSoT-обновление
  делается добровольно по духу Principle IX, не по машинному gate.

**Alternatives considered**:
- *Не трогать `knowledge/`* — rejected: нарушает Knowledge-SSoT
  (AGENTS.md Tier-1). Даже при отсутствии machine-rule изменение заметно.

## Итог

Все неизвестные закрыты. Переход к Phase 1 (data-model, contracts,
quickstart) обоснован.
