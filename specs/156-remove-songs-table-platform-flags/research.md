# Research: Удаление 18 столбцов-флагов из таблицы «Песни»

**Created**: 2026-08-06
**Feature**: [spec.md](./spec.md)

## Решения

### Decision 1: Способ удаления столбцов из Bootstrap-vue-next `<b-table>`

**Decision**: Удалить 18 объектов из массива `fields[]` (строки 1086-1307 файла `SongsTable.vue`) вместе с 18 соответствующими `<template #cell(flagX)="data">` шаблонами.

**Rationale**:
- `<b-table>` от Bootstrap-vue-next использует массив `fields[]` как декларативный контракт: какие заголовки есть в `thead` и какие ключи берутся из item для `td`. Если ключ есть в `fields[]` — отрисовывается колонка; если есть `<template #cell(key)>` — отрисовывается кастомное содержимое. Оба массива должны быть согласованы (нет ключа в `fields[]` → шаблон игнорируется, и наоборот).
- Удаление только из `fields[]` без шаблонов оставит «мёртвые» шаблоны; удаление только шаблонов оставит колонки с пустым/дефолтным содержимым. Правильный путь — удалить оба.
- Альтернатива через `v-if` (computed-срез массива) отвергнута: добавляет runtime-overhead, усложняет чтение кода и не решает проблему «мёртвых» шаблонов.

**Alternatives considered**:
- **Computed-срез**: `fieldsFiltered()` возвращает `fields.filter(f => !HIDDEN.has(f.key))` — лишний indirection, runtime-cost.
- **CSS hide через `.d-none`**: оставляет пустые 20px колонки в DOM, не решает визуальную перегрузку, ломает `table-layout: fixed` per CONTRIBUTING.md.
- **`hidden` prop на `<b-table>`**: Bootstrap-vue-next не поддерживает, пришлось бы катить свой middleware.

### Decision 2: Удаление CSS-стилей `.fld-flag-*` (FR-003)

**Decision**: Удалить 18 CSS-блоков `.fld-flag-sponsr`, `.fld-flag-vk`, `.fld-flag-dzen-*`, `.fld-flag-vk-*`, `.fld-flag-tg-*`, `.fld-flag-max-*` из `<style scoped>` секции `SongsTable.vue` (строки 2458-2570).

**Rationale**:
- После удаления `<template #cell(flagX)>` шаблонов элементы с классами `.fld-flag-sponsr` и т.п. больше не создаются ни в этом компоненте.
- `grep` подтвердил: эти классы используются **только** в удаляемых `<template #cell>` шаблонах, нигде больше в `webvue3/src/components/Songs/` или публичной части.
- ESLint-стиль проекта (per `CONTRIBUTING.md`) поощряет удаление мёртвого CSS — он увеличивает размер CSS-чанка и затрудняет чтение.

**Alternatives considered**:
- **Оставить на случай «а вдруг вернём»**: нарушает FR-003, baseline ESLint будет содержать неиспользуемые селекторы. Готовы вернуть через `git revert` если понадобится.

### Decision 3: Удаление методов `playLyrics/Karaoke/Chords/Tabs` (FR-005)

**Decision**: Удалить методы `playLyrics(id)`, `playKaraoke(id)`, `playChords(id)`, `playTabs(id)` из `methods:` секции `SongsTable.vue` (строки 2154-2168).

**Rationale**:
- Эти методы вызываются **только** из удаляемых `<template #cell(flagX)>` шаблонов: `playLyrics` → `flagDzenLyrics`/`flagVkLyrics`/`flagTelegramLyrics`/`flagMaxLyrics`, `playKaraoke` → `flagDzenKaraoke`/`flagVkKaraoke`/`flagTelegramKaraoke`/`flagMaxKaraoke`, и т.д. После удаления ячеек методы становятся «мёртвым кодом» (ESLint `no-unused-vars`).
- Эти же методы доступны через `$store.getters.playLyrics()` — геттеры остаются (используются в `Songs/edit/SongEdit.vue` и `Publish/components/PublishTableBodyTd.vue`), компонент-обёртка в `SongsTable.vue` удаляется.
- Метод `playDemo(id)` **остаётся** — он вызывается ячейкой `flagPlayerDemo` (DE), которая не удаляется (FR-005 explicitly).

