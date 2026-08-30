# Implementation Plan: Улучшение блоков «Текст пользователя», «Разметка» и «Маркеры» в модалке проверки задания

**Branch**: `263-editor-task-review-modal` | **Date**: 2026-08-30 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/263-editor-task-review-modal/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Админский SPA `webvue3` (Vue 3 + Vite + Vuex + Bootstrap-vue-next) — модалка проверки задания редактора `ReviewModal.vue` (компонент раздела «Задания редактора»). Текущая модалка показывает голый `<pre>` для «Текста пользователя» без явного выравнивания/шрифта и счётчики «Маркеров» в виде 4-строчного flex-списка. Блок «Разметка» отсутствует. Требуется:

1. **`text-align: left`** для `.se-text`.
2. **Новый блок «Разметка»** — HTML из `formatText()` (та же палитра `.ke-fx-*` на чёрном фоне, что в karaoke-public).
3. **Размер шрифта** обоих блоков из `loadEditorSettings()` (`textFontSize` 16px, `previewFontSize` 18px по умолчанию).
4. **«Маркеры» одной строкой** — горизонтальный flex с ` · ` между счётчиками.
5. **Адаптивная сетка** `.se-cols` 1/2/3 колонки в зависимости от ширины модалки.

**Технический подход**: только фронтенд, только `webvue3/src/components/SongEditor/ReviewModal.vue` (template, script, scoped style). Серверная часть не затрагивается. Импорт чистой функции `formatText` + `loadEditorSettings` из уже-используемого в `SongKaraokeEditorView.vue` модуля `karaoke-public/src/composables/useKaraokeEditor.js`. Вспомогательный per-feature LiveDoc — `livedocs/features/263-editor-task-review-modal.md`.

## Technical Context

**Language/Version**: JavaScript ES2020 (Vue 3 Options API, как и весь существующий `ReviewModal.vue`). Без TypeScript — миграция вне scope.

**Primary Dependencies**:
- `vue@^3` (Composition API не используется в существующем коде — стиль Options API).
- `bootstrap-vue-next` (импортируется в `webvue3/src/App.vue:126`).
- Чистая функция `formatText` + `loadEditorSettings` из `karaoke-public/src/composables/useKaraokeEditor.js` (уже импортируется в `SongKaraokeEditorView.vue:335` — межпакетный импорт работает).
- Никаких новых npm-зависимостей.

**Storage**: N/A — фронтенд-фича, никаких БД-изменений, никаких новых endpoints.

**Testing**:
- В CI нет (см. AGENTS.md, Constitution § «Тесты»). Существующие тесты webvue3/karaoke-public — `@Disabled`.
- Проверка — пользователем вручную на dev-машине.
- Smoke-проверки, выполняемые агентом перед merge:
  1. `cd webvue3 && npm run lint` — никаких новых ESLint-нарушений (baseline `[]`).
  2. `cd webvue3 && npm run build` — успешная сборка (Vite resolve межпакетного импорта `formatText`).
  3. Визуальный code review блока «Разметка» на соответствие палитре `.ke-fx-*` karaoke-public (по SC-002).

**Target Platform**: Современные браузеры (Chrome/Firefox/Safari последних 2 мажорных версий) — та же цель, что и для существующего `webvue3`. Серверная часть admin-машины: nginx → `webvue3` контейнер.

**Project Type**: SPA-фронтенд (админка). Mono-repo (Gradle multi-module + два Vite-проекта — `webvue3`, `karaoke-public`). Затрагивается только `webvue3/`.

**Performance Goals**:
- SC-003: переключение голоса обновляет блок «Разметка» за <50мс (один Vue reactive tick).
- Никаких дополнительных сетевых запросов.
- `v-html` для блока «Разметка» — допустимый паттерн (уже используется в `SongKaraokeEditorView.vue:317`); HTML генерируется только что вызванной `formatText` (trusted source), XSS-риск нулевой.

