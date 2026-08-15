# Research: Мини-редактор — редизайн (admin первым, потом karaoke-public)

**Branch**: `233-mini-editor-redesign` | **Date**: 2026-08-15

## Цель research

Подтвердить технические решения для UI-рефакторинга мини-редактора песен в `webvue3` (`SongKaraokeEditorView.vue`, ~1761 строк). Цель — выбрать **минимально-инвазивный** подход, который не сломает функциональность, зафиксированную в спеках `232-admin-song-editor-local-db` (LOCAL-БД) и `163-fix-song-editor-regressions` (регрессии спецтегов), и уложится в лимит ESLint-baseline.

## Изученные источники

- `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue` — главный файл, 1761 строка, шаблон + `<script>` (Options API) + `<style scoped>`.
- `webvue3/src/components/SongEditor/SongKaraokeEditorModal.vue` — обёртка-модалка.
- `webvue3/src/components/SongEditor/store.js` — Vuex-модуль `SongEditor`.
- `livedocs/features/163-fix-song-editor-regressions.md` — регрессии мини-редактора (autosave, marker virtualisation).
- `livedocs/features/232-admin-song-editor-local-db.md` — LOCAL-БД для `mode='song'`.
- `.specify/memory/constitution.md` (v2.1.0) — Principle V (двух-фронтенд), VI (Code Standards), VIII (секреты).
- `webvue3/.eslint-baseline.json` — текущий baseline (правки не должны его увеличивать).

## Открытые NEEDS CLARIFICATION из Technical Context

Из `plan.md` → **0** (все уточнения сделаны пользователем в `/speckit.clarify` Q1–Q5):
- Q1 (toggle спецтегов = «Показать клавиатуру»): закрыто.
- Q2 (панель «над preview»): закрыто.
- Q3 (карточный блок плеер+голоса+волна): закрыто.
- Q4 (drawer при <1024px): закрыто.
- Q5 (горизонтальный скролл для >4 голосов): закрыто.

Остались стилистические Open Questions (OQ-4, OQ-5, OQ-6, OQ-7, OQ-8) — **не блокеры для плана**, переносятся в следующие итерации пользователем.

## Решения

### R-1: Изменения локализованы в одном файле

**Decision**: основная масса правок — в `SongKaraokeEditorView.vue` (CSS-классы, обёртки `<div>`, возможно новое data-свойство `rightDrawerOpen`).

**Rationale**:
- Все блоки мини-редактора уже здесь: `.ske-voice-tabs`, `.ske-wave-card`, `.ske-player-toggle`, `.ske-kb-toolbar`, `.ske-spectag-toolbar`, `.ske-texts`, `.ske-text-col`.
- `232`/`163` активно опираются на это состояние; разбиение на подкомпоненты потребовало бы передачи ~10 props/emits и повысило бы риск регрессий.
- Изменения в `SongKaraokeEditorModal.vue` — минимальны (только если он задаёт `<style>`/контейнер, влияющий на ширину колонок).

**Alternatives considered**:
- **(a)** Выделить `VoiceTabs.vue`, `SpecTagToolbar.vue`, `MarkerActionsPanel.vue`, `EditorPreviewPanel.vue` как отдельные компоненты. *Отвергнуто*: scope-крейз, повышает bus-factor, нет выигрыша (компонент используется только в одном месте).
- **(b)** Полностью переписать на Composition API. *Отвергнуто*: огромный diff, нужен полный ревью всего 1761-строчного файла, рост ESLint-baseline почти неизбежен.

### R-2: Карточный блок «плеер + голоса + волна» — через CSS-обёртку

**Decision**: обернуть 3 существующих блока (`.ske-player-toggle`, `.ske-voice-tabs`, `.ske-wave-card`) в новый `<div class="ske-player-voice-wave-card">` + добавить соответствующий стиль с общим фоном/обводкой и лёгкими внутренними разделителями (через `border-top` на дочерних элементах).

**Rationale**:
- Минимальный diff в шаблоне (3 открывающих/закрывающих тега + 1 стиль).
- Внутренние классы остаются как есть — никаких каскадных переписываний.
- Соответствует Q3.

**Alternatives considered**:
- **Добавить общий фон на `<template>` root**. *Отвергнуто*: root — `.ske-page` — это вся страница, включая текст и правую колонку; общий фон на нём не решает задачу «визуально объединить 3 блока».

### R-3: Спецтеги и клавиатура — единый toggle, состояние уже есть

**Decision**: переиспользовать существующее состояние `showKeyboard` (уже персистится через `saveEditorSettings` в `SongEditor/store.js`). Блок `.ske-spectag-toolbar` (строки 197–254) уже рендерится под `.ske-text-col`, НЕ под `.ske-kb-toolbar` → чтобы соответствовать Q1, переместить `.ske-spectag-toolbar` внутрь `<div v-if="canEdit && showKeyboard">` ИЛИ внутрь `.ske-keyboard`-блока. Оба варианта эквивалентны по функциональности.

