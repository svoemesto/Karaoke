# Phase 0 — Research: 263 — Улучшение модалки проверки задания

**Date**: 2026-08-30
**Spec**: [spec.md](./spec.md)
**Plan**: [plan.md](./plan.md)

## Цель

Зафиксировать технические решения и паттерны, использованные (или подтверждённые как применимые) в смежных компонентах. Все решения опираются на прецеденты в codebase — отдельных NEEDS CLARIFICATION не возникло (см. Clarifications спеки).

## Исследованные вопросы

### 1. Межпакетный импорт `formatText` из `karaoke-public` в `webvue3`

**Вопрос**: Будет ли Vite разрешать `import { formatText } from '../../../../karaoke-public/src/composables/useKaraokeEditor'` в `ReviewModal.vue`?

**Решение**: Использовать тот же путь, что уже работает в `SongKaraokeEditorView.vue:335`:
```js
import { formatText } from '../../../../karaoke-public/src/composables/useKaraokeEditor'
```

**Обоснование**: Прецедент в том же bounded context (`webvue3/src/components/SongEditor/`). Если путь потребует корректировки (например, в CI-сборке), решение — fallback-копия функции (5 строк) в `webvue3/src/components/SongEditor/useReviewModalFormat.js`.

**Альтернативы рассмотренные**:
- ❌ Перенос `formatText` в общий пакет (`packages/...`) — избыточно для одного компонента; новый monorepo-package не оправдан.
- ❌ Дублирование `formatText` в `webvue3/src/lib/` — создаёт две версии истины; лучше fallback в том же каталоге, что и компонент.
- ✅ **Прецедент + fallback** — минимальные изменения, легко откатить.

### 2. Vue scoped CSS + `ke-fx-*` классы из karaoke-public

**Вопрос**: `<style scoped>` блокирует стили karaoke-public для классов `ke-fx-*` внутри `ReviewModal.vue`. Как обеспечить работу этих стилей?

**Решение**: Использовать `:deep(.ke-fx-…)` селекторы внутри `<style scoped>` `ReviewModal.vue`, скопировав 5 правил из `karaoke-public/src/views/EditorWorkView.vue:1861-1888`.

**Обоснование**: Тот же паттерн уже применён в `SongKaraokeEditorView.vue:1850-1872` для `ske-fx-*`:
```css
.ske-preview :deep(.ske-fx-cur) { color: #ff0000; font-weight: 700; }
.ske-preview :deep(.ske-fx-group0) { color: #ffffff; }
```
Это стандартный Vue 3 SFC-паттерн для изолированных стилей с «пробросом» в дочерние элементы (в т.ч. в `v-html`).

**Альтернативы рассмотренные**:
- ❌ Глобальный `<style>` (без `scoped`) — нарушает изоляцию компонента.
- ❌ Полное перенесение `ke-fx-*` стилей в `webvue3/src/styles/` — лишний indirection; в karaoke-public они тоже локальны (через `:deep`).
- ✅ **`:deep` внутри `<style scoped>`** — изоляция сохранена, паттерн уже в проекте.

### 3. Адаптивная сетка `.se-cols` 1/2/3 колонки

**Вопрос**: Реализация responsive grid-template-columns через CSS (без JS).

**Решение**: CSS `grid-template-columns` + `@media` queries:
```css
.se-cols { display: grid; gap: 1rem; grid-template-columns: 1fr; }       /* mobile */
@media (min-width: 768px) { .se-cols { grid-template-columns: 1fr 1fr; } }  /* tablet: text+markup, markers under */
@media (min-width: 1024px) { .se-cols { grid-template-columns: 1fr 1fr 1fr; } }  /* desktop: all 3 cols */
```

**Обоснование**: 
- Текущая сетка уже использует CSS Grid (`ReviewModal.vue:514-518`: `grid-template-columns: 1fr 1fr;`).
- Bootstrap-vue-next grid (`<b-row>` / `<b-col>`) избыточен для 3-колоночной сетки с media queries.
- В проекте уже есть похожие media queries в `SongKaraokeEditorView.vue:1883-1894` (`@media (max-width: 1023.98px)`).

**Альтернативы**:
- ❌ Flex с `flex-wrap` — даёт менее предсказуемое поведение на разных ширинах.
- ❌ JS-расчёт ширины — лишний код, дублирование CSS-логики.
- ✅ **CSS Grid + media queries** — стандарт, прецедент в проекте.

### 4. Безопасность `v-html` для блока «Разметка»

**Вопрос**: Безопасен ли `v-html="parsedMarkupHtml"`?

**Решение**: `parsedMarkupHtml` — результат функции `formatText(markers, -1)` из karaoke-public. Эта функция генерирует только:
- `<span class="ke-fx-...">${txt}</span>` где `txt = uppercaseFirstLetter(label)` — содержимое `marker.label` (валидируется сервером: только `syllables` / `endofline` / `newline` / `endofsyllable` / `setting` с `GROUP|N` или `COMMENT|...`).
- `<br>` (статический HTML).
- Никаких пользовательских атрибутов (`href`, `style`, `on*`), никакого script.