**Constraints**:
- Baseline ESLint = `[]` (`webvue3/.eslint-baseline.json`). НЕ ДОБАВЛЯТЬ новых нарушений.
- prettier-форматирование через `npm run format` (если авто-исправление необходимо) и проверка через `npm run format:check`.
- Никаких изменений серверной части (по решению FR/Notes).
- `ReviewModal.vue` — общий компонент для 6 точек входа (`SongEditorTable`, `SongsTable`, `SongEdit`, и т.д. — `codegraph` показал 6 caller'ов). Правка КОСНЕТСЯ всех 6 точек одновременно; это согласованное поведение (визуальный стандарт должен быть везде одинаковым).

**Scale/Scope**:
- 1 компонент (`ReviewModal.vue`, ~705 строк сейчас → ~750-780 после правок).
- 1 новый computed (`parsedMarkupHtml`).
- 2 новых data-поля (`textFontSize`, `previewFontSize`).
- 1 новый template-блок (`.se-col-markup`).
- ~10 строк новых CSS-правил (адаптивная сетка `.se-cols`, горизонтальный `.se-marker-summary`, чёрный фон `.se-markup`, `:deep(.ke-fx-…)`).
- 1 LiveDoc (`livedocs/features/263-editor-task-review-modal.md`).
- Затронуто 6 точек вызова модалки (визуально единообразно).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Проход по принципам Karaoke Constitution v2.1.0:

| # | Принцип | Применимо? | Статус | Обоснование |
|---|---------|-----------|--------|-------------|
| I | Self-contained автопайплайн | N/A | — | Чисто UI-фича, не затрагивает пайплайн производства видео. |
| II | Сырой JDBC + дифф по хэшам | N/A | — | Нет БД-изменений, нет SQL, нет recordhash-триггеров. |
| III | Двух-БД синхронизация через SyncRegistry | N/A | — | Не добавляем сущности в sync, не трогаем `SyncRegistry.all`. |
| IV | Async-очередь задач | N/A | — | Нет новых процессов, нет `KaraokeProcess*`, нет thread-лейнов. |
| V | Двух-фронтенд: админка и публичный | ✅ | **PASS** | Затрагиваем только `webvue3`. Импорт `formatText` из `karaoke-public/src/composables/useKaraokeEditor.js` — переиспользование ЧИСТОЙ функции (без состояния, без побочных эффектов), а не ответственности (принцип «смешивание ответственностей ЗАПРЕЩЕНО» не нарушается). Этот же приём уже работает в `SongKaraokeEditorView.vue:335` — прецедент в codebase. |
| VI | Code Standards (KDoc/JSDoc, линтеры, per-feature docs) | ✅ | **PASS** | (a) JSDoc для `parsedMarkupHtml` (computed) и для нового блока — обязателен (FR-006 Constitution: «export default Vue-компонент MUST сопровождаться JSDoc»). (b) `webvue3/.eslint-baseline.json` = `[]`, не добавлять новых нарушений. (c) Prettier (`npm run format:check`). (d) Создать LiveDoc `livedocs/features/263-editor-task-review-modal.md` в том же PR — фича меняет bounded context «Задания редактора / review» (по аналогии с `154-editor-tasks-manage.md`, `163-fix-song-editor-regressions.md`). |
| VII | Cross-Machine Setup | N/A | — | Нет machine-зависимых настроек. |
| VIII | Секреты и git-гигиена | N/A | — | Не затрагиваем `.env`, `.key`, `.pem` (их нет в этой фиче). Проверить `git ls-files | grep -iE '\.env$\|\.key$\|\.pem$'` пусто — по умолчанию, не в этой ветке. |

**Результат**: все применимые Gates **PASS**. Никаких нарушений, не требуется `Complexity Tracking`. **Re-check после Phase 1**: ✅ **PASS** (повторно). Phase 1 сгенерировал только документы (`research.md`, `data-model.md`, `contracts/README.md`, `quickstart.md`); никаких новых архитектурных решений. Все использованные паттерны (`:deep(.ke-fx-…)`, `v-html`, CSS Grid media queries, межпакетный импорт, `loadEditorSettings()` в `mounted()`) уже имеют прецеденты в codebase (см. [research.md](./research.md)).

## Project Structure

### Documentation (this feature)

```text
specs/263-editor-task-review-modal/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command) — пустая (нет внешних API-контрактов)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

Плюс per-feature LiveDoc (создаётся в PR, не здесь):

```text
livedocs/features/263-editor-task-review-modal.md
```

### Source Code (repository root)

Фича — фронтенд-only, затрагивает только:

```text
webvue3/src/components/SongEditor/
├── ReviewModal.vue                  # ЕДИНСТВЕННЫЙ изменяемый файл
└── (без других правок в этом каталоге)
```

Вспомогательный каталог (создаётся только если межпакетный импорт `formatText` через Vite не разрешится — fallback):

```text
webvue3/src/components/SongEditor/
└── useReviewModalFormat.js          # Копия formatText (5 строк) — fallback
```

**Structure Decision**: Существующая структура `webvue3/src/components/SongEditor/` сохраняется. Новые каталоги НЕ создаются. Изменения локализованы в ОДНОМ файле `ReviewModal.vue` плюс опциональный fallback-файл `useReviewModalFormat.js` (если потребуется).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

Не заполняется — нарушений нет.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |