---

description: "Задачи реализации фичи 562 — экранирование пользовательских данных в подтверждениях админки (S1, BR-123, OpenProject #360)"
---

# Tasks: 562 — Экранирование пользовательских данных в подтверждениях админки

**Input**: Design documents from `/specs/562-impl-xss-escape/`

**Prerequisites**: `plan.md`, `research.md`, контракт — `specs/558-escape-song-name-confirm/spec.md`

**Tests**: включены — спека FR-014 требует регрессионную проверку «до/после».

**Organization**: задачи сгруппированы по фазам; каждая фаза — проверяемый инкремент.

## Format: `[ID] [P?] Description`

- **[P]**: можно выполнять параллельно (разные файлы, нет зависимостей)

## Path Conventions

Монорепозиторий: `webvue3/`, `tools/`, `config/`, `docs/features/`, `knowledge/`, `specs/`.

---

## Phase 1: Knowledge-first и перепроверка инвентаря

**Purpose**: убедиться, что охват спеки (#558) соответствует коду, до правки.

- [x] T001 Прочитать `knowledge/README.md`, `knowledge/domains/README.md` (полностью) и выполнить ≥3 grep-попытки по `knowledge/` (`v-html`, `escapeHtml`, `XSS`, `sanitiz`, `CustomConfirm`, `security-config`) — результаты в `research.md`
- [x] T002 Прочитать `knowledge/domains/integration/components/song-public-dto.md` (правило «экранирование на входе»), `knowledge/system/frontend/subs-edit-admin-editor.md` (образец), `knowledge/domains/identity/components/security-config.md`, `knowledge/system/frontend/webvue3-views.md`, `knowledge/adr/0004-*`, `knowledge/adr/local-*.md`
- [x] T003 Написать `tools/check-confirm-escape.py` — сканер всех блоков `customConfirmParams` в `webvue3` (137 блоков в 18 файлах) с развёртыванием локальных переменных
- [x] T004 **Перепроверить 43 места** спеки построчно по коду и **32 исключённых места**; сверить состав семейств значений; расхождения зафиксировать в `research.md` (D11) и в спецификации #558
- [x] T005 Зафиксировать в `research.md` решения D1…D12

**Checkpoint**: охват подтверждён (43 места — все содержат пользовательские данные), расхождение
по «точкам интерполяции» (75 против 77) объяснено терминологией, состав охвата не меняется.

---

## Phase 2: Фундамент — экранирование и текстовый режим

**Purpose**: два механизма, на которые опираются все места вызова.

- [x] T006 Создать `webvue3/src/utils/escapeHtml.js` — канонический `escapeHtml` (перенос реализации из `highlightWords.js` без изменения поведения)
- [x] T007 Перевести `webvue3/src/utils/highlightWords.js` на импорт из `escapeHtml.js` с реэкспортом (обратная совместимость: `SubsEdit.vue:1210` и `highlightWords.test.js` не меняются)
- [x] T008 Переработать `webvue3/src/components/Common/CustomConfirm.vue`: текстовый режим по умолчанию (`v-text`), HTML-режим только при `params.allowHtml === true` (`v-html`); исправить неверный комментарий «message должен быть подготовленным, не пользовательским вводом»
- [x] T009 Создать `config/frontend/confirm-safe-expressions.txt` — явный список выражений, не являющихся пользовательскими данными (счётчики, id, enum, ключи настроек), с причиной по каждой строке

**Checkpoint**: механизмы готовы; поведение существующих мест **ещё** не изменилось
(`CustomConfirm` пока не переключён на текстовый режим по умолчанию до Phase 3–4).

---

## Phase 3: Правка мест сборки текста подтверждения (43 места)

**Purpose**: пользовательские данные экранируются на входе в HTML-буфер (FR-001…FR-008).

- [x] T010 [P] `webvue3/src/components/Songs/edit/SongEdit.vue` — 25 мест: `${this.song.songName}` → `${escapeHtml(this.song.songName)}`; импорт `escapeHtml`
- [x] T011 [P] `webvue3/src/views/HomeView.vue` — 9 мест: имя автора (4 прямых + 1 вложенное в `scope`), слово (`this.dictValue.toLowerCase()`) и словарь (`this.dictType`); импорт `escapeHtml`
- [x] T012 [P] `webvue3/src/components/SiteUsers/edit/SiteUserEdit.vue` — 3 места: `${this.siteUserCurrent.email}` → `escapeHtml(...)`
- [x] T013 [P] `webvue3/src/components/Songs/edit/SubsEdit.vue` — 2 места: `${selectedText.toLowerCase()}` → `escapeHtml(...)`
- [x] T014 [P] `webvue3/src/components/Common/SmartCopy/SmartCopyModal.vue` — 2 места: `this.smartCopyRenameTemplate`, `this.smartCopyPath` → `escapeHtml(...)`
- [x] T015 [P] `webvue3/src/components/Pictures/edit/PictureEdit.vue` — 1 место: `${this.pictureCurrent.name}` → `escapeHtml(...)`
- [x] T016 [P] `webvue3/src/components/Sync/SyncTable.vue` — 1 место (отчёт синхронизации): `r.displayName` и `r.error` → `escapeHtml(...)` в `showResultAlert`

**Checkpoint**: все 43 места с пользовательскими данными экранированы ровно один раз (FR-010);
данные в базе не менялись (FR-013).

---

## Phase 4: Явное разрешение HTML-режима (инвариант `allowHtml` ⇔ разметка)

**Purpose**: сохранить оформление подтверждений (FR-009) и не расширять доверенную поверхность.

- [x] T017 Проставить `allowHtml: true` во всех блоках, где в `header`/`body` есть статичная разметка (`<strong>`, `<br>`, `<hr>`) — 92 блока в 11 файлах
- [x] T018 Убедиться, что блоки **без** разметки (`allowHtml` не объявлен) остались в текстовом режиме — 45 блоков; их вывод не изменился

**Checkpoint**: оформление подтверждений сохранено; блоки без разметки безопасны по умолчанию.

---

## Phase 5: Регрессионная проверка (FR-014)

**Purpose**: проверка, которая падает на коде до правки и проходит после, и включена в проверки проекта.

- [x] T019 Довести `tools/check-confirm-escape.py` до трёх инвариантов: R1 (компонент), R2 (`allowHtml` ⇔ разметка), R3 (экранирование небезопасных выражений) + разрешение локальных переменных
- [x] T020 Создать фикстуры `tools/fixtures/confirm-escape/` (уязвимая и исправленная) и скрипт `tools/check-confirm-escape-fixtures.sh`
- [x] T021 Создать `webvue3/src/utils/__tests__/escapeHtml.test.js` — «до/после» по образцу спеки #215: baseline совпадает для значения без спецсимволов; ровно одна `&amp;` (нет `&amp;amp;`); `null`/`undefined` → `''`
- [x] T022 Подключить guard к `.pre-commit-config.yaml` (хук `confirm-escape-guard`)
- [x] T023 Подключить guard к `.github/workflows/lint.yml` (job `confirm-escape-guard`)

**Checkpoint**: FR-014 выполнен — проверка существована, падает на коде до правки, проходит
после, включена в проверки проекта (SC-002).

---

## Phase 6: Документация

- [x] T024 Создать `docs/features/escape-confirm-user-data.md` — per-feature документ (FR-009): 6 обязательных секций, slug, status
- [x] T025 Обновить `knowledge/system/frontend/` — правило «экранирование на входе» для подтверждений и решение по `vue/no-v-html`
- [x] T026 Обновить `knowledge/domains/` (L3-компонент + запись в `domain.md` по Linking Protocol) и `knowledge/README.md`/`domains/README.md` по мере надобности
- [x] T027 Обновить комментарий в `webvue3/.eslintrc.cjs` — правило остаётся выключенным (FR-015), обоснование приведено в факт
- [x] T028 Обновить `specs/558-escape-song-name-confirm/spec.md` — уточнения по итогам перепроверки (арифметика «точек интерполяции»)

---

## Phase 7: Проверки проекта

- [x] T029 `cd webvue3 && npm run lint` — PASS
- [x] T030 `cd webvue3 && npm run build` — PASS
- [x] T031 `cd webvue3 && npm run format:check` — PASS
- [x] T032 `node --test webvue3/src/utils/__tests__/escapeHtml.test.js` — PASS
- [x] T033 `bash tools/check-confirm-escape-fixtures.sh` — PASS (до → FAIL, после → PASS)
- [x] T034 `python3 tools/check-confirm-escape.py` — PASS (0 нарушений)
- [x] T035 `bash tools/check-knowledge-structure.sh` — PASS
- [x] T036 `python3 tools/check-doc-references.py` — PASS
- [x] T037 `bash tools/check-feature-doc.sh docs/features/*.md` — PASS
- [x] T038 `bash tools/check-subagent-isolation.sh` — PASS
- [x] T039 `pre-commit run --all-files` — PASS
- [x] T040 Проверить `karaoke-public`: `v-html` только к `formattedText*`, `th:utext` — 0 (вне охвата, зафиксировано)

**Checkpoint**: все проверки проекта зелёные; изменения закоммичены в ветку `562-impl-xss-escape`.