**Выбранный вариант**: переместить `.ske-spectag-toolbar` в самое начало `.ske-keyboard` (т.е. внутрь `<div v-if="canEdit && showKeyboard" class="ske-keyboard">`). Клавиатура + спецтеги появляются/скрываются одним `showKeyboard` toggle. Это даёт:
- Один источник истины (`showKeyboard`).
- Никаких новых состояний.
- Существующая логика `setEditorSettings({ showKeyboard })` продолжает работать.

**Alternatives considered**:
- **(a)** Ввести новое состояние `showSpecTags` отдельно. *Отвергнуто*: пользователь явно сказал «единый toggle» (Q1, option B).
- **(b)** Хоткей для спецтегов. *Отвергнуто*: пользователь явно выбрал option B, не C/D.

### R-4: Панель маркер-действий — в правую колонку, над preview

**Decision**: переместить `.ske-kb-toolbar` (3 кнопки) из текущей позиции (между `.ske-transport` и `.ske-keyboard`) **внутрь** правой `.ske-text-col` (`<div class="ske-text-col">` для preview), непосредственно над `.ske-col-header` блоком `Разметка` (или под ним — по результатам визуальной примерки, см. OQ-1, которая закрыта как «над preview»).

**Rationale**:
- Структурно — вырезать `<div class="ske-kb-toolbar">` из одной части шаблона и вставить в другую.
- Логика `clearMarkers`, `doTypograph` остаётся в `<script>` — никаких изменений в data/computed/methods.
- Состояние `typographError` — в этом же блоке; рендеринг ошибки остаётся рядом с кнопкой «Типограф».

**Alternatives considered**:
- **Tabs/аккордеон**. *Отвергнуто*: пользователь выбрал option B (горизонтальный ряд, не табы).
- **Плавающая панель (popover)**. *Отвергнуто*: переусложнение, нарушает «простой горизонтальный ряд» из Q2.

### R-5: Drawer при <1024px — через CSS media-query + data-свойство

**Decision**: добавить новое data-свойство `rightDrawerOpen: false` в `<script>` + `@media (max-width: 1023.98px)` CSS-правила, которые:
- Скрывают правую колонку (`.ske-text-col:nth-child(2)`) за пределами экрана (`transform: translateX(100%)`).
- Показывают кнопку-«бургер» (например, в `.ske-kb-toolbar` или в новой `.ske-drawer-toggle`).
- При `rightDrawerOpen: true` — `transform: translateX(0)` + затемнение фона.

**Rationale**:
- Минимальные изменения в `<script>` (1 data + 1 method `toggleRightDrawer` + 1 watch на resize для авто-открытия/закрытия).
- CSS media-query — стандартный путь, без зависимостей.
- Состояние `rightDrawerOpen` НЕ персистится (только runtime, теряется при F5 — это нормально для UI-state).

**Alternatives considered**:
- **CSS-only (без state)**. *Отвергнуто*: без `rightDrawerOpen` нельзя открыть drawer — не на чем его триггерить.
- **Внешняя библиотека (vue-drawer, vue-bottom-sheet)**. *Отвергнуто*: новая npm-зависимость, нарушает принцип «никаких новых зависимостей» в Technical Context.
- **Всегда показывать, горизонтальный скролл по странице**. *Отвергнуто*: пользователь явно отверг option A (Q4).

### R-6: Горизонтальный скролл для >4 голосов — через CSS

**Decision**: добавить к `.ske-voice-tabs`:
- `flex-wrap: nowrap` (по умолчанию уже nowrap, проверить).
- `overflow-x: auto`.
- `scrollbar-width: thin` (для Firefox) + кастомный стиль scrollbar (для Webkit) — опционально.

**Rationale**: чистый CSS, никаких изменений в `<script>`. Кнопки «+ Голос» и «✕ Удалить голос» остаются в конце ряда и тоже скроллятся.

**Alternatives considered**:
- **Динамическое скрытие лишних в «Ещё ▾»**. *Отвергнуто*: пользователь выбрал option B (Q5), не C.
- **Wrap на новую строку**. *Отвергнуто*: пользователь выбрал option B, не A.
- **Компактные кнопки без подписи**. *Отвергнуто*: пользователь выбрал option B, не D.

### R-7: Удаление дубля заголовка — простое скрытие блока

**Decision**: в текущем `SongKaraokeEditorView.vue` нет блока `.ske-header` с названием + исполнителем + бейджем «Песня» в том виде, как на скриншоте пользователя. В шапке файла есть `.ske-header` (строки 4–12) с `songName`, `author`, `statusLabel`/`statusKind`. Согласно скриншоту и спеке — этот блок **должен быть удалён** из тела мини-редактора, потому что в шапке страницы/модалки (`Машина Времени · редактирование песни`) эта информация уже есть.

