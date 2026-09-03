# Implementation Plan: Кнопка «Получить текст по ссылке» — обновление UI без закрытия модалки

**Branch**: `301-search-text-extract-btn` | **Date**: 2026-09-03 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/301-search-text-extract-btn/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Баг OP#51 в admin SPA (`webvue3`): в модалке «Поиск текста в интернете» (`webvue3/src/components/Songs/edit/SearchText.vue`) после нажатия кнопки «Получить текст по ссылке» UI не обновляется (textarea не показывает текст, пункт в списке остаётся серым) — админу приходится закрывать и переоткрывать модалку.

**Корневые причины** (подтверждены в [research.md](research.md)):

1. `<textarea class="result-text" v-text="resultText" />` (line 36) — `v-text` в Vue2 устанавливает `textContent`, который **игнорируется для `<textarea>`** (textarea хранит значение в `.value`). Плюс `<textarea />` — невалидный самозакрытый тег.
2. Кнопка «Получить текст по ссылке» визуально не гарантированно расположена столбиком под «Открыть на сайте» (CSS `.group-button` не имеет явного `display: block`).

**Технический подход**:

- Заменить `v-text` → `:value` и закрыть тег `</textarea>` (1 строка → 1 строка).
- Добавить `display: block` в `.group-button` (1 строка CSS).
- Добавить JSDoc-комментарий с обоснованием (Constitution FR-006).
- Логику `extractLyricsFromSelectedResult` (lines 236-258) **не трогать** — она корректна.
- Backend **не затрагивается**.

**Объём фикса**: **3 строки** кода в одном файле + JSDoc + per-feature документ.

## Technical Context

**Language/Version**: JavaScript (Vue 2.6 + Options API), Vuex 3, Bootstrap-vue-next (admin SPA). **Vue 2** — это важно для понимания `v-text` vs `:value`.

**Primary Dependencies**:
- Vue 2.6 (НЕ Vue 3 — поведение `v-text` отличается)
- Vuex 3 (state management)
- `promisedXMLHttpRequest` (utility из `webvue3/src/lib/utils`)

**Storage**: Vue in-memory state (data/computed/methods). Нет localStorage/Vuex-persisted для этой модалки.

**Testing**: ручная проверка (нет автотестов для `webvue3`); финальная проверка — пользователем по 7 сценариям из [quickstart.md](quickstart.md).

**Target Platform**: admin SPA `webvue3` (Vue 2 + Vite build), деплой через Docker-образ `karaoke-webvue3`.

**Project Type**: bug-fix на существующем web-приложении (frontend-only).

**Performance Goals**: без регрессии. Фикс — замена одной Vue-директивы на другую + один CSS-стиль. Нулевое влияние на перф.

