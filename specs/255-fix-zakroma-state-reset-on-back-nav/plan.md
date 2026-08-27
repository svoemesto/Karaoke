# Implementation Plan: Закрома — корректный сброс state при навигации от автора обратно к тайлам

**Branch**: `255-fix-zakroma-state-reset-on-back-nav` | **Date**: 2026-08-27 | **Spec**: [spec.md](spec.md)

**Input**: Bug-fix после спеки 254. Vue-router при навигации `/zakroma?author=X` → `/zakroma` (тот же path, ушёл query `author`) НЕ пересоздаёт инстанс Vue-компонента. `data()`-properties (`authorChosen`, `selectedAuthor`, `specialBucketShown`, `songFilter`) сохраняют значения, потому что были вычислены ОДИН раз при создании компонента. Результат: URL обновляется, но `v-if="authorChosen"` в template всё ещё `true` → рендерится старый контент (`.km-author-header-sticky` + `.km-author-block`), тайлы (`v-if="!authorChosen"`) НЕ отрисовываются.

## Технический подход

Добавить **Options API watcher** в `ZakromaView.vue` на `$route.query.author`. Watcher реагирует на любую смену query (vue-router internally sync'ит `$route` с текущим URL и watcher'ы на `$route.*` срабатывают):

```js
'$route.query.author'(newAuthor) {
  if (!newAuthor && this.authorChosen) {
    // Снятие выбора автора: header-back-link, browser back, programmatic $router.push/replace без query.
    this.selectedAuthor = ''
    this.authorChosen = false
    this.specialBucketShown = false
    this.songFilter = ''
  } else if (newAuthor && newAuthor !== this.selectedAuthor) {
    // Смена автора через URL (deep-link / browser forward): перезагрузка стрима.
    this.selectedAuthor = newAuthor
    this.authorChosen = true
    this.songFilter = ''
    this.loadZakromaStream({ author: newAuthor, expectedCount: undefined })
  }
}
```

**Pure Options API fix**: 1 файл, ~10 строк правок (новый watch-блок). Без backend. Без изменения компонент-контрактов.

## Technical Context

**Language/Version**: Vue 3 SFC (Options API, как и существующие `watch:` секции в `ZakromaView.vue`).

**Primary Dependencies**:
- Vue 3 + Vue Router 4 (обработка `$route` reactivity).
- Vuex 4 (не задействован — watcher оперирует только `data`-properties).
- Без новых npm-пакетов.

**Storage**: N/A.

**Testing**: ручная проверка в браузере + DevTools-Console. Никаких автотестов в проекте нет.

**Target Platform**:
- Desktop (Chromium / Firefox).
- Mobile — поведение универсальное.

**Project Type**: SPA (`karaoke-public`).

**Performance Goals**:
- Один дополнительный watcher срабатывает только при смене `$route.query.author`. На обычный скролл/клики не реагирует (watcher key — `query.author`, не `route` целиком). Минимальный runtime-cost.

**Constraints**:
- Только `karaoke-public/src/views/ZakromaView.vue`.
- Никаких изменений в AppHeader API, router-config, store.
- ESLint baseline не должен расти.

**Scale/Scope**: 1 файл, ~10 строк.

## Constitution Check

| # | Принцип | Применим? | Compliance | Обоснование |
|---|---------|-----------|-----------|-------------|
| I | Self-contained автопайплайн | N/A | ✅ | Фикс не касается пайплайна. |
| II | Сырой JDBC | N/A | ✅ | Никаких обращений к БД. |
| III | Двух-БД синхронизация | N/A | ✅ | Нет новых сущностей. |
| IV | Async-очередь задач | N/A | ✅ | Нет process-related кода. |
| V | Двух-фронтенд | ✅ | ✅ PASS | Правки ТОЛЬКО в `karaoke-public/src/views/ZakromaView.vue`. Никаких изменений в `webvue3`, `karaoke-web`, `karaoke-app`. |
| VI | Code Standards | ✅ | ✅ PASS | FR-006 (KDoc): `ZakromaView.vue` уже имеет KDoc на компонент (`ZakromaView.vue:159, :483`); новый watcher-блок в `watch:` секции следует стилю `zakroma: { handler }` (Option API в этом файле). FR-007 (линтеры): 1 новый watcher — ESLint baseline не должен расти. FR-009 (LiveDoc sync): LiveDoc 255 создаётся; LiveDoc 254 обновляется с cross-ref на bug-fix 255 (T012). |
| VII | Cross-Machine Setup | N/A | ✅ | Нет правок `.git-blame-ignore-revs`, `.gitattributes`. |
| VIII | Секреты и git-гигиена | N/A | ✅ | Никаких секрет-файлов. |

**Вердикт Phase 0**: PASS.

## Project Structure

### Documentation (this feature)

```text
specs/255-fix-zakroma-state-reset-on-back-nav/
├── plan.md              # Этот файл
├── spec.md              # ✅ создан /speckit.specify
├── checklists/
│   └── requirements.md  # ✅ PASS
├── tasks.md             # Phase 2 output
└── (research.md / data-model.md / contracts/ — пропущены, см. ниже)
```

**research.md / data-model.md / contracts/** не создаются:
- `research.md` — fix очевиден (Options API watcher), решений нет.
- `data-model.md` — pure UI state-management, нет сущностей.
- `contracts/` — фикс не вводит и не меняет внешних контрактов (AppHeader API, router-config остаются без изменений).

### Source Code

```text
karaoke-public/
└── src/
    └── views/
        └── ZakromaView.vue    # ⚠ ЕДИНСТВЕННЫЙ ФАЙЛ С ИЗМЕНЕНИЯМИ (новый watch-блок в `watch:`)
```

**Не затрагивается**: AppHeader.vue, router/, store modules, backend, `livedocs/features/254-*` (только обновляется).

## Phase 1 артефакты

| Артефакт | Путь | Статус |
|----------|------|--------|
| Spec | `specs/255-fix-zakroma-state-reset-on-back-nav/spec.md` | ✅ |
| Checklist | `specs/255-fix-zakroma-state-reset-on-back-nav/checklists/requirements.md` | ✅ PASS |
| Plan | `specs/255-fix-zakroma-state-reset-on-back-nav/plan.md` | ✅ |
| Tasks | `specs/255-fix-zakroma-state-reset-on-back-nav/tasks.md` | ⏳ Phase 2 |

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (нет) | — | — |

Никаких нарушений.

## Подтверждение готовности к `/speckit.tasks`

- [x] Все `[NEEDS CLARIFICATION]` разрешены.
- [x] Spec + checklist PASS.
- [x] Constitution Check PASS.
- [x] Complexity Tracking пуст.
- [x] Backend / БД / sync / secrets — не задеты.

Готово к `/speckit.tasks` → `/speckit.implement`.