**Способы**:
- **(a)** Полностью удалить `<div class="ske-header">…</div>` (строки 3–12) и связанные с ним стили (`.ske-header`, `.ske-header-inner`, `.ske-h-song`, `.ske-h-author`, `.ske-header-right`).
- **(b)** Скрыть через `v-if="false"` (отложенное удаление на случай, если в следующих итерациях пользователь захочет вернуть).

**Выбранный вариант**: **(b) `v-if="false"` в этом проходе**, с TODO-комментарием. Причина: пользователь сказал «будут ещё правки», и есть OQ-7 «что показывать в шапке» — возможно, через одну итерацию этот блок вернётся в изменённом виде, и удалять/восстанавливать стили будет дороже, чем снять `v-if`. Финальное удаление — в следующей итерации после решения OQ-7.

**Уточнение**: рассмотреть также prop `showHeader` (default false), чтобы `mode='assignment'` мог при желании вернуть заголовок без правок в шаблоне.

**Rationale**: минимальный риск, легко откатить. Соответствует A-8 и FR-008.

**Alternatives considered**:
- **Полное удаление**. *Отвергнуто до закрытия OQ-7*: пользователь явно сказал «будут ещё правки», заголовок может вернуться в изменённой форме.

## Best practices / паттерны

- **CSS Custom Properties** (если в проекте есть) для отступов карточек — проверить в `webvue3/src/assets/`. Если нет — использовать фиксированные отступы из существующих стилей.
- **Scoped styles**: ВСЕ новые классы добавляются внутри `<style scoped>` (как и существующие) — это уже сложившаяся практика в файле.
- **Bootstrap-vue-next**: для drawer'а НЕ использовать `<b-offcanvas>` (новая зависимость на конкретный компонент, проверить, что он уже в bundle). Если есть — использовать, иначе — кастомный CSS-transform (см. R-5).
- **JSDoc**: добавить/обновить `@see` в шапке `<script>` default export, формат: `* @see {@link livedocs/features/233-mini-editor-redesign.md}`.

## Тестирование / валидация

- **Pre-commit**: `npm run lint` → проверка ESLint baseline (`./tools/check-eslint-baseline.sh` не должен показать новых нарушений).
- **Build**: `cd webvue3 && npm run build` — должна пройти без warnings о новых undefined props/template errors.
- **Manual** (см. quickstart.md):
  1. Открыть 3 разные песни (1 голос / 2 голоса / 5 голосов), убедиться в карточной группировке.
  2. Ресайз окна <1024px → правая колонка сворачивается в drawer, открывается по кнопке.
  3. Клик «Показать клавиатуру» → появляется и клавиатура, и спецтеги.
  4. Клик «Очистить маркеры» / «Типограф» → отрабатывает как раньше.
  5. Проверить, что `mode='assignment'` остаётся функциональным (заголовок можно вернуть, но в `mode='song'` он скрыт).

## Связанные риски

| Риск | Митигация |
|------|-----------|
| Случайно сломать `232` (LOCAL-БД) — autosave, store | `v-if="false"` вместо удаления; ничего не трогаем в `<script>` data/computed/methods, кроме `rightDrawerOpen` |
| Случайно сломать `163` (регрессии спецтегов) — `onInsertSpecTag`, `syncMarkersFromSpecTags` | Только CSS + перемещение блоков в шаблоне; функции `onInsertSpecTag*` не трогаем |
| Рост ESLint baseline | Минимизировать новые `v-if`, использовать `v-show` где возможно; не отключать правила |
| Регрессия в `mode='assignment'` | Заголовок скрыт через `v-if="false"` (не удалён), prop `showHeader` опционально |

## Решения, требующие проверки в Phase 1

- **R-5**: проверить, есть ли в bundle Bootstrap-vue-next компонент `BOffcanvas` (если да — упростит R-5). Если нет — кастомный CSS.
- **R-5**: проверить, есть ли в `webvue3/src/assets/` общие CSS-переменные (цвета, отступы) для drawer/backdrop. Если нет — задать локально в `<style scoped>`.
- **R-7**: проверить, как именно `SongKaraokeEditorModal.vue` рендерит `<SongKaraokeEditorView>` — если модалка добавляет свой заголовок над view, то `.ske-header` в view — действительно дубль, и его удаление безопасно. Если модалка не даёт заголовка, удаление `.ske-header` приведёт к потере названия песни (НЕЛЬЗЯ). Это критическая проверка — выполнить **до** начала CSS-правок.

## Итог

Все 7 ключевых решений (R-1…R-7) приняты. NEEDS CLARIFICATION = 0. Открытые стилистические OQ (4, 5, 6, 7, 8) перенесены в следующие итерации пользователем, не блокируют реализацию первого прохода. Phase 1 может стартовать.