**Alternatives considered**:
- **Оставить методы в виде no-op заглушек**: лишний код, нарушает FR-005, добавляет шум.

### Decision 4: Удаление из Vuex `state.fieldSongParams[]` (FR-004)

**Decision**: Удалить 10 определений (для SP/VG/ZL/ZK/VL/VK/TL/TK/ML/MK) из `state.fieldSongParams[]` в `webvue3/src/components/Songs/store.js` (строки 230-358).

**Rationale**:
- Эти определения НЕ имеют внешних потребителей: `grep -rn "getFieldSongParams\|fieldSongParams"` нашёл только геттер внутри того же `store.js`. Ни `SongsFilterModal.vue`, ни `SongsTable.vue`, ни другие компоненты не используют эти определения.
- Геттер `getFieldSongParams` остаётся (на случай будущих потребителей), но возвращает укороченный массив.
- Для 8 остальных (`ZC/ZM/VC/VM/TC/TM/MC/MM`) определения в `fieldSongParams[]` уже отсутствуют — удалять нечего.

**Alternatives considered**:
- **Оставить всё в `fieldSongParams[]`**: нарушает FR-004, размер state растёт бессмысленно.
- **Удалить сам массив**: нарушит работу геттера `getFieldSongParams` (если он когда-то понадобится).

### Decision 5: Per-feature документация `docs/features/songs-table.md` (FR-008)

**Decision**: В том же PR обновить `docs/features/songs-table.md` — удалить из него описание/перечисление 18 удаляемых столбцов или явно отметить их как удалённые (с пометкой «Удалено в #NNN, 2026-08-06»). Добавить запись в `docs/architecture-notes.md` о PR.

**Rationale**:
- FR-009 Конституции: при правке кода одной из ключевых подсистем — обновлять соответствующий per-feature документ в том же PR.
- Без этого документация станет врать (говорит «есть 36 столбцов-флагов», а их 18). Это ухудшает онбординг и запутывает будущих разработчиков.

**Alternatives considered**:
- **Не обновлять**: нарушение FR-009 Конституции + техдолг.

### Decision 6: Ширина таблицы и `table-layout: fixed`

**Decision**: Не пересчитывать ширины остальных колонок. Оставить их `minWidth`/`maxWidth` как есть. Высвобожденные ≈360px займутся существующим горизонтальным скроллом (если он был) или контейнером таблицы.

**Rationale**:
- Per CONTRIBUTING.md: `table-layout: fixed` требует явной `width` на каждой колонке. Уменьшение ширины остальных колонок потребовало бы ручного подбора, что не относится к задаче «убрать столбцы».
- Горизонтальный скролл для админки приемлем (Bootstrap-vue-next `<b-table>` поддерживает из коробки, контейнер имеет overflow-x:auto).

**Alternatives considered**:
- **Перераспределить ширину на оставшиеся колонки**: out of scope, требует UX-исследования, рискует сломать фиксированные пиксельные ширины в существующем UI.

## Не нужно (out of scope)

- **Изменение бэкенда / БД / DTO**: FR-006 — данные сохраняются и обрабатываются как прежде.
- **Изменение таблицы публикаций `Publish`**: FR-007 — её ячейки используют те же `processColor*` и `play*` методы, но через свой собственный компонент `PublishTableBodyTd.vue`.
- **Изменение редактора `SongEdit.vue`**: FR-007 — его кнопки `playLyrics/Karaoke/Chords/Tabs/Demo` остаются.
- **Изменение Vuex-геттеров `play*`**: они используются в `SongEdit.vue` и `PublishTableBodyTd.vue`.
- **Изменение ширины оставшихся столбцов**: UX-исследование, out of scope.