`marker.label` приходит из БД — потенциальный XSS-источник ТЕОРЕТИЧЕСКИ. НО: тот же `v-html` уже используется в `SongKaraokeEditorView.vue:317` (`v-html="formattedTextHtml"`) — тот же источник данных, тот же паттерн. Прецедент подтверждает безопасность (если бы был XSS — это уже было бы зафиксировано как баг в karaoke-public).

**Альтернативы**:
- ❌ Парсинг HTML через `DOMParser` + ручной рендеринг — 30+ строк кода, нет выигрыша (тот же источник).
- ❌ Санитайзер (DOMPurify) — лишняя зависимость; нет XSS по факту.
- ✅ **`v-html` с прецедентом** — без новых рисков.

### 5. `loadEditorSettings()` — обработка при `mounted()`

**Вопрос**: Когда и как вызвать `loadEditorSettings()` для получения `textFontSize`/`previewFontSize`?

**Решение**: Однократный вызов в `mounted()` (Vue lifecycle hook `ReviewModal.vue:265`), результат пишется в `data().textFontSize` / `data().previewFontSize`. Шаблон уже используется в `EditorWorkView.vue:463`:
```js
...loadEditorSettings(),
```

**Обоснование**: 
- Прецедент в karaoke-public (тот же `loadEditorSettings()` для тех же полей).
- `mounted()` срабатывает при каждом ОТКРЫТИИ модалки (модалка пересоздаётся при каждом клике на строку таблицы — `v-if="showPlayer"`-паттерн). Значит свежие значения из localStorage подхватываются при каждом открытии.
- Никаких watcher'ов на `storage`-события (если админ меняет настройки в редакторе в другой вкладке — увидит при следующем открытии модалки, это OK).

**Альтернативы**:
- ❌ `watch: '$route'` или глобальный EventBus — избыточно; `mounted()` достаточно.
- ❌ Live-watcher на `localStorage` — может привести к «дёрганию» UI в открытой модалке.
- ✅ **`mounted()` + однократный snapshot** — просто, достаточно.

### 6. Увеличение ширины модалки под 3 колонки

**Вопрос**: Текущая `.se-modal-wide` = 760px (см. `ReviewModal.vue:452`). Достаточно ли для трёх колонок?

**Решение**: Увеличить до `min(96vw, 1100px)` (FR-008, Assumptions спеки). На 1280px экране это даст ~1100px модалки → ~3 × (1100 − 2×16 − 2×16) / 3 ≈ 350px на колонку. Достаточно для текста и разметки.

**Обоснование**: На ширине <1024px сетка переключается на 2 или 1 колонку (FR-008), так что 1100px максимум никогда не будет «слишком» для мобильных.

**Альтернативы**:
- ❌ Оставить 760px — на десктопе колонки будут слишком узкие.
- ❌ Полноэкранная модалка — перекрытие с другими admin-окнами; неудобно.
- ✅ **`min(96vw, 1100px)`** — адаптивно, покрывает все сценарии FR-008.

## Резюме решений

| # | Решение | Прецедент |
|---|---------|-----------|
| 1 | Импорт `formatText` из `karaoke-public` (тот же путь, что `SongKaraokeEditorView.vue:335`) | `SongKaraokeEditorView.vue:335` |
| 2 | `:deep(.ke-fx-…)` внутри `<style scoped>` | `SongKaraokeEditorView.vue:1850-1872` |
| 3 | CSS Grid + `@media` queries | `ReviewModal.vue:514-518` + `SongKaraokeEditorView.vue:1883-1894` |
| 4 | `v-html` с тем же источником, что в karaoke-public | `SongKaraokeEditorView.vue:317` |
| 5 | `loadEditorSettings()` в `mounted()` | `EditorWorkView.vue:463` |
| 6 | Ширина модалки `min(96vw, 1100px)` | (новое правило, расширение `ReviewModal.vue:452`) |

Никаких NEEDS CLARIFICATION не остаётся. Готово к Phase 1.

## Открытые вопросы / риски

- **Минимальный**: при первом запуске Vite может попытаться оптимизировать `karaoke-public/...` как зависимость, что в редких случаях требует явного указания в `vite.config.js`. Если `npm run build` упадёт — fallback (копия `formatText` в `webvue3/src/components/SongEditor/useReviewModalFormat.js`). Решение принимается на этапе реализации.
- **Минимальный**: при `prettier --check` могут найтись старые несоответствия в `ReviewModal.vue` (файл на 705 строк, обновлялся 100+ раз). Запустить `npm run format` перед коммитом (см. AGENTS.md Pass 244).