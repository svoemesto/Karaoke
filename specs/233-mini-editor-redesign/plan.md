# Implementation Plan: Мини-редактор песен в админке — редизайн (admin первым, потом karaoke-public)

**Branch**: `233-mini-editor-redesign` | **Date**: 2026-08-15 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/233-mini-editor-redesign/spec.md`

**Note**: Этот план сгенерирован `/speckit.plan`. Фаза 0 (research) и Фаза 1 (design/contracts) завершены; Phase 2 (tasks.md) — отдельная команда `/speckit.tasks`.

## Summary

Визуальный редизайн мини-редактора песен в admin SPA (`webvue3`): убрать дубль заголовка, объединить «Прослушать в плеере + переключатель голосов + вейвформа» в один карточный блок, скрыть панель кнопок спецтегов по умолчанию (за toggle «Показать клавиатуру»), перенести панель «Показать клавиатуру / Очистить маркеры / Типограф» в правую колонку над preview-блоком, добавить адаптивность (drawer при <1024px) и горизонтальный скролл для >4 голосов. **Никаких изменений логики, БД, API или режима `mode='assignment'`.** Полный перенос дизайна в `karaoke-public` — отдельная фича.

**Технический подход (из research.md)**: чистый CSS-рефакторинг и небольшие структурные правки в одном файле (`SongKaraokeEditorView.vue`, ~1761 строк). Новые Vue-компоненты не вводятся (кроме возможной обёртки `MiniEditorDrawer.vue` для responsive-сценария) — переиспользуются существующие блоки. Состояние `showKeyboard` уже персистится через `saveEditorSettings`; нового состояния не нужно.

## Technical Context

- **Language/Version**: Vue 3 SFC (Composition API не используется — Options API), Vite, Node 22 LTS.
- **Primary Dependencies**: `webvue3` SPA stack — Vue 3, Vuex 4, Bootstrap-vue-next, `wavesurfer.js` (для вейвформы), существующий `KaraokePlayer` (inline-плеер). Никаких новых npm-зависимостей.
- **Storage**: N/A. Состояние UI (`showKeyboard` и т.п.) уже персистится через `saveEditorSettings` (Vuex + localStorage). Новая персистенция не требуется.
- **Testing**: ручная визуальная проверка (см. quickstart.md). В CI тестов нет (см. Constitution § «Рабочий процесс» → «Тесты»). Pre-commit: `eslint` baseline не должен расти (Constitution VI FR-007).
- **Target Platform**: десктопный браузер, ≥1024px (двухколоночный лэйаут), <1024px — drawer. Мобильный сценарий не целевой для admin.
- **Project Type**: web-application (frontend-only). Backend (`karaoke-app` / `karaoke-web`) **не затрагивается**.
- **Performance Goals**: N/A (UI-рекомпозиция без тяжёлых операций). Sub-100ms отзывчивость UI сохраняется.
- **Constraints**:
  - Только `webvue3`; НЕ `karaoke-public` (отдельная фича, см. FR-007).
  - Только мини-редактор; НЕ `SubsEdit.vue` / `SongEdit.vue` (полный редактор на проде).
  - Только `mode='song'`; режим `mode='assignment'` остаётся без визуальных изменений (но в общем лэйауте — см. FR-005).
  - Функциональная совместимость с `232-admin-song-editor-local-db` (LOCAL-БД) и `163-fix-song-editor-regressions` (регрессии спецтегов).
  - Никаких новых секретов / env / правок `deploy/`.
- **Scale/Scope**: 1 Vue-файл, ~1761 строк (правки), +1 вспомогательный компонент (опционально — `MiniEditorDrawer.vue`), 1 per-feature LiveDoc (`livedocs/features/233-mini-editor-redesign.md`). Тесты не добавляются.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Применимость | Статус |
|-----------|--------------|--------|
| **I. Self-contained автопайплайн** | N/A — UI-фича, не затрагивает media-pipeline. | ✅ PASS |
| **II. Сырой JDBC + дифф по хэшам** | N/A — нет работы с БД. | ✅ PASS |
| **III. SyncRegistry** | N/A — нет новых сущностей. | ✅ PASS |
| **IV. Async-очередь задач** | N/A — UI-фича. | ✅ PASS |
| **V. Двух-фронтенд** | **Применимо.** Редизайн касается ТОЛЬКО `webvue3`; `karaoke-public` НЕ трогаем (см. FR-007, A-3). `<select>` в `webvue3` уже с `form-select`. Изображения — MinIO, не задеваем. | ✅ PASS |
| **VI. Code Standards (FR-006, FR-007, FR-009)** | **Применимо.** <ul><li>**FR-006 (JSDoc)**: `SongKaraokeEditorView.vue` — публичный компонент; в шапке файла MUST быть JSDoc с `@see livedocs/features/233-mini-editor-redesign.md`. Существующий JSDoc (если есть) — обновить ссылку.</li><li>**FR-007 (lint)**: ESLint baseline НЕ должен расти; если вводятся новые правила/отключения — обосновать.</li><li>**FR-009 (per-feature doc)**: создать `livedocs/features/233-mini-editor-redesign.md` с `related:` ссылкой на `livedocs/features/163-fix-song-editor-regressions.md` и `livedocs/features/232-admin-song-editor-local-db.md` (это 2 существующих «соседа» по подсистеме SongEditor).</li></ul> | ✅ PASS (с условиями — см. ниже) |
| **VII. Cross-Machine Setup** | N/A — не меняем `.gitattributes`, `.git-blame-ignore-revs`, конфиги. | ✅ PASS |
| **VIII. Секреты и git-гигиена** | N/A — UI-фича, не затрагивает секреты. | ✅ PASS |

**Условия FR-006/FR-009 (обязательные для merge):**

1. В `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue` (JSDoc-блок на экспорте по умолчанию) добавить/обновить `@see` со ссылкой на `livedocs/features/233-mini-editor-redesign.md`.
2. Создать `livedocs/features/233-mini-editor-redesign.md` со структурой frontmatter (как у `163`/`232`) и секцией «Что делает» (что меняется в лэйауте: удаление дубля заголовка, объединение плеер+голоса+волна в карточку, скрытие спецтегов, перенос панели в правую колонку, drawer при <1024px, горизонтальный скролл для >4 голосов).

**Re-check после Phase 1**: подтверждено — scope остаётся в `webvue3/src/components/SongEditor/`, новые зависимости не добавлены, секреты не затронуты, per-feature документ создаётся параллельно. Никаких нарушений.

## Project Structure

### Documentation (this feature)

```text
specs/233-mini-editor-redesign/
├── plan.md              # Этот файл
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output (создаётся /speckit.tasks, не здесь)
```

### Source Code (repository root)

```text
webvue3/
└── src/
    └── components/
        └── SongEditor/
            ├── SongKaraokeEditorView.vue      # ГЛАВНЫЙ файл правок
            │                                     (CSS + структура шаблона +
            │                                      JSDoc @see ссылка)
            ├── SongKaraokeEditorModal.vue     # Возможно, мелкие правки обёртки
            │                                     (если задаёт layout контейнера)
            └── MiniEditorDrawer.vue           # ОПЦИОНАЛЬНО: новый компонент —
                                                  обёртка для правой колонки,
                                                  которая при <1024px становится
                                                  выдвижной панелью. Можно
                                                  реализовать inline без нового
                                                  файла, если Vuex-state
                                                  `rightDrawerOpen` допустимо
                                                  держать локально в data().