**Constraints**:
- **НЕ менять backend-контракты** (OP#51 — чисто UI/UX).
- **НЕ менять `SearchTextResultsTable.vue`** — он корректен (`$set` в parent реактивно обновит prop).
- **НЕ менять логику `extractLyricsFromSelectedResult`** — она корректна.
- Следовать существующему стилю `webvue3`: Vue 2 Options API, JSDoc 100% (Constitution FR-006).
- Каждый файл с watcher (в данной задаче нет watcher'ов — только правки template и CSS) сопровождается JSDoc-комментарием с `@see` (Constitution FR-006).

**Scale/Scope**: 1 файл изменений в `webvue3`; ~3-5 строк кода + JSDoc; 1 per-feature документ.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Соответствие | Обоснование |
|---------|-------------|-------------|
| **I. Self-contained автопайплайн** | ✅ N/A | UI-фикс в admin SPA, не затрагивает пайплайн медиа |
| **II. Сырой JDBC + дифф по хэшам** | ✅ N/A | Без работы с БД |
| **III. Двух-ББ синхронизация через SyncRegistry** | ✅ N/A | Без сущностей |
| **IV. Async-очередь задач с парсингом stdout** | ✅ N/A | Фикс не затрагивает async-очередь (только UI-рендеринг) |
| **V. Двух-фронтенд: админка и публичный сайт** | ✅ Соответствует | Только `webvue3` (admin), не трогаем `karaoke-public` |
| **VI. Code Standards (FR-006, FR-007, FR-009)** | ✅ Соответствует | JSDoc добавляется (FR-006), ktlint/ESLint пройдут (минимальные изменения), per-feature документ создаётся (FR-009) |
| **VII. Cross-Machine Setup** | ✅ N/A | Без правок конфигов |
| **VIII. Секреты и git-гигиена** | ✅ N/A | Без секретов |
| **Рабочий процесс (сборка, git)** | ✅ Соответствует | Только `webvue3` → `npm run build` + `bash do.sh build_webvue3` (не `karaoke-app`) |

**GATE PASSED**. Все принципы соблюдены. Нарушений нет.

**Re-check после Phase 1 design**: без изменений (дизайн полностью клиентский, не затрагивает других принципов).

## Project Structure

### Documentation (this feature)

```text
specs/301-search-text-extract-btn/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output — кодовая разведка, decisions
├── data-model.md        # Phase 1 output — client state + backend response shapes
├── quickstart.md        # Phase 1 output — runnable validation scenarios
├── contracts/           # Phase 1 output — бэкенд не меняется, README с reference
│   └── README.md
├── tasks.md             # Phase 2 output (/speckit.tasks — НЕ создаётся этим планом)
└── checklists/
    └── requirements.md   # Spec Quality Checklist (16/16 ✅)
```

### Source Code (repository root)

Фикс затрагивает **только admin SPA**, без изменений backend.

```text
webvue3/src/components/Songs/edit/
├── SearchText.vue            # ИЗМЕНЁН: <textarea v-text> → <textarea :value>, .group-button display:block, JSDoc
├── SearchTextResultsTable.vue # БЕЗ изменений (корректен)
└── SubsEdit.vue              # БЕЗ изменений (не задействован — лишь импортирует SearchText)

docs/features/
└── search-text-extract-btn.md  # NEW (per-feature, FR-011)

specs/301-search-text-extract-btn/
└── (все артефакты спеки — выше)
```

**Structure Decision**: только admin SPA, frontend-only. Backend не меняется. Karaoke-public не задействован.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (нет) | — | — |

Constitution Check пройден без нарушений — секция пуста.

## Implementation outline (high-level, для следующей фазы `/speckit.tasks`)

1. **Фикс template** (T1): заменить `<textarea class="result-text" v-text="resultText" />` на `<textarea class="result-text" :value="resultText"></textarea>` в `webvue3/src/components/Songs/edit/SearchText.vue:36`.
2. **Фикс CSS** (T2): добавить `display: block;` в `.group-button` в `webvue3/src/components/Songs/edit/SearchText.vue:500-505`.
3. **JSDoc** (T3): добавить JSDoc-комментарий к обоим фиксам с обоснованием (`@see` на `docs/features/search-text-extract-btn.md`).
4. **Per-feature документ** (T4): создать `docs/features/search-text-extract-btn.md` с описанием бага, корневой причины, фикса и reference на research.md.
5. **Валидация**: ручные сценарии из [quickstart.md](quickstart.md), линтеры (`ktlintCheck` + `npm run lint:check` + `prettier --check`), пересборка `webvue3`, деплой на dev-машину.
6. **PR**: `301-search-text-extract-btn` → `master`, прохождение CI 7/7, ручная проверка пользователем, мерж.

## Open questions

Нет открытых вопросов после `/speckit-clarify` (FR-006 в спеке OP#51 не было — все требования чёткие).

## Risks

- **R1**: добавление `display: block` в `.group-button` может повлиять на другие места, где используется `group-button` (например, `.st-footer` модалки — кнопки «Искать заново», «Удалить результаты поиска»). **Mitigation**: ручная проверка Scenario 4 в quickstart.md. Если `.st-footer` имеет `display: flex; flex-direction: row`, то `display: block` на кнопках будет проигнорирован flex-контейнером. Если нет — кнопки станут столбиком (это **вероятно правильное** поведение, т.к. они и так должны быть в столбик в footer'е).

- **R2**: если пользователь **не кликал** на ссылку в списке (т.е. `currentId` в `SearchTextResultsTable` не установлен), после успешного извлечения подсветка перейдёт `gray → white`, но не `gray → blue`. Это корректное поведение (подсветка `currentId === id` отвечает за «выбран в данный момент», а не за «текст получен»). Задача говорит «не серый» — это условие выполнится. Если после фикса окажется, что нужно также автоматически устанавливать `currentId` — это отдельная задача.

- **R3**: edge case «закрытие модалки во время выполнения запроса» — `window.alert` после закрытия будет неожиданным. **Known limitation** (см. research.md Decision 4) — отдельная задача, не блокирует текущий PR.

## Done When (для этого плана)

- [x] Plan workflow выполнен
- [x] `research.md` создан с подтверждёнными decisions
- [x] `data-model.md` создан
- [x] `contracts/README.md` создан (бэкенд не меняется)
- [x] `quickstart.md` создан с 7 validation scenarios
- [x] Constitution Check пройден без нарушений
- [x] Сложность не увеличена (нет violations)
- [ ] Создать `tasks.md` через `/speckit.tasks` — следующий шаг (НЕ создаётся этим планом)