livedocs/
└── features/
    └── 233-mini-editor-redesign.md            # Per-feature LiveDoc (FR-009)
```

**Structure Decision**: Single SPA (`webvue3`), frontend-only правка. Backend-изменений нет, потому что редизайн не затрагивает API, схему БД, или контракты `232-admin-song-editor-local-db` / `163-fix-song-editor-regressions`. Все правки локализованы в `webvue3/src/components/SongEditor/`.

## Complexity Tracking

> Заполняется ТОЛЬКО при нарушениях Constitution Check. Нарушений нет → секция пустая.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |

---

## Phase 0: Research

См. [`research.md`](./research.md). Краткие выводы:

- **Decision**: рефакторинг CSS-структуры и шаблона в `SongKaraokeEditorView.vue` + минимальные правки состояния (responsive breakpoint, drawer state).
- **Rationale**: вся логика мини-редактора уже в одном файле; разбиение на подкомпоненты повысит риск регрессий в `232`/`163` без выгоды. Единственное потенциально новое — обёртка `MiniEditorDrawer.vue` для responsive-сценария.
- **Alternatives considered**: (a) выделить `VoiceTabs.vue`/`SpecTagToolbar.vue`/`MarkerActionsPanel.vue` как отдельные компоненты — отвергнуто, scope-крейз; (b) использовать CSS Grid вместо Bootstrap row/col — отвергнуто, ломает существующие утилиты и baseline.

## Phase 1: Design & Contracts

См. [`data-model.md`](./data-model.md), [`contracts/`](./contracts/), [`quickstart.md`](./quickstart.md).

**Изменения по сущностям**: новых доменных сущностей нет. UI-layout-сущности (3 группы: `PlayerVoiceWaveCard`, `TextsArea`, `PreviewPanel`) описаны в `data-model.md` как визуальные блоки, без БД-схемы.

**Контракты**: `contracts/ui-layout.md` фиксирует:
- DOM-структуру 3 карточек (`PlayerVoiceWaveCard`, `TextsArea`, `PreviewPanel`).
- CSS-классы, которые используются как «контракт» (JSDoc ссылается на них).
- Состояние `showKeyboard` (уже есть через `saveEditorSettings`).
- Опциональное состояние `rightDrawerOpen` (только для responsive).
- Хедер страницы (`Машина Времени · редактирование песни`) — НЕ в скоупе мини-редактора (см. OQ-7, остаётся без изменений в этом проходе).

**Quickstart**: ручной сценарий проверки для админа (открыть 3 разные песни, проверить 3 карточки, проверить drawer при ресайзе окна, проверить >4 голосов).

## Связанные артефакты

- Спека: [`spec.md`](./spec.md) (10 FR, 5 US, 5 closed OQ, 3 open OQ)
- Per-feature LiveDoc (создаётся): [`../../livedocs/features/233-mini-editor-redesign.md`](../../livedocs/features/233-mini-editor-redesign.md)
- Смежные LiveDoc'и: [`../../livedocs/features/232-admin-song-editor-local-db.md`](../../livedocs/features/232-admin-song-editor-local-db.md), [`../../livedocs/features/163-fix-song-editor-regressions.md`](../../livedocs/features/163-fix-song-editor-regressions.md)
- Constitution: [`../../.specify/memory/constitution.md`](../../.specify/memory/constitution.md) (v2.1.0)
